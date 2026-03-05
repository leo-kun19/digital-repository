import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import ShellLayout from '../../layout/ShellLayout';
import { adminApi } from '../../lib/api/admin';

export default function AdminDashboardPage() {
  const navigate = useNavigate();
  const [registrationCount, setRegistrationCount] = useState(0);
  const [reviewCount, setReviewCount] = useState(0);
  const [clearanceCount, setClearanceCount] = useState(0);
  const [publishCount, setPublishCount] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    const load = async () => {
      setLoading(true);
      try {
        const [registrations, review, clearance, publish] = await Promise.all([
          adminApi.registrationApprovals(),
          adminApi.reviewQueue(),
          adminApi.clearanceQueue(),
          adminApi.publishQueue(),
        ]);
        setRegistrationCount(registrations.length);
        setReviewCount(review.length);
        setClearanceCount(clearance.length);
        setPublishCount(publish.length);
      } catch (err) {
        setError(err instanceof Error ? err.message : 'Failed to load admin dashboard data.');
      } finally {
        setLoading(false);
      }
    };

    void load();
  }, []);

  const cards = [
    { label: 'Registration', value: registrationCount, icon: '📋', color: '#e0f2fe', path: '/admin/registration-approvals', desc: 'Pending registration verifications' },
    { label: 'Review Queue', value: reviewCount, icon: '📝', color: '#fff3cd', path: '/admin/review', desc: 'Submissions awaiting checklist review' },
    { label: 'Clearance Queue', value: clearanceCount, icon: '🏛️', color: '#ede9fe', path: '/admin/clearance', desc: 'Clearance forms pending approval' },
    { label: 'Publish Queue', value: publishCount, icon: '🚀', color: '#d1e7dd', path: '/admin/publish', desc: 'Ready to publish to repository' },
  ];

  const totalPending = registrationCount + reviewCount + clearanceCount + publishCount;

  return (
    <ShellLayout title="Admin Dashboard" subtitle="Library administration overview — registration, review, clearance, and publishing">
      {error && <div className="alert alert-danger">{error}</div>}

      {/* Total summary banner */}
      {!loading && totalPending > 0 && (
        <div className="alert d-flex align-items-center gap-2 mb-4 fade-in"
          style={{ background: '#e0f7fa', border: '1px solid #b2ebf2', borderRadius: '0.75rem', color: '#00695c' }}>
          <span style={{ fontSize: '1.3rem' }}>📌</span>
          <div>
            <strong>{totalPending} total item{totalPending > 1 ? 's' : ''}</strong> across all queues require your attention.
          </div>
        </div>
      )}

      <div className="row g-3">
        {cards.map((card, index) => (
          <div className="col-md-6 col-xl-3" key={card.path}>
            <div
              className="su-stat-card su-card-clickable fade-in"
              role="button"
              onClick={() => navigate(card.path)}
              style={{ animationDelay: `${index * 0.08}s` }}
            >
              <div className="su-stat-icon" style={{ background: card.color }}>{card.icon}</div>
              <div className="su-stat-value">{loading ? '—' : card.value}</div>
              <div className="su-stat-label">{card.label}</div>
              <div className="text-muted small mt-2" style={{ fontSize: '0.75rem' }}>{card.desc}</div>
            </div>
          </div>
        ))}
      </div>
    </ShellLayout>
  );
}
