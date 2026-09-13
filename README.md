<div align="center">

# 🗂️ MediaFlow Archiver

**AI-Powered Desktop Media Archiver & Intelligent Asset Manager**

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0-6DB33F?logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![Tauri](https://img.shields.io/badge/Tauri-2.0-FFC131?logo=tauri&logoColor=white)](https://tauri.app)
[![React](https://img.shields.io/badge/React-19-61DAFB?logo=react&logoColor=white)](https://react.dev)
[![Java](https://img.shields.io/badge/Java-21-ED8B00?logo=openjdk&logoColor=white)](https://openjdk.org)

*Ingest media from any source. Deduplicate. Extract metadata. Tag with AI. Organize automatically.*

</div>

---

## ✨ What is MediaFlow Archiver?

MediaFlow Archiver is a **cross-platform desktop application** that transforms chaotic photo and video libraries into a searchable, AI-tagged, deduplicated archive. It handles the entire pipeline — from raw SD card imports to intelligent file organization — without cloud dependency.

### Key Features

| Feature | Description |
|---------|-------------|
| 🔐 **SHA-256 Deduplication** | Every file is cryptographically hashed. Plug in the same SD card twice — zero duplicates. |
| 🧠 **Gemini AI Vision Tagging** | Automatically describes, tags, and categorizes every photo/video using Google Gemini. |
| 📸 **EXIF Metadata Extraction** | Camera model, GPS coordinates, capture date — all extracted and searchable natively in Java. |
| 🖼️ **Proxy Thumbnail Generation** | Instant-loading optimized JPG proxies for every asset, including HEIC and RAW formats. |
| 📁 **Smart File Renaming** | AI-generated descriptive filenames replace `IMG_20240301_143022.jpg` with `sunset_beach_hawaii_a1b2c3d4.jpg`. |
| 🔄 **Multi-Model Rotation** | Automatically cycles through 6 Gemini models when free-tier quotas are hit. Zero downtime. |
| 🛑 **Stop/Resume Ingestion** | Cooperative stop mechanism — halt processing after the current asset, resume anytime. |
| 📡 **Real-Time SSE Updates** | Live Server-Sent Events stream processing status to the UI with zero polling. |
| 🔍 **Full-Text + AI Search** | Search across filenames, AI tags, descriptions, camera models, and more. |

---

## 🏗️ Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                     Tauri Desktop Shell (Rust)                  │
│  ┌───────────────────────────┐  ┌────────────────────────────┐  │
│  │    React 19 Frontend      │  │  Spring Boot 4 Backend     │  │
│  │    (Vite + TailwindCSS)   │  │  (Sidecar JAR Process)     │  │
│  │                           │  │                            │  │
│  │  • Dashboard Gallery      │◄►│  • REST API (port 8080)    │  │
│  │  • Processing Queue       │  │  • SSE Event Stream        │  │
│  │  • Asset Inspector        │  │  • Gemini AI Orchestrator  │  │
│  │  • Settings Panel         │  │  • EXIF Metadata Engine    │  │
│  │  • Live System Logs       │  │  • File Crawler & Filer    │  │
│  └───────────────────────────┘  │  • Thumbnail Proxy Gen     │  │
│                                 │  • PostgreSQL + pgvector    │  │
│                                 └────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
```

### Tech Stack

- **Desktop Shell**: [Tauri 2.0](https://tauri.app) (Rust) — lightweight native wrapper
- **Frontend**: [React 19](https://react.dev) + [Vite](https://vite.dev) + [Tailwind CSS 4](https://tailwindcss.com) + [Framer Motion](https://motion.dev)
- **Backend**: [Spring Boot 4](https://spring.io/projects/spring-boot) (Java 21) — runs as a managed sidecar process
- **Database**: [PostgreSQL 16](https://www.postgresql.org) + [pgvector](https://github.com/pgvector/pgvector) for semantic search
- **AI**: [Google Gemini Vision API](https://ai.google.dev) — multi-model rotation across free-tier quotas

---

## 🚀 Getting Started

### Prerequisites

- **Java 21** ([Adoptium](https://adoptium.net/) or [OpenJDK](https://openjdk.org/))
- **Node.js 20+** ([nodejs.org](https://nodejs.org/))
- **PostgreSQL 16** with [pgvector extension](https://github.com/pgvector/pgvector)
- **Rust toolchain** ([rustup.rs](https://rustup.rs/)) — for building the Tauri shell
- **Google Gemini API Key** ([AI Studio](https://aistudio.google.com/apikey)) — free tier works

### 1. Clone the Repository

```bash
git clone https://github.com/Ayush-0117/MediaFlow-Archiver.git
cd MediaFlow-Archiver
```

### 2. Start PostgreSQL

Use the included Docker Compose file:

```bash
cd backend
docker compose up -d
```

Or configure an existing PostgreSQL instance — see [`application.properties`](backend/src/main/resources/application.properties).

### 3. Set Your Gemini API Key

```bash
# Add to your shell profile (~/.zshrc, ~/.bashrc, etc.)
export GEMINI_API_KEY="your_api_key_here"
```

Or configure it through the **Settings** page in the app UI after launching.

### 4. Build & Run

#### Option A: Development Mode (Hot Reload)

```bash
# Terminal 1 — Start the backend
cd backend
./mvnw spring-boot:run

# Terminal 2 — Start the frontend
cd frontend
npm install
npm run dev
```

Open `http://localhost:3000` in your browser.

#### Option B: Tauri Desktop App

```bash
# Build the backend JAR first
cd backend
./mvnw clean package -DskipTests

# Copy JAR to Tauri sidecar location
cp target/archiver-0.0.1-SNAPSHOT.jar ../frontend/src-tauri/binaries/MediaflowArchiver.jar

# Build & launch the desktop app
cd ../frontend
npm install
npx tauri dev
```

#### Option C: Production Build (RPM/DEB)

```bash
cd backend
./mvnw clean package -DskipTests
cp target/archiver-0.0.1-SNAPSHOT.jar ../frontend/src-tauri/binaries/MediaflowArchiver.jar

cd ../frontend
npm install
npx tauri build
```

Packages are output to:
- `frontend/src-tauri/target/release/bundle/rpm/` — Fedora/RHEL/openSUSE
- `frontend/src-tauri/target/release/bundle/deb/` — Debian/Ubuntu

---

## 📖 Usage

### Ingesting Media

1. Navigate to the **Processing Queue** tab
2. Click **Browse** (Tauri) or type a folder path (browser mode)
3. Click **Ingest** — the pipeline runs automatically:
   - Files are hashed (SHA-256) and deduplicated
   - EXIF metadata is extracted
   - Proxy thumbnails are generated
   - Assets are queued for AI tagging
4. Watch progress in the **Live System Log** panel

### AI Tagging

The Gemini AI worker automatically processes queued assets:
- Generates natural language descriptions
- Detects subjects, environments, moods, and dominant colors
- Identifies human presence
- Smart-renames files based on AI analysis
- Rotates through 6 Gemini models when quotas are hit

### Stop / Resume

Click the **Stop** button in the Processing Queue to gracefully halt ingestion after the current asset. Remaining assets stay queued and resume on the next ingest trigger or app restart.

---

## 🔧 Configuration

### Application Properties

Key settings in [`backend/src/main/resources/application.properties`](backend/src/main/resources/application.properties):

| Property | Default | Description |
|----------|---------|-------------|
| `spring.datasource.url` | `jdbc:postgresql://localhost:5432/mediaflow_db` | PostgreSQL connection URL |
| `spring.datasource.password` | `${DB_PASSWORD:mediaflow_password}` | Database password (use env var in production) |
| `gemini.api.key` | `${GEMINI_API_KEY:}` | Gemini API key (env var or UI settings) |
| `archiver.cors.allowed-origins` | `http://localhost:3000,tauri://localhost` | Allowed CORS origins |

### Gemini Model Rotation

The AI orchestrator cycles through these models automatically:

1. `gemini-2.5-flash`
2. `gemini-2.0-flash`
3. `gemini-2.0-flash-lite`
4. `gemini-2.5-flash-lite`
5. `gemini-3.1-flash-lite`
6. `gemini-3.5-flash`

When a model returns HTTP 429, it's marked as exhausted and the next model takes over.

---

## 📂 Project Structure

```
MediaFlow-Archiver/
├── backend/                          # Spring Boot 4 (Java 21)
│   ├── src/main/java/com/mediaflow/archiver/
│   │   ├── controller/               # REST endpoints
│   │   │   ├── SystemController.java  # All API routes
│   │   │   └── GlobalExceptionHandler.java
│   │   ├── entity/                    # JPA entities
│   │   │   ├── MediaAsset.java        # Core asset model
│   │   │   └── MetadataSpec.java      # EXIF data model
│   │   ├── repository/               # Spring Data JPA repos
│   │   ├── service/                   # Business logic
│   │   │   ├── GeminiOrchestratorService.java   # AI queue + model rotation
│   │   │   ├── IngestionPipelineOrchestrator.java # Master pipeline
│   │   │   ├── FileCrawlerService.java           # Directory scanner
│   │   │   ├── FilerService.java                 # Archive & DB writer
│   │   │   ├── ExifMetadataService.java          # EXIF extraction
│   │   │   ├── ThumbnailProxyService.java        # Proxy generation
│   │   │   ├── IngestionStagingService.java      # SHA-256 hashing
│   │   │   ├── SseBroadcastService.java          # SSE event stream
│   │   │   └── SettingsService.java              # User preferences
│   │   └── event/
│   │       └── AiInferenceWakeEvent.java
│   ├── docker-compose.yml            # PostgreSQL + pgvector
│   └── pom.xml
│
├── frontend/                         # React 19 + Vite + Tauri 2
│   ├── src/
│   │   ├── pages/
│   │   │   ├── Dashboard.jsx         # Media gallery grid
│   │   │   ├── IngestionQueue.jsx    # Processing queue + stop button
│   │   │   ├── Settings.jsx          # App configuration
│   │   │   └── LogsWindow.jsx        # System log viewer
│   │   ├── components/
│   │   │   └── Shared.jsx            # Reusable UI components
│   │   ├── hooks/
│   │   │   └── useBackend.js         # SSE, notifications, backend status
│   │   ├── api.js                    # REST API client
│   │   ├── App.jsx                   # Root component + routing
│   │   └── index.css                 # Design system (Material You)
│   ├── src-tauri/                    # Tauri desktop shell (Rust)
│   │   ├── binaries/                 # Sidecar launcher script
│   │   ├── src/
│   │   │   ├── lib.rs                # Tauri plugin setup
│   │   │   ├── sidecar.rs            # Backend process manager
│   │   │   └── tray.rs               # System tray integration
│   │   └── tauri.conf.json           # Window config, CSP, bundling
│   └── package.json
│
├── LICENSE                           # GPL v3
└── README.md
```

---

## 🌐 API Reference

All endpoints are prefixed with `/api` and served on `http://localhost:8080`.

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/media?page=0&size=50` | Paginated media gallery |
| `GET` | `/api/media/all` | All assets (unpaginated) |
| `GET` | `/api/media/search?q=sunset` | Full-text + AI tag search |
| `GET` | `/api/media/filter?status=COMPLETED&type=IMAGE` | Advanced faceted filtering |
| `GET` | `/api/proxy/{sha256Hash}` | Serve proxy thumbnail |
| `GET` | `/api/original/{id}` | Serve original file |
| `DELETE` | `/api/media/{id}` | Delete asset (file + DB + proxy) |
| `POST` | `/api/ingest` | Trigger folder ingestion |
| `POST` | `/api/stop-ingestion` | Stop AI processing gracefully |
| `POST` | `/api/retry-failed` | Re-queue failed assets |
| `GET` | `/api/ai-status` | AI worker stats & quota info |
| `GET` | `/api/settings` | Get user settings |
| `PUT` | `/api/settings` | Update user settings |
| `POST` | `/api/settings/test-gemini` | Test Gemini API connectivity |
| `GET` | `/api/events` | SSE event stream (live updates) |

---

## 🤝 Contributing

Contributions are welcome! This project is licensed under GPL v3, which means:

- ✅ You can use, modify, and distribute this software freely
- ✅ You can use it for commercial purposes
- ⚠️ Any modifications or derivative works **must also be open-sourced** under GPL v3
- ⚠️ You must include the original copyright and license notice

### Development Setup

1. Fork the repository
2. Create a feature branch (`git checkout -b feat/amazing-feature`)
3. Commit your changes (`git commit -m 'feat: Add amazing feature'`)
4. Push to the branch (`git push origin feat/amazing-feature`)
5. Open a Pull Request

---

## 📜 License

This project is licensed under the **GNU General Public License v3.0** — see the [LICENSE](LICENSE) file for details.

---

<div align="center">

**Built with ❤️ by [Ayush](https://github.com/Ayush-0117)**

</div>
