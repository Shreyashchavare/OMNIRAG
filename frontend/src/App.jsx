import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { AuthProvider } from './contexts/AuthContext';
import { NotificationProvider } from './contexts/NotificationContext';
import { ProtectedRoute } from './components/ProtectedRoute';

// Layouts
import { AdminLayout } from './layouts/AdminLayout';
import { ControllerLayout } from './layouts/ControllerLayout';
import { UserLayout } from './layouts/UserLayout';

// Public Pages
import LandingPage from './pages/LandingPage';
import AuthPage from './pages/AuthPage';

// Admin Pages
import AdminDashboard from './pages/admin/AdminDashboard';
import AdminDocuments from './pages/admin/AdminDocuments';
import AdminAnalytics from './pages/admin/AdminAnalytics';
import UserManagement from './pages/admin/UserManagement';

// Controller Pages
import ControllerDashboard from './pages/controller/ControllerDashboard';
import UploadDocs from './pages/controller/UploadDocs';
import Testing from './pages/controller/Testing';
import RequestApproval from './pages/controller/RequestApproval';
import ModelDashboard from './pages/controller/ModelDashboard';

// User Pages
import ChatPage from './pages/user/ChatPage';
import ChatHistory from './pages/user/ChatHistory';
import HelpPage from './pages/user/HelpPage';

export default function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <NotificationProvider>
          <Routes>
            {/* Public Routes */}
            <Route path="/" element={<LandingPage />} />
            <Route path="/login" element={<AuthPage initialView="login" />} />
            <Route path="/signup" element={<AuthPage initialView="signup" />} />

            {/* Admin Workspace */}
            <Route
              path="/admin"
              element={
                <ProtectedRoute allowedRoles={['admin']}>
                  <AdminLayout />
                </ProtectedRoute>
              }
            >
              <Route index element={<Navigate to="/admin/dashboard" replace />} />
              <Route path="dashboard" element={<AdminDashboard />} />
              <Route path="documents" element={<AdminDocuments />} />
              <Route path="analytics" element={<AdminAnalytics />} />
              <Route path="users" element={<UserManagement />} />
            </Route>

            {/* Controller Workspace */}
            <Route
              path="/controller"
              element={
                <ProtectedRoute allowedRoles={['controller', 'admin']}>
                  <ControllerLayout />
                </ProtectedRoute>
              }
            >
              <Route index element={<Navigate to="/controller/dashboard" replace />} />
              <Route path="dashboard" element={<ControllerDashboard />} />
              <Route path="upload" element={<UploadDocs />} />
              <Route path="testing" element={<Testing />} />
              <Route path="approval" element={<RequestApproval />} />
              <Route path="models" element={<ModelDashboard />} />
            </Route>

            {/* Standard User Workspace */}
            <Route
              path="/user"
              element={
                <ProtectedRoute allowedRoles={['user', 'controller', 'admin']}>
                  <UserLayout />
                </ProtectedRoute>
              }
            >
              <Route index element={<Navigate to="/user/chat" replace />} />
              <Route path="chat" element={<ChatPage />} />
              <Route path="history" element={<ChatHistory />} />
              <Route path="help" element={<HelpPage />} />
            </Route>

            {/* Fallback */}
            <Route path="*" element={<Navigate to="/" replace />} />
          </Routes>
        </NotificationProvider>
      </AuthProvider>
    </BrowserRouter>
  );
}
