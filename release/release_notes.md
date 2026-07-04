# Release v1.1.4 - PDF Viewer & Multi-Format Reader

This release introduces comprehensive multi-format support, organized 3-dot options menu, and quick-action bar customization.

## New Features

### 1. Multi-Format Document Reader
The app can now open, parse, and render the following standard document and book formats:
* **DOCX** (Microsoft Word Documents)
* **ODT** (OpenDocument Text files)
* **RTF** (Rich Text Format documents)
* **UMD** (Universal Mobile Document format with UTF-16LE decryption)
* **CHM** (Compiled HTML Help files)

### 2. Comic Book Reader (CBZ & CBR)
Read comic books directly within the app:
* **CBZ** (Zip comic archives)
* **CBR** (Rar comic archives using `junrar`)
* Comic images are sorted and loaded on-demand to prevent memory/performance issues.

### 3. Categorized 3-Dot Options Menu
The viewer top bar's 3-dot menu has been reorganized:
* **PDF & Document Options** (Bookmarks, highlights, editor tools)
* **Control Options** (Auto-scroll, orientation rotation, TTS reader toggle)
* **Misc Options** (Day/night theme inversion, language settings, offline translation center)

### 4. Customizable Bottom Reader Bar
Customize the quick-action icons displayed in the bottom reader bar:
* Open the **Customize Reader Bar** dialog from the 3-dot menu to select which buttons (Auto Scroll, Scroll Direction, Search, TTS, Translate) appear.
* Settings are automatically saved to persistent device storage.
