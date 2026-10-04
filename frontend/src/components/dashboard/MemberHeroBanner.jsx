import styles from './MemberHeroBanner.module.css';

/**
 * Member counterpart to AdminHeroBanner — same pattern (a photo of the
 * gym with the greeting over it), which is what the admin portal uses
 * and what reads as a finished product. Deliberately its own component
 * and stylesheet so the admin banner is untouched.
 *
 * The subtitle is real data, not filler: it mirrors the logic
 * MembershipCard already uses (frozen -> "Frozen", otherwise "Active"),
 * so the two never disagree on one screen.
 */
export default function MemberHeroBanner({ name, membership, now }) {
  const firstName = name ? name.trim().split(/\s+/)[0] : '';

  const subtitle = membership
    ? `${membership.membershipType} membership · ${membership.frozen ? 'Frozen' : 'Active'}`
    : 'No membership assigned yet';

  return (
    <div className={styles.banner}>
      <img src="/assets/gym-hero.jpg" alt="" className={styles.image} decoding="async" />
      <div className={styles.scrim} />
      <div className={styles.content}>
        <h1 className={styles.greeting}>
          {firstName ? `Welcome back, ${firstName}` : 'Welcome back'}
        </h1>
        <p className={styles.subtitle}>{subtitle}</p>
      </div>
      <div className={styles.dateChip}>
        {now.toLocaleDateString('en-GB', { weekday: 'long', day: 'numeric', month: 'long' })}
      </div>
    </div>
  );
}