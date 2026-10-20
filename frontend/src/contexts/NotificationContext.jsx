import { createContext, useContext, useState, useEffect, useCallback } from 'react';
import { notificationApi } from '../api/services/notificationApi';
import { useAuth } from './AuthContext';

const NotificationContext = createContext(null);

export function NotificationProvider({ children }) {
  const { isAuthenticated } = useAuth();
  const [unreadCount, setUnreadCount] = useState(0);
  const [notifications, setNotifications] = useState([]);
  const [isLoading, setIsLoading] = useState(false);
  const [isOpen, setIsOpen] = useState(false);

  const fetchUnreadCount = useCallback(async () => {
    if (!isAuthenticated) {
      setUnreadCount(0);
      return;
    }
    try {
      const data = await notificationApi.getUnreadCount();
      if (data && typeof data.unreadCount === 'number') {
        setUnreadCount(data.unreadCount);
      }
    } catch {
      setUnreadCount(0);
    }
  }, [isAuthenticated]);

  const fetchRecentNotifications = useCallback(async () => {
    if (!isAuthenticated) return;
    setIsLoading(true);
    try {
      const data = await notificationApi.getNotifications(0, 10, false);
      if (data && Array.isArray(data.content)) {
        setNotifications(data.content);
      }
    } catch {
      setNotifications([]);
    } finally {
      setIsLoading(false);
    }
  }, [isAuthenticated]);

  useEffect(() => {
    let ignore = false;
    if (isAuthenticated) {
      notificationApi.getUnreadCount()
        .then((data) => {
          if (!ignore && data && typeof data.unreadCount === 'number') {
            setUnreadCount(data.unreadCount);
          }
        })
        .catch(() => {
          if (!ignore) setUnreadCount(0);
        });

      notificationApi.getNotifications(0, 10, false)
        .then((data) => {
          if (!ignore && data?.content) {
            setNotifications(data.content);
          }
        })
        .catch(() => {
          if (!ignore) setNotifications([]);
        });

      const interval = setInterval(() => {
        notificationApi.getUnreadCount()
          .then((data) => {
            if (data && typeof data.unreadCount === 'number') {
              setUnreadCount(data.unreadCount);
            }
          })
          .catch(() => {});
      }, 60000);

      return () => {
        ignore = true;
        clearInterval(interval);
      };
    }
  }, [isAuthenticated]);

  const markAsRead = async (id) => {
    try {
      await notificationApi.markAsRead(id);
      setNotifications((prev) =>
        prev.map((n) => (n.id === id ? { ...n, read: true } : n))
      );
      setUnreadCount((c) => Math.max(0, c - 1));
    } catch (err) {
      console.warn('Failed to mark notification as read:', err);
    }
  };

  const markAllAsRead = async () => {
    try {
      await notificationApi.markAllAsRead();
      setNotifications((prev) => prev.map((n) => ({ ...n, read: true })));
      setUnreadCount(0);
    } catch (err) {
      console.warn('Failed to mark all notifications as read:', err);
    }
  };

  const toggleDropdown = () => {
    setIsOpen((prev) => !prev);
    if (!isOpen) {
      fetchRecentNotifications();
    }
  };

  const value = {
    unreadCount,
    notifications,
    isLoading,
    isOpen,
    setIsOpen,
    toggleDropdown,
    markAsRead,
    markAllAsRead,
    refreshNotifications: fetchRecentNotifications,
    refreshUnreadCount: fetchUnreadCount,
  };

  return (
    <NotificationContext.Provider value={value}>
      {children}
    </NotificationContext.Provider>
  );
}

export function useNotifications() {
  const context = useContext(NotificationContext);
  if (!context) {
    throw new Error('useNotifications must be used within a NotificationProvider');
  }
  return context;
}
