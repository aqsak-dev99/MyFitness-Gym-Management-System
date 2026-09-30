import { Link } from 'react-router-dom';
import { SparkleIcon, ArrowIcon } from './Icons';
import styles from './AdminAiBanner.module.css';

/**
 * The reference design shows a literal text input here, implying
 * inline chat. A rendered <input> that can't actually send a message
 * would itself be a fake-functional element — the same problem as the
 * Quick Actions buttons. Instead this whole banner is one real link to
 * the actual /ai-assistant page (already fully functional — general
 * ask, document RAG, bootcamp tool-calling, recommendations), so
 * clicking it goes somewhere genuine rather than nowhere.
 */
export default function AdminAiBanner() {
  return (
    <Link to="/ai-assistant" className={styles.banner}>
      <div className={styles.left}>
        <SparkleIcon size={18} color="var(--color-primary)" />
        <div>
          <span className={styles.title}>Need help managing your gym?</span>
          <span className={styles.sub}>Ask me anything about members, classes, staff or operations&hellip;</span>
        </div>
      </div>
      <span className={styles.cta}>
        Open AI Assistant <ArrowIcon size={14} />
      </span>

      <div className={styles.robotWrap}>
        <div className={styles.speechBubble}>
          Hi! I'm your AI assistant.<br />
          Ask me about members, classes, staff<br />
          or gym operations.
        </div>
        <img src="/assets/ai-mascot.png" alt="MyFitness AI Assistant" className={styles.robotImg} />
      </div>
    </Link>
  );
}