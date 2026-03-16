import { apiClient } from './client';
import type { UploadJob } from '../types';

export const uploadFile = async (
  file: File,
  periodType: string
): Promise<UploadJob> => {
  const formData = new FormData();
  formData.append('file', file);
  formData.append('periodType', periodType);
  const res = await apiClient.post<UploadJob>('/api/uploads', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
  return res.data;
};

export const getUploadJobs = async (tenantId: string): Promise<UploadJob[]> => {
  const res = await apiClient.get<UploadJob[]>(`/api/uploads/tenant/${tenantId}`);
  return res.data;
};
