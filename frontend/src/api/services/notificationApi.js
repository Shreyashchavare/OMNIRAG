import { apiClient } from '../client';

export const notificationApi = {
  /**
   * Get paginated notifications for authenticated user
   * @param {number} [page=0]
   * @param {number} [size=20]
   * @param {boolean} [unreadOnly=false]
   */
  async getNotifications(page = 0, size = 20, unreadOnly = false) {
    const response = await apiClient.get('/api/notifications', {
      params: { page, size, unreadOnly },
    });
    return response.data;
  },

  /**
   * Get single notification by ID
   * @param {string} notificationId UUID
   */
  async getNotificationById(notificationId) {
    const response = await apiClient.get(`/api/notifications/${notificationId}`);
    return response.data;
  },

  /**
   * Get unread notification count
   * @returns {Promise<{ unreadCount: number }>}
   */
  async getUnreadCount() {
    const response = await apiClient.get('/api/notifications/unread-count');
    return response.data;
  },

  /**
   * Mark single notification as read
   * @param {string} notificationId UUID
   */
  async markAsRead(notificationId) {
    const response = await apiClient.patch(`/api/notifications/${notificationId}/read`);
    return response.data;
  },

  /**
   * Mark all notifications as read
   * @returns {Promise<{ updatedCount: number }>}
   */
  async markAllAsRead() {
    const response = await apiClient.patch('/api/notifications/read-all');
    return response.data;
  },

  /**
   * Delete notification
   * @param {string} notificationId UUID
   */
  async deleteNotification(notificationId) {
    const response = await apiClient.delete(`/api/notifications/${notificationId}`);
    return response.data;
  },
};
