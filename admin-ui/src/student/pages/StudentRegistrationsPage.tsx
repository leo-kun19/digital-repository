import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import ShellLayout from '../../layout/ShellLayout';
import { studentApi } from '../../lib/api/student';
import type { CaseSummary } from '../../lib/types/workflow';
import { canSubmitRegistration, formatStatus, statusBadgeClass } from '../../lib/workflowUi';

export default function StudentRegistrationsPage() {
  const navigate = useNavigate();
  const [cases, setCases] = useState<CaseSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const load = async () => {
    setLoading(true);
    setError('');
    try {
      setCases(await studentApi.listCases());
    } catch (err) {
      setCases([]);
      setError(err instanceof Error ? err.message : 'Unable to load registrations.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void load();
  }, []);

  const submitForApproval = async (caseId: number) => {
    try {
      await studentApi.submitRegistration(caseId, true);
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to submit registration.');
    }
  };

  const statusHint: Record<string, string> = {
    REGISTRATION_PENDING: '⏳ Waiting supervisor approval',
    REGISTRATION_APPROVED: '⏳ Waiting library verification',
    REGISTRATION_VERIFIED: '✅ Verified — go to Submission to upload',
    REJECTED: '❌ Rejected — edit and resubmit',
  };

  return (
    <ShellLayout title="Publication Registration" subtitle="Prepare registration metadata and submit for approvals">
      <div className="d-flex justify-content-between align-items-center mb-4">
        <button className="btn btn-outline-secondary btn-sm" style={{ borderRadius: '999px' }} onClick={() => void load()} disabled={loading}>
          {loading ? '⏳ Loading...' : '🔄 Refresh'}
        </button>
        <button className="btn btn-primary" style={{ borderRadius: '999px' }} onClick={() => navigate('/student/registrations/new')}>
          ➕ Create New Registration
        </button>
      </div>

      {error && <div className="alert alert-danger">{error}</div>}

      {!loading && cases.length === 0 && (
        <div className="su-empty-state">
          <div className="su-empty-icon">📋</div>
          <h5>No Registrations Yet</h5>
          <p className="text-muted">Create your first publication registration to get started.</p>
          <button className="btn btn-primary" onClick={() => navigate('/student/registrations/new')}>
            Create First Registration
          </button>
        </div>
      )}

      {cases.length > 0 && (
        <div className="table-responsive su-card">
          <table className="table table-hover align-middle mb-0">
            <thead>
              <tr>
                <th>Case</th>
                <th>Type</th>
                <th>Status</th>
                <th>Updated</th>
                <th className="text-end">Actions</th>
              </tr>
            </thead>
            <tbody>
              {cases.map((c) => {
                const canSubmit = canSubmitRegistration(c.status);

                return (
                  <tr key={c.id}>
                    <td className="fw-semibold">{c.title || `Case #${c.id}`}</td>
                    <td><span className="badge bg-dark-subtle text-dark-emphasis" style={{ borderRadius: '999px' }}>{c.type}</span></td>
                    <td>
                      <span className={`badge status-badge ${statusBadgeClass(c.status)}`}>
                        {formatStatus(c.status)}
                      </span>
                    </td>
                    <td className="text-muted small">{c.updatedAt ? new Date(c.updatedAt).toLocaleString() : 'N/A'}</td>
                    <td className="text-end">
                      <div className="d-flex justify-content-end gap-2">
                        <button className="btn btn-outline-primary btn-sm" style={{ borderRadius: '999px' }} onClick={() => navigate(`/student/cases/${c.id}`)}>
                          Open
                        </button>
                        <button
                          className="btn btn-primary btn-sm"
                          style={{ borderRadius: '999px' }}
                          onClick={() => void submitForApproval(c.id)}
                          disabled={!canSubmit}
                        >
                          📨 Submit
                        </button>
                      </div>
                      {statusHint[c.status] && (
                        <div className="small text-muted mt-1">{statusHint[c.status]}</div>
                      )}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}
    </ShellLayout>
  );
}
