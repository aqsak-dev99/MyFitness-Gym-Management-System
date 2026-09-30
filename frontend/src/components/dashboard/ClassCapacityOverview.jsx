import { Link } from 'react-router-dom';
import GlassCard from './GlassCard';
import { ArrowIcon } from './Icons';
import styles from './ClassCapacityOverview.module.css';

/**
 * Real utilization computed from currentEnrolments/maxCapacity per
 * class — not the backend's `full` boolean alone, since that's only
 * true at exactly 100%. Status thresholds (Full/Near/Available) are a
 * genuine frontend display rule, applied consistently, not inferred
 * from any backend field that doesn't already mean this.
 *
 * `classes` here is expected to already be the SCHED-excluded list —
 * see Dashboard.jsx's primaryClasses filter and its comment for the
 * full explanation of that exclusion rule.
 */
function statusFor(pct) {
  if (pct >= 100) return { label: 'FULL', className: 'full' };
  if (pct >= 80) return { label: 'NEAR CAPACITY', className: 'near' };
  return { label: 'AVAILABLE', className: 'available' };
}

const STATUS_ORDER = { full: 0, near: 1, available: 2 };

export default function ClassCapacityOverview({ classes }) {
  const rows = classes
    .map((c) => {
      const pct = c.maxCapacity > 0 ? Math.round((c.currentEnrolments / c.maxCapacity) * 100) : 0;
      return { ...c, pct, status: statusFor(pct) };
    })
    .sort((a, b) => STATUS_ORDER[a.status.className] - STATUS_ORDER[b.status.className] || b.pct - a.pct);

  return (
    <GlassCard className={styles.card}>
      <div className={styles.header}>
        <span className={styles.eyebrow}>Class Capacity Overview</span>
        <Link to="/bootcamp-classes" className={styles.viewAll}>
          View all classes <ArrowIcon size={13} />
        </Link>
      </div>

      <div className={styles.list}>
        {rows.map((c) => (
          <div key={c.classId} className={styles.row}>
            <div className={styles.rowTop}>
              <span className={styles.className}>
                <span className={styles.classId}>{c.classId}</span> — {c.className} ({c.schedule})
              </span>
              <span className={`${styles.statusBadge} ${styles[c.status.className]}`}>{c.status.label}</span>
            </div>
            <div className={styles.rowBottom}>
              <div className={styles.barTrack}>
                <div className={`${styles.barFill} ${styles[c.status.className]}`} style={{ width: `${Math.min(c.pct, 100)}%` }} />
              </div>
              <span className={styles.counts}>{c.currentEnrolments}/{c.maxCapacity}</span>
              <span className={styles.pct}>{c.pct}%</span>
            </div>
          </div>
        ))}
      </div>

      <div className={styles.legend}>
        <span><i className={`${styles.dot} ${styles.full}`} /> Full (100%)</span>
        <span><i className={`${styles.dot} ${styles.near}`} /> Near Capacity (80%+)</span>
        <span><i className={`${styles.dot} ${styles.available}`} /> Available</span>
      </div>
    </GlassCard>
  );
}