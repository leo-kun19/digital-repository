import type {
  CaseDetailPayload,
  CaseSummary,
  ChecklistResult,
  PublicationType,
  SubmissionVersion,
} from '../types/workflow';
import { getJson, postJson, putJson, request } from './http';

export interface CreateRegistrationPayload {
  type: PublicationType;
  title: string;
  year?: number;
  articlePublishIn?: string;
  faculty?: string;
  studentIdNumber?: string;
  authorName?: string;

  // preferred (single supervisor)
  supervisorEmail?: string;
  supervisorUserId?: number;

  // legacy (keep temporarily)
  supervisorUserIds?: number[];    // deprecated
  supervisorEmails?: string[];     // deprecated
}

export interface UpdateRegistrationPayload {
  title: string;
  year?: number;
  articlePublishIn?: string;
  faculty?: string;
  studentIdNumber?: string;
  authorName?: string;
}

export interface SubmissionMetaPayload {
  metadataTitle?: string;
  metadataAuthors?: string;
  metadataKeywords?: string;
  metadataFaculty?: string;
  metadataYear?: number;
  abstractText?: string;
}

export interface SupervisorRow {
  id: number;
  email: string;
  name: string;
  faculty?: string | null;
  department?: string | null;
}

export const studentApi = {
  listCases(): Promise<CaseSummary[]> {
    return getJson('/api/student/cases');
  },

  listSupervisors(): Promise<SupervisorRow[]> {
    return getJson('/api/student/supervisors');
  },

  createRegistration(payload: CreateRegistrationPayload): Promise<{ caseId: number; status: string }> {
    return postJson('/api/student/registrations', payload);
  },

  updateRegistration(caseId: number, payload: UpdateRegistrationPayload): Promise<{ caseId: number; status: string }> {
    return putJson(`/api/student/registrations/${caseId}`, payload);
  },

  submitRegistration(caseId: number, permissionAccepted: boolean): Promise<{ caseId: number; status: string }> {
    return postJson(`/api/student/registrations/${caseId}/submit`, { permissionAccepted });
  },

  caseDetail(caseId: number): Promise<CaseDetailPayload> {
    return getJson(`/api/student/cases/${caseId}`);
  },

  listSubmissions(caseId: number): Promise<SubmissionVersion[]> {
    return getJson(`/api/student/cases/${caseId}/submissions`);
  },

  listChecklistResults(caseId: number): Promise<ChecklistResult[]> {
    return getJson(`/api/student/cases/${caseId}/checklist-results`);
  },

  submitClearance(caseId: number, note: string): Promise<{ caseId: number; status: string }> {
    return postJson(`/api/student/cases/${caseId}/clearance`, { note });
  },

  uploadSubmission(
    caseId: number,
    file: File,
    metadata: SubmissionMetaPayload
  ): Promise<{ submissionId: number; version: number }> {
    const form = new FormData();
    form.append('file', file);
    form.append('meta', new Blob([JSON.stringify(metadata)], { type: 'application/json' }));
    return request(`/api/student/cases/${caseId}/submissions`, {
      method: 'POST',
      body: form,
    });
  },
};
