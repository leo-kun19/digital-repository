import { useEffect, useMemo, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import ShellLayout from '../../layout/ShellLayout';
import { studentApi } from '../../lib/api/student';
import CaseTimeline from '../../lib/components/CaseTimeline';
import type { CaseDetailPayload, ChecklistResult } from '../../lib/types/workflow';
import {
  canSubmitClearance,
  canSubmitRegistration,
  canUploadSubmission,
  getStageIndex,
  getStageKey,
  formatStatus,
  statusBadgeClass,
  formatStageName,
} from '../../lib/workflowUi';

const STAGES = ['registration', 'supervisor', 'library', 'clearance', 'publish'] as const;

export default function StudentCaseDetailPage() {
  const { caseId } = useParams();
  const navigate = useNavigate();

  const [detail, setDetail] = useState<CaseDetailPayload | null>(null);
  const [checklist, setChecklist] = useState<ChecklistResult[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const load = async () => {
    if (!caseId) return;
    setLoading(true);
    setError('');
    try {
      const [caseDetail, checklistResults] = await Promise.all([
        studentApi.caseDetail(Number(caseId)),
        studentApi.listChecklistResults(Number(caseId)).catch(() => []),
      ]);
      setDetail(caseDetail);
      setChecklist(checklistResults);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to load case detail.');
      setDetail(null);
      setChecklist([]);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void load();
  }, [caseId]);

  const status = detail?.case.status;
  const stageKey = useMemo(() => (status ? getStageKey(status) : null), [status]);
  const currentStage = useMemo(() => (status ? getStageIndex(status) : 0), [status]);
  const isRejected = stageKey === 'rejected';
  const clearanceActionEnabled = status
    ? [
      'APPROVED_FOR_CLEARANCE',
      'CLEARANCE_SUBMITTED',
      'CLEARANCE_APPROVED',
      'READY_TO_PUBLISH',
      'PUBLISHED',
    ].includes(status)
    : false;
  const showClearance = clearanceActionEnabled;
  const clearanceButtonLabel = status && canSubmitClearance(status) ? 'Submit Clearance' : 'Open Clearance';

  const submitRegistration = async () => {
    if (!caseId) return;
    try {
      await studentApi.submitRegistration(Number(caseId), true);
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to submit registration.');
    }
  };

  if (loading) {
    return (
      <ShellLayout title="Case Detail">
        <div className="text-center py-5">
          <div className="su-spinner mx-auto mb-3" />
          <div className="text-muted">Loading case details...</div>
        </div>
      </ShellLayout>
    );
  }

  if (error || !detail) {
    return (
      <ShellLayout title="Case Detail">
        <div className="alert alert-danger">{error || 'Case detail is unavailable.'}</div>
      </ShellLayout>
    );
  }

  return (
    <ShellLayout
      title={`Case #${detail.case.id}`}
      subtitle={`${detail.case.title || 'Untitled'} | ${detail.case.type}`}
    >
      {/* Status & Stage */}
      <div className="d-flex flex-wrap align-items-center gap-2 mb-4">
        <span className={`badge status-badge ${statusBadgeClass(detail.case.status)}`} style={{ fontSize: '0.85rem', padding: '0.4rem 0.9rem' }}>
          {formatStatus(detail.case.status)}
        </span>
        {stageKey && stageKey !== 'rejected' && (
          <span className="badge bg-light text-muted" style={{ borderRadius: '999px', fontSize: '0.75rem' }}>
            Stage: {formatStageName(stageKey)}
          </span>
        )}
      </div>

      {isRejected && (
        <div className="alert alert-danger d-flex align-items-center gap-2" style={{ borderRadius: '0.75rem' }}>
          <span style={{ fontSize: '1.2rem' }}>❌</span>
          <div><strong>Rejected.</strong> Review the feedback and update your registration before resubmitting.</div>
        </div>
      )}

      {/* ===== WORKFLOW STEPPER ===== */}
      <div className="su-card mb-4 fade-in">
        <div className="card-body">
          <h3 className="h6 mb-3 su-page-title">📊 Workflow Progress</h3>
          <div className="su-stepper">
            {STAGES.map((stage, index) => {
              const isCompleted = currentStage > index;
              const isActive = currentStage === index;
              return (
                <div
                  key={stage}
                  className={`su-stepper-step ${isCompleted ? 'completed' : ''} ${isActive ? 'active' : ''}`}
                >
                  <div className="su-stepper-dot">
                    {isCompleted ? '✓' : index + 1}
                  </div>
                  {formatStageName(stage)}
                </div>
              );
            })}
          </div>
        </div>
      </div>

      {/* Timeline */}
      <div className="su-card mb-3 fade-in" style={{ animationDelay: '0.1s' }}>
        <div className="card-body">
          <h3 className="h6 mb-3 su-page-title">📜 Activity Timeline</h3>
          <CaseTimeline items={detail.timeline ?? []} />
        </div>
      </div>

      {/* Registration */}
      <div className="su-card mb-3 fade-in" style={{ animationDelay: '0.15s' }}>
        <div className="card-body">
          <h3 className="h6 su-page-title mb-2">📋 Registration</h3>
          <div className="small text-muted mb-3">Edit draft and resubmit when required.</div>
          <div className="d-flex flex-wrap justify-content-between align-items-center gap-2">
            <strong>{detail.registration?.title || 'Untitled'}</strong>
            <button
              className="btn btn-primary btn-sm"
              style={{ borderRadius: '999px' }}
              onClick={() => void submitRegistration()}
              disabled={!canSubmitRegistration(detail.case.status)}
            >
              📨 Submit for Approval
            </button>
          </div>
        </div>
      </div>

      {/* Submission Versions */}
      <div className="su-card mb-3 fade-in" style={{ animationDelay: '0.2s' }}>
        <div className="card-body">
          <div className="d-flex flex-wrap justify-content-between align-items-center mb-3">
            <h3 className="h6 mb-0 su-page-title">📄 Submission Versions</h3>
            <button
              className="btn btn-outline-primary btn-sm"
              style={{ borderRadius: '999px' }}
              onClick={() => navigate(`/student/cases/${detail.case.id}/submission`)}
              disabled={!canUploadSubmission(detail.case.status)}
            >
              Open Submission Page
            </button>
          </div>
          {(detail.versions || []).length === 0 ? (
            <div className="text-muted small text-center py-3">No submission versions yet.</div>
          ) : (
            <div className="vstack gap-2">
              {(detail.versions || []).map((version) => (
                <div className="d-flex justify-content-between align-items-center p-2 px-3" key={version.id}
                  style={{ background: '#f8fafc', borderRadius: '0.5rem', border: '1px solid #e8eff5' }}>
                  <div>
                    <span className="fw-semibold">v{version.versionNumber}</span>
                    <span className="text-muted mx-1">—</span>
                    <span>{version.originalFilename}</span>
                    <div className="text-muted small">
                      Uploaded: {version.createdAt ? new Date(version.createdAt).toLocaleString() : 'N/A'}
                    </div>
                  </div>
                  <span className={`badge ${version.status === 'APPROVED' ? 'bg-success' : 'bg-secondary'}`} style={{ borderRadius: '999px' }}>
                    {version.status}
                  </span>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>

      {/* Checklist */}
      <div className="su-card mb-3 fade-in" style={{ animationDelay: '0.25s' }}>
        <div className="card-body">
          <h3 className="h6 su-page-title mb-3">✅ Checklist Results</h3>
          {checklist.length === 0 ? (
            <div className="text-muted small text-center py-3">Checklist has not been reviewed yet.</div>
          ) : (
            <div className="vstack gap-2">
              {checklist.map((result) => (
                <div className="d-flex justify-content-between align-items-center p-2 px-3" key={result.id}
                  style={{ background: '#f8fafc', borderRadius: '0.5rem', border: '1px solid #e8eff5' }}>
                  <div>
                    <div className="fw-semibold">{result.checklistItem?.itemText || `Item #${result.id}`}</div>
                    {result.note && <div className="text-muted small">{result.note}</div>}
                  </div>
                  <span className={`badge ${result.passFail === 'PASS' ? 'bg-success' : 'bg-danger'}`} style={{ borderRadius: '999px' }}>
                    {result.passFail}
                  </span>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>

      {/* Clearance */}
      {showClearance && (
        <div className="su-card mb-3 fade-in" style={{ animationDelay: '0.3s' }}>
          <div className="card-body">
            <div className="d-flex flex-wrap justify-content-between align-items-center gap-2">
              <div>
                <h3 className="h6 mb-1 su-page-title">🏛️ Library Clearance</h3>
                <p className="small text-muted mb-0">
                  Submit at APPROVED_FOR_CLEARANCE, then open to track status and updates.
                </p>
              </div>
              <button
                className="btn btn-primary btn-sm"
                style={{ borderRadius: '999px' }}
                onClick={() => navigate(`/student/clearance/${detail.case.id}`)}
                disabled={!clearanceActionEnabled}
              >
                {clearanceButtonLabel}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Comments */}
      <div className="su-card fade-in" style={{ animationDelay: '0.35s' }}>
        <div className="card-body">
          <h3 className="h6 su-page-title mb-3">💬 Comments</h3>
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
    </ShellLayout>
  );
}
