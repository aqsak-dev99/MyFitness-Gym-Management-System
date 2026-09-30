import { useAuth } from '../auth/AuthContext';
import Button from '../components/Button';
import styles from './AuthVerification.module.css';

/**
 * TEMPORARY placeholder — exists only to verify the full auth chain
 * (register/login -> token stored -> protected route allows access ->
 * user data displayed) end to end against the real backend, before the
 * real Dashboard gets built in the next phase. Nothing here is meant
 * to be the final UI.
 */
export default function AuthVerification() {
  const { user, logout } = useAuth();

  return (
    <div className="page-container">
      <div className={styles.card}>
        <p className={styles.badge}>Auth flow verified ✓</p>
        <h1>Welcome, {user.username}</h1>
        <dl className={styles.details}>
          <dt>User ID</dt>
          <dd>{user.userId}</dd>
          <dt>Role</dt>
          <dd>{user.role}</dd>
          <dt>Linked member</dt>
          <dd>{user.linkedMemberId || 'None'}</dd>
        </dl>
        <p className={styles.note}>
          This is a temporary screen to confirm authentication works end to end.
          The real Dashboard replaces this in the next phase.
        </p>
        <Button variant="secondary" onClick={logout}>Log out</Button>
      </div>
    </div>
  );
}