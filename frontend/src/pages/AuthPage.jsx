import { useState, useEffect } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import {
  Brain, Check, Eye, EyeOff, Shield, Settings2, UserCircle,
  AlertCircle, ArrowRight, Loader2, Info,
} from 'lucide-react';
import { useAuth } from '../contexts/AuthContext';

const ROLES = [
  { id: 'admin',      icon: Shield,     label: 'Admin',      subtitle: 'System governance & analytics' },
  { id: 'controller', icon: Settings2,  label: 'Controller', subtitle: 'Document ingestion & processing' },
  { id: 'user',       icon: UserCircle, label: 'User',       subtitle: 'Knowledge search & retrieval' },
];

export default function AuthPage({ initialView = 'login' }) {
  const { login, signup, isAuthenticated, role, authError, setAuthError } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();

  const [view, setView] = useState(initialView); // 'login' | 'signup' | 'forgot'
  const [showPass, setShowPass] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [successMessage, setSuccessMessage] = useState(null);

  // Form fields
  const [fields, setFields] = useState({
    identifier: '',
    username: '',
    email: '',
    password: '',
    confirm: '',
    firstName: '',
    lastName: '',
    targetRole: 'admin',
  });

  const setField = (k) => (e) => {
    setFields((p) => ({ ...p, [k]: e.target.value }));
    if (authError) setAuthError(null);
  };

  // If already authenticated, redirect to role home
  useEffect(() => {
    if (isAuthenticated) {
      const from = location.state?.from?.pathname;
      if (from) {
        navigate(from, { replace: true });
      } else {
        const defaultRoutes = {
          admin: '/admin/dashboard',
          controller: '/controller/dashboard',
          user: '/user/chat',
        };
        navigate(defaultRoutes[role] || '/user/chat', { replace: true });
      }
    }
  }, [isAuthenticated, role, navigate, location]);

  const handleLoginSubmit = async (e) => {
    e?.preventDefault();
    if (!fields.identifier || !fields.password) {
      setAuthError('Please provide both username/email and password.');
      return;
    }

    setIsSubmitting(true);
    setAuthError(null);

    try {
      const result = await login(
        {
          identifier: fields.identifier,
          password: fields.password,
        },
        fields.targetRole
      );

      const targetRole = result.role || fields.targetRole || 'user';
      const defaultRoutes = {
        admin: '/admin/dashboard',
        controller: '/controller/dashboard',
        user: '/user/chat',
      };
      navigate(defaultRoutes[targetRole] || '/user/chat', { replace: true });
    } catch {
      // Error is set in context
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleSignupSubmit = async (e) => {
    e?.preventDefault();
    if (!fields.username || !fields.email || !fields.password) {
      setAuthError('Please fill in username, email, and password.');
      return;
    }
    if (fields.password !== fields.confirm) {
      setAuthError('Passwords do not match.');
      return;
    }
    if (fields.password.length < 8) {
      setAuthError('Password must be at least 8 characters long.');
      return;
    }

    setIsSubmitting(true);
    setAuthError(null);

    try {
      await signup({
        username: fields.username.trim(),
        email: fields.email.trim(),
        password: fields.password,
        profile: {
          firstName: fields.firstName.trim() || undefined,
          lastName: fields.lastName.trim() || undefined,
        },
      });

      setSuccessMessage('Account created successfully! You can now sign in.');
      setView('login');
      setFields((p) => ({ ...p, identifier: p.username, password: '', confirm: '' }));
    } catch {
      // Error is set in context
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div style={wrap}>
      {/* Background glow */}
      <div style={glow} />

      <div style={panel}>
        {/* Header Branding */}
        <div style={{ textAlign: 'center', marginBottom: 26 }}>
          <div style={logoBox}>
            <Brain size={24} color="#fff" />
          </div>
          <h2 style={{ fontWeight: 800, fontSize: 22, marginBottom: 6, color: '#f1f5f9' }}>
            {view === 'login'  ? 'Welcome to OMNI-RAG' :
             view === 'signup' ? 'Create an Account'   : 'Reset Password'}
          </h2>
          <p style={{ color: '#64748b', fontSize: 13 }}>
            {view === 'login'  ? 'Enterprise Multi-Modal Retrieval-Augmented Generation' :
             view === 'signup' ? 'Get started with intelligent document search' : "Account recovery assistance"}
          </p>
        </div>

        {/* Global Error Banner */}
        {authError && (
          <div style={errorBanner}>
            <AlertCircle size={15} color="#ef4444" style={{ flexShrink: 0, marginTop: 1 }} />
            <span>{authError}</span>
          </div>
        )}

        {/* Global Success Banner */}
        {successMessage && (
          <div style={successBanner}>
            <Check size={15} color="#10b981" style={{ flexShrink: 0, marginTop: 1 }} />
            <span>{successMessage}</span>
          </div>
        )}

        {/* ── FORGOT PASSWORD VIEW ───────────────────────────────────────── */}
        {view === 'forgot' ? (
          <div>
            <div style={infoBox}>
              <Info size={16} color="#38bdf8" style={{ flexShrink: 0, marginTop: 2 }} />
              <div>
                <strong style={{ color: '#e0f2fe', display: 'block', marginBottom: 4 }}>
                  Backend Integration Note
                </strong>
                <p style={{ margin: 0, fontSize: 12, color: '#94a3b8', lineHeight: 1.5 }}>
                  Self-service password reset is currently awaiting email SMTP service configuration.
                  Please contact your system administrator or controller to reset credentials.
                </p>
              </div>
            </div>

            <div style={{ marginBottom: 16 }}>
              <Input
                label="Registered Email Address"
                type="email"
                value={fields.email}
                onChange={setField('email')}
                placeholder="user@example.com"
              />
            </div>

            <button
              type="button"
              onClick={() => {
                setSuccessMessage('Password reset instructions requested. Please check with your administrator.');
                setView('login');
              }}
              style={submitBtn}
            >
              Request Admin Assistance
            </button>

            <p style={{ textAlign: 'center', marginTop: 18 }}>
              <button
                type="button"
                onClick={() => { setView('login'); setAuthError(null); }}
                style={linkBtn}
              >
                ← Back to Sign In
              </button>
            </p>
          </div>

        ) : view === 'signup' ? (

          /* ── SIGNUP FORM ─────────────────────────────────────────────── */
          <form onSubmit={handleSignupSubmit}>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 10 }}>
              <Input
                label="First Name"
                value={fields.firstName}
                onChange={setField('firstName')}
                placeholder="Arjun"
              />
              <Input
                label="Last Name"
                value={fields.lastName}
                onChange={setField('lastName')}
                placeholder="Sharma"
              />
            </div>

            <Input
              label="Username"
              value={fields.username}
              onChange={setField('username')}
              placeholder="arjun_sharma"
              required
            />

            <Input
              label="Email Address"
              type="email"
              value={fields.email}
              onChange={setField('email')}
              placeholder="arjun@example.com"
              required
            />

            <div style={{ position: 'relative', marginBottom: 12 }}>
              <Input
                label="Password (min 8 chars)"
                type={showPass ? 'text' : 'password'}
                value={fields.password}
                onChange={setField('password')}
                placeholder="••••••••"
                required
                noMargin
              />
              <button
                type="button"
                onClick={() => setShowPass((p) => !p)}
                style={eyeBtn}
              >
                {showPass ? <EyeOff size={15} /> : <Eye size={15} />}
              </button>
            </div>

            <Input
              label="Confirm Password"
              type="password"
              value={fields.confirm}
              onChange={setField('confirm')}
              placeholder="••••••••"
              required
            />

            <button
              type="submit"
              disabled={isSubmitting}
              style={{ ...submitBtn, opacity: isSubmitting ? 0.7 : 1 }}
            >
              {isSubmitting ? (
                <span style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 8 }}>
                  <Loader2 size={16} className="spin" /> Registering...
                </span>
              ) : (
                'Create OMNI-RAG Account →'
              )}
            </button>

            <p style={{ textAlign: 'center', color: '#64748b', fontSize: 13, marginTop: 18 }}>
              Already have an account?{' '}
              <button
                type="button"
                onClick={() => { setView('login'); setAuthError(null); }}
                style={linkBtn}
              >
                Sign in
              </button>
            </p>
          </form>

        ) : (

          /* ── LOGIN FORM ──────────────────────────────────────────────── */
          <form onSubmit={handleLoginSubmit}>
            {/* Target Role Selector */}
            <div style={{ marginBottom: 16 }}>
              <label style={{ fontSize: 12, color: '#64748b', display: 'block', marginBottom: 8, fontWeight: 600 }}>
                Select Workspace Mode
              </label>
              <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
                {ROLES.map(({ id, icon: Icon, label, subtitle }) => {
                  const selected = fields.targetRole === id;
                  return (
                    <button
                      key={id}
                      type="button"
                      onClick={() => setFields((p) => ({ ...p, targetRole: id }))}
                      style={{
                        display: 'flex',
                        alignItems: 'center',
                        gap: 12,
                        padding: '8px 12px',
                        borderRadius: 10,
                        cursor: 'pointer',
                        textAlign: 'left',
                        background: selected ? 'rgba(99,102,241,0.14)' : 'rgba(255,255,255,0.02)',
                        border: selected ? '1px solid rgba(168,85,247,0.5)' : '1px solid rgba(255,255,255,0.07)',
                        transition: 'all 0.15s',
                      }}
                    >
                      <div style={{
                        width: 32,
                        height: 32,
                        borderRadius: 8,
                        flexShrink: 0,
                        background: selected ? 'rgba(124,58,237,0.25)' : 'rgba(255,255,255,0.04)',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                      }}>
                        <Icon size={16} color={selected ? '#c084fc' : '#64748b'} />
                      </div>
                      <div style={{ minWidth: 0, flex: 1 }}>
                        <div style={{ fontSize: 13, fontWeight: 700, color: selected ? '#f1f5f9' : '#94a3b8' }}>
                          {label}
                        </div>
                        <div style={{ fontSize: 11, color: '#64748b', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                          {subtitle}
                        </div>
                      </div>
                    </button>
                  );
                })}
              </div>
            </div>

            {/* Identifier input */}
            <Input
              label="Username or Email"
              type="text"
              value={fields.identifier}
              onChange={setField('identifier')}
              placeholder="admin or admin@omnirag.local"
              required
            />

            {/* Password input */}
            <div style={{ position: 'relative', marginBottom: 10 }}>
              <Input
                label="Password"
                type={showPass ? 'text' : 'password'}
                value={fields.password}
                onChange={setField('password')}
                placeholder="••••••••"
                required
                noMargin
              />
              <button
                type="button"
                onClick={() => setShowPass((p) => !p)}
                style={eyeBtn}
              >
                {showPass ? <EyeOff size={15} /> : <Eye size={15} />}
              </button>
            </div>

            <div style={{ textAlign: 'right', marginBottom: 16 }}>
              <button
                type="button"
                onClick={() => { setView('forgot'); setAuthError(null); }}
                style={linkBtn}
              >
                Forgot credentials?
              </button>
            </div>

            <button
              type="submit"
              disabled={isSubmitting}
              style={{ ...submitBtn, opacity: isSubmitting ? 0.7 : 1 }}
            >
              {isSubmitting ? (
                <span style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 8 }}>
                  <Loader2 size={16} style={{ animation: 'spin 0.8s linear infinite' }} /> Authenticating...
                </span>
              ) : (
                <span style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 6 }}>
                  Sign In to OMNI-RAG <ArrowRight size={15} />
                </span>
              )}
            </button>

            <p style={{ textAlign: 'center', color: '#64748b', fontSize: 13, marginTop: 18 }}>
              Don&apos;t have an account?{' '}
              <button
                type="button"
                onClick={() => { setView('signup'); setAuthError(null); }}
                style={linkBtn}
              >
                Sign up
              </button>
            </p>
          </form>
        )}
      </div>
    </div>
  );
}

function Input({ label, type = 'text', value, onChange, placeholder, required = false, noMargin = false }) {
  return (
    <div style={{ marginBottom: noMargin ? 0 : 12 }}>
      <label style={{ fontSize: 12, color: '#94a3b8', display: 'block', marginBottom: 5, fontWeight: 500 }}>
        {label}
      </label>
      <input
        type={type}
        value={value}
        onChange={onChange}
        placeholder={placeholder}
        required={required}
        style={{
          width: '100%',
          padding: '10px 14px',
          borderRadius: 10,
          border: '1px solid rgba(255,255,255,0.1)',
          background: 'rgba(255,255,255,0.04)',
          color: '#f1f5f9',
          fontSize: 13,
          boxSizing: 'border-box',
          outline: 'none',
          transition: 'border-color 0.2s',
        }}
        onFocus={(e) => { e.currentTarget.style.borderColor = '#7c3aed'; }}
        onBlur={(e) => { e.currentTarget.style.borderColor = 'rgba(255,255,255,0.1)'; }}
      />
    </div>
  );
}

const wrap = {
  minHeight: '100vh',
  background: '#05050a',
  display: 'flex',
  alignItems: 'center',
  justifyContent: 'center',
  fontFamily: 'var(--font, sans-serif)',
  position: 'relative',
  padding: '24px 16px',
};

const glow = {
  position: 'absolute',
  inset: 0,
  pointerEvents: 'none',
  background: 'radial-gradient(ellipse 60% 50% at 50% 30%, rgba(124,58,237,0.15), transparent)',
};

const panel = {
  background: 'rgba(15,17,26,0.85)',
  backdropFilter: 'blur(16px)',
  WebkitBackdropFilter: 'blur(16px)',
  border: '1px solid rgba(255,255,255,0.1)',
  borderRadius: 24,
  padding: '36px 32px',
  width: '100%',
  maxWidth: 440,
  position: 'relative',
  zIndex: 1,
  boxShadow: '0 20px 48px rgba(0,0,0,0.6)',
};

const logoBox = {
  width: 50,
  height: 50,
  borderRadius: 14,
  background: 'linear-gradient(135deg, #7c3aed, #a855f7)',
  display: 'flex',
  alignItems: 'center',
  justifyContent: 'center',
  margin: '0 auto 16px',
  boxShadow: '0 4px 16px rgba(124,58,237,0.35)',
};

const submitBtn = {
  width: '100%',
  padding: '12px',
  borderRadius: 10,
  border: 'none',
  background: 'linear-gradient(135deg, #7c3aed, #6366f1)',
  color: '#fff',
  fontSize: 14,
  fontWeight: 700,
  cursor: 'pointer',
  marginTop: 6,
  boxShadow: '0 4px 16px rgba(124,58,237,0.3)',
  transition: 'transform 0.15s, opacity 0.15s',
};

const eyeBtn = {
  position: 'absolute',
  right: 12,
  bottom: 9,
  background: 'none',
  border: 'none',
  cursor: 'pointer',
  color: '#64748b',
  padding: 4,
};

const linkBtn = {
  background: 'none',
  border: 'none',
  color: '#a5b4fc',
  cursor: 'pointer',
  fontSize: 13,
  fontWeight: 600,
};

const errorBanner = {
  display: 'flex',
  alignItems: 'flex-start',
  gap: 8,
  padding: '10px 14px',
  borderRadius: 10,
  background: 'rgba(239,68,68,0.12)',
  border: '1px solid rgba(239,68,68,0.3)',
  color: '#fca5a5',
  fontSize: 12,
  marginBottom: 16,
};

const successBanner = {
  display: 'flex',
  alignItems: 'flex-start',
  gap: 8,
  padding: '10px 14px',
  borderRadius: 10,
  background: 'rgba(16,185,129,0.12)',
  border: '1px solid rgba(16,185,129,0.3)',
  color: '#86efac',
  fontSize: 12,
  marginBottom: 16,
};

const infoBox = {
  display: 'flex',
  alignItems: 'flex-start',
  gap: 10,
  padding: '12px 14px',
  borderRadius: 12,
  background: 'rgba(56,189,248,0.08)',
  border: '1px solid rgba(56,189,248,0.2)',
  marginBottom: 18,
};
