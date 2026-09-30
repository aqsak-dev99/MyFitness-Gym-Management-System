import { Link } from 'react-router-dom';
import GlassCard from './GlassCard';
import { CalendarIcon, ArrowIcon } from './Icons';
import styles from './BootcampSummaryCard.module.css';

export default function BootcampSummaryCard({ count }) {
  return (
    <GlassCard className={styles.card}>
      <div className={styles.header}>
        <div className={styles.badgeIcon}>
          <CalendarIcon size={16} color="var(--color-bootcamp-accent)" />
        </div>
        <span className={styles.eyebrow}>Bootcamp Classes</span>
      </div>

      <div className={styles.countRow}>
        <span className={styles.count}>{count}</span>
        <span className={styles.countLabel}>{count === 1 ? 'Class Joined' : 'Classes Joined'}</span>
      </div>

      <Link to="/bootcamp-classes" className={styles.link}>
        Explore Bootcamps <ArrowIcon size={13} />
      </Link>
    </GlassCard>
  );
}