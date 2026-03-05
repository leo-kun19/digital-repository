import { FormEvent, useEffect, useMemo, useState } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { getAuthConfig, type AuthConfig } from "../lib/api/auth";
import { resolveAuthMode, shouldShowLocalLogin, shouldShowSso } from "../lib/authMode";
import { useAuth } from "../lib/context/AuthContext";

function defaultPath(role: string) {
  if (role === "STUDENT") return "/student/dashboard";
  if (role === "LECTURER") return "/lecturer/dashboard";
  if (role === "ADMIN") return "/admin/dashboard";
  return "/";
}

const SSO_URL = "/oauth2/authorization/azure";

export default function LoginPage() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();

  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");

  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [authConfig, setAuthConfig] = useState<AuthConfig | null>(null);

  const oauthError = useMemo(() => {
    const params = new URLSearchParams(location.search);
    const err = params.get("error");
    return err ? decodeURIComponent(err) : "";
  }, [location.search]);

  const mode = resolveAuthMode(authConfig);
  const showLocal = shouldShowLocalLogin(authConfig);
  const showSso = shouldShowSso(authConfig);

  useEffect(() => {
    if (oauthError) setError(oauthError);
  }, [oauthError]);

  useEffect(() => {
    let active = true;
    const loadConfig = async () => {
      try {
        const config = await getAuthConfig();
        if (active) setAuthConfig(config);
      } catch {
        if (active) setAuthConfig(null);
      }
    };
    void loadConfig();
    return () => {
      active = false;
    };
  }, []);

  const submit = async (e: FormEvent) => {
    e.preventDefault();

    if (!showLocal) {
      setError("Email/password login is disabled. Use Microsoft sign-in.");
      return;
    }

    setLoading(true);
    setError("");

    try {
      const result = await login(email, password);
      if (!result.success || !result.user) {
        setError(result.error || "Login failed");
        return;
      }

      // Always redirect to verify-email since OTP is auto-sent on login
      if (!result.user.emailVerified) {
        navigate("/verify-email", { replace: true });
        return;
      }

      const from = (location.state as any)?.from?.pathname;
      navigate(from || defaultPath(result.user.role), { replace: true });
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : "Login failed";
      setError(message);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="auth-shell">
      <form className="auth-card" onSubmit={submit}>
        <div className="text-center mb-4">
          <div
            className="su-logo-circle mx-auto mb-3"
            style={{ width: "3.5rem", height: "3.5rem", fontSize: "1.1rem" }}
          >
            SU
          </div>
          <h1 className="h5 su-page-title mb-1">Sampoerna University</h1>
          <h2 className="h4 mb-1 fw-bold">Welcome Back</h2>
          <p className="text-muted small mb-0">Sign in to continue to the Digital Repository</p>
        </div>

        {showLocal && (
          <>
            <div className="mb-3">
              <label className="form-label">Email Address</label>
              <input
                className="form-control form-control-lg"
                type="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                required
                autoComplete="username"
                placeholder="your.email@sampoernauniversity.ac.id"
                style={{ fontSize: "0.9rem" }}
              />
            </div>

            <div className="mb-4">
              <label className="form-label">Password</label>
              <input
                className="form-control form-control-lg"
                type="password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                required
                autoComplete="current-password"
                placeholder="Enter your password"
                style={{ fontSize: "0.9rem" }}
              />
            </div>
          </>
        )}

        {error && (
          <div className="alert alert-danger py-2 d-flex align-items-center gap-2" style={{ borderRadius: "0.6rem" }}>
            <span>⚠️</span> {error}
          </div>
        )}

        <div className="d-grid gap-2">
          {showLocal && (
            <button
              className="btn btn-primary btn-lg"
              disabled={loading}
              type="submit"
              style={{ fontSize: "0.95rem", borderRadius: "0.6rem", padding: "0.7rem" }}
            >
              {loading ? (
                <>
                  <span
                    className="su-spinner d-inline-block me-2"
                    style={{ width: "1rem", height: "1rem", borderWidth: 2, borderTopColor: "#fff" }}
                  />{" "}
                  Signing in...
                </>
              ) : (
                "Sign In"
              )}
            </button>
          )}

          {showSso && (
            <a
              className={`btn ${showLocal ? "btn-outline-primary" : "btn-primary"} btn-lg`}
              href={SSO_URL}
              style={{ fontSize: "0.95rem", borderRadius: "0.6rem", padding: "0.7rem" }}
            >
              Sign in with Microsoft
            </a>
          )}

          {(mode === "SSO" || mode === "AAD") && (
            <div className="small text-muted text-center mt-2">Local login is disabled in this environment.</div>
          )}

          <div className="text-center mt-2">
            <button className="btn btn-link btn-sm text-muted" type="button" onClick={() => navigate("/")}>
              ← Back to repository
            </button>
          </div>
        </div>

        {showSso && (
          <>
            <hr className="my-3" />
            <div className="text-center">
              <small className="text-muted">First time here? </small>
              <button className="btn btn-link btn-sm p-0" type="button" onClick={() => navigate("/register")}>
                Sign up with Microsoft
              </button>
            </div>
          </>
        )}
      </form>
    </div>
  );
}