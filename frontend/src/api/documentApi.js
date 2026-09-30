import { apiClient } from './client';

/** Matches DocumentController's real endpoints — the RAG pipeline. */
export const documentApi = {
  getAllDocuments: () => apiClient.get('/api/documents'),
  askAboutDocument: (documentId, question) =>
    apiClient.post(`/api/documents/${documentId}/ask`, { question }),
  uploadDocument: (filename, content, audience) =>
    apiClient.post('/api/documents', { filename, content, audience }),
};