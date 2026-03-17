import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '../api/client';
import { useAuth } from '../context/AuthContext';
import { triggerForecast, clearForecast } from '../api/forecast';
import type { SalesInsight } from '../types';
import {
  BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, Legend,
  ResponsiveContainer, LineChart, Line, ReferenceLine
} from 'recharts';
import { TrendingUp, DollarSign, ShoppingCart, Award, Play, Trash2, RefreshCw } from 'lucide-react';
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

type Period = 'month' | 'quarter' | 'year' | 'all';

const PERIOD_LABELS: Record<Period, string> = {
  month: 'This Month',
  quarter: 'This Quarter',
  year: 'This Year',
  all: 'All Time',
};

function periodLabel(period: Period): string {
  const now = new Date();
  if (period === 'month') return now.toLocaleString('default', { month: 'long', year: 'numeric' });
  if (period === 'quarter') return `Q${Math.ceil((now.getMonth() + 1) / 3)} ${now.getFullYear()}`;
  if (period === 'all') return 'All Time';
  return `${now.getFullYear()}`;
}

export default function DashboardPage() {
  const { user } = useAuth();
  const qc = useQueryClient();
  const [period, setPeriod] = useState<Period>('all');
  const [forecastMsg, setForecastMsg] = useState<{ text: string; ok: boolean } | null>(null);

  const { data: insights = [], isLoading } = useQuery<SalesInsight[]>({
    queryKey: ['insights', user?.tenantId, period],
    queryFn: async () => {
      const res = await apiClient.get(`/api/insights/${user?.tenantId}?period=${period}`);
      return res.data;
    },
    enabled: !!user?.tenantId,
  });

  const { data: allInsights = [] } = useQuery<SalesInsight[]>({
    queryKey: ['insights-all', user?.tenantId],
    queryFn: async () => {
      const res = await apiClient.get(`/api/insights/${user?.tenantId}?period=all`);
      return res.data;
    },
    enabled: !!user?.tenantId,
  });

  const invalidate = () => {
    qc.invalidateQueries({ queryKey: ['insights', user?.tenantId] });
    qc.invalidateQueries({ queryKey: ['insights-all', user?.tenantId] });
  };

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

  // Actual revenue = sum of actual months only (months that have real sales data)
  const totalActual = insights
    .filter((i) => (i.actualRevenue || 0) > 0)
    .reduce((s, i) => s + i.actualRevenue, 0);

  // Predicted revenue = sum of forecast-only months (future months with no actuals yet)
  const totalPredicted = insights
    .filter((i) => (i.actualRevenue || 0) === 0 && (i.predictedRevenue || 0) > 0)
    .reduce((s, i) => s + i.predictedRevenue, 0);

  // Units from actual months only
  const totalUnits = insights
    .filter((i) => (i.actualRevenue || 0) > 0)
    .reduce((s, i) => s + (i.totalUnits || 0), 0);

  // Top category from actual months
  const topCategory = insights
    .filter((i) => (i.actualRevenue || 0) > 0)
    .find((i) => i.categoryRank === 1)?.categoryName ?? '—';

  // Category chart: aggregate across all months per category
  const chartData = (() => {
    const byCat = new Map<string, { actual: number; forecast: number }>();
    for (const i of insights) {
      const existing = byCat.get(i.categoryName) ?? { actual: 0, forecast: 0 };
      existing.actual += i.actualRevenue || 0;
      existing.forecast += i.predictedRevenue || 0;
      byCat.set(i.categoryName, existing);
    }
    return Array.from(byCat.entries())
      .sort(([, a], [, b]) => b.actual - a.actual)
      .map(([name, { actual, forecast }]) => ({
        name,
        Actual: actual > 0 ? actual : null,
        Forecast: forecast > 0 ? forecast : null,
      }));
  })();

  // Extract "YYYY-MM" from a date string like "2025-10-01"
  const toMonthKey = (dateStr: string): string => dateStr.slice(0, 7);

  // Format "2025-10" → "Oct '25"
  const fmtMonth = (m: string) => {
    const [y, mo] = m.split('-');
    const d = new Date(+y, +mo - 1);
    return d.toLocaleString('default', { month: 'short' }) + " '" + y.slice(2);
  };

  const trendData = (() => {
    const byMonth = new Map<string, { actual: number; forecast: number }>();
    for (const i of insights) {
      const m = i.periodMonth ? toMonthKey(i.periodMonth) : '';
      if (!m) continue;
      const existing = byMonth.get(m) ?? { actual: 0, forecast: 0 };
      existing.actual += i.actualRevenue || 0;
      existing.forecast += i.predictedRevenue || 0;
      byMonth.set(m, existing);
    }
    return Array.from(byMonth.entries())
      .sort(([a], [b]) => a.localeCompare(b))
      .map(([month, { actual, forecast }]) => ({
        month: fmtMonth(month),
        Actual: actual > 0 ? actual : null,
        Forecast: forecast > 0 ? forecast : null,
      }));
  })();

  // Full timeline: aggregate all months across categories, sorted ascending
  const fullTrendData = (() => {
    const byMonth = new Map<string, { actual: number; forecast: number }>();
    for (const i of allInsights) {
      const m = i.periodMonth ? toMonthKey(i.periodMonth) : '';
      if (!m) continue;
      const existing = byMonth.get(m) ?? { actual: 0, forecast: 0 };
      existing.actual += i.actualRevenue || 0;
      existing.forecast += i.predictedRevenue || 0;
      byMonth.set(m, existing);
    }
    return Array.from(byMonth.entries())
      .sort(([a], [b]) => a.localeCompare(b))
      .map(([month, { actual, forecast }]) => ({
        month: fmtMonth(month),
        monthKey: month,
        Actual: actual > 0 ? actual : null,
        Forecast: forecast > 0 ? forecast : null,
        isForecastOnly: actual === 0 && forecast > 0,
      }));
  })();

  // First forecast-only month = boundary marker
  const forecastStartMonth = fullTrendData.find((d) => d.isForecastOnly)?.month ?? null;
  const forecastStartLabel = fullTrendData.find((d) => d.isForecastOnly)?.monthKey ?? null;

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
            <button
              onClick={() => invalidate()}
              className="flex items-center gap-1.5 px-3 py-2 bg-white hover:bg-gray-50 text-gray-600 border border-gray-200 rounded-lg text-sm font-medium transition-colors"
              title="Refresh dashboard data"
            >
              <RefreshCw size={14} />
            </button>
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
              <StatCard title="Actual Revenue" value={fmt(totalActual)} icon={DollarSign} color="bg-blue-600" />
              <StatCard title="Forecasted Revenue (Next)" value={fmt(totalPredicted)} icon={TrendingUp} color="bg-emerald-500" />
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
                <h2 className="text-base font-semibold text-gray-900 mb-4">Revenue Trend ({periodLabel(period)})</h2>
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

            {/* Full actuals + forecast overlay chart */}
            {fullTrendData.length > 0 && (
              <div className="mt-6 bg-white rounded-xl p-6 shadow-sm border border-gray-100">
                <div className="flex items-center justify-between mb-1">
                  <h2 className="text-base font-semibold text-gray-900">Full Revenue Timeline — Actuals &amp; Forecast</h2>
                  {forecastStartLabel && (
                    <span className="text-xs text-emerald-600 font-medium bg-emerald-50 border border-emerald-200 px-2 py-1 rounded-full">
                      Forecast from {forecastStartMonth}
                    </span>
                  )}
                </div>
                <p className="text-xs text-gray-400 mb-4">
                  Solid line = actual sales &nbsp;·&nbsp; Dashed line = forecasted revenue
                </p>
                <ResponsiveContainer width="100%" height={320}>
                  <LineChart data={fullTrendData} margin={{ top: 5, right: 20, left: 10, bottom: 5 }}>
                    <CartesianGrid strokeDasharray="3 3" stroke="#f0f0f0" />
                    <XAxis dataKey="month" tick={{ fontSize: 11 }} />
                    <YAxis tick={{ fontSize: 11 }} tickFormatter={(v) => fmt(v)} width={70} />
                    <Tooltip
                      content={(props: any) => {
                        if (!props.active || !props.payload?.length) return null;
                        return (
                          <div className="bg-white border border-gray-200 rounded-lg shadow-lg p-3 text-sm">
                            <p className="font-semibold text-gray-700 mb-1">{props.label}</p>
                            {props.payload.map((p: any) => (
                              <p key={p.name} style={{ color: p.color }}>
                                {p.name}: {fmt(Number(p.value))}
                              </p>
                            ))}
                          </div>
                        );
                      }}
                    />
                    <Legend />
                    {forecastStartMonth && (
                      <ReferenceLine
                        x={forecastStartMonth}
                        stroke="#f59e0b"
                        strokeDasharray="4 4"
                        label={{ value: 'Forecast start', position: 'insideTopRight', fontSize: 11, fill: '#f59e0b' }}
                      />
                    )}
                    <Line
                      type="monotone"
                      dataKey="Actual"
                      stroke="#3b82f6"
                      strokeWidth={2.5}
                      dot={{ r: 3 }}
                      connectNulls={false}
                    />
                    <Line
                      type="monotone"
                      dataKey="Forecast"
                      stroke="#10b981"
                      strokeWidth={2.5}
                      strokeDasharray="6 3"
                      dot={{ r: 3 }}
                      connectNulls={false}
                    />
                  </LineChart>
                </ResponsiveContainer>
              </div>
            )}

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
