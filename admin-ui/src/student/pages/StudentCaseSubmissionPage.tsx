import { useEffect, useMemo, useState } from 'react';
import { useParams } from 'react-router-dom';
import ShellLayout from '../../layout/ShellLayout';
import { studentApi, type SubmissionMetaPayload } from '../../lib/api/student';
import { masterApi, type Faculty } from '../../lib/api/master';
import { ApiError } from '../../lib/api/http';
import type { CaseDetailPayload, SubmissionVersion } from '../../lib/types/workflow';
import { canUploadSubmission, formatStatus, statusBadgeClass } from '../../lib/workflowUi';

const MAX_UPLOAD_BYTES = 25 * 1024 * 1024;

export default function StudentCaseSubmissionPage() {
  const { caseId } = useParams();
  const [detail, setDetail] = useState<CaseDetailPayload | null>(null);
  const [versions, setVersions] = useState<SubmissionVersion[]>([]);
  const [file, setFile] = useState<File | null>(null);
  const [meta, setMeta] = useState<SubmissionMetaPayload>({
    metadataTitle: '',
    metadataAuthors: '',
    metadataKeywords: '',
    metadataFaculty: '',
    metadataYear: undefined,
    abstractText: '',
  });
  const [faculties, setFaculties] = useState<Faculty[]>([]);
  const [facultyLoadError, setFacultyLoadError] = useState(false);
  const [loading, setLoading] = useState(true);
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');

  const load = async () => {
    if (!caseId) return;
    setLoading(true);
    setError('');
    try {
      const [caseDetail, submissionList] = await Promise.all([
        studentApi.caseDetail(Number(caseId)),
        studentApi.listSubmissions(Number(caseId)),
      ]);
      setDetail(caseDetail);
      setVersions(submissionList);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to load submission data.');
      setDetail(null);
      setVersions([]);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void load();
  }, [caseId]);

  useEffect(() => {
    const loadFaculties = async () => {
      try {
        const data = await masterApi.listFaculties();
        setFaculties(data);
      } catch {
        setFacultyLoadError(true);
      }
    };
    void loadFaculties();
  }, []);

  const uploadAllowed = useMemo(
    () => (detail ? canUploadSubmission(detail.case.status) : false),
    [detail]
  );
  const useFacultySelect = faculties.length > 0 && !facultyLoadError;

  const validateFile = (candidate: File | null): string => {
    if (!candidate) {
      return 'Please choose a file before uploading.';
    }
    const lowerName = candidate.name.toLowerCase();
    if (!lowerName.endsWith('.pdf')) {
      return 'Only PDF files are accepted.';
    }
    if (candidate.size > MAX_UPLOAD_BYTES) {
      return 'File exceeds 25 MB maximum upload size.';
    }
    return '';
  };

  const onUpload = async () => {
    if (!caseId) return;
    const validationError = validateFile(file);
    if (validationError) {
      setError(validationError);
      return;
    }
    if (!uploadAllowed) {
      setError('Upload is currently gated by workflow status.');
      return;
    }

    setUploading(true);
    setError('');
    setMessage('');
    try {
      await studentApi.uploadSubmission(Number(caseId), file as File, meta);
      setMessage('Submission uploaded successfully.');
      setFile(null);
      await load();
    } catch (err) {
      if (err instanceof ApiError && (err.status === 400 || err.status === 409)) {
        setError(err.message || 'Upload is only allowed after library approval or when revisions are requested.');
      } else {
        setError(err instanceof Error ? err.message : 'Upload failed.');
      }
    } finally {
      setUploading(false);
    }
  };

  return (
    <ShellLayout
      title={`Case #${caseId} Submissions`}
      subtitle="Upload first version or revisions with metadata"
    >
      {loading && <div className="alert alert-info">Loading submission page...</div>}
      {error && <div className="alert alert-danger">{error}</div>}
      {message && <div className="alert alert-success">{message}</div>}

      {detail && (
        <div className="card shadow-sm mb-3">
          <div className="card-body d-flex flex-wrap justify-content-between align-items-center gap-2">
            <div>
              <div className="fw-semibold">Current Status</div>
              <span className={`badge status-badge ${statusBadgeClass(detail.case.status)}`}>{formatStatus(detail.case.status)}</span>
            </div>
            {!uploadAllowed && (
              <div className="text-muted small">Upload disabled: waiting registration verification or revision stage.</div>
            )}
          </div>
        </div>
      )}

      <div className="card shadow-sm mb-3">
        <div className="card-body">
          <h3 className="h6 mb-3">Upload Submission</h3>
          <div className="row g-3">
            <div className="col-md-6">
              <label className="form-label">Document file</label>
              <input
                className="form-control"
                type="file"
                accept="application/pdf,.pdf"
                onChange={(event) => {
                  setFile(event.target.files?.[0] ?? null);
                  setError('');
                }}
                disabled={!uploadAllowed || uploading}
              />
              <div className="form-text">Only PDF files, max 25 MB.</div>
            </div>
            <div className="col-md-6">
              <label className="form-label">Metadata Title</label>
              <input
                className="form-control"
                value={meta.metadataTitle ?? ''}
                onChange={(event) => setMeta((prev) => ({ ...prev, metadataTitle: event.target.value }))}
              />
            </div>
            <div className="col-md-6">
              <label className="form-label">Metadata Authors</label>
              <input
                className="form-control"
                value={meta.metadataAuthors ?? ''}
                onChange={(event) => setMeta((prev) => ({ ...prev, metadataAuthors: event.target.value }))}
              />
            </div>
            <div className="col-md-3">
              <label className="form-label">Metadata Year</label>
              <input
                className="form-control"
                type="number"
                min={1900}
                max={2100}
                value={meta.metadataYear ?? ''}
                onChange={(event) =>
                  setMeta((prev) => ({ ...prev, metadataYear: event.target.value ? Number(event.target.value) : undefined }))
                }
              />
            </div>
            <div className="col-md-3">
              <label className="form-label">Metadata Faculty</label>
              {useFacultySelect ? (
                <select
                  className="form-select"
                  value={meta.metadataFaculty ?? ''}
                  onChange={(event) => setMeta((prev) => ({ ...prev, metadataFaculty: event.target.value }))}
                >
                  <option value="">Select faculty</option>
                  {faculties.map((item) => (
                    <option key={item.id} value={item.name}>{item.name}</option>
                  ))}
                </select>
              ) : (
                <input
                  className="form-control"
                  value={meta.metadataFaculty ?? ''}
                  onChange={(event) => setMeta((prev) => ({ ...prev, metadataFaculty: event.target.value }))}
                />
              )}
            </div>
            <div className="col-12">
              <label className="form-label">Keywords</label>
              <input
                className="form-control"
                value={meta.metadataKeywords ?? ''}
                onChange={(event) => setMeta((prev) => ({ ...prev, metadataKeywords: event.target.value }))}
              />
            </div>
            <div className="col-12">
              <label className="form-label">Abstract</label>
              <textarea
                className="form-control"
                rows={4}
                value={meta.abstractText ?? ''}
                onChange={(event) => setMeta((prev) => ({ ...prev, abstractText: event.target.value }))}
              />
            </div>
            <div className="col-12">
              <button className="btn btn-primary" onClick={() => void onUpload()} disabled={!uploadAllowed || uploading}>
                {uploading ? 'Uploading...' : 'Upload Version'}
              </button>
            </div>
          </div>
        </div>
      </div>

      <div className="card shadow-sm">
        <div className="card-body">
          <h3 className="h6 mb-3">Submission Versions</h3>
          <div className="table-responsive">
            <table className="table table-sm align-middle">
              <thead>
                <tr>
                  <th>Version</th>
                  <th>File</th>
                  <th>Status</th>
                  <th>Created</th>
                </tr>
              </thead>
              <tbody>
                {versions.map((version) => (
                  <tr key={version.id}>
                    <td>v{version.versionNumber}</td>
                    <td>{version.originalFilename}</td>
                    <td>{version.status}</td>
                    <td>{version.createdAt ? new Date(version.createdAt).toLocaleString() : 'N/A'}</td>
                  </tr>
                ))}
                {versions.length === 0 && (
                  <tr>
                    <td colSpan={4} className="text-muted">
                      No versions uploaded yet.
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        </div>
      </div>
    </ShellLayout>
  );
}
