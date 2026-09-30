import { Navigate, Outlet } from 'react-router-dom';
import { useAuth } from './AuthContext';

/**
 * Guards any route nested under it — redirects to /login if there's no
 * active session. The isLoading check that used to live here moved up
 * to App.jsx's AppRoutes — by the time ANY route (including this one)
 * ever renders, that top-level check has already resolved, so checking
 * it again here would be redundant, not a second real state.
 */
export function ProtectedRoute() {
  const { isAuthenticated } = useAuth();

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }

  return <Outlet />;
}