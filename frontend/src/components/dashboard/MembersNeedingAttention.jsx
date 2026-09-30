import { Link } from 'react-router-dom';
import GlassCard from './GlassCard';
import { ArrowIcon } from './Icons';
import styles from './MembersNeedingAttention.module.css';

/**
 * Two genuinely separate real categories, not inferred from each
 * other: `membership === null` (no membership assigned at all) vs
 * `membership.frozen === true` (has a real membership, currently
 * frozen). Both are real fields already returned by GET /api/members.
 * A member could theoretically appear in only one category — there's
 * no overlap, since a frozen membership is by definition non-null.
 */
export default function MembersNeedingAttention({ members }) {
  const noMembership = members.filter((m) => !m.membership);
  const frozen = members.filter((m) => m.membership && m.membership.frozen);
  const total = noMembership.length + frozen.length;

  return (
    <GlassCard className={styles.card}>
      <div className={styles.header}>
        <span className={styles.eyebrow}>Members Needing Attention</span>
        <Link to="/members" className={styles.viewAll}>
          View all members <ArrowIcon size={13} />
        </Link>
      </div>

      {total === 0 ? (
        <p className={styles.empty}>All members are in good standing — nothing needs attention right now.</p>
      ) : (
        <div className={styles.list}>
          {noMembership.length > 0 && (
            <div className={styles.groupBanner}>
              <span className={styles.groupTitle}>
                {noMembership.length} member{noMembership.length === 1 ? '' : 's'} without a membership
              </span>
              <span className={styles.groupSub}>These members don't have an active membership.</span>
            </div>
          )}
          {noMembership.map((m) => (
            <div key={m.memberId} className={styles.row}>
              <span className={styles.avatar}>{initials(m.name)}</span>
              <div className={styles.rowInfo}>
                <span className={styles.name}>{m.name}</span>
                <span className={styles.status}>No membership assigned</span>
              </div>
              <span className={styles.memberId}>{m.memberId}</span>
            </div>
          ))}

          {frozen.length > 0 && (
            <div className={`${styles.groupBanner} ${styles.frozenBanner}`}>
              <span className={styles.groupTitle}>
                {frozen.length} frozen membership{frozen.length === 1 ? '' : 's'}
              </span>
            </div>
          )}
          {frozen.map((m) => (
            <div key={m.memberId} className={styles.row}>
              <span className={styles.avatar}>{initials(m.name)}</span>
              <div className={styles.rowInfo}>
                <span className={styles.name}>{m.name}</span>
                <span className={styles.statusFrozen}>Membership frozen</span>
              </div>
              <span className={styles.memberId}>{m.memberId}</span>
            </div>
          ))}
        </div>
      )}
    </GlassCard>
  );
}

function initials(name) {
  return name.split(' ').map((p) => p[0]).slice(0, 2).join('').toUpperCase();
}