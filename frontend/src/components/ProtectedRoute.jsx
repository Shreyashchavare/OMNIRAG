import { Navigate, useLocation } from 'react-router-dom';
import { useAuth } from '../contexts/AuthContext';

export function ProtectedRoute({ allowedRoles = null, children }) {
  const { isAuthenticated, isLoading, role } = useAuth();
  const location = useLocation();

  if (isLoading) {
    return (
      <div style={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        height: '100vh',
        background: 'var(--bg-base, #05050a)',
        color: '#a5b4fc',
        fontSize: 14,
        gap: 12,
        fontFamily: 'var(--font, sans-serif)',
      }}>
        <div style={{
          width: 24,
          height: 24,
          border: '2px solid rgba(99,102,241,0.2)',
          borderTopColor: '#6366f1',
          borderRadius: '50%',
          animation: 'spin 0.8s linear infinite',
        }} />
        <span>Authenticating OMNIRAG session...</span>
      </div>
    );
  }

  if (!isAuthenticated) {
    return <Navigate to="/login" state={{ from: location }} replace />;
  }

  if (allowedRoles && !allowedRoles.includes(role)) {
    // Redirect to the appropriate home dashboard for the user's role
    const roleDefaultRoutes = {
      admin: '/admin/dashboard',
      controller: '/controller/dashboard',
      user: '/user/chat',
    };
    const targetRoute = roleDefaultRoutes[role] || '/user/chat';
    return <Navigate to={targetRoute} replace />;
  }

  return children;
}
