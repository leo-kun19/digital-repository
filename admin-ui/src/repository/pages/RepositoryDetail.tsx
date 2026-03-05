import { useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { publicRepositoryApi, type RepositoryItemDetail } from '../../lib/api/publicRepository';
import ThemeSwitch from '../../components/ThemeSwitch';
import { useAuth } from '../../lib/context/AuthContext';
import { useTheme } from '../../theme/ThemeContext';

export default function RepositoryDetail() {
  const { id } = useParams();
  const navigate = useNavigate();
  const { user, logout } = useAuth();
  const { theme, setTheme } = useTheme();

  const [item, setItem] = useState<RepositoryItemDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [downloadMessage, setDownloadMessage] = useState('');
  const [downloadLoading, setDownloadLoading] = useState(false);

  useEffect(() => {
    if (!id) return;
    setLoading(true);
    setError('');
    publicRepositoryApi
      .detail(Number(id))
      .then(setItem)
      .catch((err) => setError(err instanceof Error ? err.message : 'Failed to load repository detail.'))
      .finally(() => setLoading(false));
  }, [id]);

  const onDownload = async () => {
    if (!id) return;
    if (!user) {
      setDownloadMessage('Please log in first to download this published file.');
      return;
    }

    setDownloadLoading(true);
    setDownloadMessage('');
    try {
      const { blob, filename } = await publicRepositoryApi.download(Number(id));
      const url = URL.createObjectURL(blob);
      const anchor = document.createElement('a');
      anchor.href = url;
      anchor.download = filename;
      document.body.appendChild(anchor);
      anchor.click();
      anchor.remove();
      URL.revokeObjectURL(url);
      setDownloadMessage('Download started.');
    } catch (err) {
      setDownloadMessage(err instanceof Error ? err.message : 'Download failed.');
    } finally {
      setDownloadLoading(false);
    }
  };

  if (loading) {
    return (
      <div className="min-vh-100 d-flex align-items-center justify-content-center" style={{ background: '#f0f6fa' }}>
        <div className="text-center">
          <div className="su-spinner mx-auto mb-3" />
          <div className="text-muted">Loading publication details...</div>
        </div>
      </div>
    );
  }

  if (error || !item) {
    return (
      <div className="container py-4">
        <div className="alert alert-danger">{error || 'Repository item was not found.'}</div>
        <button className="btn btn-outline-secondary btn-sm" onClick={() => navigate('/')}>
          ← Back to Repository
        </button>
      </div>
    );
  }

  return (
    <div className="min-vh-100" style={{ background: 'linear-gradient(180deg, #f0f6fa 0%, #fff 30%)' }}>
      <header className="su-app-header">
        <div className="container py-3 d-flex flex-wrap justify-content-between align-items-center gap-2">
          <div className="d-flex align-items-center gap-2">
            <div className="su-logo-circle">SU</div>
            <div>
              <h1 className="h5 mb-0 text-white fw-bold">Sampoerna University</h1>
              <small className="text-white-50">Publication Detail</small>
            </div>
          </div>
          <div className="d-flex flex-wrap align-items-center gap-3">
            <div className="d-flex align-items-center gap-2 text-white">
              <span className="small text-white-50">Dark mode</span>
              <ThemeSwitch
                checked={theme === 'dark'}
                onChange={(checked) => setTheme(checked ? 'dark' : 'light')}
              />
            </div>
            <div className="d-flex flex-wrap gap-2">
              {user ? (
                <>
                  <button
                    type="button"
                    className="btn btn-outline-light btn-sm"
                    style={{ borderRadius: '999px' }}
                    onClick={() => navigate(
                      user.role === 'STUDENT'
                        ? '/student/dashboard'
                        : user.role === 'LECTURER'
                          ? '/lecturer/dashboard'
                          : '/admin/dashboard'
                    )}
                  >
                    📊 Dashboard
                  </button>
                  <button type="button" className="btn btn-light btn-sm" style={{ borderRadius: '999px' }} onClick={() => void logout()}>
                    Logout
                  </button>
                </>
              ) : (
                <>
                  <button type="button" className="btn btn-outline-light btn-sm" style={{ borderRadius: '999px' }} onClick={() => navigate('/login')}>
                    Sign In
                  </button>
                  <button type="button" className="btn btn-light btn-sm" style={{ borderRadius: '999px', fontWeight: 600 }} onClick={() => navigate('/register')}>
                    Register
                  </button>
                </>
              )}
            </div>
          </div>
        </div>
      </header>

      <div className="container py-4 fade-in">
        <div className="d-flex justify-content-between align-items-center mb-3 gap-2">
          <button className="btn btn-outline-secondary btn-sm" style={{ borderRadius: '999px' }} onClick={() => navigate(-1)}>
            ← Back
          </button>
          <Link to="/" className="btn btn-outline-primary btn-sm" style={{ borderRadius: '999px' }}>
            🔍 Back to Repository
          </Link>
        </div>

        <div className="su-card fade-in">
          <div className="card-body p-4">
            <h2 className="su-page-title mb-3" style={{ fontSize: '1.5rem', lineHeight: 1.3 }}>{item.title}</h2>

            <div className="d-flex flex-wrap gap-2 mb-3">
              <span className="badge bg-primary-subtle text-primary-emphasis" style={{ borderRadius: '999px', fontSize: '0.78rem', padding: '0.35rem 0.8rem' }}>
                👤 {item.authors || item.authorName || 'Unknown'}
              </span>
              <span className="badge bg-secondary-subtle text-secondary-emphasis" style={{ borderRadius: '999px', fontSize: '0.78rem', padding: '0.35rem 0.8rem' }}>
                🏛️ {item.faculty || 'N/A'}
              </span>
              <span className="badge bg-secondary-subtle text-secondary-emphasis" style={{ borderRadius: '999px', fontSize: '0.78rem', padding: '0.35rem 0.8rem' }}>
                📖 {item.program || 'N/A'}
              </span>
              <span className="badge bg-secondary-subtle text-secondary-emphasis" style={{ borderRadius: '999px', fontSize: '0.78rem', padding: '0.35rem 0.8rem' }}>
                📅 {item.year || 'N/A'}
              </span>
            </div>

            {item.keywords && (
              <div className="mb-3 p-2 px-3" style={{ background: '#f8fafc', borderRadius: '0.5rem', border: '1px solid #e8eff5' }}>
                <small className="text-muted fw-semibold">Keywords: </small>
                <small>{item.keywords}</small>
              </div>
            )}

            <hr />

            <h3 className="h6 su-page-title mb-2">📄 Abstract</h3>
            <p className="mb-4" style={{ lineHeight: 1.7, color: '#3d5a73' }}>{item.abstractText || 'No abstract available.'}</p>

            <div className="d-flex flex-wrap align-items-center gap-3">
              <button
                type="button"
                className="btn btn-primary"
                style={{ borderRadius: '999px', padding: '0.5rem 1.5rem' }}
                onClick={() => void onDownload()}
                disabled={!user || downloadLoading}
                title={!user ? 'Login is required to download files.' : undefined}
              >
                {downloadLoading ? '⏳ Downloading...' : '⬇️ Download PDF'}
              </button>
              {!user && (
                <div className="d-flex align-items-center gap-2">
                  <span className="badge bg-warning-subtle text-warning-emphasis" style={{ borderRadius: '999px' }}>🔒 Login required</span>
                  <button className="btn btn-link btn-sm p-0" onClick={() => navigate('/login')}>Sign in to download</button>
                </div>
              )}
            </div>
            {downloadMessage && (
              <p className="small mt-2 mb-0" style={{ color: downloadMessage.includes('started') ? '#198754' : '#dc3545' }}>
                {downloadMessage}
              </p>
            )}
          </div>
        </div>

        <footer className="text-center text-muted small py-4">
          <div className="fw-semibold">Sampoerna University Library</div>
          <div>© {new Date().getFullYear()} — Digital Repository Portal</div>
        </footer>
      </div>
    </div>
  );
}
