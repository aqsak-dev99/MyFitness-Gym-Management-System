import GlassCard from './GlassCard';
import { ProfileGlyph } from './Icons';
import styles from './EmptyProfileState.module.css';

/**
 * The genuine empty state for a MEMBER account with no linkedMemberId
 * — same real condition and message as before. Restyled to use the
 * hero card treatment and a real icon (not emoji), so it reads as part
 * of the same product rather than a bare fallback screen.
 */
export default function EmptyProfileState({ username }) {
  return (
    <GlassCard hero className={styles.card}>
      <div className={styles.iconGlow}>
        <ProfileGlyph size={36} color="var(--dash-accent-green)" />
      </div>
      <h2 className={styles.title}>No member profile linked</h2>
      <p className={styles.body}>
        Your account, <strong>{username}</strong>, isn't linked to a member profile yet.
        Membership status, fitness goals, and personalized recommendations will appear here
        once a profile is linked. Contact gym staff to get set up.
      </p>
    </GlassCard>
  );
}