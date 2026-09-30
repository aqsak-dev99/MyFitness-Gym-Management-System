import styles from './Button.module.css';

/**
 * A single, reusable button — variant controls color/intent
 * (primary action vs. a plain secondary/text-style action),
 * isLoading disables it and swaps the label for a spinner
 * dot pattern, so every form in the app shows a consistent
 * "submitting" state instead of each page inventing its own.
 */
export default function Button({
  children,
  variant = 'primary',
  isLoading = false,
  type = 'button',
  ...rest
}) {
  return (
    <button
      type={type}
      className={`${styles.button} ${styles[variant]}`}
      disabled={isLoading || rest.disabled}
      {...rest}
    >
      {isLoading ? <span className={styles.spinner} aria-hidden="true" /> : children}
    </button>
  );
}