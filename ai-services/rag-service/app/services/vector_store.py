import json
import os
import faiss
import numpy as np


class VectorStore:

    def __init__(self, dimension: int, storage_dir: str = "faiss/indexes"):
        self.dimension = dimension
        self.storage_dir = storage_dir
        self.index_file = os.path.join(storage_dir, "index.faiss")
        self.docs_file = os.path.join(storage_dir, "documents.json")

        self.documents = []
        if os.path.exists(self.index_file) and os.path.exists(self.docs_file):
            try:
                self.index = faiss.read_index(self.index_file)
                with open(self.docs_file, "r", encoding="utf-8") as f:
                    self.documents = json.load(f)
            except Exception:
                self.index = faiss.IndexFlatIP(dimension)
                self.documents = []
        else:
            self.index = faiss.IndexFlatIP(dimension)

    def _save(self):
        try:
            os.makedirs(self.storage_dir, exist_ok=True)
            faiss.write_index(self.index, self.index_file)
            with open(self.docs_file, "w", encoding="utf-8") as f:
                json.dump(self.documents, f, ensure_ascii=False)
        except Exception:
            pass

    def add(
        self,
        embeddings: list[list[float]],
        documents: list[dict]
    ):
        vectors = np.array(
            embeddings,
            dtype="float32"
        )

        faiss.normalize_L2(vectors)

        self.index.add(vectors)

        self.documents.extend(documents)
        self._save()

    def search(
        self,
        query_embedding: list[float],
        top_k: int = 5
    ):

        query_vector = np.array(
            [query_embedding],
            dtype="float32"
        )

        faiss.normalize_L2(query_vector)

        scores, indices = self.index.search(
            query_vector,
            top_k
        )

        results = []

        for score, index in zip(
            scores[0],
            indices[0]
        ):

            if index == -1:
                continue

            results.append({
                "score": float(score),
                "document": self.documents[index]
            })

        return results

    def count(self) -> int:
        return self.index.ntotal