import { useState, useEffect, useRef, useCallback } from 'react';
import { motion, AnimatePresence } from 'motion/react';
import {
  Search, PlayCircle, ListRestart, CheckCircle2, RefreshCw, Info,
  Image as ImageIcon, Terminal, Loader2, AlertCircle, FolderInput, FolderOpen,
  StopCircle, Square
} from 'lucide-react';
import { UtilityNav, TagChip } from '../components/Shared';
import { fetchMedia, fetchAiStatus, proxyUrl, triggerIngest, retryFailed, stopIngestion } from '../api';

const IS_TAURI = '__TAURI__' in window || '__TAURI_INTERNALS__' in window;

export default function IngestionQueue({ notifications, markRead, activeTab, onTabChange, sseLogs, onNotify }) {
  const [assets, setAssets] = useState([]);
  const [selectedAsset, setSelectedAsset] = useState(null);
  const [loading, setLoading] = useState(true);
  const [ingestPath, setIngestPath] = useState('');
  const [isStopping, setIsStopping] = useState(false);
  const [workerActive, setWorkerActive] = useState(false);

  // Track known asset IDs so we only animate genuinely new items
  const knownIdsRef = useRef(new Set());
  const refreshTimerRef = useRef(null);

  const loadAssets = useCallback(async () => {
    try {
      if (assets.length === 0) setLoading(true);
      const [mediaData, aiStats] = await Promise.all([
        fetchMedia(0, 100),
        fetchAiStatus().catch(() => null),
      ]);
      const all = (mediaData.content || []).sort((a, b) => a.id - b.id);

      setAssets(all);
      if (all.length > 0 && !selectedAsset) setSelectedAsset(all[0]);

      // Update worker/stop state from backend
      if (aiStats) {
        setWorkerActive(aiStats.workerActive);
        if (!aiStats.workerActive) setIsStopping(false);
        if (aiStats.stopRequested) setIsStopping(true);
      }
    } catch (err) {
      console.error('Failed to load queue:', err);
    } finally {
      setLoading(false);
    }
  }, []);

  // Update selectedAsset when assets change
  useEffect(() => {
    if (selectedAsset) {
      const updated = assets.find(a => a.id === selectedAsset.id);
      if (updated) setSelectedAsset(updated);
    }
  }, [assets]);

  // After rendering, update the known IDs set so next render knows which are new
  useEffect(() => {
    const newSet = new Set(assets.map(a => a.id));
    knownIdsRef.current = newSet;
  }, [assets]);

  useEffect(() => { loadAssets(); }, []);

  // Debounced auto-refresh when SSE events arrive — prevents rapid-fire full re-renders
  useEffect(() => {
    if (sseLogs.length > 0) {
      if (refreshTimerRef.current) clearTimeout(refreshTimerRef.current);
      refreshTimerRef.current = setTimeout(loadAssets, 2000);
      return () => {
        if (refreshTimerRef.current) clearTimeout(refreshTimerRef.current);
      };
    }
  }, [sseLogs.length]);

  /** Opens the native OS folder picker (Tauri) or falls back to manual text input */
  const handleBrowseFolder = async () => {
    if (!IS_TAURI) return; // Text input is shown instead in browser mode
    try {
      const { open } = await import('@tauri-apps/plugin-dialog');
      const selected = await open({
        directory: true,
        multiple: false,
        title: 'Select Media Folder to Ingest',
      });
      if (selected) {
        setIngestPath(selected);
      }
    } catch (err) {
      console.error('Folder dialog failed:', err);
      onNotify('Error', 'Could not open folder picker: ' + err.message, 'error');
    }
  };

  const handleIngest = async () => {
    if (!ingestPath.trim()) {
      // If path is empty and we're in Tauri, auto-open the folder picker first
      if (IS_TAURI) {
        await handleBrowseFolder();
        return;
      }
      return;
    }
    try {
      await triggerIngest(ingestPath);
      onNotify('Ingest', `Pipeline dispatched for: ${ingestPath}`, 'success');
      setIngestPath('');
    } catch (err) {
      onNotify('Error', 'Ingest failed: ' + err.message, 'error');
    }
  };

  const handleRetry = async () => {
    try {
      const res = await retryFailed();
      onNotify('AI', res.message || 'Retry queued.', 'success');
      setTimeout(loadAssets, 1000);
    } catch (err) {
      onNotify('Error', 'Retry failed: ' + err.message, 'error');
    }
  };

  const handleStop = async () => {
    try {
      setIsStopping(true);
      const res = await stopIngestion();
      onNotify('AI', res.message || 'Stop requested.', 'warning');
    } catch (err) {
      setIsStopping(false);
      onNotify('Error', 'Stop failed: ' + err.message, 'error');
    }
  };

  const processing = assets.filter(a => a.status === 'PROCESSING' || a.status === 'PENDING_AI');
  const completed = assets.filter(a => a.status === 'COMPLETED');
  const failed = assets.filter(a => a.status === 'FAILED');

  const statusColor = (s) => s === 'COMPLETED' ? 'text-secondary-container' : s === 'PROCESSING' ? 'text-primary' : s === 'FAILED' ? 'text-error' : 'text-on-surface-variant';

  const aiTags = (() => { try { return selectedAsset?.aiTagsJson ? JSON.parse(selectedAsset.aiTagsJson) : null; } catch { return null; } })();

  // Determine which items are new (for entrance animation)
  const isNewItem = (id) => !knownIdsRef.current.has(id);

  return (
    <div className="flex-1 flex flex-col md:ml-0 bg-surface h-full overflow-hidden">
      <header className="px-8 h-14 border-b border-surface-container flex justify-between items-center shrink-0 gap-8 bg-surface-dim/80 backdrop-blur-md">
        <div className="flex-1 relative flex items-center">
          <Search className="absolute left-3 w-4 h-4 text-on-surface-variant" />
          <input className="w-full bg-surface-container-lowest border border-outline-variant/20 rounded-md py-1.5 pl-10 pr-4 text-sm focus:outline-none focus:border-primary/40 focus:ring-1 focus:ring-primary/40 transition-colors" placeholder="Search archive..." />
        </div>
        <UtilityNav notifications={notifications} markRead={markRead} activeTab={activeTab} onTabChange={onTabChange} />
      </header>

      <div className="flex-1 flex flex-col overflow-hidden">
        <header className="px-8 py-6 border-b border-surface-container-low flex flex-wrap justify-between items-center shrink-0 bg-surface gap-4">
          <div>
            <h1 className="font-headline font-black text-3xl tracking-tight text-on-surface mb-1">Processing Queue</h1>
            <p className="font-label text-on-surface-variant text-sm flex items-center gap-2">
              <span className={`w-2 h-2 rounded-full ${isStopping ? 'bg-error animate-pulse' : processing.length > 0 ? 'bg-secondary-container animate-pulse' : 'bg-primary'}`} />
              {isStopping ? (
                <span className="text-error font-bold">Stopping after current asset...</span>
              ) : (
                <>{processing.length} assets pending / {completed.length} completed / {failed.length} failed</>
              )}
            </p>
          </div>
          <div className="flex items-center gap-3">
            {/* Folder Picker — native dialog in Tauri, text input fallback in browser */}
            {IS_TAURI ? (
              <div className="flex items-center bg-surface-container-low border border-outline-variant rounded-md overflow-hidden focus-within:border-primary/40">
                <button onClick={handleBrowseFolder} className="flex items-center gap-2 px-3 py-2 bg-surface-container hover:bg-surface-container-high transition-colors border-r border-outline-variant/30" title="Open folder picker">
                  <FolderOpen className="w-4 h-4 text-primary" />
                  <span className="text-xs font-label text-on-surface-variant">Browse</span>
                </button>
                <span className={`text-sm py-2 px-3 min-w-[180px] max-w-[280px] truncate ${ingestPath ? 'text-on-surface font-mono text-[11px]' : 'text-on-surface-variant/50 italic text-xs'}`}>
                  {ingestPath || 'No folder selected'}
                </span>
              </div>
            ) : (
              <div className="flex items-center bg-surface-container-low border border-outline-variant rounded-md overflow-hidden focus-within:border-primary/40">
                <FolderInput className="w-4 h-4 text-on-surface-variant ml-3" />
                <input value={ingestPath} onChange={e => setIngestPath(e.target.value)} onKeyDown={e => e.key === 'Enter' && handleIngest()} placeholder="/path/to/folder" className="bg-transparent text-sm text-on-surface py-2 px-3 focus:outline-none w-56" />
              </div>
            )}
            <button onClick={handleIngest} className="bg-gradient-to-r from-primary to-primary-container text-on-primary px-4 py-2 rounded-md font-label text-sm flex items-center gap-2 hover:brightness-110 transition-all">
              <PlayCircle className="w-4 h-4" /> Ingest
            </button>

            {/* Stop Button — only shown/active when worker is processing */}
            <button
              onClick={handleStop}
              disabled={isStopping || (!workerActive && processing.length === 0)}
              className={`relative px-4 py-2 rounded-md font-label text-sm flex items-center gap-2 transition-all overflow-hidden ${
                isStopping
                  ? 'bg-error/20 text-error border border-error/40 cursor-wait'
                  : workerActive || processing.length > 0
                    ? 'bg-error/10 text-error border border-error/30 hover:bg-error/20 hover:border-error/50 cursor-pointer'
                    : 'bg-surface-container-low text-on-surface-variant/40 border border-outline-variant/20 cursor-not-allowed'
              }`}
            >
              {isStopping && (
                <span className="absolute inset-0 bg-error/5 animate-pulse rounded-md" />
              )}
              {isStopping ? (
                <Loader2 className="w-4 h-4 animate-spin relative z-10" />
              ) : (
                <Square className="w-3.5 h-3.5 relative z-10" />
              )}
              <span className="relative z-10">{isStopping ? 'Stopping...' : 'Stop'}</span>
            </button>

            <button onClick={handleRetry} className="bg-surface-container-low border border-outline-variant hover:border-primary/40 text-on-surface px-4 py-2 rounded-md font-label text-sm flex items-center gap-2 transition-colors">
              <ListRestart className="w-4 h-4" /> Retry Failed
            </button>
            <button onClick={loadAssets} className="bg-surface-variant text-on-surface hover:bg-surface-container-highest px-4 py-2 rounded-md font-label text-sm flex items-center gap-2 transition-colors">
              <RefreshCw className="w-4 h-4" /> Refresh
            </button>
          </div>
        </header>

        <div className="flex-1 flex overflow-hidden">
          {/* Queue Table */}
          <div className="flex-1 flex flex-col overflow-y-auto p-8 gap-1 custom-scrollbar bg-surface border-r border-surface-container-low shadow-[inset_0_0_40px_rgba(0,0,0,0.2)]">
            <div className="mb-4 grid grid-cols-12 px-6 py-2 border-b border-surface-container-highest uppercase tracking-[0.2em] text-[10px] font-bold text-on-surface-variant/40">
              <div className="col-span-1">ID</div>
              <div className="col-span-5">Asset / Resource</div>
              <div className="col-span-3">Status</div>
              <div className="col-span-3 text-right">Type</div>
            </div>
            {loading ? (
              <div className="flex items-center justify-center py-20"><Loader2 className="w-8 h-8 text-primary animate-spin" /></div>
            ) : assets.length === 0 ? (
              <div className="text-center py-20 text-on-surface-variant text-sm">No assets in the system. Ingest a folder to begin.</div>
            ) : (
              <AnimatePresence initial={false}>
                {assets.map((item) => {
                  const isNew = isNewItem(item.id);
                  return (
                    <motion.div
                      key={item.id}
                      layout
                      initial={isNew ? { opacity: 0, x: -20, height: 0 } : false}
                      animate={{ opacity: 1, x: 0, height: 'auto' }}
                      exit={{ opacity: 0, x: 20, height: 0 }}
                      transition={{
                        layout: { type: 'spring', stiffness: 500, damping: 35 },
                        opacity: { duration: 0.25 },
                        x: { duration: 0.25 },
                        height: { duration: 0.2 },
                      }}
                      onClick={() => setSelectedAsset(item)}
                      className={`grid grid-cols-12 gap-4 px-6 py-4 items-center group relative border-b border-surface-container-low transition-colors hover:bg-surface-container-low/30 cursor-pointer ${
                        selectedAsset?.id === item.id ? 'bg-primary/10 border-l-2 border-l-primary' : 'border-l-2 border-l-transparent'
                      }`}
                    >
                      <div className="col-span-1 font-mono text-[10px] text-on-surface-variant font-bold">#{item.id}</div>
                      <div className="col-span-5 flex items-center gap-4">
                        <div className="relative w-14 h-10 rounded-sm bg-surface-container-lowest border border-outline-variant/30 overflow-hidden shrink-0">
                          {item.sha256Hash ? (
                            <img src={proxyUrl(item.sha256Hash)} alt="" className="w-full h-full object-cover opacity-60 group-hover:opacity-100 transition-opacity duration-500" loading="lazy" />
                          ) : (
                            <div className="w-full h-full flex items-center justify-center"><ImageIcon className="w-4 h-4 text-on-surface-variant opacity-30" /></div>
                          )}
                        </div>
                        <div className="min-w-0">
                          <h3 className="font-headline font-bold text-sm text-on-surface truncate mb-0.5">{item.originalFilename}</h3>
                          <p className="text-[10px] text-on-surface-variant uppercase tracking-wider font-label opacity-60">{item.fileType || 'Unknown'}</p>
                        </div>
                      </div>
                      <div className="col-span-3">
                        <motion.span
                          key={item.status}
                          initial={{ opacity: 0, scale: 0.8 }}
                          animate={{ opacity: 1, scale: 1 }}
                          transition={{ duration: 0.2 }}
                          className={`text-[10px] font-bold uppercase tracking-widest ${statusColor(item.status)}`}
                        >
                          {item.status}
                        </motion.span>
                      </div>
                      <div className="col-span-3 text-right">
                        {item.status === 'COMPLETED' ? <CheckCircle2 className="inline w-4 h-4 text-secondary-container" /> :
                         item.status === 'PROCESSING' || item.status === 'PENDING_AI' ? <Loader2 className="inline w-4 h-4 text-primary animate-spin" /> :
                         item.status === 'FAILED' ? <AlertCircle className="inline w-4 h-4 text-error" /> :
                         <span className="font-mono text-[10px] text-on-surface-variant/40 italic">—</span>}
                      </div>
                    </motion.div>
                  );
                })}
              </AnimatePresence>
            )}
          </div>

          {/* Inspector Panel */}
          <div className="w-[450px] flex flex-col bg-surface-container-low shrink-0 z-10 border-l border-surface-dim overflow-y-auto custom-scrollbar">
            <div className="p-8 border-b border-surface-container-highest">
              <div className="flex items-center gap-2 mb-8"><Info className="w-6 h-6 text-primary" /><h2 className="font-headline font-black text-xl text-on-surface">Asset Inspector</h2></div>
              {selectedAsset ? (
                <div className="space-y-6">
                  <div className="grid grid-cols-3 gap-3 text-sm font-label">
                    <div className="text-on-surface-variant">File Size</div>
                    <div className="col-span-2 font-mono text-[11px] text-on-surface">{selectedAsset.fileSize ? `${(selectedAsset.fileSize / 1e6).toFixed(2)} MB (${selectedAsset.fileSize.toLocaleString()} B)` : '—'}</div>
                  </div>
                  <div className="grid grid-cols-3 gap-3 text-sm font-label">
                    <div className="text-on-surface-variant">Path</div>
                    <div className="col-span-2 font-mono text-[11px] bg-surface-container-lowest p-3 rounded border border-outline-variant/30 break-all">{selectedAsset.relativePath || '—'}</div>
                  </div>
                  <div className="grid grid-cols-3 gap-3 text-sm font-label">
                    <div className="text-on-surface-variant">Checksum</div>
                    <div className="col-span-2 font-mono text-[11px] text-primary bg-surface-container-lowest p-3 rounded border border-primary/20 font-bold break-all">SHA256: {selectedAsset.sha256Hash || '—'}</div>
                  </div>
                  {selectedAsset.metadata && (
                    <div className="pt-6 border-t border-surface-container-highest">
                      <h3 className="font-label text-xs text-on-surface-variant mb-4 uppercase tracking-widest opacity-60">Exif Data</h3>
                      <div className="grid grid-cols-3 gap-y-3 text-[11px] font-label">
                        {selectedAsset.metadata.cameraMake && <><span className="text-on-surface-variant">Device</span><span className="col-span-2 text-on-surface font-mono">{selectedAsset.metadata.cameraMake} {selectedAsset.metadata.cameraModel || ''}</span></>}
                        {selectedAsset.metadata.takenAtUtc && <><span className="text-on-surface-variant">Capture Date</span><span className="col-span-2 text-on-surface font-mono">{new Date(selectedAsset.metadata.takenAtUtc).toLocaleString()}</span></>}
                        {selectedAsset.metadata.resolution && <><span className="text-on-surface-variant">Resolution</span><span className="col-span-2 text-on-surface font-mono">{selectedAsset.metadata.resolution}</span></>}
                      </div>
                    </div>
                  )}
                  {aiTags && (
                    <div className="pt-6 border-t border-surface-container-highest">
                      <h3 className="font-label text-sm text-on-surface-variant mb-4">AI-Detected Tags</h3>
                      <div className="flex flex-wrap gap-2">
                        {aiTags.subject && <TagChip label={Array.isArray(aiTags.subject) ? aiTags.subject.join(', ') : aiTags.subject} />}
                        {aiTags.environment && <TagChip label={aiTags.environment} />}
                        {aiTags.mood && <TagChip label={aiTags.mood} />}
                        {aiTags.dominantColor && <TagChip label={aiTags.dominantColor} />}
                        {selectedAsset.status === 'PROCESSING' && (
                          <span className="bg-surface px-2.5 py-1 rounded text-xs font-label text-on-surface border border-outline-variant/30 flex items-center gap-2">
                            <span className="w-1.5 h-1.5 rounded-full bg-secondary-container animate-pulse" /> analyzing...
                          </span>
                        )}
                      </div>
                    </div>
                  )}
                </div>
              ) : (
                <p className="text-on-surface-variant text-sm italic">Select an asset to inspect.</p>
              )}
            </div>

            {/* Live SSE Logs */}
            <div className="flex-1 flex flex-col p-6 bg-surface-container-lowest m-6 rounded-xl border border-outline-variant/20 shadow-inner">
              <div className="flex items-center justify-between mb-4 px-2">
                <h3 className="font-label text-xs text-outline tracking-widest uppercase">Live System Log</h3>
                <Terminal className="text-outline w-4 h-4 opacity-50" />
              </div>
              <div className="flex-1 overflow-y-auto font-mono text-[11px] leading-relaxed text-on-surface-variant px-2 custom-scrollbar space-y-1">
                {sseLogs.length === 0 ? (
                  <div className="text-on-surface-variant/40 italic">Waiting for events...</div>
                ) : (
                  sseLogs.slice(-30).map((log, i) => (
                    <div key={i} className={`${log.type === 'error' ? 'text-error' : log.type === 'warning' ? 'text-secondary-container' : log.type === 'success' ? 'text-primary' : 'text-on-surface-variant opacity-60'}`}>
                      [{log.time}] {log.text}
                    </div>
                  ))
                )}
                <div className="text-primary mt-2 flex items-center gap-2 font-bold"><span className="animate-pulse">_</span></div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
