import styles from './SplashScreen.module.css';

const STARS = [
  [6, 10, 1.3, 0.8], [15, 5, 1, 0.5], [88, 8, 1.4, 0.75], [93, 15, 1, 0.5],
  [25, 18, 1, 0.55], [70, 12, 1.2, 0.6], [10, 30, 1, 0.45], [80, 25, 1.3, 0.65],
  [45, 6, 1, 0.5], [55, 20, 1, 0.45], [4, 45, 1.1, 0.5], [95, 40, 1, 0.5],
];

/**
 * Splash screen — the real robot asset, centered with a glowing ring
 * (matching the reference exactly), a hand-drawn logo mark (dumbbell +
 * pulse forming an M shape), and gym-equipment silhouettes for
 * atmosphere. Shown for exactly as long as App.jsx's real
 * isLoading-or-minimum-time state says to — nothing here controls its
 * own timing; this component is purely presentational.
 */
export default function SplashScreen() {
  return (
    <div className={styles.wrapper}>
      <svg className={styles.starsLayer} preserveAspectRatio="none">
        {STARS.map(([x, y, r, o], i) => (
          <circle key={i} cx={`${x}%`} cy={`${y}%`} r={r} fill="#ffffff" opacity={o} />
        ))}
      </svg>

      <svg className={styles.mountainLayer} viewBox="0 0 1000 260" preserveAspectRatio="xMidYMax slice">
        <polygon points="0,260 140,90 260,180 400,50 520,160 650,70 780,190 900,110 1000,260" fill="rgba(155,92,246,0.10)" />
        <polygon points="0,260 200,150 350,220 500,120 680,210 850,140 1000,260" fill="rgba(74,184,247,0.07)" />
      </svg>

      {/* Simple gym-equipment silhouettes flanking the composition */}
      <svg className={styles.equipLeft} viewBox="0 0 100 140" opacity="0.5">
        <g stroke="rgba(240,98,156,0.4)" strokeWidth="2" fill="none">
          <rect x="10" y="20" width="70" height="16" rx="3" />
          <rect x="10" y="44" width="70" height="16" rx="3" />
          <rect x="10" y="68" width="70" height="16" rx="3" />
        </g>
      </svg>
      <svg className={styles.equipRight} viewBox="0 0 100 140" opacity="0.5">
        <g stroke="rgba(74,184,247,0.4)" strokeWidth="2" fill="none">
          <rect x="20" y="100" width="60" height="22" rx="4" />
          <line x1="10" y1="60" x2="90" y2="60" />
          <circle cx="14" cy="60" r="12" />
          <circle cx="86" cy="60" r="12" />
        </g>
      </svg>

      <div className={styles.content}>
        <div className={styles.logoMark}>
          <svg width="64" height="40" viewBox="0 0 64 40" fill="none">
            <defs>
              <linearGradient id="logoGrad" x1="0%" y1="0%" x2="100%" y2="0%">
                <stop offset="0%" stopColor="#9b5cf6" />
                <stop offset="55%" stopColor="#f0629c" />
                <stop offset="100%" stopColor="#f5a623" />
              </linearGradient>
            </defs>
            <g stroke="url(#logoGrad)" strokeWidth="4" strokeLinecap="round" strokeLinejoin="round">
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

        <div className={styles.robotWrap}>
          <div className={styles.robotRing} />
          <img src="/assets/ai-mascot.png" alt="MyFitness AI Assistant" className={styles.robotImg} />
        </div>

        <p className={styles.loadingLabel}>LOADING</p>
        <div className={styles.progressTrack}>
          <div className={styles.progressFill} />
        </div>
        <p className={styles.loadingSub}>Preparing your fitness experience&hellip;</p>

        <div className={styles.chips}>
          <span className={styles.chip}>&#10084; Stay Consistent</span>
          <span className={styles.chipDivider} />
          <span className={styles.chip}>&#128170; Stay Strong</span>
          <span className={styles.chipDivider} />
          <span className={styles.chip}>&#11088; Be Your Best</span>
        </div>
      </div>
    </div>
  );
}