import type { AuthConfig, AuthMode } from "./api/auth";

export function resolveAuthMode(config: AuthConfig | null): AuthMode {
  return config?.mode ?? "HYBRID";
}

export function shouldShowLocalLogin(config: AuthConfig | null): boolean {
  const mode = resolveAuthMode(config);
  if (mode === "SSO" || mode === "AAD") {
    return false;
  }
  return config?.localEnabled ?? true;
}

export function shouldShowSso(config: AuthConfig | null): boolean {
  const mode = resolveAuthMode(config);
  if (mode === "LOCAL") {
    return false;
  }
  return config?.ssoEnabled ?? true;
}
