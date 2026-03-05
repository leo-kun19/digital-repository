import { FormEvent, useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { authApi } from '../lib/api/auth';
import { defaultPath } from '../lib/authUi';
import { masterApi, type Faculty, type Program } from '../lib/api/master';
import { useAuth } from '../lib/context/AuthContext';

type FormErrors = {
  name?: string;
  faculty?: string;
  studyProgram?: string;
  studentId?: string;
};

export default function OnboardingPage() {
  const { user, refetch } = useAuth();
  const navigate = useNavigate();
  const [checking, setChecking] = useState(true);

  const [name, setName] = useState(user?.name ?? user?.fullName ?? '');
  const [facultyId, setFacultyId] = useState<number | ''>('');
  const [studyProgram, setStudyProgram] = useState(
    user?.role === 'STUDENT' ? (user?.program ?? '') : (user?.department ?? '')
  );
  const [studentId, setStudentId] = useState(user?.studentId ?? '');

  const [faculties, setFaculties] = useState<Faculty[]>([]);
  const [programs, setPrograms] = useState<Program[]>([]);
  const [loadingPrograms, setLoadingPrograms] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');
  const [errors, setErrors] = useState<FormErrors>({});

  const isStudent = user?.role === 'STUDENT';

  useEffect(() => {
    const loadMe = async () => {
      try {
        const me = await authApi.me();
        if (me.profileComplete) {
          navigate(defaultPath(me.role), { replace: true });
          return;
        }
        setName(me.name ?? me.fullName ?? '');
        setStudyProgram(me.role === 'STUDENT' ? (me.program ?? '') : (me.department ?? ''));
        setStudentId(me.studentId ?? '');
      } catch (err) {
        setError(err instanceof Error ? err.message : 'Failed to load current user profile.');
      } finally {
        setChecking(false);
      }
    };
    void loadMe();
  }, [navigate]);

  useEffect(() => {
    const loadFaculties = async () => {
      try {
        const data = await masterApi.listFaculties();
        setFaculties(data);
      } catch (err) {
        setError(err instanceof Error ? err.message : 'Failed to load faculties.');
      }
    };
    void loadFaculties();
  }, []);

  useEffect(() => {
    if (faculties.length === 0 || !user?.faculty || facultyId !== '') {
      return;
    }
    const match = faculties.find((item) => item.name === user.faculty);
    if (match) {
      setFacultyId(match.id);
    }
  }, [faculties, facultyId, user?.faculty]);

  useEffect(() => {
    if (!facultyId) {
      setPrograms([]);
      return;
    }

    const loadPrograms = async () => {
      setLoadingPrograms(true);
      try {
        const data = await masterApi.listPrograms(Number(facultyId));
        setPrograms(data);
      } catch (err) {
        setError(err instanceof Error ? err.message : 'Failed to load study programs.');
      } finally {
        setLoadingPrograms(false);
      }
    };

    void loadPrograms();
  }, [facultyId]);

  const selectedFaculty = useMemo(
    () => faculties.find((item) => item.id === Number(facultyId)),
    [faculties, facultyId]
  );

  const validate = (): FormErrors => {
    const next: FormErrors = {};
    if (!name.trim()) {
      next.name = 'Full name is required.';
    }
    if (!selectedFaculty) {
      next.faculty = 'Faculty is required.';
    }
    if (!studyProgram.trim()) {
      next.studyProgram = 'Study Program is required.';
    }
    if (isStudent && !studentId.trim()) {
      next.studentId = 'Student ID is required.';
    }
    return next;
  };

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    setError('');

    const nextErrors = validate();
    if (Object.values(nextErrors).some(Boolean)) {
      setErrors(nextErrors);
      return;
    }
    setErrors({});

    if (!selectedFaculty || !user) {
      setError('Faculty is invalid. Please select again.');
      return;
    }

    setSubmitting(true);
    try {
      await authApi.onboarding({
        name: name.trim(),
        faculty: selectedFaculty.name,
        studyProgram: studyProgram.trim(),
        studentId: isStudent ? studentId.trim() : undefined,
      });

      const updated = await refetch();
      if (updated?.profileComplete) {
        navigate(defaultPath(updated.role), { replace: true });
      } else {
        setError('Profile is still incomplete. Please check all required fields.');
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to save onboarding data.');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="auth-shell">
      <form className="auth-card su-card" onSubmit={submit}>
        {checking && <div className="alert alert-info py-2">Checking profile...</div>}
        <div className="mb-3">
          <h1 className="h5 su-page-title mb-1">Complete your profile</h1>
          <p className="text-muted small mb-0">
            Please complete the required information before accessing the portal.
          </p>
        </div>

        <div className="mb-3">
          <label className="form-label">Full name</label>
          <input
            className={`form-control${errors.name ? ' is-invalid' : ''}`}
            value={name}
            onChange={(e) => {
              setName(e.target.value);
              setErrors((prev) => ({ ...prev, name: undefined }));
            }}
            required
          />
          {errors.name && <div className="invalid-feedback">{errors.name}</div>}
        </div>

        <div className="mb-3">
          <label className="form-label">Faculty</label>
          <select
            className={`form-select${errors.faculty ? ' is-invalid' : ''}`}
            value={facultyId}
            onChange={(e) => {
              const nextValue = e.target.value;
              setFacultyId(nextValue ? Number(nextValue) : '');
              setStudyProgram('');
              setErrors((prev) => ({ ...prev, faculty: undefined, studyProgram: undefined }));
            }}
            required
          >
            <option value="">Select faculty</option>
            {faculties.map((faculty) => (
              <option key={faculty.id} value={faculty.id}>{faculty.name}</option>
            ))}
          </select>
          {errors.faculty && <div className="invalid-feedback">{errors.faculty}</div>}
        </div>

        <div className="mb-3">
          <label className="form-label">Study Program</label>
          <select
            className={`form-select${errors.studyProgram ? ' is-invalid' : ''}`}
            value={studyProgram}
            onChange={(e) => {
              setStudyProgram(e.target.value);
              setErrors((prev) => ({ ...prev, studyProgram: undefined }));
            }}
            disabled={!facultyId || loadingPrograms}
            required
          >
            <option value="">Select study program</option>
            {programs.map((program) => (
              <option key={program.id} value={program.name}>{program.name}</option>
            ))}
          </select>
          {errors.studyProgram && <div className="invalid-feedback">{errors.studyProgram}</div>}
        </div>

        {isStudent && (
          <div className="mb-3">
            <label className="form-label">Student ID</label>
            <input
              className={`form-control${errors.studentId ? ' is-invalid' : ''}`}
              value={studentId}
              onChange={(e) => {
                setStudentId(e.target.value);
                setErrors((prev) => ({ ...prev, studentId: undefined }));
              }}
              required
            />
            {errors.studentId && <div className="invalid-feedback">{errors.studentId}</div>}
          </div>
        )}

        {error && <div className="alert alert-danger py-2">{error}</div>}

        <div className="d-grid">
          <button className="btn btn-primary" type="submit" disabled={submitting || checking}>
            {submitting ? 'Saving...' : 'Complete profile'}
          </button>
        </div>
      </form>
    </div>
  );
}
