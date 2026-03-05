export type UserRole = 'STUDENT' | 'LECTURER' | 'ADMIN';

export type PublicationType = 'THESIS' | 'ARTICLE' | 'INTERNSHIP_REPORT' | 'OTHER';

export type CaseStatus =
  | 'REGISTRATION_DRAFT'
  | 'REGISTRATION_PENDING'
  | 'REGISTRATION_APPROVED'
  | 'REGISTRATION_VERIFIED'
  | 'UNDER_SUPERVISOR_REVIEW'
  | 'NEEDS_REVISION_SUPERVISOR'
  | 'READY_TO_FORWARD'
  | 'FORWARDED_TO_LIBRARY'
  | 'UNDER_LIBRARY_REVIEW'
  | 'NEEDS_REVISION_LIBRARY'
  | 'APPROVED_FOR_CLEARANCE'
  | 'CLEARANCE_SUBMITTED'
  | 'CLEARANCE_APPROVED'
  | 'READY_TO_PUBLISH'
  | 'PUBLISHED'
  | 'REJECTED';

export type SubmissionStatus =
  | 'SUBMITTED'
  | 'UNDER_REVIEW'
  | 'NEEDS_REVISION'
  | 'APPROVED'
  | 'REJECTED';

export interface CaseSummary {
  id: number;
  type: PublicationType;
  status: CaseStatus;
  title?: string | null;
  createdAt?: string;
  updatedAt?: string;
}

export interface PublicationRegistration {
  id: number;
  title: string;
  year?: number | null;
  articlePublishIn?: string | null;
  faculty?: string | null;
  studentIdNumber?: string | null;
  authorName?: string | null;
  permissionAcceptedAt?: string | null;
  submittedAt?: string | null;
  supervisorDecisionAt?: string | null;
  supervisorDecisionNote?: string | null;
}

export interface SubmissionVersion {
  id: number;
  versionNumber: number;
  originalFilename: string;
  contentType: string;
  fileSize: number;
  metadataTitle?: string | null;
  metadataAuthors?: string | null;
  metadataKeywords?: string | null;
  metadataFaculty?: string | null;
  metadataYear?: number | null;
  abstractText?: string | null;
  status: SubmissionStatus;
  createdAt?: string;
  checklistTemplate?: {
    id: number;
    publicationType: PublicationType;
    version: number;
    active: boolean;
  } | null;
}

export interface WorkflowComment {
  id: number;
  authorRole: UserRole;
  authorEmail?: string | null;
  body: string;
  createdAt?: string;
}

export interface TimelineItem {
  at?: string | null;
  actorRole?: UserRole | null;
  actorEmail?: string | null;
  type: string;
  message?: string | null;
  relatedSubmissionVersionId?: number | null;
}

export interface ChecklistResult {
  id: number;
  checklistItem: {
    id: number;
    section?: string | null;
    itemText: string;
  };
  passFail: 'PASS' | 'FAIL';
  note?: string | null;
}

export interface CaseDetailPayload {
  case: CaseSummary;
  registration?: PublicationRegistration | null;
  versions?: SubmissionVersion[];
  submissions?: SubmissionVersion[];
  comments?: WorkflowComment[];
  checklistResults?: ChecklistResult[];
  clearance?: {
    id: number;
    status: 'DRAFT' | 'SUBMITTED' | 'APPROVED' | 'NEEDS_CORRECTION';
    note?: string | null;
    submittedAt?: string | null;
    approvedAt?: string | null;
  } | null;
  timeline?: TimelineItem[];
}

export interface ChecklistTemplate {
  id: number;
  publicationType: PublicationType;
  version: number;
  active: boolean;
  createdAt?: string;
}

export interface ChecklistItem {
  id: number;
  orderIndex: number;
  section?: string | null;
  itemText: string;
  guidanceText?: string | null;
  required: boolean;
}

export interface ChecklistTemplateResponse {
  template: ChecklistTemplate;
  items: ChecklistItem[];
}

export interface AdminCaseQueueItem {
  caseId: number;
  title?: string | null;
  type: PublicationType;
  status: CaseStatus;
  updatedAt?: string;
  latestSubmissionAt?: string | null;
}

export interface AdminStudentReviewGroup {
  studentUserId: number;
  studentName: string;
  studentIdNumber?: string | null;
  faculty?: string | null;
  program?: string | null;
  cases: AdminCaseQueueItem[];
}

export interface AdminPublishQueueItem {
  caseId: number;
  title?: string | null;
  type: PublicationType;
  status: CaseStatus;
  updatedAt?: string;
}

export interface AdminRegistrationApproval {
  caseId: number;
  title?: string | null;
  type: PublicationType;
  status: CaseStatus;
  updatedAt?: string;
  submittedAt?: string | null;
  studentUserId: number;
  studentName: string;
  studentIdNumber?: string | null;
  faculty?: string | null;
  program?: string | null;
  studentEmail?: string | null;
}

export interface AdminPublishDetail {
  caseId: number;
  title?: string | null;
  type: PublicationType;
  status: CaseStatus;
  updatedAt?: string;
  metadata?: {
    title?: string | null;
    authors?: string | null;
    keywords?: string | null;
    faculty?: string | null;
    year?: number | null;
    abstractText?: string | null;
  };
  latestSubmission?: {
    id: number;
    originalFilename?: string | null;
    createdAt?: string | null;
    fileSize?: number | null;
    downloadUrl?: string | null;
  } | null;
}
