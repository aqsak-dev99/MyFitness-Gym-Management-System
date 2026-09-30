import { createContext, useCallback, useContext, useRef, useState } from 'react';
import styles from './Toast.module.css';

const ToastContext = createContext(null);

/**
 * One small, reusable toast system — built because none existed
 * anywhere in the project (confirmed by inspection before writing
 * this). Matches ThemeProvider/AuthProvider's existing Context
 * pattern rather than introducing a different style for one feature.
 *
 * Deliberately NOT a UI library — plain React state + CSS Modules,
 * consistent with how every other component in this project is built.
 */
export function ToastProvider({ children }) {
  const [toasts, setToasts] = useState([]);
  const nextId = useRef(0);

  const showToast = useCallback((message, type = 'success') => {
    const id = nextId.current++;
    setToasts((current) => [...current, { id, message, type }]);
    // Auto-dismiss — 4s is enough to read a short confirmation without
    // toasts piling up if several actions happen in quick succession.
    setTimeout(() => {
      setToasts((current) => current.filter((t) => t.id !== id));
    }, 4000);
  }, []);

  function dismiss(id) {
    setToasts((current) => current.filter((t) => t.id !== id));
  }

  return (
    <ToastContext.Provider value={{ showToast }}>
      {children}
      <div className={styles.container} aria-live="polite">
        {toasts.map((t) => (
          <div
            key={t.id}
            className={`${styles.toast} ${t.type === 'error' ? styles.error : styles.success}`}
            onClick={() => dismiss(t.id)}
          >
            {t.message}
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  );
}

export function useToast() {
  const context = useContext(ToastContext);
  if (!context) {
    throw new Error('useToast must be used within a ToastProvider');
  }
  return context;
}