# PROJECT SYNOPSIS

**Project Title:** Unified Digital Asset Manager and AI-Driven Media Sorter (Architecture V2.0)
**Domain:** Artificial Intelligence, Distributed Systems, Image Processing, and File Management
**Core Technologies:** Java 21+ (Virtual Threads), Spring Boot 3.x (Web MVC), REST APIs (Tus Protocol), Generative AI (Vision Models), PostgreSQL 17+ (pgvector)

## 1. Introduction
### 1.1 Background
In the modern digital ecosystem, individuals generate vast quantities of media across a highly fragmented array of devices. A single user’s digital footprint might span modern Android smartphones, legacy early-2010s digital cameras, digitized scans of 35mm film, downloaded social media images, and digital documents. While capturing and downloading media is effortless, centralizing, organizing, and retrieving these files remains a significant manual burden.

### 1.2 Problem Statement
Users face the "fragmented shoebox" problem. Media is trapped in silos—sitting unorganized on SD cards, scattered in mobile phone galleries, or dumped into generic computer folders. Finding a specific file relies entirely on human memory. Furthermore, local desktop operating systems lack built-in, context-aware visual search capabilities to automatically categorize mixed media (e.g., distinguishing a landscape photo from a screenshot of a coding error or a scanned PDF document). Additionally, modern files like HEIC/Live Photos further complicate routing logic.

### 1.3 Objectives
* **Omni-Channel Ingestion:** To develop a multi-source background service that can ingest media autonomously from physical hardware (SD cards/USB drives) and network sources (Wi-Fi transfers from mobile devices).
* **Intelligent Classification & Fallback:** To utilize a hybrid Local AI (YOLO) and Cloud Generative AI (Gemini Flash) pipeline to classify media types while maximizing privacy.
* **Semantic Metadata Enrichment:** To extract standard EXIF data (timezones, coordinates) and append AI-generated tags and vector embeddings into a local database for natural language retrieval.
* **Centralized Archiving:** To automatically route and rename files into a single, unified directory structure, maintaining complex relationships like paired Compound Live Photos.

## 2. Literature Review & Existing Systems
* **Cloud Solutions (Google Photos / Apple iCloud):** Offer excellent AI sorting but trap users in a subscription ecosystem. They require uploading massive amounts of private data, consuming bandwidth, and raising data-sovereignty concerns.
* **Professional DAMs (Adobe Bridge / Lightroom):** Powerful local tools that read EXIF data efficiently but lack automatic AI-driven content tagging without extensive manual user input.
* **Standard OS File Managers:** Only sort by basic filesystem metadata (Date Modified, File Size, File Extension).

**Gap Analysis:** There is a distinct lack of open-source, locally hosted software that bridges the gap between multi-device hardware ingestion and advanced generative AI tagging, without forcing the user to upload their entire archive to a paid cloud provider.

## 3. Proposed System
The "Unified Digital Asset Manager" is a desktop-grade backend service built to run silently on a host machine (e.g., a Fedora Linux workstation). It acts as a centralized brain, continuously polling for new media. Upon detection, it triggers a multi-stage processing pipeline:
1. **Multi-Source Ingestion:** Listens for physical mounts using cross-platform OS hardware polling (via OSHI) OR receives large payloads via the chunked Tus Protocol from a smartphone.
2. **Extraction:** Reads embedded metadata using ExifTool, handling proprietary maker notes and timezone offsets.
3. **AI Inference:** Creates downscaled thumbnails (supporting HEIC via libvips/TwelveMonkeys). Uses local YOLO for basic tagging and forwards complex images to Google Gemini Flash API for deep semantic tagging.
4. **Filing & Routing:** Renames the file logically (using Relative Paths) and moves it to the archive, ensuring Live Photos (HEIC+MOV) stay grouped.

## 4. System Architecture
The system is engineered as a Modular Monolith using Spring Boot. It operates on **Java 21**, utilizing lightweight **Virtual Threads** rather than WebFlux to achieve massive concurrency without reactive API complexity.

### 4.1 The Ingestion Layer
* **Cross-Platform Hardware Monitor (OSHI):** Utilizes `oshi-core` to cleanly detect USB/SD hardware mounts. To ensure files are accessible, it actively drills down into the raw physical `HWDiskStore` and extracts the logical partition Mount Point (e.g., `/run/media/SD_CARD` or `F:\`), allowing the Java stream readers to instantly access the drive.
* **Network Gateway (Tus Protocol):** Built on Spring Web MVC using the Tus Protocol. It supports resumable, chunked uploads so massive 4K videos from smartphones never fail mid-transfer due to momentary Wi-Fi drops.

### 4.2 The Cryptographic Integrity & Backup Engine
* **Hybrid Deduplication (SHA-256 + pHash):** Validates file uniqueness using streaming SHA-256 hashes for exact binary duplicates, coupled with Perceptual Hashing (pHash) to catch compressed, visually-identical duplicates (e.g., WhatsApp vs Original).
* **Two-Phase Commit:** Source files remain un-deleted until copied to a staging TEMP zone and verified against checksums.

### 4.3 Multimodal Analysis & Extraction (The Intelligence Hub)
* **Metadata Extraction (ExifTool):** Uses an ExifTool wrapper over standard extractors to guarantee extraction of complex smartphone MakerNotes and GPS tracks embedded deep in vintage MP4 or proprietary HEIC files.
* **Thumbnail Proxying (HEIC Support):** Relies on **Byedeco (JavaCPP)** to bundle native C++ `libheif` libraries directly inside the compiled Java executable. This allows pure Java to natively decode iPhone `.HEIC` Live Photos into small local `.JPG` proxies instantly without requiring the user to manually install ImageMagick on their host OS.
* **Hybrid AI Vision Orchestrator & API Rate Limiting:** Basic images are tagged using a local YOLO proxy. Complex images proceed to Gemini. To strictly prevent Google `429 API Bans` (15 RPM capacity limit), AI inference is decoupled from the ingestion speed. All files load onto the database disk at maximum hardware speed into a `BlockingQueue`. A throttled worker thread processes the queue outbound to Gemini at a strict 14 RPM (sleeping 4.5s per image).
* **Dead Letter Queue (DLQ):** Failed parsing or model generation pushes the file to an `Error_Logs` table and quarantine folder rather than crashing the ingestion pipeline.

### 4.4 Persistence & Logical Routing Layer
* **PostgreSQL 17+ with `pgvector`:** Stores relational metadata, JSONB tags, and vector embeddings of images, allowing "Natural Language Searching" instead of just keyword matching.
* **Timezone Normalization (Dual-Storage):** The database forces storage of the exact moment in standard UTC, alongside the `original_timezone` offset gathered from EXIF, solving cross-border import synchronization issues.
* **Relative Archive Paths:** The Postgres database only stores relative paths (e.g., `2021/Nature/001.jpg`). An `ARCHIVE_ROOT` environment variable prepends the absolute path, ensuring the entire system can migrate to a NAS without breaking the database.

### 4.5 Presentation & Administrative Dashboard
* **Tauri Desktop Application:** An ultra-lightweight, native cross-platform desktop UI built natively on Rust and React (Javascript). It launches instantly and uses native OS webviews to provide a premium, fluid SPA experience capable of rendering massive masonry galleries and real-time statistics with minimal RAM overhead. Combined with GraalVM Native Images for the Java sidecar, it operates as a blazing-fast localized monolith.
* **Multi-Agent Librarian Pipeline (Future-Ready):** Lays the groundwork for integrating the Google Agent Development Kit (ADK) on the UI layer to allow natural language commands (e.g., "Find and delete all blurry documents").

## 5. Technology Stack & Database
* **Backend:** Java 21 (LTS), Spring Boot 3.x (Web MVC with Virtual Threads)
* **Storage:** PostgreSQL 17+ (with GIN index & `pgvector` extension)
* **Intelligence:** Google GenAI Java SDK (Gemini Flash API) + Local ONNX Runtime (YOLO)
* **Processing:** ExifTool (Metadata), TwelveMonkeys / FFmpeg (Media Resampling)
* **UI:** Tauri, React (Vite / Javascript), SCSS CSS Modules, Framer Motion

## 6. Implementation Methodology
The codebase adopts Agile-Scrum sprints.
* **Sprint 1 (Core Foundation):** Setup PostgreSQL relative-path schema, cross-platform OSHI hardware event listeners, and the SHA-256 staging pipeline.
* **Sprint 2 (Data Science Pipeline):** Integrate ExifTool, implement Strict UTC Timezone normalization, and compound Live-Photo detection logic.
* **Sprint 3 (Intelligence):** Integrate the Bounded Queue / DLQ system. Setup the local YOLO detector fallback and wire the Google GenAI SDK for Gemini Flash JSON parsing.
* **Sprint 4 (UI & Gateway):** Build the pure React desktop application wrapped in the Tauri Shell, and establish the Tus Protocol Wi-Fi upload gate.

## 7. Conclusion
The "Unified Digital Asset Manager" (V2) incorporates enterprise-grade protections—including pHash deduplication, Virtual Thread queue safety, pgvector embeddings, and cross-platform hardware monitors—ensuring it acts as a flawless, privacy-respecting bridge between hardware realities and cloud intelligence.
