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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
    var isDarkThemeInverted by remember { mutableStateOf(false) }
    var isSearchActive by remember { mutableStateOf(false) }
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
    var currentTtsSpeed by remember { mutableStateOf(1.0f) }
    var currentTtsPitch by remember { mutableStateOf(1.0f) }
    
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
    LaunchedEffect(pdfPath) {
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
                        
                        // Restore last read position
                        val recent = database.recentPdfDao().getRecentPdfByPath(pdfPath)
                        if (recent != null && recent.lastPage < renderer.pageCount) {
                            withContext(Dispatchers.Main) {
                                listState.scrollToItem(recent.lastPage)
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
                    IconButton(onClick = onNavigateToBookmarks) {
                        Icon(imageVector = Icons.Default.Bookmarks, contentDescription = "Open Bookmarks")
                    }
                    IconButton(onClick = { sharePdf(context, Uri.parse(pdfPath)) }) {
                        Icon(imageVector = Icons.Default.Share, contentDescription = "Share PDF")
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
                            isSearchActive = !isSearchActive
                            if (!isSearchActive) {
                                searchQuery = ""
                            }
                        }) {
                            Icon(imageVector = Icons.Default.Search, contentDescription = "Search text")
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
                        // Load or Render bitmap
                        var bitmap by remember { mutableStateOf<Bitmap?>(bitmapCache.get(index)) }
                        val density = LocalDensity.current
                        
                        LaunchedEffect(index) {
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
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(aspectRatio)
                                .padding(horizontal = 8.dp),
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
                            IconButton(onClick = { isTranslationBarActive = false }) {
                                Icon(Icons.Default.Close, contentDescription = "Close translation bar")
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
