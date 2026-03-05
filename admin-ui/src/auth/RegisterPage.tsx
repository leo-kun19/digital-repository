import { Link, useNavigate } from "react-router-dom";

const SSO_URL = "/oauth2/authorization/azure";

function RegisterPage() {
  const navigate = useNavigate();

  return (
    <div className="auth-shell">
      <div className="auth-card text-center">
        <div className="su-logo-circle mx-auto mb-3" style={{ width: "3.5rem", height: "3.5rem", fontSize: "1.1rem" }}>
          SU
        </div>
        <h1 className="h5 su-page-title mb-1">Sampoerna University</h1>
        <h2 className="h4 mb-2 fw-bold">Create Account</h2>

        <div className="p-3 mb-3" style={{ background: "#f0f6fa", borderRadius: "0.75rem", border: "1px solid #d5e3ed" }}>
          <p className="text-muted small mb-2">
            <strong>ℹ️ Microsoft SSO</strong>
          </p>
          <p className="text-muted small mb-0">
            Registration is handled via Microsoft SSO using your Sampoerna University email. After signing in, you will complete your
            profile (faculty, study program, student ID).
          </p>
        </div>

        <div className="d-grid gap-2">
          <a className="btn btn-primary" style={{ borderRadius: "0.6rem" }} href={SSO_URL}>
            Sign up with Microsoft
          </a>

          <button className="btn btn-outline-secondary" style={{ borderRadius: "0.6rem" }} onClick={() => navigate("/login")}>
            Sign In Instead
          </button>

          <Link className="btn btn-link text-muted" to="/" style={{ borderRadius: "0.6rem" }}>
            ← Back to Repository
          </Link>
        </div>
      </div>
    </div>
  );
}

export default RegisterPage;