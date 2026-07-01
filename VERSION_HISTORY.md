# Project Version History

This file tracks all modifications, updates, and releases for the PDF Viewer App.

## [V 1.0.1] - Major Feature Update - 2026-07-01
### Added
- **Persistent Highlights & Markup Manager**: Integrated a database-driven highlighting system with a custom manager dialog to add and delete key phrases with colored labels.
- **Real-Time Spoken Word Highlighting**: Overrode TTS progress listeners to dynamically track pronunciation coordinates and paint spoken words on the Compose canvas in real-time.
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
