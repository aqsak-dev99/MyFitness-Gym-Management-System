import styles from './PageShell.module.css';

// Fixed, hand-placed positions rather than random-on-every-render —
// avoids any flicker/mismatch and gives control over a natural,
// non-repeating scatter rather than an obviously regular pattern.
const STARS = [
  [4, 8, 1.4, 0.9], [12, 3, 1, 0.6], [22, 14, 1.8, 0.8], [31, 5, 1, 0.5],
  [45, 18, 1.3, 0.7], [58, 6, 1, 0.55], [67, 22, 1.6, 0.85], [78, 9, 1, 0.5],
  [88, 16, 1.4, 0.75], [95, 4, 1.1, 0.6], [8, 28, 1, 0.5], [19, 34, 1.5, 0.7],
  [37, 30, 1, 0.55], [52, 36, 1.7, 0.8], [64, 32, 1, 0.5], [73, 40, 1.3, 0.65],
  [84, 28, 1, 0.55], [92, 38, 1.5, 0.7], [15, 46, 1, 0.5], [28, 52, 1.4, 0.75],
  [40, 48, 1, 0.5], [56, 54, 1.2, 0.6], [70, 50, 1, 0.5], [86, 56, 1.6, 0.8],
  [96, 46, 1, 0.55],
];

/**
 * Purely decorative — a real star field (SVG dots, not an image) and a
 * mountain silhouette, both matching the reference's compositional
 * elements that were previously missing entirely. No data, no logic,
 * fixed-position so it doesn't interfere with page scroll or content.
 */
export default function BackgroundDecoration() {
  return (
    <>
      <svg className={styles.starsLayer} preserveAspectRatio="none">
        {STARS.map(([x, y, r, o], i) => (
          <circle key={i} cx={`${x}%`} cy={`${y}%`} r={r} fill="#ffffff" opacity={o} />
        ))}
      </svg>

      <svg className={styles.mountainLayer} viewBox="0 0 400 300" preserveAspectRatio="xMaxYMax meet">
        <defs>
          <linearGradient id="mtnGlow" x1="0%" y1="0%" x2="100%" y2="100%">
            <stop offset="0%" stopColor="#9b5cf6" />
            <stop offset="100%" stopColor="#4ab8f7" />
          </linearGradient>
        </defs>
        <polygon points="60,300 180,120 260,220 400,60 400,300" fill="rgba(155,92,246,0.05)" stroke="url(#mtnGlow)" strokeWidth="1.2" opacity="0.5" />
        <polygon points="0,300 120,180 220,260 400,140 400,300" fill="rgba(74,184,247,0.06)" stroke="url(#mtnGlow)" strokeWidth="1" opacity="0.35" />
      </svg>
    </>
  );
}