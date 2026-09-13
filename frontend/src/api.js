/**
 * MediaFlow Archiver — API Service Layer
 * All REST communication with the Spring Boot backend.
 */

const IS_TAURI = '__TAURI__' in window || '__TAURI_INTERNALS__' in window;
const BASE = IS_TAURI ? 'http://localhost:8080/api' : '/api';

async function request(path, options = {}) {
  const res = await fetch(`${BASE}${path}`, {
    headers: { 'Content-Type': 'application/json', ...options.headers },
    ...options,
  });
  if (!res.ok) throw new Error(`API ${res.status}: ${res.statusText}`);
  return res.json();
}

/** Paginated gallery endpoint */
export const fetchMedia = (page = 0, size = 50) =>
  request(`/media?page=${page}&size=${size}`);

/** All media (unpaginated, for counts) */
export const fetchAllMedia = () => request('/media/all');

/** AI processing stats */
export const fetchAiStatus = () => request('/ai-status');

/** User settings */
export const fetchSettings = () => request('/settings');
export const updateSettings = (updates) =>
  request('/settings', { method: 'PUT', body: JSON.stringify(updates) });

/** Test Gemini API Connection */
export const testGeminiConnection = () => 
  request('/settings/test-gemini', { method: 'POST' });

/** Search */
export const searchMedia = (query, page = 0, size = 50) =>
  request(`/media/search?q=${encodeURIComponent(query)}&page=${page}&size=${size}`);

/** Advanced filter */
export const filterMedia = (params) => {
  const qs = new URLSearchParams();
  Object.entries(params).forEach(([k, v]) => {
    if (v !== undefined && v !== null && v !== '') qs.append(k, v);
  });
  return request(`/media/filter?${qs.toString()}`);
};

/** Manual folder ingest */
export const triggerIngest = (path) =>
  request('/ingest', { method: 'POST', body: JSON.stringify({ path }) });

/** Delete a media asset */
export const deleteAsset = (id) =>
  request(`/media/${id}`, { method: 'DELETE' });

/** Retry all failed AI tasks */
export const retryFailed = () =>
  request('/retry-failed', { method: 'POST' });

/** Stop the currently running ingestion/AI worker */
export const stopIngestion = () =>
  request('/stop-ingestion', { method: 'POST' });

/** Proxy thumbnail URL (for <img src>) */
export const proxyUrl = (sha256Hash) => `${BASE}/proxy/${sha256Hash}`;

/** Original file URL */
export const originalUrl = (id) => `${BASE}/original/${id}`;

/** SSE event stream URL */
export const SSE_URL = `${BASE}/events`;
