import { apiClient } from './client';
import type { User } from '../types';

export const getAllUsers = async (): Promise<User[]> => {
  const res = await apiClient.get<User[]>('/api/users');
  return res.data;
};

export const getUsersByTenant = async (tenantId: string): Promise<User[]> => {
  const res = await apiClient.get<User[]>(`/api/users/tenant/${tenantId}`);
  return res.data;
};

export const createUser = async (data: {
  tenantId: string;
  email: string;
  password: string;
  role: string;
  firstName?: string;
  lastName?: string;
}): Promise<User> => {
  const res = await apiClient.post<User>('/api/users', data);
  return res.data;
};

export const deactivateUser = async (userId: string): Promise<void> => {
  await apiClient.delete(`/api/users/${userId}`);
};

export const resetPassword = async (userId: string, password: string): Promise<void> => {
  await apiClient.put(`/api/users/${userId}/password`, { password });
};
