import { getJson } from './http';

export interface RepositorySearchParams {
  title?: string;
  author?: string;
  faculty?: string;
  program?: string;
  year?: number;
  keyword?: string;
}

export interface RepositoryItemSummary {
  id: number;
  title: string;
  authors?: string;
  authorName?: string;
  faculty?: string;
  program?: string;
  year?: number;
  keywords?: string;
  publishedAt?: string;
}

export interface RepositoryItemDetail extends RepositoryItemSummary {
  abstractText?: string;
  caseId: number;
}

function buildQuery(params: RepositorySearchParams): string {
  const query = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') {
      query.set(key, String(value));
    }
  });
  const raw = query.toString();
  return raw.length > 0 ? `?${raw}` : '';
}

export const publicRepositoryApi = {
  search(params: RepositorySearchParams): Promise<{ total: number; results: RepositoryItemSummary[] }> {
    return getJson(`/api/public/repository/search${buildQuery(params)}`);
  },

  detail(id: number): Promise<RepositoryItemDetail> {
    return getJson(`/api/public/repository/${id}`);
  },

  async download(id: number): Promise<{ blob: Blob; filename: string }> {
    const response = await fetch(`/api/public/repository/${id}/download`, {
      credentials: 'include',
    });

    if (!response.ok) {
      const message = response.status === 401 || response.status === 403
        ? 'Please sign in to download this file.'
        : `Download failed (${response.status})`;
      throw new Error(message);
    }

    const blob = await response.blob();
    const disposition = response.headers.get('content-disposition') ?? '';
    const match = disposition.match(/filename="?([^"]+)"?/i);
    return {
      blob,
      filename: match?.[1] ?? `publication-${id}.pdf`,
    };
  },
};
