/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import { useState } from 'react';
import { AnimatePresence, motion } from 'motion/react';

import { Sidebar, DynamicNotch } from './components/Shared';
import { useBackendStatus, useSSE, useNotifications } from './hooks/useBackend';

import Dashboard from './pages/Dashboard';
import IngestionQueue from './pages/IngestionQueue';
import Settings from './pages/Settings';
import LogsWindow from './pages/LogsWindow';

export default function App() {
  const [activeTab, setActiveTab] = useState('dashboard');
  const backendStatus = useBackendStatus();
  const { logs: sseLogs, lastEvent, clearLogs } = useSSE();
  const { notifications, activeNotify, setActiveNotify, triggerNotification, markRead } = useNotifications();

  // Auto-notify on SSE events
  // (lastEvent is already handled by the hook's auto-dismiss)

  return (
    <div className="flex h-screen bg-background text-on-surface overflow-hidden relative">
      <DynamicNotch
        activeNotify={activeNotify}
        onClose={() => setActiveNotify(null)}
      />

      <Sidebar
        activeTab={activeTab}
        backendStatus={backendStatus}
        onTabChange={(tab, notify) => {
          setActiveTab(tab);
          if (notify) triggerNotification(notify.category, notify.message, notify.type);
        }}
      />

      <main className="flex-1 flex flex-col h-full md:ml-64 relative overflow-hidden">
        <AnimatePresence mode="wait">
          <motion.div
            key={activeTab}
            initial={{ opacity: 0, x: 10 }}
            animate={{ opacity: 1, x: 0 }}
            exit={{ opacity: 0, x: -10 }}
            transition={{ duration: 0.2 }}
            className="flex-1 flex flex-col h-full overflow-hidden"
          >
            {activeTab === 'dashboard' && (
              <Dashboard
                notifications={notifications}
                markRead={markRead}
                onNotify={triggerNotification}
                activeTab={activeTab}
                onTabChange={setActiveTab}
              />
            )}
            {activeTab === 'ingestion-queue' && (
              <IngestionQueue
                notifications={notifications}
                markRead={markRead}
                activeTab={activeTab}
                onTabChange={setActiveTab}
                sseLogs={sseLogs}
                onNotify={triggerNotification}
              />
            )}
            {activeTab === 'settings' && (
              <Settings
                notifications={notifications}
                markRead={markRead}
                activeTab={activeTab}
                onTabChange={setActiveTab}
                onNotify={triggerNotification}
              />
            )}
            {activeTab === 'logs' && (
              <LogsWindow
                onNotify={triggerNotification}
                notifications={notifications}
                markRead={markRead}
                activeTab={activeTab}
                onTabChange={setActiveTab}
                sseLogs={sseLogs}
                clearLogs={clearLogs}
              />
            )}
          </motion.div>
        </AnimatePresence>
      </main>
    </div>
  );
}
