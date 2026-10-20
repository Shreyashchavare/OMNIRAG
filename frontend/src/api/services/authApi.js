import { apiClient } from '../client';

export const authApi = {
  /**
   * User registration
   * @param {Object} data { username, email, password, profile: { firstName, lastName, phoneNumber } }
   * @returns {Promise<{ message: string, user: Object }>}
   */
  async signup(data) {
    const response = await apiClient.post('/api/v1/auth/signup', data);
    return response.data;
  },

  /**
   * User login
   * @param {Object} credentials { identifier: string (username or email), password: string }
   * @returns {Promise<{ accessToken: string, refreshToken: string, tokenType: string, expiresIn: number, user: Object }>}
   */
  async login(credentials) {
    const response = await apiClient.post('/api/v1/auth/login', credentials);
    return response.data;
  },

  /**
   * Refresh access token
   * @param {string} refreshToken
   * @returns {Promise<{ accessToken: string, refreshToken: string, tokenType: string, expiresIn: number }>}
   */
  async refreshToken(refreshToken) {
    const response = await apiClient.post('/api/v1/auth/refresh', { refreshToken });
    return response.data;
  },

  /**
   * Logout single session
   * @param {string} refreshToken
   */
  async logout(refreshToken) {
    const response = await apiClient.post('/api/v1/auth/logout', { refreshToken });
    return response.data;
  },

  /**
   * Logout all sessions
   */
  async logoutAll() {
    const response = await apiClient.post('/api/v1/auth/logout-all');
    return response.data;
  },

  /**
   * Gateway health check
   */
  async checkHealth() {
    const response = await apiClient.get('/health');
    return response.data;
  },
};
