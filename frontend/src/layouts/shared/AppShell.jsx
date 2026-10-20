import { useState, useRef, useEffect } from 'react';
import {
  Gem, LogOut, ChevronLeft, ChevronRight, Search, Bell,
  CheckCheck, Clock,
} from 'lucide-react';
import { ROLE_CONFIG } from './roleConfig';
import { useAuth } from '../../contexts/AuthContext';
import { useNotifications } from '../../contexts/NotificationContext';

export function OmRagBrand({ size = 28 }) {
  return (
    <div style={{
      width: size,
      height: size,
      borderRadius: 8,
      flexShrink: 0,
      background: 'linear-gradient(135deg, #7c3aed 0%, #a855f7 50%, #6366f1 100%)',
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      boxShadow: '0 2px 8px rgba(124,58,237,0.35)',
    }}>
      <Gem size={size * 0.55} color="#fff" strokeWidth={2.2} />
    </div>
  );
}

export function SidebarShell({
  role,
  collapsed,
  setCollapsed,
  activePath,
  onNavClick,
  nav,
}) {
  const { logout } = useAuth();
  const theme = ROLE_CONFIG[role] || ROLE_CONFIG.user;
  const w = collapsed ? 62 : 230;

  return (
    <aside style={{
      width: w,
      minWidth: w,
      background: 'var(--bg-panel, #0d0d15)',
      borderRight: '1px solid var(--border, rgba(255,255,255,0.07))',
      display: 'flex',
      flexDirection: 'column',
      transition: 'width 0.25s',
      overflow: 'hidden',
    }}>
      {/* Branding */}
      <div style={{
        height: 60,
        flexShrink: 0,
        padding: collapsed ? '0' : '0 16px',
        display: 'flex',
        alignItems: 'center',
        justifyContent: collapsed ? 'center' : 'space-between',
        borderBottom: '1px solid var(--border, rgba(255,255,255,0.07))',
      }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 9 }}>
          <OmRagBrand collapsed={collapsed} />
          {!collapsed && (
            <span style={{ fontWeight: 800, fontSize: 15, whiteSpace: 'nowrap', letterSpacing: '0.02em', color: '#f1f5f9' }}>
              OMNI-RAG
            </span>
          )}
        </div>
        {!collapsed && (
          <button type="button" onClick={() => setCollapsed(true)} style={iconBtn} aria-label="Collapse sidebar">
            <ChevronLeft size={15} color="var(--text-faint, #64748b)" />
          </button>
        )}
      </div>

      {collapsed && (
        <button
          type="button"
          onClick={() => setCollapsed(false)}
          style={{ ...iconBtn, margin: '8px auto' }}
          aria-label="Expand sidebar"
        >
          <ChevronRight size={15} color="var(--text-faint, #64748b)" />
        </button>
      )}

      {/* Role panel badge */}
      <div style={{
        padding: collapsed ? '12px 8px' : '12px 14px',
        borderBottom: '1px solid var(--border, rgba(255,255,255,0.07))',
        background: 'rgba(255,255,255,0.02)',
      }}>
        <div style={{
          display: 'flex',
          alignItems: 'center',
          gap: 10,
          justifyContent: collapsed ? 'center' : 'flex-start',
        }}>
          <div style={{
            width: 32,
            height: 32,
            borderRadius: '50%',
            flexShrink: 0,
            background: theme.sidebarAvatarBg,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            fontSize: 13,
            fontWeight: 800,
            color: '#fff',
          }}>
            {theme.sidebarLetter}
          </div>
          {!collapsed && (
            <div style={{ minWidth: 0 }}>
              <div style={{ fontWeight: 700, fontSize: 13, color: '#f1f5f9', whiteSpace: 'nowrap' }}>
                {theme.panelLabel}
              </div>
              <div style={{ fontSize: 11, color: '#64748b', textTransform: 'capitalize' }}>
                {role} Mode
              </div>
            </div>
          )}
        </div>
      </div>

      {/* Navigation Links */}
      <nav style={{ flex: 1, padding: '10px 8px', display: 'flex', flexDirection: 'column', gap: 3, overflowY: 'auto' }}>
        {nav.map((item) => {
          const { path, id, icon: Icon, label } = item;
          const target = path || id;
          const active = activePath === target || (activePath && target && activePath.endsWith(target));

          return (
            <button
              key={target || label}
              type="button"
              onClick={() => onNavClick(item)}
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: 10,
                padding: collapsed ? '10px 0' : '9px 12px',
                justifyContent: collapsed ? 'center' : 'flex-start',
                borderRadius: 10,
                border: 'none',
                width: '100%',
                background: active ? 'linear-gradient(135deg, rgba(99,102,241,0.25), rgba(168,85,247,0.25))' : 'transparent',
                borderLeft: active ? '3px solid #a855f7' : '3px solid transparent',
                color: active ? '#f1f5f9' : 'var(--text-faint, #94a3b8)',
                cursor: 'pointer',
                fontSize: 13,
                fontWeight: active ? 700 : 500,
                transition: 'all 0.15s',
                textAlign: 'left',
              }}
            >
              <Icon size={17} color={active ? '#c084fc' : 'var(--text-faint, #64748b)'} strokeWidth={active ? 2.4 : 2} />
              {!collapsed && <span>{label}</span>}
            </button>
          );
        })}
      </nav>

      {/* Sidebar logout */}
      <div style={{ padding: '10px 8px', borderTop: '1px solid var(--border, rgba(255,255,255,0.07))' }}>
        <button
          type="button"
          onClick={logout}
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: 10,
            padding: collapsed ? '10px 0' : '9px 12px',
            justifyContent: collapsed ? 'center' : 'flex-start',
            borderRadius: 10,
            border: 'none',
            width: '100%',
            background: 'transparent',
            color: 'var(--text-faint, #64748b)',
            cursor: 'pointer',
            fontSize: 13,
            transition: 'color 0.15s',
          }}
          onMouseEnter={(e) => { e.currentTarget.style.color = '#ef4444'; }}
          onMouseLeave={(e) => { e.currentTarget.style.color = 'var(--text-faint, #64748b)'; }}
        >
          <LogOut size={17} />
          {!collapsed && 'Sign Out'}
        </button>
      </div>
    </aside>
  );
}

export function TopBar({ role, pageLabel }) {
  const { user, logout } = useAuth();
  const { unreadCount, notifications, isOpen, toggleDropdown, setIsOpen, markAsRead, markAllAsRead } = useNotifications();
  const theme = ROLE_CONFIG[role] || ROLE_CONFIG.user;

  const [profileDropdownOpen, setProfileDropdownOpen] = useState(false);
  const notifRef = useRef(null);
  const profileRef = useRef(null);

  useEffect(() => {
    const handleOutside = (e) => {
      if (notifRef.current && !notifRef.current.contains(e.target)) {
        setIsOpen(false);
      }
      if (profileRef.current && !profileRef.current.contains(e.target)) {
        setProfileDropdownOpen(false);
      }
    };
    document.addEventListener('mousedown', handleOutside);
    return () => document.removeEventListener('mousedown', handleOutside);
  }, [setIsOpen]);

  const displayName = user?.username || theme.dropdownName;
  const userInitials = displayName ? displayName.slice(0, 2).toUpperCase() : theme.topAvatarInitials;

  return (
    <header style={{
      height: 60,
      flexShrink: 0,
      background: 'var(--bg-panel, #0d0d15)',
      borderBottom: '1px solid var(--border, rgba(255,255,255,0.07))',
      display: 'flex',
      alignItems: 'center',
      padding: '0 24px',
      gap: 16,
      position: 'relative',
      zIndex: 50,
    }}>
      {pageLabel && (
        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          <span style={{ fontWeight: 700, fontSize: 15, color: 'var(--text-primary, #f1f5f9)' }}>
            {pageLabel}
          </span>
          <span style={{
            fontSize: 10,
            padding: '2px 8px',
            borderRadius: 99,
            background: 'rgba(99,102,241,0.12)',
            color: '#a5b4fc',
            border: '1px solid rgba(99,102,241,0.3)',
            fontWeight: 600,
            textTransform: 'uppercase',
          }}>
            Live
          </span>
        </div>
      )}

      <div style={{ flex: 1 }} />

      {/* Search Bar */}
      <div style={{
        display: 'flex',
        alignItems: 'center',
        gap: 8,
        background: 'rgba(255,255,255,0.04)',
        border: '1px solid var(--border, rgba(255,255,255,0.07))',
        borderRadius: 10,
        padding: '6px 14px',
      }}>
        <Search size={14} color="var(--text-faint, #64748b)" />
        <input
          placeholder="Search documents, jobs..."
          style={{
            background: 'none',
            border: 'none',
            outline: 'none',
            color: 'var(--text-muted, #94a3b8)',
            fontSize: 13,
            width: 170,
          }}
        />
      </div>

      {/* Notifications Button & Dropdown */}
      <div ref={notifRef} style={{ position: 'relative' }}>
        <button
          type="button"
          onClick={toggleDropdown}
          style={{ ...iconBtn, position: 'relative', width: 36, height: 36 }}
          aria-label="Notifications"
        >
          <Bell size={18} color={isOpen ? '#a855f7' : 'var(--text-faint, #94a3b8)'} />
          {unreadCount > 0 && (
            <span style={{
              position: 'absolute',
              top: 4,
              right: 4,
              minWidth: 16,
              height: 16,
              borderRadius: 8,
              background: '#ef4444',
              color: '#fff',
              fontSize: 10,
              fontWeight: 800,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              padding: '0 3px',
              border: '2px solid #0d0d15',
            }}>
              {unreadCount > 99 ? '99+' : unreadCount}
            </span>
          )}
        </button>

        {isOpen && (
          <div style={{
            position: 'absolute',
            top: 'calc(100% + 8px)',
            right: 0,
            width: 320,
            background: '#161926',
            border: '1px solid rgba(255,255,255,0.1)',
            borderRadius: 14,
            boxShadow: '0 12px 36px rgba(0,0,0,0.5)',
            zIndex: 100,
            overflow: 'hidden',
          }}>
            <div style={{
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'center',
              padding: '12px 16px',
              borderBottom: '1px solid rgba(255,255,255,0.08)',
            }}>
              <span style={{ fontWeight: 700, fontSize: 13, color: '#f1f5f9' }}>
                Notifications ({unreadCount} unread)
              </span>
              {unreadCount > 0 && (
                <button
                  type="button"
                  onClick={markAllAsRead}
                  style={{
                    background: 'none',
                    border: 'none',
                    color: '#a5b4fc',
                    fontSize: 11,
                    cursor: 'pointer',
                    display: 'flex',
                    alignItems: 'center',
                    gap: 4,
                  }}
                >
                  <CheckCheck size={13} /> Mark all read
                </button>
              )}
            </div>

            <div style={{ maxHeight: 300, overflowY: 'auto' }}>
              {notifications.length === 0 ? (
                <div style={{ padding: '24px 16px', textAlign: 'center', color: '#64748b', fontSize: 12 }}>
                  No recent notifications
                </div>
              ) : (
                notifications.map((n) => (
                  <div
                    key={n.id}
                    onClick={() => !n.read && markAsRead(n.id)}
                    style={{
                      padding: '10px 14px',
                      borderBottom: '1px solid rgba(255,255,255,0.05)',
                      background: n.read ? 'transparent' : 'rgba(99,102,241,0.06)',
                      cursor: n.read ? 'default' : 'pointer',
                      transition: 'background 0.15s',
                    }}
                  >
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
                      <span style={{ fontWeight: n.read ? 500 : 700, fontSize: 12, color: '#f1f5f9' }}>
                        {n.title || 'System Notification'}
                      </span>
                      {!n.read && (
                        <span style={{ width: 6, height: 6, borderRadius: '50%', background: '#7c3aed', marginTop: 4 }} />
                      )}
                    </div>
                    <p style={{ margin: '3px 0 0', fontSize: 11, color: '#94a3b8', lineHeight: 1.4 }}>
                      {n.message}
                    </p>
                    {n.createdAt && (
                      <span style={{ fontSize: 10, color: '#475569', display: 'flex', alignItems: 'center', gap: 4, marginTop: 4 }}>
                        <Clock size={10} /> {new Date(n.createdAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                      </span>
                    )}
                  </div>
                ))
              )}
            </div>
          </div>
        )}
      </div>

      {/* User Avatar & Dropdown */}
      <div ref={profileRef} style={{ position: 'relative' }}>
        <button
          type="button"
          onClick={() => setProfileDropdownOpen((o) => !o)}
          aria-expanded={profileDropdownOpen}
          aria-haspopup="true"
          style={{
            height: 36,
            borderRadius: 10,
            border: '1px solid var(--border, rgba(255,255,255,0.08))',
            padding: '3px 10px 3px 4px',
            cursor: 'pointer',
            background: 'rgba(255,255,255,0.04)',
            display: 'flex',
            alignItems: 'center',
            gap: 8,
          }}
        >
          <div style={{
            width: 28,
            height: 28,
            borderRadius: '50%',
            background: theme.topAvatarBg,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            fontSize: 11,
            fontWeight: 800,
            color: '#fff',
          }}>
            {userInitials}
          </div>
          <span style={{ fontSize: 12, fontWeight: 600, color: '#f1f5f9' }}>
            {displayName}
          </span>
        </button>

        {profileDropdownOpen && (
          <div style={{
            position: 'absolute',
            top: 'calc(100% + 8px)',
            right: 0,
            width: 230,
            background: '#161926',
            border: '1px solid rgba(255,255,255,0.1)',
            borderRadius: 12,
            boxShadow: '0 8px 32px rgba(0,0,0,0.5)',
            padding: '8px 0',
            zIndex: 100,
          }}>
            <div style={{
              display: 'flex',
              alignItems: 'center',
              gap: 12,
              padding: '10px 14px',
              borderBottom: '1px solid rgba(255,255,255,0.08)',
              marginBottom: 4,
            }}>
              <div style={{
                width: 34,
                height: 34,
                borderRadius: '50%',
                background: theme.topAvatarBg,
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                fontSize: 13,
                fontWeight: 800,
                color: '#fff',
                flexShrink: 0,
              }}>
                {userInitials}
              </div>
              <div style={{ minWidth: 0 }}>
                <div style={{ fontWeight: 700, fontSize: 13, color: '#f1f5f9', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                  {displayName}
                </div>
                <div style={{ fontSize: 11, color: '#64748b', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                  {user?.email || `${role}@omnirag.local`}
                </div>
              </div>
            </div>

            <div style={{ padding: '6px 14px', fontSize: 11, color: '#94a3b8' }}>
              Role: <strong style={{ color: '#a5b4fc', textTransform: 'capitalize' }}>{role}</strong>
            </div>

            <div style={{ height: 1, background: 'rgba(255,255,255,0.08)', margin: '6px 0' }} />

            <button
              type="button"
              onClick={() => {
                setProfileDropdownOpen(false);
                logout();
              }}
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: 10,
                width: '100%',
                padding: '9px 14px',
                border: 'none',
                background: 'transparent',
                color: '#ef4444',
                fontSize: 13,
                fontWeight: 600,
                cursor: 'pointer',
                textAlign: 'left',
              }}
            >
              <LogOut size={15} color="#ef4444" />
              Sign Out
            </button>
          </div>
        )}
      </div>
    </header>
  );
}

const iconBtn = {
  background: 'none',
  border: 'none',
  cursor: 'pointer',
  padding: 6,
  borderRadius: 8,
  display: 'flex',
  alignItems: 'center',
  justifyContent: 'center',
};
