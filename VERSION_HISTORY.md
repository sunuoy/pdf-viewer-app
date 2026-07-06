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

## [V 1.5.1] - Advanced Images to PDF Formatting & Grid Layout - 2026-07-06
### Added
- **3-Column Grid Layout**: Selection screen displaying images in a clean, square grid layout with page order indicators.
- **Translucent Circle Edit Button**: Translucent circle with expand icon in top-right of each image tile for direct editing access.
- **Dialog-Embedded Management**: Move Left, Move Right, and Delete options inside the image editor dialog.
- **Scrollable Dialog Support**: Scroll support on Page Sizing, Borders, and Page Number dialogs to resolve layout clipping on small screens.
- **Page Number Custom Dialog**: Fully styled page style numbering selection with a Set as default checkbox.
- **Instant PDF Preview**: Real-time PDF layout preview with page numbers, custom margins, and borders, loaded via file:// URI.
- **Interactive Settings summary card**: Clickable AssistChips directly on screen to modify page size, layout margins, and numbering.

## [V 1.5.0] - Dedicated Arrange Images Screen & Memory Optimization - 2026-07-06
### Added
- **Dedicated Arrange Images Screen**: Full-screen image list ordering, file deletion, and quick addition flow replacing the limited dialog layout.
- **Page Layout Sizing Settings**: Integrated custom PDF target sizing (Auto-size matching images, A4 Portrait, A4 Landscape) with high-quality scaling.
- **Memory Safety Optimization**: Integrated explicit native bitmap recycling in PDF image compilation loops (for both local images and ZIP compilation) to prevent OOM exceptions on large batches.
- **Premium UI Upgrades**: Redesigned layout selection panel using gradient-filled background cards and clean, custom Material icons.

## [V 1.4.0] - Secure PDF Tools & Quick Action Layout - 2026-07-05
### Added
- **Secure PDF Encryption**: Password protection, password removal, and custom watermarking tools added to the PDF Document Editor.
- **Enhance Created PDFs**: New 3-column quick action grid layout section added to the Editor screen.
- **Device & App Diagnostics**: System launch time tracking, live session uptime, and detailed build/version code added to the settings screen.
- **Scroll Mapping Synchronization**: Synchronized scrollbar thumb and pages navigation mapping to eliminate layout jumps.
### Changed
- **Home Screen Label**: Changed 'Open PDF File' action text to 'Open File'.
### Fixed
- **Password Protected PDF Loading**: Dynamically prompts for password, decrypts securely in cache, and loads the decrypted copy securely.

## [V 1.2.3] - Tabbed Drawer & Reader Bar Promotion - 2026-07-05
### Added
- **Relocated Drawer Navigation**: Re-architected drawer layout by relocating Documents, PDF, Control, and Misc options to separate, dedicated pages.
- **Customize Reader Bar Promotion**: Promoted customizable bottom reader bar options for direct settings drawer access.

## [V 1.2.2] - Ruler Live Preview & Stripe Toggle - 2026-07-04
### Added
- **Live Ruler Preview**: Added a responsive, mini themed reading ruler preview inside the Ruler Settings dialog that updates in real-time as themes, height, stripe height, opacity, and stripe color are configured.
- **Stripe Color ON/OFF Switch**: Added a dedicated switch to toggle the focus stripe color tint on/off, leaving it fully transparent when disabled and dynamically disabling the RGB sliders.
### Removed
- **Open Multi-page PDF**: Completely removed this test/debug option from the main Home Screen dropdown options menu.

## [V 1.2.1] - Themed Reading Ruler & Tabbed Bookmarks - 2026-07-04
### Added
- **Reading Ruler Themes**: Added "Classic Wood Ruler" (wooden background, physical tick marks, and numbers) and "Memphis Retro" (retro indigo background, teal stripes, yellow dots/squiggles, pink triangles).
- **Ruler ON/OFF Switch**: Added direct toggle switch at the top of Ruler Settings dialog.
- **Manual RGB Stripe Color Picker**: Replaced predefined color row with Red, Green, and Blue manual color sliders along with a live color preview bubble.
- **Tabbed Bookmarks / Chapters / Notes Screen**: Rebuilt BookmarkScreen to feature standard top-bar tabs (Chapters, Bookmarks, Notes) with page-snippet text previews and a multi-functional bottom control bar.

## [V 1.2.0] - Collapsible Menu & Ruler Settings - 2026-07-04
### Added
- **Collapsible Options Menu**: Main dropdown menu categories are now collapsible with chevron arrow indicators.
- **Custom Ruler Settings Dialog**: Floating configuration panel with sliders for ruler height, stripe height, opacity, and selectable color circles.
- **Ruler Double-Tap Shortcut**: Double-tapping the Ruler quick-action button in the bottom reader bar instantly launches the settings dialog.
- **Ruler Menu Entry**: Added "Ruler Settings" to the options menu under Misc Options.

## [V 1.1.20] - Multi-Format & Comic Reader - 2026-07-04
### Added
- **Multi-Format Support**: Opened document formats including DOCX, ODT, RTF, UMD, and CHM.
- **Comic Reader (CBZ & CBR)**: Supported zip and rar comic books with sorting and on-demand loading.
- **Customizable Bottom Reader Bar**: Added double-line layout toggle, visibility controls, reordering buttons list, and accelerometer-based tilt-to-turn-page functionality.
- **Draggable Reading Ruler**: Interactive, semi-transparent highlight ruler bar that can be dragged vertically to focus on reading lines.

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
