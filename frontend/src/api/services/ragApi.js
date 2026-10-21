import { apiClient } from '../client';

export const ragApi = {
  /**
   * Search RAG vector store
   * @param {string} query
   * @param {number} topK
   */
  async search(query, topK = 5) {
    const response = await apiClient.post('/api/v1/rag/search', {
      query,
      top_k: topK,
    });
    return response.data;
  },

  /**
   * Chat using Agent Service (grounded in RAG context + LLM response)
   * @param {string} query
   */
  async chat(query) {
    const response = await apiClient.post('/api/v1/agent/chat', {
      query,
    });
    return response.data;
  },

  /**
   * Generate query embedding
   * @param {string} query
   */
  async generateEmbedding(query) {
    const response = await apiClient.post('/api/v1/rag/query/embedding', {
      query,
    });
    return response.data;
  },

  /**
   * Check RAG service health
   */
  async health() {
    const response = await apiClient.get('/api/v1/rag/health');
    return response.data;
  },
};
