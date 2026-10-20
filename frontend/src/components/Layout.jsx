// Legacy shared layout — mirrors AppShell branding for any legacy usage.
import {
  Home, Upload, MessageSquare, FileText, BarChart2,
} from 'lucide-react';
import { SidebarShell, TopBar as AppTopBar } from '../layouts/shared/AppShell';

const NAV = [
  { id: 'dashboard', icon: Home,          label: 'Dashboard' },
  { id: 'upload',    icon: Upload,        label: 'Upload' },
  { id: 'chat',      icon: MessageSquare, label: 'Chat' },
  { id: 'documents', icon: FileText,      label: 'Documents' },
  { id: 'analytics', icon: BarChart2,     label: 'Analytics' },
];

const PAGE_LABELS = {
  dashboard: 'Dashboard',
  upload:    'Upload Documents',
  chat:      'Chat with Documents',
  documents: 'Document Viewer',
  analytics: 'Analytics',
};

export function Sidebar({ page, setPage, collapsed, setCollapsed, onLogout }) {
  return (
    <SidebarShell
      role="controller"
      collapsed={collapsed}
      setCollapsed={setCollapsed}
      activePage={page}
      onNavClick={(item) => setPage(item.id)}
      onLogout={onLogout}
      nav={NAV}
    />
  );
}

export function TopBar({ page, onLogout = () => {} }) {
  return (
    <AppTopBar
      role="controller"
      pageLabel={PAGE_LABELS[page]}
      onLogout={onLogout}
    />
  );
}
