import base64
import hashlib
import json
import logging
import os
import shutil
import tempfile
import threading
import time
from typing import Any, Dict, List, Optional, Tuple

import numpy as np

try:
    import faiss
    # In test environments, faiss might be a MagicMock
    from unittest.mock import Mock
    if isinstance(faiss, Mock) or not hasattr(faiss, "IndexFlatIP"):
        faiss = None
        _FAISS_AVAILABLE = False
    else:
        _FAISS_AVAILABLE = True
except (ImportError, AttributeError):
    faiss = None
    _FAISS_AVAILABLE = False

logger = logging.getLogger(__name__)


class VectorStoreException(Exception):
    """Base exception for VectorStore operations."""
    pass


class PersistenceException(VectorStoreException):
    """Raised when FAISS index or metadata persistence fails."""
    pass


class CorruptedIndexException(VectorStoreException):
    """Raised when active index or snapshot data is corrupted."""
    pass


class DimensionMismatchException(VectorStoreException):
    """Raised when vector embedding dimensions do not match index configuration."""
    pass


class ValidationException(VectorStoreException):
    """Raised when vector or chunk inputs fail structural or numerical validation."""
    pass


def _calculate_file_sha256(filepath: str) -> str:
    """Calculates SHA-256 checksum of a file on disk."""
    hasher = hashlib.sha256()
    with open(filepath, "rb") as f:
        while chunk := f.read(65536):
            hasher.update(chunk)
    return hasher.hexdigest()


class KBPartition:
    """
    Manages FAISS index, raw vectors, and document metadata for a single Knowledge Base.
    Ensures complete isolation between different Knowledge Bases.
    """

    def __init__(self, kb_id: str, dimension: int, kb_dir: str):
        self.kb_id = kb_id
        self.dimension = dimension
        self.kb_dir = kb_dir
        self.snapshots_dir = os.path.join(kb_dir, "snapshots")
        self.active_pointer_file = os.path.join(kb_dir, "active_generation.json")

        self.lock = threading.RLock()
        self.documents: List[Dict[str, Any]] = []
        self.vectors: Optional[np.ndarray] = None
        self.index = None
        self.current_generation: int = 0

        os.makedirs(self.snapshots_dir, exist_ok=True)
        self._load_or_initialize()

    def _create_empty_index(self):
        """Initializes a new empty IndexFlatIP for cosine similarity (with L2-normalized vectors)."""
        if _FAISS_AVAILABLE:
            self.index = faiss.IndexFlatIP(self.dimension)
        else:
            self.index = None
        self.vectors = np.empty((0, self.dimension), dtype="float32")
        self.documents = []
        self.current_generation = 0

    def _load_or_initialize(self):
        """Loads the active generation snapshot, falls back to recovery, or initializes empty."""
        with self.lock:
            if not os.path.exists(self.active_pointer_file):
                # Try to discover any existing valid snapshots
                if not self._recover_from_existing_snapshots():
                    self._create_empty_index()
                return

            try:
                with open(self.active_pointer_file, "r", encoding="utf-8") as f:
                    pointer_data = json.load(f)

                active_gen = pointer_data.get("active_generation")
                if not active_gen:
                    raise CorruptedIndexException("Missing active_generation field in pointer file")

                gen_dir = os.path.join(self.snapshots_dir, active_gen)
                if not os.path.isdir(gen_dir):
                    raise CorruptedIndexException(f"Active generation directory does not exist: {active_gen}")

                self._load_snapshot(gen_dir)
                logger.info("Successfully loaded active snapshot '%s' for KB %s (%d vectors)",
                            active_gen, self.kb_id, len(self.documents))

            except Exception as e:
                logger.warning("Active snapshot corrupt for KB %s: %s. Attempting recovery from older snapshots.",
                               self.kb_id, str(e))
                if not self._recover_from_existing_snapshots():
                    # If files exist but cannot be recovered, do NOT silently wipe data
                    if os.listdir(self.snapshots_dir):
                        raise CorruptedIndexException(
                            f"Fatal: All snapshots for Knowledge Base {self.kb_id} are corrupted and unrecoverable"
                        ) from e
                    self._create_empty_index()

    def _load_snapshot(self, gen_dir: str):
        """Validates and loads index, vectors, and documents from a snapshot directory."""
        manifest_file = os.path.join(gen_dir, "manifest.json")
        docs_file = os.path.join(gen_dir, "documents.json")
        index_file = os.path.join(gen_dir, "index.faiss")
        vectors_file = os.path.join(gen_dir, "vectors.npy")

        if not os.path.isfile(manifest_file):
            raise CorruptedIndexException(f"Missing manifest in snapshot: {gen_dir}")
        if not os.path.isfile(docs_file):
            raise CorruptedIndexException(f"Missing documents.json in snapshot: {gen_dir}")

        with open(manifest_file, "r", encoding="utf-8") as f:
            manifest = json.load(f)

        expected_count = manifest.get("vector_count", 0)
        expected_dim = manifest.get("dimension", self.dimension)
        if expected_dim != self.dimension:
            raise DimensionMismatchException(
                f"Dimension drift in snapshot: expected {self.dimension}, found {expected_dim}"
            )

        # Verify documents checksum
        if manifest.get("checksum_docs") != _calculate_file_sha256(docs_file):
            raise CorruptedIndexException("Checksum mismatch for documents.json in snapshot")

        with open(docs_file, "r", encoding="utf-8") as f:
            loaded_docs = json.load(f)

        if len(loaded_docs) != expected_count:
            raise CorruptedIndexException(
                f"Document count mismatch: manifest says {expected_count}, got {len(loaded_docs)}"
            )

        # Load raw vectors if present
        if os.path.isfile(vectors_file):
            if manifest.get("checksum_vectors") and manifest.get("checksum_vectors") != _calculate_file_sha256(vectors_file):
                raise CorruptedIndexException("Checksum mismatch for vectors.npy in snapshot")
            loaded_vectors = np.load(vectors_file)
            if loaded_vectors.shape[0] != expected_count or loaded_vectors.shape[1] != self.dimension:
                raise CorruptedIndexException("Vectors shape mismatch with manifest")
        else:
            loaded_vectors = np.empty((0, self.dimension), dtype="float32")

        # Load FAISS index if present and faiss is available
        if _FAISS_AVAILABLE and os.path.isfile(index_file):
            if manifest.get("checksum_index") and manifest.get("checksum_index") != _calculate_file_sha256(index_file):
                raise CorruptedIndexException("Checksum mismatch for index.faiss in snapshot")
            loaded_index = faiss.read_index(index_file)
            if loaded_index.ntotal != expected_count:
                raise CorruptedIndexException(
                    f"FAISS index count mismatch: expected {expected_count}, found {loaded_index.ntotal}"
                )
        elif _FAISS_AVAILABLE:
            loaded_index = faiss.IndexFlatIP(self.dimension)
            if len(loaded_vectors) > 0:
                normalized = loaded_vectors.copy()
                faiss.normalize_L2(normalized)
                loaded_index.add(normalized)
        else:
            loaded_index = None

        self.documents = loaded_docs
        self.vectors = loaded_vectors
        self.index = loaded_index
        self.current_generation = manifest.get("generation", 0)

    def _recover_from_existing_snapshots(self) -> bool:
        """Inspects all snapshot directories, finds the newest valid snapshot, and recovers it."""
        if not os.path.isdir(self.snapshots_dir):
            return False

        entries = [
            e for e in os.listdir(self.snapshots_dir)
            if os.path.isdir(os.path.join(self.snapshots_dir, e)) and not e.startswith(".")
        ]
        # Sort descending by name (generation format: gen_{timestamp}_{counter})
        entries.sort(reverse=True)

        for candidate in entries:
            candidate_dir = os.path.join(self.snapshots_dir, candidate)
            try:
                self._load_snapshot(candidate_dir)
                # Atomically update active generation pointer to the recovered snapshot
                self._publish_active_pointer(candidate)
                logger.warning("Recovered Knowledge Base %s from previous valid snapshot '%s'", self.kb_id, candidate)
                return True
            except Exception as ex:
                logger.warning("Snapshot candidate '%s' is corrupted: %s", candidate, str(ex))

        return False

    def _publish_active_pointer(self, generation_name: str):
        """Atomically publishes the active generation pointer file."""
        tmp_pointer = self.active_pointer_file + f".tmp.{os.getpid()}.{threading.get_ident()}"
        pointer_data = {
            "active_generation": generation_name,
            "published_at": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
            "kb_id": self.kb_id
        }
        with open(tmp_pointer, "w", encoding="utf-8") as f:
            json.dump(pointer_data, f, indent=2)
            f.flush()
            os.fsync(f.fileno())

        os.replace(tmp_pointer, self.active_pointer_file)

    def persist_snapshot(self) -> str:
        """
        Durable atomic publication protocol:
        1. Writes index, metadata, vectors, manifest into a staging directory.
        2. Validates files and checksums.
        3. Renames staging directory to generation name.
        4. Atomically updates active_generation.json.
        5. Prunes older generations (retaining current and previous snapshot).
        """
        with self.lock:
            next_generation = self.current_generation + 1
            gen_timestamp = int(time.time())
            gen_name = f"gen_{gen_timestamp}_{next_generation:06d}"
            staging_dir = os.path.join(self.snapshots_dir, f".staging_{gen_name}_{threading.get_ident()}")
            published = False

            try:
                os.makedirs(staging_dir, exist_ok=False)

                docs_file = os.path.join(staging_dir, "documents.json")
                with open(docs_file, "w", encoding="utf-8") as f:
                    json.dump(self.documents, f, ensure_ascii=False)
                    f.flush()
                    os.fsync(f.fileno())

                vectors_file = os.path.join(staging_dir, "vectors.npy")
                if self.vectors is not None and len(self.vectors) > 0:
                    np.save(vectors_file, self.vectors)
                else:
                    np.save(vectors_file, np.empty((0, self.dimension), dtype="float32"))

                index_file = os.path.join(staging_dir, "index.faiss")
                if _FAISS_AVAILABLE and self.index is not None:
                    faiss.write_index(self.index, index_file)

                # Compute checksums
                checksum_docs = _calculate_file_sha256(docs_file)
                checksum_vectors = _calculate_file_sha256(vectors_file)
                checksum_index = _calculate_file_sha256(index_file) if os.path.isfile(index_file) else None

                manifest_file = os.path.join(staging_dir, "manifest.json")
                manifest_data = {
                    "generation": next_generation,
                    "generation_name": gen_name,
                    "kb_id": self.kb_id,
                    "timestamp": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
                    "dimension": self.dimension,
                    "vector_count": len(self.documents),
                    "checksum_docs": checksum_docs,
                    "checksum_vectors": checksum_vectors,
                    "checksum_index": checksum_index
                }
                with open(manifest_file, "w", encoding="utf-8") as f:
                    json.dump(manifest_data, f, indent=2)
                    f.flush()
                    os.fsync(f.fileno())

                # Step 2: Validate complete staging snapshot
                if _calculate_file_sha256(docs_file) != checksum_docs:
                    raise PersistenceException("Checksum verification failed for staged documents.json")
                if _calculate_file_sha256(vectors_file) != checksum_vectors:
                    raise PersistenceException("Checksum verification failed for staged vectors.npy")
                if checksum_index and _calculate_file_sha256(index_file) != checksum_index:
                    raise PersistenceException("Checksum verification failed for staged index.faiss")

                # Step 3: Promote staging directory to published generation
                final_gen_dir = os.path.join(self.snapshots_dir, gen_name)
                os.replace(staging_dir, final_gen_dir)
                published = True

                # Step 4: Atomically publish active generation pointer
                self._publish_active_pointer(gen_name)
                self.current_generation = next_generation

                # Step 5: Prune older snapshots (keep active + 1 previous)
                self._prune_snapshots(keep_count=2)

                logger.info("Successfully published generation snapshot '%s' for KB %s (%d vectors)",
                            gen_name, self.kb_id, len(self.documents))
                return gen_name

            except Exception as e:
                logger.error("Failed to persist snapshot for KB %s: %s", self.kb_id, str(e))
                if not published and os.path.exists(staging_dir):
                    shutil.rmtree(staging_dir, ignore_errors=True)
                raise PersistenceException(f"Durable persistence failed for KB {self.kb_id}: {str(e)}") from e

    def _prune_snapshots(self, keep_count: int = 2):
        """Keeps the newest `keep_count` snapshots and removes older ones."""
        try:
            entries = [
                e for e in os.listdir(self.snapshots_dir)
                if os.path.isdir(os.path.join(self.snapshots_dir, e)) and not e.startswith(".")
            ]
            entries.sort(reverse=True)
            for old_snapshot in entries[keep_count:]:
                old_path = os.path.join(self.snapshots_dir, old_snapshot)
                shutil.rmtree(old_path, ignore_errors=True)
                logger.debug("Pruned old snapshot: %s", old_snapshot)
        except Exception as e:
            logger.warning("Error pruning old snapshots for KB %s: %s", self.kb_id, str(e))

    def add_documents(self, embeddings: List[List[float]], documents: List[Dict[str, Any]]):
        """
        Idempotent document indexing:
        Replaces any existing chunks for the same document_id, preventing vector duplication.
        Rebuilds index and updates raw vectors.
        """
        with self.lock:
            # 1. Identify all distinct document IDs in the incoming batch
            incoming_doc_ids = {
                str(doc["document_id"]) for doc in documents
                if doc.get("document_id") is not None
            }

            # 2. Filter out existing chunks that share these document IDs
            if incoming_doc_ids and self.documents:
                keep_indices = [
                    idx for idx, doc in enumerate(self.documents)
                    if str(doc.get("document_id")) not in incoming_doc_ids
                ]
                removed_count = len(self.documents) - len(keep_indices)
                if removed_count > 0:
                    logger.info("KB %s: Idempotently replacing %d old chunks for document IDs: %s",
                                self.kb_id, removed_count, incoming_doc_ids)
                    self.documents = [self.documents[idx] for idx in keep_indices]
                    if self.vectors is not None and len(self.vectors) > 0:
                        self.vectors = self.vectors[keep_indices]
                    else:
                        self.vectors = np.empty((0, self.dimension), dtype="float32")

            # 3. Append new vectors and documents
            new_vectors_arr = np.array(embeddings, dtype="float32")
            if self.vectors is not None and len(self.vectors) > 0:
                self.vectors = np.vstack([self.vectors, new_vectors_arr])
            else:
                self.vectors = new_vectors_arr

            # Normalize chunk metadata to include stable chunk_id
            for idx, doc in enumerate(documents):
                doc_copy = dict(doc)
                doc_copy["knowledge_base_id"] = self.kb_id
                if "chunk_index" not in doc_copy:
                    doc_copy["chunk_index"] = idx
                if "document_id" in doc_copy and "chunk_id" not in doc_copy:
                    doc_copy["chunk_id"] = f"{doc_copy['document_id']}#{doc_copy['chunk_index']}"
                self.documents.append(doc_copy)

            # 4. Rebuild FAISS index
            if _FAISS_AVAILABLE:
                new_index = faiss.IndexFlatIP(self.dimension)
                if len(self.vectors) > 0:
                    normalized = self.vectors.copy()
                    faiss.normalize_L2(normalized)
                    new_index.add(normalized)
                self.index = new_index

            # 5. Persist durable snapshot
            self.persist_snapshot()

    def delete_document(self, document_id: str) -> int:
        """Removes all chunks and vectors belonging to a document_id."""
        with self.lock:
            if not self.documents:
                return 0

            doc_id_str = str(document_id)
            keep_indices = [
                idx for idx, doc in enumerate(self.documents)
                if str(doc.get("document_id")) != doc_id_str
            ]
            deleted_count = len(self.documents) - len(keep_indices)
            if deleted_count == 0:
                return 0

            self.documents = [self.documents[idx] for idx in keep_indices]
            if self.vectors is not None and len(self.vectors) > 0:
                self.vectors = self.vectors[keep_indices]
            else:
                self.vectors = np.empty((0, self.dimension), dtype="float32")

            if _FAISS_AVAILABLE:
                new_index = faiss.IndexFlatIP(self.dimension)
                if len(self.vectors) > 0:
                    normalized = self.vectors.copy()
                    faiss.normalize_L2(normalized)
                    new_index.add(normalized)
                self.index = new_index

            self.persist_snapshot()
            logger.info("KB %s: Deleted %d chunks for document %s", self.kb_id, deleted_count, doc_id_str)
            return deleted_count

    def search(self, query_embedding: List[float], top_k: int = 5) -> List[Dict[str, Any]]:
        """Searches exclusively within this KB partition."""
        with self.lock:
            if not self.documents or self.vectors is None or len(self.vectors) == 0:
                return []

            query_arr = np.array([query_embedding], dtype="float32")

            if _FAISS_AVAILABLE and self.index is not None:
                faiss.normalize_L2(query_arr)
                k = min(top_k, self.index.ntotal)
                if k <= 0:
                    return []
                scores, indices = self.index.search(query_arr, k)

                results = []
                for score, index in zip(scores[0], indices[0]):
                    if index == -1 or index >= len(self.documents):
                        continue
                    results.append({
                        "score": float(score),
                        "document": self.documents[index]
                    })
                return results
            else:
                # NumPy fallback for test/mock environments
                query_norm = query_arr / (np.linalg.norm(query_arr, axis=1, keepdims=True) + 1e-10)
                vectors_norm = self.vectors / (np.linalg.norm(self.vectors, axis=1, keepdims=True) + 1e-10)
                sims = np.dot(vectors_norm, query_norm.T).flatten()
                top_indices = np.argsort(sims)[::-1][:top_k]

                results = []
                for idx in top_indices:
                    results.append({
                        "score": float(sims[idx]),
                        "document": self.documents[idx]
                    })
                return results

    def count(self) -> int:
        with self.lock:
            return len(self.documents)


class VectorStore:
    """
    Enterprise FAISS VectorStore with dedicated Knowledge Base partitioning,
    idempotent document replacement, bounded validation, and crash-consistent persistence.
    """

    def __init__(self, dimension: int, storage_dir: str = "faiss/indexes"):
        self.dimension = dimension
        self.storage_dir = storage_dir
        self.global_lock = threading.RLock()
        self.partitions: Dict[str, KBPartition] = {}

        os.makedirs(self.storage_dir, exist_ok=True)
        self._initialize_partitions()

    def _initialize_partitions(self):
        """Scans storage directory for existing KB partitions and handles legacy index migration."""
        with self.global_lock:
            # 1. Check for legacy root index (index.faiss and documents.json)
            legacy_index_file = os.path.join(self.storage_dir, "index.faiss")
            legacy_docs_file = os.path.join(self.storage_dir, "documents.json")

            if os.path.isfile(legacy_docs_file):
                self._migrate_legacy_index(legacy_index_file, legacy_docs_file)

            # 2. Discover and initialize all partition directories
            for entry in os.listdir(self.storage_dir):
                entry_path = os.path.join(self.storage_dir, entry)
                if os.path.isdir(entry_path) and not entry.startswith("."):
                    try:
                        self.partitions[entry] = KBPartition(
                            kb_id=entry,
                            dimension=self.dimension,
                            kb_dir=entry_path
                        )
                    except Exception as e:
                        logger.error("Failed to initialize partition for KB %s: %s", entry, str(e))

    def _migrate_legacy_index(self, index_file: str, docs_file: str):
        """
        Safely migrates legacy unpartitioned index into dedicated per-KB partitions
        without discarding any existing data.
        """
        logger.info("Legacy unpartitioned index detected at %s. Inspecting for migration.", self.storage_dir)
        try:
            with open(docs_file, "r", encoding="utf-8") as f:
                legacy_docs = json.load(f)

            if not legacy_docs:
                return

            legacy_vectors = None
            if _FAISS_AVAILABLE and os.path.isfile(index_file):
                try:
                    loaded_index = faiss.read_index(index_file)
                    if hasattr(loaded_index, "reconstruct_n") and loaded_index.ntotal == len(legacy_docs):
                        legacy_vectors = loaded_index.reconstruct_n(0, loaded_index.ntotal)
                except Exception as ex:
                    logger.warning("Could not reconstruct vectors from legacy index.faiss: %s", str(ex))

            # Validate trustworthiness:
            # 1. Vectors must be reconstructed and match doc count
            # 2. Every document must have a non-empty, valid knowledge_base_id
            # 3. No record may default to "default"
            can_safely_migrate = True
            quarantine_reason = ""

            if legacy_vectors is None or len(legacy_vectors) != len(legacy_docs):
                can_safely_migrate = False
                quarantine_reason = "Cannot reconstruct valid embeddings for legacy documents (no all-zero fallbacks permitted)"
            else:
                for idx, doc in enumerate(legacy_docs):
                    kb_id = doc.get("knowledge_base_id")
                    if not kb_id or not str(kb_id).strip() or str(kb_id).strip().lower() in ("default", "none", "null"):
                        can_safely_migrate = False
                        quarantine_reason = f"Legacy document at index {idx} has missing or untrusted knowledge_base_id: {kb_id}"
                        break

            if not can_safely_migrate:
                # Quarantine legacy index files rather than indexing into an untrusted partition
                quarantine_dir = os.path.join(self.storage_dir, f".quarantine_{int(time.time())}")
                os.makedirs(quarantine_dir, exist_ok=True)
                logger.error("Legacy index failed safety verification (%s). Quarantining files to %s.",
                             quarantine_reason, quarantine_dir)
                if os.path.isfile(docs_file):
                    shutil.move(docs_file, os.path.join(quarantine_dir, "documents.json"))
                if os.path.isfile(index_file):
                    shutil.move(index_file, os.path.join(quarantine_dir, "index.faiss"))
                return

            # Safe migration path
            kb_grouped: Dict[str, Tuple[List[Dict[str, Any]], List[List[float]]]] = {}
            for idx, doc in enumerate(legacy_docs):
                kb_id = str(doc["knowledge_base_id"]).strip()
                if kb_id not in kb_grouped:
                    kb_grouped[kb_id] = ([], [])
                kb_grouped[kb_id][0].append(doc)
                kb_grouped[kb_id][1].append(legacy_vectors[idx].tolist())

            for kb_id, (docs, embs) in kb_grouped.items():
                kb_dir = os.path.join(self.storage_dir, kb_id)
                os.makedirs(kb_dir, exist_ok=True)
                partition = KBPartition(kb_id=kb_id, dimension=self.dimension, kb_dir=kb_dir)
                partition.add_documents(embs, docs)
                self.partitions[kb_id] = partition
                logger.info("Migrated %d validated legacy chunks into KB partition '%s'", len(docs), kb_id)

            # Archive legacy files safely with timestamp suffix
            archive_suffix = f".migrated_{int(time.time())}"
            if os.path.isfile(docs_file):
                os.rename(docs_file, docs_file + archive_suffix)
            if os.path.isfile(index_file):
                os.rename(index_file, index_file + archive_suffix)

            logger.info("Legacy index migration completed successfully.")

        except Exception as e:
            logger.error("Error during legacy index migration: %s", str(e), exc_info=True)

    def _get_or_create_partition(self, kb_id: str) -> KBPartition:
        """Retrieves an existing KB partition or initializes a new one under global lock."""
        clean_kb_id = str(kb_id).strip()
        if not clean_kb_id:
            raise ValidationException("knowledge_base_id cannot be blank")

        with self.global_lock:
            if clean_kb_id not in self.partitions:
                kb_dir = os.path.join(self.storage_dir, clean_kb_id)
                os.makedirs(kb_dir, exist_ok=True)
                self.partitions[clean_kb_id] = KBPartition(
                    kb_id=clean_kb_id,
                    dimension=self.dimension,
                    kb_dir=kb_dir
                )
            return self.partitions[clean_kb_id]

    def validate_vectors(self, embeddings: List[List[float]], documents: List[Dict[str, Any]]):
        """
        Validates embedding dimensions, counts, and numerical integrity.
        Rejects NaN, infinity, empty vectors, and dimension drift.
        """
        if not embeddings or not documents:
            raise ValidationException("Embeddings and documents cannot be empty")

        if len(embeddings) != len(documents):
            raise ValidationException(
                f"Count mismatch: received {len(embeddings)} embeddings for {len(documents)} documents"
            )

        for idx, emb in enumerate(embeddings):
            if not isinstance(emb, (list, np.ndarray)) or len(emb) == 0:
                raise ValidationException(f"Empty or non-vector embedding at index {idx}")

            if len(emb) != self.dimension:
                raise DimensionMismatchException(
                    f"Dimension mismatch at index {idx}: expected {self.dimension}, got {len(emb)}"
                )

            # Numerical validation: reject NaN, infinity, or non-numeric types
            arr = np.asarray(emb, dtype="float32")
            if np.isnan(arr).any():
                raise ValidationException(f"Embedding vector at index {idx} contains NaN values")
            if np.isinf(arr).any():
                raise ValidationException(f"Embedding vector at index {idx} contains infinite values")

    def add(
        self,
        embeddings: List[List[float]],
        documents: List[Dict[str, Any]],
        knowledge_base_id: str
    ):
        """
        Adds documents and embeddings idempotently to the target Knowledge Base partition.
        Requires authoritative knowledge_base_id; rejects missing, empty, or default values.
        """
        if not knowledge_base_id or not str(knowledge_base_id).strip():
            raise ValidationException("knowledge_base_id is mandatory for vector indexing")

        clean_kb_id = str(knowledge_base_id).strip()
        if clean_kb_id.lower() in ("default", "none", "null"):
            raise ValidationException(f"Invalid knowledge_base_id: {clean_kb_id}")

        self.validate_vectors(embeddings, documents)
        partition = self._get_or_create_partition(clean_kb_id)
        partition.add_documents(embeddings, documents)

    def search(
        self,
        query_embedding: List[float],
        knowledge_base_id: Optional[str] = None,
        top_k: int = 5
    ) -> List[Dict[str, Any]]:
        """
        Searches strictly within the requested Knowledge Base partition.
        Guarantees ZERO data leakage from other Knowledge Bases.
        """
        if not knowledge_base_id:
            raise ValidationException("knowledge_base_id is mandatory for vector search")

        clean_kb_id = str(knowledge_base_id).strip()
        if not clean_kb_id:
            raise ValidationException("knowledge_base_id cannot be blank")

        # Validate query embedding
        if not query_embedding or len(query_embedding) != self.dimension:
            raise DimensionMismatchException(
                f"Query embedding dimension mismatch: expected {self.dimension}, got {len(query_embedding) if query_embedding else 0}"
            )

        query_arr = np.asarray(query_embedding, dtype="float32")
        if np.isnan(query_arr).any() or np.isinf(query_arr).any():
            raise ValidationException("Query embedding contains NaN or infinite values")

        with self.global_lock:
            partition = self.partitions.get(clean_kb_id)

        if partition is None:
            # KB partition not found or has no documents indexed yet
            return []

        return partition.search(query_embedding=query_embedding, top_k=top_k)

    def delete_document(self, document_id: str, knowledge_base_id: Optional[str] = None) -> int:
        """Removes a document from the specified KB partition or all partitions."""
        if not document_id:
            return 0

        clean_doc_id = str(document_id).strip()
        if knowledge_base_id:
            clean_kb_id = str(knowledge_base_id).strip()
            with self.global_lock:
                partition = self.partitions.get(clean_kb_id)
            if partition:
                return partition.delete_document(clean_doc_id)
            return 0

        total_deleted = 0
        with self.global_lock:
            partitions_snapshot = list(self.partitions.values())

        for partition in partitions_snapshot:
            total_deleted += partition.delete_document(clean_doc_id)
        return total_deleted

    def delete_knowledge_base(self, knowledge_base_id: str) -> bool:
        """Removes an entire Knowledge Base partition and deletes its persisted directory."""
        if not knowledge_base_id:
            return False

        clean_kb_id = str(knowledge_base_id).strip()
        with self.global_lock:
            partition = self.partitions.pop(clean_kb_id, None)

        if partition:
            try:
                shutil.rmtree(partition.kb_dir, ignore_errors=True)
                logger.info("Purged Knowledge Base partition: %s", clean_kb_id)
                return True
            except Exception as e:
                logger.error("Error removing partition directory for KB %s: %s", clean_kb_id, str(e))
                return False
        return False

    def count(self, knowledge_base_id: Optional[str] = None) -> int:
        """Returns total vector count for a specific Knowledge Base or all partitions."""
        if knowledge_base_id:
            clean_kb_id = str(knowledge_base_id).strip()
            with self.global_lock:
                partition = self.partitions.get(clean_kb_id)
            return partition.count() if partition else 0

        with self.global_lock:
            return sum(p.count() for p in self.partitions.values())
