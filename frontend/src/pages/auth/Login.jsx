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

export default function Login() {
  const { login } = useAuth();
  const navigate = useNavigate();

  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);

  // Public demo accounts (also listed in the README). They exist so
  // recruiters and visitors can try both sides of the app in one click.
  const DEMO_ACCOUNTS = [
    { label: 'Try as admin', username: 'demo_admin', password: 'DemoPass123' },
    { label: 'Try as member', username: 'dm01_demo', password: 'DemoPass123' },
  ];

  async function signIn(user, pass) {
    setError('');
    setIsSubmitting(true);
    try {
      await login(user, pass);
      navigate('/');
    } catch (err) {
      // InvalidCredentialsException deliberately returns the SAME
      // message whether the username doesn't exist or the password is
      // wrong (see AuthService) — this just displays that real message
      // as-is, not a frontend-invented one.
      const message = err instanceof ApiError ? err.message : 'Something went wrong. Please try again.';
      setError(message);
    } finally {
      setIsSubmitting(false);
    }
  }

  function handleSubmit(event) {
    event.preventDefault();
    signIn(username, password);
  }

  function tryDemo(account) {
    setUsername(account.username);
    setPassword(account.password);
    signIn(account.username, account.password);
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
            <div className={styles.cardMark}><img src="/assets/logo.png" alt="MyFitness" width={24} height={24} style={{ objectFit: 'contain' }} /></div>
            <span className={styles.cardBrand}>MyFitness</span>
          </div>

          <h2 className={styles.title}>Welcome back</h2>
          <p className={styles.subtitle}>
            Don't have an account? <Link to="/register">Create one</Link>
          </p>

          <form onSubmit={handleSubmit} className="stack">
            <ErrorMessage>{error}</ErrorMessage>

            <TextField
              id="username"
              label="Username"
              type="text"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              autoComplete="username"
              required
            />
            <TextField
              id="password"
              label="Password"
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              autoComplete="current-password"
              required
            />

            <Button type="submit" isLoading={isSubmitting}>
              Log In
            </Button>
          </form>

          <div className={styles.demoBox}>
            <p className={styles.demoTitle}>Just looking around? Use a demo account</p>
            <div className={styles.demoButtons}>
              {DEMO_ACCOUNTS.map((account) => (
                <button
                  key={account.username}
                  type="button"
                  className={styles.demoButton}
                  onClick={() => tryDemo(account)}
                  disabled={isSubmitting}
                >
                  {account.label}
                </button>
              ))}
            </div>
            <p className={styles.demoNote}>
              Admin: demo_admin · Member: dm01_demo · Password: DemoPass123
            </p>
          </div>
        </div>
      </div>
      </div>
    </div>
  );
}
