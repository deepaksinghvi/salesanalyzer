import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { getAllUsers, getUsersByTenant, createUser, deactivateUser, resetPassword } from '../api/users';
import { getTenants } from '../api/tenants';
import { useAuth } from '../context/AuthContext';
import Layout from '../components/Layout';
import type { User, Tenant } from '../types';
import { Plus, UserX, UserCheck, KeyRound } from 'lucide-react';

const ROLES = ['Admin', 'Viewer'];

export default function UsersPage() {
  const { user: authUser } = useAuth();
  const qc = useQueryClient();
  const [showForm, setShowForm] = useState(false);
  const [selectedTenantId, setSelectedTenantId] = useState(authUser?.tenantId ?? '');
  const [form, setForm] = useState({
    email: '', password: '', role: 'Viewer', firstName: '', lastName: '', tenantId: authUser?.tenantId ?? '',
  });
  const [resetTarget, setResetTarget] = useState<User | null>(null);
  const [newPassword, setNewPassword] = useState('');
  const [resetSuccess, setResetSuccess] = useState(false);

  const isSuperAdmin = authUser?.role === 'SuperAdmin';

  const { data: tenants = [] } = useQuery<Tenant[]>({
    queryKey: ['tenants'],
    queryFn: getTenants,
    enabled: isSuperAdmin,
  });

  const { data: users = [], isLoading } = useQuery<User[]>({
    queryKey: ['users', selectedTenantId],
    queryFn: () => selectedTenantId ? getUsersByTenant(selectedTenantId) : getAllUsers(),
    enabled: isSuperAdmin || !!selectedTenantId,
  });

  const createMutation = useMutation({
    mutationFn: createUser,
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['users', selectedTenantId] });
      setShowForm(false);
      setForm({ email: '', password: '', role: 'Viewer', firstName: '', lastName: '', tenantId: selectedTenantId });
    },
  });

  const deactivateMutation = useMutation({
    mutationFn: deactivateUser,
    onSuccess: () => qc.invalidateQueries({ queryKey: ['users', selectedTenantId] }),
  });

  const resetPasswordMutation = useMutation({
    mutationFn: ({ userId, password }: { userId: string; password: string }) =>
      resetPassword(userId, password),
    onSuccess: () => {
      setResetSuccess(true);
      setNewPassword('');
      setTimeout(() => {
        setResetTarget(null);
        setResetSuccess(false);
      }, 1500);
    },
  });

  const roleColor: Record<string, string> = {
    SuperAdmin: 'bg-red-100 text-red-700',
    Admin: 'bg-blue-100 text-blue-700',
    Viewer: 'bg-gray-100 text-gray-600',
  };

  return (
    <Layout>
      <div className="p-8">
        <div className="flex items-center justify-between mb-8">
          <div>
            <h1 className="text-2xl font-bold text-gray-900">Users</h1>
            <p className="text-gray-500 mt-1">Manage users within organizations</p>
          </div>
          <button
            onClick={() => setShowForm(true)}
            className="flex items-center gap-2 bg-blue-600 hover:bg-blue-700 text-white px-4 py-2.5 rounded-lg text-sm font-medium transition-colors"
          >
            <Plus size={16} /> Add User
          </button>
        </div>

        {isSuperAdmin && (
          <div className="mb-6">
            <label className="block text-sm font-medium text-gray-700 mb-1">Filter by Tenant</label>
            <select
              value={selectedTenantId}
              onChange={(e) => setSelectedTenantId(e.target.value)}
              className="px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
            >
              <option value="">All tenants</option>
              {tenants.map((t) => (
                <option key={t.tenantId} value={t.tenantId}>{t.companyName}</option>
              ))}
            </select>
          </div>
        )}

        {showForm && (
          <div className="bg-white rounded-xl shadow-sm border border-gray-100 p-6 mb-6">
            <h2 className="text-base font-semibold text-gray-900 mb-4">New User</h2>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              {isSuperAdmin && (
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">Tenant</label>
                  <select
                    value={form.tenantId}
                    onChange={(e) => setForm({ ...form, tenantId: e.target.value })}
                    className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                  >
                    <option value="">Select tenant...</option>
                    {tenants.map((t) => <option key={t.tenantId} value={t.tenantId}>{t.companyName}</option>)}
                  </select>
                </div>
              )}
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Email</label>
                <input
                  type="email"
                  value={form.email}
                  onChange={(e) => setForm({ ...form, email: e.target.value })}
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                  placeholder="user@company.com"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Password</label>
                <input
                  type="password"
                  value={form.password}
                  onChange={(e) => setForm({ ...form, password: e.target.value })}
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Role</label>
                <select
                  value={form.role}
                  onChange={(e) => setForm({ ...form, role: e.target.value })}
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                >
                  {ROLES.map((r) => <option key={r}>{r}</option>)}
                </select>
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">First Name</label>
                <input
                  value={form.firstName}
                  onChange={(e) => setForm({ ...form, firstName: e.target.value })}
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Last Name</label>
                <input
                  value={form.lastName}
                  onChange={(e) => setForm({ ...form, lastName: e.target.value })}
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                />
              </div>
            </div>
            <div className="flex gap-3 mt-4">
              <button
                onClick={() => createMutation.mutate(form)}
                disabled={!form.email || !form.password || createMutation.isPending}
                className="px-4 py-2 bg-blue-600 hover:bg-blue-700 disabled:bg-gray-300 text-white rounded-lg text-sm font-medium transition-colors"
              >
                {createMutation.isPending ? 'Creating...' : 'Create User'}
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
                  <th className="text-left px-6 py-3 font-medium text-gray-500">User</th>
                  {isSuperAdmin && <th className="text-left px-6 py-3 font-medium text-gray-500">Tenant</th>}
                  <th className="text-left px-6 py-3 font-medium text-gray-500">Role</th>
                  <th className="text-left px-6 py-3 font-medium text-gray-500">Status</th>
                  <th className="text-left px-6 py-3 font-medium text-gray-500">Created</th>
                  <th className="px-6 py-3" />
                </tr>
              </thead>
              <tbody>
                {users.map((u) => (
                  <tr key={u.userId} className="border-b border-gray-50 hover:bg-gray-50 transition-colors">
                    <td className="px-6 py-4">
                      <p className="font-medium text-gray-900">{u.firstName} {u.lastName}</p>
                      <p className="text-gray-400 text-xs">{u.email}</p>
                    </td>
                    {isSuperAdmin && (
                      <td className="px-6 py-4 text-sm text-gray-600">
                        {tenants.find(t => t.tenantId === u.tenantId)?.companyName ?? u.tenantId.slice(0, 8)}
                      </td>
                    )}
                    <td className="px-6 py-4">
                      <span className={`px-2.5 py-1 rounded-full text-xs font-medium ${roleColor[u.role] ?? 'bg-gray-100 text-gray-600'}`}>
                        {u.role}
                      </span>
                    </td>
                    <td className="px-6 py-4">
                      {u.active
                        ? <span className="flex items-center gap-1 text-emerald-600 text-xs font-medium"><UserCheck size={14}/> Active</span>
                        : <span className="flex items-center gap-1 text-red-500 text-xs font-medium"><UserX size={14}/> Inactive</span>
                      }
                    </td>
                    <td className="px-6 py-4 text-gray-400">{new Date(u.createdAt).toLocaleDateString()}</td>
                    <td className="px-6 py-4 text-right">
                      <div className="flex items-center justify-end gap-3">
                        {isSuperAdmin && (
                          <button
                            onClick={() => { setResetTarget(u); setNewPassword(''); setResetSuccess(false); }}
                            className="text-gray-400 hover:text-blue-500 transition-colors"
                            title="Reset Password"
                          >
                            <KeyRound size={16} />
                          </button>
                        )}
                        {u.active && (
                          <button
                            onClick={() => { if (confirm('Deactivate this user?')) deactivateMutation.mutate(u.userId); }}
                            className="text-gray-400 hover:text-red-500 transition-colors"
                            title="Deactivate"
                          >
                            <UserX size={16} />
                          </button>
                        )}
                      </div>
                    </td>
                  </tr>
                ))}
                {users.length === 0 && (
                  <tr><td colSpan={5} className="px-6 py-12 text-center text-gray-400">No users found.</td></tr>
                )}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {resetTarget && (
        <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50">
          <div className="bg-white rounded-2xl shadow-2xl p-6 w-full max-w-sm mx-4">
            <h2 className="text-base font-semibold text-gray-900 mb-1">Reset Password</h2>
            <p className="text-sm text-gray-500 mb-4">
              Set a new password for <span className="font-medium text-gray-700">{resetTarget.email}</span>
            </p>

            {resetSuccess ? (
              <div className="bg-emerald-50 border border-emerald-200 text-emerald-700 text-sm rounded-lg px-4 py-3 text-center font-medium">
                Password updated successfully!
              </div>
            ) : (
              <>
                {resetPasswordMutation.isError && (
                  <div className="bg-red-50 border border-red-200 text-red-700 text-sm rounded-lg px-4 py-3 mb-3">
                    Failed to reset password. Please try again.
                  </div>
                )}
                <input
                  type="password"
                  placeholder="New password"
                  value={newPassword}
                  onChange={(e) => setNewPassword(e.target.value)}
                  className="w-full px-3 py-2.5 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 mb-4"
                  autoFocus
                />
                <div className="flex gap-3">
                  <button
                    onClick={() => resetPasswordMutation.mutate({ userId: resetTarget.userId, password: newPassword })}
                    disabled={!newPassword || resetPasswordMutation.isPending}
                    className="flex-1 bg-blue-600 hover:bg-blue-700 disabled:bg-gray-300 text-white text-sm font-medium py-2.5 rounded-lg transition-colors"
                  >
                    {resetPasswordMutation.isPending ? 'Saving...' : 'Update Password'}
                  </button>
                  <button
                    onClick={() => setResetTarget(null)}
                    className="flex-1 border border-gray-200 text-gray-600 text-sm rounded-lg py-2.5 hover:bg-gray-50 transition-colors"
                  >
                    Cancel
                  </button>
                </div>
              </>
            )}
          </div>
        </div>
      )}
    </Layout>
  );
}
