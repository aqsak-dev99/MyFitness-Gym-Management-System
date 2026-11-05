import { apiClient } from './client';

/**
 * Matches AuthController's real request/response shapes exactly:
 * both register and login return { token, user } — register auto-
 * issues a token too, no separate login step required after signing up.
 */
export const authApi = {
  // No role is sent: the server always creates public sign-ups as MEMBER.
  register: ({ username, password, linkedMemberId }) =>
    apiClient.post('/api/auth/register', { username, password, linkedMemberId }),

  login: ({ username, password }) =>
    apiClient.post('/api/auth/login', { username, password }),
};