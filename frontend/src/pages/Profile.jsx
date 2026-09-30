import { useEffect, useState } from 'react';
import { useAuth } from '../auth/AuthContext';
import { memberApi } from '../api/memberApi';
import { ApiError } from '../api/client';

import PageShell from '../components/layout/PageShell';
import LoadingSpinner from '../components/LoadingSpinner';
import GlassCard from '../components/dashboard/GlassCard';
import GoalCard from '../components/dashboard/GoalCard';
import EmptyProfileState from '../components/dashboard/EmptyProfileState';
import { ProfileGlyph } from '../components/dashboard/Icons';
import styles from './Profile.module.css';

/**
 * Only fitnessGoal is genuinely editable here — the backend has no
 * endpoint to update name/email/phone (registerMember creates them,
 * nothing updates them afterward), so those render as read-only real
 * data rather than pretending they can be changed. Reuses GoalCard
 * directly rather than re-implementing the same edit flow twice.
 */
export default function Profile() {
  const { user } = useAuth();
  const [profile, setProfile] = useState(null);
  const [isLoading, setIsLoading] = useState(true);
  const [hasNoProfile, setHasNoProfile] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    async function load() {
      setIsLoading(true);
      setError('');
      setHasNoProfile(false);
      try {
        const data = await memberApi.getMyProfile();
        setProfile(data);
      } catch (err) {
        if (err instanceof ApiError && err.status === 404) {
          setHasNoProfile(true);
        } else {
          setError(err instanceof ApiError ? err.message : 'Could not load your profile.');
        }
      } finally {
        setIsLoading(false);
      }
    }
    load();
  }, []);

  return (
    <PageShell>
      <h1 className={styles.title}>Profile</h1>

      {isLoading ? (
        <LoadingSpinner label="Loading your profile…" />
      ) : error ? (
        <p className={styles.errorText}>{error}</p>
      ) : hasNoProfile ? (
        <EmptyProfileState username={user.username} />
      ) : (
        <div className={styles.stack}>
          <GlassCard hero>
            <div className={styles.header}>
              <div className={styles.avatarGlow}>
                <ProfileGlyph size={30} color="var(--dash-accent-green)" />
              </div>
              <div>
                <div className={styles.name}>{profile.name}</div>
                <div className={styles.username}>@{user.username}</div>
              </div>
            </div>

            <div className={styles.fieldGrid}>
              <div className={styles.field}>
                <span className={styles.fieldLabel}>Email</span>
                <span className={styles.fieldValue}>{profile.email}</span>
              </div>
              <div className={styles.field}>
                <span className={styles.fieldLabel}>Phone</span>
                <span className={styles.fieldValue}>{profile.phone}</span>
              </div>
              <div className={styles.field}>
                <span className={styles.fieldLabel}>Member since</span>
                <span className={styles.fieldValue}>
                  {new Date(profile.registrationDate).toLocaleDateString('en-GB', { day: 'numeric', month: 'long', year: 'numeric' })}
                </span>
              </div>
              <div className={styles.field}>
                <span className={styles.fieldLabel}>Member ID</span>
                <span className={styles.fieldValue}>{profile.memberId}</span>
              </div>
            </div>
            <p className={styles.readOnlyNote}>
              Name, email, and phone are managed by gym staff and can't be edited here yet.
            </p>
          </GlassCard>

          <GoalCard
            memberId={profile.memberId}
            fitnessGoal={profile.fitnessGoal}
            onGoalUpdated={(newGoal) => setProfile((p) => ({ ...p, fitnessGoal: newGoal }))}
          />
        </div>
      )}
    </PageShell>
  );
}