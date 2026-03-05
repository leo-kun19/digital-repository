import { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import ShellLayout from '../../layout/ShellLayout';
import { adminApi } from '../../lib/api/admin';
import type { AdminStudentReviewGroup } from '../../lib/types/workflow';

export default function AdminReviewPage() {
  const navigate = useNavigate();
  const [groups, setGroups] = useState<AdminStudentReviewGroup[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const load = async () => {
    setLoading(true);
    setError('');
    try {
      setGroups(await adminApi.reviewQueueGrouped());
    } catch (err) {
      setGroups([]);
      setError(err instanceof Error ? err.message : 'Failed to load review queue.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void load();
  }, []);

  const rows = useMemo(() => (
    groups.map((group) => {
      const latestActivity = group.cases.reduce((latest, item) => {
        const next = item.latestSubmissionAt || item.updatedAt || null;
        if (!next) return latest;
        if (!latest) return next;
        return new Date(next) > new Date(latest) ? next : latest;
      }, null as string | null);
      return { ...group, caseCount: group.cases.length, latestActivity };
    })
  ), [groups]);

  return (
    <ShellLayout title="Submission Review" subtitle="Cases forwarded to library for checklist review">
      {error && <div className="alert alert-danger">{error}</div>}

      {loading && (
        <div className="text-center py-5">
          <div className="su-spinner mx-auto mb-3" />
          <div className="text-muted">Loading review queue...</div>
        </div>
      )}

      {!loading && rows.length === 0 && (
        <div className="su-empty-state">
          <div className="su-empty-icon">📝</div>
          <h5>No Cases in Review</h5>
          <p className="text-muted">Review queue is empty.</p>
        </div>
      )}

      <div className="row g-3">
        {rows.map((group, index) => (
          <div className="col-lg-6" key={group.studentUserId}>
            <div
              className="su-card su-card-clickable h-100 fade-in"
              role="button"
              onClick={() => navigate(`/admin/review/students/${group.studentUserId}`)}
              style={{ animationDelay: `${index * 0.05}s` }}
            >
              <div className="card-body p-4">
                <div className="d-flex justify-content-between align-items-start mb-2">
                  <div>
                    <h3 className="h6 mb-1 fw-bold">{group.studentName}</h3>
                    <div className="text-muted small">
                      🆔 {group.studentIdNumber || 'N/A'} • 🏛️ {[group.faculty, group.program].filter(Boolean).join(' / ') || 'N/A'}
                    </div>
                  </div>
                  <span className="badge bg-primary-subtle text-primary-emphasis" style={{ borderRadius: '999px' }}>
                    {group.caseCount} case{group.caseCount > 1 ? 's' : ''}
                  </span>
                </div>
                <div className="text-muted small">
                  Latest activity: {group.latestActivity ? new Date(group.latestActivity).toLocaleString() : 'N/A'}
                </div>
              </div>
            </div>
          </div>
        ))}
      </div>
    </ShellLayout>
  );
}
