import { useEffect, useState } from 'react';
import { useAuth } from '../auth/AuthContext';
import { memberApi } from '../api/memberApi';
import { ApiError } from '../api/client';

import PageShell from '../components/layout/PageShell';
import LoadingSpinner from '../components/LoadingSpinner';
import EmptyProfileState from '../components/dashboard/EmptyProfileState';
import MembershipCard from '../components/dashboard/MembershipCard';
import styles from './Membership.module.css';

/**
 * A dedicated route for membership detail — same real GET /api/members/me
 * call already used everywhere else, same MembershipCard component
 * already built for the Dashboard. No new backend surface, no new
 * component logic — just a genuine, separate destination for the
 * sidebar's "Membership" link.
 */
export default function Membership() {
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
          setError(err instanceof ApiError ? err.message : 'Could not load your membership.');
        }
      } finally {
        setIsLoading(false);
      }
    }
    load();
  }, []);

  return (
    <PageShell>
      <h1 className={styles.title}>Membership</h1>

      {isLoading ? (
        <LoadingSpinner label="Loading your membership…" />
      ) : error ? (
        <p className={styles.errorText}>{error}</p>
      ) : hasNoProfile ? (
        <EmptyProfileState username={user.username} />
      ) : !profile.membership ? (
        <p className={styles.noMembership}>No membership has been assigned to your account yet. Contact gym staff.</p>
      ) : (
        <MembershipCard membership={profile.membership} />
      )}
    </PageShell>
  );
}