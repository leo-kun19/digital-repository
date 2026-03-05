import type { ChecklistTemplateResponse, PublicationType } from '../types/workflow';
import { deleteJson, getJson, postJson, putJson } from './http';

export interface ChecklistTemplateSummary {
  id: number;
  publicationType: PublicationType;
  version: number;
  active: boolean;
  createdAt?: string;
  itemCount: number;
}

export interface ChecklistEditorItem {
  orderIndex?: number;
  section?: string;
  itemText: string;
  guidanceText?: string;
  isRequired: boolean;
}

export const checklistApi = {
  listTemplates(type: PublicationType): Promise<ChecklistTemplateSummary[]> {
    return getJson(`/api/admin/checklists?type=${type}`);
  },

  createEmpty(type: PublicationType): Promise<ChecklistTemplateSummary> {
    return postJson(`/api/admin/checklists/${type}/create-empty`);
  },

  newDraft(type: PublicationType): Promise<ChecklistTemplateSummary> {
    return postJson(`/api/admin/checklists/${type}/new-draft`);
  },

  newVersion(type: PublicationType): Promise<{ templateId: number; version: number }> {
    return postJson(`/api/admin/checklists/${type}/new-version`);
  },

  getTemplate(templateId: number): Promise<ChecklistTemplateResponse> {
    return getJson(`/api/admin/checklists/templates/${templateId}`);
  },

  saveItems(templateId: number, items: ChecklistEditorItem[]): Promise<{ ok: boolean }> {
    return putJson(`/api/admin/checklists/templates/${templateId}/items`, items);
  },

  activate(templateId: number): Promise<{ templateId: number; active: boolean }> {
    return postJson(`/api/admin/checklists/templates/${templateId}/activate`);
  },

  deleteTemplate(templateId: number): Promise<{ deleted: boolean; templateId: number }> {
    return deleteJson(`/api/admin/checklists/templates/${templateId}`);
  },
};
