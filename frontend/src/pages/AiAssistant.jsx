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
  { id: 'documents', label: 'Documents' },
  { id: 'bootcamps', label: 'Bootcamp Classes' },
];

/**
 * Three real, distinct backend capabilities, kept visibly separate
 * rather than merged into one fake "chat" that pretends they're the
 * same thing:
 *  - General: POST /api/ai/ask — plain, contextless
 *  - Documents: POST /api/documents/{id}/ask — the real RAG pipeline,
 *    with genuine citations and confidence-based refusal
 *  - Bootcamp Classes: POST /api/ai/bootcamp-classes/ask — real tool
 *    calling, never wired to any UI before this page
 */
export default function AiAssistant() {
  const [mode, setMode] = useState('general');
  const [question, setQuestion] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState('');
  const [result, setResult] = useState(null); // { answer, citations? }

  // Documents-mode-only state
  const [documents, setDocuments] = useState([]);
  const [selectedDocId, setSelectedDocId] = useState('');
  const [docsLoading, setDocsLoading] = useState(false);
  const [docsError, setDocsError] = useState('');

  useEffect(() => {
    if (mode === 'documents' && documents.length === 0 && !docsLoading) {
      loadDocuments();
    }
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
      } else if (mode === 'bootcamps') {
        const res = await aiApi.askAboutBootcampClasses(question.trim());
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
      <h1 className={styles.title}>AI Assistant</h1>
      <p className={styles.subtitle}>Ask a general question, query uploaded documents, or ask about bootcamp classes.</p>

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
            {docsLoading ? (
              <p className={styles.docHint}>Loading documents…</p>
            ) : docsError ? (
              <p className={styles.docError}>{docsError}</p>
            ) : documents.length === 0 ? (
              <p className={styles.docHint}>No documents have been uploaded yet — an admin needs to upload one first.</p>
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
          </div>
        )}

        <form onSubmit={handleAsk} className={styles.askForm}>
          <div className={styles.inputRow}>
            <SparkleIcon size={16} color="var(--dash-accent-green)" />
            <input
              className={styles.questionInput}
              placeholder={
                mode === 'documents' ? 'Ask a question about this document…' :
                mode === 'bootcamps' ? 'Ask about available bootcamp classes…' :
                'Ask anything…'
              }
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