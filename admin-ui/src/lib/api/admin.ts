import type {
  AdminPublishDetail,
  AdminPublishQueueItem,
  AdminRegistrationApproval,
  AdminStudentReviewGroup,
  CaseDetailPayload,
  CaseStatus,
  CaseSummary,
  ChecklistTemplateResponse,
  PublicationType,
} from '../types/workflow';
import { getJson, postJson, putJson, request } from './http';

export interface AdminChecklistEntry {
  checklistItemId: number;
  pass: boolean;
  note?: string;
}

export interface ReplaceTemplateItem {
  section?: string;
  itemText: string;
  guidanceText?: string;
  required: boolean;
}

export interface ChecklistImportSummary {
  type: PublicationType;
  newTemplateId: number;
  newVersion: number;
  itemsImported: number;
  sections: string[];
}

export const adminApi = {
  reviewQueue(): Promise<CaseSummary[]> {
    return getJson('/api/admin/review');
  },

  registrationApprovals(): Promise<AdminRegistrationApproval[]> {
    return getJson('/api/admin/registration-approvals');
  },

  approveRegistration(caseId: number): Promise<{ caseId: number; status: CaseStatus }> {
    return postJson(`/api/admin/registration-approvals/${caseId}/approve`);
  },

  rejectRegistration(caseId: number, reason: string): Promise<{ caseId: number; status: CaseStatus }> {
    return postJson(`/api/admin/registration-approvals/${caseId}/reject`, { reason });
  },

  reviewQueueGrouped(): Promise<AdminStudentReviewGroup[]> {
    return getJson('/api/admin/review-queue-grouped');
  },

  caseDetail(caseId: number): Promise<CaseDetailPayload> {
    return getJson(`/api/admin/cases/${caseId}`);
  },

  saveChecklistResults(
    caseId: number,
    submissionVersionId: number,
    results: AdminChecklistEntry[]
  ): Promise<{ ok: boolean }> {
    return postJson(`/api/admin/cases/${caseId}/checklist-results`, {
      submissionVersionId,
      results,
    });
  },

  requestRevision(caseId: number, reason: string): Promise<{ caseId: number; status: CaseStatus }> {
    return postJson(`/api/admin/cases/${caseId}/request-revision`, { reason });
  },

  approveCase(caseId: number): Promise<{ caseId: number; status: CaseStatus }> {
    return postJson(`/api/admin/cases/${caseId}/approve`);
  },

  rejectCase(caseId: number, reason: string): Promise<{ caseId: number; status: CaseStatus }> {
    return postJson(`/api/admin/cases/${caseId}/reject`, { reason });
  },

  clearanceQueue(): Promise<CaseSummary[]> {
    return getJson('/api/admin/clearance');
  },

  approveClearance(caseId: number): Promise<{ caseId: number; status: CaseStatus }> {
    return postJson(`/api/admin/clearance/${caseId}/approve`);
  },

  requestClearanceCorrection(caseId: number, reason: string): Promise<{ caseId: number; status: CaseStatus }> {
    return postJson(`/api/admin/clearance/${caseId}/request-correction`, { reason });
  },

  publishQueue(): Promise<AdminPublishQueueItem[]> {
    return getJson('/api/admin/publish');
  },

  publishDetail(caseId: number): Promise<AdminPublishDetail> {
    return getJson(`/api/admin/publish/${caseId}`);
  },

  publish(caseId: number): Promise<{ publishedId: number; status: CaseStatus }> {
    return postJson(`/api/admin/publish/${caseId}`);
  },

  unpublish(caseId: number, reason: string): Promise<{ caseId: number; status: CaseStatus }> {
    return postJson(`/api/admin/publish/${caseId}/unpublish`, { reason });
  },

  checklists(type: PublicationType): Promise<ChecklistTemplateResponse[]> {
    return getJson(`/api/admin/checklists/full?type=${type}`);
  },

  newChecklistVersion(type: PublicationType): Promise<{ templateId: number; version: number }> {
    return postJson(`/api/admin/checklists/${type}/new-version`);
  },

  replaceTemplateItems(templateId: number, items: ReplaceTemplateItem[]): Promise<{ ok: boolean }> {
    return putJson(`/api/admin/checklists/templates/${templateId}/items`, items);
  },

  activateTemplate(templateId: number): Promise<{ templateId: number; active: boolean }> {
    return postJson(`/api/admin/checklists/templates/${templateId}/activate`);
  },

  importChecklistXlsx(
    type: PublicationType,
    file: File,
    activate: boolean,
    sheetName?: string
  ): Promise<ChecklistImportSummary> {
    const form = new FormData();
    form.append('file', file);
    const query = new URLSearchParams();
    query.set('activate', String(activate));
    if (sheetName && sheetName.trim().length > 0) {
      query.set('sheetName', sheetName.trim());
    }
    return request(`/api/admin/checklists/${type}/import-xlsx?${query.toString()}`, {
      method: 'POST',
      body: form,
    });
  },
};
