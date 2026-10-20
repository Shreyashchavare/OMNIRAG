import { useState } from 'react';
import {
  Search, Lock, Unlock, AlertCircle, Info,
  Check, Loader2, Key,
} from 'lucide-react';
import { userApi } from '../../api/services/userApi';
import { extractErrorMessage } from '../../api/client';
import { users as sampleUsers } from '../../data/mockData';

export default function UserManagement() {
  const [lookupId, setLookupId] = useState('');
  const [liveUser, setLiveUser] = useState(null);
  const [loading, setLoading] = useState(false);
  const [actionLoading, setActionLoading] = useState(false);
  const [error, setError] = useState(null);
  const [success, setSuccess] = useState(null);
  const [search, setSearch] = useState('');

  const handleLookup = async (idToSearch) => {
    const id = idToSearch || lookupId;
    if (!id.trim()) {
      setError('Please provide a valid User UUID.');
      return;
    }

    setLoading(true);
    setError(null);
    setSuccess(null);
    setLiveUser(null);

    try {
      const data = await userApi.getUserById(id.trim());
      setLiveUser(data);
    } catch (err) {
      setError(extractErrorMessage(err));
    } finally {
      setLoading(false);
    }
  };

  const handleToggleLock = async () => {
    if (!liveUser) return;
    setActionLoading(true);
    setError(null);
    setSuccess(null);

    const isLocked = liveUser.status === 'LOCKED';
    try {
      if (isLocked) {
        await userApi.unlockUser(liveUser.userId);
        setSuccess(`User ${liveUser.username} unlocked successfully.`);
        setLiveUser((prev) => ({ ...prev, status: 'ACTIVE' }));
      } else {
        await userApi.lockUser(liveUser.userId);
        setSuccess(`User ${liveUser.username} locked successfully.`);
        setLiveUser((prev) => ({ ...prev, status: 'LOCKED' }));
      }
    } catch (err) {
      setError(extractErrorMessage(err));
    } finally {
      setActionLoading(false);
    }
  };

  const filteredSamples = sampleUsers.filter((u) =>
    u.name.toLowerCase().includes(search.toLowerCase()) ||
    u.email.toLowerCase().includes(search.toLowerCase())
  );

  return (
    <div style={wrap}>
      {/* Page Title */}
      <div style={{ marginBottom: 14 }}>
        <h2 style={{ fontWeight: 800, fontSize: 20, color: '#f1f5f9' }}>User Governance &amp; Access Control</h2>
        <p style={{ color: '#64748b', fontSize: 13, marginTop: 3 }}>
          Identity Service user status, lock/unlock operations, and permissions
        </p>
      </div>

      {/* Backend API Status Banner */}
      <div style={infoBox}>
        <Info size={17} color="#38bdf8" style={{ flexShrink: 0, marginTop: 2 }} />
        <div>
          <strong style={{ color: '#e0f2fe', fontSize: 13 }}>Backend Contract Notice: User Directory Listing</strong>
          <p style={{ margin: '4px 0 0', fontSize: 12, color: '#94a3b8', lineHeight: 1.5 }}>
            The backend Identity Service exposes direct single-user operations (<code>GET /api/v1/users/{'{userId}'}</code>, <code>PATCH /lock</code>, <code>PATCH /unlock</code>),
            while a bulk paginated user listing endpoint (<code>GET /api/v1/users</code>) is currently pending backend implementation.
            Use the <strong>Live UUID Lookup</strong> below to inspect and manage users directly against the real Identity Service.
          </p>
        </div>
      </div>

      {/* Direct UUID Lookup Tool */}
      <div style={card}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 12 }}>
          <Key size={16} color="#c084fc" />
          <span style={{ fontWeight: 700, fontSize: 14, color: '#f1f5f9' }}>
            Live Identity Service UUID Lookup &amp; Lock Controls
          </span>
        </div>

        <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap' }}>
          <input
            value={lookupId}
            onChange={(e) => setLookupId(e.target.value)}
            placeholder="Enter User UUID (e.g., 550e8400-e29b-41d4-a716-446655440000)"
            style={inputStyle}
          />
          <button
            type="button"
            onClick={() => handleLookup()}
            disabled={loading}
            style={primaryBtn}
          >
            {loading ? (
              <span style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                <Loader2 size={14} style={{ animation: 'spin 0.8s linear infinite' }} /> Querying...
              </span>
            ) : (
              'Lookup User by UUID'
            )}
          </button>
        </div>

        {error && (
          <div style={errorBox}>
            <AlertCircle size={15} color="#ef4444" style={{ flexShrink: 0 }} />
            <span>{error}</span>
          </div>
        )}

        {success && (
          <div style={successBox}>
            <Check size={15} color="#10b981" style={{ flexShrink: 0 }} />
            <span>{success}</span>
          </div>
        )}

        {/* Live User Result Card */}
        {liveUser && (
          <div style={userResultCard}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', flexWrap: 'wrap', gap: 12 }}>
              <div style={{ display: 'flex', gap: 12, alignItems: 'center' }}>
                <div style={{
                  width: 44,
                  height: 44,
                  borderRadius: '50%',
                  background: 'linear-gradient(135deg, #7c3aed, #6366f1)',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  fontSize: 14,
                  fontWeight: 800,
                  color: '#fff',
                }}>
                  {liveUser.username ? liveUser.username.slice(0, 2).toUpperCase() : 'U'}
                </div>
                <div>
                  <div style={{ fontWeight: 700, fontSize: 15, color: '#f1f5f9' }}>
                    {liveUser.username}
                  </div>
                  <div style={{ fontSize: 12, color: '#94a3b8', marginTop: 2 }}>
                    {liveUser.email} · ID: <code style={{ color: '#a5b4fc' }}>{liveUser.userId}</code>
                  </div>
                </div>
              </div>

              <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                <span style={{
                  fontSize: 11,
                  padding: '3px 10px',
                  borderRadius: 99,
                  fontWeight: 700,
                  background: liveUser.status === 'ACTIVE' ? 'rgba(16,185,129,0.15)' : 'rgba(239,68,68,0.15)',
                  color: liveUser.status === 'ACTIVE' ? '#10b981' : '#ef4444',
                }}>
                  Status: {liveUser.status}
                </span>

                <button
                  type="button"
                  onClick={handleToggleLock}
                  disabled={actionLoading}
                  style={liveUser.status === 'LOCKED' ? unlockBtn : lockBtn}
                >
                  {liveUser.status === 'LOCKED' ? (
                    <>
                      <Unlock size={14} /> Unlock Account
                    </>
                  ) : (
                    <>
                      <Lock size={14} /> Lock Account
                    </>
                  )}
                </button>
              </div>
            </div>

            {liveUser.profile && (
              <div style={{ marginTop: 14, paddingTop: 12, borderTop: '1px solid rgba(255,255,255,0.06)', display: 'flex', gap: 24, fontSize: 12, color: '#94a3b8' }}>
                <span>First Name: <strong style={{ color: '#f1f5f9' }}>{liveUser.profile.firstName || 'N/A'}</strong></span>
                <span>Last Name: <strong style={{ color: '#f1f5f9' }}>{liveUser.profile.lastName || 'N/A'}</strong></span>
                <span>Phone: <strong style={{ color: '#f1f5f9' }}>{liveUser.profile.phoneNumber || 'N/A'}</strong></span>
              </div>
            )}
          </div>
        )}
      </div>

      {/* Reference Directory */}
      <div style={card}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 14, flexWrap: 'wrap', gap: 10 }}>
          <span style={{ fontWeight: 700, fontSize: 14, color: '#f1f5f9' }}>
            Registered System Accounts &amp; Sample Directory
          </span>

          <div style={{
            display: 'flex', alignItems: 'center', gap: 8,
            background: 'rgba(255,255,255,0.04)', border: '1px solid var(--border, rgba(255,255,255,0.08))',
            borderRadius: 8, padding: '6px 12px', minWidth: 240,
          }}>
            <Search size={14} color="#64748b" />
            <input
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder="Filter preview list..."
              style={{ flex: 1, background: 'none', border: 'none', outline: 'none', fontSize: 12, color: '#f1f5f9' }}
            />
          </div>
        </div>

        <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 13 }}>
          <thead>
            <tr>
              {['User', 'Role', 'Status', 'Last Login', 'UUID Action'].map((h) => (
                <th key={h} style={thStyle}>{h}</th>
              ))}
            </tr>
          </thead>
          <tbody>
            {filteredSamples.map((u) => (
              <tr key={u.id}>
                <td style={tdStyle}>
                  <div>
                    <div style={{ fontWeight: 600, color: '#f1f5f9' }}>{u.name}</div>
                    <div style={{ fontSize: 11, color: '#64748b' }}>{u.email}</div>
                  </div>
                </td>
                <td style={tdStyle}>
                  <span style={{
                    fontSize: 10, padding: '2px 8px', borderRadius: 6, fontWeight: 700,
                    background: u.role === 'Admin' ? 'rgba(251,191,36,0.15)' : u.role === 'Controller' ? 'rgba(168,85,247,0.15)' : 'rgba(16,185,129,0.15)',
                    color: u.role === 'Admin' ? '#fbbf24' : u.role === 'Controller' ? '#c084fc' : '#10b981',
                  }}>
                    {u.role}
                  </span>
                </td>
                <td style={tdStyle}>
                  <span style={{ color: u.status === 'Active' ? '#10b981' : '#ef4444', fontWeight: 600, fontSize: 12 }}>
                    {u.status}
                  </span>
                </td>
                <td style={tdStyle}>{u.lastLogin}</td>
                <td style={tdStyle}>
                  <button
                    type="button"
                    onClick={() => {
                      setLookupId(`00000000-0000-0000-0000-00000000000${u.id}`);
                      handleLookup(`00000000-0000-0000-0000-00000000000${u.id}`);
                    }}
                    style={lookupBtn}
                  >
                    Query UUID
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}

const wrap = { padding: '24px 28px', maxWidth: 1240, display: 'flex', flexDirection: 'column', gap: 18 };
const card = { background: 'rgba(255,255,255,0.03)', border: '1px solid rgba(255,255,255,0.07)', borderRadius: 16, padding: '18px 20px' };
const inputStyle = { flex: 1, minWidth: 280, padding: '10px 14px', borderRadius: 10, border: '1px solid rgba(255,255,255,0.1)', background: 'rgba(255,255,255,0.04)', color: '#f1f5f9', fontSize: 13, outline: 'none' };
const primaryBtn = { display: 'flex', alignItems: 'center', gap: 6, padding: '10px 18px', borderRadius: 10, border: 'none', background: 'linear-gradient(135deg,#7c3aed,#6366f1)', color: '#fff', fontWeight: 700, fontSize: 13, cursor: 'pointer' };
const infoBox = { display: 'flex', alignItems: 'flex-start', gap: 10, padding: '12px 16px', borderRadius: 12, background: 'rgba(56,189,248,0.08)', border: '1px solid rgba(56,189,248,0.2)' };
const errorBox = { display: 'flex', alignItems: 'center', gap: 8, padding: '10px 14px', borderRadius: 10, background: 'rgba(239,68,68,0.1)', border: '1px solid rgba(239,68,68,0.3)', color: '#fca5a5', fontSize: 12, marginTop: 12 };
const successBox = { display: 'flex', alignItems: 'center', gap: 8, padding: '10px 14px', borderRadius: 10, background: 'rgba(16,185,129,0.1)', border: '1px solid rgba(16,185,129,0.3)', color: '#86efac', fontSize: 12, marginTop: 12 };
const userResultCard = { marginTop: 16, padding: '16px', borderRadius: 12, background: 'rgba(255,255,255,0.04)', border: '1px solid rgba(168,85,247,0.3)' };
const lockBtn = { display: 'flex', alignItems: 'center', gap: 6, padding: '6px 14px', borderRadius: 8, border: '1px solid rgba(239,68,68,0.3)', background: 'rgba(239,68,68,0.12)', color: '#ef4444', fontWeight: 700, fontSize: 12, cursor: 'pointer' };
const unlockBtn = { display: 'flex', alignItems: 'center', gap: 6, padding: '6px 14px', borderRadius: 8, border: '1px solid rgba(16,185,129,0.3)', background: 'rgba(16,185,129,0.12)', color: '#10b981', fontWeight: 700, fontSize: 12, cursor: 'pointer' };
const lookupBtn = { padding: '4px 10px', borderRadius: 6, border: '1px solid rgba(255,255,255,0.1)', background: 'rgba(255,255,255,0.04)', color: '#a5b4fc', fontSize: 11, cursor: 'pointer' };
const thStyle = { textAlign: 'left', padding: '10px 12px', background: 'rgba(255,255,255,0.04)', fontSize: 11, textTransform: 'uppercase', color: '#64748b', borderBottom: '1px solid rgba(255,255,255,0.05)' };
const tdStyle = { padding: '12px', fontSize: 13, borderBottom: '1px solid rgba(255,255,255,0.05)', color: '#94a3b8' };
