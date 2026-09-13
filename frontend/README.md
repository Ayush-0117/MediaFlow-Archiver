# MediaFlow Archiver — Frontend

React 19 + Vite + Tailwind CSS 4 frontend for MediaFlow Archiver, wrapped in a Tauri 2.0 desktop shell.

## Development

```bash
npm install
npm run dev         # Vite dev server on http://localhost:3000
```

## Tauri Desktop

```bash
npx tauri dev       # Launch desktop app with hot reload
npx tauri build     # Production build (RPM, DEB, AppImage)
```

## Environment

Copy `.env.example` to `.env.local` and configure:

```env
GEMINI_API_KEY="your_key_here"
```

Or configure the API key through the **Settings** page in the app UI.

## Stack

- **React 19** — UI framework
- **Vite 6** — Build tool & dev server
- **Tailwind CSS 4** — Utility-first styling (Material You design tokens)
- **Framer Motion** — Animations & transitions
- **Lucide React** — Icon library
- **Tauri 2** — Desktop shell (Rust)
