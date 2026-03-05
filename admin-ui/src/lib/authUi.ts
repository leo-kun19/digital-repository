import type { Role } from "./api/auth";

export function defaultPath(role: Role | string) {
  if (role === "STUDENT") return "/student/dashboard";
  if (role === "LECTURER") return "/lecturer/dashboard";
  if (role === "ADMIN") return "/admin/dashboard";
  return "/";
}

export function parseOAuthError(search: string) {
  const params = new URLSearchParams(search);
  const value = params.get("error");
  return value ? decodeURIComponent(value) : "";
}

export function resolveReturnPath(locationState: unknown) {
  return (locationState as { from?: { pathname?: string } } | null)?.from?.pathname;
}
