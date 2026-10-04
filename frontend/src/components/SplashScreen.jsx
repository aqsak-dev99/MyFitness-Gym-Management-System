import styles from './SplashScreen.module.css';

/**
 * Splash screen — monochrome fluid-blob design (black / white / grey).
 * Three CSS-shaped blobs sit in the corners, leaving a dark centre for
 * the logo mark, wordmark, tagline and loading bar.
 *
 * Purely presentational: App.jsx's real isLoading-or-minimum-time state
 * decides how long this is shown. Nothing here controls its own timing,
 * which is why the loading bar is an indeterminate loop rather than a
 * fill that pretends to know the duration.
 */
export default function SplashScreen() {
  return (
    <div className={styles.wrapper} role="status" aria-label="Loading MyFitness">
      <div className={`${styles.blob} ${styles.blobTop}`} />
      <div className={`${styles.blob} ${styles.blobLeft}`} />
      <div className={`${styles.blob} ${styles.blobBottom}`} />

      <div className={styles.content}>
        <div className={styles.logoMark}>
          <svg width="64" height="40" viewBox="0 0 64 40" fill="none" aria-hidden="true">
            <defs>
              <linearGradient id="splashLogoGrad" x1="0%" y1="0%" x2="100%" y2="0%">
                <stop offset="0%" stopColor="#ffffff" />
                <stop offset="100%" stopColor="#9a9a9a" />
              </linearGradient>
            </defs>
            <g stroke="url(#splashLogoGrad)" strokeWidth="4" strokeLinecap="round" strokeLinejoin="round">
              <path d="M6 10v20M14 4v32" />
              <path d="M14 20h8l4-10 5 18 4-8h9" />
              <path d="M50 4v32M58 10v20" />
            </g>
          </svg>
        </div>

        <h1 className={styles.wordmark}>
          My<span className={styles.wordmarkAccent}>Fitness</span>
        </h1>
        <p className={styles.tagline}>Your Journey. Our Support. Stronger Together.</p>
        <div className={styles.taglineRule} />

        <p className={styles.loadingLabel}>LOADING</p>
        <div className={styles.progressTrack}>
          <div className={styles.progressFill} />
        </div>
        <p className={styles.loadingSub}>Preparing your fitness experience&hellip;</p>
      </div>
    </div>
  );
}