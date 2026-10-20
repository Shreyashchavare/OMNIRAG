import { apiClient } from '../client';

export const documentApi = {
  /**
   * Create document record in Knowledge Service
   * @param {Object} documentData DocumentRequest
   */
  async createDocument(documentData) {
    const response = await apiClient.post('/api/v1/documents', documentData);
    return response.data;
  },

  /**
   * Get document by UUID
   * @param {string} id UUID
   */
  async getDocumentById(id) {
    const response = await apiClient.get(`/api/v1/documents/${id}`);
    return response.data;
  },

  /**
   * Get all documents in a knowledge base
   * @param {string} knowledgeBaseId UUID
   */
  async getDocumentsByKnowledgeBase(knowledgeBaseId) {
    const response = await apiClient.get(`/api/v1/documents/knowledge-base/${knowledgeBaseId}`);
    return response.data;
  },

  /**
   * Get paginated documents in a knowledge base
   * @param {string} knowledgeBaseId UUID
   * @param {number} page
   * @param {number} size
   * @param {string} sort e.g. "createdAt,desc"
   */
  async getDocumentsByKnowledgeBasePaged(knowledgeBaseId, page = 0, size = 20, sort = 'createdAt,desc') {
    const response = await apiClient.get(`/api/v1/documents/knowledge-base/${knowledgeBaseId}/page`, {
      params: { page, size, sort },
    });
    return response.data;
  },

  /**
   * Get documents filtered by status (ACTIVE, ARCHIVED, DELETED)
   * @param {string} knowledgeBaseId UUID
   * @param {string} status
   */
  async getDocumentsByStatus(knowledgeBaseId, status) {
    const response = await apiClient.get(`/api/v1/documents/knowledge-base/${knowledgeBaseId}/status/${status}`);
    return response.data;
  },

  /**
   * Get documents filtered by processing status (PENDING, PROCESSING, COMPLETED, FAILED)
   * @param {string} knowledgeBaseId UUID
   * @param {string} processingStatus
   */
  async getDocumentsByProcessingStatus(knowledgeBaseId, processingStatus) {
    const response = await apiClient.get(
      `/api/v1/documents/knowledge-base/${knowledgeBaseId}/processing-status/${processingStatus}`
    );
    return response.data;
  },

  /**
   * Update document metadata
   * @param {string} id UUID
   * @param {Object} updateData DocumentUpdateRequest
   */
  async updateDocument(id, updateData) {
    const response = await apiClient.put(`/api/v1/documents/${id}`, updateData);
    return response.data;
  },

  /**
   * Archive document
   * @param {string} id UUID
   */
  async archiveDocument(id) {
    const response = await apiClient.patch(`/api/v1/documents/${id}/archive`);
    return response.data;
  },

  /**
   * Restore document
   * @param {string} id UUID
   */
  async restoreDocument(id) {
    const response = await apiClient.patch(`/api/v1/documents/${id}/restore`);
    return response.data;
  },

  /**
   * Soft-delete document
   * @param {string} id UUID
   */
  async deleteDocument(id) {
    const response = await apiClient.delete(`/api/v1/documents/${id}`);
    return response.data;
  },

  /**
   * List knowledge bases owned by current user
   */
  async getKnowledgeBases() {
    const response = await apiClient.get('/knowledge-bases');
    return response.data;
  },

  /**
   * Get knowledge base by ID
   * @param {string} id UUID
   */
  async getKnowledgeBaseById(id) {
    const response = await apiClient.get(`/knowledge-bases/${id}`);
    return response.data;
  },

  /**
   * Get knowledge base statistics
   * @param {string} id UUID
   */
  async getKnowledgeBaseStats(id) {
    const response = await apiClient.get(`/knowledge-bases/${id}/statistics`);
    return response.data;
  },

  /**
   * Create a new knowledge base
   * @param {Object} data { name, description, slug }
   */
  async createKnowledgeBase(data) {
    const response = await apiClient.post('/knowledge-bases', data);
    return response.data;
  },
};
