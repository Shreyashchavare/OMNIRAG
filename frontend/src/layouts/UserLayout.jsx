import { useState } from 'react';
import { useLocation, useNavigate, Outlet } from 'react-router-dom';
import {
  MessageSquarePlus, History, HelpCircle,
} from 'lucide-react';
import { SidebarShell, TopBar } from './shared/AppShell';

const NAV = [
  { path: '/user/chat',    icon: MessageSquarePlus, label: 'Search / Query' },
  { path: '/user/history', icon: History,           label: 'Chat History' },
  { path: '/user/help',    icon: HelpCircle,        label: 'Help & Docs' },
];

const PAGE_LABELS = {
  '/user/chat':    'Knowledge Search & Chat',
  '/user/history': 'Conversation History',
  '/user/help':    'Help & System Documentation',
};

export function UserLayout({ children }) {
  const [collapsed, setCollapsed] = useState(false);
  const location = useLocation();
  const navigate = useNavigate();

  const handleNav = (item) => {
    if (item.path) {
      navigate(item.path);
    }
  };

  const currentLabel = PAGE_LABELS[location.pathname] || 'User Workspace';

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
        role="user"
        collapsed={collapsed}
        setCollapsed={setCollapsed}
        activePath={location.pathname}
        onNavClick={handleNav}
        nav={NAV}
      />
      <div style={{ flex: 1, display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
        <TopBar
          role="user"
          pageLabel={currentLabel}
        />
        <main style={{ flex: 1, overflow: 'hidden', display: 'flex', flexDirection: 'column' }}>
          {children || <Outlet />}
        </main>
      </div>
    </div>
  );
}
