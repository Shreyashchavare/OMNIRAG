import { createContext, useContext, useState, useEffect, useCallback } from 'react';
import { authApi } from '../api/services/authApi';
import { extractErrorMessage } from '../api/client';

const AuthContext = createContext(null);

function parseJwt(token) {
  try {
    const base64Url = token.split('.')[1];
    if (!base64Url) return null;
    const base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/');
    const jsonPayload = decodeURIComponent(
      atob(base64)
        .split('')
        .map((c) => '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2))
        .join('')
    );
    return JSON.parse(jsonPayload);
  } catch {
    return null;
  }
}

function resolveRole(claims, userObject, requestedRole) {
  const rolesList = claims?.roles || userObject?.roles || [];
  const normalized = Array.isArray(rolesList)
    ? rolesList.map((r) => String(r).toUpperCase().replace(/^ROLE_/, ''))
    : [String(rolesList).toUpperCase().replace(/^ROLE_/, '')];

  if (normalized.includes('ADMIN')) return 'admin';
  if (normalized.includes('CONTROLLER')) return 'controller';
  if (normalized.includes('USER')) return 'user';

  // Fallback to requested/persisted role or 'user'
  if (['admin', 'controller', 'user'].includes(requestedRole?.toLowerCase())) {
    return requestedRole.toLowerCase();
  }
  return 'user';
}

function getInitialAuthState() {
  try {
    const storedToken = localStorage.getItem('omnirag_access_token');
    const storedUser = localStorage.getItem('omnirag_user');
    const storedRole = localStorage.getItem('omnirag_role');

    if (storedToken) {
      const claims = parseJwt(storedToken);
      const isExpired = claims?.exp && claims.exp * 1000 < Date.now();

      if (!isExpired) {
        const parsedUser = storedUser ? JSON.parse(storedUser) : null;
        return {
          user: parsedUser,
          token: storedToken,
          role: resolveRole(claims, parsedUser, storedRole),
        };
      }
      localStorage.removeItem('omnirag_access_token');
      localStorage.removeItem('omnirag_user');
      localStorage.removeItem('omnirag_role');
    }
  } catch (e) {
    console.warn('Failed to restore auth session:', e);
  }
  return { user: null, token: null, role: 'user' };
}

export function AuthProvider({ children }) {
  const [initial] = useState(getInitialAuthState);
  const [user, setUser] = useState(initial.user);
  const [accessToken, setAccessToken] = useState(initial.token);
  const [role, setRole] = useState(initial.role);
  const [isLoading] = useState(false);
  const [authError, setAuthError] = useState(null);

  // Listen to logout event dispatched by 401 refresh failure
  useEffect(() => {
    const handleForceLogout = () => {
      setUser(null);
      setAccessToken(null);
      setRole('user');
      setAuthError('Your session has expired. Please sign in again.');
    };

    window.addEventListener('omnirag:auth_logout', handleForceLogout);
    return () => window.removeEventListener('omnirag:auth_logout', handleForceLogout);
  }, []);

  const login = useCallback(async (credentials, requestedRole = null) => {
    setAuthError(null);
    try {
      const data = await authApi.login({
        identifier: credentials.identifier || credentials.email,
        password: credentials.password,
      });

      const { accessToken: token, refreshToken, user: userData } = data;
      const claims = parseJwt(token);
      const determinedRole = resolveRole(claims, userData, requestedRole);

      localStorage.setItem('omnirag_access_token', token);
      if (refreshToken) {
        localStorage.setItem('omnirag_refresh_token', refreshToken);
      }
      if (userData) {
        localStorage.setItem('omnirag_user', JSON.stringify(userData));
      }
      localStorage.setItem('omnirag_role', determinedRole);

      setAccessToken(token);
      setUser(userData);
      setRole(determinedRole);

      return { success: true, role: determinedRole, user: userData };
    } catch (err) {
      const msg = extractErrorMessage(err);
      setAuthError(msg);
      throw new Error(msg, { cause: err });
    }
  }, []);

  const signup = useCallback(async (formData) => {
    setAuthError(null);
    try {
      const data = await authApi.signup(formData);
      return data;
    } catch (err) {
      const msg = extractErrorMessage(err);
      setAuthError(msg);
      throw new Error(msg, { cause: err });
    }
  }, []);

  const logout = useCallback(async () => {
    const refreshToken = localStorage.getItem('omnirag_refresh_token');
    if (refreshToken) {
      try {
        await authApi.logout(refreshToken);
      } catch (err) {
        // Silently continue local cleanup even if network fails
        console.warn('Backend logout failed:', err);
      }
    }

    localStorage.removeItem('omnirag_access_token');
    localStorage.removeItem('omnirag_refresh_token');
    localStorage.removeItem('omnirag_user');
    localStorage.removeItem('omnirag_role');

    setAccessToken(null);
    setUser(null);
    setRole('user');
    setAuthError(null);
  }, []);

  const value = {
    user,
    accessToken,
    role,
    setRole,
    isAuthenticated: Boolean(accessToken),
    isLoading,
    authError,
    setAuthError,
    login,
    signup,
    logout,
  };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
}
