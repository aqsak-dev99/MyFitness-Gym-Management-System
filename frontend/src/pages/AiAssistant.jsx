import { useEffect, useState } from 'react';
import { useAuth } from '../auth/AuthContext';
import { aiApi } from '../api/aiApi';
import { documentApi } from '../api/documentApi';
import { ApiError } from '../api/client';

import PageShell from '../components/layout/PageShell';
import GlassCard from '../components/dashboard/GlassCard';
import { SparkleIcon } from '../components/dashboard/Icons';
import MarkdownAnswer from '../components/MarkdownAnswer';
import styles from './AiAssistant.module.css';

const MODES = [
  { id: 'general', label: 'General' },
  { id: 'documents', label: 'Documents' },
  { id: 'bootcamps', label: 'Bootcamp Classes' },
  { id: 'recommendation', label: 'My Recommendation' },
];

/**
 * Four real, distinct backend capabilities, kept visibly separate
 * rather than merged into one fake "chat":
 *  - General: POST /api/ai/ask — plain, contextless
 *  - Documents: POST /api/documents/{id}/ask — real RAG, citations + refusal
 *  - Bootcamp Classes: POST /api/ai/bootcamp-classes/ask — real tool calling
 *  - My Recommendation: GET /api/ai/members/{id}/bootcamp-recommendation —
 *    the feature that previously lived inline on the Dashboard's
 *    QuickActions; relocated here (not dropped) once Dashboard's Quick
 *    Actions needed to match the exact 4-card spec instead. Genuinely
 *    different interaction — no question input, since it's driven by
 *    the member's already-stored goal, not a typed question.
 */
export default function AiAssistant() {
  const { user } = useAuth();
  const [mode, setMode] = useState('general');
  const [question, setQuestion] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState('');
  const [result, setResult] = useState(null);

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

  async function handleGetRecommendation() {
    if (!user.linkedMemberId) {
      setError('No member profile is linked to this account, so a recommendation can\'t be generated.');
      return;
    }
    setError('');
    setResult(null);
    setIsLoading(true);
    try {
      const res = await aiApi.getBootcampRecommendation(user.linkedMemberId);
      setResult({ answer: res.recommendation });
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not get a recommendation right now.');
    } finally {
      setIsLoading(false);
    }
  }

  return (
    <PageShell>
      <h1 className={styles.title}>AI Assistant</h1>
      <p className={styles.subtitle}>Ask a general question, query uploaded documents, ask about bootcamp classes, or get a personalized recommendation.</p>

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
        {mode === 'recommendation' ? (
          <div className={styles.recommendationArea}>
            <p className={styles.recommendationHint}>
              Get a bootcamp class recommendation based on your current fitness goal and enrolments.
            </p>
            <button className={styles.askBtn} onClick={handleGetRecommendation} disabled={isLoading}>
              {isLoading ? 'Thinking…' : 'Get My Recommendation'}
            </button>
          </div>
        ) : (
          <>
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
                <SparkleIcon size={16} color="var(--color-primary)" />
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
          </>
        )}

        {error && <p className={styles.error}>{error}</p>}

        {result && (
          <div className={styles.resultArea}>
            <MarkdownAnswer text={result.answer} />
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