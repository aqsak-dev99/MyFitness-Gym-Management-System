import { useEffect, useState } from 'react';
import { useAuth } from '../auth/AuthContext';
import { useToast } from '../toast/ToastContext';
import { memberApi } from '../api/memberApi';
import { ApiError } from '../api/client';

import PageShell from '../components/layout/PageShell';
import LoadingSpinner from '../components/LoadingSpinner';
import EmptyProfileState from '../components/dashboard/EmptyProfileState';
import MembershipCard from '../components/dashboard/MembershipCard';
import MembershipPaymentPanel from '../components/dashboard/MembershipPaymentPanel';
import styles from './Membership.module.css';

/**
 * A dedicated route for membership detail — same real GET /api/members/me
 * call already used everywhere else, same MembershipCard component
 * already built for the Dashboard, plus a payment panel beside it:
 * the member's payment status, a simulated "Pay now" and their recent
 * payments. Paying calls POST /api/members/{id}/membership/pay, then
 * reloads the profile so the status and history update in place.
 */
export default function Membership() {
  const { user } = useAuth();
  const { showToast } = useToast();
  const [profile, setProfile] = useState(null);
  const [isLoading, setIsLoading] = useState(true);
  const [hasNoProfile, setHasNoProfile] = useState(false);
  const [error, setError] = useState('');
  const [isPaying, setIsPaying] = useState(false);

  // `silent` refreshes the data without swapping the page for the
  // loading spinner — used after a payment so the panel updates in place.
  async function loadProfile({ silent = false } = {}) {
    if (!silent) setIsLoading(true);
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

  useEffect(() => { loadProfile(); }, []);

  async function handlePay() {
    setIsPaying(true);
    try {
      const updated = await memberApi.payMembership(profile.memberId);
      await loadProfile({ silent: true });
      // One payment settles one monthly cycle. Someone more than a
      // month behind is still overdue afterwards — say so, rather than
      // leaving them wondering why the badge didn't change.
      showToast(
        updated.paymentStatus === 'OVERDUE'
          ? 'Payment received (demo). You are still overdue — one more payment is needed to catch up.'
          : 'Payment received (demo). Thank you!'
      );
    } catch (err) {
      showToast(err instanceof ApiError ? err.message : 'Payment could not be completed.', 'error');
      // Re-sync in case the failure was "already paid up" from another tab.
      await loadProfile({ silent: true });
    } finally {
      setIsPaying(false);
    }
  }

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
        <div className={styles.layout}>
          <MembershipCard membership={profile.membership} />
          <MembershipPaymentPanel
            membership={profile.membership}
            payments={profile.paymentHistory}
            onPay={handlePay}
            isPaying={isPaying}
          />
        </div>
      )}
    </PageShell>
  );
}
