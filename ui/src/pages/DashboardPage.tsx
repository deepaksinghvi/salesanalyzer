import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '../api/client';
import { useAuth } from '../context/AuthContext';
import { triggerForecast, clearForecast } from '../api/forecast';
import type { SalesInsight } from '../types';
import {
  BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, Legend,
  ResponsiveContainer, LineChart, Line
} from 'recharts';
import { TrendingUp, DollarSign, ShoppingCart, Award, Play, Trash2 } from 'lucide-react';
import Layout from '../components/Layout';

function StatCard({ title, value, icon: Icon, color }: {
  title: string; value: string; icon: React.ElementType; color: string;
}) {
  return (
    <div className="bg-white rounded-xl p-6 shadow-sm border border-gray-100">
      <div className="flex items-center justify-between">
        <div>
          <p className="text-sm text-gray-500 font-medium">{title}</p>
          <p className="text-2xl font-bold text-gray-900 mt-1">{value}</p>
        </div>
        <div className={`w-12 h-12 rounded-xl flex items-center justify-center ${color}`}>
          <Icon size={22} className="text-white" />
        </div>
      </div>
    </div>
  );
}

function fmt(n: number) {
  return new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD', notation: 'compact' }).format(n);
}

type Period = 'month' | 'quarter' | 'year';

const PERIOD_LABELS: Record<Period, string> = {
  month: 'This Month',
  quarter: 'This Quarter',
  year: 'This Year',
};

function periodLabel(period: Period): string {
  const now = new Date();
  if (period === 'month') return now.toLocaleString('default', { month: 'long', year: 'numeric' });
  if (period === 'quarter') return `Q${Math.ceil((now.getMonth() + 1) / 3)} ${now.getFullYear()}`;
  return `${now.getFullYear()}`;
}

export default function DashboardPage() {
  const { user } = useAuth();
  const qc = useQueryClient();
  const [period, setPeriod] = useState<Period>('month');
  const [forecastMsg, setForecastMsg] = useState<{ text: string; ok: boolean } | null>(null);

  const { data: insights = [], isLoading } = useQuery<SalesInsight[]>({
    queryKey: ['insights', user?.tenantId, period],
    queryFn: async () => {
      const res = await apiClient.get(`/api/insights/${user?.tenantId}?period=${period}`);
      return res.data;
    },
    enabled: !!user?.tenantId,
  });

  const invalidate = () => qc.invalidateQueries({ queryKey: ['insights', user?.tenantId] });

  const runForecastMutation = useMutation({
    mutationFn: () => triggerForecast(user!.tenantId),
    onSuccess: () => {
      setForecastMsg({ text: 'Forecast triggered successfully!', ok: true });
      setTimeout(() => { setForecastMsg(null); invalidate(); }, 3000);
    },
    onError: () => setForecastMsg({ text: 'Failed to trigger forecast. Please try again.', ok: false }),
  });

  const clearForecastMutation = useMutation({
    mutationFn: () => clearForecast(user!.tenantId),
    onSuccess: () => {
      setForecastMsg({ text: 'Forecast data cleared.', ok: true });
      setTimeout(() => { setForecastMsg(null); invalidate(); }, 2000);
    },
    onError: () => setForecastMsg({ text: 'Failed to clear forecast data.', ok: false }),
  });

  const totalActual = insights.reduce((s, i) => s + (i.actualRevenue || 0), 0);
  const totalPredicted = insights.reduce((s, i) => s + (i.predictedRevenue || 0), 0);
  const totalUnits = insights.reduce((s, i) => s + (i.totalUnits || 0), 0);
  const topCategory = insights.find((i) => i.categoryRank === 1)?.categoryName ?? '—';

  const chartData = insights.slice(0, 10).map((i) => ({
    name: i.categoryName,
    Actual: i.actualRevenue,
    Forecast: i.predictedRevenue,
  }));

  const trendData = Array.from(
    new Map(insights.map((i) => [i.periodMonth?.slice(0, 7), i])).values()
  ).map((i) => ({
    month: i.periodMonth?.slice(0, 7),
    Actual: i.actualRevenue,
    Forecast: i.predictedRevenue,
  }));

  return (
    <Layout>
      <div className="p-8">
        <div className="mb-8 flex items-start justify-between">
          <div>
            <h1 className="text-2xl font-bold text-gray-900">Sales Dashboard</h1>
            <p className="text-gray-500 mt-1">
              {periodLabel(period)} — Overview of actual vs forecasted performance
            </p>
          </div>
          <div className="flex items-center gap-2">
            {(Object.keys(PERIOD_LABELS) as Period[]).map((p) => (
              <button
                key={p}
                onClick={() => setPeriod(p)}
                className={`px-4 py-2 rounded-lg text-sm font-medium transition-colors ${
                  period === p
                    ? 'bg-blue-600 text-white'
                    : 'bg-white text-gray-600 border border-gray-200 hover:bg-gray-50'
                }`}
              >
                {PERIOD_LABELS[p]}
              </button>
            ))}
            <div className="w-px h-6 bg-gray-200 mx-1" />
            <button
              onClick={() => runForecastMutation.mutate()}
              disabled={runForecastMutation.isPending || clearForecastMutation.isPending}
              className="flex items-center gap-1.5 px-4 py-2 bg-emerald-600 hover:bg-emerald-700 disabled:bg-gray-300 text-white rounded-lg text-sm font-medium transition-colors"
              title="Run forecast for this tenant"
            >
              <Play size={14} />
              {runForecastMutation.isPending ? 'Running…' : 'Run Forecast'}
            </button>
            <button
              onClick={() => { if (confirm('Clear all forecast data for this tenant?')) clearForecastMutation.mutate(); }}
              disabled={runForecastMutation.isPending || clearForecastMutation.isPending}
              className="flex items-center gap-1.5 px-4 py-2 bg-white hover:bg-red-50 disabled:bg-gray-100 text-red-600 border border-red-200 rounded-lg text-sm font-medium transition-colors"
              title="Clear forecast data for this tenant"
            >
              <Trash2 size={14} />
              {clearForecastMutation.isPending ? 'Clearing…' : 'Clear Forecast'}
            </button>
          </div>
        </div>

        {forecastMsg && (
          <div className={`mb-4 px-4 py-3 rounded-lg text-sm font-medium ${
            forecastMsg.ok
              ? 'bg-emerald-50 border border-emerald-200 text-emerald-700'
              : 'bg-red-50 border border-red-200 text-red-700'
          }`}>
            {forecastMsg.text}
          </div>
        )}

        {isLoading ? (
          <div className="flex items-center justify-center h-64">
            <div className="animate-spin rounded-full h-10 w-10 border-b-2 border-blue-600" />
          </div>
        ) : (
          <>
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-6 mb-8">
              <StatCard title="Total Actual Revenue" value={fmt(totalActual)} icon={DollarSign} color="bg-blue-600" />
              <StatCard title="Predicted Revenue" value={fmt(totalPredicted)} icon={TrendingUp} color="bg-emerald-500" />
              <StatCard title="Units Sold" value={totalUnits.toLocaleString()} icon={ShoppingCart} color="bg-violet-500" />
              <StatCard title="Top Category" value={topCategory} icon={Award} color="bg-amber-500" />
            </div>

            <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
              <div className="bg-white rounded-xl p-6 shadow-sm border border-gray-100">
                <h2 className="text-base font-semibold text-gray-900 mb-4">Revenue by Category</h2>
                <ResponsiveContainer width="100%" height={300}>
                  <BarChart data={chartData}>
                    <CartesianGrid strokeDasharray="3 3" stroke="#f0f0f0" />
                    <XAxis dataKey="name" tick={{ fontSize: 12 }} />
                    <YAxis tick={{ fontSize: 12 }} tickFormatter={(v) => fmt(v)} />
                    <Tooltip formatter={(v: unknown) => fmt(Number(v))} />
                    <Legend />
                    <Bar dataKey="Actual" fill="#3b82f6" radius={[4, 4, 0, 0]} />
                    <Bar dataKey="Forecast" fill="#10b981" radius={[4, 4, 0, 0]} />
                  </BarChart>
                </ResponsiveContainer>
              </div>

              <div className="bg-white rounded-xl p-6 shadow-sm border border-gray-100">
                <h2 className="text-base font-semibold text-gray-900 mb-4">Revenue Trend</h2>
                <ResponsiveContainer width="100%" height={300}>
                  <LineChart data={trendData}>
                    <CartesianGrid strokeDasharray="3 3" stroke="#f0f0f0" />
                    <XAxis dataKey="month" tick={{ fontSize: 12 }} />
                    <YAxis tick={{ fontSize: 12 }} tickFormatter={(v) => fmt(v)} />
                    <Tooltip formatter={(v: unknown) => fmt(Number(v))} />
                    <Legend />
                    <Line type="monotone" dataKey="Actual" stroke="#3b82f6" strokeWidth={2} dot={false} />
                    <Line type="monotone" dataKey="Forecast" stroke="#10b981" strokeWidth={2} strokeDasharray="5 5" dot={false} />
                  </LineChart>
                </ResponsiveContainer>
              </div>
            </div>

            {insights.length === 0 && (
              <div className="mt-12 text-center text-gray-400">
                <TrendingUp size={48} className="mx-auto mb-3 opacity-30" />
                <p className="text-lg font-medium">No data yet</p>
                <p className="text-sm mt-1">Upload a CSV file to see your sales insights here.</p>
              </div>
            )}
          </>
        )}
      </div>
    </Layout>
  );
}
