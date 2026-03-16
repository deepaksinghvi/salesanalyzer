import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { getTenants, createTenant, deleteTenant } from '../api/tenants';
import Layout from '../components/Layout';
import type { Tenant } from '../types';
import { Plus, Trash2, Building2 } from 'lucide-react';

const TIERS = ['Basic', 'Pro', 'Enterprise'];

export default function TenantsPage() {
  const qc = useQueryClient();
  const [showForm, setShowForm] = useState(false);
  const [form, setForm] = useState<{ companyName: string; subscriptionTier: 'Basic' | 'Pro' | 'Enterprise'; timezone: string }>({ companyName: '', subscriptionTier: 'Basic', timezone: 'UTC' });

  const { data: tenants = [], isLoading } = useQuery<Tenant[]>({
    queryKey: ['tenants'],
    queryFn: getTenants,
  });

  const createMutation = useMutation({
    mutationFn: createTenant,
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['tenants'] }); setShowForm(false); setForm({ companyName: '', subscriptionTier: 'Basic', timezone: 'UTC' }); },
  });

  const deleteMutation = useMutation({
    mutationFn: deleteTenant,
    onSuccess: () => qc.invalidateQueries({ queryKey: ['tenants'] }),
  });

  const tierColor: Record<string, string> = {
    Basic: 'bg-gray-100 text-gray-600',
    Pro: 'bg-blue-100 text-blue-700',
    Enterprise: 'bg-violet-100 text-violet-700',
  };

  return (
    <Layout>
      <div className="p-8">
        <div className="flex items-center justify-between mb-8">
          <div>
            <h1 className="text-2xl font-bold text-gray-900">Tenants</h1>
            <p className="text-gray-500 mt-1">Manage organizations using SalesAnalyzer</p>
          </div>
          <button
            onClick={() => setShowForm(true)}
            className="flex items-center gap-2 bg-blue-600 hover:bg-blue-700 text-white px-4 py-2.5 rounded-lg text-sm font-medium transition-colors"
          >
            <Plus size={16} /> Add Tenant
          </button>
        </div>

        {showForm && (
          <div className="bg-white rounded-xl shadow-sm border border-gray-100 p-6 mb-6">
            <h2 className="text-base font-semibold text-gray-900 mb-4">New Tenant</h2>
            <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Company Name</label>
                <input
                  value={form.companyName}
                  onChange={(e) => setForm({ ...form, companyName: e.target.value })}
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                  placeholder="Acme Corp"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Subscription Tier</label>
                <select
                  value={form.subscriptionTier}
                  onChange={(e) => setForm({ ...form, subscriptionTier: e.target.value as 'Basic' | 'Pro' | 'Enterprise' })}
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                >
                  {TIERS.map((t) => <option key={t}>{t}</option>)}
                </select>
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Timezone</label>
                <input
                  value={form.timezone}
                  onChange={(e) => setForm({ ...form, timezone: e.target.value })}
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                  placeholder="America/New_York"
                />
              </div>
            </div>
            <div className="flex gap-3 mt-4">
              <button
                onClick={() => createMutation.mutate(form)}
                disabled={!form.companyName || createMutation.isPending}
                className="px-4 py-2 bg-blue-600 hover:bg-blue-700 disabled:bg-gray-300 text-white rounded-lg text-sm font-medium transition-colors"
              >
                {createMutation.isPending ? 'Creating...' : 'Create Tenant'}
              </button>
              <button onClick={() => setShowForm(false)} className="px-4 py-2 border border-gray-200 text-gray-600 rounded-lg text-sm hover:bg-gray-50">
                Cancel
              </button>
            </div>
          </div>
        )}

        {isLoading ? (
          <div className="flex items-center justify-center h-48">
            <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-blue-600" />
          </div>
        ) : (
          <div className="bg-white rounded-xl shadow-sm border border-gray-100 overflow-hidden">
            <table className="w-full text-sm">
              <thead>
                <tr className="bg-gray-50 border-b border-gray-100">
                  <th className="text-left px-6 py-3 font-medium text-gray-500">Company</th>
                  <th className="text-left px-6 py-3 font-medium text-gray-500">Tier</th>
                  <th className="text-left px-6 py-3 font-medium text-gray-500">Timezone</th>
                  <th className="text-left px-6 py-3 font-medium text-gray-500">Created</th>
                  <th className="px-6 py-3" />
                </tr>
              </thead>
              <tbody>
                {tenants.map((t) => (
                  <tr key={t.tenantId} className="border-b border-gray-50 hover:bg-gray-50 transition-colors">
                    <td className="px-6 py-4">
                      <div className="flex items-center gap-3">
                        <div className="w-8 h-8 bg-blue-100 rounded-lg flex items-center justify-center">
                          <Building2 size={16} className="text-blue-600" />
                        </div>
                        <span className="font-medium text-gray-900">{t.companyName}</span>
                      </div>
                    </td>
                    <td className="px-6 py-4">
                      <span className={`px-2.5 py-1 rounded-full text-xs font-medium ${tierColor[t.subscriptionTier] ?? 'bg-gray-100 text-gray-600'}`}>
                        {t.subscriptionTier}
                      </span>
                    </td>
                    <td className="px-6 py-4 text-gray-600">{t.timezone}</td>
                    <td className="px-6 py-4 text-gray-400">{new Date(t.createdAt).toLocaleDateString()}</td>
                    <td className="px-6 py-4 text-right">
                      <button
                        onClick={() => { if (confirm('Delete this tenant?')) deleteMutation.mutate(t.tenantId); }}
                        className="text-gray-400 hover:text-red-500 transition-colors"
                      >
                        <Trash2 size={16} />
                      </button>
                    </td>
                  </tr>
                ))}
                {tenants.length === 0 && (
                  <tr><td colSpan={5} className="px-6 py-12 text-center text-gray-400">No tenants yet. Create one above.</td></tr>
                )}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </Layout>
  );
}
