import { ReactNode } from 'react';
import { Navigate } from 'react-router-dom';
import type { AuthUser } from '../lib/api/auth';

interface RoleGuardProps {
  user: AuthUser | null;
  allowedRoles: Array<'STUDENT' | 'LECTURER' | 'ADMIN'>;
  children: ReactNode;
  redirectTo?: string;
}

export function RoleGuard({ 
  user, 
  allowedRoles, 
  children, 
  redirectTo
}: RoleGuardProps) {
  if (!user) {
    return <Navigate to="/login" replace />;
  }

  if (!allowedRoles.includes(user.role)) {
    const defaultPath =
      user.role === 'STUDENT'
        ? '/student/dashboard'
        : user.role === 'LECTURER'
          ? '/lecturer/dashboard'
          : '/admin/dashboard';
    return <Navigate to={redirectTo ?? defaultPath} replace />;
  }

  return <>{children}</>;
}
