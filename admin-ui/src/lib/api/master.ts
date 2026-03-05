import { getJson } from './http';

export interface Faculty {
  id: number;
  code?: string | null;
  name: string;
}

export interface Program {
  id: number;
  facultyId: number;
  code?: string | null;
  name: string;
}

export const masterApi = {
  listFaculties(): Promise<Faculty[]> {
    return getJson('/api/public/master/faculties');
  },

  listPrograms(facultyId?: number): Promise<Program[]> {
    const query = facultyId ? `?facultyId=${facultyId}` : '';
    return getJson(`/api/public/master/programs${query}`);
  },
};
