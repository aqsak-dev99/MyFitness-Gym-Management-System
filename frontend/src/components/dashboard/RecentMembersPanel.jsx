import { Link } from 'react-router-dom';
import GlassCard from './GlassCard';
import { ArrowIcon } from './Icons';
import styles from './RecentMembersPanel.module.css';

/**
 * Sorted by the real registrationDate field (ISO date string from the
 * backend), newest first, top 5. No fabricated dates — a member with
 * no real date to compare would simply not sort meaningfully, but
 * every real Member always has this field set (defaults to
 * LocalDate.now() at registration, confirmed in the model).
 */
export default function RecentMembersPanel({ members }) {
  const recent = [...members]
    .sort((a, b) => new Date(b.registrationDate) - new Date(a.registrationDate))
    .slice(0, 5);

  return (
    <GlassCard className={styles.card}>
      <div className={styles.header}>
        <span className={styles.eyebrow}>Recently Registered Members</span>
        <Link to="/members" className={styles.viewAll}>
          View all members <ArrowIcon size={13} />
        </Link>
      </div>

      <div className={styles.list}>
        {recent.map((m) => (
          <div key={m.memberId} className={styles.row}>
            <span className={styles.avatar}>{initials(m.name)}</span>
            <span className={styles.name}>{m.name}</span>
            <span className={styles.date}>
              Joined {new Date(m.registrationDate).toLocaleDateString('en-GB', { day: 'numeric', month: 'short', year: 'numeric' })}
            </span>
          </div>
        ))}
      </div>
    </GlassCard>
  );
}

function initials(name) {
  return name.split(' ').map((p) => p[0]).slice(0, 2).join('').toUpperCase();
}