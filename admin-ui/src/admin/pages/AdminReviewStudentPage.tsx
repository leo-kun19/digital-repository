import { useEffect, useMemo, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import ShellLayout from '../../layout/ShellLayout';
import { adminApi } from '../../lib/api/admin';
import type { AdminStudentReviewGroup } from '../../lib/types/workflow';
import { formatStatus, statusBadgeClass } from '../../lib/workflowUi';

export default function AdminReviewStudentPage() {
  const navigate = useNavigate();
  const { studentUserId } = useParams();
  const [group, setGroup] = useState<AdminStudentReviewGroup | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    const load = async () => {
      setLoading(true);
      setError('');
      try {
        const groups = await adminApi.reviewQueueGrouped();
        const match = groups.find((row) => String(row.studentUserId) === String(studentUserId));
        setGroup(match ?? null);
      } catch (err) {
        setGroup(null);
        setError(err instanceof Error ? err.message : 'Failed to load student review queue.');
      } finally {
        setLoading(false);
      }
    };

    void load();
  }, [studentUserId]);

  const latestActivity = useMemo(() => {
    if (!group) return null;
    return group.cases.reduce((latest, item) => {
      const next = item.latestSubmissionAt || item.updatedAt || null;
      if (!next) return latest;
      if (!latest) return next;
      return new Date(next) > new Date(latest) ? next : latest;
    }, null as string | null);
  }, [group]);

  return (
    <ShellLayout title="Student Review Detail" subtitle="Cases in library review for the selected student">
      <button className="btn btn-outline-secondary btn-sm mb-4" style={{ borderRadius: '999px' }} onClick={() => navigate('/admin/review')}>
        ← Back to Review Queue
      </button>

      {error && <div className="alert alert-danger d-flex align-items-center gap-2" style={{ borderRadius: '0.75rem' }}><span>⚠️</span> {error}</div>}

      {loading && (
        <div className="text-center py-5">
          <div className="su-spinner mx-auto mb-3" />
          <div className="text-muted">Loading student review detail...</div>
        </div>
      )}

      {!loading && !group && !error && (
        <div className="su-empty-state">
          <div className="su-empty-icon">👤</div>
          <h5>Student Not Found</h5>
          <p className="text-muted">Student not found in review queue.</p>
        </div>
      )}

      {group && (
        <>
          {/* Student Info Card */}
          <div className="su-card mb-4 fade-in">
            <div className="card-body p-4">
              <div className="d-flex flex-wrap align-items-center gap-3">
                <div
                  className="rounded-circle d-flex align-items-center justify-content-center"
                  style={{ width: '3rem', height: '3rem', background: '#e8f4f8', color: '#0b7584', fontWeight: 700, fontSize: '1.1rem' }}
                >
                  {(group.studentName || 'S')[0].toUpperCase()}
                </div>
                <div>
                  <h3 className="h5 mb-0 fw-bold">{group.studentName}</h3>
                  <div className="text-muted small">
                    🆔 {group.studentIdNumber || 'N/A'} • 🏛️ {group.faculty || 'N/A'} {group.program ? `• 📖 ${group.program}` : ''}
                  </div>
                  <div className="text-muted small mt-1">
                    {group.cases.length} case{group.cases.length > 1 ? 's' : ''} in review •
                    Latest: {latestActivity ? new Date(latestActivity).toLocaleString() : 'N/A'}
                  </div>
                </div>
              </div>
            </div>
          </div>

          {/* Cases */}
          <div className="vstack gap-3">
            {group.cases.map((c, index) => (
              <div className="su-card su-card-clickable fade-in" key={c.caseId} style={{ animationDelay: `${index * 0.05}s` }}
                role="button" onClick={() => navigate(`/admin/review/${c.caseId}`)}>
                <div className="card-body p-4">
                  <div className="d-flex flex-wrap justify-content-between align-items-center gap-2">
                    <div>
                      <h5 className="fw-bold mb-1">{c.title || `Case #${c.caseId}`}</h5>
                      <div className="d-flex flex-wrap gap-2 align-items-center">
                        <span className="badge bg-dark-subtle text-dark-emphasis" style={{ borderRadius: '999px' }}>{c.type}</span>
                        <span className={`badge status-badge ${statusBadgeClass(c.status)}`}>{formatStatus(c.status)}</span>
                        <span className="text-muted small">Updated: {c.updatedAt ? new Date(c.updatedAt).toLocaleString() : 'N/A'}</span>
                      </div>
                    </div>
                    <span className="btn btn-primary btn-sm" style={{ borderRadius: '999px' }}>
                      📝 Open Review →
                    </span>
                  </div>
                </div>
              </div>
            ))}
          </div>
        </>
      )}
    </ShellLayout>
  );
}
