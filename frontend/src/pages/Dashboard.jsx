import { useState, useEffect, useRef, useCallback } from 'react';
import { motion } from 'motion/react';
import {
  Search, Filter, Calendar, CheckCircle2, RefreshCw, AlertCircle,
  PlayCircle, MoreVertical, Info, BrainCircuit, Image as ImageIcon, X, Loader2
} from 'lucide-react';
import { UtilityNav, TagChip, SpecItem } from '../components/Shared';
import { fetchMedia, proxyUrl, deleteAsset, searchMedia } from '../api';

export default function Dashboard({ onNotify, notifications, markRead, activeTab, onTabChange }) {
  const [assets, setAssets] = useState([]);
  const [selectedAsset, setSelectedAsset] = useState(null);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [loading, setLoading] = useState(true);
  const [query, setQuery] = useState('');
  const sentinelRef = useRef(null);

  // Load media from backend
  const loadMedia = useCallback(async (pageNum, append = false) => {
    try {
      setLoading(true);
      const data = query.trim()
        ? await searchMedia(query, pageNum, 40)
        : await fetchMedia(pageNum, 40);
      
      const newAssets = data.content || [];
      setAssets(prev => append ? [...prev, ...newAssets] : newAssets);
      setTotalPages(data.totalPages || 0);
      if (!append && newAssets.length > 0) setSelectedAsset(newAssets[0]);
      if (!append && newAssets.length === 0) setSelectedAsset(null);
    } catch (err) {
      onNotify('Error', 'Failed to load media: ' + err.message, 'error');
    } finally {
      setLoading(false);
    }
  }, [query, onNotify]);

  useEffect(() => { setPage(0); loadMedia(0); }, [query]);

  // Infinite scroll via IntersectionObserver
  useEffect(() => {
    if (!sentinelRef.current) return;
    const obs = new IntersectionObserver(entries => {
      if (entries[0].isIntersecting && !loading && page + 1 < totalPages) {
        const next = page + 1;
        setPage(next);
        loadMedia(next, true);
      }
    }, { threshold: 0.1 });
    obs.observe(sentinelRef.current);
    return () => obs.disconnect();
  }, [loading, page, totalPages, loadMedia]);

  const handleDelete = async (id) => {
    try {
      await deleteAsset(id);
      setAssets(prev => prev.filter(a => a.id !== id));
      if (selectedAsset?.id === id) setSelectedAsset(null);
      onNotify('Archive', 'Asset permanently deleted.', 'success');
    } catch (err) {
      onNotify('Error', 'Delete failed: ' + err.message, 'error');
    }
  };

  const getStatusBadge = (status) => {
    if (status === 'COMPLETED') return <span className="bg-primary-container text-on-primary text-[10px] px-1.5 py-0.5 rounded font-label font-bold uppercase tracking-wide">AI</span>;
    if (status === 'PROCESSING') return <span className="bg-secondary-container text-on-secondary px-1.5 py-0.5 rounded font-label font-bold uppercase tracking-wide text-[10px]">Proc</span>;
    if (status === 'FAILED') return <span className="bg-error-container text-white px-1.5 py-0.5 rounded font-label font-bold uppercase tracking-wide text-[10px]">Err</span>;
    return <span className="bg-surface-container/80 text-on-surface text-[10px] px-1.5 py-0.5 rounded font-label">Pending</span>;
  };

  const getStatusIcon = (status) => {
    if (status === 'COMPLETED') return <CheckCircle2 className="w-4 h-4 text-primary" />;
    if (status === 'PROCESSING') return <RefreshCw className="w-4 h-4 text-secondary-container animate-spin" />;
    if (status === 'FAILED') return <AlertCircle className="w-4 h-4 text-error" />;
    return <Loader2 className="w-4 h-4 text-on-surface-variant animate-spin" />;
  };

  const formatSize = (bytes) => {
    if (!bytes) return '—';
    if (bytes > 1e9) return (bytes / 1e9).toFixed(1) + ' GB';
    if (bytes > 1e6) return (bytes / 1e6).toFixed(1) + ' MB';
    return (bytes / 1e3).toFixed(0) + ' KB';
  };

  const parseAiTags = (json) => {
    try { return JSON.parse(json); } catch { return null; }
  };

  const aiTags = selectedAsset ? parseAiTags(selectedAsset.aiTagsJson) : null;

  return (
    <div className="flex-1 flex flex-col h-full bg-surface-dim overflow-hidden">
      {/* Top Header */}
      <header className="px-8 h-14 border-b border-surface-container flex justify-between items-center shrink-0 gap-8 bg-surface-dim/80 backdrop-blur-md z-30">
        <div className="flex-1 relative flex items-center">
          <Search className="absolute left-3 w-4 h-4 text-on-surface-variant" />
          <input
            className="w-full bg-surface-container-lowest border border-outline-variant/20 rounded-md py-1.5 pl-10 pr-4 text-sm focus:outline-none focus:border-primary/40 focus:ring-1 focus:ring-primary/40 transition-colors"
            placeholder="Search archive..."
            value={query}
            onChange={e => setQuery(e.target.value)}
          />
        </div>
        <UtilityNav notifications={notifications} markRead={markRead} activeTab={activeTab} onTabChange={onTabChange} />
      </header>

      {/* Tool Bar */}
      <div className="bg-surface-container-low px-8 py-3 flex flex-wrap items-center justify-between gap-4 border-b border-surface-dim">
        <div className="flex items-center gap-3">
          <div className="flex bg-surface-container-highest rounded-md p-0.5">
            <button className="px-3 py-1 bg-surface-dim rounded text-sm font-label text-primary shadow-sm">Grid</button>
            <button className="px-3 py-1 text-sm font-label text-on-surface-variant hover:text-on-surface transition-colors">List</button>
          </div>
          <div className="h-6 w-px bg-outline-variant/30" />
          <span className="text-xs font-label text-on-surface-variant">{assets.length} assets loaded</span>
        </div>
        <div className="flex items-center gap-2">
          <span className="text-sm font-label text-on-surface-variant mr-2">Bulk Actions:</span>
          <button className="px-3 py-1.5 rounded bg-surface-container border border-outline-variant/20 text-sm font-label text-primary hover:bg-surface-container-high transition-colors">Download</button>
          <button onClick={() => selectedAsset && handleDelete(selectedAsset.id)} className="px-3 py-1.5 rounded bg-surface-container border border-outline-variant/20 text-sm font-label text-error hover:bg-surface-container-high transition-colors">Delete</button>
        </div>
      </div>

      <div className="flex-1 flex overflow-hidden">
        {/* Main Grid Area */}
        <div className="flex-1 p-8 overflow-y-auto bg-surface custom-scrollbar">
          {loading && assets.length === 0 ? (
            <div className="flex items-center justify-center h-full">
              <Loader2 className="w-8 h-8 text-primary animate-spin" />
            </div>
          ) : assets.length === 0 ? (
            <div className="flex flex-col items-center justify-center h-full text-on-surface-variant">
              <ImageIcon className="w-16 h-16 opacity-20 mb-4" />
              <p className="text-lg font-headline font-bold mb-2">No media found</p>
              <p className="text-sm">Ingest a folder to start building your archive.</p>
            </div>
          ) : (
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-6">
              {assets.map(asset => (
                <div
                  key={asset.id}
                  className={`group relative bg-surface-container-low rounded-xl overflow-hidden border transition-all cursor-pointer ${selectedAsset?.id === asset.id ? 'border-primary ring-1 ring-primary/30' : 'border-transparent hover:border-outline-variant/30 hover:bg-surface-container-high'}`}
                  onClick={() => setSelectedAsset(asset)}
                >
                  <div className="absolute top-3 right-3 z-10 flex gap-1">
                    {getStatusBadge(asset.status)}
                    <span className="bg-surface-container/80 backdrop-blur-sm text-on-surface text-[10px] px-1.5 py-0.5 rounded font-label border border-outline-variant/30">
                      {asset.fileType || 'FILE'}
                    </span>
                  </div>
                  <div className="aspect-video bg-surface-container-lowest relative overflow-hidden">
                    {asset.sha256Hash ? (
                      <img src={proxyUrl(asset.sha256Hash)} alt={asset.originalFilename} className="w-full h-full object-cover transition-transform duration-500 group-hover:scale-105" loading="lazy" />
                    ) : (
                      <div className="w-full h-full flex items-center justify-center text-on-surface-variant"><ImageIcon className="w-12 h-12 opacity-20" /></div>
                    )}
                    <div className="absolute inset-0 bg-gradient-to-t from-black/60 via-transparent to-transparent opacity-0 group-hover:opacity-100 transition-opacity" />
                  </div>
                  <div className="p-4">
                    <h3 className={`font-headline text-sm font-bold truncate transition-colors ${asset.status === 'FAILED' ? 'text-error' : 'text-on-surface'}`}>
                      {asset.originalFilename}
                    </h3>
                    <div className="flex justify-between items-center mt-2">
                      <span className="font-label text-xs text-on-surface-variant">{formatSize(asset.fileSize)}</span>
                      {getStatusIcon(asset.status)}
                    </div>
                  </div>
                </div>
              ))}
              {/* Infinite scroll sentinel */}
              <div ref={sentinelRef} className="h-4" />
            </div>
          )}
        </div>

        {/* Inspector Sidebar */}
        {selectedAsset && (
          <aside className="w-80 lg:w-96 bg-surface-container-low border-l border-surface-dim flex flex-col h-full overflow-y-auto custom-scrollbar z-20">
            <div className="p-6 bg-surface-container-lowest">
              <div className="aspect-video rounded-lg bg-black relative overflow-hidden group">
                {selectedAsset.sha256Hash ? (
                  <img src={proxyUrl(selectedAsset.sha256Hash)} alt="Preview" className="w-full h-full object-cover" />
                ) : (
                  <div className="w-full h-full flex items-center justify-center bg-surface-container"><X className="w-12 h-12 text-on-surface-variant opacity-20" /></div>
                )}
              </div>
              <div className="mt-4 flex justify-between items-start">
                <div>
                  <h2 className="font-headline font-black text-lg text-on-surface">{selectedAsset.originalFilename}</h2>
                  <p className="font-label text-xs text-on-surface-variant mt-1.5">
                    {selectedAsset.createdAt ? new Date(selectedAsset.createdAt).toLocaleDateString() : '—'}
                  </p>
                </div>
                <button className="text-on-surface-variant hover:text-primary transition-colors"><MoreVertical className="w-5 h-5" /></button>
              </div>
            </div>

            <div className="p-6 border-b border-surface-dim">
              <h3 className="font-headline text-sm font-bold text-on-surface mb-4 flex items-center gap-2"><Info className="w-4 h-4 text-primary" /> Technical Specs</h3>
              <div className="grid grid-cols-2 gap-4 font-label text-xs">
                <SpecItem label="Format" value={selectedAsset.fileType || '—'} />
                <SpecItem label="Size" value={formatSize(selectedAsset.fileSize)} />
                <SpecItem label="Resolution" value={selectedAsset.metadata?.resolution || '—'} />
                <SpecItem label="Camera" value={selectedAsset.metadata?.cameraModel || '—'} />
                <SpecItem label="Hash" value={selectedAsset.sha256Hash?.substring(0, 12) + '...' || '—'} />
                <SpecItem label="Status" value={selectedAsset.status} />
              </div>
            </div>

            {/* AI Intelligence Section */}
            <div className="p-6 flex-1">
              <h3 className="font-headline text-sm font-bold text-on-surface mb-4 flex items-center gap-2">
                <BrainCircuit className="w-4 h-4 text-primary" /> AI Intelligence
              </h3>
              {aiTags ? (
                <div className="space-y-4">
                  {aiTags.description && (
                    <p className="text-xs text-on-surface-variant leading-relaxed italic">"{aiTags.description}"</p>
                  )}
                  <div>
                    <span className="font-label text-xs text-on-surface-variant block mb-3">Detected Labels</span>
                    <div className="flex flex-wrap gap-2">
                      {aiTags.subject && <TagChip label={`Subject: ${Array.isArray(aiTags.subject) ? aiTags.subject.join(', ') : aiTags.subject}`} />}
                      {aiTags.environment && <TagChip label={`Env: ${aiTags.environment}`} />}
                      {aiTags.mood && <TagChip label={`Mood: ${aiTags.mood}`} />}
                      {aiTags.dominantColor && <TagChip label={`Color: ${aiTags.dominantColor}`} />}
                      {aiTags.hasPerson !== undefined && <TagChip label={`Person: ${aiTags.hasPerson ? 'Yes' : 'No'}`} />}
                    </div>
                  </div>
                </div>
              ) : selectedAsset.status === 'PROCESSING' || selectedAsset.status === 'PENDING_AI' ? (
                <div className="flex items-center gap-2 text-xs text-on-surface-variant">
                  <Loader2 className="w-4 h-4 animate-spin text-primary" />
                  <span>AI analysis in progress...</span>
                </div>
              ) : selectedAsset.status === 'FAILED' ? (
                <p className="text-xs text-error">AI processing failed after {selectedAsset.retryCount || 0} attempts.</p>
              ) : (
                <p className="text-xs text-on-surface-variant italic">No AI data available.</p>
              )}
            </div>

            {/* Original Filename (Smart Rename Info) */}
            <div className="p-6 border-t border-surface-dim">
              <span className="font-label text-[10px] text-on-surface-variant uppercase tracking-widest block mb-1">Original Filename</span>
              <code className="text-xs text-primary font-mono bg-surface-container-lowest p-2 rounded block break-all border border-outline-variant/20">
                {selectedAsset.originalFilename}
              </code>
              <span className="font-label text-[10px] text-on-surface-variant uppercase tracking-widest block mb-1 mt-3">Smart Path</span>
              <code className="text-xs text-on-surface font-mono bg-surface-container-lowest p-2 rounded block break-all border border-outline-variant/20">
                {selectedAsset.relativePath || '—'}
              </code>
            </div>
          </aside>
        )}
      </div>
    </div>
  );
}
