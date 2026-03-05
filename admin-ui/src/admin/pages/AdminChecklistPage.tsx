import { useEffect, useMemo, useRef, useState } from 'react';
import ShellLayout from '../../layout/ShellLayout';
import { checklistApi, type ChecklistEditorItem, type ChecklistTemplateSummary } from '../../lib/api/checklist';
import type { ChecklistTemplateResponse, PublicationType } from '../../lib/types/workflow';

type ItemDraft = {
  id: string;
  title: string;
  guidanceText: string;
  isRequired: boolean;
  errorTitle?: string;
};

type CategoryDraft = {
  id: string;
  title: string;
  items: ItemDraft[];
  errorCategoryTitle?: string;
  errorAddItem?: string;
};

type TemplateMap = Record<PublicationType, ChecklistTemplateSummary[]>;

const TYPES: PublicationType[] = ['THESIS', 'ARTICLE'];

function emptyTemplates(): TemplateMap {
  return { THESIS: [], ARTICLE: [] };
}

function newCategory(partial?: Partial<CategoryDraft>): CategoryDraft {
  return {
    id: crypto.randomUUID(),
    title: partial?.title ?? '',
    items: partial?.items ?? [],
    errorAddItem: partial?.errorAddItem,
    errorCategoryTitle: partial?.errorCategoryTitle,
  };
}

function newItem(partial?: Partial<ItemDraft>): ItemDraft {
  return {
    id: crypto.randomUUID(),
    title: partial?.title ?? '',
    guidanceText: partial?.guidanceText ?? '',
    isRequired: partial?.isRequired ?? true,
    errorTitle: partial?.errorTitle,
  };
}

export default function AdminChecklistPage() {
  const [templatesByType, setTemplatesByType] = useState<TemplateMap>(emptyTemplates());
  const [selectedTemplateId, setSelectedTemplateId] = useState<number | null>(null);
  const [selectedTemplate, setSelectedTemplate] = useState<ChecklistTemplateResponse | null>(null);
  const [categories, setCategories] = useState<CategoryDraft[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [isMutating, setIsMutating] = useState(false);
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');
  const editorRef = useRef<HTMLDivElement | null>(null);

  const activeByType = useMemo(() => ({
    THESIS: templatesByType.THESIS.find((template) => template.active) ?? null,
    ARTICLE: templatesByType.ARTICLE.find((template) => template.active) ?? null,
  }), [templatesByType]);

  const loadTemplates = async () => {
    setIsLoading(true);
    setError('');
    try {
      const [thesis, article] = await Promise.all([
        checklistApi.listTemplates('THESIS'),
        checklistApi.listTemplates('ARTICLE'),
      ]);
      setTemplatesByType({ THESIS: thesis, ARTICLE: article });
    } catch (err) {
      setTemplatesByType(emptyTemplates());
      setError(err instanceof Error ? err.message : 'Failed to load checklist templates.');
    } finally {
      setIsLoading(false);
    }
  };

  const loadTemplateDetail = async (templateId: number | null) => {
    if (!templateId) {
      setSelectedTemplateId(null);
      setSelectedTemplate(null);
      setCategories([]);
      return;
    }

    setIsLoading(true);
    setSelectedTemplateId(templateId);
    try {
      const detail = await checklistApi.getTemplate(templateId);
      setSelectedTemplate(detail);

      const grouped = new Map<string, CategoryDraft>();
      detail.items
        .slice()
        .sort((a, b) => a.orderIndex - b.orderIndex)
        .forEach((item) => {
          const section = (item.section?.trim() || 'General');
          if (!grouped.has(section)) {
            grouped.set(section, newCategory({ title: section }));
          }
          grouped.get(section)?.items.push(newItem({
            title: item.itemText,
            guidanceText: item.guidanceText ?? '',
            isRequired: item.required,
          }));
        });

      setCategories(Array.from(grouped.values()));
    } catch (err) {
      setSelectedTemplate(null);
      setCategories([]);
      setError(err instanceof Error ? err.message : 'Failed to load template detail.');
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    void loadTemplates();
  }, []);

  const focusEditor = () => {
    if (!editorRef.current) return;
    editorRef.current.scrollIntoView({ behavior: 'smooth', block: 'start' });
    const firstInput = editorRef.current.querySelector('input');
    if (firstInput instanceof HTMLInputElement) {
      firstInput.focus();
    }
  };

  const runMutation = async (action: () => Promise<void>, successMessage: string): Promise<boolean> => {
    setIsMutating(true);
    setError('');
    setMessage('');
    try {
      await action();
      setMessage(successMessage);
      return true;
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Checklist action failed.');
      return false;
    } finally {
      setIsMutating(false);
    }
  };

  const createTemplate = (type: PublicationType) =>
    runMutation(async () => {
      const created = await checklistApi.newDraft(type);
      await loadTemplates();
      await loadTemplateDetail(created.id);
      focusEditor();
    }, `${type} template draft created.`);

  const createDraftToEdit = async () => {
    if (!selectedTemplate) return;
    const type = selectedTemplate.template.publicationType;
    await runMutation(async () => {
      const created = await checklistApi.newVersion(type);
      await loadTemplates();
      await loadTemplateDetail(created.templateId);
      focusEditor();
    }, `${type} template draft created.`);
  };

  const addCategory = () => {
    setCategories((prev) => [...prev, newCategory()]);
  };

  const updateCategoryTitle = (categoryId: string, value: string) => {
    setCategories((prev) =>
      prev.map((category) => {
        if (category.id !== categoryId) return category;
        return {
          ...category,
          title: value,
          errorCategoryTitle: value.trim() ? undefined : category.errorCategoryTitle,
        };
      })
    );
  };

  const addItem = (categoryId: string) => {
    setCategories((prev) =>
      prev.map((category) => {
        if (category.id !== categoryId) return category;

        if (!category.title.trim()) {
          return {
            ...category,
            errorCategoryTitle: 'Please enter a category title first.',
          };
        }

        if (category.items.length > 0) {
          const lastIndex = category.items.length - 1;
          const lastItem = category.items[lastIndex];
          if (!lastItem.title.trim()) {
            const updatedItems = category.items.map((item, idx) =>
              idx === lastIndex ? { ...item, errorTitle: 'Please enter an item title before adding another.' } : item
            );
            return {
              ...category,
              items: updatedItems,
            };
          }
        }

        return {
          ...category,
          errorCategoryTitle: undefined,
          errorAddItem: undefined,
          items: [...category.items, newItem()],
        };
      })
    );
  };

  const updateItem = (categoryId: string, itemId: string, patch: Partial<ItemDraft>) => {
    setCategories((prev) =>
      prev.map((category) => {
        if (category.id !== categoryId) return category;
        return {
          ...category,
          items: category.items.map((item) => {
            if (item.id !== itemId) return item;
            const next = { ...item, ...patch };
            if (typeof patch.title === 'string' && patch.title.trim()) {
              next.errorTitle = undefined;
            }
            return next;
          }),
        };
      })
    );
  };

  const deleteItem = (categoryId: string, itemId: string) => {
    setCategories((prev) =>
      prev.map((category) =>
        category.id === categoryId
          ? { ...category, items: category.items.filter((item) => item.id !== itemId) }
          : category
      )
    );
  };

  const validateAndBuildPayload = (): ChecklistEditorItem[] | null => {
    let hasError = false;
    const payload: ChecklistEditorItem[] = [];
    let orderIndex = 1;

    const nextCategories = categories.map((category) => {
      const nextCategory = { ...category, errorCategoryTitle: undefined, errorAddItem: undefined };
      const title = category.title.trim();
      if (!title) {
        nextCategory.errorCategoryTitle = 'Please enter a category title first.';
        hasError = true;
      }
      if (category.items.length === 0) {
        nextCategory.errorAddItem = 'Each category must have at least 1 item.';
        hasError = true;
      }

      nextCategory.items = category.items.map((item) => {
        const nextItem = { ...item, errorTitle: undefined };
        if (!item.title.trim()) {
          nextItem.errorTitle = 'Item title is required.';
          hasError = true;
        } else if (title) {
          payload.push({
            orderIndex: orderIndex++,
            section: title,
            itemText: item.title.trim(),
            guidanceText: item.guidanceText.trim() || undefined,
            isRequired: item.isRequired,
          });
        }
        return nextItem;
      });

      return nextCategory;
    });

    setCategories(nextCategories);

    if (hasError) {
      setError('Please fix checklist validation errors before saving.');
      return null;
    }

    return payload;
  };

  const saveDraft = async (): Promise<boolean> => {
    if (!selectedTemplateId || !selectedTemplate) return false;
    if (selectedTemplate.template.active) {
      setError('Cannot edit active template; create a new draft first.');
      return false;
    }

    const payload = validateAndBuildPayload();
    if (!payload) {
      return false;
    }

    return runMutation(async () => {
      await checklistApi.saveItems(selectedTemplateId, payload);
      await loadTemplateDetail(selectedTemplateId);
      await loadTemplates();
    }, 'Draft checklist items saved.');
  };

  const activateTemplateFromTable = (templateId: number) => {
    if (!window.confirm('This becomes active for future reviews. Continue?')) {
      return;
    }
    void runMutation(async () => {
      await checklistApi.activate(templateId);
      await loadTemplates();
      if (selectedTemplateId) {
        await loadTemplateDetail(selectedTemplateId);
      }
    }, 'Template activated.');
  };

  const activateFromEditor = async () => {
    if (!selectedTemplateId || !selectedTemplate || selectedTemplate.template.active) return;
    const saved = await saveDraft();
    if (!saved) {
      return;
    }
    if (!window.confirm('This becomes active for future reviews. Continue?')) {
      return;
    }
    void runMutation(async () => {
      await checklistApi.activate(selectedTemplateId);
      await loadTemplates();
      await loadTemplateDetail(selectedTemplateId);
    }, 'Template activated.');
  };

  const deleteTemplate = async (templateId: number) => {
    const template = templatesByType.THESIS.find((t) => t.id === templateId)
      ?? templatesByType.ARTICLE.find((t) => t.id === templateId);
    const prompt = template?.active
      ? 'Delete this ACTIVE template? This cannot be undone.'
      : 'Delete this draft template version? This cannot be undone.';
    if (!window.confirm(prompt)) {
      return;
    }

    void runMutation(async () => {
      await checklistApi.deleteTemplate(templateId);
      if (selectedTemplateId === templateId) {
        await loadTemplateDetail(null);
      }
      await loadTemplates();
    }, 'Template version deleted.');
  };

  const isReadOnly = selectedTemplate?.template.active ?? false;

  return (
    <ShellLayout title="Templates" subtitle="Create draft versions, edit items safely, then activate">
      {isLoading && (
        <div className="text-center py-5">
          <div className="su-spinner mx-auto mb-3" />
          <div className="text-muted">Loading checklist templates...</div>
        </div>
      )}
      {error && <div className="alert alert-danger d-flex align-items-center gap-2" style={{ borderRadius: '0.75rem' }}><span>⚠️</span> {error}</div>}
      {message && <div className="alert alert-success d-flex align-items-center gap-2" style={{ borderRadius: '0.75rem' }}><span>✅</span> {message}</div>}

      {TYPES.map((type) => (
        <div className="su-card mb-3" key={type}>
          <div className="card-body p-4">
            <div className="d-flex flex-wrap justify-content-between align-items-center gap-2 mb-3">
              <div>
                <h3 className="h6 mb-1">{type}</h3>
                <div className="small text-muted">
                  {activeByType[type] ? `Active: V${activeByType[type]?.version}` : 'No active template'}
                </div>
              </div>
              <div className="d-flex gap-2">
                <button className="btn btn-outline-primary btn-sm" style={{ borderRadius: '999px' }} disabled={isMutating} onClick={() => void createTemplate(type)}>
                  ➕ Create Template
                </button>
              </div>
            </div>

            <div className="table-responsive">
              <table className="table table-sm align-middle">
                <thead>
                  <tr>
                    <th>Version</th>
                    <th>Status</th>
                    <th>Items</th>
                    <th>Created</th>
                    <th className="text-end">Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {templatesByType[type].map((template) => (
                    <tr key={template.id}>
                      <td>V{template.version}</td>
                      <td><span className={`badge ${template.active ? 'bg-success' : 'bg-secondary'}`} style={{ borderRadius: '999px' }}>{template.active ? '✅ ACTIVE' : 'DRAFT'}</span></td>
                      <td>{template.itemCount}</td>
                      <td>{template.createdAt ? new Date(template.createdAt).toLocaleString() : 'N/A'}</td>
                      <td className="text-end">
                        <div className="btn-group btn-group-sm">
                          <button className="btn btn-outline-primary" onClick={() => void loadTemplateDetail(template.id)}>
                            Edit Items
                          </button>
                          <button className="btn btn-danger" onClick={() => void deleteTemplate(template.id)} disabled={isMutating}>
                            Delete
                          </button>
                          {!template.active && (
                            <button className="btn btn-success" onClick={() => activateTemplateFromTable(template.id)} disabled={isMutating}>
                              Activate
                            </button>
                          )}
                        </div>
                      </td>
                    </tr>
                  ))}
                  {templatesByType[type].length === 0 && (
                    <tr>
                      <td colSpan={5} className="text-muted">No template yet. Use "Create Template" above.</td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>
          </div>
        </div>
      ))}

      {selectedTemplate && (
        <div className="su-card fade-in" ref={editorRef}>
          <div className="card-body p-4">
            <div className="d-flex flex-wrap justify-content-between align-items-center gap-2 mb-3">
              <h3 className="h6 mb-0">
                Editing {selectedTemplate.template.publicationType} V{selectedTemplate.template.version}{' '}
                {selectedTemplate.template.active && <span className="badge bg-success ms-1">ACTIVE</span>}
                {!selectedTemplate.template.active && <span className="badge bg-secondary ms-1">DRAFT</span>}
              </h3>
              <button
                className="btn btn-outline-secondary btn-sm"
                style={{ borderRadius: '999px' }}
                disabled={isReadOnly}
                onClick={addCategory}
              >
                ➕ Add Category
              </button>
            </div>

            {isReadOnly && (
              <div className="alert alert-warning d-flex flex-wrap align-items-center justify-content-between gap-2 py-2">
                <div>Active templates are read-only.</div>
                <button className="btn btn-outline-primary btn-sm" disabled={isMutating} onClick={() => void createDraftToEdit()}>
                  Create Draft to Edit
                </button>
              </div>
            )}

            <div className="vstack gap-3">
              {categories.map((category) => (
                <div className="p-3" style={{ background: '#f8fafc', borderRadius: '0.6rem', border: '1px solid #e8eff5' }} key={category.id}>
                  <div className="mb-2">
                    <label className="form-label small">Category Title</label>
                    <input
                      className="form-control form-control-sm"
                      value={category.title}
                      disabled={isReadOnly}
                      onChange={(event) => updateCategoryTitle(category.id, event.target.value)}
                    />
                    {category.errorCategoryTitle && (
                      <div className="text-danger small mt-1">{category.errorCategoryTitle}</div>
                    )}
                  </div>

                  <div className="d-flex justify-content-between align-items-center mb-2">
                    <div className="small text-muted">Checklist Items</div>
                    <button
                      className="btn btn-outline-primary btn-sm"
                      disabled={isReadOnly}
                      onClick={() => addItem(category.id)}
                    >
                      Add Item(s)
                    </button>
                  </div>

                  {category.items.length === 0 && (
                    <div className="small text-muted mb-2">No items yet.</div>
                  )}

                  <div className="vstack gap-2">
                    {category.items.map((item) => (
                      <div className="border rounded p-2" key={item.id}>
                        <div className="row g-2">
                          <div className="col-md-5">
                            <label className="form-label small">Item Title</label>
                            <input
                              className="form-control form-control-sm"
                              value={item.title}
                              disabled={isReadOnly}
                              onChange={(event) => updateItem(category.id, item.id, { title: event.target.value })}
                            />
                            {item.errorTitle && <div className="text-danger small mt-1">{item.errorTitle}</div>}
                          </div>
                          <div className="col-md-5">
                            <label className="form-label small">Guidance (optional)</label>
                            <input
                              className="form-control form-control-sm"
                              value={item.guidanceText}
                              disabled={isReadOnly}
                              onChange={(event) => updateItem(category.id, item.id, { guidanceText: event.target.value })}
                            />
                          </div>
                          <div className="col-md-2">
                            <label className="form-label small">Required</label>
                            <div className="form-check mt-1">
                              <input
                                type="checkbox"
                                className="form-check-input"
                                checked={item.isRequired}
                                disabled={isReadOnly}
                                onChange={(event) => updateItem(category.id, item.id, { isRequired: event.target.checked })}
                              />
                            </div>
                          </div>
                        </div>
                        <div className="mt-2">
                          <button
                            className="btn btn-outline-danger btn-sm"
                            disabled={isReadOnly}
                            onClick={() => deleteItem(category.id, item.id)}
                          >
                            Delete Item
                          </button>
                        </div>
                      </div>
                    ))}
                  </div>

                  {category.errorAddItem && (
                    <div className="text-danger small mt-1">{category.errorAddItem}</div>
                  )}
                </div>
              ))}
            </div>

            <div className="mt-4 d-flex flex-wrap gap-2">
              <button className="btn btn-primary" style={{ borderRadius: '999px', padding: '0.5rem 1.5rem' }} disabled={isMutating || isReadOnly} onClick={() => void saveDraft()}>
                💾 Save Draft
              </button>
              {!isReadOnly && (
                <button className="btn btn-success" style={{ borderRadius: '999px', padding: '0.5rem 1.5rem' }} disabled={isMutating} onClick={() => void activateFromEditor()}>
                  🚀 Activate
                </button>
              )}
            </div>
          </div>
        </div>
      )}
    </ShellLayout>
  );
}
