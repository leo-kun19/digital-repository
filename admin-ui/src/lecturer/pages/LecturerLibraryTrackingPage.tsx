import { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import ShellLayout from '../../layout/ShellLayout';
import { lecturerApi, type LecturerStudentGroup } from '../../lib/api/lecturer';

type LibraryStatusSummary = {
  needRevisions: number;
  inReview: number;
  approved: number;
};

function summarizeLibraryStatuses(groups: LecturerStudentGroup[]): Record<number, LibraryStatusSummary> {
  const summary: Record<number, LibraryStatusSummary> = {};
  groups.forEach((group) => {
    const counts = { needRevisions: 0, inReview: 0, approved: 0 };
    group.cases.forEach((c) => {
      if (c.status === 'NEEDS_REVISION_LIBRARY') {
        counts.needRevisions += 1;
      } else if (c.status === 'UNDER_LIBRARY_REVIEW' || c.status === 'FORWARDED_TO_LIBRARY') {
        counts.inReview += 1;
      } else if (
        c.status === 'APPROVED_FOR_CLEARANCE' ||
        c.status === 'CLEARANCE_SUBMITTED' ||
        c.status === 'CLEARANCE_APPROVED' ||
        c.status === 'READY_TO_PUBLISH' ||
        c.status === 'PUBLISHED'
      ) {
        counts.approved += 1;
      }
    });
    summary[group.studentUserId] = counts;
  });
  return summary;
}

export default function LecturerLibraryTrackingPage() {
  const navigate = useNavigate();
  const [groups, setGroups] = useState<LecturerStudentGroup[]>([]);
  const [year, setYear] = useState<number>(new Date().getFullYear());
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const load = async () => {
    setLoading(true);
    setError('');
    try {
      setGroups(await lecturerApi.libraryTracking(year));
    } catch (err) {
      setGroups([]);
      setError(err instanceof Error ? err.message : 'Failed to load library tracking.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void load();
  }, [year]);

  const yearOptions = useMemo(() => {
    const current = new Date().getFullYear();
    return [current - 1, current, current + 1];
  }, []);

  const summaries = useMemo(() => summarizeLibraryStatuses(groups), [groups]);

  return (
    <ShellLayout title="Library Tracking Hub" subtitle="Track cases forwarded to library for review">
      {error && <div className="alert alert-danger">{error}</div>}

      <div className="d-flex flex-wrap align-items-center gap-2 mb-4">
        <label className="form-label mb-0 fw-semibold small">📅 Year:</label>
        <select
          className="form-select form-select-sm"
          style={{ width: 120, borderRadius: '999px' }}
          value={year}
          onChange={(event) => setYear(Number(event.target.value))}
        >
          {yearOptions.map((value) => (
            <option key={value} value={value}>{value}</option>
          ))}
        </select>
      </div>

      {loading && (
        <div className="text-center py-5">
          <div className="su-spinner mx-auto mb-3" />
          <div className="text-muted">Loading library tracking...</div>
        </div>
      )}

      {!loading && groups.length === 0 && (
        <div className="su-empty-state">
          <div className="su-empty-icon">🏛️</div>
          <h5>No Library Cases</h5>
          <p className="text-muted">No cases in library review for {year}.</p>
        </div>
      )}

      <div className="row g-3">
        {groups.map((group, index) => {
          const summary = summaries[group.studentUserId] ?? { needRevisions: 0, inReview: 0, approved: 0 };
          return (
            <div className="col-lg-6" key={group.studentUserId}>
              <div
                className="su-card su-card-clickable h-100 fade-in"
                role="button"
                onClick={() => navigate(`/lecturer/students/${group.studentUserId}?tab=library`)}
                style={{ animationDelay: `${index * 0.05}s` }}
              >
                <div className="card-body p-4">
                  <div className="d-flex justify-content-between align-items-start mb-2">
                    <div>
                      <h3 className="h6 mb-1 fw-bold">{group.studentName || group.studentEmail}</h3>
                      <div className="text-muted small">
                        🆔 {group.studentIdNumber ?? 'N/A'} • 🏛️ {group.faculty ?? 'N/A'}
                      </div>
                    </div>
                    <span className="badge bg-primary-subtle text-primary-emphasis" style={{ borderRadius: '999px' }}>
                      {group.cases.length} case{group.cases.length > 1 ? 's' : ''}
                    </span>
                  </div>
                  <div className="d-flex gap-2 flex-wrap mt-2">
                    {summary.needRevisions > 0 && (
                      <span className="badge bg-danger-subtle text-danger-emphasis" style={{ borderRadius: '999px' }}>
                        🔄 {summary.needRevisions} Need Revision
                      </span>
                    )}
                    {summary.inReview > 0 && (
                      <span className="badge bg-warning-subtle text-warning-emphasis" style={{ borderRadius: '999px' }}>
                        ⏳ {summary.inReview} In Review
                      </span>
                    )}
                    {summary.approved > 0 && (
                      <span className="badge bg-success-subtle text-success-emphasis" style={{ borderRadius: '999px' }}>
                        ✅ {summary.approved} Approved
                      </span>
                    )}
                  </div>
                </div>
              </div>
            </div>
          );
        })}
      </div>
    </ShellLayout>
  );
}
