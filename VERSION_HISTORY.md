# Project Version History

This file tracks all modifications, updates, and releases for the PDF Viewer App.

## Versioning Policy

### 1. Semantic Versioning (SemVer)
- **PATCH / Small Changes (`0.0.1` bump)**: Used for small tweaks, bug fixes, UI adjustments, logging, and performance updates (e.g., `1.0.1` -> `1.0.2`).
- **MINOR / Feature Updates (`0.1.0` bump)**: Used for adding new backward-compatible features like a new document utility or additional cloud integrations (e.g., `1.0.2` -> `1.1.0`).
- **MAJOR / Structural Changes (`1.0.0` bump)**: Used for major architectural shifts, breaking layout overhauls, database redesigns, or bumping the minimum SDK version (e.g., `1.0.2` -> `2.0.0`).

### 2. Build Version Code Rule
- The build integer `versionCode` defined in Gradle must increment by exactly `1` with each update (e.g., `7` -> `8` -> `9`).

### 3. Database Migration Rule
- Any changes to database schema properties must increment the database version in `AppDatabase.kt`. Destructive migrations are permitted only for offline caches or initial testing; production updates must specify explicit database migrations to preserve user history and bookmarks.

### 4. Assets Synchronization
- During application startup, check the active dynamic version and perform any necessary file or cache migrations (like asset copying) to match the new bundle layout.

## [V 1.0.3] - Scrollbar & Menu Updates - 2026-07-02
### Added
- **Custom Page Scrollbars**: Added highly elegant vertical and horizontal scrollbar tracks with smooth thumbs dynamically positioned based on current list scroll fraction.
- **TopAppBar Actions Consolidation**: Replaced cluttered top action icons in `PdfViewerScreen` with a clean Material `MoreVert` dropdown overflow menu.
- **Scroll Direction Toggle**: Added quick scroll direction buttons in the reader's bottom control bar.
- **Official Version Bump**: Incremented version code to `8` and version name to `1.0.3` in accordance with the Small Changes rule.

## [V 1.0.2] - Dynamic Versioning Update - 2026-07-02
### Added
- **Dynamic Versioning Integration**: Replaced static settings screen version display with dynamic package information query.
- **Official Version Bump**: Incremented version code to `7` and version name to `1.0.2` in accordance with the Small Changes rule.

## [V 1.0.1] - Major Feature Update - 2026-07-01
### Added
- **Persistent Highlights & Markup Manager**: Integrated a database-driven highlighting system with a custom manager dialog to add and delete key phrases with colored labels.
- **Real-Time Spoken Word Highlighting**: Overrode TTS progress listeners to dynamically track pronunciation coordinates and paint spoken words on the Compose canvas in real-time.
- **PDF Direct Text/Word Editor**: Added direct in-place text replacement capabilities on PDF documents utilizing PDFBox-Android, with coordinates lookup and instant screen refreshes.
- **Dedicated Text-to-Speech (TTS) HUD**: Added a floating player panel with dedicated Play, Pause, Stop, and speed control chips (`0.75x`, `Normal`, `1.25x`, `1.5x`, `2.0x`).
- **Voice Gender Customization Profile**: Added voice gender chips to toggle between male and female voice outputs.
- **Calibrated Voice Profiles**: Implemented pitch profiles to ensure realistic male (`0.78f`) and female (`1.5f`) voices on all engines.
- **Offline Language Models Manager**: Integrated manual cloud download and deletion dialog in the translation interface.

## [V 0.0.1] - Small Change - 2026-07-01
- Adjusted Male voice pitch preference to 1.0f and Female voice pitch preference to 0.78f.
- Added automatic assets synchronization: assets (like `sample.pdf`) are automatically copied and updated in private storage whenever the app is updated.
- Disabled automatic recents registration for copied assets files to prevent them from automatically appearing in the Recents list or opening on startup.

## [V 0.0.1] - Minor Patch Update - 2026-07-01
### Fixed
- **Language Support Integration**: Added Telugu and Hindi supporting locales to translation catalog.
- **Gradle and Build System**: Configured Java 17 toolchain alignment and removed duplicate Activity-alias manifest declarations.
- **Compose State Optimization**: Migrated primitive Compose states from `mutableStateOf` to type-safe state delegates.
