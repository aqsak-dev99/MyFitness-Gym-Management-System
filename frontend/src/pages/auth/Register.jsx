import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../../auth/AuthContext';
import { ApiError } from '../../api/client';
import Button from '../../components/Button';
import TextField from '../../components/TextField';
import ErrorMessage from '../../components/ErrorMessage';
import styles from './AuthPage.module.css';

function DumbbellMark() {
  return (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.4" strokeLinecap="round">
      <path d="M4 12h16" />
      <path d="M4 8v8M2 9v6M20 8v8M22 9v6" strokeLinejoin="round" />
    </svg>
  );
}

/**
 * Registration form, designed around a real backend behavior confirmed
 * by direct inspection, not assumption: AuthService.register() never
 * validates that linkedMemberId corresponds to an actual Member row —
 * it just stores whatever string is given. Creating a Member is a
 * separate, ADMIN-only action entirely. Rather than pretend otherwise,
 * this form is upfront about it: linkedMemberId is genuinely optional,
 * with a plain-language note explaining what it means to leave it blank.
 */
export default function Register() {
  const { register } = useAuth();
  const navigate = useNavigate();

  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [role, setRole] = useState('MEMBER');
  const [linkedMemberId, setLinkedMemberId] = useState('');

  const [error, setError] = useState('');
  const [fieldErrors, setFieldErrors] = useState({});
  const [isSubmitting, setIsSubmitting] = useState(false);

  async function handleSubmit(event) {
    event.preventDefault();
    setError('');
    setFieldErrors({});

    if (password !== confirmPassword) {
      setError('Passwords do not match.');
      return;
    }

    setIsSubmitting(true);
    try {
      await register({
        username,
        password,
        role,
        linkedMemberId: role === 'MEMBER' && linkedMemberId.trim() ? linkedMemberId.trim() : null,
      });
      navigate('/');
    } catch (err) {
      if (err instanceof ApiError && err.fieldErrors) {
        // Bean Validation failure — the backend's real shape is a map
        // of field name -> message, e.g. { password: "size must be
        // between 6 and 2147483647" }. Route each straight to its field.
        setFieldErrors(err.fieldErrors);
      } else if (err instanceof ApiError) {
        // Everything else (DuplicateUserException -> "Username already
        // taken: ...", etc.) — a single, top-level message.
        setError(err.message);
      } else {
        setError('Something went wrong. Please try again.');
      }
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <div className={styles.authRoot}>
      <div className={styles.shell}>
      <div className={styles.brandPanel}>
        <img src="/assets/gym-hero.jpg" alt="" className={styles.brandPhoto} />
        <div className={styles.brandScrim} />
        <div className={styles.brandTop}>
          <DumbbellMark />
          <span>MyFitness</span>
        </div>
        <p className={styles.brandCaption}>Stronger, healthier, happier.</p>
      </div>

      <div className={styles.formPanel}>
        <div className={styles.card}>
          <div className={styles.cardHeader}>
            <div className={styles.cardMark}><img src="/assets/logo.png" alt="MyFitness" width={24} height={24} /></div>
            <span className={styles.cardBrand}>MyFitness</span>
          </div>

          <h2 className={styles.title}>Create an account</h2>
          <p className={styles.subtitle}>
            Already have an account? <Link to="/login">Log in</Link>
          </p>

          <form onSubmit={handleSubmit} className="stack">
          <ErrorMessage>{error}</ErrorMessage>

          <TextField
            id="username"
            label="Username"
            type="text"
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            fieldError={fieldErrors.username}
            autoComplete="username"
            required
          />
          <TextField
            id="password"
            label="Password"
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            fieldError={fieldErrors.password}
            autoComplete="new-password"
            minLength={6}
            required
          />
          <TextField
            id="confirmPassword"
            label="Confirm password"
            type="password"
            value={confirmPassword}
            onChange={(e) => setConfirmPassword(e.target.value)}
            autoComplete="new-password"
            required
          />

          <div className={styles.roleGroup}>
            <span>Account type</span>
            <div className={styles.roleOptions}>
              <label className={styles.roleOption}>
                <input
                  type="radio"
                  name="role"
                  value="MEMBER"
                  checked={role === 'MEMBER'}
                  onChange={() => setRole('MEMBER')}
                />
                Member
              </label>
              <label className={styles.roleOption}>
                <input
                  type="radio"
                  name="role"
                  value="ADMIN"
                  checked={role === 'ADMIN'}
                  onChange={() => setRole('ADMIN')}
                />
                Admin (staff)
              </label>
            </div>
          </div>

          {role === 'MEMBER' && (
            <>
              <TextField
                id="linkedMemberId"
                label="Member ID (optional)"
                type="text"
                value={linkedMemberId}
                onChange={(e) => setLinkedMemberId(e.target.value)}
                fieldError={fieldErrors.linkedMemberId}
                placeholder="e.g. M001"
              />
              <p className={styles.hint}>
                If gym staff have already registered you as a member, enter your Member ID
                to link this login to your profile. If you don't have one yet, leave this
                blank — you can still create an account now.
              </p>
            </>
          )}

          <Button type="submit" isLoading={isSubmitting}>
            Create account
          </Button>
        </form>
        </div>
      </div>
      </div>
    </div>
  );
}
