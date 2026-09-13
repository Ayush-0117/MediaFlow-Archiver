import { useState, useEffect, useRef, useCallback } from 'react';
import { fetchAiStatus, SSE_URL } from '../api';

const IS_TAURI = '__TAURI__' in window || '__TAURI_INTERNALS__' in window;

/**
 * Sends a native OS notification when running inside Tauri.
 * Silently no-ops in browser mode.
 */
async function sendNativeNotification(title, body) {
  if (!IS_TAURI) return;
  try {
    const { isPermissionGranted, requestPermission, sendNotification } =
      await import('@tauri-apps/plugin-notification');

    let granted = await isPermissionGranted();
    if (!granted) {
      const result = await requestPermission();
      granted = result === 'granted';
    }
    if (granted) {
      sendNotification({ title, body });
    }
  } catch (err) {
    console.warn('[Notification] Native notification failed:', err);
  }
}

/** Hook: polls backend health and returns 'online' | 'connecting' | 'offline' */
export function useBackendStatus() {
  const [status, setStatus] = useState('connecting');
  useEffect(() => {
    let mounted = true;
    const check = async () => {
      try {
        await fetchAiStatus();
        if (mounted) setStatus('online');
      } catch {
        if (mounted) setStatus('offline');
      }
    };
    check();
    const id = setInterval(check, 15000);
    return () => { mounted = false; clearInterval(id); };
  }, []);
  return status;
}

/** Hook: subscribes to SSE and returns log array + latest event */
export function useSSE() {
  const [logs, setLogs] = useState([]);
  const [lastEvent, setLastEvent] = useState(null);
  const esRef = useRef(null);

  useEffect(() => {
    const es = new EventSource(SSE_URL);
    esRef.current = es;

    es.addEventListener('ingestion-progress', (e) => {
      const msg = e.data;
      const entry = {
        time: new Date().toLocaleTimeString('en-GB', { hour12: false }),
        text: msg,
        type: msg.toLowerCase().includes('error') ? 'error'
            : msg.toLowerCase().includes('warn') ? 'warning'
            : msg.toLowerCase().includes('success') || msg.toLowerCase().includes('complete') ? 'success'
            : 'info',
      };
      setLogs(prev => [...prev.slice(-200), entry]);
      setLastEvent(entry);

      // Fire native OS notifications for critical events
      if (msg.toLowerCase().includes('all') && msg.toLowerCase().includes('processed')) {
        sendNativeNotification('MediaFlow Archiver', `🎉 ${msg}`);
      } else if (msg.toLowerCase().includes('switching') && msg.toLowerCase().includes('model')) {
        sendNativeNotification('MediaFlow — Model Switch', msg);
      } else if (msg.toLowerCase().includes('ingestion complete')) {
        sendNativeNotification('MediaFlow Archiver', `✅ ${msg}`);
      } else if (msg.toLowerCase().includes('failed') && msg.toLowerCase().includes('retry')) {
        sendNativeNotification('MediaFlow — Retry', msg);
      }
    });

    es.onerror = () => {
      // EventSource auto-reconnects
    };

    return () => es.close();
  }, []);

  const clearLogs = useCallback(() => setLogs([]), []);
  return { logs, lastEvent, clearLogs };
}

/** Hook: notification system */
export function useNotifications() {
  const [notifications, setNotifications] = useState([
    { id: 1, category: 'System', message: 'Welcome back. Ingestion engine is active.', type: 'info', unread: true, time: '2m ago' },
  ]);
  const [activeNotify, setActiveNotify] = useState(null);

  const triggerNotification = useCallback((category, message, type = 'info') => {
    const n = { id: Date.now(), category, message, type, unread: true, time: 'Just now' };
    setNotifications(prev => [...prev, n]);
    setActiveNotify(n);
    setTimeout(() => setActiveNotify(null), 2500);
  }, []);

  const markRead = useCallback(() => {
    setNotifications(prev => prev.map(n => ({ ...n, unread: false })));
  }, []);

  return { notifications, activeNotify, setActiveNotify, triggerNotification, markRead };
}
