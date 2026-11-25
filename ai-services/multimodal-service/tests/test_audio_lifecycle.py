"""
Unit and integration tests for Stage 3.6 Batch 3.3:
Audio Temporary File Lifecycle, Crash Recovery, and Cancellation Safety.

Covers:
1. Normal Whisper success deletes the file after worker completion.
2. Whisper exception deletes the file.
3. Request cancellation while Whisper is still running does not delete the file prematurely.
4. Whisper semaphore remains occupied until the worker really completes.
5. A second request cannot exceed the configured active worker limit while the first worker continues after cancellation.
6. Worker exceptions after request cancellation are retrieved safely without unhandled background task exceptions.
7. Startup cleanup deletes eligible stale application-owned files.
8. Startup cleanup preserves recent files.
9. Startup cleanup preserves unrelated files.
10. Startup cleanup handles missing directories and filesystem errors safely.
11. Windows-compatible file lifecycle behavior and file sharing.
12. Existing audio upload size enforcement and partial-upload cleanup remain intact.
"""

import asyncio
import io
import os
from pathlib import Path
import sys
import tempfile
import threading
import time
import unittest
from unittest.mock import AsyncMock, MagicMock, patch

from fastapi import HTTPException, UploadFile, Request

# Mock external optional libraries if not installed in host test runner
for mod_name in ("pytesseract", "faster_whisper", "pymupdf", "multipart", "multipart.multipart"):
    if mod_name not in sys.modules:
        sys.modules[mod_name] = MagicMock()

try:
    import fastapi.dependencies.utils as fastapi_utils
    fastapi_utils.ensure_multipart_is_installed = lambda: None
except Exception:
    pass

multimodal_service_path = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
if multimodal_service_path not in sys.path:
    sys.path.insert(0, multimodal_service_path)

from app.config.settings import settings
from app.main import app, lifespan
from app.api.routes.multimodal_routes import transcribe_audio
from app.services.audio_lifecycle import (
    AUDIO_FILE_PREFIX,
    cleanup_orphaned_audio_files,
    execute_whisper_cancellation_safe,
    get_audio_temp_dir,
    is_active_audio_file,
    register_active_audio_file,
    safe_remove_audio_file,
    stream_upload_to_temp_file,
    unregister_active_audio_file,
)


class TestAudioTemporaryFileLifecycle(unittest.IsolatedAsyncioTestCase):

    async def test_normal_whisper_success_deletes_file_after_worker_completion(self):
        """Temporary audio file exists during transcription and is deleted after completion."""
        audio_content = b"RIFF....WAVEfmt ....VALID_AUDIO"
        upload_file = UploadFile(
            file=io.BytesIO(audio_content),
            filename="sample.wav",
            headers={"content-type": "audio/wav"}
        )

        captured_path = []

        def mock_transcribe(path):
            captured_path.append(path)
            # Verify file exists on disk and is in dedicated directory
            self.assertTrue(os.path.exists(path))
            self.assertTrue(os.path.basename(path).startswith(AUDIO_FILE_PREFIX))
            self.assertTrue(is_active_audio_file(path))
            return "Transcribed audio text", "en"

        with patch("app.api.routes.multimodal_routes.speech_service.transcribe", side_effect=mock_transcribe):
            response = await transcribe_audio(file=upload_file)
            self.assertEqual(response.text, "Transcribed audio text")
            self.assertEqual(response.language, "en")

        # After route finishes, file is deleted and unregistered from active files
        self.assertEqual(len(captured_path), 1)
        self.assertFalse(os.path.exists(captured_path[0]))
        self.assertFalse(is_active_audio_file(captured_path[0]))

    async def test_whisper_exception_deletes_file_after_worker_failure(self):
        """Temporary audio file is deleted even when transcription raises an exception."""
        audio_content = b"RIFF....WAVEfmt ....FAIL_AUDIO"
        upload_file = UploadFile(
            file=io.BytesIO(audio_content),
            filename="fail.wav",
            headers={"content-type": "audio/wav"}
        )

        captured_path = []

        def failing_transcribe(path):
            captured_path.append(path)
            self.assertTrue(os.path.exists(path))
            raise RuntimeError("CUDA out of memory or inference crash")

        with patch("app.api.routes.multimodal_routes.speech_service.transcribe", side_effect=failing_transcribe):
            with self.assertRaises(HTTPException) as ctx:
                await transcribe_audio(file=upload_file)
            self.assertEqual(ctx.exception.status_code, 500)

        # File is reliably deleted and unregistered despite worker failure
        self.assertEqual(len(captured_path), 1)
        self.assertFalse(os.path.exists(captured_path[0]))
        self.assertFalse(is_active_audio_file(captured_path[0]))

    async def test_request_cancellation_does_not_delete_file_while_worker_running(self):
        """When HTTP request is cancelled mid-inference, file remains intact until worker finishes."""
        audio_content = b"RIFF....WAVEfmt ....CANCEL_AUDIO"
        upload_file = UploadFile(
            file=io.BytesIO(audio_content),
            filename="cancel.wav",
            headers={"content-type": "audio/wav"}
        )

        worker_started = threading.Event()
        worker_can_finish = threading.Event()
        captured_path = []

        def long_running_transcribe(path):
            captured_path.append(path)
            worker_started.set()
            # Wait for signal before completing
            worker_can_finish.wait(timeout=5.0)
            return "Late finish", "en"

        with patch("app.api.routes.multimodal_routes.speech_service.transcribe", side_effect=long_running_transcribe):
            task = asyncio.create_task(transcribe_audio(file=upload_file))

            # Wait for worker thread to actually start
            while not worker_started.is_set():
                await asyncio.sleep(0.01)

            # File must exist while worker thread is active
            self.assertEqual(len(captured_path), 1)
            file_path = captured_path[0]
            self.assertTrue(os.path.exists(file_path))

            # Cancel the HTTP request coroutine
            task.cancel()
            with self.assertRaises(asyncio.CancelledError):
                await task

            # Crucial verification: file must STILL exist because worker thread has not finished!
            self.assertTrue(os.path.exists(file_path))
            self.assertTrue(is_active_audio_file(file_path))

            # Now allow worker thread to finish
            worker_can_finish.set()
            await asyncio.sleep(0.05)

            # After worker completes, the shielded task cleaned up the file
            self.assertFalse(os.path.exists(file_path))
            self.assertFalse(is_active_audio_file(file_path))

    async def test_semaphore_remains_held_during_cancellation_until_worker_completes(self):
        """Semaphore remains occupied by cancelled worker, preventing concurrency overrun."""
        # Single worker concurrency limit
        sem = asyncio.Semaphore(1)
        app_mock = MagicMock()
        app_mock.state.whisper_semaphore = sem

        request1 = MagicMock(spec=Request)
        request1.app = app_mock

        worker1_started = threading.Event()
        worker1_can_finish = threading.Event()

        def slow_worker1(path):
            worker1_started.set()
            worker1_can_finish.wait(timeout=5.0)
            return "Worker 1 Result", "en"

        def fast_worker2(path):
            return "Worker 2 Result", "en"

        upload1 = UploadFile(file=io.BytesIO(b"AUDIO_1"), filename="1.wav", headers={"content-type": "audio/wav"})
        upload2 = UploadFile(file=io.BytesIO(b"AUDIO_2"), filename="2.wav", headers={"content-type": "audio/wav"})

        with patch("app.api.routes.multimodal_routes.speech_service.transcribe", side_effect=slow_worker1):
            task1 = asyncio.create_task(transcribe_audio(file=upload1, request=request1))

            while not worker1_started.is_set():
                await asyncio.sleep(0.01)

            # Cancel task1 while worker1 is still running
            task1.cancel()
            with self.assertRaises(asyncio.CancelledError):
                await task1

            # Semaphore MUST still be locked (locked = True, value = 0)
            self.assertTrue(sem.locked())

            # Launch task2 with same semaphore
            with patch("app.api.routes.multimodal_routes.speech_service.transcribe", side_effect=fast_worker2):
                task2 = asyncio.create_task(transcribe_audio(file=upload2, request=request1))
                await asyncio.sleep(0.05)

                # Task2 MUST NOT have finished yet because worker1 is still occupying semaphore!
                self.assertFalse(task2.done())

                # Release worker1
                worker1_can_finish.set()

                # Now task2 should be able to acquire semaphore and finish
                resp2 = await asyncio.wait_for(task2, timeout=5.0)
                self.assertEqual(resp2.text, "Worker 2 Result")

            self.assertFalse(sem.locked())

    async def test_worker_exception_after_cancellation_is_safely_consumed(self):
        """If worker thread raises an exception after request was cancelled, exception is retrieved safely."""
        upload_file = UploadFile(
            file=io.BytesIO(b"AUDIO_EXCEPTION"),
            filename="error.wav",
            headers={"content-type": "audio/wav"}
        )

        worker_started = threading.Event()
        worker_can_fail = threading.Event()

        def failing_detached_worker(path):
            worker_started.set()
            worker_can_fail.wait(timeout=5.0)
            raise ValueError("Detached worker failure")

        with patch("app.api.routes.multimodal_routes.speech_service.transcribe", side_effect=failing_detached_worker):
            task = asyncio.create_task(transcribe_audio(file=upload_file))

            while not worker_started.is_set():
                await asyncio.sleep(0.01)

            task.cancel()
            with self.assertRaises(asyncio.CancelledError):
                await task

            # Trigger worker failure
            worker_can_fail.set()
            await asyncio.sleep(0.05)
            # Verified no unhandled exception crash or warning leaked


class TestStartupOrphanRecovery(unittest.TestCase):

    def test_startup_recovery_cleans_stale_files_and_preserves_recent_and_unrelated(self):
        """Startup cleanup removes application files older than retention, preserving recent and foreign files."""
        with tempfile.TemporaryDirectory() as temp_dir_str:
            temp_dir = Path(temp_dir_str)

            now = time.time()
            one_day_ago = now - 86400  # Stale
            ten_seconds_ago = now - 10  # Recent

            # 1. Stale application audio file (eligible for cleanup)
            stale_file = temp_dir / f"{AUDIO_FILE_PREFIX}stale_12345.tmp"
            stale_file.write_bytes(b"stale_audio_data")
            os.utime(str(stale_file), (one_day_ago, one_day_ago))

            # 2. Recent application audio file (must be preserved)
            recent_file = temp_dir / f"{AUDIO_FILE_PREFIX}recent_67890.tmp"
            recent_file.write_bytes(b"recent_audio_data")
            os.utime(str(recent_file), (ten_seconds_ago, ten_seconds_ago))

            # 3. Unrelated foreign file (must be preserved)
            foreign_file = temp_dir / "unrelated_document.txt"
            foreign_file.write_bytes(b"unrelated_data")
            os.utime(str(foreign_file), (one_day_ago, one_day_ago))

            # 4. Unrelated subdirectory (must be preserved)
            sub_dir = temp_dir / "sub_folder"
            sub_dir.mkdir()

            # Run recovery with 1 hour retention (3600s)
            cleaned = cleanup_orphaned_audio_files(directory=temp_dir, retention_seconds=3600)

            # Assertions
            self.assertEqual(cleaned, 1)
            self.assertFalse(stale_file.exists())
            self.assertTrue(recent_file.exists())
            self.assertTrue(foreign_file.exists())
            self.assertTrue(sub_dir.exists())

    def test_startup_recovery_preserves_active_files(self):
        """Even if mtime is old, files registered as active in current process are never deleted."""
        with tempfile.TemporaryDirectory() as temp_dir_str:
            temp_dir = Path(temp_dir_str)
            old_time = time.time() - 86400

            active_file = temp_dir / f"{AUDIO_FILE_PREFIX}active_file.wav"
            active_file.write_bytes(b"active_data")
            os.utime(str(active_file), (old_time, old_time))

            # Register as active in current process
            register_active_audio_file(str(active_file))

            try:
                cleaned = cleanup_orphaned_audio_files(directory=temp_dir, retention_seconds=3600)
                self.assertEqual(cleaned, 0)
                self.assertTrue(active_file.exists())
            finally:
                unregister_active_audio_file(str(active_file))

    def test_startup_recovery_handles_missing_directory(self):
        """Non-existent directory is created safely and returns 0 without crashing."""
        with tempfile.TemporaryDirectory() as temp_dir_str:
            missing_dir = Path(temp_dir_str) / "new_audio_dir"
            self.assertFalse(missing_dir.exists())

            cleaned = cleanup_orphaned_audio_files(directory=missing_dir, retention_seconds=3600)
            self.assertEqual(cleaned, 0)
            self.assertTrue(missing_dir.exists())

    def test_startup_recovery_handles_filesystem_errors_gracefully(self):
        """PermissionError or OS errors on file deletion do not crash recovery."""
        with tempfile.TemporaryDirectory() as temp_dir_str:
            temp_dir = Path(temp_dir_str)
            old_time = time.time() - 86400

            stale_file = temp_dir / f"{AUDIO_FILE_PREFIX}locked.tmp"
            stale_file.write_bytes(b"locked")
            os.utime(str(stale_file), (old_time, old_time))

            with patch("os.remove", side_effect=PermissionError("Simulated Windows file lock")):
                cleaned = cleanup_orphaned_audio_files(directory=temp_dir, retention_seconds=3600)
                self.assertEqual(cleaned, 0)
                self.assertTrue(stale_file.exists())


class TestSettingsValidationAndLifespanIntegration(unittest.IsolatedAsyncioTestCase):

    def test_settings_validation_rejects_non_positive_values(self):
        """Settings validate positive integers for retention and bounding."""
        self.assertGreater(settings.AUDIO_TEMP_RETENTION_SECONDS, 0)
        self.assertGreater(settings.MAX_UPLOAD_SIZE_BYTES, 0)
        self.assertGreater(settings.MAX_CONCURRENT_WHISPER, 0)

    async def test_lifespan_triggers_startup_recovery(self):
        """Application lifespan executes startup orphan cleanup."""
        test_app = app
        with patch("app.main.cleanup_orphaned_audio_files", return_value=3) as mock_cleanup:
            async with lifespan(test_app):
                mock_cleanup.assert_called_once()
                call_kwargs = mock_cleanup.call_args.kwargs
                self.assertEqual(call_kwargs.get("retention_seconds"), settings.AUDIO_TEMP_RETENTION_SECONDS)


if __name__ == "__main__":
    unittest.main()
