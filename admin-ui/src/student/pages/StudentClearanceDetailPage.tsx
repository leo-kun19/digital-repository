import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import ShellLayout from '../../layout/ShellLayout';
import { studentApi } from '../../lib/api/student';
import type { CaseDetailPayload } from '../../lib/types/workflow';
import { canSubmitClearance, formatStatus, statusBadgeClass } from '../../lib/workflowUi';

export default function StudentClearanceDetailPage() {
  const { caseId } = useParams();
  const navigate = useNavigate();
  const [detail, setDetail] = useState<CaseDetailPayload | null>(null);
  const [note, setNote] = useState('');
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');

  const load = async () => {
    if (!caseId) return;
    setLoading(true);
    setError('');
    try {
      const payload = await studentApi.caseDetail(Number(caseId));
      setDetail(payload);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to load clearance case.');
      setDetail(null);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void load();
  }, [caseId]);

  const onSubmit = async () => {
    if (!caseId || !detail) return;
    if (!canSubmitClearance(detail.case.status)) {
      setError('Clearance submission is gated until case is APPROVED_FOR_CLEARANCE.');
      return;
    }

    setSubmitting(true);
    setError('');
    setMessage('');
    try {
      await studentApi.submitClearance(Number(caseId), note);
      setMessage('Clearance submitted successfully.');
      navigate(`/student/cases/${caseId}`);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to submit clearance.');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <ShellLayout title={`🏛️ Clearance Form — Case #${caseId}`} subtitle="Library clearance declaration for publication">
      {loading && (
        <div className="text-center py-5">
          <div className="su-spinner mx-auto mb-3" />
          <div className="text-muted">Loading clearance form...</div>
        </div>
      )}
      {error && <div className="alert alert-danger d-flex align-items-center gap-2" style={{ borderRadius: '0.75rem' }}><span>⚠️</span> {error}</div>}
      {message && <div className="alert alert-success d-flex align-items-center gap-2" style={{ borderRadius: '0.75rem' }}><span>✅</span> {message}</div>}

      {detail && (
        <div className="su-card fade-in">
          <div className="card-body p-4">
            <div className="d-flex flex-wrap align-items-center gap-2 mb-4">
              <span className={`badge status-badge ${statusBadgeClass(detail.case.status)}`} style={{ fontSize: '0.85rem', padding: '0.4rem 0.9rem' }}>
                {formatStatus(detail.case.status)}
              </span>
              <span className="text-muted small">Current case status</span>
            </div>

            <div className="p-3 mb-4" style={{ background: '#f0f6fa', borderRadius: '0.75rem', border: '1px solid #d5e3ed' }}>
              <h6 className="fw-bold mb-2">📋 Declaration</h6>
              <p className="small text-muted mb-0">
                By submitting this clearance form, I hereby declare that I have no outstanding library obligations,
                including but not limited to borrowed books, equipment, or any other library assets. I understand
                that this clearance is a requirement for the publication of my thesis/article in the Sampoerna
                University Digital Repository.
              </p>
            </div>

            <div className="mb-4">
              <label className="form-label">📝 Additional Notes (optional)</label>
              <textarea
                className="form-control"
                rows={5}
                value={note}
                onChange={(event) => setNote(event.target.value)}
                placeholder="Add any additional notes for the library admin..."
                style={{ borderRadius: '0.6rem' }}
              />
            </div>

            <div className="d-flex flex-wrap gap-2">
              <button
                className="btn btn-primary"
                style={{ borderRadius: '999px', padding: '0.5rem 1.5rem' }}
                onClick={() => void onSubmit()}
                disabled={submitting || !canSubmitClearance(detail.case.status)}
              >
                {submitting ? '⏳ Submitting...' : '📨 Submit Clearance'}
              </button>
              <button
                className="btn btn-outline-secondary"
                style={{ borderRadius: '999px' }}
                onClick={() => navigate(`/student/cases/${caseId}`)}
              >
                ← Back to Case
              </button>
            </div>
          </div>
        </div>
      )}
    </ShellLayout>
  );
}
