import { apiClient } from './client';

export type ForecastHorizon = '1w' | '1m' | '1q' | '1y';

export const HORIZON_OPTIONS: { value: ForecastHorizon; label: string; minDays: number }[] = [
  { value: '1w', label: 'Week', minDays: 21 },
  { value: '1m', label: 'Month', minDays: 90 },
  { value: '1q', label: 'Quarter', minDays: 180 },
  { value: '1y', label: 'Year', minDays: 365 },
];

export const triggerForecast = async (
  tenantId: string,
  algorithm: string = 'xgboost',
  horizon: ForecastHorizon = '1m',
): Promise<{ workflowName: string; status: string }> => {
  const res = await apiClient.post('/api/forecast/trigger', { tenantId, algorithm, horizon });
  return res.data;
};

export const clearForecast = async (tenantId: string): Promise<void> => {
  await apiClient.delete(`/api/forecast/${tenantId}`);
};
