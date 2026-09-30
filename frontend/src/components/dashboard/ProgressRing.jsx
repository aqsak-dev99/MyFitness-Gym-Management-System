import { useEffect, useId, useState } from 'react';
import styles from './ProgressRing.module.css';

/**
 * Real SVG circular progress with a gradient stroke and a subtle glow —
 * the detail that most directly echoes the reference images' rings.
 * useId() (built into React, no new dependency) generates a unique
 * gradient id per instance, since SVG <defs> ids are document-scoped —
 * without this, multiple rings on one page would silently share (and
 * corrupt) each other's gradient.
 */
export default function ProgressRing({ percent, label, sublabel, colorFrom = '#34e0a1', colorTo = '#1a9d6f', size = 120 }) {
  const gradientId = useId();
  const strokeWidth = 9;
  const radius = (size - strokeWidth) / 2;
  const circumference = 2 * Math.PI * radius;

  const [animatedPercent, setAnimatedPercent] = useState(0);
  useEffect(() => {
    const id = requestAnimationFrame(() => setAnimatedPercent(percent));
    return () => cancelAnimationFrame(id);
  }, [percent]);

  const offset = circumference * (1 - animatedPercent / 100);

  return (
    <div className={styles.wrapper} style={{ width: size, height: size }}>
      <svg width={size} height={size} className={styles.svg}>
        <defs>
          <linearGradient id={gradientId} x1="0%" y1="0%" x2="100%" y2="100%">
            <stop offset="0%" stopColor={colorFrom} />
            <stop offset="100%" stopColor={colorTo} />
          </linearGradient>
        </defs>
        <circle
          cx={size / 2} cy={size / 2} r={radius}
          className={styles.track}
          strokeWidth={strokeWidth}
        />
        <circle
          cx={size / 2} cy={size / 2} r={radius}
          stroke={`url(#${gradientId})`}
          strokeWidth={strokeWidth}
          strokeDasharray={circumference}
          strokeDashoffset={offset}
          strokeLinecap="round"
          className={styles.progress}
        />
      </svg>
      <div className={styles.labelWrap}>
        <span className={styles.label}>{label}</span>
        {sublabel && <span className={styles.sublabel}>{sublabel}</span>}
      </div>
    </div>
  );
}