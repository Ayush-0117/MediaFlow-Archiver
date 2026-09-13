import { useState } from 'react';
import { motion, AnimatePresence } from 'motion/react';
import {
  LayoutDashboard, Settings as SettingsIcon, Plus, Search, Bell, HelpCircle,
  Terminal, LifeBuoy, ListRestart, Menu, X, AlertCircle, CheckCircle2, Info,
  Settings2,
} from 'lucide-react';

// --- Sidebar ---
export function Sidebar({ activeTab, onTabChange, backendStatus }) {
  const [isMobileMenuOpen, setIsMobileMenuOpen] = useState(false);
  return (
    <>
      <nav className="md:hidden flex justify-between items-center px-6 py-3 w-full bg-surface-dim border-b border-surface-container fixed top-0 z-50 shadow-lg">
        <div className="flex items-center gap-4">
          <button onClick={() => setIsMobileMenuOpen(!isMobileMenuOpen)}><Menu className="text-primary w-6 h-6" /></button>
        </div>
        <div className="flex items-center gap-4"><Search className="text-on-surface opacity-70 w-6 h-6" /></div>
      </nav>
      <aside className={`fixed left-0 top-0 h-full bg-surface-container-low w-64 z-[60] flex flex-col py-6 px-4 border-r border-surface-dim transition-transform duration-300 md:translate-x-0 ${isMobileMenuOpen ? 'translate-x-0' : '-translate-x-full'}`}>
        <div className="mb-8 px-2 flex justify-between items-center">
          <h1 className="text-xl font-black text-primary font-headline tracking-tighter">MediaFlow Archiver</h1>
          <button className="md:hidden" onClick={() => setIsMobileMenuOpen(false)}><X className="w-6 h-6 text-on-surface-variant" /></button>
        </div>
        <button onClick={() => { onTabChange('ingestion-queue', { category: 'Ingest', message: 'Initializing dynamic scanning of local volumes...', type: 'info' }); setIsMobileMenuOpen(false); }} className="w-full bg-gradient-to-r from-primary to-primary-container text-on-primary font-headline font-bold py-3 px-4 rounded-md mb-8 hover:brightness-110 transition-all shadow-md flex items-center justify-center gap-2">
          <Plus className="w-5 h-5" /> New Ingest
        </button>
        <div className="flex-1 flex flex-col gap-2">
          <NavItem active={activeTab === 'dashboard'} onClick={() => { onTabChange('dashboard'); setIsMobileMenuOpen(false); }} icon={<LayoutDashboard className="w-5 h-5" />} label="Dashboard" />
          <NavItem active={activeTab === 'ingestion-queue'} onClick={() => { onTabChange('ingestion-queue'); setIsMobileMenuOpen(false); }} icon={<ListRestart className="w-5 h-5" />} label="Ingestion Queue" />
          <NavItem active={activeTab === 'settings'} onClick={() => { onTabChange('settings'); setIsMobileMenuOpen(false); }} icon={<SettingsIcon className="w-5 h-5" />} label="Settings" />
        </div>
        <div className="mt-auto flex flex-col gap-2 pt-6 border-t border-surface-container-highest">
          <div className="px-4 py-2 flex items-center gap-2 mb-2">
            <div className={`w-2 h-2 rounded-full ${backendStatus === 'online' ? 'bg-primary shadow-[0_0_8px_var(--color-primary)]' : backendStatus === 'connecting' ? 'bg-secondary-container animate-pulse' : 'bg-error'}`} />
            <span className="text-[10px] font-mono uppercase tracking-widest text-on-surface-variant opacity-60">Server: {backendStatus}</span>
          </div>
          <NavItem onClick={() => onTabChange('dashboard', { category: 'Support', message: 'Help center is currently undergoing maintenance.', type: 'warning' })} icon={<LifeBuoy className="w-5 h-5" />} label="Support" />
          <NavItem active={activeTab === 'logs'} onClick={() => { onTabChange('logs'); setIsMobileMenuOpen(false); }} icon={<Terminal className="w-5 h-5" />} label="Logs" />
        </div>
      </aside>
      {isMobileMenuOpen && <div className="fixed inset-0 bg-black/50 z-[55] md:hidden" onClick={() => setIsMobileMenuOpen(false)} />}
    </>
  );
}

function NavItem({ active, icon, label, onClick }) {
  return (
    <button onClick={onClick} className={`flex items-center gap-3 px-4 py-2.5 rounded-lg font-label text-sm tracking-wide transition-all duration-200 w-full text-left ${active ? 'bg-surface-container-high text-primary border-r-4 border-primary fill-icon' : 'text-on-surface opacity-60 hover:bg-surface-container-highest hover:opacity-100 hover:text-primary'}`}>
      {icon}<span>{label}</span>
    </button>
  );
}

// --- Dynamic Notch ---
export function DynamicNotch({ activeNotify, onClose }) {
  return (
    <AnimatePresence>
      {activeNotify && (
        <div className="fixed top-0 left-1/2 -translate-x-1/2 z-[150] pt-2 pointer-events-none">
          <motion.div layout initial={{ width: 140, height: 28, borderRadius: 20, opacity: 0, y: -20 }} animate={{ width: 320, height: 64, borderRadius: 24, opacity: 1, y: 0 }} exit={{ opacity: 0, y: -20, scale: 0.8 }} transition={{ type: "spring", stiffness: 300, damping: 30 }} className="bg-black shadow-2xl flex items-center justify-center overflow-hidden pointer-events-auto cursor-pointer border border-white/5" onClick={onClose}>
            <motion.div initial={{ opacity: 0, scale: 0.9 }} animate={{ opacity: 1, scale: 1 }} transition={{ delay: 0.1 }} className="flex items-center gap-4 px-5 w-full">
              <div className={`p-2 rounded-full shrink-0 ${activeNotify.type === 'error' ? 'bg-error/20 text-error' : activeNotify.type === 'warning' ? 'bg-secondary-container/20 text-secondary-container' : 'bg-primary/20 text-primary'}`}>
                {activeNotify.type === 'error' ? <AlertCircle className="w-5 h-5" /> : activeNotify.type === 'warning' ? <Info className="w-5 h-5" /> : <CheckCircle2 className="w-5 h-5" />}
              </div>
              <div className="flex-1 min-w-0">
                <p className="text-[10px] font-mono font-bold text-on-surface/50 uppercase tracking-wider mb-0.5 whitespace-nowrap overflow-hidden text-ellipsis">{activeNotify.category}</p>
                <p className="text-xs font-medium text-on-surface truncate">{activeNotify.message}</p>
              </div>
              <X className="w-4 h-4 text-on-surface/30 hover:text-on-surface transition-colors shrink-0" />
            </motion.div>
          </motion.div>
        </div>
      )}
    </AnimatePresence>
  );
}

// --- Notification Stack ---
function NotificationStack({ notifications, onClose, onClear }) {
  return (
    <motion.div initial={{ opacity: 0, y: 10, scale: 0.95 }} animate={{ opacity: 1, y: 0, scale: 1 }} exit={{ opacity: 0, y: 10, scale: 0.95 }} className="absolute top-full right-0 mt-2 w-80 bg-surface-container-high rounded-2xl shadow-2xl border border-outline-variant/20 z-[110] overflow-hidden">
      <div className="p-4 border-b border-surface-container-highest flex justify-between items-center bg-surface-container-low/50">
        <h3 className="font-headline font-bold text-sm text-on-surface">Notifications</h3>
        <button onClick={onClear} className="text-[10px] font-label text-primary hover:underline uppercase tracking-widest">Mark all as read</button>
      </div>
      <div className="max-h-[400px] overflow-y-auto custom-scrollbar">
        {notifications.length === 0 ? (
          <div className="p-12 text-center text-on-surface-variant italic text-xs">No notifications yet.</div>
        ) : (
          notifications.map((n) => (
            <div key={n.id} className={`p-4 border-b border-surface-container-highest last:border-none flex gap-3 hover:bg-surface-container-highest transition-colors cursor-default ${n.unread ? 'bg-primary/5' : ''}`}>
              <div className={`w-8 h-8 rounded-full flex items-center justify-center shrink-0 ${n.type === 'error' ? 'bg-error/20 text-error' : 'bg-primary/20 text-primary'}`}>
                {n.type === 'error' ? <AlertCircle className="w-4 h-4" /> : <Bell className="w-4 h-4" />}
              </div>
              <div className="flex-1 min-w-0">
                <div className="flex justify-between items-start mb-0.5">
                  <p className="text-[11px] font-bold text-on-surface truncate pr-2">{n.category}</p>
                  <span className="text-[9px] text-on-surface-variant whitespace-nowrap">{n.time}</span>
                </div>
                <p className="text-xs text-on-surface-variant leading-relaxed">{n.message}</p>
              </div>
            </div>
          )).reverse()
        )}
      </div>
      <div className="p-3 bg-surface-container-low/30 text-center border-t border-surface-container-highest">
        <button onClick={onClose} className="text-[10px] font-label text-on-surface-variant hover:text-on-surface uppercase tracking-widest">Close</button>
      </div>
    </motion.div>
  );
}

// --- Utility Nav (Top-Right) ---
export function UtilityNav({ notifications, markRead, activeTab, onTabChange }) {
  const [isOpen, setIsOpen] = useState(false);
  const [showHelp, setShowHelp] = useState(false);
  const hasUnread = notifications.some(n => n.unread);

  return (
    <div className="flex items-center gap-1.5 md:gap-3 relative">
      <div className="relative">
        <button onClick={() => setIsOpen(!isOpen)} className={`p-2 rounded-full transition-all ${isOpen ? 'bg-primary/10 text-primary' : 'text-on-surface-variant hover:bg-surface-container-high hover:text-on-surface'}`}>
          <Bell className="w-5 h-5" />
          {hasUnread && <span className="absolute top-2 right-2.5 w-2 h-2 bg-error rounded-full border-2 border-surface-dim" />}
        </button>
        <AnimatePresence>
          {isOpen && (<><div className="fixed inset-0 z-[105]" onClick={() => setIsOpen(false)} /><NotificationStack notifications={notifications} onClose={() => setIsOpen(false)} onClear={() => { markRead(); setIsOpen(false); }} /></>)}
        </AnimatePresence>
      </div>
      <button onClick={() => onTabChange('settings')} className={`p-2 rounded-full transition-all ${activeTab === 'settings' ? 'bg-primary/10 text-primary' : 'text-on-surface-variant hover:bg-surface-container-high hover:text-on-surface'}`}>
        <Settings2 className="w-5 h-5" />
      </button>
      <div className="relative">
        <button onClick={() => setShowHelp(!showHelp)} className={`p-2 rounded-full transition-all ${showHelp ? 'bg-primary/10 text-primary' : 'text-on-surface-variant hover:bg-surface-container-high hover:text-on-surface'}`}>
          <HelpCircle className="w-5 h-5" />
        </button>
        <AnimatePresence>
          {showHelp && (<><div className="fixed inset-0 z-[105]" onClick={() => setShowHelp(false)} /><motion.div initial={{ opacity: 0, y: 10, scale: 0.95 }} animate={{ opacity: 1, y: 0, scale: 1 }} exit={{ opacity: 0, y: 10, scale: 0.95 }} className="absolute top-full right-0 mt-2 w-64 bg-surface-container-high rounded-xl shadow-2xl border border-outline-variant/20 z-[110] p-4">
            <div className="flex items-center gap-2 mb-3 text-primary"><LifeBuoy className="w-4 h-4" /><h3 className="font-headline font-bold text-sm">System Help</h3></div>
            <p className="text-xs text-on-surface-variant leading-relaxed mb-4">Welcome to the MediaFlow terminal. Use the sidebar to navigate between your assets, ingestion tasks, and system logs.</p>
            <button className="w-full py-2 bg-surface-container-highest rounded text-[10px] font-label uppercase tracking-widest text-on-surface hover:bg-surface-dim transition-colors">Open Documentation</button>
          </motion.div></>)}
        </AnimatePresence>
      </div>
    </div>
  );
}

// --- Small Helpers ---
export function FilterChip({ label, value, onRemove }) {
  return (
    <div className="flex items-center gap-1.5 bg-surface-container-highest px-3 py-1.5 rounded-md text-xs font-label text-on-surface shadow-sm border border-outline-variant/10">
      <span className="text-on-surface-variant">{label}:</span> {value}
      <button onClick={onRemove} className="hover:text-error transition-colors ml-1"><X className="w-3.5 h-3.5" /></button>
    </div>
  );
}

export function TagChip({ label }) {
  return <span className="bg-surface-container text-on-surface text-xs font-label px-2.5 py-1.5 rounded-md border border-outline-variant/20 hover:border-primary/50 cursor-pointer transition-colors">{label}</span>;
}

export function SpecItem({ label, value }) {
  return (
    <div>
      <span className="text-on-surface-variant block mb-1 uppercase tracking-wider text-[10px]">{label}</span>
      <span className="text-on-surface font-medium bg-surface-container px-2 py-1 rounded inline-block w-full">{value}</span>
    </div>
  );
}
