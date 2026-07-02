package com.pdfviewerapp.sunuy.ui.screens

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
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ShareCompat
import com.pdfviewerapp.sunuy.data.AppDatabase
import com.pdfviewerapp.sunuy.data.entities.Bookmark
import com.pdfviewerapp.sunuy.data.entities.RecentPdf
import com.pdfviewerapp.sunuy.services.PdfTextService
import com.pdfviewerapp.sunuy.services.SearchMatch
import com.pdfviewerapp.sunuy.services.PageTextAndPositions
import com.pdfviewerapp.sunuy.services.TranslationService
import com.pdfviewerapp.sunuy.services.TtsService
import com.pdfviewerapp.sunuy.services.TtsState
import com.pdfviewerapp.sunuy.ui.PdfSessionViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.activity.ComponentActivity
import com.pdfviewerapp.sunuy.services.AutoScrollMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import android.util.LruCache
import androidx.compose.ui.platform.LocalDensity
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfViewerScreen(
    pdfPath: String,
    onBack: () -> Unit,
    onNavigateToBookmarks: () -> Unit,
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
    var isDarkThemeInverted by remember { mutableStateOf(false) }
    var isSearchActive by remember { mutableStateOf(false) }
    var isMenuExpanded by remember { mutableStateOf(false) }
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
    var pdfReloadTrigger by remember { mutableStateOf(0) }

    fun startTtsForPage(pageIndex: Int) {
        if (isTtsLoading) return
        isTtsLoading = true
        isTtsActive = true
        scope.launch {
            try {
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
                val text = pdfTextService.extractTextFromPage(context, Uri.parse(pdfPath), pageIndex)
                if (text.isNotBlank()) {
                    val translated = translationService.translate(
                        text = text,
                        targetLangCode = targetLanguageCode
                    )
                    activeTranslations[pageIndex] = translated
                } else {
                    activeTranslations[pageIndex] = "No readable text found on this page."
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
                
                if (lowerName.endsWith(".txt") || lowerName.endsWith(".md") || lowerName.endsWith(".html") || lowerName.endsWith(".htm")) {
                    isTextDocument = true
                    val content = context.contentResolver.openInputStream(uri)?.use { input ->
                        input.bufferedReader(Charsets.UTF_8).readText()
                    } ?: ""
                    textDocumentContent = content
                    pageCount = 1
                } else {
                    try {
                        pdfRenderer?.close()
                        parcelFileDescriptor?.close()
                    } catch (e: Exception) {
                        // Ignore
                    }
                    val pfd = context.contentResolver.openFileDescriptor(uri, "r")
                    if (pfd != null) {
                        parcelFileDescriptor = pfd
                        val renderer = PdfRenderer(pfd)
                        pdfRenderer = renderer
                        pageCount = renderer.pageCount
                        
                        // Pre-fill page sizes under lock
                        rendererMutex.withLock {
                            for (i in 0 until renderer.pageCount) {
                                val page = renderer.openPage(i)
                                pageSizes[i] = Pair(page.width, page.height)
                                page.close()
                            }
                        }
                        
                        // Initialize PDFBox
                        PdfTextService.init(context)
                        
                        // Restore last read position (only on initial load)
                        if (pdfReloadTrigger == 0) {
                            val recent = database.recentPdfDao().getRecentPdfByPath(pdfPath)
                            if (recent != null && recent.lastPage < renderer.pageCount) {
                                withContext(Dispatchers.Main) {
                                    listState.scrollToItem(recent.lastPage)
                                }
                            }
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
        if (searchQuery.length >= 3) {
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
                AutoScrollMode.ROLLING_BLIND -> {
                    rollingBlindY = (rollingBlindY + (3f * autoScrollSpeed)) % 800f
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
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { isDarkThemeInverted = !isDarkThemeInverted }) {
                        Icon(
                            imageVector = if (isDarkThemeInverted) Icons.Default.LightMode else Icons.Default.DarkMode,
                            contentDescription = "Day/Night Mode Inversion"
                        )
                    }
                    IconButton(onClick = {
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
                    }) {
                        Icon(
                            imageVector = if (isCurrentPageBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            contentDescription = "Bookmark",
                            tint = if (isCurrentPageBookmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Box {
                        IconButton(onClick = { isMenuExpanded = true }) {
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
                            DropdownMenuItem(
                                text = { Text("Open Bookmarks") },
                                onClick = {
                                    isMenuExpanded = false
                                    onNavigateToBookmarks()
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
                            if (!isTextDocument) {
                                DropdownMenuItem(
                                    text = { Text("Edit PDF Text") },
                                    onClick = {
                                        isMenuExpanded = false
                                        isPdfWordEditorOpen = true
                                    },
                                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }
                                )
                            }
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
                            DropdownMenuItem(
                                text = { Text("Share PDF") },
                                onClick = {
                                    isMenuExpanded = false
                                    sharePdf(context, Uri.parse(pdfPath))
                                },
                                leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) }
                            )
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
                    
                    // Reader / Translator Pane / Auto-Scroll Toggle
                    Row {
                        IconButton(onClick = { isAutoScrollActive = !isAutoScrollActive }) {
                            Icon(
                                imageVector = if (isAutoScrollActive) Icons.Default.PauseCircle else Icons.Default.PlayCircle,
                                contentDescription = "Auto Scroll",
                                tint = if (isAutoScrollActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                        IconButton(onClick = { 
                            isVerticalScroll = !isVerticalScroll
                            sharedPrefs.edit().putBoolean("is_vertical_scroll", isVerticalScroll).apply()
                        }) {
                            Icon(
                                imageVector = if (isVerticalScroll) Icons.Default.SwapVert else Icons.Default.SwapHoriz,
                                contentDescription = "Scroll Direction",
                                tint = if (isVerticalScroll) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                        IconButton(onClick = {
                            isSearchActive = !isSearchActive
                            if (!isSearchActive) {
                                searchQuery = ""
                            }
                        }) {
                            Icon(imageVector = Icons.Default.Search, contentDescription = "Search text")
                        }
                        IconButton(onClick = {
                            if (isTtsActive) {
                                ttsService.stop()
                                isTtsActive = false
                            } else {
                                startTtsForPage(currentPageIndex)
                            }
                        }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = "TTS Read Page",
                                tint = if (isTtsActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                        IconButton(onClick = {
                            isTranslationBarActive = !isTranslationBarActive
                            if (isTranslationBarActive) {
                                translatePage(currentPageIndex)
                            }
                        }) {
                            Icon(imageVector = Icons.Default.Translate, contentDescription = "Translate/TTS Pane")
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
                            .padding(20.dp)
                    ) {
                        val scrollState = androidx.compose.foundation.rememberScrollState()
                        Text(
                            text = textDocumentContent!!,
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(scrollState),
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontSize = 16.sp,
                                lineHeight = 24.sp
                            ),
                            color = if (isDarkThemeInverted) Color.White else Color(0xFF1E293B)
                        )
                    }
                }
            } else if (pdfRenderer != null && pageCount > 0) {
                val configuration = LocalConfiguration.current
                val screenWidth = configuration.screenWidthDp.dp
                
                val PageItem = @Composable { index: Int ->
                    // Load or Render bitmap
                    var bitmap by remember(index, pdfReloadTrigger) { mutableStateOf<Bitmap?>(bitmapCache.get(index)) }
                    val density = LocalDensity.current
                    
                    LaunchedEffect(index, pdfReloadTrigger) {
                        if (bitmap == null) {
                            withContext(Dispatchers.IO) {
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
                            containerColor = if (isDarkThemeInverted) Color(0xFF1E1E1E) else Color.White
                        ),
                        elevation = CardDefaults.cardElevation(2.dp)
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            if (bitmap != null) {
                                ZoomableImage(
                                    bitmap = bitmap!!,
                                    aspectRatio = aspectRatio,
                                    isInverted = isDarkThemeInverted,
                                    colorFilter = if (isDarkThemeInverted) ColorFilter.colorMatrix(darkInvertMatrix) else null,
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
                                                                imageVector = Icons.Default.VolumeUp,
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
                        ((firstVisibleItem.toFloat() + progress) / totalItems.toFloat()).coerceIn(0f, 1f)
                    }
                }

                if (isVerticalScroll && totalItems > 1) {
                    var trackHeight by remember { mutableStateOf(1f) }
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight()
                            .width(36.dp) // Touch-friendly target area
                            .onGloballyPositioned { coordinates ->
                                trackHeight = coordinates.size.height.toFloat()
                            }
                            .pointerInput(totalItems) {
                                detectTapGestures { offset ->
                                    val fraction = (offset.y / trackHeight).coerceIn(0f, 1f)
                                    val targetIndex = (fraction * totalItems).toInt().coerceIn(0, totalItems - 1)
                                    scope.launch {
                                        listState.scrollToItem(targetIndex)
                                    }
                                }
                            }
                            .pointerInput(totalItems) {
                                detectDragGestures { change, _ ->
                                    change.consume()
                                    val fraction = (change.position.y / trackHeight).coerceIn(0f, 1f)
                                    val targetIndex = (fraction * totalItems).toInt().coerceIn(0, totalItems - 1)
                                    scope.launch {
                                        listState.scrollToItem(targetIndex)
                                    }
                                }
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
                                    translationY = (this.size.height * 0.85f) * scrollFraction
                                }
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.8f), RoundedCornerShape(8.dp))
                        )
                    }
                } else if (!isVerticalScroll && totalItems > 1) {
                    var trackWidth by remember { mutableStateOf(1f) }
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(36.dp) // Touch-friendly target area
                            .onGloballyPositioned { coordinates ->
                                trackWidth = coordinates.size.width.toFloat()
                            }
                            .pointerInput(totalItems) {
                                detectTapGestures { offset ->
                                    val fraction = (offset.x / trackWidth).coerceIn(0f, 1f)
                                    val targetIndex = (fraction * totalItems).toInt().coerceIn(0, totalItems - 1)
                                    scope.launch {
                                        listState.scrollToItem(targetIndex)
                                    }
                                }
                            }
                            .pointerInput(totalItems) {
                                detectDragGestures { change, _ ->
                                    change.consume()
                                    val fraction = (change.position.x / trackWidth).coerceIn(0f, 1f)
                                    val targetIndex = (fraction * totalItems).toInt().coerceIn(0, totalItems - 1)
                                    scope.launch {
                                        listState.scrollToItem(targetIndex)
                                    }
                                }
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
                                    translationX = (this.size.width * 0.85f) * scrollFraction
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
                                        Icon(Icons.Default.ArrowBack, contentDescription = "Prev match")
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
                                        Icon(Icons.Default.ArrowForward, contentDescription = "Next match")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Moon+ Reader Rolling Blind Overlay
            if (isAutoScrollActive && autoScrollMode == AutoScrollMode.ROLLING_BLIND) {
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

                        // Voice Gender Selection Row
                        if (!isTtsLoading) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Voice:",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
                        }
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isTtsLoading) {
                                Box(
                                    modifier = Modifier.fillMaxWidth(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                }
                            } else {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    when (ttsState) {
                                        TtsState.SPEAKING -> {
                                            IconButton(onClick = { ttsService.pause() }) {
                                                Icon(
                                                    imageVector = Icons.Default.Pause,
                                                    contentDescription = "Pause",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(28.dp)
                                                )
                                            }
                                            IconButton(onClick = { ttsService.stop(); isTtsActive = false }) {
                                                Icon(
                                                    imageVector = Icons.Default.Stop,
                                                    contentDescription = "Stop",
                                                    tint = MaterialTheme.colorScheme.error,
                                                    modifier = Modifier.size(28.dp)
                                                )
                                            }
                                        }
                                        TtsState.PAUSED -> {
                                            IconButton(onClick = { ttsService.resume() }) {
                                                Icon(
                                                    imageVector = Icons.Default.PlayArrow,
                                                    contentDescription = "Resume",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(28.dp)
                                                )
                                            }
                                            IconButton(onClick = { ttsService.stop(); isTtsActive = false }) {
                                                Icon(
                                                    imageVector = Icons.Default.Stop,
                                                    contentDescription = "Stop",
                                                    tint = MaterialTheme.colorScheme.error,
                                                    modifier = Modifier.size(28.dp)
                                                )
                                            }
                                        }
                                        TtsState.IDLE -> {
                                            IconButton(onClick = { startTtsForPage(currentPageIndex) }) {
                                                Icon(
                                                    imageVector = Icons.Default.PlayArrow,
                                                    contentDescription = "Play",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(28.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                                
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
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
                                            modifier = Modifier.height(28.dp)
                                        )
                                    }
                                }
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
                                                com.pdfviewerapp.sunuy.data.entities.HighlightAnnotation(
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
                            if (isTextDocument) {
                                Button(
                                    onClick = {
                                        scope.launch {
                                            try {
                                                withContext(Dispatchers.IO) {
                                                    val uri = Uri.parse(pdfPath)
                                                    context.contentResolver.openOutputStream(uri, "rwt")?.use { output ->
                                                        output.write(editorContent.toByteArray(Charsets.UTF_8))
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y
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
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share PDF File"))
    } catch (e: Exception) {
        Toast.makeText(context, "Cannot share this file", Toast.LENGTH_SHORT).show()
    }
}
