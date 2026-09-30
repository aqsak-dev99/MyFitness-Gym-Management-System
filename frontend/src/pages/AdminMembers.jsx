import { useEffect, useState } from 'react';
import { memberApi } from '../api/memberApi';
import { ApiError } from '../api/client';
import { useToast } from '../toast/ToastContext';
import { downloadCsv } from '../utils/csv';

import PageShell from '../components/layout/PageShell';
import LoadingSpinner from '../components/LoadingSpinner';
import ConfirmDialog from '../components/ConfirmDialog';
import styles from './AdminMembers.module.css';

const EMPTY_NEW_MEMBER = { personId: '', memberId: '', name: '', email: '', phone: '' };
const EMPTY_MEMBERSHIP_FORM = { membershipId: '', type: 'STANDARD', durationMonths: '12', studentIdNumber: '' };

/**
 * Full real Members CRUD — every action here calls a genuine backend
 * endpoint, all verified against the actual controller/service code
 * before being wired up. No mock data, no fake buttons.
 */
export default function AdminMembers() {
  const { showToast } = useToast();
  const [members, setMembers] = useState([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState('');
  const [search, setSearch] = useState('');
  const [confirmTarget, setConfirmTarget] = useState(null); // { type: 'deactivate'|'removeMembership', member }

  const [showAddForm, setShowAddForm] = useState(false);
  const [newMember, setNewMember] = useState(EMPTY_NEW_MEMBER);
  const [addError, setAddError] = useState('');
  const [isSaving, setIsSaving] = useState(false);

  const [editingId, setEditingId] = useState(null);
  const [editDraft, setEditDraft] = useState({ name: '', email: '', phone: '' });
  const [editError, setEditError] = useState('');

  const [membershipPanelId, setMembershipPanelId] = useState(null);
  const [membershipForm, setMembershipForm] = useState(EMPTY_MEMBERSHIP_FORM);
  const [membershipError, setMembershipError] = useState('');
  const [membershipBusy, setMembershipBusy] = useState(false);

  useEffect(() => { loadMembers(); }, []);

  async function loadMembers() {
    setIsLoading(true);
    setError('');
    try {
      const data = await memberApi.getAll();
      setMembers(data);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not load members.');
    } finally {
      setIsLoading(false);
    }
  }

  const filtered = members.filter((m) => {
    const q = search.toLowerCase();
    return !q || m.name.toLowerCase().includes(q) || m.email.toLowerCase().includes(q) || m.memberId.toLowerCase().includes(q);
  });

  function handleExportCsv() {
    const headers = ['Member ID', 'Name', 'Email', 'Phone', 'Status', 'Membership Type', 'Payment Status'];
    const rows = filtered.map((m) => [
      m.memberId,
      m.name,
      m.email,
      m.phone,
      m.active ? 'Active' : 'Inactive',
      m.membership?.membershipType ?? 'None',
      m.membership?.paymentStatus ?? 'N/A',
    ]);
    downloadCsv(`myfitness-members-${new Date().toISOString().slice(0, 10)}.csv`, headers, rows);
    showToast('Members exported.');
  }

  async function handleAddMember(e) {
    e.preventDefault();
    setAddError('');
    setIsSaving(true);
    try {
      await memberApi.register(newMember);
      setNewMember(EMPTY_NEW_MEMBER);
      setShowAddForm(false);
      await loadMembers();
      showToast('Member created.');
    } catch (err) {
      setAddError(err instanceof ApiError ? err.message : 'Could not add member.');
    } finally {
      setIsSaving(false);
    }
  }

  function startEdit(m) {
    setEditingId(m.memberId);
    setEditDraft({ name: m.name, email: m.email, phone: m.phone });
    setEditError('');
  }

  async function saveEdit(memberId) {
    setEditError('');
    try {
      await memberApi.update(memberId, editDraft);
      setEditingId(null);
      await loadMembers();
      showToast('Member updated.');
    } catch (err) {
      setEditError(err instanceof ApiError ? err.message : 'Could not save changes.');
    }
  }

  // Reactivation is restorative, not destructive — no confirmation needed.
  // Deactivation goes through confirmTarget instead (see confirmDeactivate).
  async function toggleActive(m) {
    if (m.active) {
      setConfirmTarget({ type: 'deactivate', member: m });
      return;
    }
    try {
      await memberApi.reactivate(m.memberId);
      await loadMembers();
      showToast('Member reactivated.');
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not update member status.');
      showToast('Could not reactivate member.', 'error');
    }
  }

  async function confirmDeactivate() {
    const m = confirmTarget.member;
    setConfirmTarget(null);
    try {
      await memberApi.deactivate(m.memberId);
      await loadMembers();
      showToast('Member deactivated.');
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not update member status.');
      showToast('Could not deactivate member.', 'error');
    }
  }

  function openMembershipPanel(m) {
    setMembershipPanelId(m.memberId);
    setMembershipForm(EMPTY_MEMBERSHIP_FORM);
    setMembershipError('');
  }

  async function handleAssignMembership(memberId) {
    setMembershipError('');
    setMembershipBusy(true);
    try {
      const payload = { membershipId: membershipForm.membershipId, type: membershipForm.type };
      if (membershipForm.type === 'STANDARD') payload.durationMonths = Number(membershipForm.durationMonths);
      if (membershipForm.type === 'STUDENT_SAVER') payload.studentIdNumber = membershipForm.studentIdNumber;
      await memberApi.assignMembership(memberId, payload);
      await loadMembers();
      setMembershipForm(EMPTY_MEMBERSHIP_FORM);
      showToast('Membership assigned.');
    } catch (err) {
      setMembershipError(err instanceof ApiError ? err.message : 'Could not assign membership.');
    } finally {
      setMembershipBusy(false);
    }
  }

  async function handleFreezeToggle(m) {
    setMembershipBusy(true);
    try {
      if (m.membership.frozen) await memberApi.unfreezeMembership(m.memberId);
      else await memberApi.freezeMembership(m.memberId);
      await loadMembers();
      showToast(m.membership.frozen ? 'Membership unfrozen.' : 'Membership frozen.');
    } catch (err) {
      setMembershipError(err instanceof ApiError ? err.message : 'Could not update membership.');
    } finally {
      setMembershipBusy(false);
    }
  }

  function requestRemoveMembership(m) {
    setConfirmTarget({ type: 'removeMembership', member: m });
  }

  async function confirmRemoveMembership() {
    const memberId = confirmTarget.member.memberId;
    setConfirmTarget(null);
    setMembershipBusy(true);
    try {
      await memberApi.removeMembership(memberId);
      await loadMembers();
      showToast('Membership removed.');
    } catch (err) {
      setMembershipError(err instanceof ApiError ? err.message : 'Could not remove membership.');
      showToast('Could not remove membership.', 'error');
    } finally {
      setMembershipBusy(false);
    }
  }

  return (
    <PageShell>
      <div className={styles.header}>
        <div>
          <h1 className={styles.title}>Members</h1>
          <p className={styles.subtitle}>Add, edit, and manage every member and their membership.</p>
        </div>
        <div className={styles.headerActions}>
          <button className={styles.addBtn} onClick={() => setShowAddForm((v) => !v)}>
            {showAddForm ? 'Cancel' : '+ Add Member'}
          </button>
          <button className={styles.exportBtn} onClick={handleExportCsv}>Export CSV</button>
        </div>
      </div>

      {showAddForm && (
        <form onSubmit={handleAddMember} className={styles.addForm}>
          <input required placeholder="Person ID" value={newMember.personId} onChange={(e) => setNewMember({ ...newMember, personId: e.target.value })} />
          <input required placeholder="Member ID" value={newMember.memberId} onChange={(e) => setNewMember({ ...newMember, memberId: e.target.value })} />
          <input required placeholder="Full name" value={newMember.name} onChange={(e) => setNewMember({ ...newMember, name: e.target.value })} />
          <input required type="email" placeholder="Email" value={newMember.email} onChange={(e) => setNewMember({ ...newMember, email: e.target.value })} />
          <input required placeholder="Phone" value={newMember.phone} onChange={(e) => setNewMember({ ...newMember, phone: e.target.value })} />
          <button type="submit" disabled={isSaving}>{isSaving ? 'Saving…' : 'Create'}</button>
          {addError && <p className={styles.formError}>{addError}</p>}
        </form>
      )}

      <input
        className={styles.search}
        placeholder="Search by name, email, or member ID…"
        value={search}
        onChange={(e) => setSearch(e.target.value)}
      />

      {isLoading ? (
        <LoadingSpinner label="Loading members…" />
      ) : error ? (
        <p className={styles.formError}>{error}</p>
      ) : (
        <div className={styles.table}>
          <div className={styles.tableHead}>
            <span>Member</span><span>Contact</span><span>Status</span><span>Membership</span><span>Actions</span>
          </div>
          {filtered.map((m) => (
            <div key={m.memberId} className={styles.rowGroup}>
              <div className={`${styles.row} ${!m.active ? styles.rowInactive : ''}`}>
                {editingId === m.memberId ? (
                  <>
                    <div className={styles.editCell}>
                      <input value={editDraft.name} onChange={(e) => setEditDraft({ ...editDraft, name: e.target.value })} />
                    </div>
                    <div className={styles.editCell}>
                      <input value={editDraft.email} onChange={(e) => setEditDraft({ ...editDraft, email: e.target.value })} />
                      <input value={editDraft.phone} onChange={(e) => setEditDraft({ ...editDraft, phone: e.target.value })} />
                    </div>
                    <span />
                    <span />
                    <div className={styles.actions}>
                      <button onClick={() => saveEdit(m.memberId)} className={styles.saveBtn}>Save</button>
                      <button onClick={() => setEditingId(null)} className={styles.cancelBtn}>Cancel</button>
                    </div>
                  </>
                ) : (
                  <>
                    <div>
                      <div className={styles.name}>{m.name}</div>
                      <div className={styles.memberId}>{m.memberId}</div>
                    </div>
                    <div>
                      <div className={styles.email}>{m.email}</div>
                      <div className={styles.phone}>{m.phone}</div>
                    </div>
                    <span className={`${styles.statusBadge} ${m.active ? styles.active : styles.inactive}`}>
                      {m.active ? 'Active' : 'Inactive'}
                    </span>
                    <span className={`${styles.membershipBadge} ${m.membership ? (m.membership.frozen ? styles.frozen : styles.hasMembership) : styles.noMembership}`}>
                      {m.membership ? (m.membership.frozen ? 'Frozen' : 'Active') : 'None'}
                    </span>
                    <div className={styles.actions}>
                      <button onClick={() => startEdit(m)} className={styles.linkBtn}>Edit</button>
                      <button onClick={() => toggleActive(m)} className={styles.linkBtn}>
                        {m.active ? 'Deactivate' : 'Reactivate'}
                      </button>
                      <button onClick={() => openMembershipPanel(m)} className={styles.linkBtn}>Membership</button>
                    </div>
                  </>
                )}
              </div>
              {editError && editingId === m.memberId && <p className={styles.formError}>{editError}</p>}

              {membershipPanelId === m.memberId && (
                <div className={styles.membershipPanel}>
                  {m.membership ? (
                    <div className={styles.currentMembership}>
                      <span>Current: <strong>{m.membership.membershipId}</strong> — £{m.membership.monthlyFee?.toFixed(2)}/mo, {m.membership.daysRemaining} days left, {m.membership.frozen ? 'FROZEN' : 'active'}</span>
                      <div className={styles.membershipActions}>
                        <button onClick={() => handleFreezeToggle(m)} disabled={membershipBusy}>{m.membership.frozen ? 'Unfreeze' : 'Freeze'}</button>
                        <button onClick={() => requestRemoveMembership(m)} disabled={membershipBusy} className={styles.dangerBtn}>Remove</button>
                      </div>
                    </div>
                  ) : (
                    <p className={styles.noMembershipText}>No membership assigned.</p>
                  )}

                  <div className={styles.assignForm}>
                    <span className={styles.assignLabel}>Assign new membership:</span>
                    <input placeholder="Membership ID" value={membershipForm.membershipId} onChange={(e) => setMembershipForm({ ...membershipForm, membershipId: e.target.value })} />
                    <select value={membershipForm.type} onChange={(e) => setMembershipForm({ ...membershipForm, type: e.target.value })}>
                      <option value="STANDARD">Standard</option>
                      <option value="STUDENT_SAVER">Student Saver</option>
                      <option value="PAY_AS_YOU_GO">Pay As You Go</option>
                    </select>
                    {membershipForm.type === 'STANDARD' && (
                      <input type="number" placeholder="Months" value={membershipForm.durationMonths} onChange={(e) => setMembershipForm({ ...membershipForm, durationMonths: e.target.value })} />
                    )}
                    {membershipForm.type === 'STUDENT_SAVER' && (
                      <input placeholder="Student ID" value={membershipForm.studentIdNumber} onChange={(e) => setMembershipForm({ ...membershipForm, studentIdNumber: e.target.value })} />
                    )}
                    <button onClick={() => handleAssignMembership(m.memberId)} disabled={membershipBusy || !membershipForm.membershipId}>Assign</button>
                    <button onClick={() => setMembershipPanelId(null)} className={styles.cancelBtn}>Close</button>
                  </div>
                  {membershipError && <p className={styles.formError}>{membershipError}</p>}
                </div>
              )}
            </div>
          ))}
          {filtered.length === 0 && <p className={styles.empty}>No members match your search.</p>}
        </div>
      )}

      <ConfirmDialog
        open={!!confirmTarget}
        title={confirmTarget?.type === 'deactivate' ? 'Deactivate member?' : 'Remove membership?'}
        message={
          confirmTarget?.type === 'deactivate'
            ? `${confirmTarget?.member?.name} will no longer be able to log in or access their account. Their history is preserved and this can be undone by reactivating.`
            : `This removes ${confirmTarget?.member?.name}'s current membership entirely. This cannot be undone — a new membership would need to be assigned from scratch.`
        }
        confirmLabel={confirmTarget?.type === 'deactivate' ? 'Deactivate' : 'Remove'}
        onConfirm={confirmTarget?.type === 'deactivate' ? confirmDeactivate : confirmRemoveMembership}
        onCancel={() => setConfirmTarget(null)}
      />
    </PageShell>
  );
}