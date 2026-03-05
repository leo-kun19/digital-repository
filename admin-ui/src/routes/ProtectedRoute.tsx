import { ReactNode } from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import type { AuthUser } from '../lib/api/auth';

interface ProtectedRouteProps {
  user: AuthUser | null;
  loading: boolean;
  children: ReactNode;
}

export function ProtectedRoute({ user, loading, children }: ProtectedRouteProps) {
  const location = useLocation();

  if (loading) {
    return (
      <div style={{
        display: 'flex',
        justifyContent: 'center',
        alignItems: 'center',
        height: '100vh',
        fontSize: '1.2rem',
        color: '#666'
      }}>
        Loading...
      </div>
    );
  }

  if (!user) {
    // Redirect to login, but save the location they were trying to access
    return <Navigate to="/login" state={{ from: location }} replace />;
  }

  // Email verification check (must verify before accessing any protected page)
  if (!user.emailVerified && location.pathname !== '/verify-email') {
    return <Navigate to="/verify-email" replace />;
  }

  if (user.role !== 'ADMIN' && !user.profileComplete && location.pathname !== '/onboarding') {
    return <Navigate to="/onboarding" replace />;
  }

  return <>{children}</>;
}
