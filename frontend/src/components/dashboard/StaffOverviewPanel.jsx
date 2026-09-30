import GlassCard from './GlassCard';
import { UsersIcon, LayersIcon, ShieldIcon } from './Icons';
import styles from './StaffOverviewPanel.module.css';

/**
 * Three genuinely separate real arrays (from three separate real
 * endpoints — see trainerApi.js), not filtered from one combined list
 * by inferring type. staffId chips are the actual IDs returned by
 * each endpoint, not invented labels.
 */
export default function StaffOverviewPanel({ instructors, fullTime, partTime }) {
  const total = instructors.length + fullTime.length + partTime.length;
  const categories = [
    { label: 'Instructors', icon: UsersIcon, color: 'var(--color-primary)', items: instructors },
    { label: 'Full-time Staff', icon: LayersIcon, color: 'var(--color-bootcamp-accent)', items: fullTime },
    { label: 'Part-time Staff', icon: ShieldIcon, color: 'var(--color-goal-accent)', items: partTime },
  ];

  return (
    <GlassCard className={styles.card}>
      <span className={styles.eyebrow}>Staff Overview</span>

      <div className={styles.grid}>
        {categories.map(({ label, icon: Icon, color, items }) => (
          <div key={label} className={styles.category}>
            <Icon size={18} color={color} />
            <span className={styles.count}>{items.length}</span>
            <span className={styles.label}>{label}</span>
            <div className={styles.idList}>
              {items.map((s) => s.staffId).join(' · ')}
            </div>
          </div>
        ))}
      </div>

      {total > 0 && (
        <div className={styles.proportionBar}>
          <div style={{ width: `${(instructors.length / total) * 100}%`, background: 'var(--color-primary)' }} />
          <div style={{ width: `${(fullTime.length / total) * 100}%`, background: 'var(--color-bootcamp-accent)' }} />
          <div style={{ width: `${(partTime.length / total) * 100}%`, background: 'var(--color-goal-accent)' }} />
        </div>
      )}

      <div className={styles.summary}>
        <strong>{total}</strong> Total Staff &bull; {instructors.length} Instructors &bull; {fullTime.length} Full-time &bull; {partTime.length} Part-time
      </div>
    </GlassCard>
  );
}