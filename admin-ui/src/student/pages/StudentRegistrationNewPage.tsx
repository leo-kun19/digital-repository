import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import ShellLayout from '../../layout/ShellLayout';
import { studentApi, type SupervisorRow } from '../../lib/api/student';
import { masterApi, type Faculty } from '../../lib/api/master';
import type { PublicationType } from '../../lib/types/workflow';
import { useAuth } from '../../lib/context/AuthContext';

type FormErrors = {
  title?: string;
  publicationType?: string;
  year?: string;
  faculty?: string;
  authorName?: string;
  studentId?: string;
  supervisorIds?: string;
  agreement1?: string;
  agreement2?: string;
};

function supervisorLabel(supervisor: SupervisorRow): string {
  const details = [supervisor.faculty, supervisor.department].filter((part) => part && part.trim().length > 0).join(' - ');
  return details ? `${supervisor.name} (${details})` : supervisor.name;
}

export default function StudentRegistrationNewPage() {
  const navigate = useNavigate();
  const { user } = useAuth();

  const [title, setTitle] = useState('');
  const [year, setYear] = useState<number | undefined>(new Date().getFullYear());
  const [type, setType] = useState<PublicationType>('THESIS');
  const [faculty, setFaculty] = useState(user?.faculty ?? '');
  const [faculties, setFaculties] = useState<Faculty[]>([]);
  const [facultyLoadError, setFacultyLoadError] = useState(false);
  const [articlePublishIn, setArticlePublishIn] = useState('');
  const [authorName, setAuthorName] = useState(user?.fullName ?? '');
  const [studentIdNumber, setStudentIdNumber] = useState(user?.studentId ?? '');
  const [supervisors, setSupervisors] = useState<SupervisorRow[]>([]);
  const [selectedSupervisorEmail, setSelectedSupervisorEmail] = useState('');
  const [permissionChecklistOneAccepted, setPermissionChecklistOneAccepted] = useState(false);
  const [permissionChecklistTwoAccepted, setPermissionChecklistTwoAccepted] = useState(false);
  const [saving, setSaving] = useState(false);
  const [loadingSupervisors, setLoadingSupervisors] = useState(true);
  const [errors, setErrors] = useState<FormErrors>({});
  const [serverError, setServerError] = useState('');

  const clearFieldError = (field: keyof FormErrors) => {
    setErrors((prev) => ({ ...prev, [field]: undefined }));
  };

  const scrollToFirstError = (nextErrors: FormErrors) => {
    const fieldOrder: Array<keyof FormErrors> = [
      'title',
      'publicationType',
      'year',
      'faculty',
      'authorName',
      'studentId',
      'supervisorIds',
      'agreement1',
      'agreement2',
    ];
    const firstField = fieldOrder.find((field) => nextErrors[field]);
    if (!firstField) {
      return;
    }

    const elementId =
      firstField === 'agreement1' || firstField === 'agreement2'
        ? 'registration-agreements'
        : firstField === 'supervisorIds'
          ? 'registration-supervisors'
          : `registration-${firstField}`;
    const target = document.getElementById(elementId);
    target?.scrollIntoView({ behavior: 'smooth', block: 'center' });
    if (target instanceof HTMLInputElement || target instanceof HTMLSelectElement || target instanceof HTMLTextAreaElement) {
      target.focus();
    }
  };

  const validateForDraft = (): FormErrors => {
    const nextErrors: FormErrors = {};
    if (!title.trim()) nextErrors.title = 'Title is required to save draft.';
    if (!selectedSupervisorEmail.trim()) nextErrors.supervisorIds = 'Please select a supervisor.';
    return nextErrors;
  };

  const validateForSubmit = (): FormErrors => {
    const nextErrors: FormErrors = {};
    if (!title.trim()) nextErrors.title = 'Title is required.';
    if (!type) nextErrors.publicationType = 'Publication type is required.';
    if (year === undefined || Number.isNaN(year) || !/^\d{4}$/.test(String(year))) {
      nextErrors.year = 'Year is required and must be 4 digits.';
    }
    if (!faculty.trim()) nextErrors.faculty = 'Faculty is required.';
    if (!authorName.trim()) nextErrors.authorName = 'Author name is required.';
    if (!studentIdNumber.trim()) nextErrors.studentId = 'Student ID number is required.';
    if (!selectedSupervisorEmail.trim()) nextErrors.supervisorIds = 'Please select a supervisor.';
    if (!permissionChecklistOneAccepted) nextErrors.agreement1 = 'Please accept agreement checklist 1.';
    if (!permissionChecklistTwoAccepted) nextErrors.agreement2 = 'Please accept agreement checklist 2.';
    return nextErrors;
  };

  useEffect(() => {
    const loadSupervisors = async () => {
      setLoadingSupervisors(true);
      try {
        const rows = await studentApi.listSupervisors();
        setSupervisors(rows);
      } catch (err) {
        setServerError(err instanceof Error ? err.message : 'Failed to load supervisors.');
      } finally {
        setLoadingSupervisors(false);
      }
    };
    void loadSupervisors();
  }, []);

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

  const useFacultySelect = faculties.length > 0 && !facultyLoadError;

  const submit = async (submitForApproval: boolean) => {
    setServerError('');
    const nextErrors = submitForApproval ? validateForSubmit() : validateForDraft();
    if (Object.values(nextErrors).some(Boolean)) {
      setErrors(nextErrors);
      scrollToFirstError(nextErrors);
      return;
    }
    setErrors({});

    setSaving(true);
    try {
      const createResponse = await studentApi.createRegistration({
        title,
        type,
        year,
        faculty,
        articlePublishIn: articlePublishIn || undefined,
        authorName: authorName || undefined,
        studentIdNumber: studentIdNumber || undefined,
        supervisorEmail: selectedSupervisorEmail || undefined,
      });

      if (submitForApproval) {
        await studentApi.submitRegistration(createResponse.caseId, true);
      }

      navigate(`/student/cases/${createResponse.caseId}`);
    } catch (err) {
      setServerError(err instanceof Error ? err.message : 'Failed to create registration.');
    } finally {
      setSaving(false);
    }
  };

  return (
    <ShellLayout title="New Publication Registration" subtitle="Create draft then submit when agreement is accepted">
      <div className="su-card fade-in">
        <div className="card-body p-4">
          <form
            className="row g-3"
            onSubmit={(event) => {
              event.preventDefault();
              void submit(false);
            }}
          >
            <div className="col-12">
              <label className="form-label" htmlFor="registration-title">Title</label>
              <input
                id="registration-title"
                className={`form-control${errors.title ? ' is-invalid' : ''}`}
                value={title}
                onChange={(event) => {
                  setTitle(event.target.value);
                  clearFieldError('title');
                }}
              />
              {errors.title && <div className="text-danger small mt-1">{errors.title}</div>}
            </div>

            <div className="col-md-4">
              <label className="form-label" htmlFor="registration-publicationType">Publication Type</label>
              <select
                id="registration-publicationType"
                className={`form-select${errors.publicationType ? ' is-invalid' : ''}`}
                value={type}
                onChange={(event) => {
                  setType(event.target.value as PublicationType);
                  clearFieldError('publicationType');
                }}
              >
                <option value="THESIS">THESIS</option>
                <option value="ARTICLE">ARTICLE</option>
                <option value="INTERNSHIP_REPORT" disabled>INTERNSHIP_REPORT (Not enabled yet)</option>
                <option value="OTHER" disabled>OTHER (Not enabled yet)</option>
              </select>
              <div className="form-text">Only THESIS and ARTICLE are currently enabled.</div>
              {errors.publicationType && <div className="text-danger small mt-1">{errors.publicationType}</div>}
            </div>

            <div className="col-md-4">
              <label className="form-label" htmlFor="registration-year">Year</label>
              <input
                id="registration-year"
                className={`form-control${errors.year ? ' is-invalid' : ''}`}
                type="number"
                min={1900}
                max={2100}
                value={year ?? ''}
                onChange={(event) => {
                  setYear(event.target.value ? Number(event.target.value) : undefined);
                  clearFieldError('year');
                }}
              />
              {errors.year && <div className="text-danger small mt-1">{errors.year}</div>}
            </div>

            <div className="col-md-4">
              <label className="form-label" htmlFor="registration-faculty">Faculty</label>
              {useFacultySelect ? (
                <select
                  id="registration-faculty"
                  className={`form-select${errors.faculty ? ' is-invalid' : ''}`}
                  value={faculty}
                  onChange={(event) => {
                    setFaculty(event.target.value);
                    clearFieldError('faculty');
                  }}
                >
                  <option value="">Select faculty</option>
                  {faculties.map((item) => (
                    <option key={item.id} value={item.name}>{item.name}</option>
                  ))}
                </select>
              ) : (
                <input
                  id="registration-faculty"
                  className={`form-control${errors.faculty ? ' is-invalid' : ''}`}
                  value={faculty}
                  onChange={(event) => {
                    setFaculty(event.target.value);
                    clearFieldError('faculty');
                  }}
                />
              )}
              {errors.faculty && <div className="text-danger small mt-1">{errors.faculty}</div>}
            </div>

            <div className="col-md-6">
              <label className="form-label" htmlFor="registration-authorName">Author Name</label>
              <input
                id="registration-authorName"
                className={`form-control${errors.authorName ? ' is-invalid' : ''}`}
                value={authorName}
                onChange={(event) => {
                  setAuthorName(event.target.value);
                  clearFieldError('authorName');
                }}
              />
              {errors.authorName && <div className="text-danger small mt-1">{errors.authorName}</div>}
            </div>

            <div className="col-md-6">
              <label className="form-label" htmlFor="registration-studentId">Student ID Number</label>
              <input
                id="registration-studentId"
                className={`form-control${errors.studentId ? ' is-invalid' : ''}`}
                value={studentIdNumber}
                onChange={(event) => {
                  setStudentIdNumber(event.target.value);
                  clearFieldError('studentId');
                }}
              />
              {errors.studentId && <div className="text-danger small mt-1">{errors.studentId}</div>}
            </div>

            <div className="col-12">
              <label className="form-label">Article Publish In</label>
              <input
                className="form-control"
                value={articlePublishIn}
                onChange={(event) => setArticlePublishIn(event.target.value)}
                placeholder="Journal or conference name"
              />
            </div>

            <div className="col-12" id="registration-supervisors">
              <label className="form-label">Supervisors</label>
              <select
                className={`form-select${errors.supervisorIds ? ' is-invalid' : ''}`}
                value={selectedSupervisorEmail}
                onChange={(event) => {
                  setSelectedSupervisorEmail(event.target.value);
                  clearFieldError('supervisorIds');
                }}
                disabled={loadingSupervisors}
              >
                <option value="">{loadingSupervisors ? 'Loading supervisors...' : 'Select supervisor'}</option>
                {supervisors.map((supervisor) => (
                  <option key={supervisor.id} value={supervisor.email}>
                    {`${supervisorLabel(supervisor)} (${supervisor.email})`}
                  </option>
                ))}
              </select>
              {!loadingSupervisors && supervisors.length === 0 && (
                <div className="text-muted small mt-1">No supervisors available for your study program.</div>
              )}
              <div className="form-text">Supervisor options are filtered by your study program.</div>
              {errors.supervisorIds && <div className="text-danger small mt-1">{errors.supervisorIds}</div>}
            </div>

            <div className="col-12" id="registration-agreements">
              <div className="form-check mb-2">
                <input
                  className={`form-check-input${errors.agreement1 ? ' is-invalid' : ''}`}
                  id="permissionChecklistOneAccepted"
                  type="checkbox"
                  checked={permissionChecklistOneAccepted}
                  onChange={(event) => {
                    setPermissionChecklistOneAccepted(event.target.checked);
                    clearFieldError('agreement1');
                  }}
                />
                <label className="form-check-label" htmlFor="permissionChecklistOneAccepted">
                  I hereby grant to Sampoerna University (SU) the nonexclusive right to create a digital version of the above-named publication and to make my publication available as part of library electronic local content collections. I understand that the full text of my publication will be available to the SU Library members, in digital form without restriction as part of the collection, and I give my permission for the SU Library to reproduce, distribute, display, and transmit my publication in order to make it available online to support education and research activities.
                </label>
              </div>
              <div className="form-check">
                <input
                  className={`form-check-input${errors.agreement2 ? ' is-invalid' : ''}`}
                  id="permissionChecklistTwoAccepted"
                  type="checkbox"
                  checked={permissionChecklistTwoAccepted}
                  onChange={(event) => {
                    setPermissionChecklistTwoAccepted(event.target.checked);
                    clearFieldError('agreement2');
                  }}
                />
                <label className="form-check-label" htmlFor="permissionChecklistTwoAccepted">
                  I understand that this permission constitutes a non-exclusive, perpetual, royalty-free license, and that I retain all other rights to the copyright in my publication, including the right to use it in other works such as articles and books.
                </label>
              </div>
              {errors.agreement1 && <div className="text-danger small mt-1">{errors.agreement1}</div>}
              {errors.agreement2 && <div className="text-danger small mt-1">{errors.agreement2}</div>}
            </div>

            {serverError && (
              <div className="col-12">
                <div className="alert alert-danger d-flex align-items-center gap-2 mb-0" style={{ borderRadius: '0.75rem' }}><span>⚠️</span> {serverError}</div>
              </div>
            )}

            <div className="col-12 d-flex flex-wrap gap-2">
              <button className="btn btn-outline-primary" type="submit" disabled={saving || loadingSupervisors} style={{ borderRadius: '999px', padding: '0.5rem 1.5rem' }}>
                {saving ? '⏳ Saving...' : '💾 Save Draft'}
              </button>
              <button
                className="btn btn-primary"
                type="button"
                disabled={saving || loadingSupervisors}
                onClick={() => void submit(true)}
                style={{ borderRadius: '999px', padding: '0.5rem 1.5rem' }}
              >
                {saving ? '⏳ Submitting...' : '📨 Save and Submit for Approval'}
              </button>
            </div>
          </form>
        </div>
      </div>
    </ShellLayout>
  );
}
