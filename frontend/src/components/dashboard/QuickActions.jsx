import { Link } from 'react-router-dom';
import GlassCard from './GlassCard';
import { CalendarIcon, SparkleIcon, ProfileGlyph, TargetIcon, ArrowIcon } from './Icons';
import styles from './QuickActions.module.css';

/**
 * The four cards specified — all genuine navigation to real routes
 * (Membership/MyGoal/AiAssistant/BootcampClasses all exist as actual
 * pages). The previous "AI Bootcamp Recommendation" inline action
 * moved into the AI Assistant page itself as a fourth mode, rather
 * than being dropped — real functionality preserved, just relocated
 * to match this exact new information architecture.
 */
export default function QuickActions() {
  const actions = [
    {
      to: '/bootcamp-classes', icon: CalendarIcon, title: 'Browse Classes',
      sub: 'Find your next bootcamp.', color: 'blue',
    },
    {
      to: '/ai-assistant', icon: SparkleIcon, title: 'AI Assistant',
      sub: 'Get answers and fitness guidance.', color: 'pink',
    },
    {
      to: '/profile', icon: ProfileGlyph, title: 'My Profile',
      sub: 'View your profile information.', color: 'purple',
    },
    {
      to: '/my-goal', icon: TargetIcon, title: 'My Goal',
      sub: 'Update and track your goal.', color: 'gold',
    },
  ];

  return (
    <GlassCard>
      <div className={styles.header}>
        <div className={styles.badgeIcon}>
          <SparkleIcon size={16} color="var(--color-primary)" />
        </div>
        <div>
          <span className={styles.eyebrow}>Quick actions</span>
          <p className={styles.subtitle}>Personalized recommendations and tools to help you reach your goals</p>
        </div>
      </div>

      <div className={styles.actionGrid}>
        {actions.map(({ to, icon: Icon, title, sub, color }) => (
          <Link key={to} to={to} className={`${styles.actionCard} ${styles[color]}`}>
            <div className={styles.actionIcon}><Icon size={20} /></div>
            <span className={styles.actionCardTitle}>{title}</span>
            <span className={styles.actionCardSub}>{sub}</span>
            <span className={`${styles.arrowBtn} ${styles[color + 'Arrow']}`}><ArrowIcon size={14} /></span>
          </Link>
        ))}
      </div>
    </GlassCard>
  );
}