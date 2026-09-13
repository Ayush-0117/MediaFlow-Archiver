import { useState, useEffect } from 'react';
import { motion } from 'motion/react';
import {
  Search, Cpu, BrainCircuit, HardDrive, Cloud, History, ExternalLink,
  Settings2, ChevronRight
} from 'lucide-react';
import { UtilityNav } from '../components/Shared';
import { fetchSettings, updateSettings, fetchAiStatus, testGeminiConnection } from '../api';

export default function Settings({ notifications, markRead, activeTab, onTabChange, onNotify }) {
  const [settings, setSettings] = useState({});
  const [aiStats, setAiStats] = useState({});
  const [dirty, setDirty] = useState(false);
  const [testingApi, setTestingApi] = useState(false);

  const handleTestApi = async () => {
    setTestingApi(true);
    try {
      if (dirty) {
        const updated = await updateSettings(settings);
        setSettings(updated);
        setDirty(false);
      }
      const res = await testGeminiConnection();
      onNotify('API Test', res.message, 'success');
    } catch (err) {
      onNotify('API Test Failed', err.message, 'error');
    } finally {
      setTestingApi(false);
    }
  };

  useEffect(() => {
    fetchSettings().then(setSettings).catch(console.error);
    fetchAiStatus().then(setAiStats).catch(console.error);
  }, []);

  const handleChange = (key, value) => {
    setSettings(prev => ({ ...prev, [key]: value }));
    setDirty(true);
  };

  const handleSave = async () => {
    try {
      const updated = await updateSettings(settings);
      setSettings(updated);
      setDirty(false);
      onNotify('Settings', 'Configuration saved successfully.', 'success');
    } catch (err) {
      onNotify('Error', 'Failed to save: ' + err.message, 'error');
    }
  };

  const archiveUsedGB = aiStats.archiveSizeBytes ? (aiStats.archiveSizeBytes / 1e9).toFixed(1) : '0';
  const proxyUsedMB = aiStats.proxySizeMB || '0';

  return (
    <div className="flex-1 flex flex-col h-full bg-surface overflow-hidden">
      <header className="px-8 h-14 border-b border-surface-container flex justify-between items-center shrink-0 gap-8 bg-surface-dim/80 backdrop-blur-md z-30">
        <div className="flex-1 relative flex items-center">
          <Search className="absolute left-3 w-4 h-4 text-on-surface-variant" />
          <input className="w-full bg-surface-container-lowest border border-outline-variant/20 rounded-md py-1.5 pl-10 pr-4 text-sm focus:outline-none focus:border-primary/40 focus:ring-1 focus:ring-primary/40 transition-colors" placeholder="Search archive..." />
        </div>
        <UtilityNav notifications={notifications} markRead={markRead} activeTab={activeTab} onTabChange={onTabChange} />
      </header>

      <main className="flex-1 overflow-y-auto bg-surface p-8 md:p-12 custom-scrollbar">
        <div className="mb-12 flex flex-col md:flex-row justify-between items-start md:items-end gap-6">
          <div className="max-w-3xl">
            <h1 className="font-headline text-4xl font-black tracking-tight text-on-surface mb-3">System Settings</h1>
            <p className="text-on-surface-variant text-base leading-relaxed">Configure core parameters for the ingestion engine, AI processing, and storage topology.</p>
          </div>
          <div className="flex gap-4 w-full md:w-auto">
            <button onClick={() => { fetchSettings().then(setSettings); setDirty(false); }} className="flex-1 md:flex-none px-6 py-2.5 bg-transparent border border-outline-variant/20 text-on-surface font-label text-sm uppercase tracking-widest rounded-md hover:bg-surface-container-high transition-colors">Discard Changes</button>
            <button onClick={handleSave} disabled={!dirty} className={`flex-1 md:flex-none px-6 py-2.5 font-label text-sm uppercase tracking-widest rounded-md shadow-lg transition-all ${dirty ? 'bg-gradient-to-r from-primary to-primary-container text-on-primary hover:brightness-110' : 'bg-surface-container text-on-surface-variant cursor-not-allowed'}`}>Save Configuration</button>
          </div>
        </div>

        <div className="grid grid-cols-1 xl:grid-cols-12 gap-8">
          <div className="xl:col-span-8 flex flex-col gap-8">
            {/* Archive Path */}
            <section className="bg-surface-container-low rounded-2xl p-8 border border-surface-dim">
              <h2 className="font-headline text-xl font-black text-primary mb-8 flex items-center gap-3 border-b border-surface-container pb-4"><HardDrive className="w-6 h-6" /> Storage Configuration</h2>
              <div className="space-y-8">
                <div className="grid grid-cols-1 sm:grid-cols-3 gap-6 items-start">
                  <div className="sm:col-span-1">
                    <label className="block font-label text-xs uppercase tracking-widest text-on-surface-variant font-bold mb-1.5">Archive Path</label>
                    <p className="text-xs text-on-surface/50">Root directory for all ingested media.</p>
                  </div>
                  <div className="sm:col-span-2">
                    <div className="bg-surface-container-lowest rounded-lg border border-outline-variant/30 flex items-center focus-within:border-primary/40 focus-within:ring-2 focus-within:ring-primary/20 transition-all overflow-hidden group">
                      <HardDrive className="text-on-surface-variant ml-4 w-4 h-4 opacity-50 group-focus-within:opacity-100" />
                      <input type="text" value={settings.archivePath || ''} onChange={e => handleChange('archivePath', e.target.value)} className="bg-transparent border-none text-on-surface font-body text-sm w-full py-3 px-4 focus:ring-0 focus:outline-none" />
                    </div>
                  </div>
                </div>
              </div>
            </section>

            {/* Ingestion Engine */}
            <section className="bg-surface-container-low rounded-2xl p-8 border border-surface-dim">
              <h2 className="font-headline text-xl font-black text-primary mb-8 flex items-center gap-3 border-b border-surface-container pb-4"><Cpu className="w-6 h-6" /> Ingestion Engine</h2>
              <div className="space-y-8">
                <div className="grid grid-cols-1 sm:grid-cols-3 gap-6 items-start">
                  <div className="sm:col-span-1">
                    <label className="block font-label text-xs uppercase tracking-widest text-on-surface-variant font-bold mb-1.5">Concurrency Limit</label>
                    <p className="text-xs text-on-surface/50">Maximum parallel processing threads.</p>
                  </div>
                  <div className="sm:col-span-2">
                    <div className="bg-surface-container-lowest rounded-lg border border-outline-variant/30 flex items-center focus-within:border-primary/40 focus-within:ring-2 focus-within:ring-primary/20 transition-all overflow-hidden group">
                      <History className="text-on-surface-variant ml-4 w-4 h-4 opacity-50 group-focus-within:opacity-100" />
                      <input type="number" defaultValue="16" className="bg-transparent border-none text-on-surface font-body text-sm w-full py-3 px-4 focus:ring-0 focus:outline-none" />
                    </div>
                  </div>
                </div>
                <div className="grid grid-cols-1 sm:grid-cols-3 gap-6 items-start pt-8 border-t border-surface-container/50">
                  <div className="sm:col-span-1">
                    <label className="block font-label text-xs uppercase tracking-widest text-on-surface-variant font-bold mb-1.5">Retry Policy</label>
                    <p className="text-xs text-on-surface/50">Attempts before marking as failed.</p>
                  </div>
                  <div className="sm:col-span-2">
                    <select className="bg-surface-container-lowest border border-outline-variant/30 text-on-surface font-body text-sm rounded-lg w-full py-3 px-4 focus:border-primary/40 focus:ring-2 focus:ring-primary/20 appearance-none transition-all outline-none">
                      <option value="1">1 Attempt (Strict)</option>
                      <option selected value="3">3 Attempts (Standard)</option>
                      <option value="5">5 Attempts (Aggressive)</option>
                    </select>
                  </div>
                </div>
              </div>
            </section>

            {/* AI Processing */}
            <section className="bg-surface-container-low rounded-2xl p-8 border border-surface-dim">
              <div className="flex justify-between items-center mb-8 border-b border-surface-container pb-4">
                <h2 className="font-headline text-xl font-black text-primary flex items-center gap-3"><BrainCircuit className="w-6 h-6" /> AI Processing Matrix</h2>
                <label className="relative inline-flex items-center cursor-pointer">
                  <input type="checkbox" defaultChecked className="sr-only peer" />
                  <div className="w-12 h-6 bg-surface-container-highest peer-focus:outline-none rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:rounded-full after:h-5 after:w-5 after:transition-all peer-checked:bg-primary shadow-inner"></div>
                  <span className="ml-3 text-sm font-label uppercase tracking-widest text-primary font-bold">Enabled</span>
                </label>
              </div>
              <div className="space-y-8">
                <div className="grid grid-cols-1 sm:grid-cols-3 gap-6 items-start">
                  <div className="sm:col-span-1">
                    <label className="block font-label text-xs uppercase tracking-widest text-on-surface-variant font-bold mb-1.5">Gemini API Key</label>
                    <p className="text-xs text-on-surface/50">Set via GEMINI_API_KEY env variable.</p>
                  </div>
                  <div className="sm:col-span-2">
                    <div className="bg-surface-container-lowest rounded-lg border border-outline-variant/30 flex items-center focus-within:border-primary/40 focus-within:ring-2 focus-within:ring-primary/20 transition-all overflow-hidden group">
                      <Settings2 className="text-on-surface-variant ml-4 w-4 h-4 opacity-50 group-focus-within:opacity-100" />
                      <input 
                        type="password" 
                        value={settings.geminiApiKey || ''} 
                        onChange={e => handleChange('geminiApiKey', e.target.value)} 
                        placeholder="Leave blank to use environment variable..."
                        className="bg-transparent border-none text-on-surface font-body text-sm w-full py-3 px-4 focus:ring-0 focus:outline-none placeholder:text-on-surface-variant/40" 
                      />
                      <button 
                        onClick={handleTestApi} 
                        disabled={testingApi}
                        className="bg-primary/10 hover:bg-primary/20 text-primary px-4 py-2 text-xs font-label uppercase tracking-widest font-bold whitespace-nowrap transition-colors"
                      >
                        {testingApi ? 'Testing...' : 'Test Connection'}
                      </button>
                    </div>
                  </div>
                </div>
                <div className="mt-8">
                  <label className="block font-label text-xs uppercase tracking-widest text-on-surface-variant font-bold mb-3">Custom System Prompt (JSON)</label>
                  <div className="bg-surface-container-lowest rounded-xl border border-outline-variant/30 p-1 focus-within:border-primary/40 transition-all overflow-hidden shadow-inner">
                    <div className="bg-surface-container px-4 py-2 text-xs font-label text-on-surface-variant rounded-t-lg flex justify-between items-center opacity-70">
                      <span className="flex items-center gap-2"><Settings2 className="w-3 h-3" /> system_override.json</span>
                      <button className="hover:text-primary transition-colors"><ChevronRight className="w-3 h-3 rotate-90" /></button>
                    </div>
                    <textarea className="w-full bg-transparent text-primary font-mono text-[11px] border-none p-5 focus:ring-0 resize-none h-48 leading-relaxed" spellCheck="false" defaultValue={`{\n  "confidence_threshold": 0.85,\n  "fallback_behavior": "quarantine",\n  "extract_metadata": ["geo", "timestamp", "entities"],\n  "strict_mode": true\n}`} />
                  </div>
                </div>
              </div>
            </section>
          </div>

          {/* Right Status Panel */}
          <div className="xl:col-span-4 flex flex-col gap-8">
            <div className="bg-surface-container-high rounded-2xl p-8 border border-outline-variant/10 shadow-xl">
              <h3 className="font-headline text-base font-black text-on-surface mb-6 flex items-center gap-2"><History className="w-4 h-4 text-primary" /> System Status</h3>
              <ul className="space-y-6">
                <li className="flex justify-between items-center">
                  <span className="text-sm text-on-surface-variant font-medium opacity-80">Engine State</span>
                  <span className="bg-primary/20 text-primary px-3 py-1 rounded text-[10px] font-bold uppercase tracking-widest">{aiStats.workerActive ? 'Processing' : 'Online'}</span>
                </li>
                <li className="flex justify-between items-center">
                  <span className="text-sm text-on-surface-variant font-medium opacity-80">Total Assets</span>
                  <span className="font-mono text-primary font-bold">{aiStats.totalAssets || 0}</span>
                </li>
                <li className="flex justify-between items-center">
                  <span className="text-sm text-on-surface-variant font-medium opacity-80">AI Completed</span>
                  <span className="font-mono text-on-surface font-bold">{aiStats.completed || 0} / {aiStats.totalAssets || 0}</span>
                </li>
                <li className="flex justify-between items-center">
                  <span className="text-sm text-on-surface-variant font-medium opacity-80">Failed</span>
                  <span className="font-mono text-error font-bold">{aiStats.failed || 0}</span>
                </li>
                <li className="flex justify-between items-center">
                  <span className="text-sm text-on-surface-variant font-medium opacity-80">Requests Today</span>
                  <span className="font-mono text-on-surface font-bold">{aiStats.requestsToday || 0}</span>
                </li>
              </ul>
            </div>

            <section className="bg-surface-container-low rounded-2xl p-8 flex-1 border border-surface-dim">
              <h2 className="font-headline text-xl font-black text-primary mb-8 flex items-center gap-3 border-b border-surface-container pb-4"><HardDrive className="w-6 h-6" /> Storage Topology</h2>
              <div className="space-y-6">
                <VolumeCard title="Archive Volume" path={settings.archivePath || '—'} usage={`${archiveUsedGB} GB used`} icon={<HardDrive className="w-3.5 h-3.5 text-primary" />} color="bg-primary" />
                <VolumeCard title="Proxy Cache" path="PROXIES/" usage={`${proxyUsedMB} MB`} icon={<Cloud className="w-3.5 h-3.5 text-tertiary-container" />} color="bg-tertiary-container" />
              </div>
            </section>
          </div>
        </div>
      </main>
    </div>
  );
}

function VolumeCard({ title, path, usage, icon, color }) {
  return (
    <div className="bg-surface-container-lowest rounded-xl p-5 border border-outline-variant/20 hover:border-primary/30 transition-all group">
      <div className="flex items-center justify-between mb-4">
        <span className="font-headline font-bold text-sm text-on-surface flex items-center gap-2 group-hover:text-primary transition-colors">{icon} {title}</span>
        <span className="text-[10px] font-mono text-on-surface-variant opacity-50 truncate max-w-32">{path}</span>
      </div>
      <div className="text-[10px] uppercase font-label tracking-widest text-on-surface-variant text-right font-bold">{usage}</div>
    </div>
  );
}
