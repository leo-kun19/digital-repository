import { useEffect, useMemo, useState } from 'react';
import { useNavigate, useParams, useSearchParams } from 'react-router-dom';
import ShellLayout from '../../layout/ShellLayout';
import { lecturerApi, type LecturerCaseWorkItem, type LecturerStudentGroup, type LecturerSubmissionVersion } from '../../lib/api/lecturer';
import CaseTimeline from '../../lib/components/CaseTimeline';
import { formatStatus, statusBadgeClass } from '../../lib/workflowUi';
import type { TimelineItem } from '../../lib/types/workflow';

const supervisorStatuses: Array<LecturerCaseWorkItem['status']> = [
  'UNDER_SUPERVISOR_REVIEW',
  'NEEDS_REVISION_SUPERVISOR',
  'READY_TO_FORWARD',
];

export default function LecturerStudentDetailPage() {
  const { studentId } = useParams();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const tab = searchParams.get('tab') === 'library' ? 'library' : 'supervisor';

  const [group, setGroup] = useState<LecturerStudentGroup | null>(null);
  const [year, setYear] = useState<number>(new Date().getFullYear());
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [workingCaseId, setWorkingCaseId] = useState<number | null>(null);
  const [commentDrafts, setCommentDrafts] = useState<Record<number, string>>({});
  const [revisionDrafts, setRevisionDrafts] = useState<Record<number, string>>({});
  const [timelines, setTimelines] = useState<Record<number, TimelineItem[]>>({});
  const [timelineOpen, setTimelineOpen] = useState<Record<number, boolean>>({});
  const [submissions, setSubmissions] = useState<Record<number, LecturerSubmissionVersion[]>>({});
  const [submissionsOpen, setSubmissionsOpen] = useState<Record<number, boolean>>({});

  const load = async () => {
    if (!studentId) return;
    setLoading(true);
    setError('');
    try {
      const groups = await lecturerApi.myStudents(year);
      const match = groups.find((g) => String(g.studentUserId) === String(studentId)) ?? null;
      setGroup(match);
    } catch (err) {
      setGroup(null);
      setError(err instanceof Error ? err.message : 'Failed to load student detail.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void load();
  }, [studentId, year]);

  const yearOptions = useMemo(() => {
    const current = new Date().getFullYear();
    return [current - 1, current, current + 1];
  }, []);

  const runAction = async (caseId: number, action: () => Promise<void>) => {
    setWorkingCaseId(caseId);
    setError('');
    try {
      await action();
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Action failed.');
    } finally {
      setWorkingCaseId(null);
    }
  };

  const toggleTimeline = async (caseId: number) => {
    const isOpen = timelineOpen[caseId];
    if (!isOpen && !timelines[caseId]) {
      const items = await lecturerApi.caseTimeline(caseId).catch(() => []);
      setTimelines((prev) => ({ ...prev, [caseId]: items }));
    }
    setTimelineOpen((prev) => ({ ...prev, [caseId]: !isOpen }));
  };

  const toggleSubmissions = async (caseId: number) => {
    const isOpen = submissionsOpen[caseId];
    if (!isOpen && !submissions[caseId]) {
      const items = await lecturerApi.caseSubmissions(caseId).catch(() => []);
      setSubmissions((prev) => ({ ...prev, [caseId]: items }));
    }
    setSubmissionsOpen((prev) => ({ ...prev, [caseId]: !isOpen }));
  };

  const renderTimestamp = (value?: string | null) => (value ? new Date(value).toLocaleString() : '—');

  return (
    <ShellLayout title="Student Detail" subtitle="Case status, timestamps, and supervisor actions">
      {error && <div className="alert alert-danger d-flex align-items-center gap-2" style={{ borderRadius: '0.75rem' }}><span>⚠️</span> {error}</div>}

      <div className="d-flex flex-wrap align-items-center gap-2 mb-4">
        <button className="btn btn-outline-secondary btn-sm" style={{ borderRadius: '999px' }} onClick={() => navigate(-1)}>
          ← Back
        </button>
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
          <div className="text-muted">Loading student detail...</div>
        </div>
      )}

      {!loading && !group && (
        <div className="su-empty-state">
          <div className="su-empty-icon">🎓</div>
          <h5>Student Not Found</h5>
          <p className="text-muted">No data found for selected year.</p>
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
                  <h3 className="h5 mb-0 fw-bold">{group.studentName || group.studentEmail}</h3>
                  <div className="text-muted small">
                    🆔 {group.studentIdNumber ?? 'N/A'} • 🏛️ {group.faculty ?? 'N/A'} {group.program ? `• 📖 ${group.program}` : ''}
                  </div>
                </div>
                <span className="badge bg-primary-subtle text-primary-emphasis ms-auto" style={{ borderRadius: '999px', fontSize: '0.8rem' }}>
                  {group.cases.length} case{group.cases.length > 1 ? 's' : ''}
                </span>
              </div>
            </div>
          </div>

          {/* Cases */}
          <div className="vstack gap-3">
            {group.cases.map((c, index) => {
              const canAct = tab !== 'library' && supervisorStatuses.includes(c.status);
              const busy = workingCaseId === c.caseId;
              return (
                <div key={c.caseId} className="su-card fade-in" style={{ animationDelay: `${index * 0.06}s` }}>
                  <div className="card-body p-4">
                    <div className="d-flex flex-wrap justify-content-between align-items-start gap-2 mb-3">
                      <div>
                        <h4 className="h6 fw-bold mb-1">{c.registrationTitle || `Case #${c.caseId}`}</h4>
                        <div className="d-flex flex-wrap gap-2 align-items-center">
                          <span className="badge bg-dark-subtle text-dark-emphasis" style={{ borderRadius: '999px' }}>{c.type}</span>
                          <span className={`badge status-badge ${statusBadgeClass(c.status)}`}>{formatStatus(c.status)}</span>
                          {c.registrationYear && <span className="text-muted small">📅 {c.registrationYear}</span>}
                        </div>
                      </div>
                      <a
                        className="btn btn-outline-primary btn-sm"
                        style={{ borderRadius: '999px' }}
                        href={`/api/lecturer/cases/${c.caseId}/submissions/latest/download`}
                      >
                        ⬇️ Download Latest
                      </a>
                    </div>

                    {/* Timestamps */}
                    <div className="row g-2 mb-3">
                      {[
                        { label: 'Last Submission', value: renderTimestamp(c.latestSubmissionAt), icon: '📄' },
                        { label: 'Lecturer Feedback', value: renderTimestamp(c.lastLecturerFeedbackAt), icon: '💬' },
                        { label: 'Forwarded', value: renderTimestamp(c.lecturerForwardedAt), icon: '📦' },
                        { label: 'Library Feedback', value: renderTimestamp(c.lastLibraryFeedbackAt), icon: '🏛️' },
                      ].map((ts) => (
                        <div className="col-6 col-md-3" key={ts.label}>
                          <div className="p-2" style={{ background: '#f8fafc', borderRadius: '0.5rem', fontSize: '0.78rem' }}>
                            <div className="text-muted">{ts.icon} {ts.label}</div>
                            <div className="fw-semibold">{ts.value}</div>
                          </div>
                        </div>
                      ))}
                    </div>

                    {/* Toggle buttons */}
                    <div className="d-flex flex-wrap gap-2 mb-3">
                      <button className="btn btn-outline-secondary btn-sm" style={{ borderRadius: '999px' }} onClick={() => void toggleTimeline(c.caseId)}>
                        {timelineOpen[c.caseId] ? '📜 Hide Timeline' : '📜 Show Timeline'}
                      </button>
                      <button className="btn btn-outline-secondary btn-sm" style={{ borderRadius: '999px' }} onClick={() => void toggleSubmissions(c.caseId)}>
                        {submissionsOpen[c.caseId] ? '📄 Hide Submissions' : '📄 View Submissions'}
                      </button>
                    </div>

                    {/* Timeline */}
                    {timelineOpen[c.caseId] && (
                      <div className="mb-3 p-3" style={{ background: '#f8fafc', borderRadius: '0.6rem' }}>
                        <h6 className="fw-bold mb-2">📜 Timeline</h6>
                        <CaseTimeline items={timelines[c.caseId] ?? []} />
                      </div>
                    )}

                    {/* Submissions */}
                    {submissionsOpen[c.caseId] && (
                      <div className="mb-3 p-3" style={{ background: '#f8fafc', borderRadius: '0.6rem' }}>
                        <h6 className="fw-bold mb-2">📄 Submissions</h6>
                        {(submissions[c.caseId] ?? []).length === 0 ? (
                          <div className="text-muted small">No submission versions yet.</div>
                        ) : (
                          <div className="vstack gap-2">
                            {(submissions[c.caseId] ?? []).map((version) => (
                              <div key={version.id} className="d-flex justify-content-between align-items-center p-2 bg-white" style={{ borderRadius: '0.4rem', border: '1px solid #e8eff5' }}>
                                <div>
                                  <span className="fw-semibold">v{version.versionNumber}</span> — {version.originalFilename}
                                  <div className="text-muted small">Uploaded: {version.createdAt ? new Date(version.createdAt).toLocaleString() : 'N/A'}</div>
                                </div>
                                <div className="d-flex gap-2 align-items-center">
                                  <span className="badge bg-secondary" style={{ borderRadius: '999px' }}>{version.status}</span>
                                  <a className="btn btn-outline-primary btn-sm" style={{ borderRadius: '999px' }} href={`/api/lecturer/cases/${c.caseId}/submissions/${version.id}/download`}>
                                    ⬇️
                                  </a>
                                </div>
                              </div>
                            ))}
                          </div>
                        )}
                      </div>
                    )}

                    {/* Supervisor Actions */}
                    {canAct && (
                      <div className="p-3" style={{ background: '#f0f9ff', borderRadius: '0.6rem', border: '1px solid #bae6fd' }}>
                        <h6 className="fw-bold mb-2">⚡ Supervisor Actions</h6>
                        <div className="mb-2">
                          <textarea
                            className="form-control form-control-sm mb-1"
                            rows={2}
                            placeholder="Post feedback comment..."
                            value={commentDrafts[c.caseId] ?? ''}
                            onChange={(event) =>
                              setCommentDrafts((prev) => ({ ...prev, [c.caseId]: event.target.value }))
                            }
                            disabled={busy}
                            style={{ borderRadius: '0.5rem' }}
                          />
                          <button
                            className="btn btn-outline-primary btn-sm"
                            style={{ borderRadius: '999px' }}
                            disabled={busy}
                            onClick={() =>
                              void runAction(c.caseId, () => {
                                const body = commentDrafts[c.caseId]?.trim();
                                if (!body) { setError('Comment body is required.'); return Promise.resolve(); }
                                return lecturerApi.comment(c.caseId, body).then(() => {
                                  setCommentDrafts((prev) => ({ ...prev, [c.caseId]: '' }));
                                });
                              })
                            }
                          >
                            💬 Post Feedback
                          </button>
                        </div>
                        <div className="mb-2">
                          <textarea
                            className="form-control form-control-sm mb-1"
                            rows={2}
                            placeholder="Revision request reason..."
                            value={revisionDrafts[c.caseId] ?? ''}
                            onChange={(event) =>
                              setRevisionDrafts((prev) => ({ ...prev, [c.caseId]: event.target.value }))
                            }
                            disabled={busy}
                            style={{ borderRadius: '0.5rem' }}
                          />
                          <button
                            className="btn btn-warning btn-sm"
                            style={{ borderRadius: '999px' }}
                            disabled={busy}
                            onClick={() =>
                              void runAction(c.caseId, () => {
                                const reason = revisionDrafts[c.caseId]?.trim();
                                if (!reason) { setError('Revision reason is required.'); return Promise.resolve(); }
                                return lecturerApi.requestRevision(c.caseId, reason).then(() => {
                                  setRevisionDrafts((prev) => ({ ...prev, [c.caseId]: '' }));
                                });
                              })
                            }
                          >
                            🔄 Request Revision
                          </button>
                        </div>
                        <button
                          className="btn btn-success"
                          style={{ borderRadius: '999px' }}
                          disabled={busy}
                          onClick={() => void runAction(c.caseId, () => lecturerApi.approveAndForward(c.caseId).then(() => undefined))}
                        >
                          ✅ Approve & Forward to Library
                        </button>
                      </div>
                    )}
                  </div>
                </div>
              );
            })}
          </div>

          {group.cases.length === 0 && (
            <div className="su-empty-state mt-3">
              <div className="su-empty-icon">📁</div>
              <h5>No Cases</h5>
              <p className="text-muted">No cases available for this student.</p>
            </div>
          )}
        </>
      )}
    </ShellLayout>
  );
}
