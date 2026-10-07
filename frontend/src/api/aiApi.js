import { apiClient } from './client';

/**
 * Three distinct real AI capabilities, kept as three distinct methods
 * rather than one generic "chat" call — that's an honest reflection of
 * what actually exists server-side: a plain contextless ask, a
 * document-grounded RAG pipeline with citations/refusal, and a genuine
 * tool-calling flow. Conflating them into one endpoint would misrepresent
 * what's actually happening on each request.
 */
export const aiApi = {
  ask: (question) => apiClient.post('/api/ai/ask', { question }),
  // Admin-only (403 for members). The model can look up live gym data -
  // revenue, overdue members, memberships, class capacity - through a fixed
  // set of read-only backend tools; it never touches the database itself.
  askAdmin: (question) => apiClient.post('/api/ai/admin/ask', { question }),
  askAboutBootcampClasses: (question) => apiClient.post('/api/ai/bootcamp-classes/ask', { question }),
  getBootcampRecommendation: (memberId) =>
    apiClient.get(`/api/ai/members/${memberId}/bootcamp-recommendation`),
};
