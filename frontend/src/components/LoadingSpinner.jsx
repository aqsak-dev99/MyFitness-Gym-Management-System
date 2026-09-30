import styles from './LoadingSpinner.module.css';

/** A page/section-level loading indicator — distinct from Button's inline spinner. */
export default function LoadingSpinner({ label = 'Loading…' }) {
  return (
    <div className={styles.wrapper}>
      <span className={styles.spinner} aria-hidden="true" />
      <span className={styles.label}>{label}</span>
    </div>
  );
}

