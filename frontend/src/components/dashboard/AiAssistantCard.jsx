import { Link } from 'react-router-dom';
import GlassCard from './GlassCard';
import { ArrowIcon } from './Icons';
import styles from './AiAssistantCard.module.css';

export default function AiAssistantCard() {
  return (
    <GlassCard className={styles.card}>
      <div className={styles.robotWrap}>
        <div className={styles.glow} />
        <img src="/assets/ai-mascot.png" alt="MyFitness AI Assistant" className={styles.robotImg} />
      </div>

      <h3 className={styles.title}>Need a little help?</h3>
      <p className={styles.body}>Your AI fitness assistant is ready. Ask me about:</p>
      <ul className={styles.list}>
        <li>Workouts</li>
        <li>Nutrition</li>
        <li>Bootcamp Classes</li>
      </ul>

      <Link to="/ai-assistant" className={styles.cta}>
        Ask AI Assistant <ArrowIcon size={14} />
      </Link>
    </GlassCard>
  );
}