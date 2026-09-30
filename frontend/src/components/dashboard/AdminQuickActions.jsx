import { Link } from 'react-router-dom';
import GlassCard from './GlassCard';
import { UsersIcon, CalendarIcon, ShieldIcon, ArrowIcon } from './Icons';
import styles from './AdminQuickActions.module.css';

/**
 * All four now link to real, working management pages — AdminMembers,
 * AdminClasses, and AdminStaff. No "Coming Soon" left: each of these
 * genuinely lets an admin add/edit/deactivate real records against the
 * real database.
 */
export default function AdminQuickActions() {
  const actions = [
    { to: '/members', icon: UsersIcon, title: 'Manage Members', sub: 'Add, edit, and deactivate members', color: 'purple' },
    { to: '/admin/classes', icon: CalendarIcon, title: 'Manage Bootcamp Classes', sub: 'Create, edit, and manage enrolments', color: 'blue' },
    { to: '/staff', icon: ShieldIcon, title: 'Manage Staff', sub: 'Add, edit, and deactivate staff', color: 'gold' },
    { to: '/members', icon: UsersIcon, title: 'Manage Memberships', sub: 'Assign, freeze, or update memberships', color: 'green' },
  ];

  return (
    <GlassCard className={styles.card}>
      <span className={styles.eyebrow}>Quick Actions</span>
      <div className={styles.list}>
        {actions.map(({ to, icon: Icon, title, sub, color }, i) => (
          <Link key={title + i} to={to} className={`${styles.action} ${styles[color]}`}>
            <div className={styles.icon}><Icon size={18} /></div>
            <div className={styles.text}>
              <span className={styles.title}>{title}</span>
              <span className={styles.sub}>{sub}</span>
            </div>
            <ArrowIcon size={14} />
          </Link>
        ))}
      </div>
    </GlassCard>
  );
}