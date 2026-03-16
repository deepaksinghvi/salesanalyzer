import { apiClient } from './client';

export const triggerForecast = async (tenantId: string): Promise<{ workflowName: string; status: string }> => {
  const res = await apiClient.post('/api/forecast/trigger', { tenantId });
  return res.data;
};

export const clearForecast = async (tenantId: string): Promise<void> => {
  await apiClient.delete(`/api/forecast/${tenantId}`);
};
