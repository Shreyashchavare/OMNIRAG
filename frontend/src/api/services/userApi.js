import { apiClient } from '../client';

export const userApi = {
  /**
   * Get user by ID
   * @param {string} userId UUID
   */
  async getUserById(userId) {
    const response = await apiClient.get(`/api/v1/users/${userId}`);
    return response.data;
  },

  /**
   * Update user basic details (username, email)
   * @param {string} userId UUID
   * @param {Object} data { username, email }
   */
  async updateUser(userId, data) {
    const response = await apiClient.put(`/api/v1/users/${userId}`, data);
    return response.data;
  },

  /**
   * Delete user (soft-delete)
   * @param {string} userId UUID
   */
  async deleteUser(userId) {
    const response = await apiClient.delete(`/api/v1/users/${userId}`);
    return response.data;
  },

  /**
   * Restore soft-deleted user
   * @param {string} userId UUID
   */
  async restoreUser(userId) {
    const response = await apiClient.patch(`/api/v1/users/${userId}/restore`);
    return response.data;
  },

  /**
   * Lock user account
   * @param {string} userId UUID
   */
  async lockUser(userId) {
    const response = await apiClient.patch(`/api/v1/users/${userId}/lock`);
    return response.data;
  },

  /**
   * Unlock user account
   * @param {string} userId UUID
   */
  async unlockUser(userId) {
    const response = await apiClient.patch(`/api/v1/users/${userId}/unlock`);
    return response.data;
  },

  /**
   * Get user profile details
   * @param {string} userId UUID
   */
  async getUserProfile(userId) {
    const response = await apiClient.get(`/api/v1/users/${userId}/profile`);
    return response.data;
  },

  /**
   * Update user profile details
   * @param {string} userId UUID
   * @param {Object} profileData
   */
  async updateUserProfile(userId, profileData) {
    const response = await apiClient.put(`/api/v1/users/${userId}/profile`, profileData);
    return response.data;
  },

  /**
   * Get user assigned roles
   * @param {string} userId UUID
   */
  async getUserRoles(userId) {
    const response = await apiClient.get(`/api/v1/users/${userId}/roles`);
    return response.data;
  },

  /**
   * Assign role to user
   * @param {string} userId UUID
   * @param {string} roleId UUID
   */
  async assignRole(userId, roleId) {
    const response = await apiClient.post(`/api/v1/users/${userId}/roles/${roleId}`);
    return response.data;
  },

  /**
   * Revoke role from user
   * @param {string} userId UUID
   * @param {string} roleId UUID
   */
  async revokeRole(userId, roleId) {
    const response = await apiClient.delete(`/api/v1/users/${userId}/roles/${roleId}`);
    return response.data;
  },
};
