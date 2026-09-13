import { useState } from 'react';
import { motion } from 'motion/react';
import { Search, Terminal, Cpu, Download, Trash2 } from 'lucide-react';
import { UtilityNav } from '../components/Shared';

export default function LogsWindow({ onNotify, notifications, markRead, activeTab, onTabChange, sseLogs, clearLogs }) {
  const [filter, setFilter] = useState('all');
  const [search, setSearch] = useState('');

  const filteredLogs = sseLogs.filter(log => {
    const matchesFilter = filter === 'all' || log.type === filter;
    const matchesSearch = log.text.toLowerCase().includes(search.toLowerCase());
    return matchesFilter && matchesSearch;
  });

  return (
    <div className="flex-1 flex flex-col h-full bg-surface-dim overflow-hidden">
      <header className="px-8 h-14 border-b border-surface-container flex justify-between items-center shrink-0 gap-8 bg-surface-dim/80 backdrop-blur-md">
        <div className="flex-1 relative flex items-center">
          <Search className="absolute left-3 w-4 h-4 text-on-surface-variant" />
          <input className="w-full bg-surface-container-lowest border border-outline-variant/20 rounded-md py-1.5 pl-10 pr-4 text-sm focus:outline-none focus:border-primary/40 focus:ring-1 focus:ring-primary/40 transition-colors" placeholder="Search archive..." />
        </div>
        <UtilityNav notifications={notifications} markRead={markRead} activeTab={activeTab} onTabChange={onTabChange} />
      </header>

      <div className="flex-1 flex flex-col overflow-hidden">
        <div className="px-8 py-8 flex flex-col md:flex-row md:items-end justify-between gap-6 shrink-0 bg-surface-dim">
          <div>
            <h1 className="font-headline font-black text-3xl tracking-tight text-on-surface mb-2">System Console</h1>
            <div className="flex items-center gap-4 text-xs font-label text-on-surface-variant">
              <span className="flex items-center gap-1.5"><Terminal className="w-3.5 h-3.5" /> root@mediaarchivist</span>
              <span className="opacity-30">•</span>
              <span className="flex items-center gap-1.5"><Cpu className="w-3.5 h-3.5" /> {sseLogs.length} events captured</span>
              <span className="opacity-30">•</span>
              <span className="text-primary font-bold">LIVE</span>
            </div>
          </div>

          <div className="flex items-center gap-3">
            <div className="flex bg-surface-container rounded-lg p-1 border border-outline-variant/10 shadow-inner">
              <button onClick={() => setFilter('all')} className={`px-4 py-1.5 rounded-md text-xs font-label transition-all ${filter === 'all' ? 'bg-surface-dim text-primary shadow-sm' : 'text-on-surface-variant hover:text-on-surface'}`}>All</button>
              <button onClick={() => setFilter('error')} className={`px-4 py-1.5 rounded-md text-xs font-label transition-all ${filter === 'error' ? 'bg-error/10 text-error' : 'text-on-surface-variant hover:text-on-surface'}`}>Errors</button>
              <button onClick={() => setFilter('warning')} className={`px-4 py-1.5 rounded-md text-xs font-label transition-all ${filter === 'warning' ? 'bg-secondary-container/10 text-secondary-container' : 'text-on-surface-variant hover:text-on-surface'}`}>Warnings</button>
            </div>
            <button onClick={() => onNotify('System', 'Log archive exported.', 'success')} className="p-2.5 bg-surface-container rounded-lg border border-outline-variant/20 hover:text-primary transition-colors"><Download className="w-4 h-4" /></button>
            <button onClick={clearLogs} className="p-2.5 bg-surface-container rounded-lg border border-outline-variant/20 hover:text-tertiary transition-colors"><Trash2 className="w-4 h-4" /></button>
          </div>
        </div>

        <div className="flex-1 flex flex-col overflow-hidden mx-8 mb-8 rounded-2xl border border-surface-container-high bg-black/40 shadow-2xl">
          <div className="px-6 py-3 border-b border-surface-container-high flex items-center justify-between bg-surface-container-low/50">
            <div className="flex gap-1.5">
              <div className="w-2.5 h-2.5 rounded-full bg-error/30" />
              <div className="w-2.5 h-2.5 rounded-full bg-secondary-container/30" />
              <div className="w-2.5 h-2.5 rounded-full bg-primary/30" />
            </div>
            <div className="relative flex items-center group">
              <Search className="absolute left-3 w-3.5 h-3.5 text-on-surface-variant group-focus-within:text-primary transition-colors" />
              <input value={search} onChange={(e) => setSearch(e.target.value)} placeholder="Filter logs..." className="bg-transparent border-none text-[11px] font-mono text-on-surface py-1 pl-9 pr-2 focus:ring-0 w-48 transition-all focus:w-64" />
            </div>
          </div>

          <div className="flex-1 overflow-y-auto p-6 font-mono text-xs leading-relaxed custom-scrollbar">
            {filteredLogs.length === 0 ? (
              <div className="text-on-surface-variant/30 italic">
                {sseLogs.length === 0 ? 'Listening for events from the backend SSE stream...' : 'No logs match your filter.'}
              </div>
            ) : (
              filteredLogs.map((log, i) => (
                <motion.div initial={{ opacity: 0, x: -10 }} animate={{ opacity: 1, x: 0 }} transition={{ delay: i * 0.01 }} key={i}
                  className={`flex gap-4 py-1.5 border-b border-white/[0.02] last:border-none group ${
                    log.type === 'error' ? 'text-error' :
                    log.type === 'warning' ? 'text-secondary-container' :
                    log.type === 'success' ? 'text-primary' : 'text-on-surface-variant'
                  }`}>
                  <span className="opacity-30 select-none tabular-nums shrink-0">{log.time}</span>
                  <span className="opacity-20 select-none shrink-0 group-hover:opacity-60 transition-opacity">[{log.type.toUpperCase()}]</span>
                  <span className="flex-1 break-all">{log.text}</span>
                </motion.div>
              ))
            )}
            <div className="mt-4 flex items-center gap-2 text-primary font-bold"><span className="animate-pulse">_</span></div>
          </div>
        </div>
      </div>
    </div>
  );
}
