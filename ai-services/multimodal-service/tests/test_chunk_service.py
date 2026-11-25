"""
Unit tests for ChunkService in multimodal-service.
Uses standard library unittest.
"""

import sys
import os
import unittest

multimodal_service_path = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
if multimodal_service_path not in sys.path:
    sys.path.insert(0, multimodal_service_path)

from app.services.chunk_service import ChunkService


class TestChunkService(unittest.TestCase):

    def test_chunk_normal_text(self):
        """Standard text chunking should produce expected number of overlapping chunks."""
        text = "A" * 2500  # 2500 characters
        # chunk_size=1000, chunk_overlap=200 -> stride = 800
        # start positions: 0, 800, 1600, 2400 (4 chunks)
        chunks = ChunkService.create_chunks(text, chunk_size=1000, chunk_overlap=200)

        self.assertEqual(len(chunks), 4)
        self.assertEqual(len(chunks[0]), 1000)
        self.assertEqual(len(chunks[1]), 1000)
        self.assertEqual(len(chunks[2]), 900)
        self.assertEqual(len(chunks[3]), 100)

    def test_chunk_empty_text(self):
        """Empty or whitespace text should return an empty list."""
        self.assertEqual(ChunkService.create_chunks(""), [])
        self.assertEqual(ChunkService.create_chunks("   "), [])
        self.assertEqual(ChunkService.create_chunks(None), [])

    def test_chunk_invalid_parameters(self):
        """chunk_overlap >= chunk_size should raise ValueError."""
        with self.assertRaises(ValueError):
            ChunkService.create_chunks("Valid text", chunk_size=500, chunk_overlap=500)

        with self.assertRaises(ValueError):
            ChunkService.create_chunks("Valid text", chunk_size=500, chunk_overlap=600)

    def test_chunk_small_text(self):
        """Text smaller than chunk_size should produce a single chunk."""
        text = "Short text passage."
        chunks = ChunkService.create_chunks(text, chunk_size=1000, chunk_overlap=200)
        self.assertEqual(len(chunks), 1)
        self.assertEqual(chunks[0], text)


if __name__ == "__main__":
    unittest.main()
