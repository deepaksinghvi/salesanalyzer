import { apiClient } from './client';
import type { AuthUser } from '../types';

export const login = async (email: string, password: string): Promise<AuthUser> => {
  const res = await apiClient.post<AuthUser>('/api/auth/login', { email, password });
  return res.data;
};
