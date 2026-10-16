import faiss
import numpy as np


class VectorStore:

    def __init__(self, dimension: int):
        self.dimension = dimension

        self.index = faiss.IndexFlatIP(dimension)

        self.documents = []

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