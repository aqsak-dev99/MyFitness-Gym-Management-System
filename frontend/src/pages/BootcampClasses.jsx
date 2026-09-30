import { useEffect, useState } from 'react';
import { useAuth } from '../auth/AuthContext';
import { bootcampApi } from '../api/bootcampApi';
import { ApiError } from '../api/client';

import PageShell from '../components/layout/PageShell';
import LoadingSpinner from '../components/LoadingSpinner';
import ErrorMessage from '../components/ErrorMessage';
import GlassCard from '../components/dashboard/GlassCard';
import { CalendarIcon } from '../components/dashboard/Icons';
import styles from './BootcampClasses.module.css';

/**
 * The real per-class fee isn't a separate JSON field — it only exists
 * inside BootcampClass's formatted `details` text (calcBootcampFee()/
 * applyDiscount() are parameterized methods, not plain getters, so
 * Jackson never serializes them directly — confirmed by direct
 * inspection). This extracts the real, server-computed base fee from
 * that real text rather than inventing or recomputing a value
 * client-side, which would duplicate business logic that belongs on
 * the backend. Falls back to null (rendered as nothing) rather than a
 * fabricated number if the format ever changes.
 */
function extractBaseFee(details) {
  const match = details?.match(/Fee \(1 class\)\s*:\s*£([\d.]+)/);
  return match ? match[1] : null;
}

export default function BootcampClasses() {
  const { user } = useAuth();
  const isAdmin = user.role === 'ADMIN';

  const [classes, setClasses] = useState([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState('');

  // ADMIN-only inline enrolment state — which class's form is open,
  // the memberId being entered, and any in-flight/result state per class.
  const [enrolFormOpenFor, setEnrolFormOpenFor] = useState(null);
  const [memberIdDraft, setMemberIdDraft] = useState('');
  const [enrolStatus, setEnrolStatus] = useState({});

  useEffect(() => {
    loadClasses();
  }, []);

  async function loadClasses() {
    setIsLoading(true);
    setError('');
    try {
      const data = await bootcampApi.getAllClasses();
      setClasses(data);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not load bootcamp classes.');
    } finally {
      setIsLoading(false);
    }
  }

  async function handleEnrol(classId) {
    if (!memberIdDraft.trim()) return;
    setEnrolStatus((s) => ({ ...s, [classId]: { loading: true } }));
    try {
      await bootcampApi.enrolMember(classId, memberIdDraft.trim());
      setEnrolStatus((s) => ({ ...s, [classId]: { success: true } }));
      setMemberIdDraft('');
      setEnrolFormOpenFor(null);
      loadClasses(); // refresh real enrolment counts
    } catch (err) {
      const message = err instanceof ApiError ? err.message : 'Enrolment failed.';
      setEnrolStatus((s) => ({ ...s, [classId]: { error: message } }));
    }
  }

  return (
    <PageShell>
      <h1 className={styles.title}>Bootcamp Classes</h1>
      <p className={styles.subtitle}>
        {isAdmin
          ? 'Browse all classes and enrol members.'
          : "Browse what's available — enrolment is handled by gym staff."}
      </p>

      {isLoading ? (
        <LoadingSpinner label="Loading classes…" />
      ) : error ? (
        <ErrorMessage>{error}</ErrorMessage>
      ) : classes.length === 0 ? (
        <GlassCard>
          <p className={styles.empty}>No bootcamp classes are available right now.</p>
        </GlassCard>
      ) : (
        <div className={styles.grid}>
          {classes.map((bc) => {
            const baseFee = extractBaseFee(bc.details);
            const status = enrolStatus[bc.classId];

            return (
              <GlassCard key={bc.classId} className={styles.classCard}>
                <div className={styles.cardHeader}>
                  <div className={styles.badgeIcon}>
                    <CalendarIcon size={18} color="var(--dash-accent-blue)" />
                  </div>
                  <div>
                    <div className={styles.className}>{bc.className}</div>
                    <div className={styles.schedule}>{bc.schedule}</div>
                  </div>
                </div>

                <div className={styles.metaRow}>
                  <span className={`${styles.capacityBadge} ${bc.full ? styles.full : ''}`}>
                    {bc.currentEnrolments}/{bc.maxCapacity} enrolled{bc.full ? ' · Full' : ''}
                  </span>
                  {baseFee && <span className={styles.feeBadge}>From £{baseFee}</span>}
                </div>

                {isAdmin && (
                  <div className={styles.enrolArea}>
                    {enrolFormOpenFor === bc.classId ? (
                      <div className={styles.enrolForm}>
                        <input
                          className={styles.enrolInput}
                          placeholder="Member ID (e.g. M100)"
                          value={memberIdDraft}
                          onChange={(e) => setMemberIdDraft(e.target.value)}
                        />
                        <button
                          className={styles.enrolSubmit}
                          onClick={() => handleEnrol(bc.classId)}
                          disabled={status?.loading}
                        >
                          {status?.loading ? '…' : 'Enrol'}
                        </button>
                        <button className={styles.enrolCancel} onClick={() => setEnrolFormOpenFor(null)}>
                          Cancel
                        </button>
                      </div>
                    ) : (
                      <button
                        className={styles.enrolTrigger}
                        onClick={() => { setEnrolFormOpenFor(bc.classId); setMemberIdDraft(''); }}
                        disabled={bc.full}
                      >
                        {bc.full ? 'Class full' : 'Enrol a member'}
                      </button>
                    )}
                    {status?.error && <p className={styles.enrolError}>{status.error}</p>}
                    {status?.success && <p className={styles.enrolSuccess}>Enrolled successfully.</p>}
                  </div>
                )}
              </GlassCard>
            );
          })}
        </div>
      )}
    </PageShell>
  );
}