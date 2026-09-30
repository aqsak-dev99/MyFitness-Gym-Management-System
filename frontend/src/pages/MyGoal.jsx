import { useEffect, useState } from 'react';
import { useAuth } from '../auth/AuthContext';
import { memberApi } from '../api/memberApi';
import { ApiError } from '../api/client';

import PageShell from '../components/layout/PageShell';
import LoadingSpinner from '../components/LoadingSpinner';
import EmptyProfileState from '../components/dashboard/EmptyProfileState';
import GoalCard from '../components/dashboard/GoalCard';
import styles from './Membership.module.css';

/**
 * Dedicated route for goal editing — reuses GoalCard's real edit logic
 * (PATCH .../goal) exactly as Dashboard and Profile already do. No new
 * endpoint, no duplicated editing logic — just a separate destination
 * for the sidebar's "My Goal" link and QuickActions' matching card.
 */
export default function MyGoal() {
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
          setError(err instanceof ApiError ? err.message : 'Could not load your goal.');
        }
      } finally {
        setIsLoading(false);
      }
    }
    load();
  }, []);

  return (
    <PageShell>
      <h1 className={styles.title}>My Goal</h1>

      {isLoading ? (
        <LoadingSpinner label="Loading your goal…" />
      ) : error ? (
        <p className={styles.errorText}>{error}</p>
      ) : hasNoProfile ? (
        <EmptyProfileState username={user.username} />
      ) : (
        <GoalCard
          memberId={profile.memberId}
          fitnessGoal={profile.fitnessGoal}
          onGoalUpdated={(newGoal) => setProfile((p) => ({ ...p, fitnessGoal: newGoal }))}
        />
      )}
    </PageShell>
  );
}