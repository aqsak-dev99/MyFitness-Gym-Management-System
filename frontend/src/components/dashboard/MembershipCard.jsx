import GlassCard from './GlassCard';
import ProgressRing from './ProgressRing';
import { CardIcon } from './Icons';
import styles from './MembershipCard.module.css';

/**
 * The dashboard's hero card. Data logic is completely unchanged from
 * the previous version — same computeCyclePercent derivation from real
 * startDate/endDate, same real fields (monthlyFee, active, frozen,
 * daysRemaining). Only the visual structure changed: bigger ring, an
 * icon badge, and fee/status presented as genuine callout stats rather
 * than a plain label/value list.
 */
function computeCyclePercent(startDate, endDate) {
  const start = new Date(startDate).getTime();
  const end = new Date(endDate).getTime();
  const now = Date.now();
  if (now <= start) return 100;
  if (now >= end) return 0;
  return Math.round(((end - now) / (end - start)) * 100);
}

export default function MembershipCard({ membership }) {
  if (!membership) return null;

  const percent = computeCyclePercent(membership.startDate, membership.endDate);
  const isFrozen = membership.frozen;

  return (
    <GlassCard className={styles.card}>
      <div className={styles.header}>
        <div className={styles.badgeIcon}>
          <CardIcon size={20} color="var(--dash-accent-green)" />
        </div>
        <div>
          <span className={styles.eyebrow}>Membership</span>
        </div>
        <span className={`${styles.status} ${isFrozen ? styles.statusFrozen : styles.statusActive}`}>
          {isFrozen ? 'Frozen' : 'Active'}
        </span>
      </div>

      <div className={styles.body}>
        <ProgressRing
          percent={percent}
          label={`${membership.daysRemaining}`}
          sublabel="days left"
          size={104}
          colorFrom={isFrozen ? '#e2766a' : '#b57bf5'}
          colorTo={isFrozen ? '#c14f42' : '#7c3aed'}
        />

        <div className={styles.stats}>
          <div className={styles.stat}>
            <span className={styles.statValue}>£{membership.monthlyFee.toFixed(2)}</span>
            <span className={styles.statLabel}>Monthly fee</span>
          </div>
          {/* joiningFee only exists on StandardMembership, confirmed
              directly against the model — guarded here so this never
              breaks for StudentSaver or PayAsYouGo memberships, which
              show "Currently active" in this slot instead. */}
          {membership.joiningFee !== undefined ? (
            <div className={styles.stat}>
              <span className={styles.statValue}>£{membership.joiningFee.toFixed(2)}</span>
              <span className={styles.statLabel}>Joining fee</span>
            </div>
          ) : (
            <div className={styles.stat}>
              <span className={styles.statValue}>{membership.active ? 'Yes' : 'No'}</span>
              <span className={styles.statLabel}>Currently active</span>
            </div>
          )}
        </div>
      </div>
    </GlassCard>
  );
}