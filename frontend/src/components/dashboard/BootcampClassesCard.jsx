import { Link } from 'react-router-dom';
import GlassCard from './GlassCard';
import { CalendarIcon } from './Icons';
import styles from './BootcampClassesCard.module.css';

/**
 * There's no direct "my enrolled classes" endpoint — enrolment only
 * exists as a participants list on the CLASS side. This card receives
 * the already-filtered list (Dashboard.jsx does the cross-referencing
 * against the real /bootcamp-classes response), keeping this component
 * itself simple and purely presentational.
 *
 * The dumbbell + bottle illustration is purely decorative (real SVG,
 * hand-drawn — no image asset, no invented data), matching the
 * reference's empty-state composition.
 */
export default function BootcampClassesCard({ classes }) {
  return (
    <GlassCard className={styles.card}>
      <div className={styles.header}>
        <div className={styles.badgeIcon}>
          <CalendarIcon size={16} color="var(--dash-accent-blue)" />
        </div>
        <span className={styles.eyebrow}>Your bootcamp classes</span>
      </div>

      {classes.length === 0 ? (
        <div className={styles.emptyRow}>
          <div className={styles.emptyText}>
            <p className={styles.empty}>You're not enrolled in any bootcamp classes yet.</p>
            <p className={styles.emptySub}>Explore our bootcamp classes and start your fitness journey today!</p>
          </div>

          <svg className={styles.illustration} viewBox="0 0 220 110" fill="none">
            <g opacity="0.9" stroke="var(--dash-accent-blue)" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round">
              <rect x="10" y="45" width="14" height="24" rx="3" />
              <rect x="26" y="50" width="8" height="14" rx="2" />
              <line x1="34" y1="57" x2="86" y2="57" />
              <rect x="86" y="50" width="8" height="14" rx="2" />
              <rect x="96" y="45" width="14" height="24" rx="3" />
              {/* water bottle */}
              <rect x="140" y="20" width="26" height="52" rx="6" />
              <rect x="148" y="10" width="10" height="12" rx="2" />
              <line x1="140" y1="38" x2="166" y2="38" />
            </g>
            <g opacity="0.6" fill="var(--dash-accent-blue)">
              <circle cx="190" cy="20" r="1.6" />
              <circle cx="200" cy="35" r="1.2" />
              <circle cx="195" cy="55" r="1.4" />
              <circle cx="15" cy="20" r="1.2" />
            </g>
          </svg>

          <Link to="/bootcamp-classes" className={styles.browseLink}>
            Browse Classes <span className={styles.arrow}>&rarr;</span>
          </Link>
        </div>
      ) : (
        <ul className={styles.list}>
          {classes.map((bc) => (
            <li key={bc.classId} className={styles.item}>
              <div>
                <div className={styles.className}>{bc.className}</div>
                <div className={styles.schedule}>{bc.schedule}</div>
              </div>
              <span className={styles.capacity}>{bc.currentEnrolments}/{bc.maxCapacity}</span>
            </li>
          ))}
        </ul>
      )}
    </GlassCard>
  );
}