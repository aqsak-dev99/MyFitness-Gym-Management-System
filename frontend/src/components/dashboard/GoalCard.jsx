import { useState } from 'react';
import GlassCard from './GlassCard';
import { memberApi } from '../../api/memberApi';
import { useToast } from '../../toast/ToastContext';
import { TargetIcon } from './Icons';
import styles from './GoalCard.module.css';

/**
 * Displays the real fitnessGoal field, or an honest empty state if
 * it's null. Editing is a small, genuine inline interaction — not a
 * separate page — since it's a single PATCH call the API layer
 * already supports (updateMyGoal), not new backend surface.
 */
export default function GoalCard({ memberId, fitnessGoal, onGoalUpdated }) {
  const { showToast } = useToast();
  const [isEditing, setIsEditing] = useState(false);
  const [draft, setDraft] = useState(fitnessGoal || '');
  const [isSaving, setIsSaving] = useState(false);
  const [error, setError] = useState('');

  async function handleSave() {
    setError('');
    setIsSaving(true);
    try {
      const updated = await memberApi.updateMyGoal(memberId, draft.trim());
      onGoalUpdated(updated.fitnessGoal);
      setIsEditing(false);
      showToast('Goal updated.');
    } catch {
      setError('Could not save your goal. Please try again.');
      showToast('Could not save your goal.', 'error');
    } finally {
      setIsSaving(false);
    }
  }

  return (
    <GlassCard className={styles.card}>
      <div className={styles.header}>
        <div className={styles.headerLeft}>
          <div className={styles.badgeIcon}>
            <TargetIcon size={16} color="var(--dash-accent-gold)" />
          </div>
          <span className={styles.eyebrow}>Fitness goal</span>
        </div>
        {!isEditing && (
          <button className={styles.editBtn} onClick={() => setIsEditing(true)}>
            {fitnessGoal ? 'Edit' : 'Set goal'}
          </button>
        )}
      </div>

      {isEditing ? (
        <div className={styles.editArea}>
          <textarea
            className={styles.textarea}
            value={draft}
            onChange={(e) => setDraft(e.target.value)}
            placeholder="e.g. Build endurance for a 10k race"
            rows={3}
          />
          {error && <span className={styles.error}>{error}</span>}
          <div className={styles.actions}>
            <button className={styles.cancelBtn} onClick={() => { setIsEditing(false); setDraft(fitnessGoal || ''); }}>
              Cancel
            </button>
            <button className={styles.saveBtn} onClick={handleSave} disabled={isSaving}>
              {isSaving ? 'Saving…' : 'Save'}
            </button>
          </div>
        </div>
      ) : fitnessGoal ? (
        <p className={styles.goalText}>{fitnessGoal}</p>
      ) : (
        <p className={styles.empty}>No fitness goal set yet — set one to get a personalized bootcamp recommendation.</p>
      )}
    </GlassCard>
  );
}