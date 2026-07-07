package com.pdfsuny.app.ui.screens

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.RectF
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.speech.tts.TextToSpeech
import android.util.Log
import android.widget.Toast
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.content.pm.ActivityInfo
import kotlinx.coroutines.awaitCancellation
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.positionChanged
import kotlin.math.abs
import kotlin.math.roundToInt

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.ui.text.TextStyle
import android.graphics.PointF
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ShareCompat
import com.pdfsuny.app.data.AppDatabase
import com.pdfsuny.app.data.entities.Bookmark
import com.pdfsuny.app.data.entities.RecentPdf
import com.pdfsuny.app.services.PdfTextService
import com.pdfsuny.app.services.SearchMatch
import com.pdfsuny.app.services.PageTextAndPositions
import com.pdfsuny.app.services.TranslationService
import com.pdfsuny.app.services.TtsService
import com.pdfsuny.app.services.TtsState
import com.pdfsuny.app.ui.PdfSessionViewModel
import com.pdfsuny.app.ui.components.TooltipIconButton
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.pdfsuny.app.services.AutoScrollMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.CompletableDeferred
import android.util.LruCache
import androidx.compose.ui.platform.LocalDensity
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

enum class ViewerSubPage {
    NONE,
    PDF_DOCUMENT_OPTIONS,
    CONTROL_OPTIONS,
    MISC_OPTIONS
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfViewerScreen(
    pdfPath: String,
    onBack: () -> Unit,
    onNavigateToBookmarks: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    val sessionViewModel: PdfSessionViewModel = viewModel(
        viewModelStoreOwner = LocalContext.current as ComponentActivity
    )
    
    // Services
    val pdfTextService = remember { PdfTextService() }
    val translationService = remember { TranslationService() }
    val ttsService = remember { TtsService(context) }
    val rendererMutex = remember { Mutex() }
    
    // Database
    val database = remember { AppDatabase.getDatabase(context) }
    
    // State holders
    var pdfRenderer by remember { mutableStateOf<PdfRenderer?>(null) }
    var parcelFileDescriptor by remember { mutableStateOf<ParcelFileDescriptor?>(null) }
    var pageCount by remember { mutableStateOf(0) }
    var documentName by remember { mutableStateOf("Document") }
    var isTextDocument by remember { mutableStateOf(false) }
    var textDocumentContent by remember { mutableStateOf<String?>(null) }
    var isComicBook by remember { mutableStateOf(false) }
    var comicPages by remember { mutableStateOf<List<com.pdfsuny.app.services.ComicService.ComicPage>>(emptyList()) }
    
    // Password decryption state
    var unlockedTempFile by remember { mutableStateOf<File?>(null) }
    var showPasswordPrompt by remember { mutableStateOf(false) }
    var passwordInput by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var activePasswordDeferred by remember { mutableStateOf<CompletableDeferred<String>?>(null) }

    
    // Page dimensions cache (pageIndex -> Pair(width, height))
    val pageSizes = remember { mutableStateMapOf<Int, Pair<Int, Int>>() }
    
    // Bounded bitmaps cache (evicts and recycles when full to prevent OOM)
    val bitmapCache = remember {
        object : LruCache<Int, Bitmap>(8) {
            override fun entryRemoved(evicted: Boolean, key: Int?, oldValue: Bitmap?, newValue: Bitmap?) {
                if (evicted && oldValue != null && !oldValue.isRecycled) {
                    oldValue.recycle()
                }
            }
        }
    }
    
    // Navigation / Scroll State
    val listState = rememberLazyListState()
    val currentPageIndex by remember {
        derivedStateOf {
            val visibleItems = listState.layoutInfo.visibleItemsInfo
            if (visibleItems.isEmpty()) 0
            else visibleItems.first().index.coerceIn(0, maxOf(0, pageCount - 1))
        }
    }
    
    // Bookmarking state
    val bookmarksFlow = remember(pdfPath) { database.bookmarkDao().getBookmarksForPdf(pdfPath) }
    val bookmarks by bookmarksFlow.collectAsState(initial = emptyList())
    val isCurrentPageBookmarked = remember(bookmarks, currentPageIndex) {
        bookmarks.any { it.pageNumber == currentPageIndex }
    }
    
    // UI control states
    val sharedPrefs = remember { context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE) }
    var isVerticalScroll by remember { mutableStateOf(sharedPrefs.getBoolean("is_vertical_scroll", true)) }
    
    val defaultButtons = remember {
        listOf(
            "orientation" to "Screen Orientation",
            "daynight" to "Day/Night Mode",
            "speak" to "Speak",
            "fontsize" to "Font Size",
            "autoscroll" to "Autoscroll",
            "chapters" to "Chapters",
            "bookmarks" to "Bookmarks",
            "brightness" to "Brightness",
            "search" to "Search",
            "tilt" to "Allow tilt device to turn page",
            "ruler" to "Ruler",
            "visual" to "Visual Options",
            "control" to "Control Options",
            "misc" to "Miscellaneous",
            "customize" to "Customize reader bar buttons"
        )
    }

    val savedOrder = remember {
        val raw = sharedPrefs.getString("reader_bar_order", null)
        if (raw == null) {
            "orientation,daynight,speak,fontsize,autoscroll,chapters,bookmarks,brightness,search,tilt,ruler,visual,control,misc,customize"
        } else {
            val keys = raw.split(",").filter { it.isNotEmpty() }.toMutableList()
            val defaultKeys = listOf("orientation", "daynight", "speak", "fontsize", "autoscroll", "chapters", "bookmarks", "brightness", "search", "tilt", "ruler", "visual", "control", "misc", "customize")
            defaultKeys.forEach { key ->
                if (key !in keys) {
                    keys.add(key)
                }
            }
            keys.joinToString(",")
        }
    }
    
    var buttonOrderList by remember { mutableStateOf(savedOrder.split(",").filter { it.isNotEmpty() }) }
    
    val buttonEnabledMap = remember {
        mutableStateMapOf<String, Boolean>().apply {
            defaultButtons.forEach { (id, _) ->
                val defaultVal = id in listOf("orientation", "daynight", "speak", "fontsize", "autoscroll", "chapters", "bookmarks", "brightness", "search", "ruler", "customize")
                put(id, sharedPrefs.getBoolean("reader_bar_enabled_$id", defaultVal))
            }
        }
    }
    
    var isDoubleLineLayout by remember { mutableStateOf(sharedPrefs.getBoolean("reader_bar_double_line", false)) }
    var isCustomizeReaderBarDialogOpen by remember { mutableStateOf(false) }
    
    // Custom button action states
    var isTiltToTurnPageEnabled by remember { mutableStateOf(sharedPrefs.getBoolean("is_tilt_to_turn_page", false)) }
    var isReadingRulerEnabled by remember { mutableStateOf(sharedPrefs.getBoolean("is_reading_ruler_enabled", false)) }
    var rulerYOffset by remember { mutableStateOf(sharedPrefs.getFloat("reading_ruler_y", 400f)) }
    var rulerHeight by remember { mutableStateOf(sharedPrefs.getFloat("reading_ruler_height", 55f)) }
    var rulerFocusStripeHeight by remember { mutableStateOf(sharedPrefs.getFloat("reading_ruler_stripe_height", 12f)) }
    var rulerColorStripe by remember { mutableStateOf(sharedPrefs.getInt("reading_ruler_stripe_color", 0xE6E9FF32.toInt())) }
    var isRulerStripeColorEnabled by remember { mutableStateOf(sharedPrefs.getBoolean("reading_ruler_stripe_color_enabled", true)) }
    var rulerOpacityOuter by remember { mutableStateOf(sharedPrefs.getFloat("reading_ruler_opacity_outer", 0.3f)) }
    var isRulerSettingsOpen by remember { mutableStateOf(false) }
    var rulerTheme by remember { mutableStateOf(sharedPrefs.getString("reading_ruler_theme", "solid") ?: "solid") }
    var textFontSize by remember { mutableStateOf(sharedPrefs.getFloat("text_font_size", 13f)) }
    var isBrightnessMenuOpen by remember { mutableStateOf(false) }
    var isAutoBrightness by remember { mutableStateOf(sharedPrefs.getBoolean("is_auto_brightness", true)) }
    var brightnessLevel by remember { mutableStateOf(sharedPrefs.getFloat("brightness_level", 0.5f)) }
    var isBlueLightFilterEnabled by remember { mutableStateOf(sharedPrefs.getBoolean("is_bluelight_filter_enabled", false)) }
    var blueLightOpacity by remember { mutableStateOf(sharedPrefs.getFloat("bluelight_opacity", 0.3f)) }
    val isScreenDimmed = !isAutoBrightness || isBlueLightFilterEnabled
    var isDarkThemeInverted by remember { mutableStateOf(sharedPrefs.getBoolean("night_mode_default", false)) }
    LaunchedEffect(isDarkThemeInverted) {
        sharedPrefs.edit().putBoolean("night_mode_default", isDarkThemeInverted).apply()
    }
    var pageColorType by remember { mutableStateOf(sharedPrefs.getString("reader_page_color_type", "original") ?: "original") }
    var isSearchActive by remember { mutableStateOf(false) }
    var isMenuExpanded by remember { mutableStateOf(false) }
    var isDocOptionsExpanded by remember { mutableStateOf(false) }
    var isPdfOptionsExpanded by remember { mutableStateOf(false) }
    var isControlOptionsExpanded by remember { mutableStateOf(false) }
    var isMiscOptionsExpanded by remember { mutableStateOf(false) }
    var useOpenGlEngine by remember { mutableStateOf(sharedPrefs.getBoolean("use_opengl_engine", false)) }
    var searchQuery by remember { mutableStateOf("") }
    var searchMatches by remember { mutableStateOf<List<SearchMatch>>(emptyList()) }
    var currentMatchIndex by remember { mutableStateOf(-1) }
    
    // Translation & TTS Mode
    var isTranslationBarActive by remember { mutableStateOf(false) }
    val activeTranslations = remember { mutableStateMapOf<Int, String>() }
    val isTranslatingMap = remember { mutableStateMapOf<Int, Boolean>() }
    var targetLanguageCode by remember { mutableStateOf("es") }
    
    // TTS State
    val ttsState by ttsService.state.collectAsState()
    val isMaleTts by ttsService.isMale.collectAsState()
    var currentTtsSpeed by remember { mutableStateOf(1.0f) }
    var currentTtsPitch by remember { mutableStateOf(1.0f) }
    
    // Dedicated TTS states
    var isTtsActive by remember { mutableStateOf(false) }
    var ttsTextCurrentPage by remember { mutableStateOf("") }
    var isTtsLoading by remember { mutableStateOf(false) }
    var activeTtsPageData by remember { mutableStateOf<PageTextAndPositions?>(null) }
    var activeTtsPageIndex by remember { mutableStateOf(-1) }
    
    // Clear TTS page tracking if TTS goes idle
    LaunchedEffect(ttsState) {
        if (ttsState == TtsState.IDLE) {
            activeTtsPageData = null
            activeTtsPageIndex = -1
        }
    }

    // Document Editor states
    var isEditorActive by remember { mutableStateOf(false) }
    var editorContent by remember { mutableStateOf("") }
    var isEditorLoading by remember { mutableStateOf(false) }

    // PDF Direct Text Word Editor states
    var isPdfWordEditorOpen by remember { mutableStateOf(false) }
    var wordToFind by remember { mutableStateOf("") }
    var replacementText by remember { mutableStateOf("") }
    var isPdfEditorSaving by remember { mutableStateOf(false) }

    // Viewer Signature states
    var showSignatureDialog by remember { mutableStateOf(false) }
    val signaturePaths = remember { mutableStateListOf<List<Offset>>() }
    var signatureAlignment by remember { mutableStateOf("bottom_right") }
    var sigInkR by remember { mutableIntStateOf(0) }
    var sigInkG by remember { mutableIntStateOf(0) }
    var sigInkB by remember { mutableIntStateOf(0) }
    var sigInkHexText by remember { mutableStateOf("#000000") }

    // Viewer Stamp states
    var showStampDialog by remember { mutableStateOf(false) }
    var stampText by remember { mutableStateOf("APPROVED") }
    var stampTypeTab by remember { mutableStateOf(0) }
    var stampAlignment by remember { mutableStateOf("center") }
    var stampImageUri by remember { mutableStateOf<Uri?>(null) }
    var stampR by remember { mutableIntStateOf(255) }
    var stampG by remember { mutableIntStateOf(0) }
    var stampB by remember { mutableIntStateOf(0) }
    var stampHexText by remember { mutableStateOf("#FF0000") }

    val stampImagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        stampImageUri = uri
    }
    var pdfReloadTrigger by remember { mutableStateOf(0) }

    var activeSubPage by remember { mutableStateOf(ViewerSubPage.NONE) }

    fun startTtsForPage(pageIndex: Int) {
        if (isTtsLoading) return
        if (isComicBook) {
            Toast.makeText(context, "TTS is not supported on comic book archives", Toast.LENGTH_SHORT).show()
            return
        }
        isTtsLoading = true
        isTtsActive = true
        scope.launch {
            try {
                if (isTextDocument) {
                    val text = textDocumentContent ?: ""
                    if (text.isNotBlank()) {
                        ttsService.setLanguage(Locale.getDefault().toLanguageTag())
                        ttsService.startSpeaking(text)
                    } else {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "No text content found to read", Toast.LENGTH_SHORT).show()
                            isTtsActive = false
                        }
                    }
                } else {
                    val pageData = pdfTextService.getPageTextAndPositions(context, Uri.parse(pdfPath), pageIndex)
                    if (pageData.text.isNotBlank()) {
                        activeTtsPageData = pageData
                        activeTtsPageIndex = pageIndex
                        ttsTextCurrentPage = pageData.text
                        ttsService.setLanguage(Locale.getDefault().toLanguageTag())
                        ttsService.startSpeaking(pageData.text)
                    } else {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "No readable text found on this page", Toast.LENGTH_SHORT).show()
                            isTtsActive = false
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("PdfViewerScreen", "Error starting TTS", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Failed to extract text: ${e.message}", Toast.LENGTH_SHORT).show()
                    isTtsActive = false
                }
            } finally {
                isTtsLoading = false
            }
        }
    }

    // Offline models manager states
    var isModelManagerOpen by remember { mutableStateOf(false) }
    val downloadedModelsMap = remember { mutableStateMapOf<String, Boolean>() }
    val downloadingModelsMap = remember { mutableStateMapOf<String, Boolean>() }

    fun refreshDownloadedModels() {
        scope.launch {
            translationService.supportedLanguages.forEach { lang ->
                val isDownloaded = translationService.isModelDownloaded(lang.code)
                downloadedModelsMap[lang.code] = isDownloaded
            }
        }
    }

    fun downloadModel(langCode: String) {
        if (downloadingModelsMap[langCode] == true) return
        downloadingModelsMap[langCode] = true
        scope.launch {
            try {
                val success = translationService.downloadModel(langCode)
                if (success) {
                    downloadedModelsMap[langCode] = true
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Model downloaded successfully!", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Failed to download model.", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            } finally {
                downloadingModelsMap[langCode] = false
            }
        }
    }

    fun deleteModel(langCode: String) {
        scope.launch {
            try {
                val success = translationService.deleteModel(langCode)
                if (success) {
                    downloadedModelsMap[langCode] = false
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Model deleted successfully!", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Failed to delete model.", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Persistent highlights states
    var isHighlightManagerOpen by remember { mutableStateOf(false) }
    val highlightListFlow = remember(pdfPath) { database.highlightDao().getHighlightsForPdf(pdfPath) }
    val highlights by highlightListFlow.collectAsState(initial = emptyList())
    val highlightMatchesMap = remember { mutableStateMapOf<Int, MutableList<Pair<SearchMatch, Color>>>() }

    LaunchedEffect(highlights, pdfReloadTrigger) {
        highlightMatchesMap.clear()
        withContext(Dispatchers.IO) {
            highlights.forEach { highlight ->
                try {
                    val color = try {
                        Color(android.graphics.Color.parseColor(highlight.colorHex))
                    } catch (e: Exception) {
                        Color.Yellow
                    }
                    val matches = pdfTextService.searchInsidePdf(context, Uri.parse(pdfPath), highlight.phrase)
                    matches.forEach { match ->
                        withContext(Dispatchers.Main) {
                            highlightMatchesMap.getOrPut(match.pageIndex) { mutableStateListOf() }
                                .add(Pair(match, color))
                        }
                    }
                } catch (e: Exception) {
                    Log.e("PdfViewerScreen", "Error pre-calculating highlight matches", e)
                }
            }
        }
    }

    // Spoken word highlights tracking
    val currentWordRange by ttsService.currentWordRange.collectAsState()
    val currentSpokenWordRects = remember(currentWordRange, activeTtsPageData) {
        val range = currentWordRange
        val data = activeTtsPageData
        if (range != null && data != null) {
            pdfTextService.getRectsForRange(data, range.first, range.second)
        } else {
            emptyList()
        }
    }

    // Moon+ Reader Auto-Scroll State
    var isAutoScrollActive by remember { mutableStateOf(false) }
    var autoScrollMode by remember { mutableStateOf(AutoScrollMode.BY_PIXEL) }
    var autoScrollSpeed by remember { mutableStateOf(0.10f) } // 0.10x to 5.0x
    var rollingBlindY by remember { mutableStateOf(0f) }
    
    // Helper to translate a page
    fun translatePage(pageIndex: Int) {
        if (isTranslatingMap[pageIndex] == true) return
        isTranslatingMap[pageIndex] = true
        scope.launch {
            try {
                if (isComicBook) {
                    activeTranslations[pageIndex] = "Translation is not supported on comic books."
                } else {
                    val text = if (isTextDocument) {
                        textDocumentContent ?: ""
                    } else {
                        pdfTextService.extractTextFromPage(context, Uri.parse(pdfPath), pageIndex)
                    }
                    if (text.isNotBlank()) {
                        val translated = translationService.translate(
                            text = text,
                            targetLangCode = targetLanguageCode
                        )
                        activeTranslations[pageIndex] = translated
                    } else {
                        activeTranslations[pageIndex] = "No readable text found on this page."
                    }
                }
            } catch (e: Exception) {
                Log.e("PdfViewerScreen", "Error translating page $pageIndex", e)
                activeTranslations[pageIndex] = "Translation failed: ${e.message}"
            } finally {
                isTranslatingMap[pageIndex] = false
            }
        }
    }
    
    // Auto-translate visible/active page translations when target language changes
    LaunchedEffect(targetLanguageCode) {
        ttsService.setLanguage(targetLanguageCode)
        activeTranslations.keys.toList().forEach { pageIndex ->
            translatePage(pageIndex)
        }
    }
    
    // Clean up services
    DisposableEffect(Unit) {
        onDispose {
            ttsService.shutdown()
            translationService.closeActiveTranslator()
            bitmapCache.evictAll()
            try {
                pdfRenderer?.close()
                parcelFileDescriptor?.close()
            } catch (e: Exception) {
                // Ignore
            }
            try {
                unlockedTempFile?.delete()
            } catch (e: Exception) {
                // Ignore
            }
        }
    }
    
    // Scroll to page event listener from bookmarks
    LaunchedEffect(sessionViewModel) {
        sessionViewModel.jumpToPageEvent.collect { pageIndex ->
            if (pageIndex in 0 until pageCount) {
                listState.scrollToItem(pageIndex)
            }
        }
    }
    
    // Load PDF or Text/Markdown Document
    LaunchedEffect(pdfPath, pdfReloadTrigger) {
        withContext(Dispatchers.IO) {
            try {
                val uri = Uri.parse(pdfPath)
                documentName = getFileName(context, uri) ?: "Document"
                val lowerName = documentName.lowercase()
                
                val isTextDoc = lowerName.endsWith(".txt") || lowerName.endsWith(".md") || 
                                lowerName.endsWith(".html") || lowerName.endsWith(".htm") || 
                                lowerName.endsWith(".epub") || lowerName.endsWith(".docx") || 
                                lowerName.endsWith(".odt") || lowerName.endsWith(".rtf") || 
                                lowerName.endsWith(".umd") || lowerName.endsWith(".chm")
                
                val isComic = lowerName.endsWith(".cbz") || lowerName.endsWith(".cbr")
                
                val file = if (uri.scheme == "file") {
                    File(uri.path ?: "")
                } else {
                    val tempFile = File(context.cacheDir, "temp_render_${System.currentTimeMillis()}_$documentName")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(tempFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    tempFile
                }
                
                if (isTextDoc) {
                    isTextDocument = true
                    isComicBook = false
                    val content = when {
                        lowerName.endsWith(".epub") -> {
                            com.pdfsuny.app.services.EpubService.parseEpubToText(context, uri)
                        }
                        lowerName.endsWith(".docx") -> {
                            com.pdfsuny.app.services.DocxService.extractText(file)
                        }
                        lowerName.endsWith(".odt") -> {
                            com.pdfsuny.app.services.OdtService.extractText(file)
                        }
                        lowerName.endsWith(".rtf") -> {
                            com.pdfsuny.app.services.RtfService.extractText(file)
                        }
                        lowerName.endsWith(".umd") -> {
                            com.pdfsuny.app.services.UmdService.extractText(file)
                        }
                        lowerName.endsWith(".chm") -> {
                            com.pdfsuny.app.services.ChmService.extractText(file)
                        }
                        else -> {
                            file.readText(Charsets.UTF_8)
                        }
                    }
                    if (uri.scheme == "content" && file.name.startsWith("temp_render_")) {
                        try { file.delete() } catch(e: Exception) {}
                    }
                    textDocumentContent = content
                    pageCount = 1
                } else if (isComic) {
                    isTextDocument = false
                    isComicBook = true
                    val pages = com.pdfsuny.app.services.ComicService.getPages(file)
                    comicPages = pages
                    pageCount = pages.size
                    for (i in pages.indices) {
                        pageSizes[i] = Pair(768, 1024)
                    }
                    if (uri.scheme == "content" && file.name.startsWith("temp_render_")) {
                        try { file.delete() } catch(e: Exception) {}
                    }
                } else {
                    isTextDocument = false
                    isComicBook = false
                    try {
                        pdfRenderer?.close()
                        parcelFileDescriptor?.close()
                    } catch (e: Exception) {
                        // Ignore
                    }
                    
                    // Check if PDF is encrypted/password protected
                    var isEncrypted = false
                    try {
                        val tempCheckFile = File(context.cacheDir, "temp_view_check_${System.currentTimeMillis()}.pdf")
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            tempCheckFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                        try {
                            com.tom_roush.pdfbox.pdmodel.PDDocument.load(tempCheckFile).close()
                        } catch (e: Exception) {
                            if (e.javaClass.simpleName == "InvalidPasswordException" || 
                                e.message?.contains("password", ignoreCase = true) == true || 
                                e.message?.contains("encrypted", ignoreCase = true) == true) {
                                isEncrypted = true
                            }
                        } finally {
                            tempCheckFile.delete()
                        }
                    } catch (e: Exception) {
                        // Ignore
                    }

                    if (isEncrypted) {
                        var success = false
                        while (!success) {
                            val deferred = CompletableDeferred<String>()
                            activePasswordDeferred = deferred
                            showPasswordPrompt = true
                            val enteredPassword = deferred.await()
                            
                            if (enteredPassword == "__CANCEL_PDF_VIEW__") {
                                withContext(Dispatchers.Main) {
                                    onBack()
                                }
                                return@withContext
                            }
                            
                            try {
                                val tempDecrypted = File(context.cacheDir, "unlocked_${System.currentTimeMillis()}.pdf")
                                context.contentResolver.openInputStream(uri)?.use { input ->
                                    val document = com.tom_roush.pdfbox.pdmodel.PDDocument.load(input, enteredPassword)
                                    document.setAllSecurityToBeRemoved(true)
                                    tempDecrypted.outputStream().use { output ->
                                        document.save(output)
                                    }
                                    document.close()
                                }
                                unlockedTempFile = tempDecrypted
                                success = true
                                showPasswordPrompt = false
                                passwordError = null
                            } catch (e: Exception) {
                                passwordError = "Incorrect password. Please try again."
                            }
                        }
                    }

                    val pfd = if (unlockedTempFile != null) {
                        ParcelFileDescriptor.open(unlockedTempFile, ParcelFileDescriptor.MODE_READ_ONLY)
                    } else if (uri.scheme == "file") {
                        ParcelFileDescriptor.open(File(uri.path ?: ""), ParcelFileDescriptor.MODE_READ_ONLY)
                    } else {
                        context.contentResolver.openFileDescriptor(uri, "r")
                    }
                    if (pfd != null) {
                        parcelFileDescriptor = pfd
                        val renderer = PdfRenderer(pfd)
                        pdfRenderer = renderer
                        pageCount = renderer.pageCount
                        
                        rendererMutex.withLock {
                            for (i in 0 until renderer.pageCount) {
                                val page = renderer.openPage(i)
                                pageSizes[i] = Pair(page.width, page.height)
                                page.close()
                            }
                        }
                        
                        PdfTextService.init(context)
                    }
                }

                if (pdfReloadTrigger == 0) {
                    val recent = database.recentPdfDao().getRecentPdfByPath(pdfPath)
                    if (recent != null && recent.lastPage < pageCount) {
                        withContext(Dispatchers.Main) {
                            listState.scrollToItem(recent.lastPage)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("PdfViewerScreen", "Error loading document", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Failed to load document file", Toast.LENGTH_LONG).show()
                    onBack()
                }
            }
        }
    }

    // Load initial download states for offline models
    LaunchedEffect(Unit) {
        refreshDownloadedModels()
    }
    
    // Tilt-to-turn page accelerometer listener
    LaunchedEffect(isTiltToTurnPageEnabled) {
        if (!isTiltToTurnPageEnabled) return@LaunchedEffect
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (accelerometer == null) {
            Toast.makeText(context, "No accelerometer sensor found", Toast.LENGTH_SHORT).show()
            isTiltToTurnPageEnabled = false
            sharedPrefs.edit().putBoolean("is_tilt_to_turn_page", false).apply()
            return@LaunchedEffect
        }
        
        var lastTurnTime = 0L
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (event == null) return
                val now = System.currentTimeMillis()
                if (now - lastTurnTime < 1500L) return
                
                val x = event.values[0]
                if (x > 3.5f) { // Tilt Left
                    scope.launch {
                        if (currentPageIndex > 0) {
                            listState.animateScrollToItem(currentPageIndex - 1)
                            lastTurnTime = now
                        }
                    }
                } else if (x < -3.5f) { // Tilt Right
                    scope.launch {
                        if (currentPageIndex < pageCount - 1) {
                            listState.animateScrollToItem(currentPageIndex + 1)
                            lastTurnTime = now
                        }
                    }
                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        
        sensorManager.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_NORMAL)
        try {
            awaitCancellation()
        } finally {
            sensorManager.unregisterListener(listener)
        }
    }

    // Toggle Screen Orientation Lock
    fun toggleScreenOrientation() {
        val activity = context as? ComponentActivity ?: return
        val currentOrientation = activity.requestedOrientation
        if (currentOrientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            Toast.makeText(context, "Orientation: Portrait", Toast.LENGTH_SHORT).show()
        } else {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            Toast.makeText(context, "Orientation: Landscape", Toast.LENGTH_SHORT).show()
        }
    }

    // Cycle text document font size
    fun cycleFontSize() {
        val sizes = listOf(11f, 13f, 15f, 17f, 19f, 21f)
        val currentIndex = sizes.indexOf(textFontSize)
        // Fallback in case old textFontSize is not in the new list (e.g. 22f)
        val nextIndex = if (currentIndex == -1) 1 else (currentIndex + 1) % sizes.size
        textFontSize = sizes[nextIndex]
        sharedPrefs.edit().putFloat("text_font_size", textFontSize).apply()
        Toast.makeText(context, "Font Size: ${textFontSize.toInt()}sp", Toast.LENGTH_SHORT).show()
    }

    // Apply screen brightness on state changes
    LaunchedEffect(isAutoBrightness, brightnessLevel) {
        val activity = context as? ComponentActivity ?: return@LaunchedEffect
        val layoutParams = activity.window.attributes
        if (isAutoBrightness) {
            layoutParams.screenBrightness = -1f
        } else {
            layoutParams.screenBrightness = brightnessLevel.coerceIn(0.01f, 1.0f)
        }
        activity.window.attributes = layoutParams
    }

    // Restore system brightness when leaving the document reader screen
    DisposableEffect(Unit) {
        onDispose {
            val activity = context as? ComponentActivity ?: return@onDispose
            val layoutParams = activity.window.attributes
            layoutParams.screenBrightness = -1f
            activity.window.attributes = layoutParams
        }
    }
    
    // Save last page progress to database (debounced to avoid heavy DB writes during active scroll)
    LaunchedEffect(currentPageIndex) {
        if (pageCount > 0) {
            delay(1000L) // Wait for 1 second of inactivity before saving
            val existing = database.recentPdfDao().getRecentPdfByPath(pdfPath)
            if (existing != null) {
                database.recentPdfDao().insertRecentPdf(
                    existing.copy(lastPage = currentPageIndex, lastOpened = System.currentTimeMillis())
                )
            }
        }
    }
    
    // Search handler
    LaunchedEffect(searchQuery) {
        if (searchQuery.length >= 3 && !isTextDocument && !isComicBook) {
            searchMatches = pdfTextService.searchInsidePdf(context, Uri.parse(pdfPath), searchQuery)
            currentMatchIndex = if (searchMatches.isNotEmpty()) 0 else -1
        } else {
            searchMatches = emptyList()
            currentMatchIndex = -1
        }
    }
    
    // Jump to match
    val currentMatch = remember(searchMatches, currentMatchIndex) {
        if (currentMatchIndex in searchMatches.indices) searchMatches[currentMatchIndex] else null
    }
    
    LaunchedEffect(currentMatch) {
        currentMatch?.let { match ->
            listState.animateScrollToItem(match.pageIndex)
        }
    }
    
    // Moon+ Reader Auto-Scroll Execution Loop
    LaunchedEffect(isAutoScrollActive, autoScrollMode, autoScrollSpeed) {
        if (!isAutoScrollActive) return@LaunchedEffect
        
        while (isAutoScrollActive) {
            when (autoScrollMode) {
                AutoScrollMode.BY_PIXEL -> {
                    val scrollAmount = (3f * autoScrollSpeed).coerceAtLeast(0.2f)
                    listState.scrollBy(scrollAmount)
                    delay(16L)
                }
                AutoScrollMode.BY_LINE -> {
                    val scrollAmount = 60f
                    listState.scrollBy(scrollAmount)
                    val delayMs = (1500L / autoScrollSpeed.toDouble()).toLong().coerceAtLeast(100L)
                    delay(delayMs)
                }
                AutoScrollMode.BY_PAGE -> {
                    val delayMs = (4000L / autoScrollSpeed.toDouble()).toLong().coerceAtLeast(300L)
                    delay(delayMs)
                    if (currentPageIndex < pageCount - 1) {
                        listState.animateScrollToItem(currentPageIndex + 1)
                    }
                }
                AutoScrollMode.ROLLING_BLIND_PIXEL,
                AutoScrollMode.ROLLING_BLIND_LINE -> {
                    rollingBlindY = (rollingBlindY + (3f * autoScrollSpeed)) % 800f
                    delay(16L)
                }
                else -> {
                    delay(16L)
                }
            }
        }
    }
    
    // Dark mode color matrix filter
    val darkInvertMatrix = remember {
        ColorMatrix(floatArrayOf(
            -1f, 0f, 0f, 0f, 255f, // Red
            0f, -1f, 0f, 0f, 255f, // Green
            0f, 0f, -1f, 0f, 255f, // Blue
            0f, 0f, 0f, 1f, 0f     // Alpha
        ))
    }
    
    LaunchedEffect(pageCount) {
        if (pageCount > 0) {
            if (sharedPrefs.getBoolean("open_bookmarks_on_launch", false)) {
                sharedPrefs.edit().putBoolean("open_bookmarks_on_launch", false).apply()
                onNavigateToBookmarks(currentPageIndex)
            }
            if (sharedPrefs.getBoolean("open_highlights_on_launch", false)) {
                sharedPrefs.edit().putBoolean("open_highlights_on_launch", false).apply()
                isHighlightManagerOpen = true
            }
            if (sharedPrefs.getBoolean("open_word_editor_on_launch", false)) {
                sharedPrefs.edit().putBoolean("open_word_editor_on_launch", false).apply()
                if (!isTextDocument && !isComicBook) {
                    isPdfWordEditorOpen = true
                }
            }
            if (sharedPrefs.getBoolean("open_editor_on_launch", false)) {
                sharedPrefs.edit().putBoolean("open_editor_on_launch", false).apply()
                if (!isComicBook) {
                    isEditorLoading = true
                    try {
                        if (isTextDocument) {
                            editorContent = textDocumentContent ?: ""
                            isEditorActive = true
                        } else {
                            val pageText = pdfTextService.extractTextFromPage(context, Uri.parse(pdfPath), currentPageIndex)
                            editorContent = pageText
                            isEditorActive = true
                        }
                    } catch (e: Exception) {
                        Log.e("PdfViewerScreen", "Failed to extract page text for launch editor", e)
                    } finally {
                        isEditorLoading = false
                    }
                }
            }
            if (sharedPrefs.getBoolean("share_on_launch", false)) {
                sharedPrefs.edit().putBoolean("share_on_launch", false).apply()
                sharePdf(context, Uri.parse(pdfPath))
            }
            if (sharedPrefs.getBoolean("auto_scroll_on_launch", false)) {
                sharedPrefs.edit().putBoolean("auto_scroll_on_launch", false).apply()
                isAutoScrollActive = true
            }
            if (sharedPrefs.getBoolean("tts_on_launch", false)) {
                sharedPrefs.edit().putBoolean("tts_on_launch", false).apply()
                if (!isComicBook) {
                    startTtsForPage(currentPageIndex)
                }
            }
            if (sharedPrefs.getBoolean("ruler_on_launch", false)) {
                sharedPrefs.edit().putBoolean("ruler_on_launch", false).apply()
                isReadingRulerEnabled = true
                sharedPrefs.edit().putBoolean("is_reading_ruler_enabled", true).apply()
            }
            if (sharedPrefs.getBoolean("customize_bar_on_launch", false)) {
                sharedPrefs.edit().putBoolean("customize_bar_on_launch", false).apply()
                isCustomizeReaderBarDialogOpen = true
            }
            if (sharedPrefs.getBoolean("translation_on_launch", false)) {
                sharedPrefs.edit().putBoolean("translation_on_launch", false).apply()
                isTranslationBarActive = true
            }
            if (sharedPrefs.getBoolean("models_on_launch", false)) {
                sharedPrefs.edit().putBoolean("models_on_launch", false).apply()
                refreshDownloadedModels()
                isModelManagerOpen = true
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (showPasswordPrompt) {
            AlertDialog(
                onDismissRequest = {
                    showPasswordPrompt = false
                    activePasswordDeferred?.complete("__CANCEL_PDF_VIEW__")
                },
                title = {
                    Text("Password Protected PDF", fontWeight = FontWeight.Bold)
                },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "This document is encrypted. Please enter the password to view it.",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        OutlinedTextField(
                            value = passwordInput,
                            onValueChange = { passwordInput = it },
                            label = { Text("Password") },
                            visualTransformation = if (passwordVisible) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                            trailingIcon = {
                                val image = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Icon(imageVector = image, contentDescription = "Toggle password visibility")
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            isError = passwordError != null
                        )
                        if (passwordError != null) {
                            Text(
                                text = passwordError ?: "",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (passwordInput.isBlank()) {
                                Toast.makeText(context, "Password cannot be empty", Toast.LENGTH_SHORT).show()
                            } else {
                                activePasswordDeferred?.complete(passwordInput)
                            }
                        }
                    ) {
                        Text("Unlock")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showPasswordPrompt = false
                            activePasswordDeferred?.complete("__CANCEL_PDF_VIEW__")
                        }
                    ) {
                        Text("Cancel")
                    }
                }
            )
        }

        Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = documentName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    TooltipIconButton(onClick = onBack, tooltipText = "Back") {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TooltipIconButton(
                        onClick = { isDarkThemeInverted = !isDarkThemeInverted },
                        tooltipText = "Toggle Dark Theme Inversion"
                    ) {
                        Icon(
                            imageVector = if (isDarkThemeInverted) Icons.Default.LightMode else Icons.Default.DarkMode,
                            contentDescription = "Day/Night Mode Inversion"
                        )
                    }
                    TooltipIconButton(
                        onClick = {
                            scope.launch {
                                if (isCurrentPageBookmarked) {
                                    bookmarks.find { it.pageNumber == currentPageIndex }?.let {
                                        database.bookmarkDao().deleteBookmark(it)
                                    }
                                } else {
                                    database.bookmarkDao().insertBookmark(
                                        Bookmark(
                                            pdfPath = pdfPath,
                                            pageNumber = currentPageIndex,
                                            note = "Bookmark added at page ${currentPageIndex + 1}",
                                            timestamp = System.currentTimeMillis()
                                        )
                                    )
                                }
                            }
                        },
                        tooltipText = if (isCurrentPageBookmarked) "Remove Bookmark" else "Add Bookmark"
                    ) {
                        Icon(
                            imageVector = if (isCurrentPageBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            contentDescription = "Bookmark",
                            tint = if (isCurrentPageBookmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Box {
                        TooltipIconButton(onClick = { isMenuExpanded = true }, tooltipText = "More Options") {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More options menu",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        DropdownMenu(
                            expanded = isMenuExpanded,
                            onDismissRequest = { isMenuExpanded = false }
                        ) {
                            // --- Documents Options ---
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { isDocOptionsExpanded = !isDocOptionsExpanded }
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Documents Options",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Icon(
                                    imageVector = if (isDocOptionsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = if (isDocOptionsExpanded) "Collapse" else "Expand",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            if (isDocOptionsExpanded) {
                                DropdownMenuItem(
                                    text = { Text("Open Bookmarks") },
                                    onClick = {
                                        isMenuExpanded = false
                                        onNavigateToBookmarks(currentPageIndex)
                                    },
                                    leadingIcon = { Icon(Icons.Default.Bookmarks, contentDescription = null) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Text Highlights") },
                                    onClick = {
                                        isMenuExpanded = false
                                        isHighlightManagerOpen = true
                                    },
                                    leadingIcon = { Icon(Icons.Default.BorderColor, contentDescription = null) }
                                )
                                if (!isComicBook) {
                                    DropdownMenuItem(
                                        text = { Text("Edit Page / Document") },
                                        onClick = {
                                            isMenuExpanded = false
                                            if (isTextDocument) {
                                                editorContent = textDocumentContent ?: ""
                                                isEditorActive = true
                                            } else {
                                                scope.launch {
                                                    isEditorLoading = true
                                                    try {
                                                        val pageText = pdfTextService.extractTextFromPage(context, Uri.parse(pdfPath), currentPageIndex)
                                                        editorContent = pageText
                                                        isEditorActive = true
                                                    } catch (e: Exception) {
                                                        Log.e("PdfViewerScreen", "Failed to extract page text", e)
                                                        Toast.makeText(context, "Failed to extract text for editing", Toast.LENGTH_SHORT).show()
                                                    } finally {
                                                        isEditorLoading = false
                                                    }
                                                }
                                            }
                                        },
                                        leadingIcon = {
                                            if (isEditorLoading) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(20.dp),
                                                    strokeWidth = 2.dp
                                                )
                                            } else {
                                                Icon(Icons.Default.EditNote, contentDescription = null)
                                            }
                                        }
                                    )
                                }
                                val shareLabel = when {
                                    isComicBook -> "Share Comic"
                                    isTextDocument -> "Share Document"
                                    else -> "Share Document"
                                }
                                DropdownMenuItem(
                                    text = { Text(shareLabel) },
                                    onClick = {
                                        isMenuExpanded = false
                                        sharePdf(context, Uri.parse(pdfPath))
                                    },
                                    leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) }
                                )
                            }

                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                            // --- PDF Options ---
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { isPdfOptionsExpanded = !isPdfOptionsExpanded }
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "PDF Options",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Icon(
                                    imageVector = if (isPdfOptionsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = if (isPdfOptionsExpanded) "Collapse" else "Expand",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            if (isPdfOptionsExpanded) {
                                if (!isTextDocument && !isComicBook) {
                                    DropdownMenuItem(
                                        text = { Text("Edit PDF Text") },
                                        onClick = {
                                            isMenuExpanded = false
                                            isPdfWordEditorOpen = true
                                        },
                                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Add Signature") },
                                        onClick = {
                                            isMenuExpanded = false
                                            signaturePaths.clear()
                                            showSignatureDialog = true
                                        },
                                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Add Stamp") },
                                        onClick = {
                                            isMenuExpanded = false
                                            stampImageUri = null
                                            showStampDialog = true
                                        },
                                        leadingIcon = { Icon(Icons.Default.Check, contentDescription = null) }
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text("OpenGL Reading Engine") },
                                    onClick = {
                                        useOpenGlEngine = true
                                        sharedPrefs.edit().putBoolean("use_opengl_engine", true).apply()
                                        bitmapCache.evictAll()
                                        pdfReloadTrigger++
                                        Toast.makeText(context, "OpenGL GPU Engine Enabled", Toast.LENGTH_SHORT).show()
                                    },
                                    leadingIcon = {
                                        RadioButton(
                                            selected = useOpenGlEngine,
                                            onClick = null
                                        )
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Normal/Standard View") },
                                    onClick = {
                                        useOpenGlEngine = false
                                        sharedPrefs.edit().putBoolean("use_opengl_engine", false).apply()
                                        bitmapCache.evictAll()
                                        pdfReloadTrigger++
                                        Toast.makeText(context, "Standard Reading Engine Enabled", Toast.LENGTH_SHORT).show()
                                    },
                                    leadingIcon = {
                                        RadioButton(
                                            selected = !useOpenGlEngine,
                                            onClick = null
                                        )
                                    }
                                )
                            }

                            // --- Control Options ---
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { isControlOptionsExpanded = !isControlOptionsExpanded }
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Control Options",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Icon(
                                    imageVector = if (isControlOptionsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = if (isControlOptionsExpanded) "Collapse" else "Expand",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            if (isControlOptionsExpanded) {
                                DropdownMenuItem(
                                    text = { Text(if (isAutoScrollActive) "Stop Auto Scroll" else "Start Auto Scroll") },
                                    onClick = {
                                        isMenuExpanded = false
                                        isAutoScrollActive = !isAutoScrollActive
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = if (isAutoScrollActive) Icons.Default.Pause else Icons.Default.PlayArrow,
                                            contentDescription = null
                                        )
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(if (isVerticalScroll) "Switch to Horizontal" else "Switch to Vertical") },
                                    onClick = {
                                        isMenuExpanded = false
                                        isVerticalScroll = !isVerticalScroll
                                        sharedPrefs.edit().putBoolean("is_vertical_scroll", isVerticalScroll).apply()
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = if (isVerticalScroll) Icons.Default.SwapHoriz else Icons.Default.SwapVert,
                                            contentDescription = null
                                        )
                                    }
                                )
                                if (!isComicBook) {
                                    DropdownMenuItem(
                                        text = { Text(if (isTtsActive) "Stop TTS Reading" else "Start TTS Reading") },
                                        onClick = {
                                            isMenuExpanded = false
                                            if (isTtsActive) {
                                                ttsService.stop()
                                                isTtsActive = false
                                            } else {
                                                startTtsForPage(currentPageIndex)
                                            }
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = if (isTtsActive) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                                                contentDescription = null
                                            )
                                        }
                                    )
                                }
                                
                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                                
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = "Page Color:",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        val pageColorsList = listOf(
                                            "original" to ("Original" to Color.White),
                                            "sepia" to ("Sepia" to Color(0xFFF4ECD8)),
                                            "mint" to ("Mint" to Color(0xFFE8F5E9)),
                                            "warm" to ("Warm" to Color(0xFFFDF2E9))
                                        )
                                        pageColorsList.forEach { (colorId, pair) ->
                                            val (label, displayColor) = pair
                                            val isSelected = pageColorType == colorId
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                                    .clickable {
                                                        pageColorType = colorId
                                                        sharedPrefs.edit().putString("reader_page_color_type", colorId).apply()
                                                    }
                                                    .padding(vertical = 6.dp, horizontal = 2.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Column(
                                                    horizontalAlignment = Alignment.CenterHorizontally,
                                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(16.dp)
                                                            .clip(RoundedCornerShape(8.dp))
                                                            .background(displayColor)
                                                            .border(
                                                                width = 1.dp,
                                                                color = if (isSelected) Color.White else Color.Gray,
                                                                shape = RoundedCornerShape(8.dp)
                                                            )
                                                    )
                                                    Text(
                                                        text = label,
                                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                        maxLines = 1
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                            // --- Misc Options ---
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { isMiscOptionsExpanded = !isMiscOptionsExpanded }
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Misc Options",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Icon(
                                    imageVector = if (isMiscOptionsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = if (isMiscOptionsExpanded) "Collapse" else "Expand",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            if (isMiscOptionsExpanded) {
                                DropdownMenuItem(
                                    text = { Text(if (isDarkThemeInverted) "Light Theme" else "Dark Theme Inversion") },
                                    onClick = {
                                        isMenuExpanded = false
                                        isDarkThemeInverted = !isDarkThemeInverted
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = if (isDarkThemeInverted) Icons.Default.LightMode else Icons.Default.DarkMode,
                                            contentDescription = null
                                        )
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Translation Settings") },
                                    onClick = {
                                        isMenuExpanded = false
                                        isTranslationBarActive = true
                                    },
                                    leadingIcon = { Icon(Icons.Default.Translate, contentDescription = null) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Offline Translation Models") },
                                    onClick = {
                                        isMenuExpanded = false
                                        isModelManagerOpen = true
                                    },
                                    leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Ruler Settings") },
                                    onClick = {
                                        isMenuExpanded = false
                                        isRulerSettingsOpen = true
                                    },
                                    leadingIcon = { Icon(Icons.Default.Tune, contentDescription = null) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Customize Reader Bar") },
                                    onClick = {
                                        isMenuExpanded = false
                                        isCustomizeReaderBarDialogOpen = true
                                    },
                                    leadingIcon = { Icon(Icons.Default.Build, contentDescription = null) }
                                )
                            }
                        }
                    }
                }
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                val ReaderBarButtonIcon = @Composable { id: String ->
                    when (id) {
                        "orientation" -> {
                            TooltipIconButton(onClick = { toggleScreenOrientation() }, tooltipText = "Screen Orientation") {
                                Icon(
                                    imageVector = Icons.Default.StayCurrentPortrait,
                                    contentDescription = "Screen Orientation",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                        "daynight" -> {
                            TooltipIconButton(onClick = { isDarkThemeInverted = !isDarkThemeInverted }, tooltipText = "Toggle Dark Theme Inversion") {
                                Icon(
                                    imageVector = if (isDarkThemeInverted) Icons.Default.LightMode else Icons.Default.DarkMode,
                                    contentDescription = "Day/Night Mode Inversion",
                                    tint = if (isDarkThemeInverted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                        "speak" -> {
                            if (!isComicBook) {
                                TooltipIconButton(onClick = {
                                    if (isTtsActive) {
                                        ttsService.stop()
                                        isTtsActive = false
                                    } else {
                                        startTtsForPage(currentPageIndex)
                                    }
                                }, tooltipText = "Read Aloud (TTS)") {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                        contentDescription = "TTS Read Page",
                                        tint = if (isTtsActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                        "fontsize" -> {
                            TooltipIconButton(onClick = {
                                if (isTextDocument) {
                                    cycleFontSize()
                                } else {
                                    Toast.makeText(context, "Font size adjustment is only supported for reflowable text. Use pinch-to-zoom for PDFs.", Toast.LENGTH_LONG).show()
                                }
                            }, tooltipText = "Font Size") {
                                Icon(
                                    imageVector = Icons.Default.FormatSize,
                                    contentDescription = "Font Size",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                        "autoscroll" -> {
                            TooltipIconButton(onClick = { isAutoScrollActive = !isAutoScrollActive }, tooltipText = "Auto Scroll") {
                                Icon(
                                    imageVector = if (isAutoScrollActive) Icons.Default.PauseCircle else Icons.Default.PlayCircle,
                                    contentDescription = "Auto Scroll",
                                    tint = if (isAutoScrollActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                        "chapters" -> {
                            TooltipIconButton(onClick = { onNavigateToBookmarks(currentPageIndex) }, tooltipText = "Open Bookmarks") {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.FormatListBulleted,
                                    contentDescription = "Open Bookmarks",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                        "bookmarks" -> {
                            TooltipIconButton(onClick = {
                                scope.launch {
                                    if (isCurrentPageBookmarked) {
                                        bookmarks.find { it.pageNumber == currentPageIndex }?.let {
                                            database.bookmarkDao().deleteBookmark(it)
                                        }
                                    } else {
                                        database.bookmarkDao().insertBookmark(
                                            Bookmark(
                                                pdfPath = pdfPath,
                                                pageNumber = currentPageIndex,
                                                note = "Bookmark added at page ${currentPageIndex + 1}",
                                                timestamp = System.currentTimeMillis()
                                            )
                                        )
                                    }
                                }
                            }, tooltipText = if (isCurrentPageBookmarked) "Remove Bookmark" else "Add Bookmark") {
                                Icon(
                                    imageVector = if (isCurrentPageBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                    contentDescription = "Bookmark",
                                    tint = if (isCurrentPageBookmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                        "brightness" -> {
                            TooltipIconButton(onClick = { isBrightnessMenuOpen = true }, tooltipText = "Toggle Brightness Overlay") {
                                Icon(
                                    imageVector = Icons.Default.WbSunny,
                                    contentDescription = "Brightness Overlay",
                                    tint = if (isScreenDimmed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                        "search" -> {
                            TooltipIconButton(onClick = {
                                isSearchActive = !isSearchActive
                                if (!isSearchActive) {
                                    searchQuery = ""
                                }
                            }, tooltipText = "Search Text") {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Search text",
                                    tint = if (isSearchActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                        "tilt" -> {
                            TooltipIconButton(onClick = {
                                isTiltToTurnPageEnabled = !isTiltToTurnPageEnabled
                                sharedPrefs.edit().putBoolean("is_tilt_to_turn_page", isTiltToTurnPageEnabled).apply()
                                Toast.makeText(context, "Tilt Page Turn: ${if (isTiltToTurnPageEnabled) "ON" else "OFF"}", Toast.LENGTH_SHORT).show()
                            }, tooltipText = "Tilt Page Turn") {
                                Icon(
                                    imageVector = Icons.Default.ScreenLockRotation,
                                    contentDescription = "Allow tilt device to turn page",
                                    tint = if (isTiltToTurnPageEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                        "ruler" -> {
                            TooltipIconButton(
                                onClick = {
                                    isReadingRulerEnabled = !isReadingRulerEnabled
                                    sharedPrefs.edit().putBoolean("is_reading_ruler_enabled", isReadingRulerEnabled).apply()
                                    Toast.makeText(context, "Ruler: ${if (isReadingRulerEnabled) "ON" else "OFF"}", Toast.LENGTH_SHORT).show()
                                },
                                onDoubleClick = {
                                    isRulerSettingsOpen = true
                                },
                                tooltipText = "Ruler"
                            ) {
                                Icon(
                                    imageVector = Icons.Default.HorizontalSplit,
                                    contentDescription = "Ruler",
                                    tint = if (isReadingRulerEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                        "visual" -> {
                            TooltipIconButton(onClick = {
                                isTranslationBarActive = !isTranslationBarActive
                                if (isTranslationBarActive) {
                                    translatePage(currentPageIndex)
                                }
                            }, tooltipText = "Translate/TTS Pane") {
                                Icon(
                                    imageVector = Icons.Default.RemoveRedEye,
                                    contentDescription = "Translate/TTS Pane",
                                    tint = if (isTranslationBarActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                        "control" -> {
                            TooltipIconButton(onClick = {
                                isMenuExpanded = true
                            }, tooltipText = "Quick Control Options") {
                                Icon(
                                    imageVector = Icons.Default.SettingsApplications,
                                    contentDescription = "Quick Control Options",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                        "misc" -> {
                            TooltipIconButton(onClick = {
                                isModelManagerOpen = true
                            }, tooltipText = "Offline Models Manager") {
                                Icon(
                                    imageVector = Icons.Default.CloudDownload,
                                    contentDescription = "Offline Models Manager",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                        "customize" -> {
                            TooltipIconButton(onClick = {
                                isCustomizeReaderBarDialogOpen = true
                            }, tooltipText = "Customize Reader Bar Buttons") {
                                Icon(
                                    imageVector = Icons.Default.MoreHoriz,
                                    contentDescription = "Customize reader bar buttons",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Page Indicator / Selector
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Page ${currentPageIndex + 1} of $pageCount",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    
                    val enabledButtons = buttonOrderList.filter { buttonEnabledMap[it] == true }
                    
                    if (isDoubleLineLayout) {
                        val mid = (enabledButtons.size + 1) / 2
                        val firstRow = enabledButtons.take(mid)
                        val secondRow = enabledButtons.drop(mid)
                        Column(horizontalAlignment = Alignment.End) {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                firstRow.forEach { id -> ReaderBarButtonIcon(id) }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                secondRow.forEach { id -> ReaderBarButtonIcon(id) }
                            }
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            enabledButtons.forEach { id -> ReaderBarButtonIcon(id) }
                        }
                    }
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(if (isDarkThemeInverted) Color(0xFF121212) else Color(0xFFF1F5F9))
        ) {
            // Pages list or Text document display
            if (isTextDocument && textDocumentContent != null) {
                Card(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isDarkThemeInverted) Color(0xFF1E1E1E) else Color.White
                    ),
                    elevation = CardDefaults.cardElevation(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        val scrollState = androidx.compose.foundation.rememberScrollState()
                        val isTranslating = isTranslatingMap[0] == true
                        val displayText = if (isTranslationBarActive && activeTranslations.containsKey(0)) {
                            activeTranslations[0] ?: ""
                        } else {
                            textDocumentContent!!
                        }

                        Text(
                            text = displayText,
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(scrollState),
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontSize = textFontSize.sp,
                                lineHeight = textFontSize.sp
                            ),
                            color = if (isDarkThemeInverted) Color.White else Color(0xFF1E293B)
                        )

                        if (isTranslating) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.3f)),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                    }
                }
            } else if ((pdfRenderer != null || isComicBook) && pageCount > 0) {
                val configuration = LocalConfiguration.current
                val screenWidth = configuration.screenWidthDp.dp
                
                val PageItem = @Composable { index: Int ->
                    // Load or Render bitmap
                    var bitmap by remember(index, pdfReloadTrigger) { mutableStateOf<Bitmap?>(bitmapCache.get(index)) }
                    val density = LocalDensity.current
                    
                    LaunchedEffect(index, pdfReloadTrigger) {
                        if (bitmap == null) {
                            withContext(Dispatchers.IO) {
                                if (isComicBook) {
                                    if (index in comicPages.indices) {
                                        val page = comicPages[index]
                                        val bmp = com.pdfsuny.app.services.ComicService.loadPageBitmap(page)
                                        if (bmp != null) {
                                            bitmapCache.put(index, bmp)
                                            pageSizes[index] = Pair(bmp.width, bmp.height)
                                            bitmap = bmp
                                        }
                                    }
                                } else {
                                    pdfRenderer?.let { renderer ->
                                        try {
                                            val pageSize = pageSizes[index] ?: Pair(1, 1)
                                            val screenWidthPx = with(density) { screenWidth.toPx() }
                                            val dynamicScale = (screenWidthPx / pageSize.first.toFloat()).coerceIn(1.5f, 2.5f)
                                            
                                            val bmp = renderPageToBitmap(renderer, index, dynamicScale, rendererMutex)
                                            bitmapCache.put(index, bmp)
                                            bitmap = bmp
                                        } catch (e: Exception) {
                                            Log.e("PdfViewerScreen", "Error rendering page $index", e)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    
                    val pageSize = pageSizes[index] ?: Pair(1, 1)
                    val aspectRatio = pageSize.first.toFloat() / pageSize.second.toFloat()
                    
                    Card(
                        modifier = if (isVerticalScroll) {
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(aspectRatio)
                                .padding(horizontal = 8.dp)
                        } else {
                            Modifier
                                .fillMaxHeight()
                                .aspectRatio(aspectRatio)
                                .padding(vertical = 8.dp)
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = when {
                                isDarkThemeInverted -> Color(0xFF1E1E1E)
                                pageColorType == "sepia" -> Color(0xFFF4ECD8)
                                pageColorType == "mint" -> Color(0xFFE8F5E9)
                                pageColorType == "warm" -> Color(0xFFFDF2E9)
                                else -> Color.White
                            }
                        ),
                        elevation = CardDefaults.cardElevation(2.dp)
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            if (bitmap != null) {
                                ZoomableImage(
                                    bitmap = bitmap!!,
                                    aspectRatio = aspectRatio,
                                    isInverted = isDarkThemeInverted,
                                    colorFilter = when {
                                        isDarkThemeInverted -> ColorFilter.colorMatrix(darkInvertMatrix)
                                        pageColorType == "sepia" -> ColorFilter.tint(Color(0xFFF4ECD8), BlendMode.Multiply)
                                        pageColorType == "mint" -> ColorFilter.tint(Color(0xFFE8F5E9), BlendMode.Multiply)
                                        pageColorType == "warm" -> ColorFilter.tint(Color(0xFFFDF2E9), BlendMode.Multiply)
                                        else -> null
                                    },
                                    searchMatches = searchMatches.filter { it.pageIndex == index },
                                    highlightMatches = highlightMatchesMap[index] ?: emptyList(),
                                    ttsSpokenWordRects = if (index == activeTtsPageIndex) currentSpokenWordRects else emptyList(),
                                    pdfPageWidth = pageSize.first.toFloat(),
                                    pdfPageHeight = pageSize.second.toFloat()
                                )
                            } else {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(strokeWidth = 2.dp)
                                }
                            }

                            val hasTranslation = activeTranslations.containsKey(index)
                            val isTranslatingPage = isTranslatingMap[index] == true
                            
                            if (isTranslatingPage) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.Black.copy(alpha = 0.4f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                                }
                            } else if (hasTranslation && isTranslationBarActive) {
                                val translationText = activeTranslations[index] ?: ""
                                
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            if (isDarkThemeInverted) Color(0xED1E1E1E)
                                            else Color(0xF2F8FAFC)
                                        )
                                        .padding(16.dp)
                                ) {
                                    Column(modifier = Modifier.fillMaxSize()) {
                                        // Control Header
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            val currentLangName = remember(targetLanguageCode) {
                                                translationService.supportedLanguages.find { it.code == targetLanguageCode }?.name ?: "Spanish"
                                            }
                                            Text(
                                                text = "Translation ($currentLangName)",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Row {
                                                when (ttsState) {
                                                    TtsState.SPEAKING -> {
                                                        IconButton(
                                                            onClick = { ttsService.pause() }
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Default.Pause,
                                                                contentDescription = "Pause speaking",
                                                                tint = MaterialTheme.colorScheme.primary
                                                            )
                                                        }
                                                        IconButton(
                                                            onClick = { ttsService.stop() }
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Default.Stop,
                                                                contentDescription = "Stop speaking",
                                                                tint = MaterialTheme.colorScheme.error
                                                            )
                                                        }
                                                    }
                                                    TtsState.PAUSED -> {
                                                        IconButton(
                                                            onClick = { ttsService.resume() }
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Default.PlayArrow,
                                                                contentDescription = "Resume speaking",
                                                                tint = MaterialTheme.colorScheme.primary
                                                            )
                                                        }
                                                        IconButton(
                                                            onClick = { ttsService.stop() }
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Default.Stop,
                                                                contentDescription = "Stop speaking",
                                                                tint = MaterialTheme.colorScheme.error
                                                            )
                                                        }
                                                    }
                                                    TtsState.IDLE -> {
                                                        IconButton(
                                                            onClick = { ttsService.startSpeaking(translationText) }
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                                                contentDescription = "Speak translation",
                                                                tint = MaterialTheme.colorScheme.primary
                                                            )
                                                        }
                                                    }
                                                }
                                                IconButton(
                                                    onClick = {
                                                        ttsService.stop()
                                                        activeTranslations.remove(index)
                                                    }
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Close,
                                                        contentDescription = "Dismiss translation",
                                                        tint = MaterialTheme.colorScheme.error
                                                    )
                                                }
                                            }
                                        }
                                        
                                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                                        
                                        // TTS Speed & Pitch controls
                                        Column(
                                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "Speed:",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { speed ->
                                                    val isSelected = currentTtsSpeed == speed
                                                    val speedLabel = if (speed == 1.0f) "Normal" else "${speed}x"
                                                    FilterChip(
                                                        selected = isSelected,
                                                        onClick = {
                                                            currentTtsSpeed = speed
                                                            ttsService.setSpeed(speed)
                                                        },
                                                        label = { Text(speedLabel, fontSize = 11.sp) },
                                                        colors = FilterChipDefaults.filterChipColors(
                                                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                                        ),
                                                        modifier = Modifier.height(28.dp)
                                                    )
                                                }
                                            }
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "Pitch:",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                listOf(0.8f, 1.0f, 1.2f).forEach { pitch ->
                                                    val isSelected = currentTtsPitch == pitch
                                                    val pitchLabel = when (pitch) {
                                                        0.8f -> "Low"
                                                        1.0f -> "Normal"
                                                        else -> "High"
                                                    }
                                                    FilterChip(
                                                        selected = isSelected,
                                                        onClick = {
                                                            currentTtsPitch = pitch
                                                            ttsService.setPitch(pitch)
                                                        },
                                                        label = { Text(pitchLabel, fontSize = 11.sp) },
                                                        colors = FilterChipDefaults.filterChipColors(
                                                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                                        ),
                                                        modifier = Modifier.height(28.dp)
                                                    )
                                                }
                                            }
                                        }
                                        
                                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                                        
                                        // Scrollable Translated Text
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .weight(1f)
                                        ) {
                                            val transTextValue = remember(translationText) {
                                                TextFieldValue(translationText)
                                            }
                                            OutlinedTextField(
                                                value = transTextValue,
                                                onValueChange = {},
                                                readOnly = true,
                                                modifier = Modifier.fillMaxSize(),
                                                textStyle = MaterialTheme.typography.bodyMedium.copy(
                                                    fontSize = 15.sp,
                                                    lineHeight = 22.sp
                                                ),
                                                colors = TextFieldDefaults.colors(
                                                    focusedContainerColor = Color.Transparent,
                                                    unfocusedContainerColor = Color.Transparent,
                                                    disabledContainerColor = Color.Transparent,
                                                    focusedIndicatorColor = Color.Transparent,
                                                    unfocusedIndicatorColor = Color.Transparent
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (isVerticalScroll) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 16.dp, horizontal = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        itemsIndexed(
                            items = List(pageCount) { it },
                            key = { index, _ -> "page_$index" }
                        ) { index, _ ->
                            PageItem(index)
                        }
                    }
                } else {
                    LazyRow(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 8.dp, horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        itemsIndexed(
                            items = List(pageCount) { it },
                            key = { index, _ -> "page_$index" }
                        ) { index, _ ->
                            PageItem(index)
                        }
                    }
                }

                // Smooth scroll progress calculation
                val totalItems = pageCount
                val firstVisibleItem = listState.firstVisibleItemIndex
                val firstVisibleOffset = listState.firstVisibleItemScrollOffset
                val firstVisibleItemInfo = listState.layoutInfo.visibleItemsInfo.firstOrNull()
                
                val scrollFraction = remember(firstVisibleItem, firstVisibleOffset, totalItems) {
                    if (totalItems <= 1) 0f
                    else {
                        val itemSize = firstVisibleItemInfo?.size ?: 1
                        val progress = firstVisibleOffset.toFloat() / itemSize.toFloat()
                        ((firstVisibleItem.toFloat() + progress) / (totalItems - 1).toFloat()).coerceIn(0f, 1f)
                    }
                }

                // Autohide / sleep scrollbar logic
                var showScrollbar by remember { mutableStateOf(false) }
                var isDraggingScrollbar by remember { mutableStateOf(false) }
                LaunchedEffect(firstVisibleItem, firstVisibleOffset, isDraggingScrollbar) {
                    if (isDraggingScrollbar) {
                        showScrollbar = true
                    } else {
                        showScrollbar = true
                        kotlinx.coroutines.delay(2000L)
                        showScrollbar = false
                    }
                }

                val scrollbarAlpha by animateFloatAsState(
                    targetValue = if (showScrollbar) 1f else 0f,
                    animationSpec = tween(durationMillis = 300)
                )

                val density = androidx.compose.ui.platform.LocalDensity.current
                val scrollbarPaddingPx = remember(density) { with(density) { 32.dp.toPx() } }

                if (isVerticalScroll && totalItems > 1 && scrollbarAlpha > 0f) {
                    var trackHeight by remember { mutableStateOf(1f) }
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight()
                            .width(36.dp) // Touch-friendly target area
                            .graphicsLayer { alpha = scrollbarAlpha }
                            .onGloballyPositioned { coordinates ->
                                trackHeight = coordinates.size.height.toFloat()
                            }
                            .pointerInput(totalItems) {
                                detectTapGestures { offset ->
                                    val fraction = (offset.y / trackHeight).coerceIn(0f, 1f)
                                    val targetIndex = (fraction * (totalItems - 1)).roundToInt().coerceIn(0, totalItems - 1)
                                    scope.launch {
                                        listState.scrollToItem(targetIndex)
                                    }
                                }
                            }
                            .pointerInput(totalItems) {
                                detectDragGestures(
                                    onDragStart = { isDraggingScrollbar = true },
                                    onDragEnd = { isDraggingScrollbar = false },
                                    onDragCancel = { isDraggingScrollbar = false },
                                    onDrag = { change, _ ->
                                        change.consume()
                                        val fraction = (change.position.y / trackHeight).coerceIn(0f, 1f)
                                        val targetIndex = (fraction * (totalItems - 1)).roundToInt().coerceIn(0, totalItems - 1)
                                        scope.launch {
                                            listState.scrollToItem(targetIndex)
                                        }
                                    }
                                )
                            }
                            .padding(vertical = 16.dp, horizontal = 8.dp)
                    ) {
                        // Track
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Gray.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                        )
                        // Thumb
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(0.15f) // Thumb height is 15% of track height
                                .graphicsLayer {
                                    translationY = (trackHeight - scrollbarPaddingPx) * 0.85f * scrollFraction
                                }
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.8f), RoundedCornerShape(8.dp))
                        )
                    }
                } else if (!isVerticalScroll && totalItems > 1 && scrollbarAlpha > 0f) {
                    var trackWidth by remember { mutableStateOf(1f) }
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(36.dp) // Touch-friendly target area
                            .graphicsLayer { alpha = scrollbarAlpha }
                            .onGloballyPositioned { coordinates ->
                                trackWidth = coordinates.size.width.toFloat()
                            }
                            .pointerInput(totalItems) {
                                detectTapGestures { offset ->
                                    val fraction = (offset.x / trackWidth).coerceIn(0f, 1f)
                                    val targetIndex = (fraction * (totalItems - 1)).roundToInt().coerceIn(0, totalItems - 1)
                                    scope.launch {
                                        listState.scrollToItem(targetIndex)
                                    }
                                }
                            }
                            .pointerInput(totalItems) {
                                detectDragGestures(
                                    onDragStart = { isDraggingScrollbar = true },
                                    onDragEnd = { isDraggingScrollbar = false },
                                    onDragCancel = { isDraggingScrollbar = false },
                                    onDrag = { change, _ ->
                                        change.consume()
                                        val fraction = (change.position.x / trackWidth).coerceIn(0f, 1f)
                                        val targetIndex = (fraction * (totalItems - 1)).roundToInt().coerceIn(0, totalItems - 1)
                                        scope.launch {
                                            listState.scrollToItem(targetIndex)
                                        }
                                    }
                                )
                            }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        // Track
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Gray.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                        )
                        // Thumb
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(0.15f) // Thumb width is 15% of track width
                                .graphicsLayer {
                                    translationX = (trackWidth - scrollbarPaddingPx) * 0.85f * scrollFraction
                                }
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.8f), RoundedCornerShape(8.dp))
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            // Translation Control Panel Overlay
            if (isTranslationBarActive) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                        .align(Alignment.TopCenter),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                    ) {
                        // Title Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Translate,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "Page Translation",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = {
                                    refreshDownloadedModels()
                                    isModelManagerOpen = true
                                }) {
                                    Icon(
                                        imageVector = Icons.Default.CloudDownload,
                                        contentDescription = "Manage Offline Models",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                                IconButton(onClick = { isTranslationBarActive = false }) {
                                    Icon(Icons.Default.Close, contentDescription = "Close translation bar")
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // Actions Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val isCurrentPageTranslating = isTranslatingMap[currentPageIndex] == true
                            val hasCurrentPageTranslation = activeTranslations.containsKey(currentPageIndex)
                            
                            Button(
                                onClick = { translatePage(currentPageIndex) },
                                enabled = !isCurrentPageTranslating,
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            ) {
                                if (isCurrentPageTranslating) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                } else {
                                    Text(
                                        text = if (hasCurrentPageTranslation) "Re-translate" else "Translate Page",
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            
                            var expandedLang by remember { mutableStateOf(false) }
                            val currentLangName = remember(targetLanguageCode) {
                                translationService.supportedLanguages.find { it.code == targetLanguageCode }?.name ?: "Spanish"
                            }
                            
                            Box(modifier = Modifier.weight(1f)) {
                                Button(
                                    onClick = { expandedLang = true },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                ) {
                                    Text(
                                        text = currentLangName,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                }
                                
                                DropdownMenu(
                                    expanded = expandedLang,
                                    onDismissRequest = { expandedLang = false }
                                ) {
                                    translationService.supportedLanguages.forEach { lang ->
                                        DropdownMenuItem(
                                            text = { Text(lang.name) },
                                            onClick = {
                                                targetLanguageCode = lang.code
                                                expandedLang = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Search Bar overlay
            if (isSearchActive) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                        .align(Alignment.TopCenter),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(8.dp)
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text("Search inside document...") },
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(onClick = {
                                isSearchActive = false
                                searchQuery = ""
                            }) {
                                Icon(Icons.Default.Close, contentDescription = "Close search")
                            }
                        }
                        if (searchMatches.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Match ${currentMatchIndex + 1} of ${searchMatches.size}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Row {
                                    IconButton(
                                        onClick = {
                                            if (currentMatchIndex > 0) {
                                                currentMatchIndex--
                                            } else {
                                                currentMatchIndex = searchMatches.size - 1
                                            }
                                        }
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Prev match")
                                    }
                                    IconButton(
                                        onClick = {
                                            if (currentMatchIndex < searchMatches.size - 1) {
                                                currentMatchIndex++
                                            } else {
                                                currentMatchIndex = 0
                                            }
                                        }
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next match")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Moon+ Reader Rolling Blind Overlay
            if (isAutoScrollActive && (autoScrollMode == AutoScrollMode.ROLLING_BLIND_PIXEL || autoScrollMode == AutoScrollMode.ROLLING_BLIND_LINE)) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val lineY = rollingBlindY % size.height
                    drawLine(
                        color = Color(0xAA00E5FF),
                        start = Offset(0f, lineY),
                        end = Offset(size.width, lineY),
                        strokeWidth = 6f
                    )
                    drawRect(
                        color = Color(0x3300E5FF),
                        topLeft = Offset(0f, lineY - 20f),
                        size = Size(size.width, 40f)
                    )
                }
            }

            // Moon+ Reader Speed & Mode Floating Control HUD
            if (isAutoScrollActive) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 16.dp),
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f),
                    tonalElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        var expandedModeMenu by remember { mutableStateOf(false) }
                        
                        Box {
                            TextButton(onClick = { expandedModeMenu = true }) {
                                Text(autoScrollMode.displayName, fontWeight = FontWeight.Bold)
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                            }
                            DropdownMenu(
                                expanded = expandedModeMenu,
                                onDismissRequest = { expandedModeMenu = false }
                            ) {
                                AutoScrollMode.values().forEach { mode ->
                                    DropdownMenuItem(
                                        text = { Text(mode.displayName) },
                                        onClick = {
                                            autoScrollMode = mode
                                            expandedModeMenu = false
                                        }
                                    )
                                }
                            }
                        }
                        
                        VerticalDivider(modifier = Modifier.height(24.dp))
                        
                        IconButton(
                            onClick = { autoScrollSpeed = (autoScrollSpeed - 0.10f).coerceAtLeast(0.10f) }
                        ) {
                            Icon(Icons.Default.Remove, contentDescription = "Slower")
                        }
                        
                        Text(
                            text = String.format("%.2fx", autoScrollSpeed),
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        
                        IconButton(
                            onClick = { autoScrollSpeed = (autoScrollSpeed + 0.10f).coerceAtMost(5.0f) }
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Faster")
                        }
                        
                        IconButton(onClick = { isAutoScrollActive = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Close HUD", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }

            // Floating TTS Controller Panel
            if (isTtsActive) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 16.dp)
                        .fillMaxWidth(0.9f),
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f),
                    tonalElevation = 8.dp
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                    contentDescription = "TTS Active",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Text-to-Speech (Page ${currentPageIndex + 1})",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            IconButton(
                                onClick = {
                                    ttsService.stop()
                                    isTtsActive = false
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close TTS",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                        
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                        // TTS Controls Panel
                        if (isTtsLoading) {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            }
                        } else {
                            // Centered Playback controls Row (Play/Pause/Stop)
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                when (ttsState) {
                                    TtsState.SPEAKING -> {
                                        FilledIconButton(
                                            onClick = { ttsService.pause() },
                                            modifier = Modifier.size(44.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Pause,
                                                contentDescription = "Pause",
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(16.dp))
                                        FilledTonalIconButton(
                                            onClick = { ttsService.stop(); isTtsActive = false },
                                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                                            ),
                                            modifier = Modifier.size(44.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Stop,
                                                contentDescription = "Stop",
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    }
                                    TtsState.PAUSED -> {
                                        FilledIconButton(
                                            onClick = { ttsService.resume() },
                                            modifier = Modifier.size(44.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.PlayArrow,
                                                contentDescription = "Resume",
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(16.dp))
                                        FilledTonalIconButton(
                                            onClick = { ttsService.stop(); isTtsActive = false },
                                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                                            ),
                                            modifier = Modifier.size(44.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Stop,
                                                contentDescription = "Stop",
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    }
                                    TtsState.IDLE -> {
                                        FilledIconButton(
                                            onClick = { startTtsForPage(currentPageIndex) },
                                            modifier = Modifier.size(44.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.PlayArrow,
                                                contentDescription = "Play",
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            // Voice Selection Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Voice:",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.width(55.dp)
                                )
                                FilterChip(
                                    selected = !isMaleTts,
                                    onClick = { ttsService.setVoiceGender(false) },
                                    label = { Text("Female (System Default)", fontSize = 11.sp) },
                                    modifier = Modifier.height(28.dp)
                                )
                                FilterChip(
                                    selected = isMaleTts,
                                    onClick = { ttsService.setVoiceGender(true) },
                                    label = { Text("Male", fontSize = 11.sp) },
                                    modifier = Modifier.height(28.dp)
                                )
                            }

                            // Speed Selection Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Speed:",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.width(55.dp)
                                )
                                Row(
                                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { speed ->
                                        val isSelected = currentTtsSpeed == speed
                                        val speedLabel = if (speed == 1.0f) "Normal" else "${speed}x"
                                        FilterChip(
                                            selected = isSelected,
                                            onClick = {
                                                currentTtsSpeed = speed
                                                ttsService.setSpeed(speed)
                                            },
                                            label = { Text(speedLabel, fontSize = 11.sp) },
                                            modifier = Modifier.height(28.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Reading Ruler Overlay
            if (isReadingRulerEnabled) {
                val density = LocalDensity.current
                Box(
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .height(rulerHeight.dp)
                            .offset(y = with(density) { rulerYOffset.toDp() })
                            .clip(RoundedCornerShape(6.dp))
                            .graphicsLayer(compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen)
                            .pointerInput(Unit) {
                                detectDragGestures { change, dragAmount ->
                                    change.consume()
                                    rulerYOffset = (rulerYOffset + dragAmount.y).coerceIn(50f, 1800f)
                                    sharedPrefs.edit().putFloat("reading_ruler_y", rulerYOffset).apply()
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (rulerTheme == "solid") {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val widthPx = size.width
                                val heightPx = size.height
                                val stripeHeightPx = rulerFocusStripeHeight.dp.toPx()

                                drawRoundRect(
                                    color = Color(0xFF3E4756).copy(alpha = rulerOpacityOuter),
                                    size = size,
                                    cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                                )

                                drawRoundRect(
                                    color = Color.Transparent,
                                    topLeft = Offset(12.dp.toPx(), (heightPx - stripeHeightPx) / 2),
                                    size = Size(widthPx - 24.dp.toPx(), stripeHeightPx),
                                    cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                                    blendMode = androidx.compose.ui.graphics.BlendMode.Clear
                                )
                            }
                            if (isRulerStripeColorEnabled) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 28.dp)
                                        .height(rulerFocusStripeHeight.dp)
                                        .background(Color(rulerColorStripe))
                                )
                            }
                        } else {
                            // Custom theme drawing using Canvas
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val widthPx = size.width
                                val heightPx = size.height
                                val stripeHeightPx = rulerFocusStripeHeight.dp.toPx()

                                if (rulerTheme == "classic") {
                                    // --- Classic Wooden / Plastic Ruler ---
                                    // 1. Draw ruler base (sand/wood color with outer opacity)
                                    drawRoundRect(
                                        color = Color(0xFFDFD0C0).copy(alpha = rulerOpacityOuter),
                                        size = size,
                                        cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                                    )

                                    // 2. Draw tick marks along top & bottom
                                    val tickColor = Color(0xFF5D4037).copy(alpha = rulerOpacityOuter)
                                    val numTicks = 20
                                    val spacing = widthPx / numTicks
                                    val textPaint = android.graphics.Paint().apply {
                                        color = android.graphics.Color.argb((rulerOpacityOuter * 255).toInt(), 0x5D, 0x40, 0x37)
                                        textSize = 10.dp.toPx()
                                        textAlign = android.graphics.Paint.Align.CENTER
                                        typeface = android.graphics.Typeface.create("serif", android.graphics.Typeface.NORMAL)
                                    }

                                    for (i in 0..numTicks) {
                                        val x = i * spacing
                                        val tickLen = when {
                                            i % 10 == 0 -> 12.dp.toPx()
                                            i % 5 == 0 -> 8.dp.toPx()
                                            else -> 5.dp.toPx()
                                        }

                                        // Top ticks
                                        drawLine(
                                            color = tickColor,
                                            start = Offset(x, 0f),
                                            end = Offset(x, tickLen),
                                            strokeWidth = 1.dp.toPx()
                                        )

                                        // Bottom ticks
                                        drawLine(
                                            color = tickColor,
                                            start = Offset(x, heightPx),
                                            end = Offset(x, heightPx - tickLen),
                                            strokeWidth = 1.dp.toPx()
                                        )

                                        // Draw major numbers
                                        if (i % 2 == 0 && x > 20f && x < widthPx - 20f) {
                                            drawContext.canvas.nativeCanvas.drawText(
                                                (i / 2).toString(),
                                                x,
                                                12.dp.toPx() + tickLen,
                                                textPaint
                                            )
                                        }
                                    }
                                } else {
                                    // --- Memphis Retro Theme ---
                                    // 1. Draw base (indigo with outer opacity)
                                    drawRoundRect(
                                        color = Color(0xFF3F3D56).copy(alpha = rulerOpacityOuter),
                                        size = size,
                                        cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                                    )

                                    // 2. Draw retro diagonal stripes on left
                                    val stripeColor = Color(0xFF00ADB5).copy(alpha = rulerOpacityOuter)
                                    for (i in 0..5) {
                                        drawLine(
                                            color = stripeColor,
                                            start = Offset(20.dp.toPx() + i * 8.dp.toPx(), 4.dp.toPx()),
                                            end = Offset(4.dp.toPx() + i * 8.dp.toPx(), heightPx - 4.dp.toPx()),
                                            strokeWidth = 3.dp.toPx()
                                        )
                                    }

                                    // 3. Draw small yellow dots on right
                                    val dotColor = Color(0xFFFFD200).copy(alpha = rulerOpacityOuter)
                                    for (row in 0..2) {
                                        for (col in 0..3) {
                                            drawCircle(
                                                color = dotColor,
                                                radius = 2.dp.toPx(),
                                                center = Offset(widthPx - 40.dp.toPx() + col * 8.dp.toPx(), 12.dp.toPx() + row * 8.dp.toPx())
                                            )
                                        }
                                    }

                                    // 4. Draw yellow triangle bottom left
                                    val yellowPath = androidx.compose.ui.graphics.Path().apply {
                                        moveTo(30.dp.toPx(), heightPx - 4.dp.toPx())
                                        lineTo(45.dp.toPx(), heightPx - 20.dp.toPx())
                                        lineTo(60.dp.toPx(), heightPx - 4.dp.toPx())
                                        close()
                                    }
                                    drawPath(yellowPath, Color(0xFFFFD200).copy(alpha = rulerOpacityOuter))

                                    // 5. Draw pink triangle bottom right
                                    val pinkPath = androidx.compose.ui.graphics.Path().apply {
                                        moveTo(widthPx - 60.dp.toPx(), heightPx - 4.dp.toPx())
                                        lineTo(widthPx - 45.dp.toPx(), heightPx - 20.dp.toPx())
                                        lineTo(widthPx - 30.dp.toPx(), heightPx - 4.dp.toPx())
                                        close()
                                    }
                                    drawPath(pinkPath, Color(0xFFFF2E93).copy(alpha = rulerOpacityOuter))

                                    // 6. Draw cyan semi-circle bottom center
                                    drawArc(
                                        color = Color(0xFF00ADB5).copy(alpha = rulerOpacityOuter),
                                        startAngle = 180f,
                                        sweepAngle = 180f,
                                        useCenter = true,
                                        topLeft = Offset(widthPx / 2 - 15.dp.toPx(), heightPx - 15.dp.toPx()),
                                        size = Size(30.dp.toPx(), 30.dp.toPx())
                                    )

                                    // 7. Draw yellow squiggle top right
                                    drawLine(
                                        color = Color(0xFFFFD200).copy(alpha = rulerOpacityOuter),
                                        start = Offset(widthPx - 80.dp.toPx(), 10.dp.toPx()),
                                        end = Offset(widthPx - 65.dp.toPx(), 25.dp.toPx()),
                                        strokeWidth = 2.dp.toPx()
                                    )
                                }

                                // 3. Clear cutout in the middle for reading
                                drawRoundRect(
                                    color = Color.Transparent,
                                    topLeft = Offset(12.dp.toPx(), (heightPx - stripeHeightPx) / 2),
                                    size = Size(widthPx - 24.dp.toPx(), stripeHeightPx),
                                    cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                                    blendMode = androidx.compose.ui.graphics.BlendMode.Clear
                                )
                            }
                            // 4. Draw focus stripe with user selected color
                            if (isRulerStripeColorEnabled) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 28.dp)
                                        .height(rulerFocusStripeHeight.dp)
                                        .background(Color(rulerColorStripe).copy(alpha = 0.35f)) // semi-transparent color overlay for readability
                                )
                            }
                        }
                    }
                }
            }

            // Offline Translation Model Manager Dialog
            if (isModelManagerOpen) {
                AlertDialog(
                    onDismissRequest = { isModelManagerOpen = false },
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudDownload,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text("Offline Models Manager", style = MaterialTheme.typography.titleMedium)
                        }
                    },
                    text = {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 400.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(translationService.supportedLanguages.size) { index ->
                                val lang = translationService.supportedLanguages[index]
                                val isDownloaded = downloadedModelsMap[lang.code] == true
                                val isDownloading = downloadingModelsMap[lang.code] == true
                                
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(lang.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                                        Text(
                                            text = if (isDownloaded) "Downloaded (Offline ready)" else "Not downloaded",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (isDownloaded) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (isDownloading) {
                                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                        } else {
                                            if (isDownloaded) {
                                                if (lang.code != "en") {
                                                    IconButton(onClick = { deleteModel(lang.code) }) {
                                                        Icon(
                                                            imageVector = Icons.Default.Delete,
                                                            contentDescription = "Delete model",
                                                            tint = MaterialTheme.colorScheme.error
                                                        )
                                                    }
                                                } else {
                                                    Icon(
                                                        imageVector = Icons.Default.Check,
                                                        contentDescription = "Default English",
                                                        tint = Color(0xFF10B981)
                                                    )
                                                }
                                            } else {
                                                IconButton(onClick = { downloadModel(lang.code) }) {
                                                    Icon(
                                                        imageVector = Icons.Default.CloudDownload,
                                                        contentDescription = "Download model",
                                                        tint = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { isModelManagerOpen = false }) {
                            Text("Close")
                        }
                    }
                )
            }

            // Customize Reader Bar Dialog
            if (isCustomizeReaderBarDialogOpen) {
                // Copy current states to temp list to allow cancelling changes
                var tempOrderList by remember { mutableStateOf(buttonOrderList.toList()) }
                val tempEnabledMap = remember { mutableStateMapOf<String, Boolean>().apply { 
                    buttonEnabledMap.forEach { (k, v) -> put(k, v) }
                }}
                var tempDoubleLine by remember { mutableStateOf(isDoubleLineLayout) }

                AlertDialog(
                    onDismissRequest = { isCustomizeReaderBarDialogOpen = false },
                    title = {
                        Text(
                            text = "Customize reader bar buttons", 
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    text = {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Double-line layout checkbox
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable { tempDoubleLine = !tempDoubleLine },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = tempDoubleLine,
                                    onCheckedChange = { tempDoubleLine = it }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Double-line layout", style = MaterialTheme.typography.bodyLarge)
                            }

                            // Preview Layouts
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // Single line preview box
                                Card(
                                    onClick = { tempDoubleLine = false },
                                    modifier = Modifier.weight(1f).height(65.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    border = if (!tempDoubleLine) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (!tempDoubleLine) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    )
                                ) {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Default.StayCurrentPortrait, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Icon(Icons.Default.Brightness4, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Icon(Icons.Default.UnfoldMore, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Icon(Icons.AutoMirrored.Filled.FormatListBulleted, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Icon(Icons.Default.MoreHoriz, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }

                                // Double line preview box
                                Card(
                                    onClick = { tempDoubleLine = true },
                                    modifier = Modifier.weight(1f).height(65.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    border = if (tempDoubleLine) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (tempDoubleLine) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    )
                                ) {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Icon(Icons.Default.StayCurrentPortrait, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                                Icon(Icons.Default.Brightness4, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                                Icon(Icons.Default.UnfoldMore, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Icon(Icons.AutoMirrored.Filled.FormatListBulleted, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                                Icon(Icons.Default.MoreHoriz, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                    }
                                }
                            }

                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                            // Scrollable list of actions
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                tempOrderList.forEachIndexed { index, id ->
                                    val name = defaultButtons.find { it.first == id }?.second ?: id
                                    val isEnabled = tempEnabledMap[id] == true
                                    
                                    val buttonIcon = when (id) {
                                        "orientation" -> Icons.Default.StayCurrentPortrait
                                        "daynight" -> Icons.Default.Brightness4
                                        "speak" -> Icons.AutoMirrored.Filled.VolumeUp
                                        "fontsize" -> Icons.Default.FormatSize
                                        "autoscroll" -> Icons.Default.UnfoldMore
                                        "chapters" -> Icons.AutoMirrored.Filled.FormatListBulleted
                                        "bookmarks" -> Icons.Default.BookmarkBorder
                                        "brightness" -> Icons.Default.WbSunny
                                        "search" -> Icons.Default.Search
                                        "tilt" -> Icons.Default.ScreenLockRotation
                                        "ruler" -> Icons.Default.HorizontalSplit
                                        "visual" -> Icons.Default.RemoveRedEye
                                        "control" -> Icons.Default.SettingsApplications
                                        "misc" -> Icons.Default.BlurOn
                                        "customize" -> Icons.Default.MoreHoriz
                                        else -> Icons.AutoMirrored.Filled.Help
                                    }

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            modifier = Modifier.weight(1f),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = buttonIcon,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(24.dp)
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Checkbox(
                                                checked = isEnabled,
                                                onCheckedChange = { tempEnabledMap[id] = it }
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = name,
                                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        
                                        // Reorder arrows
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            IconButton(
                                                onClick = {
                                                    if (index > 0) {
                                                        val newList = tempOrderList.toMutableList()
                                                        val temp = newList[index]
                                                        newList[index] = newList[index - 1]
                                                        newList[index - 1] = temp
                                                        tempOrderList = newList
                                                    }
                                                },
                                                enabled = index > 0,
                                                modifier = Modifier.size(30.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.KeyboardArrowUp,
                                                    contentDescription = "Move Up",
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                            IconButton(
                                                onClick = {
                                                    if (index < tempOrderList.size - 1) {
                                                        val newList = tempOrderList.toMutableList()
                                                        val temp = newList[index]
                                                        newList[index] = newList[index + 1]
                                                        newList[index + 1] = temp
                                                        tempOrderList = newList
                                                    }
                                                },
                                                enabled = index < tempOrderList.size - 1,
                                                modifier = Modifier.size(30.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.KeyboardArrowDown,
                                                    contentDescription = "Move Down",
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { isCustomizeReaderBarDialogOpen = false }) {
                            Text("CANCEL")
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            // Persist changes
                            buttonOrderList = tempOrderList
                            tempEnabledMap.forEach { (k, v) ->
                                buttonEnabledMap[k] = v
                                sharedPrefs.edit().putBoolean("reader_bar_enabled_$k", v).apply()
                            }
                            isDoubleLineLayout = tempDoubleLine
                            sharedPrefs.edit().putBoolean("reader_bar_double_line", tempDoubleLine).apply()
                            sharedPrefs.edit().putString("reader_bar_order", tempOrderList.joinToString(",")).apply()
                            
                            isCustomizeReaderBarDialogOpen = false
                        }) {
                            Text("OK")
                        }
                    }
                )
            }

            // Persistent Highlights Manager Dialog
            if (isHighlightManagerOpen) {
                var phraseInput by remember { mutableStateOf("") }
                val colorOptions = listOf(
                    Pair("#FFEB3B", "Yellow"),
                    Pair("#4CAF50", "Green"),
                    Pair("#00BCD4", "Cyan"),
                    Pair("#E91E63", "Pink")
                )
                var selectedColorHex by remember { mutableStateOf("#FFEB3B") }

                AlertDialog(
                    onDismissRequest = { isHighlightManagerOpen = false },
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.BorderColor,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text("Highlights & Markup", style = MaterialTheme.typography.titleMedium)
                        }
                    },
                    text = {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Text Input
                            OutlinedTextField(
                                value = phraseInput,
                                onValueChange = { phraseInput = it },
                                label = { Text("Phrase to highlight") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            // Color Selector Row
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("Highlight Color:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    colorOptions.forEach { pair ->
                                        val hex = pair.first
                                        val label = pair.second
                                        val color = Color(android.graphics.Color.parseColor(hex))
                                        val isSelected = selectedColorHex == hex
                                        
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(CircleShape)
                                                .background(color)
                                                .clickable { selectedColorHex = hex }
                                                .padding(2.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (isSelected) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = "Selected $label",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Add Button
                            Button(
                                onClick = {
                                    if (phraseInput.isNotBlank()) {
                                        scope.launch {
                                            database.highlightDao().insertHighlight(
                                                com.pdfsuny.app.data.entities.HighlightAnnotation(
                                                    pdfPath = pdfPath,
                                                    phrase = phraseInput.trim(),
                                                    colorHex = selectedColorHex,
                                                    timestamp = System.currentTimeMillis()
                                                )
                                            )
                                            phraseInput = ""
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = phraseInput.isNotBlank()
                            ) {
                                Text("Add Highlight")
                            }

                            HorizontalDivider()

                            // Active Highlights List
                            Text("Active Highlights:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                            if (highlights.isEmpty()) {
                                Text(
                                    "No persistent highlights added yet.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 180.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    items(highlights.size) { index ->
                                        val highlight = highlights[index]
                                        val color = try {
                                            Color(android.graphics.Color.parseColor(highlight.colorHex))
                                        } catch (e: Exception) {
                                            Color.Yellow
                                        }
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 2.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(16.dp)
                                                        .clip(CircleShape)
                                                        .background(color)
                                                )
                                                Text(
                                                    text = highlight.phrase,
                                                    style = MaterialTheme.typography.bodyLarge,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            IconButton(
                                                onClick = {
                                                    scope.launch {
                                                        database.highlightDao().deleteHighlight(highlight)
                                                    }
                                                },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = "Delete",
                                                    tint = MaterialTheme.colorScheme.error,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { isHighlightManagerOpen = false }) {
                            Text("Close")
                        }
                    }
                )
            }

            // Document Editor Dialog
            if (isEditorActive) {
                AlertDialog(
                    onDismissRequest = { isEditorActive = false },
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = if (isTextDocument) "Edit Document" else "Edit Page ${currentPageIndex + 1}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            val wordCount = remember(editorContent) {
                                editorContent.split("\\s+".toRegex()).filter { it.isNotBlank() }.size
                            }
                            Text(
                                text = "$wordCount words",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    text = {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 250.dp, max = 400.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = editorContent,
                                onValueChange = { editorContent = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                placeholder = { Text("Start typing...") },
                                textStyle = MaterialTheme.typography.bodyMedium,
                                shape = RoundedCornerShape(8.dp)
                            )
                        }
                    },
                    confirmButton = {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isTextDocument && !pdfPath.lowercase().endsWith(".epub")) {
                                Button(
                                    onClick = {
                                        scope.launch {
                                            try {
                                                withContext(Dispatchers.IO) {
                                                    val uri = Uri.parse(pdfPath)
                                                    if (uri.scheme == "file") {
                                                        File(uri.path ?: "").writeText(editorContent, Charsets.UTF_8)
                                                    } else {
                                                        context.contentResolver.openOutputStream(uri, "rwt")?.use { output ->
                                                            output.write(editorContent.toByteArray(Charsets.UTF_8))
                                                        }
                                                    }
                                                }
                                                textDocumentContent = editorContent
                                                Toast.makeText(context, "Changes saved successfully", Toast.LENGTH_SHORT).show()
                                                isEditorActive = false
                                            } catch (e: Exception) {
                                                Log.e("PdfViewerScreen", "Failed to save file", e)
                                                Toast.makeText(context, "Failed to save changes", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                ) {
                                    Text("Save")
                                }
                            } else {
                                Button(
                                    onClick = {
                                        scope.launch {
                                            try {
                                                val originalName = documentName.substringBeforeLast(".")
                                                val exportFile = File(
                                                    context.getExternalFilesDir(null),
                                                    "Edited_${originalName}_Page_${currentPageIndex + 1}.txt"
                                                )
                                                withContext(Dispatchers.IO) {
                                                    exportFile.writeText(editorContent, Charsets.UTF_8)
                                                }
                                                Toast.makeText(context, "Exported: ${exportFile.name}", Toast.LENGTH_LONG).show()
                                                isEditorActive = false
                                            } catch (e: Exception) {
                                                Log.e("PdfViewerScreen", "Failed to export TXT", e)
                                                Toast.makeText(context, "Failed to export text file", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                ) {
                                    Text("Export TXT")
                                }
                                
                                Button(
                                    onClick = {
                                        scope.launch {
                                            try {
                                                val originalName = documentName.substringBeforeLast(".")
                                                val exportFile = File(
                                                    context.getExternalFilesDir(null),
                                                    "Edited_${originalName}_Page_${currentPageIndex + 1}.pdf"
                                                )
                                                withContext(Dispatchers.IO) {
                                                    val pdfDocument = android.graphics.pdf.PdfDocument()
                                                    val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(595, 842, 1).create()
                                                    val page = pdfDocument.startPage(pageInfo)
                                                    val canvas = page.canvas
                                                    
                                                    val paint = android.graphics.Paint().apply {
                                                        color = android.graphics.Color.BLACK
                                                        textSize = 14f
                                                        isAntiAlias = true
                                                    }
                                                    
                                                    var y = 50f
                                                    val lines = editorContent.split("\n")
                                                    for (line in lines) {
                                                        canvas.drawText(line, 50f, y, paint)
                                                        y += paint.descent() - paint.ascent() + 4f
                                                        if (y > 800) {
                                                            // single page limit
                                                        }
                                                    }
                                                    pdfDocument.finishPage(page)
                                                    FileOutputStream(exportFile).use { out ->
                                                        pdfDocument.writeTo(out)
                                                    }
                                                    pdfDocument.close()
                                                }
                                                Toast.makeText(context, "Exported: ${exportFile.name}", Toast.LENGTH_LONG).show()
                                                isEditorActive = false
                                            } catch (e: Exception) {
                                                Log.e("PdfViewerScreen", "Failed to export PDF", e)
                                                Toast.makeText(context, "Failed to export PDF", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                ) {
                                    Text("Export PDF")
                                }
                            }
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { isEditorActive = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }

            // PDF Direct Text Word Editor Dialog
            if (isPdfWordEditorOpen) {
                AlertDialog(
                    onDismissRequest = { 
                        if (!isPdfEditorSaving) {
                            isPdfWordEditorOpen = false
                            wordToFind = ""
                            replacementText = ""
                        }
                    },
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text("Edit PDF Text (Page ${currentPageIndex + 1})", style = MaterialTheme.typography.titleMedium)
                        }
                    },
                    text = {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                text = "Search for a word/phrase on this page and replace it. Old text will be whited-out and replaced with new text.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            
                            OutlinedTextField(
                                value = wordToFind,
                                onValueChange = { wordToFind = it },
                                label = { Text("Word/Phrase to find") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            
                            OutlinedTextField(
                                value = replacementText,
                                onValueChange = { replacementText = it },
                                label = { Text("Replacement text") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    },
                    confirmButton = {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isPdfEditorSaving) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            } else {
                                Button(
                                    onClick = {
                                        if (wordToFind.isNotBlank()) {
                                            isPdfEditorSaving = true
                                            scope.launch {
                                                val success = pdfTextService.editTextOnPage(
                                                    context = context,
                                                    uri = Uri.parse(pdfPath),
                                                    pageIndex = currentPageIndex,
                                                    targetWord = wordToFind,
                                                    replacementText = replacementText
                                                )
                                                isPdfEditorSaving = false
                                                if (success) {
                                                    Toast.makeText(context, "Text replaced successfully!", Toast.LENGTH_SHORT).show()
                                                    bitmapCache.evictAll()
                                                    pdfReloadTrigger++
                                                    isPdfWordEditorOpen = false
                                                    wordToFind = ""
                                                    replacementText = ""
                                                } else {
                                                    Toast.makeText(context, "Phrase not found on this page.", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        }
                                    },
                                    enabled = wordToFind.isNotBlank()
                                ) {
                                    Text("Replace")
                                }
                            }
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = {
                                isPdfWordEditorOpen = false
                                wordToFind = ""
                                replacementText = ""
                            },
                            enabled = !isPdfEditorSaving
                        ) {
                            Text("Cancel")
                        }
                    }
                )
            }

            // PDF Direct Signature Dialog
            if (showSignatureDialog) {
                var isSigSaving by remember { mutableStateOf(false) }
                AlertDialog(
                    onDismissRequest = {
                        if (!isSigSaving) {
                            showSignatureDialog = false
                        }
                    },
                    title = {
                        Text("Add Manual Signature", fontWeight = FontWeight.Bold)
                    },
                    text = {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                        ) {
                            Text(
                                text = "Draw your signature on the pad below:",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(150.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.White)
                                    .border(1.dp, Color.LightGray, RoundedCornerShape(8.dp))
                            ) {
                                Canvas(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .pointerInput(Unit) {
                                            detectDragGestures(
                                                onDragStart = { offset ->
                                                    signaturePaths.add(listOf(offset))
                                                },
                                                onDrag = { change, _ ->
                                                    change.consume()
                                                    if (signaturePaths.isNotEmpty()) {
                                                        val lastPath = signaturePaths.last()
                                                        signaturePaths[signaturePaths.size - 1] = lastPath + change.position
                                                    }
                                                }
                                            )
                                        }
                                ) {
                                    signaturePaths.forEach { path ->
                                        if (path.size > 1) {
                                            val drawPath = Path()
                                            drawPath.moveTo(path[0].x, path[0].y)
                                            for (i in 1 until path.size) {
                                                drawPath.lineTo(path[i].x, path[i].y)
                                            }
                                            drawPath(
                                                path = drawPath,
                                                color = Color(0xFF000000.toInt() or (sigInkR shl 16) or (sigInkG shl 8) or sigInkB),
                                                style = Stroke(
                                                    width = 4.dp.toPx(),
                                                    cap = StrokeCap.Round,
                                                    join = StrokeJoin.Round
                                                )
                                            )
                                        }
                                    }
                                }
                                
                                TextButton(
                                    onClick = { signaturePaths.clear() },
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(4.dp)
                                ) {
                                    Text("Clear", color = MaterialTheme.colorScheme.error)
                                }
                            }
                            
                            Text("Ink Color (Manual Picker):", style = MaterialTheme.typography.titleSmall)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                val sigColorPreview = Color(0xFF000000.toInt() or (sigInkR shl 16) or (sigInkG shl 8) or sigInkB)
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(sigColorPreview)
                                        .border(1.5.dp, Color.White, CircleShape)
                                )
                                
                                OutlinedTextField(
                                    value = sigInkHexText,
                                    onValueChange = { input ->
                                        sigInkHexText = input
                                        val cleaned = input.removePrefix("#").trim()
                                        if (cleaned.length == 6) {
                                            try {
                                                val parsed = cleaned.toLong(16).toInt()
                                                sigInkR = (parsed ushr 16) and 0xFF
                                                sigInkG = (parsed ushr 8) and 0xFF
                                                sigInkB = parsed and 0xFF
                                            } catch (e: Exception) {}
                                        }
                                    },
                                    label = { Text("Hex Color") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f),
                                    textStyle = TextStyle(fontSize = 12.sp)
                                )
                            }
                            
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("R: $sigInkR", color = Color.Red, modifier = Modifier.width(42.dp), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                    Slider(
                                        value = sigInkR.toFloat(),
                                        onValueChange = {
                                            sigInkR = it.toInt()
                                            sigInkHexText = String.format("#%02X%02X%02X", sigInkR, sigInkG, sigInkB)
                                        },
                                        valueRange = 0f..255f,
                                        modifier = Modifier.weight(1f),
                                        colors = SliderDefaults.colors(activeTrackColor = Color.Red)
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("G: $sigInkG", color = Color(0xFF2E7D32), modifier = Modifier.width(42.dp), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                    Slider(
                                        value = sigInkG.toFloat(),
                                        onValueChange = {
                                            sigInkG = it.toInt()
                                            sigInkHexText = String.format("#%02X%02X%02X", sigInkR, sigInkG, sigInkB)
                                        },
                                        valueRange = 0f..255f,
                                        modifier = Modifier.weight(1f),
                                        colors = SliderDefaults.colors(activeTrackColor = Color(0xFF2E7D32))
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("B: $sigInkB", color = Color.Blue, modifier = Modifier.width(42.dp), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                    Slider(
                                        value = sigInkB.toFloat(),
                                        onValueChange = {
                                            sigInkB = it.toInt()
                                            sigInkHexText = String.format("#%02X%02X%02X", sigInkR, sigInkG, sigInkB)
                                        },
                                        valueRange = 0f..255f,
                                        modifier = Modifier.weight(1f),
                                        colors = SliderDefaults.colors(activeTrackColor = Color.Blue)
                                    )
                                }
                            }
                            
                            Text("Position Alignment:", style = MaterialTheme.typography.titleSmall)
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                val alignmentOptions = listOf(
                                    "bottom_left" to "Left",
                                    "bottom_center" to "Center",
                                    "bottom_right" to "Right"
                                )
                                alignmentOptions.forEach { (alignId, label) ->
                                    val isSelected = signatureAlignment == alignId
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                            .clickable { signatureAlignment = alignId }
                                            .padding(vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = label,
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        )
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isSigSaving) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            } else {
                                Button(
                                    onClick = {
                                        if (signaturePaths.isEmpty()) {
                                            Toast.makeText(context, "Please sign on the pad first", Toast.LENGTH_SHORT).show()
                                        } else {
                                            isSigSaving = true
                                            scope.launch {
                                                val pointPaths = signaturePaths.map { path ->
                                                    path.map { PointF(it.x, it.y) }
                                                }
                                                val argbColor = (0xFF shl 24) or (sigInkR shl 16) or (sigInkG shl 8) or sigInkB
                                                val success = pdfTextService.addSignatureOnPage(
                                                    context = context,
                                                    uri = Uri.parse(pdfPath),
                                                    pageIndex = currentPageIndex,
                                                    signaturePaths = pointPaths,
                                                    alignment = signatureAlignment,
                                                    inkColor = argbColor
                                                )
                                                isSigSaving = false
                                                if (success) {
                                                    Toast.makeText(context, "Signature applied successfully!", Toast.LENGTH_SHORT).show()
                                                    bitmapCache.evictAll()
                                                    pdfReloadTrigger++
                                                    showSignatureDialog = false
                                                } else {
                                                    Toast.makeText(context, "Failed to apply signature.", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        }
                                    }
                                ) {
                                    Text("Apply")
                                }
                            }
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = {
                                showSignatureDialog = false
                            },
                            enabled = !isSigSaving
                        ) {
                            Text("Cancel")
                        }
                    }
                )
            }

            // PDF Direct Stamp Dialog
            if (showStampDialog) {
                var isStampSaving by remember { mutableStateOf(false) }
                AlertDialog(
                    onDismissRequest = {
                        if (!isStampSaving) {
                            showStampDialog = false
                        }
                    },
                    title = {
                        Text("Add Manual Stamp", fontWeight = FontWeight.Bold)
                    },
                    text = {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                        ) {
                            TabRow(
                                selectedTabIndex = stampTypeTab,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Tab(
                                    selected = stampTypeTab == 0,
                                    onClick = { stampTypeTab = 0 },
                                    text = { Text("Text Stamp", style = MaterialTheme.typography.bodyMedium) }
                                )
                                Tab(
                                    selected = stampTypeTab == 1,
                                    onClick = { stampTypeTab = 1 },
                                    text = { Text("Image Stamp", style = MaterialTheme.typography.bodyMedium) }
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(4.dp))
                            
                            if (stampTypeTab == 0) {
                                Text("Stamp Text:", style = MaterialTheme.typography.titleSmall)
                                OutlinedTextField(
                                    value = stampText,
                                    onValueChange = { stampText = it },
                                    placeholder = { Text("e.g., APPROVED") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    textStyle = TextStyle(fontWeight = FontWeight.Bold)
                                )
                                
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    val presets = listOf("APPROVED", "REJECTED", "DRAFT", "CONFIDENTIAL")
                                    presets.forEach { preset ->
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                                .clickable { stampText = preset }
                                                .padding(horizontal = 8.dp, vertical = 6.dp)
                                        ) {
                                            Text(
                                                text = preset,
                                                style = MaterialTheme.typography.bodySmall,
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                                
                                Text("Stamp Color (Manual Picker):", style = MaterialTheme.typography.titleSmall)
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    val stampColorPreview = Color(0xFF000000.toInt() or (stampR shl 16) or (stampG shl 8) or stampB)
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(stampColorPreview)
                                            .border(1.5.dp, Color.White, CircleShape)
                                    )
                                    
                                    OutlinedTextField(
                                        value = stampHexText,
                                        onValueChange = { input ->
                                            stampHexText = input
                                            val cleaned = input.removePrefix("#").trim()
                                            if (cleaned.length == 6) {
                                                try {
                                                    val parsed = cleaned.toLong(16).toInt()
                                                    stampR = (parsed ushr 16) and 0xFF
                                                    stampG = (parsed ushr 8) and 0xFF
                                                    stampB = parsed and 0xFF
                                                } catch (e: Exception) {}
                                            }
                                        },
                                        label = { Text("Hex Color") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f),
                                        textStyle = TextStyle(fontSize = 12.sp)
                                    )
                                }
                                
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("R: $stampR", color = Color.Red, modifier = Modifier.width(42.dp), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                        Slider(
                                            value = stampR.toFloat(),
                                            onValueChange = {
                                                stampR = it.toInt()
                                                stampHexText = String.format("#%02X%02X%02X", stampR, stampG, stampB)
                                            },
                                            valueRange = 0f..255f,
                                            modifier = Modifier.weight(1f),
                                            colors = SliderDefaults.colors(activeTrackColor = Color.Red)
                                        )
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("G: $stampG", color = Color(0xFF2E7D32), modifier = Modifier.width(42.dp), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                        Slider(
                                            value = stampG.toFloat(),
                                            onValueChange = {
                                                stampG = it.toInt()
                                                stampHexText = String.format("#%02X%02X%02X", stampR, stampG, stampB)
                                            },
                                            valueRange = 0f..255f,
                                            modifier = Modifier.weight(1f),
                                            colors = SliderDefaults.colors(activeTrackColor = Color(0xFF2E7D32))
                                        )
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("B: $stampB", color = Color.Blue, modifier = Modifier.width(42.dp), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                        Slider(
                                            value = stampB.toFloat(),
                                            onValueChange = {
                                                stampB = it.toInt()
                                                stampHexText = String.format("#%02X%02X%02X", stampR, stampG, stampB)
                                            },
                                            valueRange = 0f..255f,
                                            modifier = Modifier.weight(1f),
                                            colors = SliderDefaults.colors(activeTrackColor = Color.Blue)
                                        )
                                    }
                                }
                            } else {
                                Text("Import Stamp Graphic:", style = MaterialTheme.typography.titleSmall)
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(120.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .border(1.dp, Color.LightGray, RoundedCornerShape(8.dp))
                                        .clickable { stampImagePicker.launch("image/*") },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (stampImageUri != null) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = Color(0xFF2E7D32))
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text("Image Selected", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                            Text("Tap to change", style = MaterialTheme.typography.bodySmall, fontSize = 10.sp)
                                        }
                                    } else {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Icon(imageVector = Icons.Default.Image, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text("Select from Gallery", style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                }
                            }
                            
                            Text("Position Alignment:", style = MaterialTheme.typography.titleSmall)
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                val alignmentOptions = listOf(
                                    "top_left" to "Top L",
                                    "top_right" to "Top R",
                                    "center" to "Center",
                                    "bottom_left" to "Bot L",
                                    "bottom_right" to "Bot R"
                                )
                                alignmentOptions.forEach { (alignId, label) ->
                                    val isSelected = stampAlignment == alignId
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                            .clickable { stampAlignment = alignId }
                                            .padding(vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = label,
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontSize = 11.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        )
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isStampSaving) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            } else {
                                Button(
                                    onClick = {
                                        if (stampTypeTab == 0 && stampText.isBlank()) {
                                            Toast.makeText(context, "Stamp text cannot be empty", Toast.LENGTH_SHORT).show()
                                        } else if (stampTypeTab == 1 && stampImageUri == null) {
                                            Toast.makeText(context, "Please select an image stamp first", Toast.LENGTH_SHORT).show()
                                        } else {
                                            isStampSaving = true
                                            scope.launch {
                                                val argbColor = (0xFF shl 24) or (stampR shl 16) or (stampG shl 8) or stampB
                                                val success = pdfTextService.addStampOnPage(
                                                    context = context,
                                                    uri = Uri.parse(pdfPath),
                                                    pageIndex = currentPageIndex,
                                                    stampType = stampTypeTab,
                                                    stampText = stampText,
                                                    stampColor = argbColor,
                                                    importedImageUri = stampImageUri,
                                                    alignment = stampAlignment
                                                )
                                                isStampSaving = false
                                                if (success) {
                                                    Toast.makeText(context, "Stamp applied successfully!", Toast.LENGTH_SHORT).show()
                                                    bitmapCache.evictAll()
                                                    pdfReloadTrigger++
                                                    showStampDialog = false
                                                } else {
                                                    Toast.makeText(context, "Failed to apply stamp.", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        }
                                    }
                                ) {
                                    Text("Apply")
                                }
                            }
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = {
                                showStampDialog = false
                            },
                            enabled = !isStampSaving
                        ) {
                            Text("Cancel")
                        }
                    }
                )
            }
        }
    }

    // --- Custom Brightness & Eye Care Overlays ---
    if (isBlueLightFilterEnabled) {
        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            drawRect(
                color = Color(0xFFFFA726).copy(alpha = blueLightOpacity)
            )
        }
    }

    if (isBrightnessMenuOpen) {
        // Dismiss scrim overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { isBrightnessMenuOpen = false }
        )

        // Brightness control panel card floating above the bottom bar
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 90.dp, start = 16.dp, end = 16.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = false) {}, // Prevent dismiss when tapping card content
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF3E4756)), // Slate grey
                elevation = CardDefaults.cardElevation(8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Brightness:",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = isAutoBrightness,
                            onCheckedChange = {
                                isAutoBrightness = it
                                sharedPrefs.edit().putBoolean("is_auto_brightness", it).apply()
                            },
                            colors = CheckboxDefaults.colors(
                                checkedColor = Color(0xFF03A9F4),
                                uncheckedColor = Color.LightGray,
                                checkmarkColor = Color.White
                            )
                        )
                        Text(
                            text = "Auto",
                            color = Color.White,
                            modifier = Modifier.padding(end = 8.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )

                        Slider(
                            value = brightnessLevel,
                            onValueChange = {
                                if (!isAutoBrightness) {
                                    brightnessLevel = it
                                    sharedPrefs.edit().putFloat("brightness_level", it).apply()
                                }
                            },
                            valueRange = 0.05f..1.0f,
                            enabled = !isAutoBrightness,
                            modifier = Modifier.weight(1f),
                            colors = SliderDefaults.colors(
                                thumbColor = Color.White,
                                activeTrackColor = Color(0xFF03A9F4),
                                inactiveTrackColor = Color.Gray,
                                disabledThumbColor = Color.Gray,
                                disabledActiveTrackColor = Color.DarkGray
                            )
                        )

                        IconButton(
                            onClick = {
                                if (!isAutoBrightness) {
                                    brightnessLevel = (brightnessLevel - 0.05f).coerceAtLeast(0.05f)
                                    sharedPrefs.edit().putFloat("brightness_level", brightnessLevel).apply()
                                }
                            },
                            enabled = !isAutoBrightness
                        ) {
                            Text("-", color = if (isAutoBrightness) Color.Gray else Color.White, style = MaterialTheme.typography.titleLarge)
                        }

                        IconButton(
                            onClick = {
                                if (!isAutoBrightness) {
                                    brightnessLevel = (brightnessLevel + 0.05f).coerceAtMost(1.0f)
                                    sharedPrefs.edit().putFloat("brightness_level", brightnessLevel).apply()
                                }
                            },
                            enabled = !isAutoBrightness
                        ) {
                            Text("+", color = if (isAutoBrightness) Color.Gray else Color.White, style = MaterialTheme.typography.titleLarge)
                        }

                        IconButton(
                            onClick = {
                                try {
                                    val intent = Intent(android.provider.Settings.ACTION_DISPLAY_SETTINGS).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Cannot open display settings", Toast.LENGTH_SHORT).show()
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "System Brightness Settings",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = isBlueLightFilterEnabled,
                            onCheckedChange = {
                                isBlueLightFilterEnabled = it
                                sharedPrefs.edit().putBoolean("is_bluelight_filter_enabled", it).apply()
                            },
                            colors = CheckboxDefaults.colors(
                                checkedColor = Color(0xFF03A9F4),
                                uncheckedColor = Color.LightGray,
                                checkmarkColor = Color.White
                            )
                        )
                        Text(
                            text = "Bluelight filter for eye care",
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Opacity",
                            color = Color.White,
                            modifier = Modifier.width(65.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )

                        Slider(
                            value = blueLightOpacity,
                            onValueChange = {
                                if (isBlueLightFilterEnabled) {
                                    blueLightOpacity = it
                                    sharedPrefs.edit().putFloat("bluelight_opacity", it).apply()
                                }
                            },
                            valueRange = 0.0f..0.8f,
                            enabled = isBlueLightFilterEnabled,
                            modifier = Modifier.weight(1f),
                            colors = SliderDefaults.colors(
                                thumbColor = Color.White,
                                activeTrackColor = Color(0xFF03A9F4),
                                inactiveTrackColor = Color.Gray,
                                disabledThumbColor = Color.Gray,
                                disabledActiveTrackColor = Color.DarkGray
                            )
                        )

                        IconButton(
                            onClick = {
                                if (isBlueLightFilterEnabled) {
                                    blueLightOpacity = (blueLightOpacity - 0.05f).coerceAtLeast(0.0f)
                                    sharedPrefs.edit().putFloat("bluelight_opacity", blueLightOpacity).apply()
                                }
                            },
                            enabled = isBlueLightFilterEnabled
                        ) {
                            Text("-", color = if (!isBlueLightFilterEnabled) Color.Gray else Color.White, style = MaterialTheme.typography.titleLarge)
                        }

                        IconButton(
                            onClick = {
                                if (isBlueLightFilterEnabled) {
                                    blueLightOpacity = (blueLightOpacity + 0.05f).coerceAtMost(0.8f)
                                    sharedPrefs.edit().putFloat("bluelight_opacity", blueLightOpacity).apply()
                                }
                            },
                            enabled = isBlueLightFilterEnabled
                        ) {
                            Text("+", color = if (!isBlueLightFilterEnabled) Color.Gray else Color.White, style = MaterialTheme.typography.titleLarge)
                        }
                    }
                }
            }
        }
    }

    if (isRulerSettingsOpen) {
        // Dismiss scrim overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { isRulerSettingsOpen = false }
        )

        // Ruler settings control panel card floating above the bottom bar
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 90.dp, start = 16.dp, end = 16.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = false) {}, // Prevent dismiss when tapping card content
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(8.dp)
            ) {
                var hexInputText by remember(rulerColorStripe) {
                    mutableStateOf(String.format("#%08X", rulerColorStripe))
                }
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Ruler Settings",
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        
                        // ON/OFF Switch
                        Switch(
                            checked = isReadingRulerEnabled,
                            onCheckedChange = {
                                isReadingRulerEnabled = it
                                sharedPrefs.edit().putBoolean("is_reading_ruler_enabled", it).apply()
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFF03A9F4),
                                uncheckedThumbColor = Color.Gray,
                                uncheckedTrackColor = Color.DarkGray
                            )
                        )
                    }

                    // Live Ruler Preview Box
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (MaterialTheme.colorScheme.surface == Color.White) Color(0xFFF1F5F9) else Color(0xFF2C323E)) // adapt preview background based on theme mode
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(36.dp) // mini fixed height for preview
                                .clip(RoundedCornerShape(4.dp))
                                .graphicsLayer(compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen),
                            contentAlignment = Alignment.Center
                        ) {
                            val previewHeight = 36f
                            val previewStripeHeight = (rulerFocusStripeHeight * (36f / rulerHeight.coerceAtLeast(1f))).coerceAtMost(24f)

                            if (rulerTheme == "solid") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color(0xFF3E4756).copy(alpha = rulerOpacityOuter))
                                 )
                                if (isRulerStripeColorEnabled) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(previewStripeHeight.dp)
                                            .background(Color(rulerColorStripe))
                                    )
                                }
                            } else {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    val widthPx = size.width
                                    val heightPx = size.height
                                    val stripeHeightPx = previewStripeHeight.dp.toPx()

                                    if (rulerTheme == "classic") {
                                        drawRoundRect(
                                            color = Color(0xFFDFD0C0).copy(alpha = rulerOpacityOuter),
                                            size = size,
                                            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                                        )
                                        val tickColor = Color(0xFF5D4037).copy(alpha = rulerOpacityOuter)
                                        val numTicks = 15
                                        val spacing = widthPx / numTicks
                                        for (i in 0..numTicks) {
                                            val x = i * spacing
                                            val tickLen = if (i % 5 == 0) 5.dp.toPx() else 3.dp.toPx()
                                            drawLine(
                                                color = tickColor,
                                                start = Offset(x, 0f),
                                                end = Offset(x, tickLen),
                                                strokeWidth = 1.dp.toPx()
                                            )
                                            drawLine(
                                                color = tickColor,
                                                start = Offset(x, heightPx),
                                                end = Offset(x, heightPx - tickLen),
                                                strokeWidth = 1.dp.toPx()
                                            )
                                        }
                                    } else {
                                        // Retro preview
                                        drawRoundRect(
                                            color = Color(0xFF3F3D56).copy(alpha = rulerOpacityOuter),
                                            size = size,
                                            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                                        )
                                        val stripeColor = Color(0xFF00ADB5).copy(alpha = rulerOpacityOuter)
                                        for (i in 0..3) {
                                            drawLine(
                                                color = stripeColor,
                                                start = Offset(10.dp.toPx() + i * 6.dp.toPx(), 2.dp.toPx()),
                                                end = Offset(2.dp.toPx() + i * 6.dp.toPx(), heightPx - 2.dp.toPx()),
                                                strokeWidth = 2.dp.toPx()
                                            )
                                        }
                                        val dotColor = Color(0xFFFFD200).copy(alpha = rulerOpacityOuter)
                                        for (row in 0..1) {
                                            for (col in 0..2) {
                                                drawCircle(
                                                    color = dotColor,
                                                    radius = 1.5f.dp.toPx(),
                                                    center = Offset(widthPx - 25.dp.toPx() + col * 5.dp.toPx(), 6.dp.toPx() + row * 5.dp.toPx())
                                                )
                                            }
                                        }
                                    }

                                    // Cutout window in center
                                    drawRoundRect(
                                        color = Color.Transparent,
                                        topLeft = Offset(8.dp.toPx(), (heightPx - stripeHeightPx) / 2),
                                        size = Size(widthPx - 16.dp.toPx(), stripeHeightPx),
                                        cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx()),
                                        blendMode = androidx.compose.ui.graphics.BlendMode.Clear
                                    )
                                }
                                if (isRulerStripeColorEnabled) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 20.dp)
                                            .height(previewStripeHeight.dp)
                                            .background(Color(rulerColorStripe).copy(alpha = 0.35f))
                                    )
                                }
                            }
                        }
                    }

                    // 1. Ruler Height
                    Column {
                        Text(
                            text = "Ruler Height: ${rulerHeight.toInt()} dp",
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Slider(
                                value = rulerHeight,
                                onValueChange = {
                                    rulerHeight = it
                                    sharedPrefs.edit().putFloat("reading_ruler_height", it).apply()
                                    // Ensure stripe height doesn't exceed ruler height
                                    if (rulerFocusStripeHeight > rulerHeight - 6f) {
                                        rulerFocusStripeHeight = (rulerHeight - 6f).coerceAtLeast(2f)
                                        sharedPrefs.edit().putFloat("reading_ruler_stripe_height", rulerFocusStripeHeight).apply()
                                    }
                                },
                                valueRange = 20f..150f,
                                modifier = Modifier.weight(1f),
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.primary,
                                    activeTrackColor = MaterialTheme.colorScheme.primary,
                                    inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.24f)
                                )
                            )
                            IconButton(onClick = {
                                rulerHeight = (rulerHeight - 5f).coerceAtLeast(20f)
                                sharedPrefs.edit().putFloat("reading_ruler_height", rulerHeight).apply()
                                if (rulerFocusStripeHeight > rulerHeight - 6f) {
                                    rulerFocusStripeHeight = (rulerHeight - 6f).coerceAtLeast(2f)
                                    sharedPrefs.edit().putFloat("reading_ruler_stripe_height", rulerFocusStripeHeight).apply()
                                }
                            }) {
                                Text("-", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleLarge)
                            }
                            IconButton(onClick = {
                                rulerHeight = (rulerHeight + 5f).coerceAtMost(150f)
                                sharedPrefs.edit().putFloat("reading_ruler_height", rulerHeight).apply()
                            }) {
                                Text("+", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleLarge)
                            }
                        }
                    }

                    // 2. Focus Stripe Height
                    Column {
                        Text(
                            text = "Stripe Height: ${rulerFocusStripeHeight.toInt()} dp",
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Slider(
                                value = rulerFocusStripeHeight,
                                onValueChange = {
                                    rulerFocusStripeHeight = it.coerceAtMost(rulerHeight - 6f)
                                    sharedPrefs.edit().putFloat("reading_ruler_stripe_height", rulerFocusStripeHeight).apply()
                                },
                                valueRange = 2f..50f,
                                modifier = Modifier.weight(1f),
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.primary,
                                    activeTrackColor = MaterialTheme.colorScheme.primary,
                                    inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.24f)
                                )
                            )
                            IconButton(onClick = {
                                rulerFocusStripeHeight = (rulerFocusStripeHeight - 2f).coerceAtLeast(2f)
                                sharedPrefs.edit().putFloat("reading_ruler_stripe_height", rulerFocusStripeHeight).apply()
                            }) {
                                Text("-", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleLarge)
                            }
                            IconButton(onClick = {
                                rulerFocusStripeHeight = (rulerFocusStripeHeight + 2f).coerceAtMost(rulerHeight - 6f)
                                sharedPrefs.edit().putFloat("reading_ruler_stripe_height", rulerFocusStripeHeight).apply()
                            }) {
                                Text("+", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleLarge)
                            }
                        }
                    }

                    // 3. Ruler Opacity
                    Column {
                        Text(
                            text = "Ruler Opacity: ${(rulerOpacityOuter * 100).toInt()}%",
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Slider(
                                value = rulerOpacityOuter,
                                onValueChange = {
                                    rulerOpacityOuter = it
                                    sharedPrefs.edit().putFloat("reading_ruler_opacity_outer", it).apply()
                                },
                                valueRange = 0.1f..0.9f,
                                modifier = Modifier.weight(1f),
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.primary,
                                    activeTrackColor = MaterialTheme.colorScheme.primary,
                                    inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.24f)
                                )
                            )
                            IconButton(onClick = {
                                rulerOpacityOuter = (rulerOpacityOuter - 0.05f).coerceAtLeast(0.1f)
                                sharedPrefs.edit().putFloat("reading_ruler_opacity_outer", rulerOpacityOuter).apply()
                            }) {
                                Text("-", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleLarge)
                            }
                            IconButton(onClick = {
                                rulerOpacityOuter = (rulerOpacityOuter + 0.05f).coerceAtMost(0.9f)
                                sharedPrefs.edit().putFloat("reading_ruler_opacity_outer", rulerOpacityOuter).apply()
                            }) {
                                Text("+", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleLarge)
                            }
                        }
                    }

                    // 4. Focus Stripe Color (Manual RGB Color Picker)
                    Column {
                        val r = (rulerColorStripe shr 16) and 0xFF
                        val g = (rulerColorStripe shr 8) and 0xFF
                        val b = rulerColorStripe and 0xFF
                        val alpha = (rulerColorStripe shr 24) and 0xFF

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Stripe Color (RGB):",
                                color = MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Stripe Color ON/OFF Switch
                                Switch(
                                    checked = isRulerStripeColorEnabled,
                                    onCheckedChange = {
                                        isRulerStripeColorEnabled = it
                                        sharedPrefs.edit().putBoolean("reading_ruler_stripe_color_enabled", it).apply()
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = Color(0xFF03A9F4),
                                        uncheckedThumbColor = Color.Gray,
                                        uncheckedTrackColor = Color.DarkGray
                                    ),
                                    modifier = Modifier.scale(0.8f)
                                )
                                // Live Preview Circle
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(if (isRulerStripeColorEnabled) Color(rulerColorStripe) else Color.Transparent)
                                        .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))

                        // Hex Color Code input field
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Color Code (Hex):",
                                color = if (isRulerStripeColorEnabled) MaterialTheme.colorScheme.onSurface else Color.Gray,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold
                            )
                            OutlinedTextField(
                                value = hexInputText,
                                onValueChange = { input ->
                                    hexInputText = input
                                    val cleaned = input.removePrefix("#").trim()
                                    if (cleaned.length == 6 || cleaned.length == 8) {
                                        try {
                                            val parsedColor = if (cleaned.length == 6) {
                                                (0xE6 shl 24) or cleaned.toLong(16).toInt()
                                            } else {
                                                cleaned.toLong(16).toInt()
                                            }
                                            rulerColorStripe = parsedColor
                                            sharedPrefs.edit().putInt("reading_ruler_stripe_color", parsedColor).apply()
                                        } catch (e: Exception) {
                                            // Ignore parsing errors on intermediate inputs
                                        }
                                    }
                                },
                                enabled = isRulerStripeColorEnabled,
                                modifier = Modifier
                                    .width(135.dp)
                                    .height(48.dp),
                                textStyle = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurface),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                    disabledBorderColor = Color.DarkGray,
                                    disabledTextColor = Color.Gray,
                                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                                )
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))

                        // Red Slider
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val rColor = if (isRulerStripeColorEnabled) Color(0xFFFF8A80) else Color.Gray
                            Text("R: $r", color = rColor, modifier = Modifier.width(42.dp), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                            Slider(
                                value = r.toFloat(),
                                onValueChange = {
                                    val newColor = (alpha shl 24) or (it.toInt() shl 16) or (g shl 8) or b
                                    rulerColorStripe = newColor
                                    sharedPrefs.edit().putInt("reading_ruler_stripe_color", newColor).apply()
                                },
                                valueRange = 0f..255f,
                                enabled = isRulerStripeColorEnabled,
                                modifier = Modifier.weight(1f),
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.primary,
                                    activeTrackColor = Color(0xFFFF5252),
                                    inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.24f),
                                    disabledThumbColor = Color.Gray,
                                    disabledActiveTrackColor = Color.DarkGray,
                                    disabledInactiveTrackColor = Color.DarkGray
                                )
                            )
                        }

                        // Green Slider
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val gColor = if (isRulerStripeColorEnabled) Color(0xFFB9F6CA) else Color.Gray
                            Text("G: $g", color = gColor, modifier = Modifier.width(42.dp), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                            Slider(
                                value = g.toFloat(),
                                onValueChange = {
                                    val newColor = (alpha shl 24) or (r shl 16) or (it.toInt() shl 8) or b
                                    rulerColorStripe = newColor
                                    sharedPrefs.edit().putInt("reading_ruler_stripe_color", newColor).apply()
                                },
                                valueRange = 0f..255f,
                                enabled = isRulerStripeColorEnabled,
                                modifier = Modifier.weight(1f),
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.primary,
                                    activeTrackColor = Color(0xFF69F0AE),
                                    inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.24f),
                                    disabledThumbColor = Color.Gray,
                                    disabledActiveTrackColor = Color.DarkGray,
                                    disabledInactiveTrackColor = Color.DarkGray
                                )
                            )
                        }

                        // Blue Slider
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val bColor = if (isRulerStripeColorEnabled) Color(0xFF82B1FF) else Color.Gray
                            Text("B: $b", color = bColor, modifier = Modifier.width(42.dp), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                            Slider(
                                value = b.toFloat(),
                                onValueChange = {
                                    val newColor = (alpha shl 24) or (r shl 16) or (g shl 8) or it.toInt()
                                    rulerColorStripe = newColor
                                    sharedPrefs.edit().putInt("reading_ruler_stripe_color", newColor).apply()
                                },
                                valueRange = 0f..255f,
                                enabled = isRulerStripeColorEnabled,
                                modifier = Modifier.weight(1f),
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.primary,
                                    activeTrackColor = Color(0xFF448AFF),
                                    inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.24f),
                                    disabledThumbColor = Color.Gray,
                                    disabledActiveTrackColor = Color.DarkGray,
                                    disabledInactiveTrackColor = Color.DarkGray
                                )
                            )
                        }
                    }

                    // 5. Ruler Theme Selection
                    Column {
                        Text(
                            text = "Ruler Theme:",
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            val themesList = listOf(
                                "solid" to "Solid",
                                "classic" to "Classic",
                                "retro" to "Retro"
                            )
                            themesList.forEach { (themeId, label) ->
                                val isSelected = rulerTheme == themeId
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                        .clickable {
                                            rulerTheme = themeId
                                            sharedPrefs.edit().putString("reading_ruler_theme", themeId).apply()
                                        }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    }
}

/**
 * Custom detectTransformGestures that conditionally consumes gestures.
 * Specifically, it does NOT consume touch/drag events if:
 * 1. The image is not zoomed in (isZoomed() is false).
 * 2. It is a single finger touch/drag gesture.
 * This allows single finger scrolling of parent LazyColumn while retaining pinch-to-zoom capability.
 */
suspend fun PointerInputScope.detectZoomableTransformGestures(
    isZoomed: () -> Boolean,
    onGesture: (centroid: Offset, pan: Offset, zoom: Float, rotation: Float) -> Unit
) {
    awaitEachGesture {
        var rotation = 0f
        var zoom = 1f
        var pan = Offset.Zero
        var pastTouchSlop = isZoomed()
        val touchSlop = viewConfiguration.touchSlop

        awaitFirstDown(requireUnconsumed = false)
        do {
            val event = awaitPointerEvent()
            val canceled = event.changes.any { it.isConsumed }
            if (!canceled) {
                val zoomChange = event.calculateZoom()
                val rotationChange = event.calculateRotation()
                val panChange = event.calculatePan()

                if (!pastTouchSlop) {
                    zoom *= zoomChange
                    rotation += rotationChange
                    pan += panChange

                    val centroidSize = event.calculateCentroidSize(useCurrent = false)
                    val zoomMotion = abs(1 - zoom) * centroidSize
                    val rotationMotion = abs(rotation * minOf(size.width, size.height) / 180f)
                    val panMotion = pan.getDistance()

                    if (zoomMotion > touchSlop ||
                        rotationMotion > touchSlop ||
                        panMotion > touchSlop
                    ) {
                        pastTouchSlop = true
                    }
                }

                if (pastTouchSlop) {
                    val centroid = event.calculateCentroid(useCurrent = false)
                    if (rotationChange != 0f ||
                        zoomChange != 1f ||
                        panChange != Offset.Zero
                    ) {
                        onGesture(centroid, panChange, zoomChange, rotationChange)
                    }
                    
                    // Consume changes if zoomed in or multi-touch
                    val shouldConsume = isZoomed() || event.changes.size > 1
                    if (shouldConsume) {
                        event.changes.forEach {
                            if (it.positionChanged()) {
                                it.consume()
                            }
                        }
                    }
                }
            }
        } while (!canceled && event.changes.any { it.pressed })
    }
}

/**
 * Zoomable container displaying page bitmap and overlay search highlights.
 */
@Composable
fun ZoomableImage(
    bitmap: Bitmap,
    aspectRatio: Float,
    isInverted: Boolean,
    colorFilter: ColorFilter?,
    searchMatches: List<SearchMatch>,
    highlightMatches: List<Pair<SearchMatch, Color>>,
    ttsSpokenWordRects: List<RectF>,
    pdfPageWidth: Float,
    pdfPageHeight: Float
) {
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(12.dp))
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        if (scale > 1.2f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            scale = 2.5f
                        }
                    }
                )
            }
            .pointerInput(Unit) {
                detectZoomableTransformGestures(
                    isZoomed = { scale > 1.05f }
                ) { centroid, pan, zoom, _ ->
                    val newScale = (scale * zoom).coerceIn(1f, 6f)
                    if (newScale > 1f) {
                        offset += pan
                    } else {
                        offset = Offset.Zero
                    }
                    scale = newScale
                }
            }
    ) {
        val context = LocalContext.current
        val sharedPrefs = remember { context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE) }
        val useOpenGl = remember(sharedPrefs) { sharedPrefs.getBoolean("use_opengl_engine", false) }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y,
                    compositingStrategy = if (useOpenGl) {
                        androidx.compose.ui.graphics.CompositingStrategy.Offscreen
                    } else {
                        androidx.compose.ui.graphics.CompositingStrategy.Auto
                    }
                )
        ) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                colorFilter = colorFilter
            )
            
            // Draw TTS spoken word highlight
            if (ttsSpokenWordRects.isNotEmpty()) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val canvasWidth = size.width
                    val canvasHeight = size.height
                    
                    for (rect in ttsSpokenWordRects) {
                        val left = rect.left * (canvasWidth / pdfPageWidth)
                        val top = rect.top * (canvasHeight / pdfPageHeight)
                        val right = rect.right * (canvasWidth / pdfPageWidth)
                        val bottom = rect.bottom * (canvasHeight / pdfPageHeight)
                        
                        drawRect(
                            color = Color(0xFFFF9800).copy(alpha = 0.4f), // Accent orange for currently spoken word
                            topLeft = Offset(left, top),
                            size = Size(right - left, bottom - top)
                        )
                    }
                }
            }

            // Draw persistent highlights
            if (highlightMatches.isNotEmpty()) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val canvasWidth = size.width
                    val canvasHeight = size.height
                    
                    for (pair in highlightMatches) {
                        val match = pair.first
                        val color = pair.second
                        for (rect in match.rects) {
                            val left = rect.left * (canvasWidth / pdfPageWidth)
                            val top = rect.top * (canvasHeight / pdfPageHeight)
                            val right = rect.right * (canvasWidth / pdfPageWidth)
                            val bottom = rect.bottom * (canvasHeight / pdfPageHeight)
                            
                            drawRect(
                                color = color.copy(alpha = 0.45f),
                                topLeft = Offset(left, top),
                                size = Size(right - left, bottom - top)
                            )
                        }
                    }
                }
            }
            
            // Draw highlights in Canvas overlay
            if (searchMatches.isNotEmpty()) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val canvasWidth = size.width
                    val canvasHeight = size.height
                    
                    for (match in searchMatches) {
                        for (rect in match.rects) {
                            // Map PDF Page points coordinates to Canvas pixels
                            val left = rect.left * (canvasWidth / pdfPageWidth)
                            val top = rect.top * (canvasHeight / pdfPageHeight)
                            val right = rect.right * (canvasWidth / pdfPageWidth)
                            val bottom = rect.bottom * (canvasHeight / pdfPageHeight)
                            
                            drawRect(
                                color = if (isInverted) Color.Green.copy(alpha = 0.35f) else Color.Yellow.copy(alpha = 0.45f),
                                topLeft = Offset(left, top),
                                size = Size(right - left, bottom - top)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Render a single page of PDF into a High-Res display bitmap on background thread.
 */
suspend fun renderPageToBitmap(
    renderer: PdfRenderer,
    pageIndex: Int,
    scale: Float,
    mutex: Mutex
): Bitmap = withContext(Dispatchers.IO) {
    mutex.withLock {
        val page = renderer.openPage(pageIndex)
        val width = (page.width * scale).toInt()
        val height = (page.height * scale).toInt()
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.WHITE)
        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        page.close()
        bitmap
    }
}

/**
 * Share PDF file using standard sharing Framework Intent.
 */
fun sharePdf(context: Context, uri: Uri) {
    try {
        val shareUri = if (uri.scheme == "file") {
            val file = File(uri.path ?: "")
            androidx.core.content.FileProvider.getUriForFile(context, "com.pdfsuny.app.fileprovider", file)
        } else {
            uri
        }
        val extension = shareUri.toString().substringAfterLast('.', "").lowercase()
        val mimeType = when (extension) {
            "epub" -> "application/epub+zip"
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            "odt" -> "application/vnd.oasis.opendocument.text"
            "rtf" -> "application/rtf"
            "umd" -> "application/x-umd"
            "chm" -> "application/x-chm"
            "cbz" -> "application/x-cbz"
            "cbr" -> "application/x-cbr"
            else -> "application/pdf"
        }
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, shareUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share Document"))
    } catch (e: Exception) {
        Toast.makeText(context, "Cannot share this file", Toast.LENGTH_SHORT).show()
    }
}
