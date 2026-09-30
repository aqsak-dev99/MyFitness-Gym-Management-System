import { useEffect, useState } from 'react';
import { bootcampApi } from '../api/bootcampApi';
import { ApiError } from '../api/client';
import { useToast } from '../toast/ToastContext';

import PageShell from '../components/layout/PageShell';
import LoadingSpinner from '../components/LoadingSpinner';
import ConfirmDialog from '../components/ConfirmDialog';
import styles from './AdminClasses.module.css';

const EMPTY_CLASS = { classId: '', type: 'FAT_BURN', schedule: '', maxCapacity: '10' };

/**
 * Full real Classes CRUD. Create and Edit deliberately share the same
 * createOrUpdateClass() call — confirmed by direct inspection that
 * POST /api/bootcamp-classes genuinely upserts on classId, so this
 * isn't a shortcut, it's using the real backend behavior correctly.
 * Cancel/Reactivate are the two real new soft-status endpoints.
 */
export default function AdminClasses() {
  const { showToast } = useToast();
  const [classes, setClasses] = useState([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState('');
  const [search, setSearch] = useState('');
  const [confirmTarget, setConfirmTarget] = useState(null); // { type: 'cancel'|'removeEnrolment', classObj, memberId? }

  const [showForm, setShowForm] = useState(false);
  const [formMode, setFormMode] = useState('create'); // 'create' | 'edit'
  const [formData, setFormData] = useState(EMPTY_CLASS);
  const [formError, setFormError] = useState('');
  const [isSaving, setIsSaving] = useState(false);

  const [enrolPanelId, setEnrolPanelId] = useState(null);
  const [enrolMemberId, setEnrolMemberId] = useState('');
  const [enrolError, setEnrolError] = useState('');
  const [enrolBusy, setEnrolBusy] = useState(false);

  useEffect(() => { loadClasses(); }, []);

  async function loadClasses() {
    setIsLoading(true);
    setError('');
    try {
      setClasses(await bootcampApi.getAllClasses());
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not load classes.');
    } finally {
      setIsLoading(false);
    }
  }

  const filtered = classes.filter((c) => {
    const q = search.toLowerCase();
    return !q || c.className.toLowerCase().includes(q) || c.classId.toLowerCase().includes(q) || c.schedule.toLowerCase().includes(q);
  });

  function openCreateForm() {
    setFormMode('create');
    setFormData(EMPTY_CLASS);
    setFormError('');
    setShowForm(true);
  }

  function openEditForm(c) {
    setFormMode('edit');
    setFormData({ classId: c.classId, type: c.type, schedule: c.schedule, maxCapacity: String(c.maxCapacity) });
    setFormError('');
    setShowForm(true);
  }

  async function handleSubmitForm(e) {
    e.preventDefault();
    setFormError('');
    setIsSaving(true);
    try {
      await bootcampApi.createOrUpdateClass({
        classId: formData.classId,
        type: formData.type,
        schedule: formData.schedule,
        maxCapacity: Number(formData.maxCapacity),
      });
      setShowForm(false);
      await loadClasses();
      showToast(formMode === 'edit' ? 'Class updated.' : 'Class created.');
    } catch (err) {
      setFormError(err instanceof ApiError ? err.message : 'Could not save class.');
    } finally {
      setIsSaving(false);
    }
  }

  // Reactivation is restorative — no confirmation needed. Cancelling a
  // class goes through confirmTarget instead (see confirmCancel).
  async function toggleCancelled(c) {
    if (!c.cancelled) {
      setConfirmTarget({ type: 'cancel', classObj: c });
      return;
    }
    try {
      await bootcampApi.reactivateClass(c.classId);
      await loadClasses();
      showToast('Class reactivated.');
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not update class status.');
      showToast('Could not reactivate class.', 'error');
    }
  }

  async function confirmCancelClass() {
    const c = confirmTarget.classObj;
    setConfirmTarget(null);
    try {
      await bootcampApi.cancelClass(c.classId);
      await loadClasses();
      showToast('Class cancelled.');
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not update class status.');
      showToast('Could not cancel class.', 'error');
    }
  }

  async function handleEnrol(classId) {
    setEnrolError('');
    setEnrolBusy(true);
    try {
      await bootcampApi.enrolMember(classId, enrolMemberId.trim());
      setEnrolMemberId('');
      await loadClasses();
      showToast('Member enrolled.');
    } catch (err) {
      setEnrolError(err instanceof ApiError ? err.message : 'Could not enrol member.');
    } finally {
      setEnrolBusy(false);
    }
  }

  function requestRemoveEnrolment(classObj, memberId) {
    setConfirmTarget({ type: 'removeEnrolment', classObj, memberId });
  }

  async function confirmRemoveEnrolment() {
    const { classObj, memberId } = confirmTarget;
    setConfirmTarget(null);
    setEnrolBusy(true);
    try {
      await bootcampApi.removeEnrolment(classObj.classId, memberId);
      await loadClasses();
      showToast('Enrolment removed.');
    } catch (err) {
      setEnrolError(err instanceof ApiError ? err.message : 'Could not remove enrolment.');
      showToast('Could not remove enrolment.', 'error');
    } finally {
      setEnrolBusy(false);
    }
  }

  return (
    <PageShell>
      <div className={styles.header}>
        <div>
          <h1 className={styles.title}>Bootcamp Classes</h1>
          <p className={styles.subtitle}>Create, edit, and manage every class and its enrolments.</p>
        </div>
        <button className={styles.addBtn} onClick={openCreateForm}>+ Create Class</button>
      </div>

      {showForm && (
        <form onSubmit={handleSubmitForm} className={styles.addForm}>
          <input required placeholder="Class ID" value={formData.classId} disabled={formMode === 'edit'}
            onChange={(e) => setFormData({ ...formData, classId: e.target.value })} />
          <select value={formData.type} onChange={(e) => setFormData({ ...formData, type: e.target.value })}>
            <option value="FAT_BURN">Fat Burn</option>
            <option value="FITNESS_AND_ENDURANCE">Fitness &amp; Endurance</option>
            <option value="FULL_BODY">Full Body</option>
          </select>
          <input required placeholder="Schedule (e.g. Mon/Wed 07:00)" value={formData.schedule}
            onChange={(e) => setFormData({ ...formData, schedule: e.target.value })} />
          <input required type="number" min="1" placeholder="Max capacity" value={formData.maxCapacity}
            onChange={(e) => setFormData({ ...formData, maxCapacity: e.target.value })} />
          <button type="submit" disabled={isSaving}>{isSaving ? 'Saving…' : formMode === 'edit' ? 'Save Changes' : 'Create'}</button>
          <button type="button" onClick={() => setShowForm(false)} className={styles.cancelFormBtn}>Cancel</button>
          {formError && <p className={styles.formError}>{formError}</p>}
        </form>
      )}

      <input className={styles.search} placeholder="Search by class name, ID, or schedule…" value={search} onChange={(e) => setSearch(e.target.value)} />

      {isLoading ? (
        <LoadingSpinner label="Loading classes…" />
      ) : error ? (
        <p className={styles.formError}>{error}</p>
      ) : (
        <div className={styles.grid}>
          {filtered.map((c) => {
            const pct = c.maxCapacity > 0 ? Math.round((c.currentEnrolments / c.maxCapacity) * 100) : 0;
            return (
              <div key={c.classId} className={`${styles.card} ${c.cancelled ? styles.cancelledCard : ''}`}>
                <div className={styles.cardTop}>
                  <div>
                    <span className={styles.classId}>{c.classId}</span>
                    <h3 className={styles.className}>{c.className}</h3>
                    <span className={styles.schedule}>{c.schedule}</span>
                  </div>
                  {c.cancelled && <span className={styles.cancelledBadge}>CANCELLED</span>}
                </div>

                <div className={styles.capacityRow}>
                  <div className={styles.barTrack}><div className={styles.barFill} style={{ width: `${Math.min(pct, 100)}%` }} /></div>
                  <span className={styles.capacityText}>{c.currentEnrolments}/{c.maxCapacity} ({pct}%)</span>
                </div>

                <div className={styles.instructor}>
                  Instructor: {c.instructor ? c.instructor.name : <em>Not assigned</em>}
                </div>

                <div className={styles.cardActions}>
                  <button onClick={() => openEditForm(c)} className={styles.linkBtn}>Edit</button>
                  <button onClick={() => toggleCancelled(c)} className={styles.linkBtn}>
                    {c.cancelled ? 'Reactivate' : 'Cancel Class'}
                  </button>
                  <button onClick={() => { setEnrolPanelId(enrolPanelId === c.classId ? null : c.classId); setEnrolError(''); }} className={styles.linkBtn}>
                    Enrolments ({c.participants.length})
                  </button>
                </div>

                {enrolPanelId === c.classId && (
                  <div className={styles.enrolPanel}>
                    {c.participants.length > 0 ? (
                      <ul className={styles.participantList}>
                        {c.participants.map((p) => (
                          <li key={p.memberId}>
                            {p.name} <span className={styles.participantId}>({p.memberId})</span>
                            <button onClick={() => requestRemoveEnrolment(c, p.memberId)} disabled={enrolBusy} className={styles.removeBtn}>&times;</button>
                          </li>
                        ))}
                      </ul>
                    ) : (
                      <p className={styles.noParticipants}>No members enrolled yet.</p>
                    )}
                    <div className={styles.enrolForm}>
                      <input placeholder="Member ID" value={enrolMemberId} onChange={(e) => setEnrolMemberId(e.target.value)} />
                      <button onClick={() => handleEnrol(c.classId)} disabled={enrolBusy || !enrolMemberId.trim()}>Enrol</button>
                    </div>
                    {enrolError && <p className={styles.formError}>{enrolError}</p>}
                  </div>
                )}
              </div>
            );
          })}
          {filtered.length === 0 && <p className={styles.empty}>No classes match your search.</p>}
        </div>
      )}

      <ConfirmDialog
        open={!!confirmTarget}
        title={confirmTarget?.type === 'cancel' ? 'Cancel this class?' : 'Remove this enrolment?'}
        message={
          confirmTarget?.type === 'cancel'
            ? `${confirmTarget?.classObj?.className} will be marked cancelled and hidden from members. Existing enrolments are preserved and this can be undone by reactivating.`
            : 'This removes the member from this class. They would need to be re-enrolled from scratch if this was a mistake.'
        }
        confirmLabel={confirmTarget?.type === 'cancel' ? 'Cancel Class' : 'Remove'}
        onConfirm={confirmTarget?.type === 'cancel' ? confirmCancelClass : confirmRemoveEnrolment}
        onCancel={() => setConfirmTarget(null)}
      />
    </PageShell>
  );
}