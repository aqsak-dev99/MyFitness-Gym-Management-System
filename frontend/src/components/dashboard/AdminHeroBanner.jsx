import styles from './AdminHeroBanner.module.css';

/**
 * The hero-banner concept from the reference layout discussed earlier —
 * scoped deliberately narrow: a photo banner at the top of the Admin
 * Dashboard only. Everything else on the dashboard (KPIs, Staff
 * Overview, Recent Members, Quick Actions, the AI banner) is completely
 * unchanged — this replaces only the previous plain-text header plus
 * the page's old subtle full-page background photo, which this now
 * supersedes for Admin specifically. The Member dashboard's own
 * background is untouched — a separate prop, a separate file.
 */
export default function AdminHeroBanner({ now }) {
  return (
    <div className={styles.banner}>
      <img src="/assets/admin-hero-banner.jpg" alt="" className={styles.image} />
      <div className={styles.scrim} />
      <div className={styles.content}>
        <h1 className={styles.greeting}>Welcome back, Admin</h1>
        <p className={styles.subtitle}>Here's what's happening at your gym today.</p>
      </div>
      <div className={styles.dateCard}>
        <span className={styles.dateLine}>
          {now.toLocaleDateString('en-GB', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' })}
        </span>
        <span className={styles.timeLine}>
          {now.toLocaleTimeString('en-GB', { hour: '2-digit', minute: '2-digit' })}
        </span>
      </div>
    </div>
  );
}