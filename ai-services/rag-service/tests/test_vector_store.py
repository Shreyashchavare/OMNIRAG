"""
Comprehensive unit tests for VectorStore:
- Idempotency and deduplication
- Bounded dimension and numerical validation
- Durable snapshot persistence and crash recovery
- Per-KB isolation
- Document deletion and replacement
"""

import json
import os
import shutil
import tempfile
import threading
import unittest
import numpy as np

import sys
rag_service_path = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
if rag_service_path not in sys.path:
    sys.path.insert(0, rag_service_path)

from app.services.vector_store import (
    VectorStore,
    ValidationException,
    DimensionMismatchException,
    CorruptedIndexException,
    PersistenceException
)


class TestVectorStore(unittest.TestCase):

    def setUp(self):
        self.test_dir = tempfile.mkdtemp(prefix="omragul_test_vectorstore_")
        self.dimension = 4
        self.store = VectorStore(dimension=self.dimension, storage_dir=self.test_dir)

    def tearDown(self):
        shutil.rmtree(self.test_dir, ignore_errors=True)

    # -------------------------------------------------------------
    # 1. Validation Tests
    # -------------------------------------------------------------
    def test_validation_dimension_mismatch(self):
        """Vectors with incorrect dimensions must raise DimensionMismatchException."""
        embs = [[1.0, 0.0, 0.0]]  # Dimension 3 instead of 4
        docs = [{"document_id": "doc1", "text": "chunk 1"}]
        with self.assertRaises(DimensionMismatchException):
            self.store.add(embs, docs, knowledge_base_id="kb-1")

    def test_validation_nan_infinity(self):
        """Vectors containing NaN or infinity must raise ValidationException."""
        embs_nan = [[1.0, float("nan"), 0.0, 0.0]]
        docs = [{"document_id": "doc1", "text": "chunk 1"}]
        with self.assertRaises(ValidationException):
            self.store.add(embs_nan, docs, knowledge_base_id="kb-1")

        embs_inf = [[1.0, float("inf"), 0.0, 0.0]]
        with self.assertRaises(ValidationException):
            self.store.add(embs_inf, docs, knowledge_base_id="kb-1")

    def test_validation_count_mismatch(self):
        """Mismatched embedding and document counts must raise ValidationException."""
        embs = [[1.0, 0.0, 0.0, 0.0], [0.0, 1.0, 0.0, 0.0]]
        docs = [{"document_id": "doc1", "text": "chunk 1"}]  # 2 embs, 1 doc
        with self.assertRaises(ValidationException):
            self.store.add(embs, docs, knowledge_base_id="kb-1")

    def test_validation_empty_inputs(self):
        """Empty vectors or empty documents must raise ValidationException."""
        with self.assertRaises(ValidationException):
            self.store.add([], [], knowledge_base_id="kb-1")

    # -------------------------------------------------------------
    # 2. Idempotency & Deduplication Tests
    # -------------------------------------------------------------
    def test_idempotent_indexing_same_content(self):
        """Re-indexing document D1 with same chunks must not duplicate vectors."""
        embs_d1 = [
            [1.0, 0.0, 0.0, 0.0],
            [0.0, 1.0, 0.0, 0.0],
            [0.0, 0.0, 1.0, 0.0]
        ]
        docs_d1 = [
            {"document_id": "doc-1", "chunk_index": 0, "text": "A"},
            {"document_id": "doc-1", "chunk_index": 1, "text": "B"},
            {"document_id": "doc-1", "chunk_index": 2, "text": "C"}
        ]

        # First indexing: 3 vectors
        self.store.add(embs_d1, docs_d1, knowledge_base_id="kb-1")
        self.assertEqual(self.store.count("kb-1"), 3)

        # Second indexing of D1: must replace, remaining count 3
        self.store.add(embs_d1, docs_d1, knowledge_base_id="kb-1")
        self.assertEqual(self.store.count("kb-1"), 3)

    def test_idempotent_indexing_different_chunk_count(self):
        """Re-indexing D1 with different chunk count replaces old chunks completely."""
        embs_d1_3 = [
            [1.0, 0.0, 0.0, 0.0],
            [0.0, 1.0, 0.0, 0.0],
            [0.0, 0.0, 1.0, 0.0]
        ]
        docs_d1_3 = [
            {"document_id": "doc-1", "chunk_index": 0, "text": "A"},
            {"document_id": "doc-1", "chunk_index": 1, "text": "B"},
            {"document_id": "doc-1", "chunk_index": 2, "text": "C"}
        ]
        self.store.add(embs_d1_3, docs_d1_3, knowledge_base_id="kb-1")
        self.assertEqual(self.store.count("kb-1"), 3)

        # Re-index D1 with 5 chunks
        embs_d1_5 = [
            [1.0, 0.0, 0.0, 0.0],
            [0.0, 1.0, 0.0, 0.0],
            [0.0, 0.0, 1.0, 0.0],
            [0.5, 0.5, 0.0, 0.0],
            [0.0, 0.5, 0.5, 0.0]
        ]
        docs_d1_5 = [
            {"document_id": "doc-1", "chunk_index": i, "text": f"New {i}"}
            for i in range(5)
        ]
        self.store.add(embs_d1_5, docs_d1_5, knowledge_base_id="kb-1")
        self.assertEqual(self.store.count("kb-1"), 5)

        # Check other documents unaffected
        embs_d2 = [[0.0, 0.0, 0.0, 1.0]]
        docs_d2 = [{"document_id": "doc-2", "chunk_index": 0, "text": "Doc 2"}]
        self.store.add(embs_d2, docs_d2, knowledge_base_id="kb-1")
        self.assertEqual(self.store.count("kb-1"), 6)

        # Re-index D1 back to 2 chunks: D2 must remain intact
        embs_d1_2 = [[1.0, 0.0, 0.0, 0.0], [0.0, 1.0, 0.0, 0.0]]
        docs_d1_2 = [{"document_id": "doc-1", "chunk_index": 0, "text": "V3-A"},
                     {"document_id": "doc-1", "chunk_index": 1, "text": "V3-B"}]
        self.store.add(embs_d1_2, docs_d1_2, knowledge_base_id="kb-1")
        self.assertEqual(self.store.count("kb-1"), 3)  # 2 for D1 + 1 for D2

    # -------------------------------------------------------------
    # 3. Knowledge Base Isolation Tests
    # -------------------------------------------------------------
    def test_knowledge_base_isolation(self):
        """Vectors in KB-A must never appear in KB-B searches."""
        # KB-A: vectors pointing along X-axis
        embs_a = [[1.0, 0.0, 0.0, 0.0]]
        docs_a = [{"document_id": "doc-A", "text": "Secret project A", "knowledge_base_id": "kb-A"}]
        self.store.add(embs_a, docs_a, knowledge_base_id="kb-A")

        # KB-B: vectors pointing along Y-axis
        embs_b = [[0.0, 1.0, 0.0, 0.0]]
        docs_b = [{"document_id": "doc-B", "text": "Secret project B", "knowledge_base_id": "kb-B"}]
        self.store.add(embs_b, docs_b, knowledge_base_id="kb-B")

        self.assertEqual(self.store.count("kb-A"), 1)
        self.assertEqual(self.store.count("kb-B"), 1)

        # Search KB-A for vector [1, 0, 0, 0]
        results_a = self.store.search(query_embedding=[1.0, 0.0, 0.0, 0.0], knowledge_base_id="kb-A", top_k=5)
        self.assertEqual(len(results_a), 1)
        self.assertEqual(results_a[0]["document"]["document_id"], "doc-A")

        # Search KB-B for vector [1, 0, 0, 0]: KB-B has only doc-B
        results_b = self.store.search(query_embedding=[1.0, 0.0, 0.0, 0.0], knowledge_base_id="kb-B", top_k=5)
        self.assertEqual(len(results_b), 1)
        self.assertEqual(results_b[0]["document"]["document_id"], "doc-B")
        # Ensure doc-A NEVER leaked into KB-B
        self.assertNotIn("doc-A", [r["document"]["document_id"] for r in results_b])

    # -------------------------------------------------------------
    # 4. Persistence & Crash Recovery Tests
    # -------------------------------------------------------------
    def test_persistence_save_and_reload(self):
        """Persisted snapshot can be loaded by a new VectorStore instance."""
        embs = [[1.0, 0.0, 0.0, 0.0], [0.0, 1.0, 0.0, 0.0]]
        docs = [
            {"document_id": "doc-1", "chunk_index": 0, "text": "First"},
            {"document_id": "doc-1", "chunk_index": 1, "text": "Second"}
        ]
        self.store.add(embs, docs, knowledge_base_id="kb-persist")
        self.assertEqual(self.store.count("kb-persist"), 2)

        # Instantiate brand new VectorStore pointing to same storage_dir
        reloaded_store = VectorStore(dimension=self.dimension, storage_dir=self.test_dir)
        self.assertEqual(reloaded_store.count("kb-persist"), 2)

        results = reloaded_store.search(
            query_embedding=[1.0, 0.0, 0.0, 0.0],
            knowledge_base_id="kb-persist",
            top_k=2
        )
        self.assertEqual(len(results), 2)
        self.assertEqual(results[0]["document"]["text"], "First")

    def test_crash_recovery_corrupted_active_generation(self):
        """When active snapshot is corrupt, recovery falls back to previous valid snapshot."""
        # Generation 1
        self.store.add([[1.0, 0.0, 0.0, 0.0]], [{"document_id": "d1", "text": "Gen1"}], knowledge_base_id="kb-rec")
        self.assertEqual(self.store.count("kb-rec"), 1)

        # Generation 2
        self.store.add([[0.0, 1.0, 0.0, 0.0]], [{"document_id": "d2", "text": "Gen2"}], knowledge_base_id="kb-rec")
        self.assertEqual(self.store.count("kb-rec"), 2)

        # Corrupt Generation 2 documents.json
        kb_dir = os.path.join(self.test_dir, "kb-rec")
        with open(os.path.join(kb_dir, "active_generation.json"), "r") as f:
            pointer = json.load(f)
        active_gen_dir = os.path.join(kb_dir, "snapshots", pointer["active_generation"])

        with open(os.path.join(active_gen_dir, "documents.json"), "w") as f:
            f.write("CORRUPTED_JSON_DATA{{{{")

        # Reload store: must automatically recover Gen 1!
        recovered_store = VectorStore(dimension=self.dimension, storage_dir=self.test_dir)
        self.assertEqual(recovered_store.count("kb-rec"), 1)
        self.assertEqual(recovered_store.partitions["kb-rec"].documents[0]["document_id"], "d1")

    # -------------------------------------------------------------
    # 5. Deletion & Replacement Tests
    # -------------------------------------------------------------
    def test_document_deletion(self):
        """Deleting a document removes all its chunks from the vector store."""
        embs = [
            [1.0, 0.0, 0.0, 0.0],
            [0.0, 1.0, 0.0, 0.0],
            [0.0, 0.0, 1.0, 0.0]
        ]
        docs = [
            {"document_id": "doc-del", "chunk_index": 0, "text": "Del-1"},
            {"document_id": "doc-del", "chunk_index": 1, "text": "Del-2"},
            {"document_id": "doc-keep", "chunk_index": 0, "text": "Keep"}
        ]
        self.store.add(embs, docs, knowledge_base_id="kb-del")
        self.assertEqual(self.store.count("kb-del"), 3)

        deleted = self.store.delete_document("doc-del", knowledge_base_id="kb-del")
        self.assertEqual(deleted, 2)
        self.assertEqual(self.store.count("kb-del"), 1)
        self.assertEqual(self.store.partitions["kb-del"].documents[0]["document_id"], "doc-keep")

    def test_knowledge_base_purge(self):
        """Purging a knowledge base removes the partition and deletes files."""
        self.store.add([[1.0, 0.0, 0.0, 0.0]], [{"document_id": "d1", "text": "T"}], knowledge_base_id="kb-purge")
        self.assertEqual(self.store.count("kb-purge"), 1)

        purged = self.store.delete_knowledge_base("kb-purge")
        self.assertTrue(purged)
        self.assertEqual(self.store.count("kb-purge"), 0)
        self.assertFalse(os.path.exists(os.path.join(self.test_dir, "kb-purge")))

    # -------------------------------------------------------------
    # 6. Concurrency Tests
    # -------------------------------------------------------------
    def test_concurrent_indexing_same_kb(self):
        """Simultaneous indexing operations on same KB do not produce duplicate corruption."""
        errors = []

        def worker(doc_idx):
            try:
                embs = [[float(doc_idx), 1.0, 0.0, 0.0]]
                docs = [{"document_id": f"doc-{doc_idx}", "chunk_index": 0, "text": f"Text {doc_idx}"}]
                self.store.add(embs, docs, knowledge_base_id="kb-concurrent")
            except Exception as e:
                errors.append(e)

        threads = [threading.Thread(target=worker, args=(i,)) for i in range(10)]
        for t in threads:
            t.start()
        for t in threads:
            t.join()

        self.assertEqual(len(errors), 0)
        self.assertEqual(self.store.count("kb-concurrent"), 10)

    def test_concurrent_reindexing_same_document(self):
        """Concurrent updates to the SAME document must not produce duplicate chunks."""
        errors = []

        def worker(thread_id):
            try:
                embs = [
                    [1.0, 0.0, 0.0, 0.0],
                    [0.0, 1.0, 0.0, 0.0],
                    [0.0, 0.0, 1.0, 0.0]
                ]
                docs = [
                    {"document_id": "doc-shared", "chunk_index": 0, "text": f"T{thread_id}-A"},
                    {"document_id": "doc-shared", "chunk_index": 1, "text": f"T{thread_id}-B"},
                    {"document_id": "doc-shared", "chunk_index": 2, "text": f"T{thread_id}-C"}
                ]
                self.store.add(embs, docs, knowledge_base_id="kb-concurrent-same")
            except Exception as e:
                errors.append(e)

        threads = [threading.Thread(target=worker, args=(i,)) for i in range(5)]
        for t in threads:
            t.start()
        for t in threads:
            t.join()

        self.assertEqual(len(errors), 0)
        # Exactly 3 chunks, not 15
        self.assertEqual(self.store.count("kb-concurrent-same"), 3)
        self.assertEqual(len(self.store.partitions["kb-concurrent-same"].vectors), 3)

    # -------------------------------------------------------------
    # 7. Legacy Quarantine & Strict Scoping Tests (DEFECT-3.6-02-6 & 7)
    # -------------------------------------------------------------
    def test_indexing_missing_or_default_kb_id_rejected(self):
        """Attempting to index with empty or default KB ID raises ValidationException."""
        embs = [[1.0, 0.0, 0.0, 0.0]]
        docs = [{"document_id": "d1", "text": "chunk"}]

        with self.assertRaises(ValidationException):
            self.store.add(embs, docs, knowledge_base_id="")

        with self.assertRaises(ValidationException):
            self.store.add(embs, docs, knowledge_base_id="default")

        with self.assertRaises(ValidationException):
            self.store.add(embs, docs, knowledge_base_id="null")

    def test_quarantine_legacy_index_missing_kb(self):
        """Legacy index with missing KB ownership is quarantined, not assigned to default."""
        legacy_dir = tempfile.mkdtemp(prefix="omragul_test_legacy_")
        try:
            legacy_docs = [
                {"document_id": "leg-1", "text": "Missing KB chunk"}  # No knowledge_base_id
            ]
            with open(os.path.join(legacy_dir, "documents.json"), "w", encoding="utf-8") as f:
                json.dump(legacy_docs, f)

            # Initialize store over legacy directory
            store = VectorStore(dimension=self.dimension, storage_dir=legacy_dir)

            # Assert default partition was NOT created
            self.assertNotIn("default", store.partitions)
            self.assertEqual(store.count(), 0)

            # Assert quarantine directory was created
            quarantine_dirs = [d for d in os.listdir(legacy_dir) if d.startswith(".quarantine_")]
            self.assertEqual(len(quarantine_dirs), 1)
            quarantine_path = os.path.join(legacy_dir, quarantine_dirs[0])
            self.assertTrue(os.path.exists(os.path.join(quarantine_path, "documents.json")))
        finally:
            shutil.rmtree(legacy_dir, ignore_errors=True)


if __name__ == "__main__":
    unittest.main()
