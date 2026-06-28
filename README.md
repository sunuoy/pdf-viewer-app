# Modern PDF Reader & Document Utility Suite

A high-performance, feature-rich Android application designed for offline PDF viewing, text extraction, translation, and complete document manipulation. Built with modern Android development best practices including Jetpack Compose, Material Design 3, Coroutines, Room Database, and Storage Access Framework (SAF).

---

## 📱 Features

### 📄 Document Viewer & Reader
* **High-Performance Rendering**: Smooth rendering and zooming of complex PDF files.
* **Offline Workspace**: Complete offline functionality with zero server dependency.
* **Recent Documents History**: Remembers your recent files, last-opened timestamp, and exact reading progress page.
* **Bookmarks & Search**: Quick document bookmarking and in-text content searching powered by PDFBox.
* **ML Kit Text Translation**: Built-in translation capabilities using Google ML Kit.

### 🛠️ PDF Editor & Document Conversion Tools
* **PDF to Images (`.zip`)**: Render pages as high-resolution PNG images and pack them into a downloadable Zip archive.
* **PDF to Word (`.doc`)**: Extract document content and format into an editable Word document.
* **PDF to Text (`.txt`)**: Clean text extraction from single or multi-page documents.
* **Merge PDFs**: Select multiple PDF documents from storage and combine them into a single continuous file.
* **Split PDF**: Separate multi-page documents into individual files.
* **Compress PDF**: Optimize and reduce document file size for easy sharing.
* **Rotate Pages**: Adjust document page orientations.

### 🔒 Privacy & User Control
* **Storage Access Framework (SAF)**: Explicit destination prompts (`CreateDocument`) before saving converted or modified files, allowing you to choose exactly where output files are stored on your device.

---

## 🛠️ Technology Stack & Architecture

* **UI Framework**: [Jetpack Compose](https://developer.android.com/jetpack/compose) with Material Design 3 and Dynamic Color palette.
* **Architecture Pattern**: Clean Architecture with MVVM (Model-View-ViewModel) pattern and Unidirectional Data Flow.
* **Database**: [Room Persistence Library](https://developer.android.com/training/data-storage/room) for fast local metadata caching.
* **Asynchronous Processing**: Kotlin Coroutines & `Flow` for reactive UI updates and off-thread IO execution.
* **PDF Processing Engine**: [PDFBox-Android](https://github.com/TomRoush/PdfBox-Android) for advanced text extraction, document parsing, and PDF merging.
* **AI & Translation**: [Google ML Kit Translate](https://developers.google.com/ml-kit/language/translation) for offline text translation.
* **Build Configuration**: Gradle Kotlin DSL (`build.gradle.kts`), Java 17, KSP annotation processing.

---

## 🚀 Getting Started

### Prerequisites
* Android Studio Ladybug | 2024.2.1 or newer
* Android SDK Platform API Level 34 / 36
* JDK 17
* Android Device / Emulator running API Level 24 (Android 7.0) or higher

### Building and Running
1. Clone the repository:
   ```bash
   git clone https://github.com/sunuoy/pdf-viewer-app.git
   ```
2. Open the project in Android Studio.
3. Sync Gradle project files.
4. Build and install the debug APK on your connected device or emulator:
   ```bash
   ./gradlew installDebug
   ```

---

## 📄 License

This project is licensed under the MIT License - see the LICENSE file for details.
