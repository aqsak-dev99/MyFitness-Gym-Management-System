import styles from './GlassCard.module.css';

/**
 * GlassCard — the single frosted-glass surface every dashboard card
 * builds on. `hero` adds extra padding for cards meant to visually
 * dominate (MembershipCard); everything else about the glass treatment
 * stays identical across all cards, which is what keeps the whole
 * dashboard feeling cohesive rather than assembled from mismatched pieces.
 */
export default function GlassCard({ children, className = '', hero = false, ...rest }) {
  return (
    <div className={`${styles.card} ${hero ? styles.hero : ''} ${className}`} {...rest}>
      {children}
    </div>
  );
}