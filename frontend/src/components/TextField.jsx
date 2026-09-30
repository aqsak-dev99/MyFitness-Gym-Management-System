import styles from './TextField.module.css';

/**
 * A labeled text input with an associated error message slot.
 * fieldError is specifically for Bean Validation's per-field
 * response shape (the backend's {fieldErrors: {email: "..."}}
 * format) — passing the field's own message here, rather than
 * every form re-inventing how to show a per-field error.
 */
export default function TextField({ label, id, fieldError, ...rest }) {
  return (
    <div className={styles.field}>
      <label htmlFor={id} className={styles.label}>{label}</label>
      <input id={id} className={`${styles.input} ${fieldError ? styles.inputError : ''}`} {...rest} />
      {fieldError && <span className={styles.errorText}>{fieldError}</span>}
    </div>
  );
}