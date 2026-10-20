import { useState } from 'react';
import { useLocation, useNavigate, Outlet } from 'react-router-dom';
import {
  LayoutDashboard, Upload, FlaskConical, ClipboardCheck, Cpu,
} from 'lucide-react';
import { SidebarShell, TopBar } from './shared/AppShell';

const NAV = [
  { path: '/controller/dashboard', icon: LayoutDashboard, label: 'Dashboard' },
  { path: '/controller/upload',    icon: Upload,          label: 'Upload Docs' },
  { path: '/controller/testing',   icon: FlaskConical,    label: 'Testing' },
  { path: '/controller/approval',  icon: ClipboardCheck,  label: 'Request & Approval' },
  { path: '/controller/models',    icon: Cpu,             label: 'Model Dashboard' },
];

const PAGE_LABELS = {
  '/controller/dashboard': 'Controller Dashboard',
  '/controller/upload':    'Upload Documents & Ingest',
  '/controller/testing':   'Model Testing',
  '/controller/approval':  'Ingestion Queue & Approval',
  '/controller/models':    'Model Dashboard & Resources',
};

export function ControllerLayout({ children }) {
  const [collapsed, setCollapsed] = useState(false);
  const location = useLocation();
  const navigate = useNavigate();

  const handleNav = (item) => {
    if (item.path) {
      navigate(item.path);
    }
  };

  const currentLabel = PAGE_LABELS[location.pathname] || 'Controller Console';

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
        role="controller"
        collapsed={collapsed}
        setCollapsed={setCollapsed}
        activePath={location.pathname}
        onNavClick={handleNav}
        nav={NAV}
      />
      <div style={{ flex: 1, display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
        <TopBar
          role="controller"
          pageLabel={currentLabel}
        />
        <main style={{ flex: 1, overflowY: 'auto' }}>
          {children || <Outlet />}
        </main>
      </div>
    </div>
  );
}
