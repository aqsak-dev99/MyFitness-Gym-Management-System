import styles from './ConfirmDialog.module.css';

/**
 * One small, reusable confirmation dialog for genuinely destructive
 * actions — not used for ordinary navigation or harmless saves.
 * Plain React + CSS Modules, matching the rest of this project's flat
 * components/ convention (Button.jsx, ErrorMessage.jsx, etc.); no new
 * dependency, no new subfolder.
 *
 * Usage: render conditionally when `open` is true, pass the action-
 * specific copy and a confirm handler.
 */
export default function ConfirmDialog({ open, title, message, confirmLabel = 'Confirm', onConfirm, onCancel }) {
  if (!open) return null;

  return (
    <div className={styles.overlay} onClick={onCancel}>
      <div className={styles.dialog} onClick={(e) => e.stopPropagation()}>
        <h3 className={styles.title}>{title}</h3>
        <p className={styles.message}>{message}</p>
        <div className={styles.actions}>
          <button className={styles.cancelBtn} onClick={onCancel}>Cancel</button>
          <button className={styles.confirmBtn} onClick={onConfirm}>{confirmLabel}</button>
        </div>
      </div>
    </div>
  );
}