import styles from './ErrorMessage.module.css';

/**
 * A top-level error banner for a whole form/page — distinct
 * from TextField's per-field error text. Used for errors that
 * aren't tied to one specific input, like the backend's generic
 * "Invalid username or password." from a failed login attempt.
 */
export default function ErrorMessage({ children }) {
  if (!children) return null;
  return (
    <div className={styles.error} role="alert">
      {children}
    </div>
  );
}