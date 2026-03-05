import { useEffect, useMemo, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { publicRepositoryApi, type RepositoryItemSummary, type RepositorySearchParams } from '../../lib/api/publicRepository';
import { masterApi, type Faculty, type Program } from '../../lib/api/master';
import ThemeSwitch from '../../components/ThemeSwitch';
import { useAuth } from '../../lib/context/AuthContext';
import { useTheme } from '../../theme/ThemeContext';

const INITIAL_FILTERS: RepositorySearchParams = {
  title: '',
  author: '',
  faculty: '',
  program: '',
  keyword: '',
};

export default function RepositorySearchPage() {
  const { user, logout } = useAuth();
  const { theme, setTheme } = useTheme();
  const navigate = useNavigate();

  const [filters, setFilters] = useState<RepositorySearchParams>(INITIAL_FILTERS);
  const [results, setResults] = useState<RepositoryItemSummary[]>([]);
  const [faculties, setFaculties] = useState<Faculty[]>([]);
  const [programs, setPrograms] = useState<Program[]>([]);
  const [selectedFacultyId, setSelectedFacultyId] = useState<number | undefined>(undefined);
  const [selectedProgramId, setSelectedProgramId] = useState<number | undefined>(undefined);
  const [masterLoadError, setMasterLoadError] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const yearOptions = useMemo(() => {
    const currentYear = new Date().getFullYear();
    const values: number[] = [];
    for (let year = currentYear; year >= 2014; year -= 1) {
      values.push(year);
    }
    return values;
  }, []);

  const activeFilterCount = useMemo(
    () =>
      Object.values(filters).filter((value) => value !== undefined && value !== null && String(value).trim() !== '').length,
    [filters]
  );

  const load = async (params: RepositorySearchParams) => {
    setLoading(true);
    setError('');
    try {
      const response = await publicRepositoryApi.search(params);
      setResults(response.results);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to load repository data.');
      setResults([]);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void load(filters);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    const loadFaculties = async () => {
      try {
        setMasterLoadError('');
        const data = await masterApi.listFaculties();
        setFaculties(data);
      } catch {
        setMasterLoadError('Failed to load faculty/program filters.');
      }
    };
    void loadFaculties();
  }, []);

  useEffect(() => {
    if (!selectedFacultyId) {
      setPrograms([]);
      return;
    }

    const loadPrograms = async () => {
      try {
        setMasterLoadError('');
        const data = await masterApi.listPrograms(selectedFacultyId);
        setPrograms(data);
      } catch {
        setPrograms([]);
        setMasterLoadError('Failed to load study programs for selected faculty.');
      }
    };
    void loadPrograms();
  }, [selectedFacultyId]);

  const onChange = (key: keyof RepositorySearchParams, value: string) => {
    setFilters((prev) => ({
      ...prev,
      [key]: key === 'year' ? (value ? Number(value) : undefined) : value,
    }));
  };

  const onFacultyChange = (value: string) => {
    if (!value) {
      setSelectedFacultyId(undefined);
      setSelectedProgramId(undefined);
      setPrograms([]);
      setFilters((prev) => ({ ...prev, faculty: '', program: '' }));
      return;
    }

    const facultyId = Number(value);
    const faculty = faculties.find((item) => item.id === facultyId);
    setSelectedFacultyId(facultyId);
    setSelectedProgramId(undefined);
    setFilters((prev) => ({
      ...prev,
      faculty: faculty?.name ?? '',
      program: '',
    }));
  };

  const onProgramChange = (value: string) => {
    if (!value) {
      setSelectedProgramId(undefined);
      setFilters((prev) => ({ ...prev, program: '' }));
      return;
    }

    const programId = Number(value);
    const program = programs.find((item) => item.id === programId);
    setSelectedProgramId(programId);
    setFilters((prev) => ({ ...prev, program: program?.name ?? '' }));
  };

  const onSearch = async (event: React.FormEvent) => {
    event.preventDefault();
    await load(filters);
  };

  const onReset = async () => {
    setFilters(INITIAL_FILTERS);
    setSelectedFacultyId(undefined);
    setSelectedProgramId(undefined);
    setPrograms([]);
    await load(INITIAL_FILTERS);
  };

  return (
    <div className="min-vh-100" style={{ background: 'linear-gradient(180deg, #f0f6fa 0%, #fff 30%)' }}>
      {/* ===== HEADER ===== */}
      <header className="su-app-header">
        <div className="container py-3 d-flex flex-wrap justify-content-between align-items-center gap-2">
          <div className="d-flex align-items-center gap-2">
            <div className="su-logo-circle">SU</div>
            <div>
              <h1 className="h5 mb-0 text-white fw-bold">Sampoerna University</h1>
              <small className="text-white-50">Digital Repository Portal</small>
            </div>
          </div>
          <div className="d-flex flex-wrap align-items-center gap-3">
            <div className="d-flex align-items-center gap-2 text-white">
              <span className="small text-white-50">Dark mode</span>
              <ThemeSwitch
                checked={theme === 'dark'}
                onChange={(checked) => setTheme(checked ? 'dark' : 'light')}
              />
            </div>
            <div className="d-flex flex-wrap gap-2">
              {user ? (
                <>
                  <button
                    type="button"
                    className="btn btn-outline-light btn-sm"
                    style={{ borderRadius: '999px' }}
                    onClick={() => navigate(
                      user.role === 'STUDENT'
                        ? '/student/dashboard'
                        : user.role === 'LECTURER'
                          ? '/lecturer/dashboard'
                          : '/admin/dashboard'
                    )}
                  >
                    📊 Dashboard
                  </button>
                  <button type="button" className="btn btn-light btn-sm" style={{ borderRadius: '999px' }} onClick={() => void logout()}>
                    Logout
                  </button>
                </>
              ) : (
                <>
                  <button type="button" className="btn btn-outline-light btn-sm" style={{ borderRadius: '999px' }} onClick={() => navigate('/login')}>
                    Sign In
                  </button>
                  <button type="button" className="btn btn-light btn-sm" style={{ borderRadius: '999px', fontWeight: 600 }} onClick={() => navigate('/register')}>
                    Register
                  </button>
                </>
              )}
            </div>
          </div>
        </div>
      </header>

      <div className="container py-4 fade-in">
        {/* ===== HERO SECTION ===== */}
        <section className="su-hero mb-4">
          <div className="row align-items-center">
            <div className="col-lg-7">
              <h2 className="display-6 mb-2">Discover Research &<br />Publications</h2>
              <p className="mb-0" style={{ fontSize: '1.05rem' }}>
                Browse theses, articles, and scholarly works from Sampoerna University's academic community.
                Access knowledge curated by our university library.
              </p>
            </div>
            <div className="col-lg-5 text-center mt-3 mt-lg-0">
              <div className="d-flex justify-content-center gap-3">
                <div className="text-center">
                  <div style={{ fontSize: '2.5rem', fontWeight: 800 }}>{results.length}</div>
                  <div style={{ fontSize: '0.78rem', opacity: 0.7, textTransform: 'uppercase', letterSpacing: '0.05em' }}>Publications</div>
                </div>
                <div style={{ width: 1, background: 'rgba(255,255,255,0.2)', margin: '0.5rem 0' }} />
                <div className="text-center">
                  <div style={{ fontSize: '2.5rem', fontWeight: 800 }}>{faculties.length}</div>
                  <div style={{ fontSize: '0.78rem', opacity: 0.7, textTransform: 'uppercase', letterSpacing: '0.05em' }}>Faculties</div>
                </div>
              </div>
            </div>
          </div>
        </section>

        {/* ===== SEARCH FILTERS ===== */}
        <div className="su-card mb-4">
          <div className="card-body p-4">
            <h3 className="h6 su-page-title mb-3">🔍 Search Repository</h3>
            <form className="row g-3" onSubmit={onSearch}>
              <div className="col-md-6">
                <label className="form-label">Title</label>
                <input
                  className="form-control"
                  value={filters.title ?? ''}
                  onChange={(event) => onChange('title', event.target.value)}
                  placeholder="Search by publication title..."
                />
              </div>
              <div className="col-md-6">
                <label className="form-label">Author</label>
                <input
                  className="form-control"
                  value={filters.author ?? ''}
                  onChange={(event) => onChange('author', event.target.value)}
                  placeholder="Search by author name..."
                />
              </div>

              <div className="col-md-3">
                <label className="form-label">Faculty</label>
                <select
                  className="form-select"
                  value={selectedFacultyId ?? ''}
                  onChange={(event) => onFacultyChange(event.target.value)}
                >
                  <option value="">Any faculty</option>
                  {faculties.map((faculty) => (
                    <option key={faculty.id} value={faculty.id}>{faculty.name}</option>
                  ))}
                </select>
              </div>

              <div className="col-md-3">
                <label className="form-label">Study Program</label>
                <select
                  className="form-select"
                  value={selectedProgramId ?? ''}
                  onChange={(event) => onProgramChange(event.target.value)}
                  disabled={!selectedFacultyId}
                >
                  <option value="">Any study program</option>
                  {programs.map((program) => (
                    <option key={program.id} value={program.id}>{program.name}</option>
                  ))}
                </select>
              </div>

              <div className="col-md-3">
                <label className="form-label">Keyword</label>
                <input
                  className="form-control"
                  value={filters.keyword ?? ''}
                  onChange={(event) => onChange('keyword', event.target.value)}
                  placeholder="Specific keyword"
                />
              </div>

              <div className="col-md-3">
                <label className="form-label">Year Published</label>
                <select
                  className="form-select"
                  value={filters.year ?? ''}
                  onChange={(event) => onChange('year', event.target.value)}
                >
                  <option value="">Any year</option>
                  {yearOptions.map((year) => (
                    <option key={year} value={year}>{year}</option>
                  ))}
                </select>
              </div>

              <div className="col-12 d-flex flex-wrap gap-2 align-items-center">
                <button className="btn btn-primary" type="submit" disabled={loading}>
                  {loading ? (
                    <><span className="su-spinner d-inline-block me-2" style={{ width: '1rem', height: '1rem', borderWidth: 2 }} /> Searching...</>
                  ) : '🔎 Search Repository'}
                </button>
                <button className="btn btn-outline-secondary" type="button" onClick={() => void onReset()} disabled={loading}>
                  Reset Filters
                </button>
                {activeFilterCount > 0 && (
                  <span className="badge bg-primary-subtle text-primary-emphasis" style={{ borderRadius: '999px' }}>
                    {activeFilterCount} active filter{activeFilterCount > 1 ? 's' : ''}
                  </span>
                )}
              </div>
            </form>
          </div>
        </div>

        {error && (
          <div className="alert alert-danger" role="alert">{error}</div>
        )}
        {masterLoadError && (
          <div className="alert alert-warning" role="alert">{masterLoadError}</div>
        )}

        {/* ===== RESULTS ===== */}
        <div className="d-flex justify-content-between align-items-center mb-3">
          <h3 className="h5 mb-0 su-page-title">📚 Search Results</h3>
          <span className="badge bg-secondary-subtle text-secondary-emphasis" style={{ borderRadius: '999px', fontSize: '0.8rem' }}>
            {results.length} item{results.length !== 1 ? 's' : ''}
          </span>
        </div>

        <div className="vstack gap-3">
          {results.map((item, index) => (
            <div className="su-result-card fade-in" key={item.id} style={{ animationDelay: `${index * 0.04}s` }}>
              <div className="d-flex justify-content-between align-items-start gap-3">
                <div style={{ flex: 1 }}>
                  <h4 className="h6 mb-1 su-page-title" style={{ fontSize: '1rem' }}>{item.title}</h4>
                  <div className="d-flex flex-wrap gap-2 mb-2">
                    <span className="badge bg-primary-subtle text-primary-emphasis" style={{ borderRadius: '999px', fontSize: '0.72rem' }}>
                      👤 {item.authors || item.authorName || 'Unknown author'}
                    </span>
                    <span className="badge bg-secondary-subtle text-secondary-emphasis" style={{ borderRadius: '999px', fontSize: '0.72rem' }}>
                      🏛️ {item.faculty || 'Unknown faculty'}
                    </span>
                    {item.program && (
                      <span className="badge bg-secondary-subtle text-secondary-emphasis" style={{ borderRadius: '999px', fontSize: '0.72rem' }}>
                        📖 {item.program}
                      </span>
                    )}
                    {item.year && (
                      <span className="badge bg-secondary-subtle text-secondary-emphasis" style={{ borderRadius: '999px', fontSize: '0.72rem' }}>
                        📅 {item.year}
                      </span>
                    )}
                  </div>
                  {item.keywords && (
                    <p className="mb-0 small text-muted">
                      <strong>Keywords:</strong> {item.keywords}
                    </p>
                  )}
                </div>
                <Link to={`/repo/${item.id}`} className="btn btn-primary btn-sm" style={{ borderRadius: '999px', whiteSpace: 'nowrap' }}>
                  View Detail →
                </Link>
              </div>
            </div>
          ))}
          {!loading && results.length === 0 && (
            <div className="su-empty-state">
              <div className="su-empty-icon">🔍</div>
              <h5>No Publications Found</h5>
              <p className="mb-0">Try adjusting your filters or search terms to discover more publications.</p>
            </div>
          )}
        </div>

        <footer className="text-center text-muted small py-4 mt-4">
          <div className="fw-semibold">Sampoerna University Library</div>
          <div>© {new Date().getFullYear()} — Digital Repository Portal</div>
        </footer>
      </div>
    </div>
  );
}
