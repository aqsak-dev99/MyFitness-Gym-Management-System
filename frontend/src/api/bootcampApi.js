import { apiClient } from './client';

/**
 * createOrUpdateClass reuses the SAME real endpoint for both create and
 * edit — POST /api/bootcamp-classes genuinely upserts on classId (see
 * SqliteBootcampRepository's ON CONFLICT DO UPDATE), confirmed by
 * direct inspection before building on it.
 */
export const bootcampApi = {
  getAllClasses: () => apiClient.get('/api/bootcamp-classes'),
  getClassById: (classId) => apiClient.get(`/api/bootcamp-classes/${classId}`),
  createOrUpdateClass: (classData) => apiClient.post('/api/bootcamp-classes', classData),
  cancelClass: (classId) => apiClient.patch(`/api/bootcamp-classes/${classId}/cancel`),
  reactivateClass: (classId) => apiClient.patch(`/api/bootcamp-classes/${classId}/reactivate`),
  enrolMember: (classId, memberId) =>
    apiClient.post(`/api/bootcamp-classes/${classId}/enrolments`, { memberId }),
  removeEnrolment: (classId, memberId) =>
    apiClient.delete(`/api/bootcamp-classes/${classId}/enrolments/${memberId}`),
  assignInstructor: (classId, instructorId) =>
    apiClient.post(`/api/bootcamp-classes/${classId}/instructor`, { instructorId }),
  removeInstructor: (classId, instructorId) =>
    apiClient.delete(`/api/bootcamp-classes/${classId}/instructor/${instructorId}`),
};