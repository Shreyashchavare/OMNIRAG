import { useState } from 'react';
import { useLocation, useNavigate, Outlet } from 'react-router-dom';
import {
  LayoutDashboard, Users, BarChart3, FileCheck,
} from 'lucide-react';
import { SidebarShell, TopBar } from './shared/AppShell';

const NAV = [
  { path: '/admin/dashboard', icon: LayoutDashboard, label: 'Dashboard' },
  { path: '/admin/documents', icon: FileCheck,       label: 'Approval Queue' },
  { path: '/admin/analytics', icon: BarChart3,       label: 'Analytics' },
  { path: '/admin/users',     icon: Users,           label: 'User Management' },
];

const PAGE_LABELS = {
  '/admin/dashboard': 'Admin Dashboard',
  '/admin/documents': 'Approval & Ingestion Queue',
  '/admin/analytics': 'System Analytics',
  '/admin/users':     'User Management',
};

export function AdminLayout({ children }) {
  const [collapsed, setCollapsed] = useState(false);
  const location = useLocation();
  const navigate = useNavigate();

  const handleNav = (item) => {
    if (item.path) {
      navigate(item.path);
    }
  };

  const currentLabel = PAGE_LABELS[location.pathname] || 'Admin Console';

  return (
    <div style={{
      display: 'flex',
      height: '100vh',
      background: 'var(--bg-surface, #0a0a0f)',
      color: 'var(--text-primary, #f1f5f9)',
      fontFamily: 'var(--font, sans-serif)',
      overflow: 'hidden',
    }}>
      <SidebarShell
        role="admin"
        collapsed={collapsed}
        setCollapsed={setCollapsed}
        activePath={location.pathname}
        onNavClick={handleNav}
        nav={NAV}
      />
      <div style={{ flex: 1, display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
        <TopBar
          role="admin"
          pageLabel={currentLabel}
        />
        <main style={{ flex: 1, overflowY: 'auto' }}>
          {children || <Outlet />}
        </main>
      </div>
    </div>
  );
}
