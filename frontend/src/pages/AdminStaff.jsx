import { useEffect, useState } from 'react';
import { trainerApi } from '../api/trainerApi';
import { ApiError } from '../api/client';
import { useToast } from '../toast/ToastContext';

import PageShell from '../components/layout/PageShell';
import LoadingSpinner from '../components/LoadingSpinner';
import ConfirmDialog from '../components/ConfirmDialog';
import styles from './AdminStaff.module.css';

const TABS = [
  { id: 'instructors', label: 'Instructors' },
  { id: 'fullTime', label: 'Full-time Staff' },
  { id: 'partTime', label: 'Part-time Staff' },
];

const EMPTY_FORMS = {
  instructors: { personId: '', staffId: '', name: '', email: '', phone: '', salary: '', workSchedule: '', specialisation: '' },
  fullTime: { personId: '', staffId: '', name: '', email: '', phone: '', role: '', salary: '', workSchedule: '' },
  partTime: { personId: '', staffId: '', name: '', email: '', phone: '', role: '', hourlyRate: '', hoursPerWeek: '', shiftPattern: '' },
};

/**
 * Full real Staff CRUD across all three types. Deactivate/reactivate
 * use the same unified endpoint regardless of type (matches
 * TrainerService.deactivateStaff()'s own unified implementation) —
 * everything else here is genuinely type-specific.
 */
export default function AdminStaff() {
  const { showToast } = useToast();
  const [activeTab, setActiveTab] = useState('instructors');
  const [instructors, setInstructors] = useState([]);
  const [fullTime, setFullTime] = useState([]);
  const [partTime, setPartTime] = useState([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState('');
  const [search, setSearch] = useState('');
  const [confirmTarget, setConfirmTarget] = useState(null); // the staff member pending deactivation

  const [showForm, setShowForm] = useState(false);
  const [formMode, setFormMode] = useState('create');
  const [formData, setFormData] = useState(EMPTY_FORMS.instructors);
  const [formError, setFormError] = useState('');
  const [isSaving, setIsSaving] = useState(false);

  useEffect(() => { loadAll(); }, []);

  async function loadAll() {
    setIsLoading(true);
    setError('');
    try {
      const [i, ft, pt] = await Promise.all([
        trainerApi.getInstructors(), trainerApi.getFullTimeStaff(), trainerApi.getPartTimeStaff(),
      ]);
      setInstructors(i); setFullTime(ft); setPartTime(pt);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not load staff.');
    } finally {
      setIsLoading(false);
    }
  }

  const listFor = { instructors, fullTime, partTime };
  const currentList = listFor[activeTab].filter((s) => {
    const q = search.toLowerCase();
    return !q || s.name.toLowerCase().includes(q) || s.staffId.toLowerCase().includes(q);
  });

  function openCreateForm() {
    setFormMode('create');
    setFormData(EMPTY_FORMS[activeTab]);
    setFormError('');
    setShowForm(true);
  }

  function openEditForm(s) {
    setFormMode('edit');
    if (activeTab === 'instructors') {
      setFormData({ staffId: s.staffId, name: s.name, email: s.email, phone: s.phone, salary: String(s.salary), workSchedule: s.workSchedule, specialisation: s.specialisation });
    } else if (activeTab === 'fullTime') {
      setFormData({ staffId: s.staffId, name: s.name, email: s.email, phone: s.phone, role: s.role, salary: String(s.salary), workSchedule: s.workSchedule });
    } else {
      setFormData({ staffId: s.staffId, name: s.name, email: s.email, phone: s.phone, role: s.role, hourlyRate: String(s.hourlyRate), hoursPerWeek: String(s.hoursPerWeek), shiftPattern: s.shiftPattern });
    }
    setFormError('');
    setShowForm(true);
  }

  async function handleSubmitForm(e) {
    e.preventDefault();
    setFormError('');
    setIsSaving(true);
    try {
      if (activeTab === 'instructors') {
        const payload = { name: formData.name, email: formData.email, phone: formData.phone, salary: Number(formData.salary), workSchedule: formData.workSchedule, specialisation: formData.specialisation };
        if (formMode === 'create') await trainerApi.addInstructor({ ...payload, personId: formData.personId, staffId: formData.staffId });
        else await trainerApi.updateInstructor(formData.staffId, payload);
      } else if (activeTab === 'fullTime') {
        const payload = { name: formData.name, email: formData.email, phone: formData.phone, role: formData.role, salary: Number(formData.salary), workSchedule: formData.workSchedule };
        if (formMode === 'create') await trainerApi.addFullTimeStaff({ ...payload, personId: formData.personId, staffId: formData.staffId });
        else await trainerApi.updateFullTimeStaff(formData.staffId, payload);
      } else {
        const payload = { name: formData.name, email: formData.email, phone: formData.phone, role: formData.role, hourlyRate: Number(formData.hourlyRate), hoursPerWeek: Number(formData.hoursPerWeek), shiftPattern: formData.shiftPattern };
        if (formMode === 'create') await trainerApi.addPartTimeStaff({ ...payload, personId: formData.personId, staffId: formData.staffId });
        else await trainerApi.updatePartTimeStaff(formData.staffId, payload);
      }
      setShowForm(false);
      await loadAll();
      showToast(formMode === 'create' ? 'Staff member added.' : 'Staff member updated.');
    } catch (err) {
      setFormError(err instanceof ApiError ? err.message : 'Could not save staff member.');
    } finally {
      setIsSaving(false);
    }
  }

  // Reactivation is restorative — no confirmation needed. Deactivation
  // goes through confirmTarget instead (see confirmDeactivateStaff).
  async function toggleAvailable(s) {
    if (!s.available) {
      try {
        await trainerApi.reactivateStaff(s.staffId);
        await loadAll();
        showToast('Staff member reactivated.');
      } catch (err) {
        setError(err instanceof ApiError ? err.message : 'Could not update staff status.');
        showToast('Could not reactivate staff member.', 'error');
      }
      return;
    }
    setConfirmTarget(s);
  }

  async function confirmDeactivateStaff() {
    const s = confirmTarget;
    setConfirmTarget(null);
    try {
      await trainerApi.deactivateStaff(s.staffId);
      await loadAll();
      showToast('Staff member deactivated.');
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not update staff status.');
      showToast('Could not deactivate staff member.', 'error');
    }
  }

  return (
    <PageShell>
      <div className={styles.header}>
        <div>
          <h1 className={styles.title}>Staff</h1>
          <p className={styles.subtitle}>Add, edit, and manage instructors and staff.</p>
        </div>
        <button className={styles.addBtn} onClick={openCreateForm}>+ Add Staff</button>
      </div>

      <div className={styles.tabs}>
        {TABS.map((t) => (
          <button key={t.id} className={`${styles.tab} ${activeTab === t.id ? styles.tabActive : ''}`}
            onClick={() => { setActiveTab(t.id); setShowForm(false); }}>
            {t.label} ({listFor[t.id].length})
          </button>
        ))}
      </div>

      {showForm && (
        <form onSubmit={handleSubmitForm} className={styles.addForm}>
          {formMode === 'create' && (
            <>
              <input required placeholder="Person ID" value={formData.personId} onChange={(e) => setFormData({ ...formData, personId: e.target.value })} />
              <input required placeholder="Staff ID" value={formData.staffId} onChange={(e) => setFormData({ ...formData, staffId: e.target.value })} />
            </>
          )}
          <input required placeholder="Full name" value={formData.name} onChange={(e) => setFormData({ ...formData, name: e.target.value })} />
          <input required type="email" placeholder="Email" value={formData.email} onChange={(e) => setFormData({ ...formData, email: e.target.value })} />
          <input required placeholder="Phone" value={formData.phone} onChange={(e) => setFormData({ ...formData, phone: e.target.value })} />

          {activeTab !== 'instructors' && (
            <input required placeholder="Role" value={formData.role} onChange={(e) => setFormData({ ...formData, role: e.target.value })} />
          )}
          {activeTab === 'instructors' && (
            <input required placeholder="Specialisation" value={formData.specialisation} onChange={(e) => setFormData({ ...formData, specialisation: e.target.value })} />
          )}

          {activeTab !== 'partTime' ? (
            <>
              <input required type="number" placeholder="Salary" value={formData.salary} onChange={(e) => setFormData({ ...formData, salary: e.target.value })} />
              <input required placeholder="Work schedule" value={formData.workSchedule} onChange={(e) => setFormData({ ...formData, workSchedule: e.target.value })} />
            </>
          ) : (
            <>
              <input required type="number" step="0.5" placeholder="Hourly rate" value={formData.hourlyRate} onChange={(e) => setFormData({ ...formData, hourlyRate: e.target.value })} />
              <input required type="number" placeholder="Hours/week" value={formData.hoursPerWeek} onChange={(e) => setFormData({ ...formData, hoursPerWeek: e.target.value })} />
              <input required placeholder="Shift pattern" value={formData.shiftPattern} onChange={(e) => setFormData({ ...formData, shiftPattern: e.target.value })} />
            </>
          )}

          <button type="submit" disabled={isSaving}>{isSaving ? 'Saving…' : formMode === 'edit' ? 'Save Changes' : 'Create'}</button>
          <button type="button" onClick={() => setShowForm(false)} className={styles.cancelFormBtn}>Cancel</button>
          {formError && <p className={styles.formError}>{formError}</p>}
        </form>
      )}

      <input className={styles.search} placeholder="Search by name or staff ID…" value={search} onChange={(e) => setSearch(e.target.value)} />

      {isLoading ? (
        <LoadingSpinner label="Loading staff…" />
      ) : error ? (
        <p className={styles.formError}>{error}</p>
      ) : (
        <div className={styles.table}>
          {currentList.map((s) => (
            <div key={s.staffId} className={`${styles.row} ${!s.available ? styles.rowInactive : ''}`}>
              <div>
                <div className={styles.name}>{s.name}</div>
                <div className={styles.staffId}>{s.staffId}</div>
              </div>
              <div>
                <div className={styles.email}>{s.email}</div>
                <div className={styles.roleText}>{activeTab === 'instructors' ? s.specialisation : s.role}</div>
              </div>
              <span className={`${styles.statusBadge} ${s.available ? styles.available : styles.unavailable}`}>
                {s.available ? 'Available' : 'Unavailable'}
              </span>
              <div className={styles.actions}>
                <button onClick={() => openEditForm(s)} className={styles.linkBtn}>Edit</button>
                <button onClick={() => toggleAvailable(s)} className={styles.linkBtn}>
                  {s.available ? 'Deactivate' : 'Reactivate'}
                </button>
              </div>
            </div>
          ))}
          {currentList.length === 0 && <p className={styles.empty}>No staff match your search.</p>}
        </div>
      )}

      <ConfirmDialog
        open={!!confirmTarget}
        title="Deactivate staff member?"
        message={`${confirmTarget?.name} will be marked unavailable. This can be undone by reactivating them.`}
        confirmLabel="Deactivate"
        onConfirm={confirmDeactivateStaff}
        onCancel={() => setConfirmTarget(null)}
      />
    </PageShell>
  );
}