import { Navigate, Route, Routes } from 'react-router-dom';
import { useAuth } from '../lib/context/AuthContext';
import LoginPage from '../auth/LoginPage';
import RegisterPage from '../auth/RegisterPage';
import VerifyEmailPage from '../auth/VerifyEmailPage';
import OnboardingPage from '../auth/OnboardingPage';
import RepositorySearchPage from '../repository/pages/RepositorySearch';
import RepositoryDetail from '../repository/pages/RepositoryDetail';
import { ProtectedRoute } from './ProtectedRoute';
import { RoleGuard } from './RoleGuard';
import StudentDashboardPage from '../student/pages/StudentDashboardPage';
import StudentRegistrationsPage from '../student/pages/StudentRegistrationsPage';
import StudentRegistrationNewPage from '../student/pages/StudentRegistrationNewPage';
import StudentSubmissionsPage from '../student/pages/StudentSubmissionsPage';
import StudentCaseDetailPage from '../student/pages/StudentCaseDetailPage';
import StudentCaseSubmissionPage from '../student/pages/StudentCaseSubmissionPage';
import StudentClearanceDetailPage from '../student/pages/StudentClearanceDetailPage';
import LecturerDashboardPage from '../lecturer/pages/LecturerDashboardPage';
import LecturerApprovalsPage from '../lecturer/pages/LecturerApprovalsPage';
import LecturerReviewPage from '../lecturer/pages/LecturerReviewPage';
import LecturerStudentsPage from '../lecturer/pages/LecturerStudentsPage';
import LecturerLibraryTrackingPage from '../lecturer/pages/LecturerLibraryTrackingPage';
import LecturerStudentDetailPage from '../lecturer/pages/LecturerStudentDetailPage';
import AdminDashboardPage from '../admin/pages/AdminDashboardPage';
import AdminRegistrationApprovalsPage from '../admin/pages/AdminRegistrationApprovalsPage';
import AdminReviewPage from '../admin/pages/AdminReviewPage';
import AdminReviewDetailPage from '../admin/pages/AdminReviewDetailPage';
import AdminReviewStudentPage from '../admin/pages/AdminReviewStudentPage';
import AdminClearancePage from '../admin/pages/AdminClearancePage';
import AdminPublishPage from '../admin/pages/AdminPublishPage';
import AdminPublishDetailPage from '../admin/pages/AdminPublishDetailPage';
import AdminChecklistPage from '../admin/pages/AdminChecklistPage';

const ROUTE_PATHS = {
  LECTURER_REVIEW: '/lecturer/review',
  LECTURER_STUDENT_DETAIL: '/lecturer/students/:studentId',
} as const;

// Lightweight guardrail so critical lecturer routes stay explicit in this file.
const REQUIRED_LECTURER_ROUTES = [ROUTE_PATHS.LECTURER_REVIEW, ROUTE_PATHS.LECTURER_STUDENT_DETAIL];
if (REQUIRED_LECTURER_ROUTES.some((path) => !path.startsWith('/lecturer/'))) {
  throw new Error('Lecturer route guard misconfigured');
}

export function AppRoutes() {
  const { user, loading } = useAuth();

  return (
    <Routes>
      <Route path="/" element={<RepositorySearchPage />} />
      <Route path="/repo/:id" element={<RepositoryDetail />} />
      <Route path="/login" element={<LoginPage />} />
      <Route path="/register" element={<RegisterPage />} />
      <Route path="/verify-email" element={<ProtectedRoute user={user} loading={loading}><VerifyEmailPage /></ProtectedRoute>} />
      <Route path="/onboarding" element={<ProtectedRoute user={user} loading={loading}><OnboardingPage /></ProtectedRoute>} />

      <Route path="/student/dashboard" element={<ProtectedRoute user={user} loading={loading}><RoleGuard user={user} allowedRoles={['STUDENT']}><StudentDashboardPage /></RoleGuard></ProtectedRoute>} />
      <Route path="/student/registrations" element={<ProtectedRoute user={user} loading={loading}><RoleGuard user={user} allowedRoles={['STUDENT']}><StudentRegistrationsPage /></RoleGuard></ProtectedRoute>} />
      <Route path="/student/registrations/new" element={<ProtectedRoute user={user} loading={loading}><RoleGuard user={user} allowedRoles={['STUDENT']}><StudentRegistrationNewPage /></RoleGuard></ProtectedRoute>} />
      <Route path="/student/submissions" element={<ProtectedRoute user={user} loading={loading}><RoleGuard user={user} allowedRoles={['STUDENT']}><StudentSubmissionsPage /></RoleGuard></ProtectedRoute>} />
      <Route path="/student/cases/:caseId" element={<ProtectedRoute user={user} loading={loading}><RoleGuard user={user} allowedRoles={['STUDENT']}><StudentCaseDetailPage /></RoleGuard></ProtectedRoute>} />
      <Route path="/student/cases/:caseId/submission" element={<ProtectedRoute user={user} loading={loading}><RoleGuard user={user} allowedRoles={['STUDENT']}><StudentCaseSubmissionPage /></RoleGuard></ProtectedRoute>} />
      <Route path="/student/clearance/:caseId" element={<ProtectedRoute user={user} loading={loading}><RoleGuard user={user} allowedRoles={['STUDENT']}><StudentClearanceDetailPage /></RoleGuard></ProtectedRoute>} />

      <Route path="/lecturer/dashboard" element={<ProtectedRoute user={user} loading={loading}><RoleGuard user={user} allowedRoles={['LECTURER']}><LecturerDashboardPage /></RoleGuard></ProtectedRoute>} />
      <Route path="/lecturer/approvals" element={<ProtectedRoute user={user} loading={loading}><RoleGuard user={user} allowedRoles={['LECTURER']}><LecturerApprovalsPage /></RoleGuard></ProtectedRoute>} />
      <Route path={ROUTE_PATHS.LECTURER_REVIEW} element={<ProtectedRoute user={user} loading={loading}><RoleGuard user={user} allowedRoles={['LECTURER']}><LecturerReviewPage /></RoleGuard></ProtectedRoute>} />
      <Route path="/lecturer/library" element={<ProtectedRoute user={user} loading={loading}><RoleGuard user={user} allowedRoles={['LECTURER']}><LecturerLibraryTrackingPage /></RoleGuard></ProtectedRoute>} />
      <Route path="/lecturer/students" element={<ProtectedRoute user={user} loading={loading}><RoleGuard user={user} allowedRoles={['LECTURER']}><LecturerStudentsPage /></RoleGuard></ProtectedRoute>} />
      <Route path={ROUTE_PATHS.LECTURER_STUDENT_DETAIL} element={<ProtectedRoute user={user} loading={loading}><RoleGuard user={user} allowedRoles={['LECTURER']}><LecturerStudentDetailPage /></RoleGuard></ProtectedRoute>} />

      <Route path="/admin/dashboard" element={<ProtectedRoute user={user} loading={loading}><RoleGuard user={user} allowedRoles={['ADMIN']}><AdminDashboardPage /></RoleGuard></ProtectedRoute>} />
      <Route path="/admin" element={<ProtectedRoute user={user} loading={loading}><RoleGuard user={user} allowedRoles={['ADMIN']}><Navigate to="/admin/dashboard" replace /></RoleGuard></ProtectedRoute>} />
      <Route path="/admin/registration-approvals" element={<ProtectedRoute user={user} loading={loading}><RoleGuard user={user} allowedRoles={['ADMIN']}><AdminRegistrationApprovalsPage /></RoleGuard></ProtectedRoute>} />
      <Route path="/admin/review" element={<ProtectedRoute user={user} loading={loading}><RoleGuard user={user} allowedRoles={['ADMIN']}><AdminReviewPage /></RoleGuard></ProtectedRoute>} />
      <Route path="/admin/review/students/:studentUserId" element={<ProtectedRoute user={user} loading={loading}><RoleGuard user={user} allowedRoles={['ADMIN']}><AdminReviewStudentPage /></RoleGuard></ProtectedRoute>} />
      <Route path="/admin/review/:caseId" element={<ProtectedRoute user={user} loading={loading}><RoleGuard user={user} allowedRoles={['ADMIN']}><AdminReviewDetailPage /></RoleGuard></ProtectedRoute>} />
      <Route path="/admin/clearance" element={<ProtectedRoute user={user} loading={loading}><RoleGuard user={user} allowedRoles={['ADMIN']}><AdminClearancePage /></RoleGuard></ProtectedRoute>} />
      <Route path="/admin/publish" element={<ProtectedRoute user={user} loading={loading}><RoleGuard user={user} allowedRoles={['ADMIN']}><AdminPublishPage /></RoleGuard></ProtectedRoute>} />
      <Route path="/admin/publish/:caseId" element={<ProtectedRoute user={user} loading={loading}><RoleGuard user={user} allowedRoles={['ADMIN']}><AdminPublishDetailPage /></RoleGuard></ProtectedRoute>} />
      <Route path="/admin/checklists" element={<ProtectedRoute user={user} loading={loading}><RoleGuard user={user} allowedRoles={['ADMIN']}><AdminChecklistPage /></RoleGuard></ProtectedRoute>} />

      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
