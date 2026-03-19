import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '../api/client';
import { useAuth } from '../context/AuthContext';
import { triggerForecast, clearForecast, HORIZON_OPTIONS } from '../api/forecast';
import type { ForecastHorizon } from '../api/forecast';
import type { SalesInsight } from '../types';
import {
  BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, Legend,
  ResponsiveContainer, LineChart, Line, ReferenceLine
} from 'recharts';
import { TrendingUp, DollarSign, ShoppingCart, Award, Play, Trash2, RefreshCw, ChevronDown } from 'lucide-react';
import Layout from '../components/Layout';

// ── Helpers ──────────────────────────────────────────────────

function fmt(n: number) {
  return new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD', notation: 'compact' }).format(n);
}

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

// ── Range Options ────────────────────────────────────────────

type RangeKey = 'last_month' | 'last_quarter' | 'last_year' | 'ytd' | 'all' | 'custom';

const RANGE_OPTIONS: { value: RangeKey; label: string }[] = [
  { value: 'last_month', label: 'Last Month' },
  { value: 'last_quarter', label: 'Last Quarter' },
  { value: 'last_year', label: 'Last Year' },
  { value: 'ytd', label: 'Year to Date' },
  { value: 'all', label: 'All Time' },
  { value: 'custom', label: 'Custom Range' },
];

interface DataRange {
  minDate: string | null;
  maxDate: string | null;
  totalDays: number;
  forecastMinDate: string | null;
  forecastMaxDate: string | null;
  forecastDays: number;
}

// Format "2025-10-01" → "Oct '25"
const fmtMonth = (dateStr: string) => {
  const [y, mo] = dateStr.split('-').map(Number);
  const d = new Date(y, mo - 1);
  return d.toLocaleString('default', { month: 'short' }) + " '" + String(y).slice(2);
};

// Format "2025-12-01" → "Dec 1, 2025"
const fmtDate = (dateStr: string) => {
  const [y, mo, d] = dateStr.split('-').map(Number);
  return new Date(y, mo - 1, d).toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' });
};

// Derive forecast type label from forecastDays
const forecastTypeLabel = (days: number): string => {
  if (days <= 0) return '';
  if (days <= 7) return 'Weekly';
  if (days <= 21) return `${days}-day`;
  if (days <= 35) return 'Monthly';
  if (days <= 100) return 'Quarterly';
  return 'Yearly';
};

// ── Component ────────────────────────────────────────────────

export default function DashboardPage() {
  const { user } = useAuth();
  const qc = useQueryClient();
  const canManage = user?.role === 'Admin' || user?.role === 'SuperAdmin';

  // State
  const [range, setRange] = useState<RangeKey>('all');
  const [customFrom, setCustomFrom] = useState('');
  const [customTo, setCustomTo] = useState('');
  const [showRangeMenu, setShowRangeMenu] = useState(false);
  const [forecastMsg, setForecastMsg] = useState<{ text: string; ok: boolean } | null>(null);
  const [selectedAlgorithm, setSelectedAlgorithm] = useState('xgboost');
  const [selectedHorizon, setSelectedHorizon] = useState<ForecastHorizon>('1m');
  const [showForecastPanel, setShowForecastPanel] = useState(false);
  const [showWeeklyWarning, setShowWeeklyWarning] = useState(false);

  // Build query params
  const insightsParams = (() => {
    if (range === 'custom' && customFrom && customTo) {
      return `range=custom&from=${customFrom}&to=${customTo}`;
    }
    return `range=${range}`;
  })();

  // Queries
  const { data: insights = [], isLoading } = useQuery<SalesInsight[]>({
    queryKey: ['insights', user?.tenantId, insightsParams],
    queryFn: async () => {
      const res = await apiClient.get(`/api/insights/${user?.tenantId}?${insightsParams}`);
      return res.data;
    },
    enabled: !!user?.tenantId,
  });

  const { data: allInsights = [] } = useQuery<SalesInsight[]>({
    queryKey: ['insights-all', user?.tenantId],
    queryFn: async () => {
      const res = await apiClient.get(`/api/insights/${user?.tenantId}?range=all`);
      return res.data;
    },
    enabled: !!user?.tenantId,
  });

  const { data: dataRange } = useQuery<DataRange>({
    queryKey: ['data-range', user?.tenantId],
    queryFn: async () => {
      const res = await apiClient.get(`/api/insights/${user?.tenantId}/data-range`);
      return res.data;
    },
    enabled: !!user?.tenantId,
  });

  const invalidate = () => {
    qc.invalidateQueries({ queryKey: ['insights'] });
    qc.invalidateQueries({ queryKey: ['insights-all'] });
    qc.invalidateQueries({ queryKey: ['data-range'] });
  };

  // Mutations
  const runForecastMutation = useMutation({
    mutationFn: () => triggerForecast(user!.tenantId, selectedAlgorithm, selectedHorizon),
    onSuccess: () => {
      const horizonLabel = HORIZON_OPTIONS.find(h => h.value === selectedHorizon)?.label ?? selectedHorizon;
      setForecastMsg({ text: `Forecast started — ${selectedAlgorithm.toUpperCase()}, ${horizonLabel} ahead`, ok: true });
      setShowForecastPanel(false);
      setTimeout(() => { setForecastMsg(null); invalidate(); }, 4000);
    },
    onError: () => setForecastMsg({ text: 'Failed to trigger forecast.', ok: false }),
  });

  const clearForecastMutation = useMutation({
    mutationFn: () => clearForecast(user!.tenantId),
    onSuccess: () => {
      setForecastMsg({ text: 'Forecast data cleared.', ok: true });
      setTimeout(() => { setForecastMsg(null); invalidate(); }, 2000);
    },
    onError: () => setForecastMsg({ text: 'Failed to clear forecast data.', ok: false }),
  });

  const actualDays = dataRange?.totalDays ?? 0;
  const existingForecastDays = dataRange?.forecastDays ?? 0;
  const hasWeeklyForecast = existingForecastDays > 0 && existingForecastDays <= 21;

  const handleRunForecast = () => {
    // If existing forecast looks like a weekly one and user picks month+, warn first
    if (hasWeeklyForecast && !selectedHorizon.endsWith('w')) {
      setShowWeeklyWarning(true);
    } else {
      runForecastMutation.mutate();
    }
  };

  const confirmForecastAfterWarning = () => {
    setShowWeeklyWarning(false);
    runForecastMutation.mutate();
  };

  // ── Derived data ──

  const totalActual = insights
    .filter(i => (i.actualRevenue || 0) > 0)
    .reduce((s, i) => s + i.actualRevenue, 0);

  const totalPredicted = insights
    .filter(i => (i.actualRevenue || 0) === 0 && (i.predictedRevenue || 0) > 0)
    .reduce((s, i) => s + i.predictedRevenue, 0);

  const totalUnits = insights
    .filter(i => (i.actualRevenue || 0) > 0)
    .reduce((s, i) => s + (i.totalUnits || 0), 0);

  const topCategory = insights
    .filter(i => (i.actualRevenue || 0) > 0)
    .find(i => i.categoryRank === 1)?.categoryName ?? '—';

  // Category breakdown chart
  const chartData = (() => {
    const byCat = new Map<string, { actual: number; forecast: number }>();
    for (const i of insights) {
      const e = byCat.get(i.categoryName) ?? { actual: 0, forecast: 0 };
      e.actual += i.actualRevenue || 0;
      e.forecast += i.predictedRevenue || 0;
      byCat.set(i.categoryName, e);
    }
    return Array.from(byCat.entries())
      .sort(([, a], [, b]) => b.actual - a.actual)
      .map(([name, { actual, forecast }]) => ({
        name,
        Actual: actual > 0 ? actual : null,
        Forecast: forecast > 0 ? forecast : null,
      }));
  })();

  // Trend chart (filtered range)
  const trendData = (() => {
    const byMonth = new Map<string, { actual: number; forecast: number }>();
    for (const i of insights) {
      const m = i.periodMonth?.slice(0, 7) ?? '';
      if (!m) continue;
      const e = byMonth.get(m) ?? { actual: 0, forecast: 0 };
      e.actual += i.actualRevenue || 0;
      e.forecast += i.predictedRevenue || 0;
      byMonth.set(m, e);
    }
    return Array.from(byMonth.entries())
      .sort(([a], [b]) => a.localeCompare(b))
      .map(([key, { actual, forecast }]) => ({
        month: fmtMonth(key + '-01'),
        Actual: actual > 0 ? actual : null,
        Forecast: forecast > 0 ? forecast : null,
      }));
  })();

  // Full timeline for the overlay chart
  const isWeeklyForecast = existingForecastDays > 0 && existingForecastDays <= 7;
  const fullTrendData = (() => {
    const byMonth = new Map<string, { actual: number; forecast: number }>();
    for (const i of allInsights) {
      const m = i.periodMonth?.slice(0, 7) ?? '';
      if (!m) continue;
      const e = byMonth.get(m) ?? { actual: 0, forecast: 0 };
      e.actual += i.actualRevenue || 0;
      e.forecast += i.predictedRevenue || 0;
      byMonth.set(m, e);
    }
    return Array.from(byMonth.entries())
      .sort(([a], [b]) => a.localeCompare(b))
      .map(([key, { actual, forecast }]) => {
        const isForecastOnly = actual === 0 && forecast > 0;
        const label = fmtMonth(key + '-01');
        return {
          month: isForecastOnly && isWeeklyForecast ? `${label} (${existingForecastDays}d)` : label,
          monthKey: key,
          Actual: actual > 0 ? actual : null,
          Forecast: forecast > 0 ? forecast : null,
          isForecastOnly,
        };
      });
  })();

  const forecastStart = fullTrendData.find(d => d.isForecastOnly);
  const rangeLabel = RANGE_OPTIONS.find(r => r.value === range)?.label ?? 'All Time';

  return (
    <Layout>
      <div className="p-8">
        {/* ── Header ── */}
        <div className="mb-6 flex items-start justify-between flex-wrap gap-4">
          <div>
            <h1 className="text-2xl font-bold text-gray-900">Sales Dashboard</h1>
            <p className="text-gray-500 mt-1">
              Actual vs forecast performance
              {dataRange && dataRange.totalDays > 0 && (
                <span className="text-gray-400"> — {dataRange.totalDays} days of data</span>
              )}
              {dataRange && dataRange.forecastDays > 0 && (
                <span className="text-emerald-500">
                  {' '} + {forecastTypeLabel(dataRange.forecastDays)} forecast ({dataRange.forecastDays}d: {fmtDate(dataRange.forecastMinDate!)} – {fmtDate(dataRange.forecastMaxDate!)})
                </span>
              )}
            </p>
          </div>
          <div className="flex items-center gap-2">
            {/* Range dropdown */}
            <div className="relative">
              <button
                onClick={() => setShowRangeMenu(!showRangeMenu)}
                className="flex items-center gap-2 px-4 py-2 bg-white border border-gray-200 rounded-lg text-sm font-medium text-gray-700 hover:bg-gray-50 transition-colors"
              >
                {rangeLabel}
                <ChevronDown size={14} />
              </button>
              {showRangeMenu && (
                <div className="absolute right-0 mt-1 w-48 bg-white border border-gray-200 rounded-lg shadow-lg z-20">
                  {RANGE_OPTIONS.map(opt => (
                    <button
                      key={opt.value}
                      onClick={() => { setRange(opt.value); setShowRangeMenu(false); }}
                      className={`w-full text-left px-4 py-2 text-sm hover:bg-gray-50 transition-colors first:rounded-t-lg last:rounded-b-lg ${
                        range === opt.value ? 'bg-blue-50 text-blue-700 font-medium' : 'text-gray-700'
                      }`}
                    >
                      {opt.label}
                    </button>
                  ))}
                </div>
              )}
            </div>

            <button
              onClick={() => invalidate()}
              className="p-2 bg-white hover:bg-gray-50 text-gray-500 border border-gray-200 rounded-lg transition-colors"
              title="Refresh"
            >
              <RefreshCw size={14} />
            </button>
          </div>
        </div>

        {/* Custom date range picker */}
        {range === 'custom' && (
          <div className="mb-6 flex items-center gap-3 bg-white rounded-lg border border-gray-200 p-3">
            <label className="text-sm text-gray-500">From</label>
            <input
              type="month"
              value={customFrom ? customFrom.slice(0, 7) : ''}
              onChange={e => setCustomFrom(e.target.value + '-01')}
              className="px-3 py-1.5 border border-gray-200 rounded-lg text-sm"
            />
            <label className="text-sm text-gray-500">To</label>
            <input
              type="month"
              value={customTo ? customTo.slice(0, 7) : ''}
              onChange={e => setCustomTo(e.target.value + '-01')}
              className="px-3 py-1.5 border border-gray-200 rounded-lg text-sm"
            />
          </div>
        )}

        {/* ── Forecast Controls ── */}
        {canManage && (
          <div className="mb-6">
            <div className="flex items-center gap-2">
              <button
                onClick={() => setShowForecastPanel(!showForecastPanel)}
                className={`flex items-center gap-1.5 px-4 py-2 rounded-lg text-sm font-medium transition-colors ${
                  showForecastPanel
                    ? 'bg-emerald-700 text-white'
                    : 'bg-emerald-600 hover:bg-emerald-700 text-white'
                }`}
              >
                <Play size={14} />
                Run Forecast
              </button>
              <button
                onClick={() => { if (confirm('Clear all forecast data?')) clearForecastMutation.mutate(); }}
                disabled={clearForecastMutation.isPending}
                className="flex items-center gap-1.5 px-4 py-2 bg-white hover:bg-red-50 text-red-600 border border-red-200 rounded-lg text-sm font-medium transition-colors disabled:opacity-50"
              >
                <Trash2 size={14} />
                Clear Forecast
              </button>
            </div>

            {showForecastPanel && (
              <div className="mt-3 bg-white rounded-xl p-5 shadow-sm border border-emerald-200">
                <div className="flex items-end gap-8 flex-wrap">
                  {/* Algorithm */}
                  <div>
                    <label className="block text-xs font-medium text-gray-500 mb-2">Algorithm</label>
                    <div className="flex gap-2">
                      {[{ id: 'xgboost', label: 'XGBoost' }, { id: 'prophet', label: 'Prophet' }].map(a => (
                        <button
                          key={a.id}
                          onClick={() => setSelectedAlgorithm(a.id)}
                          className={`px-4 py-2 rounded-lg text-sm font-medium transition-colors ${
                            selectedAlgorithm === a.id
                              ? 'bg-emerald-600 text-white'
                              : 'bg-gray-100 text-gray-600 hover:bg-gray-200'
                          }`}
                        >
                          {a.label}
                        </button>
                      ))}
                    </div>
                  </div>

                  {/* Horizon */}
                  <div>
                    <label className="block text-xs font-medium text-gray-500 mb-2">Forecast Period</label>
                    <div className="flex gap-2">
                      {HORIZON_OPTIONS.map(h => {
                        const enabled = actualDays >= h.minDays;
                        return (
                          <button
                            key={h.value}
                            onClick={() => enabled && setSelectedHorizon(h.value)}
                            disabled={!enabled}
                            title={enabled ? `Forecast ${h.label} ahead` : `Need ${h.minDays} days of data (have ${actualDays})`}
                            className={`px-4 py-2 rounded-lg text-sm font-medium transition-colors ${
                              selectedHorizon === h.value && enabled
                                ? 'bg-emerald-600 text-white'
                                : enabled
                                  ? 'bg-gray-100 text-gray-600 hover:bg-gray-200'
                                  : 'bg-gray-50 text-gray-300 cursor-not-allowed'
                            }`}
                          >
                            {h.label}
                          </button>
                        );
                      })}
                    </div>
                  </div>

                  <button
                    onClick={handleRunForecast}
                    disabled={runForecastMutation.isPending || actualDays < (HORIZON_OPTIONS.find(h => h.value === selectedHorizon)?.minDays ?? 999)}
                    className="flex items-center gap-2 px-6 py-2 bg-emerald-600 hover:bg-emerald-700 disabled:bg-gray-300 text-white rounded-lg text-sm font-medium transition-colors"
                  >
                    <Play size={14} />
                    {runForecastMutation.isPending ? 'Running...' : 'Start'}
                  </button>
                </div>

                {actualDays > 0 && actualDays < 21 && (
                  <p className="mt-3 text-xs text-amber-600">
                    Need at least 3 weeks of data to forecast. Currently have {actualDays} days.
                  </p>
                )}
                {selectedHorizon.endsWith('w') ? (
                  <p className="mt-3 text-xs text-gray-400">
                    Weekly forecasts replace all existing forecast data.
                  </p>
                ) : (
                  <p className="mt-3 text-xs text-gray-400">
                    Monthly+ forecasts are additive — existing forecast months are retained, new months are added.
                  </p>
                )}
              </div>
            )}
          </div>
        )}

        {/* No forecast hint */}
        {canManage && !showForecastPanel && actualDays > 0 && existingForecastDays === 0 && !forecastMsg && (
          <div className="mb-4 px-4 py-3 rounded-lg text-sm bg-blue-50 border border-blue-200 text-blue-700">
            No forecast data yet.{' '}
            <button
              onClick={() => setShowForecastPanel(true)}
              className="underline font-medium hover:text-blue-900"
            >
              Run a forecast
            </button>{' '}
            to generate predictions beyond your actual data.
          </div>
        )}

        {/* Toast */}
        {forecastMsg && (
          <div className={`mb-4 px-4 py-3 rounded-lg text-sm font-medium ${
            forecastMsg.ok
              ? 'bg-emerald-50 border border-emerald-200 text-emerald-700'
              : 'bg-red-50 border border-red-200 text-red-700'
          }`}>
            {forecastMsg.text}
          </div>
        )}

        {/* ── Content ── */}
        {isLoading ? (
          <div className="flex items-center justify-center h-64">
            <div className="animate-spin rounded-full h-10 w-10 border-b-2 border-blue-600" />
          </div>
        ) : (
          <>
            {/* Stat cards */}
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-6 mb-8">
              <StatCard title="Actual Revenue" value={fmt(totalActual)} icon={DollarSign} color="bg-blue-600" />
              <StatCard
                title={existingForecastDays > 0 ? `Forecast (${forecastTypeLabel(existingForecastDays)})` : 'Forecasted Revenue'}
                value={fmt(totalPredicted)} icon={TrendingUp} color="bg-emerald-500"
              />
              <StatCard title="Units Sold" value={totalUnits.toLocaleString()} icon={ShoppingCart} color="bg-violet-500" />
              <StatCard title="Top Category" value={topCategory} icon={Award} color="bg-amber-500" />
            </div>

            {/* Charts row */}
            <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
              {/* Category breakdown */}
              <div className="bg-white rounded-xl p-6 shadow-sm border border-gray-100">
                <h2 className="text-base font-semibold text-gray-900 mb-4">Revenue by Category</h2>
                <ResponsiveContainer width="100%" height={300}>
                  <BarChart data={chartData}>
                    <CartesianGrid strokeDasharray="3 3" stroke="#f0f0f0" />
                    <XAxis dataKey="name" tick={{ fontSize: 12 }} />
                    <YAxis tick={{ fontSize: 12 }} tickFormatter={v => fmt(v)} />
                    <Tooltip formatter={(v: unknown) => fmt(Number(v))} />
                    <Legend />
                    <Bar dataKey="Actual" fill="#3b82f6" radius={[4, 4, 0, 0]} />
                    <Bar dataKey="Forecast" fill="#10b981" radius={[4, 4, 0, 0]} />
                  </BarChart>
                </ResponsiveContainer>
              </div>

              {/* Trend (selected range) */}
              <div className="bg-white rounded-xl p-6 shadow-sm border border-gray-100">
                <h2 className="text-base font-semibold text-gray-900 mb-4">Monthly Trend — {rangeLabel}</h2>
                <ResponsiveContainer width="100%" height={300}>
                  <LineChart data={trendData}>
                    <CartesianGrid strokeDasharray="3 3" stroke="#f0f0f0" />
                    <XAxis dataKey="month" tick={{ fontSize: 12 }} />
                    <YAxis tick={{ fontSize: 12 }} tickFormatter={v => fmt(v)} />
                    <Tooltip formatter={(v: unknown) => fmt(Number(v))} />
                    <Legend />
                    <Line type="monotone" dataKey="Actual" stroke="#3b82f6" strokeWidth={2} dot={false} />
                    <Line type="monotone" dataKey="Forecast" stroke="#10b981" strokeWidth={2} strokeDasharray="5 5" dot={false} />
                  </LineChart>
                </ResponsiveContainer>
              </div>
            </div>

            {/* Full timeline chart */}
            {fullTrendData.length > 0 && (
              <div className="mt-6 bg-white rounded-xl p-6 shadow-sm border border-gray-100">
                <div className="flex items-center justify-between mb-1">
                  <h2 className="text-base font-semibold text-gray-900">Full Timeline — Actuals & Forecast</h2>
                  <div className="flex items-center gap-2">
                    {dataRange && existingForecastDays > 0 && (
                      <span className={`text-xs font-medium px-2 py-1 rounded-full border ${
                        existingForecastDays <= 7
                          ? 'text-amber-600 bg-amber-50 border-amber-200'
                          : 'text-emerald-600 bg-emerald-50 border-emerald-200'
                      }`}>
                        {forecastTypeLabel(existingForecastDays)} forecast
                        {dataRange.forecastMinDate && dataRange.forecastMaxDate && (
                          <> &middot; {fmtDate(dataRange.forecastMinDate)} – {fmtDate(dataRange.forecastMaxDate)}</>
                        )}
                      </span>
                    )}
                  </div>
                </div>
                <p className="text-xs text-gray-400 mb-4">
                  Solid = actuals &middot; Dashed = forecast
                  {existingForecastDays > 0 && existingForecastDays <= 7 && (
                    <span className="text-amber-500"> &middot; Note: weekly forecast covers only {existingForecastDays} days, not a full month</span>
                  )}
                </p>
                <ResponsiveContainer width="100%" height={320}>
                  <LineChart data={fullTrendData} margin={{ top: 5, right: 20, left: 10, bottom: 5 }}>
                    <CartesianGrid strokeDasharray="3 3" stroke="#f0f0f0" />
                    <XAxis dataKey="month" tick={{ fontSize: 11 }} />
                    <YAxis tick={{ fontSize: 11 }} tickFormatter={v => fmt(v)} width={70} />
                    <Tooltip
                      content={(props: any) => {
                        if (!props.active || !props.payload?.length) return null;
                        const entry = props.payload[0]?.payload;
                        const isFc = entry?.isForecastOnly;
                        return (
                          <div className="bg-white border border-gray-200 rounded-lg shadow-lg p-3 text-sm">
                            <p className="font-semibold text-gray-700 mb-1">{props.label}</p>
                            {props.payload.map((p: any) => (
                              <p key={p.name} style={{ color: p.color }}>
                                {p.name}: {fmt(Number(p.value))}
                              </p>
                            ))}
                            {isFc && existingForecastDays > 0 && existingForecastDays <= 7 && (
                              <p className="text-amber-500 text-xs mt-1">
                                {existingForecastDays}-day weekly forecast (partial month)
                              </p>
                            )}
                          </div>
                        );
                      }}
                    />
                    <Legend />
                    {forecastStart && (
                      <ReferenceLine
                        x={forecastStart.month}
                        stroke="#f59e0b"
                        strokeDasharray="4 4"
                        label={{ value: 'Forecast start', position: 'insideTopRight', fontSize: 11, fill: '#f59e0b' }}
                      />
                    )}
                    <Line type="monotone" dataKey="Actual" stroke="#3b82f6" strokeWidth={2.5} dot={{ r: 3 }} connectNulls={false} />
                    <Line type="monotone" dataKey="Forecast" stroke="#10b981" strokeWidth={2.5} strokeDasharray="6 3" dot={{ r: 3 }} connectNulls={false} />
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

      {/* Weekly forecast overwrite warning dialog */}
      {showWeeklyWarning && (
        <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50">
          <div className="bg-white rounded-xl shadow-xl p-6 max-w-md mx-4">
            <h3 className="text-lg font-semibold text-gray-900 mb-2">Replace weekly forecast?</h3>
            <p className="text-sm text-gray-600 mb-4">
              You have an existing weekly forecast ({existingForecastDays} days). Running a{' '}
              {HORIZON_OPTIONS.find(h => h.value === selectedHorizon)?.label.toLowerCase()} forecast
              will <strong>delete all existing forecast data</strong> and generate new predictions.
            </p>
            <div className="flex justify-end gap-3">
              <button
                onClick={() => setShowWeeklyWarning(false)}
                className="px-4 py-2 text-sm font-medium text-gray-700 bg-gray-100 hover:bg-gray-200 rounded-lg transition-colors"
              >
                Cancel
              </button>
              <button
                onClick={confirmForecastAfterWarning}
                className="px-4 py-2 text-sm font-medium text-white bg-emerald-600 hover:bg-emerald-700 rounded-lg transition-colors"
              >
                Continue
              </button>
            </div>
          </div>
        </div>
      )}
    </Layout>
  );
}
