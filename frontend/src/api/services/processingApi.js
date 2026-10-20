import { apiClient } from '../client';

export const processingApi = {
  /**
   * Initiate document processing job
   * @param {Object} request { document_id: string, knowledge_base_id?: string, idempotency_key?: string }
   */
  async createJob(request) {
    const response = await apiClient.post('/api/v1/processing/jobs', request);
    return response.data;
  },

  /**
   * Get full details of a processing job
   * @param {string} jobId UUID
   */
  async getJobById(jobId) {
    const response = await apiClient.get(`/api/v1/processing/jobs/${jobId}`);
    return response.data;
  },

  /**
   * Get lightweight status and progress of a processing job
   * @param {string} jobId UUID
   */
  async getJobStatus(jobId) {
    const response = await apiClient.get(`/api/v1/processing/jobs/${jobId}/status`);
    return response.data;
  },

  /**
   * Get latest processing status for a document
   * @param {string} documentId UUID
   */
  async getDocumentProcessingStatus(documentId) {
    const response = await apiClient.get(`/api/v1/processing/documents/${documentId}/status`);
    return response.data;
  },

  /**
   * List paginated processing jobs
   * @param {string} [status] optional status filter (QUEUED, PROCESSING, COMPLETED, FAILED, CANCELLED)
   * @param {number} [page=0]
   * @param {number} [size=20]
   */
  async getJobs(status, page = 0, size = 20) {
    const params = { page, size };
    if (status) params.status = status;
    const response = await apiClient.get('/api/v1/processing/jobs', { params });
    return response.data;
  },

  /**
   * Retry a failed processing job
   * @param {string} jobId UUID
   */
  async retryJob(jobId) {
    const response = await apiClient.post(`/api/v1/processing/jobs/${jobId}/retry`);
    return response.data;
  },

  /**
   * Cancel an active processing job
   * @param {string} jobId UUID
   */
  async cancelJob(jobId) {
    const response = await apiClient.post(`/api/v1/processing/jobs/${jobId}/cancel`);
    return response.data;
  },
};
