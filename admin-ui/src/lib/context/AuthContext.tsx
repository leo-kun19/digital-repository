import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { authApi, type AuthUser } from '../api/auth';
import { ApiError } from '../api/http';

interface AuthContextValue {
  user: AuthUser | null;
  loading: boolean;
  login: (email: string, password: string) => Promise<{ success: boolean; user?: AuthUser; error?: string }>;
  logout: () => Promise<void>;
  refetch: () => Promise<AuthUser | null>;
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<AuthUser | null>(null);
  const [loading, setLoading] = useState(true);

  const refetch = useCallback(async () => {
    try {
      const me = await authApi.me();
      setUser(me);
      return me;
    } catch (error) {
      if (!(error instanceof ApiError) || (error.status !== 401 && error.status !== 403)) {
        console.error('Failed to fetch /api/auth/me', error);
      }
      setUser(null);
      return null;
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void refetch();
  }, [refetch]);

  const login = useCallback(async (email: string, password: string) => {
    try {
      const loggedInUser = await authApi.login(email, password);
      setUser(loggedInUser);
      return { success: true, user: loggedInUser };
    } catch (error) {
      if (error instanceof ApiError) {
        return { success: false, error: error.message };
      }
      return { success: false, error: 'Unable to sign in right now.' };
    }
  }, []);

  const logout = useCallback(async () => {
    try {
      await authApi.logout();
    } catch (error) {
      console.error('Logout failed', error);
    } finally {
      setUser(null);
      window.location.href = '/login';
    }
  }, []);

  const value = useMemo(
    () => ({
      user,
      loading,
      login,
      logout,
      refetch,
    }),
    [login, logout, loading, refetch, user]
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within AuthProvider');
  }
  return context;
}
