import { useEffect, useMemo, useState } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { getAuthConfig, type AuthConfig } from "../lib/api/auth";
import { useAuth } from "../lib/context/AuthContext";

function defaultPath(role: string) {
  if (role === "STUDENT") return "/student/dashboard";
  if (role === "LECTURER") return "/lecturer/dashboard";
  if (role === "ADMIN") return "/admin/dashboard";
  return "/";
}

const SSO_URL = "/oauth2/authorization/azure";

export default function LoginPage() {
  const navigate = useNavigate();
  const location = useLocation();

  const [error, setError] = useState("");

  const oauthError = useMemo(() => {
    const params = new URLSearchParams(location.search);
    const err = params.get("error");
    return err ? decodeURIComponent(err) : "";
  }, [location.search]);

  useEffect(() => {
    if (oauthError) setError(oauthError);
  }, [oauthError]);

  return (
    <div className="auth-shell">
      <div className="auth-card text-center">
        <div
          className="su-logo-circle mx-auto mb-3"
          style={{ width: "3.5rem", height: "3.5rem", fontSize: "1.1rem" }}
        >
          SU
        </div>
        <h1 className="h5 su-page-title mb-1">Sampoerna University</h1>
        <h2 className="h4 mb-1 fw-bold">Welcome Back</h2>
        <p className="text-muted small mb-3">Sign in to continue to the Digital Repository</p>

        {error && (
          <div className="alert alert-danger py-2 d-flex align-items-center gap-2" style={{ borderRadius: "0.6rem" }}>
            <span>⚠️</span> {error}
          </div>
        )}

        <div className="d-grid gap-2">
          <a
            className="btn btn-primary btn-lg"
            href={SSO_URL}
            style={{ fontSize: "0.95rem", borderRadius: "0.6rem", padding: "0.7rem" }}
          >
            Sign in with Microsoft
          </a>

          <div className="text-center mt-2">
            <button className="btn btn-link btn-sm text-muted" type="button" onClick={() => navigate("/")}>
              ← Back to repository
            </button>
          </div>
        </div>

        <hr className="my-3" />
        <div className="text-center">
          <small className="text-muted">First time here? </small>
          <button className="btn btn-link btn-sm p-0" type="button" onClick={() => navigate("/register")}>
            Sign up with Microsoft
          </button>
        </div>
      </div>
    </div>
  );
}