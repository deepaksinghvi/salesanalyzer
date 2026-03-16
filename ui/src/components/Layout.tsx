import { Link, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import {
  LayoutDashboard, Upload, Building2, Users, LogOut, BarChart3, ChevronRight
} from 'lucide-react';

const navItems = [
  { label: 'Dashboard', icon: LayoutDashboard, path: '/dashboard' },
  { label: 'Upload Data', icon: Upload, path: '/upload' },
  { label: 'Tenants', icon: Building2, path: '/tenants', roles: ['SuperAdmin'] },
  { label: 'Users', icon: Users, path: '/users', roles: ['SuperAdmin', 'Admin'] },
];

export default function Layout({ children }: { children: React.ReactNode }) {
  const { user, logout } = useAuth();
  const location = useLocation();
  const navigate = useNavigate();

  const handleLogout = () => {
    logout();
    navigate('/login');
  };

  const visibleNav = navItems.filter(
    (item) => !item.roles || item.roles.includes(user?.role ?? '')
  );

  return (
    <div className="flex h-screen bg-gray-50">
      {/* Sidebar */}
      <aside className="w-64 bg-slate-900 flex flex-col">
        <div className="px-6 py-5 border-b border-slate-700">
          <div className="flex items-center gap-2">
            <BarChart3 className="text-blue-400" size={24} />
            <span className="text-white font-bold text-lg">SalesAnalyzer</span>
          </div>
          <p className="text-slate-400 text-xs mt-1">by qcom</p>
        </div>

        <nav className="flex-1 px-3 py-4 space-y-1">
          {visibleNav.map(({ label, icon: Icon, path }) => {
            const active = location.pathname.startsWith(path);
            return (
              <Link
                key={path}
                to={path}
                className={`flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm font-medium transition-colors ${
                  active
                    ? 'bg-blue-600 text-white'
                    : 'text-slate-300 hover:bg-slate-800 hover:text-white'
                }`}
              >
                <Icon size={18} />
                {label}
                {active && <ChevronRight size={14} className="ml-auto" />}
              </Link>
            );
          })}
        </nav>

        <div className="px-3 py-4 border-t border-slate-700">
          <div className="px-3 py-2 mb-2">
            <p className="text-slate-300 text-sm font-medium truncate">{user?.email}</p>
            <span className="inline-block text-xs px-2 py-0.5 bg-blue-500/20 text-blue-400 rounded-full mt-1">
              {user?.role}
            </span>
          </div>
          <button
            onClick={handleLogout}
            className="flex items-center gap-3 w-full px-3 py-2.5 rounded-lg text-sm font-medium text-slate-300 hover:bg-slate-800 hover:text-white transition-colors"
          >
            <LogOut size={18} />
            Sign Out
          </button>
        </div>
      </aside>

      {/* Main content */}
      <main className="flex-1 overflow-auto">
        {children}
      </main>
    </div>
  );
}
