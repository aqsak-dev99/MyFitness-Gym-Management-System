import { apiClient } from './client';

/**
 * Matches TrainerController's real endpoints. update/deactivate/
 * reactivate are the real Admin CRUD endpoints added for full staff
 * management — deactivate/reactivate are unified (one endpoint works
 * for all three staff types, matching TrainerService's own unified
 * implementation), while add/update stay type-specific since each
 * type has genuinely different fields.
 */
export const trainerApi = {
  getAllStaff: () => apiClient.get('/api/staff'),
  getInstructors: () => apiClient.get('/api/instructors'),
  getFullTimeStaff: () => apiClient.get('/api/staff/full-time'),
  getPartTimeStaff: () => apiClient.get('/api/staff/part-time'),

  addInstructor: (staff) => apiClient.post('/api/instructors', staff),
  addFullTimeStaff: (staff) => apiClient.post('/api/staff/full-time', staff),
  addPartTimeStaff: (staff) => apiClient.post('/api/staff/part-time', staff),

  updateInstructor: (staffId, staff) => apiClient.patch(`/api/instructors/${staffId}`, staff),
  updateFullTimeStaff: (staffId, staff) => apiClient.patch(`/api/staff/full-time/${staffId}`, staff),
  updatePartTimeStaff: (staffId, staff) => apiClient.patch(`/api/staff/part-time/${staffId}`, staff),

  deactivateStaff: (staffId) => apiClient.patch(`/api/staff/${staffId}/deactivate`),
  reactivateStaff: (staffId) => apiClient.patch(`/api/staff/${staffId}/reactivate`),
};