import { useState } from 'react';
import styles from './AiMascot.module.css';

/**
 * Floating AI Assistant mascot — uses the real provided PNG asset
 * directly, no SVG recreation. No dedicated "AI Assistant panel" UI
 * exists yet in this frontend, so the click action is deliberately
 * NOT a fabricated chat modal — it scrolls to and highlights the ONE
 * real, functional AI feature already on this page (the AI Bootcamp
 * Recommendation action inside QuickActions), leaving a clean, honest
 * hook for a real assistant panel to replace later rather than
 * inventing one now.
 */
export default function AiMascot() {
  const [isHovered, setIsHovered] = useState(false);

  function handleClick() {
    const target = document.getElementById('quick-actions-section');
    if (!target) return;
    target.scrollIntoView({ behavior: 'smooth', block: 'center' });
    target.classList.add(styles.highlightPulse);
    setTimeout(() => target.classList.remove(styles.highlightPulse), 1400);
  }

  return (
    <div
      className={styles.wrapper}
      onMouseEnter={() => setIsHovered(true)}
      onMouseLeave={() => setIsHovered(false)}
      onClick={handleClick}
      onKeyDown={(e) => (e.key === 'Enter' || e.key === ' ') && handleClick()}
      role="button"
      tabIndex={0}
      aria-label="AI Assistant — jump to AI features"
    >
      {isHovered && (
        <div className={styles.speechBubble}>I'm here to help! 👋</div>
      )}
      <div className={styles.glow} aria-hidden="true" />
      <img
        src="/assets/ai-mascot.png"
        alt="AI Assistant"
        className={`${styles.mascotImage} ${isHovered ? styles.wave : styles.idle}`}
      />
    </div>
  );
}