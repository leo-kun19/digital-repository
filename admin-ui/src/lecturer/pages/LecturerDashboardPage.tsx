import { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import ShellLayout from '../../layout/ShellLayout';
import { lecturerApi } from '../../lib/api/lecturer';

export default function LecturerDashboardPage() {
  const navigate = useNavigate();
  const [approvals, setApprovals] = useState(0);
  const [pendingSupervisor, setPendingSupervisor] = useState(0);
  const [libraryTracking, setLibraryTracking] = useState(0);
  const [students, setStudents] = useState(0);
  const [year, setYear] = useState<number>(new Date().getFullYear());
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    const load = async () => {
      setLoading(true);
      try {
        const [approvalRows, supervisorGroups, libraryGroups, studentGroups] = await Promise.all([
          lecturerApi.approvalQueue(),
          lecturerApi.pendingSupervisor(year),
          lecturerApi.libraryTracking(year),
          lecturerApi.myStudents(year),
        ]);
        setApprovals(approvalRows.length);
        setPendingSupervisor(supervisorGroups.reduce((total, group) => total + group.cases.length, 0));
        setLibraryTracking(libraryGroups.reduce((total, group) => total + group.cases.length, 0));
        setStudents(studentGroups.length);
      } catch (err) {
        setError(err instanceof Error ? err.message : 'Failed to load lecturer dashboard.');
      } finally {
        setLoading(false);
      }
    };

    void load();
  }, [year]);

  const yearOptions = useMemo(() => {
    const current = new Date().getFullYear();
    return [current - 1, current, current + 1];
  }, []);

  const cards = [
    { label: 'Pending Approvals', value: approvals, icon: '✅', color: '#e0f2fe', path: '/lecturer/approvals', desc: 'Registration requests awaiting your approval' },
    { label: 'Submission Review', value: pendingSupervisor, icon: '📝', color: '#fff3cd', path: '/lecturer/review', desc: 'Student submissions needing your review' },
    { label: 'Library Tracking', value: libraryTracking, icon: '🏛️', color: '#ede9fe', path: '/lecturer/library', desc: 'Cases forwarded to library for processing' },
    { label: 'My Students', value: students, icon: '🎓', color: '#d1e7dd', path: '/lecturer/students', desc: 'Students under your supervision' },
  ];

  return (
    <ShellLayout title="Lecturer Dashboard" subtitle="Monitor approval and supervisor review workload">
      {error && <div className="alert alert-danger">{error}</div>}

      <div className="d-flex flex-wrap align-items-center gap-2 mb-4">
        <label className="form-label mb-0 fw-semibold small">📅 Academic Year:</label>
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

      <div className="row g-3 mb-4">
        {cards.map((card, index) => (
          <div className="col-md-6 col-xl-3" key={card.path}>
            <div
              className="su-stat-card su-card-clickable fade-in"
              role="button"
              onClick={() => navigate(card.path)}
              style={{ animationDelay: `${index * 0.08}s` }}
            >
              <div className="su-stat-icon" style={{ background: card.color }}>{card.icon}</div>
              <div className="su-stat-value">{loading ? '—' : card.value}</div>
              <div className="su-stat-label">{card.label}</div>
              <div className="text-muted small mt-2" style={{ fontSize: '0.75rem' }}>{card.desc}</div>
            </div>
          </div>
        ))}
      </div>
    </ShellLayout>
  );
}
