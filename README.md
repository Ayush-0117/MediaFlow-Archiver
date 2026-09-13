# MediaFlow Archiver

**AI-powered desktop media archiver and intelligent asset manager.**

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0-6DB33F?logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![Tauri](https://img.shields.io/badge/Tauri-2.0-FFC131?logo=tauri&logoColor=white)](https://tauri.app)
[![React](https://img.shields.io/badge/React-19-61DAFB?logo=react&logoColor=white)](https://react.dev)
[![Java](https://img.shields.io/badge/Java-21-ED8B00?logo=openjdk&logoColor=white)](https://openjdk.org)

MediaFlow Archiver is a cross-platform desktop application that transforms chaotic photo and video libraries into a searchable, AI-tagged, deduplicated archive. It handles the entire pipeline — from raw SD card imports to intelligent file organization — without cloud dependency.

## Table of Contents

- [Features](#features)
- [Architecture](#architecture)
- [Getting Started](#getting-started)
  - [Prerequisites](#prerequisites)
  - [Database Setup](#database-setup)
  - [Configuration](#configuration)
  - [Development Mode](#development-mode)
  - [Desktop App (Tauri)](#desktop-app-tauri)
  - [Production Build](#production-build)
- [Usage](#usage)
- [API Reference](#api-reference)
- [Project Structure](#project-structure)
- [Contributing](#contributing)
- [License](#license)

## Features

- **SHA-256 Deduplication** — Every file is cryptographically hashed. Plug in the same SD card twice and nothing is duplicated.
- **Gemini AI Vision Tagging** — Automatically describes, tags, and categorizes every photo and video using Google Gemini.
- **EXIF Metadata Extraction** — Camera model, GPS coordinates, and capture date are all extracted and searchable natively in Java.
- **Proxy Thumbnail Generation** — Instant-loading optimized JPG proxies for every asset, including HEIC and RAW formats.
- **Smart File Renaming** — AI-generated descriptive filenames replace `IMG_20240301_143022.jpg` with `sunset_beach_hawaii_a1b2c3d4.jpg`.
- **Multi-Model Rotation** — Automatically cycles through 6 Gemini models when free-tier quotas are hit. Zero downtime.
- **Stop and Resume** — Cooperative stop mechanism halts processing after the current asset. Remaining assets stay queued and resume on next trigger.
- **Real-Time Updates** — Server-Sent Events stream processing status to the UI with zero polling.
- **Full-Text and AI Search** — Search across filenames, AI tags, descriptions, camera models, and more.

## Architecture

```
+---------------------------------------------------------------+
|                   Tauri Desktop Shell (Rust)                   |
|  +---------------------------+  +---------------------------+  |
|  |    React 19 Frontend      |  |  Spring Boot 4 Backend    |  |
|  |    (Vite + TailwindCSS)   |  |  (Sidecar JAR Process)    |  |
|  |                           |  |                           |  |
|  |  - Dashboard Gallery      |<>|  - REST API (port 8080)   |  |
|  |  - Processing Queue       |  |  - SSE Event Stream       |  |
|  |  - Asset Inspector        |  |  - Gemini AI Orchestrator |  |
|  |  - Settings Panel         |  |  - EXIF Metadata Engine   |  |
|  |  - Live System Logs       |  |  - File Crawler & Filer   |  |
|  +---------------------------+  |  - Thumbnail Proxy Gen    |  |
|                                 |  - PostgreSQL + pgvector   |  |
|                                 +---------------------------+  |
+---------------------------------------------------------------+
```

| Layer | Technology |
|-------|-----------|
| Desktop Shell | [Tauri 2.0](https://tauri.app) (Rust) |
| Frontend | [React 19](https://react.dev), [Vite 6](https://vite.dev), [Tailwind CSS 4](https://tailwindcss.com), [Framer Motion](https://motion.dev) |
| Backend | [Spring Boot 4](https://spring.io/projects/spring-boot) (Java 21) |
| Database | [PostgreSQL 16](https://www.postgresql.org) with [pgvector](https://github.com/pgvector/pgvector) |
| AI | [Google Gemini Vision API](https://ai.google.dev) |

## Getting Started

### Prerequisites

- [Java 21](https://adoptium.net/) or later
- [Node.js 20](https://nodejs.org/) or later
- [PostgreSQL 16](https://www.postgresql.org/) with the [pgvector](https://github.com/pgvector/pgvector) extension
- [Rust toolchain](https://rustup.rs/) (for building the Tauri shell)
- [Google Gemini API key](https://aistudio.google.com/apikey) (free tier is sufficient)

### Database Setup

Use the included Docker Compose file to start PostgreSQL with pgvector:

```bash
cd backend
docker compose up -d
```

Alternatively, configure an existing PostgreSQL instance by editing
[`application.properties`](backend/src/main/resources/application.properties).

### Configuration

Set your Gemini API key as an environment variable:

```bash
export GEMINI_API_KEY="your_api_key_here"
```

Or configure it through the Settings page in the application UI after launching.

Key settings in [`application.properties`](backend/src/main/resources/application.properties):

| Property | Default | Description |
|----------|---------|-------------|
| `spring.datasource.url` | `jdbc:postgresql://localhost:5432/mediaflow_db` | PostgreSQL connection URL |
| `spring.datasource.password` | `${DB_PASSWORD:mediaflow_password}` | Database password |
| `gemini.api.key` | `${GEMINI_API_KEY:}` | Gemini API key |
| `archiver.cors.allowed-origins` | `http://localhost:3000,tauri://localhost` | Allowed CORS origins |

### Development Mode

Run the backend and frontend separately with hot reload:

```bash
# Terminal 1: Start the backend
cd backend
./mvnw spring-boot:run

# Terminal 2: Start the frontend
cd frontend
npm install
npm run dev
```

The frontend is available at `http://localhost:3000`.

### Desktop App (Tauri)

Build the backend JAR, then launch the Tauri desktop app:

```bash
cd backend
./mvnw clean package -DskipTests
cp target/archiver-0.0.1-SNAPSHOT.jar ../frontend/src-tauri/binaries/MediaflowArchiver.jar

cd ../frontend
npm install
npx tauri dev
```

### Production Build

Generate installable packages for Linux:

```bash
cd backend
./mvnw clean package -DskipTests
cp target/archiver-0.0.1-SNAPSHOT.jar ../frontend/src-tauri/binaries/MediaflowArchiver.jar

cd ../frontend
npm install
npx tauri build
```

Output packages:

| Format | Location |
|--------|----------|
| RPM (Fedora, RHEL, openSUSE) | `frontend/src-tauri/target/release/bundle/rpm/` |
| DEB (Debian, Ubuntu) | `frontend/src-tauri/target/release/bundle/deb/` |

## Usage

### Ingesting Media

1. Open the **Processing Queue** tab.
2. Click **Browse** to select a folder (or type a path manually in browser mode).
3. Click **Ingest**. The pipeline processes each file through these stages:
   - SHA-256 hashing and deduplication
   - EXIF metadata extraction
   - Proxy thumbnail generation
   - AI tagging queue
4. Monitor progress in the **Live System Log** panel.

### AI Tagging

The Gemini AI worker processes queued assets automatically. For each asset it:

- Generates a natural language description
- Detects subjects, environments, moods, and dominant colors
- Identifies human presence
- Renames the file based on AI analysis

When a model's free-tier quota is exhausted (HTTP 429), the orchestrator rotates to the next available model. The rotation pool includes:

| Priority | Model |
|----------|-------|
| 1 | `gemini-2.5-flash` |
| 2 | `gemini-2.0-flash` |
| 3 | `gemini-2.0-flash-lite` |
| 4 | `gemini-2.5-flash-lite` |
| 5 | `gemini-3.1-flash-lite` |
| 6 | `gemini-3.5-flash` |

### Stopping Ingestion

Click the **Stop** button in the Processing Queue to gracefully halt the AI worker after it finishes the current asset. Remaining assets stay in the queue with `PENDING_AI` status and will resume on the next ingest trigger or application restart.

## API Reference

All endpoints are served at `http://localhost:8080/api`.

### Media

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/media?page=0&size=50` | Paginated media gallery |
| `GET` | `/api/media/all` | All assets (unpaginated) |
| `GET` | `/api/media/search?q=sunset` | Full-text and AI tag search |
| `GET` | `/api/media/filter?status=COMPLETED&type=IMAGE` | Advanced faceted filtering |
| `GET` | `/api/proxy/{sha256Hash}` | Serve proxy thumbnail |
| `GET` | `/api/original/{id}` | Serve original file |
| `DELETE` | `/api/media/{id}` | Delete asset, file, and proxy |

### Ingestion

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/ingest` | Trigger folder ingestion |
| `POST` | `/api/stop-ingestion` | Stop AI processing gracefully |
| `POST` | `/api/retry-failed` | Re-queue failed assets |
| `GET` | `/api/ai-status` | AI worker stats and quota info |

### Settings

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/settings` | Get user settings |
| `PUT` | `/api/settings` | Update user settings |
| `POST` | `/api/settings/test-gemini` | Test Gemini API connectivity |

### Events

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/events` | SSE event stream (live updates) |

## Project Structure

```
MediaFlow-Archiver/
├── backend/                            # Spring Boot 4 (Java 21)
│   ├── src/main/java/.../
│   │   ├── controller/
│   │   │   ├── SystemController.java    # REST API endpoints
│   │   │   └── GlobalExceptionHandler.java
│   │   ├── entity/
│   │   │   ├── MediaAsset.java          # Core asset model
│   │   │   └── MetadataSpec.java        # EXIF data model
│   │   ├── repository/                  # Spring Data JPA
│   │   └── service/
│   │       ├── GeminiOrchestratorService.java      # AI queue + model rotation
│   │       ├── IngestionPipelineOrchestrator.java   # Master pipeline conductor
│   │       ├── FileCrawlerService.java              # Directory scanner
│   │       ├── FilerService.java                    # Archive writer
│   │       ├── ExifMetadataService.java             # EXIF extraction
│   │       ├── ThumbnailProxyService.java           # Proxy generation
│   │       ├── IngestionStagingService.java         # SHA-256 hashing
│   │       ├── SseBroadcastService.java             # SSE event stream
│   │       └── SettingsService.java                 # User preferences
│   ├── docker-compose.yml               # PostgreSQL + pgvector
│   └── pom.xml
│
├── frontend/                            # React 19 + Vite + Tauri 2
│   ├── src/
│   │   ├── pages/
│   │   │   ├── Dashboard.jsx            # Media gallery grid
│   │   │   ├── IngestionQueue.jsx       # Processing queue with stop control
│   │   │   ├── Settings.jsx             # Application configuration
│   │   │   └── LogsWindow.jsx           # System log viewer
│   │   ├── components/Shared.jsx        # Reusable UI components
│   │   ├── hooks/useBackend.js          # SSE, notifications, status hooks
│   │   ├── api.js                       # REST API client
│   │   ├── App.jsx                      # Root component
│   │   └── index.css                    # Design system
│   ├── src-tauri/                       # Tauri desktop shell (Rust)
│   │   ├── binaries/                    # Sidecar launcher script
│   │   ├── src/
│   │   │   ├── lib.rs                   # Plugin setup
│   │   │   ├── sidecar.rs              # Backend process manager
│   │   │   └── tray.rs                 # System tray
│   │   └── tauri.conf.json
│   └── package.json
│
├── LICENSE                              # GPL-3.0
└── README.md
```

## Contributing

Contributions are welcome. This project is licensed under GPL v3, which requires that any modifications or derivative works are also distributed under the same license.

1. Fork the repository.
2. Create a feature branch: `git checkout -b feat/my-feature`
3. Commit your changes: `git commit -m 'feat: Add my feature'`
4. Push to the branch: `git push origin feat/my-feature`
5. Open a Pull Request.

Please ensure your code compiles cleanly (`./mvnw clean package -DskipTests` for backend, `npm run build` for frontend) before submitting.

## License

This project is licensed under the GNU General Public License v3.0. See [LICENSE](LICENSE) for the full text.

Copyright (C) 2026 [Ayush](https://github.com/Ayush-0117)
