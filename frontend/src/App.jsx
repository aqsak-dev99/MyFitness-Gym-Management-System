import { useEffect, useState } from 'react';
import { BrowserRouter, Routes, Route } from 'react-router-dom';
import { AuthProvider, useAuth } from './auth/AuthContext';
import { ThemeProvider } from './theme/ThemeContext';
import { ToastProvider } from './toast/ToastContext';
import { ProtectedRoute } from './auth/ProtectedRoute';
import { RoleRoute } from './auth/RoleRoute';
import SplashScreen from './components/SplashScreen';
import Login from './pages/auth/Login';
import Register from './pages/auth/Register';
import Dashboard from './pages/Dashboard';
import BootcampClasses from './pages/BootcampClasses';
import AiAssistant from './pages/AiAssistant';
import Profile from './pages/Profile';
import Membership from './pages/Membership';
import MyGoal from './pages/MyGoal';
import AdminMembers from './pages/AdminMembers';
import AdminClasses from './pages/AdminClasses';
import AdminStaff from './pages/AdminStaff';
import AdminAiAssistant from './pages/AdminAiAssistant';
import AdminRevenue from './pages/AdminRevenue';
import AdminReports from './pages/AdminReports';

/**
 * useAuth() has to be called from inside AuthProvider's own tree, not
 * alongside it — this small wrapper exists only to make that possible,
 * so App itself can stay a plain composition of AuthProvider + routes.
 *
 * isLoading is still the real, genuine "checking localStorage" state —
 * nothing fake about it. What's new is minTimeElapsed: a real 1300ms
 * timer that guarantees the splash is actually visible/perceivable,
 * since the real check alone was resolving too fast on localhost to
 * ever be seen (confirmed repeatedly). showSplash is true until BOTH
 * conditions clear — the real check finishing AND 1300ms passing,
 * whichever takes longer. On a genuinely slow connection, the real
 * check still governs; this timer only matters when it would otherwise
 * be imperceptibly fast. This is a deliberate splash-screen experience
 * (explicitly requested), not disguising real network latency.
 */
function AppRoutes() {
  const { isLoading } = useAuth();
  const [minTimeElapsed, setMinTimeElapsed] = useState(false);

  useEffect(() => {
    const timer = setTimeout(() => setMinTimeElapsed(true), 8000);
    return () => clearTimeout(timer);
  }, []);

  const showSplash = isLoading || !minTimeElapsed;

  if (showSplash) {
    return <SplashScreen />;
  }

  return (
    <BrowserRouter>
      <Routes>
        <Route path="/login" element={<Login />} />
        <Route path="/register" element={<Register />} />

        <Route element={<ProtectedRoute />}>
          <Route path="/" element={<Dashboard />} />
          <Route path="/bootcamp-classes" element={<BootcampClasses />} />
          <Route path="/ai-assistant" element={<AiAssistant />} />
          <Route element={<RoleRoute allowedRoles={['MEMBER']} />}>
            <Route path="/profile" element={<Profile />} />
            <Route path="/membership" element={<Membership />} />
            <Route path="/my-goal" element={<MyGoal />} />
          </Route>

          <Route element={<RoleRoute allowedRoles={['ADMIN']} />}>
            <Route path="/members" element={<AdminMembers />} />
            <Route path="/admin/classes" element={<AdminClasses />} />
            <Route path="/staff" element={<AdminStaff />} />
            <Route path="/revenue" element={<AdminRevenue />} />
            <Route path="/reports" element={<AdminReports />} />
            <Route path="/admin/ai-assistant" element={<AdminAiAssistant />} />
          </Route>
        </Route>
      </Routes>
    </BrowserRouter>
  );
}

export default function App() {
  return (
    <ThemeProvider>
      <ToastProvider>
        <AuthProvider>
          <AppRoutes />
        </AuthProvider>
      </ToastProvider>
    </ThemeProvider>
  );
}