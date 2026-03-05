import { useEffect, useMemo, useState } from 'react';
import ShellLayout from '../../layout/ShellLayout';
import { adminApi } from '../../lib/api/admin';
import type { AdminRegistrationApproval } from '../../lib/types/workflow';
import { formatStatus, statusBadgeClass } from '../../lib/workflowUi';

export default function AdminRegistrationApprovalsPage() {
  const [rows, setRows] = useState<AdminRegistrationApproval[]>([]);
  const [rejectNotes, setRejectNotes] = useState<Record<number, string>>({});
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const load = async () => {
    setLoading(true);
    setError('');
    try {
      setRows(await adminApi.registrationApprovals());
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to load registration approval queue.');
      setRows([]);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void load();
  }, []);

  const approve = async (caseId: number) => {
    try {
      await adminApi.approveRegistration(caseId);
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Approve action failed.');
    }
  };

  const reject = async (caseId: number) => {
    const note = rejectNotes[caseId]?.trim();
    if (!note) {
      setError('Rejection reason is required.');
      return;
    }
    try {
      await adminApi.rejectRegistration(caseId, note);
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Reject action failed.');
    }
  };

  const sortedRows = useMemo(
    () =>
      [...rows].sort((a, b) => {
        const aTime = a.submittedAt ? new Date(a.submittedAt).getTime() : 0;
        const bTime = b.submittedAt ? new Date(b.submittedAt).getTime() : 0;
        return bTime - aTime;
      }),
    [rows]
  );

  return (
    <ShellLayout title="Registration Verification" subtitle="Verify registrations after supervisor approval">
      {error && <div className="alert alert-danger d-flex align-items-center gap-2" style={{ borderRadius: '0.75rem' }}><span>⚠️</span> {error}</div>}

      {loading && (
        <div className="text-center py-5">
          <div className="su-spinner mx-auto mb-3" />
          <div className="text-muted">Loading registration queue...</div>
        </div>
      )}

      {!loading && sortedRows.length === 0 && (
        <div className="su-empty-state">
          <div className="su-empty-icon">📋</div>
          <h5>All Clear!</h5>
          <p className="text-muted">No pending registration verifications at this time.</p>
        </div>
      )}

      <div className="vstack gap-3">
        {sortedRows.map((row, index) => (
          <div className="su-card fade-in" key={row.caseId} style={{ animationDelay: `${index * 0.05}s` }}>
            <div className="card-body p-4">
              <div className="d-flex flex-wrap justify-content-between align-items-start gap-2 mb-3">
                <div>
                  <h5 className="fw-bold mb-1">{row.title || `Case #${row.caseId}`}</h5>
                  <div className="d-flex flex-wrap gap-2 align-items-center mb-2">
                    <span className="badge bg-dark-subtle text-dark-emphasis" style={{ borderRadius: '999px' }}>{row.type}</span>
                    <span className={`badge status-badge ${statusBadgeClass(row.status)}`}>{formatStatus(row.status)}</span>
                  </div>
                  <div className="d-flex flex-wrap gap-3 text-muted small">
                    <span>👤 {row.studentName || row.studentEmail} {row.studentIdNumber ? `(${row.studentIdNumber})` : ''}</span>
                    <span>🏛️ {row.faculty || 'N/A'} {row.program ? `• ${row.program}` : ''}</span>
                  </div>
                  <div className="text-muted small mt-1">
                    Submitted: {row.submittedAt ? new Date(row.submittedAt).toLocaleString() : 'N/A'}
                  </div>
                </div>
                <button className="btn btn-success btn-sm" style={{ borderRadius: '999px', padding: '0.4rem 1.2rem' }} onClick={() => void approve(row.caseId)}>
                  ✅ Verify
                </button>
              </div>

              <div className="p-3" style={{ background: '#fef3f2', borderRadius: '0.6rem', border: '1px solid #fecaca' }}>
                <label className="form-label mb-1 small fw-semibold" style={{ color: '#dc3545' }}>❌ Reject with reason</label>
                <div className="d-flex gap-2">
                  <input
                    className="form-control form-control-sm"
                    value={rejectNotes[row.caseId] ?? ''}
                    onChange={(event) =>
                      setRejectNotes((prev) => ({
                        ...prev,
                        [row.caseId]: event.target.value,
                      }))
                    }
                    placeholder="Provide rejection reason..."
                    style={{ borderRadius: '999px' }}
                  />
                  <button className="btn btn-outline-danger btn-sm" style={{ borderRadius: '999px', whiteSpace: 'nowrap' }} onClick={() => void reject(row.caseId)}>
                    Reject
                  </button>
                </div>
              </div>
            </div>
          </div>
        ))}
      </div>
    </ShellLayout>
  );
}
