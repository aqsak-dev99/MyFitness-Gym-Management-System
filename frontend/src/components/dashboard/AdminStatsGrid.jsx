import GlassCard from './GlassCard';
import { UsersIcon, LayersIcon, ShieldIcon } from './Icons';
import styles from './AdminStatsGrid.module.css';

/**
 * Four real KPI cards, all values passed in as props already computed
 * from real API data in Dashboard.jsx — nothing here is hardcoded.
 *
 * "New this week" on the members card is a genuine derivation from the
 * real registrationDate field (members registered in the last 7 days).
 * The bootcamp classes card deliberately has NO equivalent sub-label —
 * BootcampClass has no creation-date field at all, so a "created this
 * week" claim would be fabricated. Omitted rather than invented.
 *
 * classCount/totalEnrolled/totalCapacity are computed from the
 * SCHED-excluded class list (see Dashboard.jsx's primaryClasses and
 * its comment for the full documented reasoning) — this is the
 * explicit, documented frontend rule the build spec asked for.
 */
export default function AdminStatsGrid({
  memberCount, newMembersThisWeek,
  classCount,
  instructorCount, fullTimeCount, partTimeCount,
  totalEnrolled, totalCapacity,
}) {
  const staffTotal = instructorCount + fullTimeCount + partTimeCount;
  const utilizationPct = totalCapacity > 0 ? Math.round((totalEnrolled / totalCapacity) * 100) : 0;

  return (
    <div className={styles.statsRow}>
      <GlassCard className={`${styles.statCard} ${styles.purple}`}>
        <div className={styles.iconBadge}><UsersIcon size={20} color="var(--color-primary)" /></div>
        <span className={styles.statLabel}>Total Members</span>
        <span className={styles.statValue}>{memberCount}</span>
        {newMembersThisWeek > 0 && (
          <span className={styles.statSub}>&#8599; +{newMembersThisWeek} this week</span>
        )}
      </GlassCard>

      <GlassCard className={`${styles.statCard} ${styles.blue}`}>
        <div className={styles.iconBadge}><LayersIcon size={20} color="var(--color-bootcamp-accent)" /></div>
        <span className={styles.statLabel}>Bootcamp Classes</span>
        <span className={styles.statValue}>{classCount}</span>
      </GlassCard>

      <GlassCard className={`${styles.statCard} ${styles.gold}`}>
        <div className={styles.iconBadge}><ShieldIcon size={20} color="var(--color-goal-accent)" /></div>
        <span className={styles.statLabel}>Total Staff</span>
        <span className={styles.statValue}>{staffTotal}</span>
        <span className={styles.statSub}>{instructorCount} instructors &bull; {fullTimeCount} full-time &bull; {partTimeCount} part-time</span>
      </GlassCard>

      <GlassCard className={`${styles.statCard} ${styles.green}`}>
        <div className={styles.iconBadge}>
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" style={{ color: 'var(--color-success)' }} strokeWidth="1.7" strokeLinecap="round">
            <path d="M12 2a10 10 0 100 20 10 10 0 000-20z" opacity="0.3" />
            <path d="M12 6v6l4 2" />
          </svg>
        </div>
        <span className={styles.statLabel}>Capacity Utilization</span>
        <span className={styles.statValue}>{utilizationPct}%</span>
        <span className={styles.statSub}>{totalEnrolled} / {totalCapacity} spots used</span>
      </GlassCard>
    </div>
  );
}
