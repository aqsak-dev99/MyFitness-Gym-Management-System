import { apiClient } from './client';

/**
 * memberApi.js — matches MemberController's real endpoints exactly.
 * getMyProfile() calls GET /api/members/me, added specifically because
 * MEMBER-role users had no other way to fetch their own profile.
 * update/deactivate/reactivate/getById are the real Admin CRUD
 * endpoints added for full member management.
 */
export const memberApi = {
  getMyProfile: () => apiClient.get('/api/members/me'),
  updateMyGoal: (memberId, fitnessGoal) =>
    apiClient.patch(`/api/members/${memberId}/goal`, { fitnessGoal }),
  getAll: () => apiClient.get('/api/members'),
  getById: (memberId) => apiClient.get(`/api/members/${memberId}`),
  register: (member) => apiClient.post('/api/members', member),
  update: (memberId, { name, email, phone }) =>
    apiClient.patch(`/api/members/${memberId}`, { name, email, phone }),
  deactivate: (memberId) => apiClient.patch(`/api/members/${memberId}/deactivate`),
  reactivate: (memberId) => apiClient.patch(`/api/members/${memberId}/reactivate`),

  // Membership management — real, existing endpoints (assign/freeze/
  // unfreeze already worked; remove was added to close a genuine gap).
  getMembership: (memberId) => apiClient.get(`/api/members/${memberId}/membership`),
  assignMembership: (memberId, payload) =>
    apiClient.post(`/api/members/${memberId}/membership`, payload),
  freezeMembership: (memberId) => apiClient.post(`/api/members/${memberId}/membership/freeze`),
  unfreezeMembership: (memberId) => apiClient.post(`/api/members/${memberId}/membership/unfreeze`),
  removeMembership: (memberId) => apiClient.delete(`/api/members/${memberId}/membership`),

  // Payments. payMembership is the member's own simulated "Pay now"
  // (ownership-checked on the server: a member can only pay their own
  // membership). recordMembershipPayment is the ADMIN action that
  // records a payment on a member's behalf. Both return the updated
  // membership.
  payMembership: (memberId) => apiClient.post(`/api/members/${memberId}/membership/pay`),
  recordMembershipPayment: (memberId) =>
    apiClient.post(`/api/members/${memberId}/membership/payment`),
};
