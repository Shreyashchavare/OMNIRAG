"""
Audio temporary file lifecycle management, safe cleanup, cancellation safety,
and startup orphan-file recovery for OMRAGUL Multimodal Service.

Stage 3.6 Batch 3.3.
"""

import asyncio
import logging
import os
from pathlib import Path
import tempfile
import threading
import time
from typing import Callable, Optional, Set, Tuple
import uuid

from fastapi import HTTPException, UploadFile

from app.config.settings import settings

logger = logging.getLogger(__name__)

AUDIO_FILE_PREFIX = "omragul_audio_"

_ACTIVE_AUDIO_FILES: Set[str] = set()
_ACTIVE_LOCK = threading.Lock()


def get_audio_temp_dir() -> Path:
    """Returns the resolved dedicated audio temporary directory, ensuring it exists."""
    return settings.resolved_audio_temp_dir


def is_active_audio_file(path: str) -> bool:
    """Checks whether the file is currently owned by an active request or worker."""
    norm = os.path.abspath(path)
    with _ACTIVE_LOCK:
        return norm in _ACTIVE_AUDIO_FILES


def register_active_audio_file(path: str) -> str:
    """Registers an audio temporary file as active in the current process."""
    norm = os.path.abspath(path)
    with _ACTIVE_LOCK:
        _ACTIVE_AUDIO_FILES.add(norm)
    return norm


def unregister_active_audio_file(path: str) -> None:
    """Unregisters an audio temporary file from the active set."""
    norm = os.path.abspath(path)
    with _ACTIVE_LOCK:
        _ACTIVE_AUDIO_FILES.discard(norm)


def safe_remove_audio_file(path: Optional[str]) -> bool:
    """
    Safely removes a temporary audio file and unregisters it from the active set.
    Handles missing files and OS errors gracefully.
    Returns True if removed, False otherwise.
    """
    if not path:
        return False

    norm = os.path.abspath(path)
    unregister_active_audio_file(norm)

    try:
        if os.path.exists(norm):
            os.remove(norm)
            logger.debug("Successfully removed audio temporary file: %s", os.path.basename(norm))
            return True
    except Exception as e:
        logger.warning(
            "Failed to delete temporary audio file %s: %s",
            os.path.basename(norm),
            str(e)
        )
    return False


async def stream_upload_to_temp_file(
    file: UploadFile,
    suffix: str,
    max_bytes: int = settings.MAX_UPLOAD_SIZE_BYTES,
    chunk_size: int = 64 * 1024
) -> str:
    """
    Streams an UploadFile incrementally to a NamedTemporaryFile in the dedicated audio directory.
    Enforces maximum upload bytes and immediately deletes partial files on failure.
    The file handle is explicitly flushed and closed before returning to ensure Windows file-sharing safety.
    """
    if file.size is not None and file.size > max_bytes:
        raise HTTPException(
            status_code=413,
            detail=f"Uploaded audio file exceeds maximum allowed size of {max_bytes} bytes"
        )

    temp_dir = get_audio_temp_dir()
    unique_suffix = f"_{uuid.uuid4().hex}_{int(time.time_ns())}{suffix}"
    temp_path: Optional[str] = None

    try:
        temp_file = tempfile.NamedTemporaryFile(
            dir=str(temp_dir),
            prefix=AUDIO_FILE_PREFIX,
            suffix=unique_suffix,
            delete=False
        )
        temp_path = register_active_audio_file(temp_file.name)
        total = 0
        has_content = False

        with temp_file:
            while True:
                chunk = await file.read(chunk_size)
                if not chunk:
                    break
                has_content = True
                total += len(chunk)
                if total > max_bytes:
                    raise HTTPException(
                        status_code=413,
                        detail=f"Uploaded audio file exceeds maximum allowed size of {max_bytes} bytes"
                    )
                temp_file.write(chunk)
            temp_file.flush()

        if not has_content or total == 0:
            raise HTTPException(
                status_code=400,
                detail="Uploaded audio file is empty"
            )

        return temp_path

    except Exception:
        if temp_path:
            safe_remove_audio_file(temp_path)
        raise


async def execute_whisper_cancellation_safe(
    sem: asyncio.Semaphore,
    temp_path: str,
    transcribe_fn: Callable[[str], Tuple[str, str]],
    cancel_event: asyncio.Event
) -> Tuple[str, str]:
    """
    Executes Whisper transcription on a background worker thread with comprehensive cancellation safety:
    1. Holds the semaphore for the entire duration of thread execution.
    2. If the request was cancelled while waiting on the semaphore, the worker thread is never started.
    3. If the request is cancelled while the worker thread is running, the thread finishes safely without
       the file being deleted out from underneath it.
    4. Cleans up the temporary file and releases the semaphore once the worker thread has completely finished.
    """
    await sem.acquire()
    try:
        if cancel_event.is_set():
            # Request was aborted while waiting in the semaphore queue; do not start CPU worker
            raise asyncio.CancelledError()

        return await asyncio.to_thread(transcribe_fn, temp_path)
    finally:
        try:
            safe_remove_audio_file(temp_path)
        finally:
            sem.release()


def cleanup_orphaned_audio_files(
    directory: Path,
    retention_seconds: int = settings.AUDIO_TEMP_RETENTION_SECONDS
) -> int:
    """
    Scans the dedicated audio temporary directory and safely deletes stale orphaned files:
    - Only deletes files matching AUDIO_FILE_PREFIX.
    - Never deletes active files registered in the current process.
    - Never deletes files younger than the retention threshold (protecting active files from peer processes).
    - Never deletes subdirectories or unrelated files.
    - Handles permission errors and missing directories gracefully.
    Returns the count of successfully cleaned files.
    """
    if not directory.exists() or not directory.is_dir():
        try:
            directory.mkdir(parents=True, exist_ok=True)
        except Exception as e:
            logger.warning("Could not create audio temp directory %s: %s", directory, str(e))
        return 0

    cutoff = time.time() - retention_seconds
    cleaned_count = 0
    preserved_count = 0

    try:
        with os.scandir(str(directory)) as entries:
            for entry in entries:
                try:
                    if not entry.is_file(follow_symlinks=False):
                        continue

                    if not entry.name.startswith(AUDIO_FILE_PREFIX):
                        # Unrelated file; ignore
                        continue

                    full_path = os.path.abspath(entry.path)
                    if is_active_audio_file(full_path):
                        preserved_count += 1
                        continue

                    stat_result = entry.stat()
                    if stat_result.st_mtime >= cutoff:
                        # Too recent; might belong to another active instance
                        preserved_count += 1
                        continue

                    # Safe to clean
                    os.remove(full_path)
                    cleaned_count += 1
                    logger.debug("Cleaned up orphaned audio file: %s", entry.name)

                except Exception as file_err:
                    logger.warning(
                        "Error checking/cleaning file %s: %s",
                        entry.name,
                        str(file_err)
                    )

        if cleaned_count > 0:
            logger.info(
                "Audio temporary file recovery completed: %d stale files cleaned, %d active/recent preserved",
                cleaned_count,
                preserved_count
            )
    except Exception as scan_err:
        logger.warning(
            "Failed to scan audio temp directory %s: %s",
            directory,
            str(scan_err)
        )

    return cleaned_count
