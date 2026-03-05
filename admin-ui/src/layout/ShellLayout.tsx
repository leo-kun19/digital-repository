import type { ReactNode } from 'react';
import { NavLink } from 'react-router-dom';
import ThemeSwitch from '../components/ThemeSwitch';
import { useAuth } from '../lib/context/AuthContext';
import { useTheme } from '../theme/ThemeContext';

interface ShellLayoutProps {
  title: string;
  subtitle?: string;
  children: ReactNode;
}

type LinkItem = {
  label: string;
  path: string;
  icon: string; // path to PNG icon in /public/icons/
};

function roleLinks(role?: string): LinkItem[] {
  if (role === 'STUDENT') {
    return [
      { label: 'Dashboard', path: '/student/dashboard', icon: '/icons/student/dashboard.png' },
      { label: 'Publication Registration', path: '/student/registrations', icon: '/icons/student/registration.png' },
      { label: 'Submission', path: '/student/submissions', icon: '/icons/student/submission.png' },
      { label: 'Repository Search', path: '/', icon: '/icons/admin/search.png' },
    ];
  }
  if (role === 'LECTURER') {
    return [
      { label: 'Dashboard', path: '/lecturer/dashboard', icon: '/icons/lecturer/dashboard.png' },
      { label: 'Registration Approval', path: '/lecturer/approvals', icon: '/icons/lecturer/maps-and-flags.png' },
      { label: 'Submission Review', path: '/lecturer/review', icon: '/icons/lecturer/submission.png' },
      { label: 'My Students', path: '/lecturer/students', icon: '/icons/lecturer/students.png' },
      { label: 'Repository Search', path: '/', icon: '/icons/admin/search.png' },
    ];
  }
  if (role === 'ADMIN') {
    return [
      { label: 'Dashboard', path: '/admin/dashboard', icon: '/icons/admin/dashboard.png' },
      { label: 'Registration', path: '/admin/registration-approvals', icon: '/icons/admin/registration.png' },
      { label: 'Submission Review', path: '/admin/review', icon: '/icons/admin/submission.png' },
      { label: 'Clearance', path: '/admin/clearance', icon: '/icons/admin/clearance.png' },
      { label: 'Publishing', path: '/admin/publish', icon: '/icons/admin/publishing.png' },
      { label: 'Templates', path: '/admin/checklists', icon: '/icons/admin/template.png' },
      { label: 'Repository Search', path: '/', icon: '/icons/admin/search.png' },
    ];
  }
  return [{ label: 'Repository Search', path: '/', icon: '/icons/admin/search.png' }];
}

export default function ShellLayout({ title, subtitle, children }: ShellLayoutProps) {
  const { user, logout } = useAuth();
  const { theme, setTheme } = useTheme();
  const links = roleLinks(user?.role);
  const roleLabel = user?.role ? user.role.charAt(0) + user.role.slice(1).toLowerCase() : 'Guest';
  const currentYear = new Date().getFullYear();
  const initials = user?.fullName
    ? user.fullName.split(' ').map((n: string) => n[0]).join('').toUpperCase().slice(0, 2)
    : user?.email?.slice(0, 2).toUpperCase() ?? 'G';

  return (
    <div className="min-vh-100 d-flex flex-column">
      <header className="su-app-header sticky-top">
        <div className="container-fluid px-3 px-lg-4 py-2 d-flex flex-wrap align-items-center justify-content-between gap-2">
          <div className="d-flex align-items-center gap-2">
            <div className="su-logo-circle">SU</div>
            <div>
              <div className="fw-semibold" style={{ fontSize: '0.95rem' }}>Sampoerna University</div>
              <div className="text-white-50" style={{ fontSize: '0.72rem' }}>Digital Repository</div>
            </div>
          </div>
          <div className="d-flex align-items-center gap-3">
            <div className="d-flex align-items-center gap-2">
              <span className="small text-white-50">Dark mode</span>
              <ThemeSwitch
                checked={theme === 'dark'}
                onChange={(checked) => setTheme(checked ? 'dark' : 'light')}
              />
            </div>
            <div className="d-flex align-items-center gap-2">
              <div
                className="rounded-circle d-inline-flex align-items-center justify-content-center"
                style={{
                  width: '2rem', height: '2rem',
                  background: 'rgba(255,255,255,0.2)',
                  color: '#fff',
                  fontSize: '0.75rem',
                  fontWeight: 700,
                  border: '1.5px solid rgba(255,255,255,0.3)',
                }}
              >
                {initials}
              </div>
              <div className="d-none d-md-block">
                <div className="text-white" style={{ fontSize: '0.82rem', fontWeight: 500, lineHeight: 1.2 }}>
                  {user?.fullName || user?.email || 'Guest'}
                </div>
                <span className="badge su-badge-role">{roleLabel}</span>
              </div>
            </div>
            <button
              className="btn btn-outline-light btn-sm"
              style={{ borderRadius: '999px', padding: '0.3rem 1rem', fontSize: '0.8rem' }}
              onClick={() => void logout()}
            >
              Logout
            </button>
          </div>
        </div>
      </header>

      <div className="container-fluid flex-grow-1">
        <div className="row min-vh-100">
          <aside className="col-12 col-lg-3 col-xl-2 su-sidebar p-3 p-lg-4">
            <nav className="nav flex-column gap-1">
              {links.map((link) => (
                <NavLink
                  key={link.path}
                  to={link.path}
                  end={link.path === '/'}
                  className={({ isActive }) => `nav-link text-start d-flex align-items-center gap-2 ${isActive ? 'active' : ''}`}
                >
                  <img
                    src={link.icon}
                    alt=""
                    className="su-sidebar-icon-img"
                    style={{
                      width: '20px',
                      height: '20px',
                      objectFit: 'contain',
                      opacity: 0.7,
                      filter: theme === 'dark' ? 'invert(1)' : 'none',
                    }}
                  />
                  {link.label}
                </NavLink>
              ))}
            </nav>
          </aside>

          <main className="col-12 col-lg-9 col-xl-10 p-3 p-lg-4 fade-in">
            <div className="mb-4">
              <h2 className="mb-1 su-page-title" style={{ fontSize: '1.5rem' }}>{title}</h2>
              {subtitle && <p className="mb-0 su-page-subtitle">{subtitle}</p>}
            </div>
            {children}
          </main>
        </div>
      </div>

      <footer className="border-top py-3 text-center text-muted small">
        <div className="fw-semibold">Sampoerna University Library</div>
        <div>For support contact: library@sampoernauniversity.ac.id</div>
        <div>© {currentYear}</div>
      </footer>
    </div>
  );
}
