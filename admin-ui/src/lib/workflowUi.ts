import type { CaseStatus } from './types/workflow';

const STATUS_LABELS: Partial<Record<CaseStatus, string>> = {
  REGISTRATION_APPROVED: 'Registration approved (Supervisor)',
  REGISTRATION_VERIFIED: 'Registration verified',
};

export function formatStatus(status: CaseStatus): string {
  return STATUS_LABELS[status]
    ?? status.replace(/_/g, ' ').toLowerCase().replace(/(^|\s)\S/g, (s) => s.toUpperCase());
}

export function statusBadgeClass(status: CaseStatus): string {
  switch (status) {
    case 'PUBLISHED':
    case 'READY_TO_PUBLISH':
    case 'CLEARANCE_APPROVED':
    case 'APPROVED_FOR_CLEARANCE':
    case 'REGISTRATION_VERIFIED':
      return 'bg-success-subtle text-success-emphasis';
    case 'REJECTED':
    case 'NEEDS_REVISION_LIBRARY':
    case 'NEEDS_REVISION_SUPERVISOR':
      return 'bg-danger-subtle text-danger-emphasis';
    case 'REGISTRATION_PENDING':
    case 'REGISTRATION_APPROVED':
    case 'UNDER_SUPERVISOR_REVIEW':
    case 'UNDER_LIBRARY_REVIEW':
    case 'CLEARANCE_SUBMITTED':
    case 'READY_TO_FORWARD':
    case 'FORWARDED_TO_LIBRARY':
      return 'bg-warning-subtle text-warning-emphasis';
    default:
      return 'bg-secondary-subtle text-secondary-emphasis';
  }
}

export function canSubmitRegistration(status: CaseStatus): boolean {
  return status === 'REGISTRATION_DRAFT' || status === 'REJECTED';
}

export function canUploadSubmission(status: CaseStatus): boolean {
  return (
    status === 'REGISTRATION_VERIFIED' ||
    status === 'NEEDS_REVISION_SUPERVISOR' ||
    status === 'NEEDS_REVISION_LIBRARY'
  );
}

export function canSubmitClearance(status: CaseStatus): boolean {
  return status === 'APPROVED_FOR_CLEARANCE';
}

export function isAdminReviewStage(status: CaseStatus): boolean {
  return (
    status === 'FORWARDED_TO_LIBRARY' ||
    status === 'UNDER_LIBRARY_REVIEW' ||
    status === 'NEEDS_REVISION_LIBRARY'
  );
}

export function isFinalizedForLibrary(status: CaseStatus): boolean {
  return (
    status === 'APPROVED_FOR_CLEARANCE' ||
    status === 'CLEARANCE_SUBMITTED' ||
    status === 'CLEARANCE_APPROVED' ||
    status === 'READY_TO_PUBLISH' ||
    status === 'PUBLISHED' ||
    status === 'REJECTED'
  );
}

export function canAdminSaveChecklist(status: CaseStatus): boolean {
  return isAdminReviewStage(status);
}

export function canAdminDecide(status: CaseStatus): boolean {
  return isAdminReviewStage(status);
}

export const workflowStageOrder: CaseStatus[] = [
  'REGISTRATION_DRAFT',
  'REGISTRATION_PENDING',
  'REGISTRATION_APPROVED',
  'REGISTRATION_VERIFIED',
  'UNDER_SUPERVISOR_REVIEW',
  'NEEDS_REVISION_SUPERVISOR',
  'READY_TO_FORWARD',
  'FORWARDED_TO_LIBRARY',
  'UNDER_LIBRARY_REVIEW',
  'NEEDS_REVISION_LIBRARY',
  'APPROVED_FOR_CLEARANCE',
  'CLEARANCE_SUBMITTED',
  'CLEARANCE_APPROVED',
  'READY_TO_PUBLISH',
  'PUBLISHED',
];

export type WorkflowStageKey =
  | 'registration'
  | 'supervisor'
  | 'library'
  | 'clearance'
  | 'publish'
  | 'rejected';

export function getStageKey(status: CaseStatus): WorkflowStageKey {
  switch (status) {
    case 'REGISTRATION_DRAFT':
    case 'REGISTRATION_PENDING':
    case 'REGISTRATION_APPROVED':
    case 'REGISTRATION_VERIFIED':
      return 'registration';
    case 'UNDER_SUPERVISOR_REVIEW':
    case 'NEEDS_REVISION_SUPERVISOR':
    case 'READY_TO_FORWARD':
      return 'supervisor';
    case 'FORWARDED_TO_LIBRARY':
    case 'UNDER_LIBRARY_REVIEW':
    case 'NEEDS_REVISION_LIBRARY':
    case 'APPROVED_FOR_CLEARANCE':
      return 'library';
    case 'CLEARANCE_SUBMITTED':
    case 'CLEARANCE_APPROVED':
      return 'clearance';
    case 'READY_TO_PUBLISH':
    case 'PUBLISHED':
      return 'publish';
    case 'REJECTED':
      return 'rejected';
  }
}

export function getStageIndex(status: CaseStatus): number {
  const stage = getStageKey(status);
  switch (stage) {
    case 'registration':
      return 0;
    case 'supervisor':
      return 1;
    case 'library':
      return 2;
    case 'clearance':
      return 3;
    case 'publish':
      return 4;
    case 'rejected':
      return -1;
  }
}

export function formatStageName(stage: WorkflowStageKey): string {
  switch (stage) {
    case 'registration':
      return 'Registration';
    case 'supervisor':
      return 'Supervisor Review';
    case 'library':
      return 'Library Review';
    case 'clearance':
      return 'Clearance';
    case 'publish':
      return 'Publish';
    case 'rejected':
      return 'Rejected';
  }
}
