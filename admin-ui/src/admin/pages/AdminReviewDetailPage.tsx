import { useEffect, useMemo, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import ShellLayout from '../../layout/ShellLayout';
import { adminApi } from '../../lib/api/admin';
import CaseTimeline from '../../lib/components/CaseTimeline';
import type {
  CaseDetailPayload,
  ChecklistItem,
  ChecklistTemplateResponse,
  PublicationType,
  SubmissionVersion,
} from '../../lib/types/workflow';
import {
  canAdminDecide,
  canAdminSaveChecklist,
  formatStatus,
  isAdminReviewStage,
  isFinalizedForLibrary,
  statusBadgeClass,
} from '../../lib/workflowUi';

type ChecklistDraftRow = {
  checklistItemId: number;
  pass: boolean;
  note: string;
};

function toChecklistMap(items: ChecklistItem[]): Record<number, ChecklistDraftRow> {
  return items.reduce<Record<number, ChecklistDraftRow>>((acc, item) => {
    acc[item.id] = {
      checklistItemId: item.id,
      pass: true,
      note: '',
    };
    return acc;
  }, {});
}

function resolveTemplateForSubmission(
  templates: ChecklistTemplateResponse[],
  submission?: SubmissionVersion
): ChecklistTemplateResponse | null {
  if (!submission) return null;
  if (submission.checklistTemplate?.id) {
    return templates.find((entry) => entry.template.id === submission.checklistTemplate?.id) ?? null;
  }
  return templates.find((entry) => entry.template.active) ?? templates[0] ?? null;
}

export default function AdminReviewDetailPage() {
  const { caseId } = useParams();
  const navigate = useNavigate();
  const [detail, setDetail] = useState<CaseDetailPayload | null>(null);
  const [templates, setTemplates] = useState<ChecklistTemplateResponse[]>([]);
  const [selectedSubmissionId, setSelectedSubmissionId] = useState<number | null>(null);
  const [draft, setDraft] = useState<Record<number, ChecklistDraftRow>>({});
  const [revisionReason, setRevisionReason] = useState('');
  const [rejectReason, setRejectReason] = useState('');
  const [loading, setLoading] = useState(true);
  const [working, setWorking] = useState(false);
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');

  const submissions = detail?.submissions ?? detail?.versions ?? [];

  const selectedSubmission = useMemo(
    () => submissions.find((submission) => submission.id === selectedSubmissionId) ?? submissions[0],
    [selectedSubmissionId, submissions]
  );

  const selectedTemplate = useMemo(
    () => resolveTemplateForSubmission(templates, selectedSubmission),
    [templates, selectedSubmission]
  );

  const checklistItems = selectedTemplate?.items ?? [];
  const status = detail?.case.status;
  const reviewStage = status ? isAdminReviewStage(status) : false;
  const finalized = status ? isFinalizedForLibrary(status) : false;
  const hasActiveTemplate = templates.some((template) => template.template.active);
  const checklistAllowed = status ? canAdminSaveChecklist(status) : false;
  const checklistEditable = checklistAllowed && hasActiveTemplate && checklistItems.length > 0 && !working;
  const decisionAllowed = status ? canAdminDecide(status) : false;
  const trimmedRevisionReason = revisionReason.trim();
  const trimmedRejectReason = rejectReason.trim();
  const checklistSaved = selectedSubmission ? selectedSubmission.status !== 'SUBMITTED' : false;

  const load = async () => {
    if (!caseId) return;
    setLoading(true);
    setError('');
    try {
      const payload = await adminApi.caseDetail(Number(caseId));
      setDetail(payload);

      const publicationType = payload.case.type as PublicationType;
      const templateRows = await adminApi.checklists(publicationType);
      setTemplates(templateRows);

      const submission = (payload.submissions ?? payload.versions ?? [])[0];
      if (submission) {
        setSelectedSubmissionId(submission.id);
      }
      const template = resolveTemplateForSubmission(templateRows, submission);
      setDraft(toChecklistMap(template?.items ?? []));
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to load review detail.');
      setDetail(null);
      setTemplates([]);
      setDraft({});
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void load();
  }, [caseId]);

  useEffect(() => {
    setDraft(toChecklistMap(checklistItems));
  }, [selectedSubmissionId, selectedTemplate?.template.id]);

  const runAction = async (action: () => Promise<void>, successMessage: string) => {
    setWorking(true);
    setError('');
    setMessage('');
    try {
      await action();
      setMessage(successMessage);
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Action failed.');
    } finally {
      setWorking(false);
    }
  };

  const saveChecklist = () => {
    if (!caseId || !selectedSubmission) return;
    const results = checklistItems.map((item) => draft[item.id] ?? { checklistItemId: item.id, pass: true, note: '' });
    return runAction(
      () => adminApi.saveChecklistResults(Number(caseId), selectedSubmission.id, results),
      'Checklist results saved.'
    );
  };

  const requestRevision = () => {
    if (!caseId) return;
    return runAction(
      () => adminApi.requestRevision(Number(caseId), revisionReason.trim()),
      'Revision request sent.'
    );
  };

  const approveCase = () => {
    if (!caseId) return;
    return runAction(
      () => adminApi.approveCase(Number(caseId)),
      'Case approved for clearance.'
    );
  };

  const rejectCase = () => {
    if (!caseId) return;
    if (!rejectReason.trim()) {
      setError('Reject reason is required.');
      return;
    }
    return runAction(
      () => adminApi.rejectCase(Number(caseId), rejectReason.trim()),
      'Case rejected.'
    );
  };

  return (
    <ShellLayout title={`Review Detail — Case #${caseId}`} subtitle="Checklist review, timeline, and decision actions">
      {loading && (
        <div className="text-center py-5">
          <div className="su-spinner mx-auto mb-3" />
          <div className="text-muted">Loading case review detail...</div>
        </div>
      )}
      {error && <div className="alert alert-danger d-flex align-items-center gap-2" style={{ borderRadius: '0.75rem' }}><span>⚠️</span> {error}</div>}
      {message && <div className="alert alert-success d-flex align-items-center gap-2" style={{ borderRadius: '0.75rem' }}><span>✅</span> {message}</div>}

      {detail && (
        <>
          {!reviewStage && !finalized && (
            <div className="alert alert-info">
              This case is not yet in Library Review stage. Waiting for lecturer to forward.
            </div>
          )}
          {finalized && status === 'REJECTED' && (
            <div className="alert alert-danger">
              This case has been rejected and is finalized for the library stage.
            </div>
          )}
          {finalized && status !== 'REJECTED' && (
            <div className="alert alert-secondary">
              This case is already finalized for the library stage.
            </div>
          )}
          <div className="su-card mb-3 fade-in">
            <div className="card-body p-4 d-flex flex-wrap justify-content-between align-items-center gap-2">
              <div>
                <div className="fw-bold" style={{ fontSize: '1.1rem' }}>{detail.case.title || `Case #${detail.case.id}`}</div>
                <div className="d-flex gap-2 align-items-center mt-1">
                  <span className="badge bg-dark-subtle text-dark-emphasis" style={{ borderRadius: '999px' }}>{detail.case.type}</span>
                  <span className={`badge status-badge ${statusBadgeClass(detail.case.status)}`}>
                    {formatStatus(detail.case.status)}
                  </span>
                </div>
              </div>
            </div>
          </div>

          <div className="su-card mb-3 fade-in" style={{ animationDelay: '0.05s' }}>
            <div className="card-body p-4">
              <h3 className="h6 su-page-title">📄 Submission Version</h3>
              <div className="row g-2">
                <div className="col-md-6">
                  <select
                    className="form-select"
                    value={selectedSubmission?.id ?? ''}
                    onChange={(event) => setSelectedSubmissionId(Number(event.target.value))}
                  >
                    {submissions.map((submission) => (
                      <option key={submission.id} value={submission.id}>
                        v{submission.versionNumber} - {submission.originalFilename} ({submission.status}){' '}
                        {submission.createdAt ? `- ${new Date(submission.createdAt).toLocaleString()}` : ''}
                      </option>
                    ))}
                  </select>
                </div>
                <div className="col-md-6 text-muted small d-flex align-items-center">
                  <div>
                    <div>Checklist template: {selectedTemplate ? `v${selectedTemplate.template.version}` : 'Not available'}</div>
                    <div>
                      Uploaded:{' '}
                      {selectedSubmission?.createdAt ? new Date(selectedSubmission.createdAt).toLocaleString() : 'N/A'}
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <div className="su-card mb-3 fade-in" style={{ animationDelay: '0.1s' }}>
            <div className="card-body p-4">
              <h3 className="h6 mb-3 su-page-title">✅ Checklist Review</h3>
              {!hasActiveTemplate && (
                <div className="alert alert-warning d-flex flex-wrap justify-content-between align-items-center gap-2">
                  <span>No active submission template exists for this publication type.</span>
                  <button
                    className="btn btn-outline-primary btn-sm"
                    type="button"
                    onClick={() => navigate('/admin/checklists')}
                  >
                    Go to Submission Template
                  </button>
                </div>
              )}
              {!reviewStage && (
                <div className="alert alert-info mb-3">
                  Checklist review is disabled until the case enters Library Review.
                </div>
              )}
              {checklistItems.length === 0 && (
                <div className="alert alert-warning mb-0">
                  No checklist items found for this submission/template. Confirm template setup first.
                </div>
              )}
              {checklistItems.length > 0 && (
                <div className="table-responsive">
                  <table className="table table-sm align-middle">
                    <thead>
                      <tr>
                        <th>Section</th>
                        <th>Item</th>
                        <th style={{ width: 170 }}>Result</th>
                        <th>Note</th>
                      </tr>
                    </thead>
                    <tbody>
                      {checklistItems.map((item) => (
                        <tr key={item.id}>
                          <td>{item.section || 'General'}</td>
                          <td>
                            <div>{item.itemText}</div>
                            {item.guidanceText && <div className="small text-muted">{item.guidanceText}</div>}
                          </td>
                          <td>
                            <select
                              className="form-select form-select-sm"
                              disabled={!checklistEditable}
                              value={draft[item.id]?.pass ? 'PASS' : 'FAIL'}
                              onChange={(event) =>
                                setDraft((prev) => ({
                                  ...prev,
                                  [item.id]: {
                                    ...(prev[item.id] ?? { checklistItemId: item.id, note: '' }),
                                    checklistItemId: item.id,
                                    pass: event.target.value === 'PASS',
                                  },
                                }))
                              }
                            >
                              <option value="PASS">PASS</option>
                              <option value="FAIL">FAIL</option>
                            </select>
                          </td>
                          <td>
                            <input
                              className="form-control form-control-sm"
                              disabled={!checklistEditable}
                              value={draft[item.id]?.note ?? ''}
                              onChange={(event) =>
                                setDraft((prev) => ({
                                  ...prev,
                                  [item.id]: {
                                    ...(prev[item.id] ?? { checklistItemId: item.id, pass: true }),
                                    checklistItemId: item.id,
                                    note: event.target.value,
                                  },
                                }))
                              }
                              placeholder="Optional note"
                            />
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
              <button
                className="btn btn-primary btn-sm"
                style={{ borderRadius: '999px' }}
                disabled={!checklistAllowed || !hasActiveTemplate || checklistItems.length === 0 || working}
                onClick={() => void saveChecklist()}
              >
                💾 Save Checklist Results
              </button>
            </div>
          </div>

          <div className="su-card mb-3 fade-in" style={{ animationDelay: '0.15s' }}>
            <div className="card-body p-4">
              <h3 className="h6 su-page-title">📜 Timeline</h3>
              <CaseTimeline items={detail.timeline ?? []} />
            </div>
          </div>

          <div className="su-card mb-3 fade-in" style={{ animationDelay: '0.2s' }}>
            <div className="card-body p-4">
              <h3 className="h6 su-page-title">⚖️ Review Decision</h3>
              {!decisionAllowed && (
                <div className="alert alert-info">
                  Review decisions are available only during Library Review stage.
                </div>
              )}
              {decisionAllowed && !checklistSaved && (
                <div className="alert alert-warning">
                  Checklist not saved yet. You can still approve, but consider saving checklist results first.
                </div>
              )}
              <div className="row g-2 mb-3">
                <div className="col-md-8">
                  <label className="form-label">Revision request reason (optional if failed items exist)</label>
                  <input
                    className="form-control"
                    value={revisionReason}
                    onChange={(event) => setRevisionReason(event.target.value)}
                    placeholder="Explain what must be revised"
                    disabled={!decisionAllowed || working}
                  />
                </div>
                <div className="col-md-4 d-flex align-items-end">
                  <button
                    className="btn btn-outline-warning w-100"
                    disabled={working || !decisionAllowed || trimmedRevisionReason.length === 0}
                    onClick={() => void requestRevision()}
                    title={
                      !decisionAllowed
                        ? 'Review decisions are only available during library review.'
                        : trimmedRevisionReason.length === 0
                          ? 'Provide a revision reason to continue.'
                          : undefined
                    }
                  >
                    Request Revision
                  </button>
                </div>
              </div>

              <div className="row g-2">
                <div className="col-md-8">
                  <label className="form-label">Reject reason (required for reject)</label>
                  <input
                    className="form-control"
                    value={rejectReason}
                    onChange={(event) => setRejectReason(event.target.value)}
                    placeholder="Provide reject reason"
                    disabled={!decisionAllowed || working}
                  />
                </div>
                <div className="col-md-2 d-flex align-items-end">
                  <button
                    className="btn btn-success w-100"
                    style={{ borderRadius: '999px' }}
                    disabled={working || !decisionAllowed}
                    onClick={() => void approveCase()}
                  >
                    ✅ Approve
                  </button>
                </div>
                <div className="col-md-2 d-flex align-items-end">
                  <button
                    className="btn btn-danger w-100"
                    disabled={working || !decisionAllowed || trimmedRejectReason.length === 0}
                    onClick={() => void rejectCase()}
                    title={
                      !decisionAllowed
                        ? 'Review decisions are only available during library review.'
                        : trimmedRejectReason.length === 0
                          ? 'Provide a reject reason to continue.'
                          : undefined
                    }
                  >
                    Reject
                  </button>
                </div>
              </div>
            </div>
          </div>

          <div className="su-card fade-in" style={{ animationDelay: '0.25s' }}>
            <div className="card-body p-4">
              <h3 className="h6 su-page-title">💬 Comments</h3>
              {(detail.comments || []).length === 0 ? (
                <div className="text-muted small text-center py-3">No comments yet.</div>
              ) : (
                <div className="vstack gap-2">
                  {(detail.comments || []).map((comment) => (
                    <div className="p-3" key={comment.id}
                      style={{ background: '#f8fafc', borderRadius: '0.6rem', border: '1px solid #e8eff5' }}>
                      <div className="d-flex justify-content-between align-items-center mb-1">
                        <div className="fw-semibold small">
                          {comment.authorRole}
                          {comment.authorEmail ? ` — ${comment.authorEmail}` : ''}
                        </div>
                        <div className="text-muted small">
                          {comment.createdAt ? new Date(comment.createdAt).toLocaleString() : 'N/A'}
                        </div>
                      </div>
                      <div style={{ whiteSpace: 'pre-wrap' }}>{comment.body}</div>
                    </div>
                  ))}
                </div>
              )}
            </div>
          </div>
        </>
      )}
    </ShellLayout>
  );
}
