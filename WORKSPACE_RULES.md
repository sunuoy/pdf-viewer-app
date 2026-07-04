# Workspace Development & Versioning Rules

Please adhere to the following rules when contributing to this repository:

## 1. Semantic Versioning Rule
* **Small Changes (`0.0.1` bump)**: Used for small tweaks, bug fixes, UI adjustments, logging, and performance updates (e.g., `1.0.1` -> `1.0.2`).
* **MINOR / Feature Updates (`0.1.0` bump)**: Used for adding new backward-compatible features like a new document utility or additional cloud integrations (e.g., `1.0.2` -> `1.1.0`).
* **MAJOR / Structural Changes (`1.0.0` bump)**: Used for major architectural shifts, breaking layout overhauls, database redesigns, or bumping the minimum SDK version (e.g., `1.0.2` -> `2.0.0`).

## 2. Build Version Code Rule
* The build integer `versionCode` defined in Gradle must increment by exactly `1` with each update (e.g., `7` -> `8` -> `9`).

## 3. Database Migration Rule
* Any changes to database schema properties must increment the database version in `AppDatabase.kt`. Destructive migrations are permitted only for offline caches or initial testing; production updates must specify explicit database migrations to preserve user history and bookmarks.

## 4. Assets Synchronization
* During application startup, check the active dynamic version and perform any necessary file or cache migrations (like asset copying) to match the new bundle layout.
