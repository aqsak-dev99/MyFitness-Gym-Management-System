import { useState } from 'react';
import { NavLink } from 'react-router-dom';
import { useAuth } from '../../auth/AuthContext';
import { useTheme } from '../../theme/ThemeContext';
import {
  CalendarIcon, SparkleIcon, ProfileGlyph, LayersIcon,
  SunIcon, MoonIcon, CardIcon, TargetIcon, UsersIcon, ShieldIcon, RevenueIcon, ReportsIcon,
} from '../dashboard/Icons';
import styles from './NavBar.module.css';

/**
 * A real vertical sidebar now, not a top bar — same component/export
 * (avoids an unnecessary rename across every file that imports it),
 * completely rebuilt internal structure. Dashboard, Bootcamp Classes,
 * and AI Assistant are shared, real destinations for both roles.
 * Profile/Membership/My Goal are MEMBER-only — an ADMIN account has no
 * linkedMemberId, so these would just route to the same empty-profile
 * state every time, which isn't a useful link to show, not a real gap.
 *
 * Mobile doesn't just shrink this — under 860px it collapses to a
 * slim top bar with a menu button that reveals the same nav as a
 * slide-in overlay panel, a genuinely different layout, not a
 * squeezed version of the desktop one.
 */
export default function NavBar() {
  const { user, logout } = useAuth();
  const { theme, toggleTheme } = useTheme();
  const [isMobileOpen, setIsMobileOpen] = useState(false);
  const isAdmin = user.role === 'ADMIN';

  const navLink = ({ isActive }) => `${styles.navLink} ${isActive ? styles.navLinkActive : ''}`;

  const links = (
    <>
      <NavLink to="/" end className={navLink} onClick={() => setIsMobileOpen(false)}>
        <LayersIcon size={17} /> Dashboard
      </NavLink>
      <NavLink to={isAdmin ? '/admin/classes' : '/bootcamp-classes'} className={navLink} onClick={() => setIsMobileOpen(false)}>
        <CalendarIcon size={17} /> Bootcamp Classes
      </NavLink>
      {isAdmin && (
        <>
          <NavLink to="/members" className={navLink} onClick={() => setIsMobileOpen(false)}>
            <UsersIcon size={17} /> Members
          </NavLink>
          <NavLink to="/staff" className={navLink} onClick={() => setIsMobileOpen(false)}>
            <ShieldIcon size={17} /> Staff
          </NavLink>
          <NavLink to="/revenue" className={navLink} onClick={() => setIsMobileOpen(false)}>
            <RevenueIcon size={17} /> Revenue
          </NavLink>
          <NavLink to="/reports" className={navLink} onClick={() => setIsMobileOpen(false)}>
            <ReportsIcon size={17} /> Reports
          </NavLink>
        </>
      )}
      <NavLink to={isAdmin ? '/admin/ai-assistant' : '/ai-assistant'} className={navLink} onClick={() => setIsMobileOpen(false)}>
        <SparkleIcon size={17} /> AI Assistant
      </NavLink>
      {!isAdmin && (
        <>
          <NavLink to="/profile" className={navLink} onClick={() => setIsMobileOpen(false)}>
            <ProfileGlyph size={17} /> Profile
          </NavLink>
          <NavLink to="/membership" className={navLink} onClick={() => setIsMobileOpen(false)}>
            <CardIcon size={17} /> Membership
          </NavLink>
          <NavLink to="/my-goal" className={navLink} onClick={() => setIsMobileOpen(false)}>
            <TargetIcon size={17} /> My Goal
          </NavLink>
        </>
      )}
    </>
  );

  return (
    <>
      {/* Mobile top bar — only visible under the breakpoint (CSS-controlled) */}
      <div className={styles.mobileBar}>
        <NavLink to="/" className={styles.brand}>
          <img src="/assets/logo.png" alt="MyFitness" width={18} height={18} />
          MyFitness
        </NavLink>
        <button className={styles.menuBtn} onClick={() => setIsMobileOpen(true)} aria-label="Open menu">
          <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round">
            <path d="M4 7h16M4 12h16M4 17h16" />
          </svg>
        </button>
      </div>

      {isMobileOpen && <div className={styles.overlay} onClick={() => setIsMobileOpen(false)} />}

      <aside className={`${styles.sidebar} ${isMobileOpen ? styles.sidebarOpen : ''}`}>
        <NavLink to="/" className={styles.brand}>
          <img src="/assets/logo.png" alt="MyFitness" width={20} height={20} />
          MyFitness
        </NavLink>

        <nav className={styles.links}>{links}</nav>

        <div className={styles.footer}>
          <button
            className={styles.themeToggle}
            onClick={toggleTheme}
            aria-label={theme === 'dark' ? 'Switch to light mode' : 'Switch to dark mode'}
          >
            {theme === 'dark' ? <SunIcon size={15} /> : <MoonIcon size={15} />}
            {theme === 'dark' ? 'Light mode' : 'Dark mode'}
          </button>

          <div className={styles.userRow}>
            <span className={styles.username}>{user.username}</span>
            <span className={styles.roleBadge}>{user.role}</span>
          </div>

          <button className={styles.logoutBtn} onClick={logout}>Log out</button>
        </div>
      </aside>
    </>
  );
}