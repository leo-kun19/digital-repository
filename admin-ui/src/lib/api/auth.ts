import { getJson, postJson } from './http';

export type Role = 'STUDENT' | 'LECTURER' | 'ADMIN';

export interface AuthUser {
  id: number;
  email: string;
  role: Role;
  profileComplete: boolean;
  emailVerified: boolean;
  name?: string;
  fullName?: string;
  studentId?: string;
  faculty?: string;
  program?: string;
  department?: string;
}

export interface OnboardingPayload {
  name: string;
  faculty: string;
  studyProgram: string;
  studentId?: string;
}

export type AuthMode = 'LOCAL' | 'SSO' | 'AAD' | 'HYBRID';

export type AuthConfig = {
  mode: AuthMode;
  localEnabled: boolean;
  ssoEnabled: boolean;
};

const DEFAULT_AUTH_CONFIG: AuthConfig = {
  mode: 'HYBRID',
  localEnabled: true,
  ssoEnabled: true,
};

export async function getAuthConfig(): Promise<AuthConfig> {
  try {
    return await getJson<AuthConfig>('/api/auth/config');
  } catch {
    return DEFAULT_AUTH_CONFIG;
  }
}

export const authApi = {
  me(): Promise<AuthUser> {
    return getJson<AuthUser>('/api/auth/me');
  },

  login(email: string, password: string): Promise<AuthUser> {
    return postJson<AuthUser>('/api/auth/login', { email, password });
  },

  logout(): Promise<{ message?: string }> {
    return postJson<{ message?: string }>('/api/auth/logout');
  },

  onboarding(payload: OnboardingPayload): Promise<AuthUser> {
    return postJson<AuthUser>('/api/auth/onboarding', payload);
  },

  sendOtp(): Promise<{ message: string; email: string; fallbackOtp?: string }> {
    return postJson<{ message: string; email: string; fallbackOtp?: string }>('/api/auth/send-otp');
  },

  verifyOtp(otp: string): Promise<AuthUser> {
    return postJson<AuthUser>('/api/auth/verify-otp', { otp });
  },
};
