import { apiClient } from './client';
import type { Tenant } from '../types';

export const getTenants = async (): Promise<Tenant[]> => {
  const res = await apiClient.get<Tenant[]>('/api/tenants');
  return res.data;
};

export const getTenant = async (id: string): Promise<Tenant> => {
  const res = await apiClient.get<Tenant>(`/api/tenants/${id}`);
  return res.data;
};

export const createTenant = async (data: Partial<Tenant>): Promise<Tenant> => {
  const res = await apiClient.post<Tenant>('/api/tenants', data);
  return res.data;
};

export const updateTenant = async (id: string, data: Partial<Tenant>): Promise<Tenant> => {
  const res = await apiClient.put<Tenant>(`/api/tenants/${id}`, data);
  return res.data;
};

export const deleteTenant = async (id: string): Promise<void> => {
  await apiClient.delete(`/api/tenants/${id}`);
};
