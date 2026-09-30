import NavBar from './NavBar';
import BackgroundDecoration from './BackgroundDecoration';
import styles from './PageShell.module.css';

/**
 * Shared wrapper for every authenticated page — theme tokens, background,
 * and NavBar in one place. Dashboard, BootcampClasses, AiAssistant, and
 * Profile all use this now instead of each defining their own root.
 * BackgroundDecoration adds the star field + mountain silhouette that
 * were compositional elements from the reference, missing before now.
 *
 * backgroundImage is optional and deliberately only passed by Dashboard —
 * per the explicit "don't put background images everywhere" instruction,
 * this is scoped to the two actual dashboards (Member/Admin), not every
 * page. When provided, it renders as a subtle photo layer behind the
 * existing gradient system, with a dark scrim on top so text and cards
 * stay fully readable — the photo sets mood, the gradient still does the
 * real visual work.
 *
 * heroBanner is optional too, only passed by the Admin Dashboard.
 * Deliberately rendered here, before .content, rather than as a normal
 * child — .content is a centered, max-width: 1040px column (for
 * readable line lengths on wide screens), so a child inside it can
 * never truly reach the viewport's right edge on wide monitors, no
 * matter how its own margins are tuned. Rendering the banner outside
 * that constrained column entirely is what actually lets it span edge
 * to edge.
 */
export default function PageShell({ children, backgroundImage, heroBanner }) {
  return (
    <div className={styles.pageRoot}>
      {backgroundImage && (
        <div
          className={styles.photoLayer}
          style={{ backgroundImage: `url(${backgroundImage})` }}
        />
      )}
      <BackgroundDecoration />
      <NavBar />
      {heroBanner}
      <div className={styles.content}>{children}</div>
    </div>
  );
}