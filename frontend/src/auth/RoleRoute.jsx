import { Navigate, Outlet } from 'react-router-dom';
import { useAuth } from './AuthContext';

/**
 * A second, narrower guard for routes that need a SPECIFIC role, not
 * just "any logged-in user" — e.g. an ADMIN-only member list page.
 * Assumes ProtectedRoute has already confirmed authentication; this
 * only adds the role check on top, matching how the backend itself
 * layers @RequireRole on top of JwtAuthenticationFilter rather than
 * duplicating auth logic in one giant check.
 */
export function RoleRoute({ allowedRoles }) {
  const { user } = useAuth();

  if (!user || !allowedRoles.includes(user.role)) {
    return <Navigate to="/" replace />;
  }

  return <Outlet />;
}