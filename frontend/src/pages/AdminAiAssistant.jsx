import { useEffect, useState } from 'react';
import { aiApi } from '../api/aiApi';
import { documentApi } from '../api/documentApi';
import { ApiError } from '../api/client';

import PageShell from '../components/layout/PageShell';
import GlassCard from '../components/dashboard/GlassCard';
import { SparkleIcon } from '../components/dashboard/Icons';
import styles from './AiAssistant.module.css';

const MODES = [
  { id: 'general', label: 'General' },
  { id: 'documents', label: 'Admin Documents' },
];

/**
 * Admin-only AI Assistant — deliberately reuses the same two modes and
 * the same real endpoints as the Member AiAssistant.jsx (POST /api/ai/ask,
 * POST /api/documents/{id}/ask), rather than building a second AI
 * implementation. The genuine difference is scope: this page only ever
 * sees audience='ADMIN' documents (enforced server-side in
 * DocumentController, not just by what this page happens to fetch —
 * a Member hitting the same endpoints gets a completely disjoint set).
 *
 * Bootcamp tool-calling and the goal-based recommendation mode from the
 * Member page are intentionally NOT here — both are member-specific
 * concepts (a member's own goal, a member's own enrolments) that don't
 * apply to an admin account with no linked member.
 *
 * Includes a real upload form — nothing in this codebase had a document
 * upload UI before this; it only ever happened via direct API calls.
 */
export default function AdminAiAssistant() {
  const [mode, setMode] = useState('general');
  const [question, setQuestion] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState('');
  const [result, setResult] = useState(null);

  const [documents, setDocuments] = useState([]);
  const [selectedDocId, setSelectedDocId] = useState('');
  const [docsLoading, setDocsLoading] = useState(false);
  const [docsError, setDocsError] = useState('');

  const [showUpload, setShowUpload] = useState(false);
  const [uploadFilename, setUploadFilename] = useState('');
  const [uploadContent, setUploadContent] = useState('');
  const [uploadError, setUploadError] = useState('');
  const [isUploading, setIsUploading] = useState(false);

  useEffect(() => {
    if (mode === 'documents') loadDocuments();
  }, [mode]);

  async function loadDocuments() {
    setDocsLoading(true);
    setDocsError('');
    try {
      const docs = await documentApi.getAllDocuments();
      setDocuments(docs);
      if (docs.length > 0) setSelectedDocId(docs[0].documentId);
    } catch (err) {
      setDocsError(err instanceof ApiError ? err.message : 'Could not load documents.');
    } finally {
      setDocsLoading(false);
    }
  }

  async function handleUpload(e) {
    e.preventDefault();
    setUploadError('');
    setIsUploading(true);
    try {
      await documentApi.uploadDocument(uploadFilename, uploadContent, 'ADMIN');
      setUploadFilename('');
      setUploadContent('');
      setShowUpload(false);
      await loadDocuments();
    } catch (err) {
      setUploadError(err instanceof ApiError ? err.message : 'Could not upload document.');
    } finally {
      setIsUploading(false);
    }
  }

  async function handleAsk(event) {
    event.preventDefault();
    if (!question.trim()) return;
    setError('');
    setResult(null);
    setIsLoading(true);
    try {
      if (mode === 'general') {
        const res = await aiApi.ask(question.trim());
        setResult({ answer: res.answer });
      } else {
        if (!selectedDocId) {
          setError('Select a document first.');
          setIsLoading(false);
          return;
        }
        const res = await documentApi.askAboutDocument(selectedDocId, question.trim());
        setResult({ answer: res.answer, citations: res.citations });
      }
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Something went wrong. Please try again.');
    } finally {
      setIsLoading(false);
    }
  }

  return (
    <PageShell>
      <h1 className={styles.title}>Admin AI Assistant</h1>
      <p className={styles.subtitle}>Ask general questions or query your Admin knowledge base — gym policies, procedures, and operations.</p>

      <div className={styles.modeTabs}>
        {MODES.map((m) => (
          <button
            key={m.id}
            className={`${styles.modeTab} ${mode === m.id ? styles.modeTabActive : ''}`}
            onClick={() => { setMode(m.id); setResult(null); setError(''); }}
          >
            {m.label}
          </button>
        ))}
      </div>

      <GlassCard>
        {mode === 'documents' && (
          <div className={styles.docSelector}>
            <div className={styles.docSelectorHeader}>
              {docsLoading ? (
                <p className={styles.docHint}>Loading documents…</p>
              ) : docsError ? (
                <p className={styles.docError}>{docsError}</p>
              ) : documents.length === 0 ? (
                <p className={styles.docHint}>No Admin documents uploaded yet.</p>
              ) : (
                <select
                  className={styles.docSelect}
                  value={selectedDocId}
                  onChange={(e) => setSelectedDocId(e.target.value)}
                >
                  {documents.map((doc) => (
                    <option key={doc.documentId} value={doc.documentId}>{doc.filename}</option>
                  ))}
                </select>
              )}
              <button type="button" onClick={() => setShowUpload((v) => !v)} className={styles.askBtn}>
                {showUpload ? 'Cancel' : '+ Upload Document'}
              </button>
            </div>

            {showUpload && (
              <form onSubmit={handleUpload} className={styles.uploadForm}>
                <input required placeholder="Filename (e.g. refund-policy.txt)" value={uploadFilename}
                  onChange={(e) => setUploadFilename(e.target.value)} />
                <textarea required placeholder="Paste the document content here…" rows={6} value={uploadContent}
                  onChange={(e) => setUploadContent(e.target.value)} />
                <button type="submit" disabled={isUploading}>{isUploading ? 'Uploading…' : 'Upload'}</button>
                {uploadError && <p className={styles.docError}>{uploadError}</p>}
              </form>
            )}
          </div>
        )}

        <form onSubmit={handleAsk} className={styles.askForm}>
          <div className={styles.inputRow}>
            <SparkleIcon size={16} color="var(--color-primary)" />
            <input
              className={styles.questionInput}
              placeholder={mode === 'documents' ? 'Ask a question about this document…' : 'Ask anything about running the gym…'}
              value={question}
              onChange={(e) => setQuestion(e.target.value)}
              disabled={mode === 'documents' && documents.length === 0}
            />
            <button
              type="submit"
              className={styles.askBtn}
              disabled={isLoading || !question.trim() || (mode === 'documents' && documents.length === 0)}
            >
              {isLoading ? 'Thinking…' : 'Ask'}
            </button>
          </div>
        </form>

        {error && <p className={styles.error}>{error}</p>}

        {result && (
          <div className={styles.resultArea}>
            <p className={styles.answer}>{result.answer}</p>
            {result.citations && result.citations.length > 0 && (
              <div className={styles.citations}>
                <span className={styles.citationsLabel}>Sources</span>
                {result.citations.map((c) => (
                  <div key={c.chunkIndex} className={styles.citationItem}>
                    <span className={styles.citationIndex}>#{c.chunkIndex}</span> {c.excerpt}
                  </div>
                ))}
              </div>
            )}
          </div>
        )}
      </GlassCard>
    </PageShell>
  );
}