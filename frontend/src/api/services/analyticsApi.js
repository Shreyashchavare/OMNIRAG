import { apiClient } from '../client';

export const analyticsApi = {
  /**
   * Get analytics dashboard summary
   * @param {string} [from] ISO-8601 UTC timestamp
   * @param {string} [to] ISO-8601 UTC timestamp
   */
  async getDashboard(from, to) {
    const params = {};
    if (from) params.from = from;
    if (to) params.to = to;
    const response = await apiClient.get('/api/analytics/dashboard', { params });
    return response.data;
  },

  /**
   * Get fine-grained document analytics
   * @param {string} [from] ISO-8601 UTC timestamp
   * @param {string} [to] ISO-8601 UTC timestamp
   */
  async getDocumentAnalytics(from, to) {
    const params = {};
    if (from) params.from = from;
    if (to) params.to = to;
    const response = await apiClient.get('/api/analytics/documents', { params });
    return response.data;
  },

  /**
   * Get fine-grained query analytics
   * @param {string} [from] ISO-8601 UTC timestamp
   * @param {string} [to] ISO-8601 UTC timestamp
   */
  async getQueryAnalytics(from, to) {
    const params = {};
    if (from) params.from = from;
    if (to) params.to = to;
    const response = await apiClient.get('/api/analytics/queries', { params });
    return response.data;
  },

  /**
   * Get fine-grained usage & model analytics
   * @param {string} [from] ISO-8601 UTC timestamp
   * @param {string} [to] ISO-8601 UTC timestamp
   */
  async getUsageAnalytics(from, to) {
    const params = {};
    if (from) params.from = from;
    if (to) params.to = to;
    const response = await apiClient.get('/api/analytics/usage', { params });
    return response.data;
  },

  /**
   * Get fine-grained user activity analytics
   * @param {string} [from] ISO-8601 UTC timestamp
   * @param {string} [to] ISO-8601 UTC timestamp
   */
  async getUserAnalytics(from, to) {
    const params = {};
    if (from) params.from = from;
    if (to) params.to = to;
    const response = await apiClient.get('/api/analytics/users', { params });
    return response.data;
  },
};
