import { createContext, useContext, useState, useEffect } from 'react';
import { authApi } from '../api/authApi';

const AuthContext = createContext(null);

/**
 * AuthProvider — the single source of truth for "who is logged in."
 * Deliberately React Context, not Redux/Zustand — the actual state here
 * is small (a token and a user object), and Context is genuinely enough
 * for that, matching the explicit instruction not to reach for a state
 * library without a real need.
 *
 * Persists to localStorage so a page refresh doesn't silently log the
 * user out — isLoading exists specifically to cover the brief moment
 * on first load where we're checking localStorage before we know
 * whether there's a real session to restore.
 */
export function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  const [token, setToken] = useState(null);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    const storedToken = localStorage.getItem('token');
    const storedUser = localStorage.getItem('user');
    if (storedToken && storedUser) {
      setToken(storedToken);
      setUser(JSON.parse(storedUser));
    }
    setIsLoading(false);
  }, []);

  function saveSession(data) {
    localStorage.setItem('token', data.token);
    localStorage.setItem('user', JSON.stringify(data.user));
    setToken(data.token);
    setUser(data.user);
  }

  async function login(username, password) {
    const data = await authApi.login({ username, password });
    saveSession(data);
    return data;
  }

  async function register(payload) {
    const data = await authApi.register(payload);
    saveSession(data);
    return data;
  }

  function logout() {
    localStorage.removeItem('token');
    localStorage.removeItem('user');
    setToken(null);
    setUser(null);
  }

  const value = {
    user,
    token,
    isAuthenticated: Boolean(token),
    isLoading,
    login,
    register,
    logout,
  };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
}