package com.example.pdfviewerapp.ui.screens

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
import androidx.compose.foundation.gestures.detectTransformGestures
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
import com.example.pdfviewerapp.data.AppDatabase
import com.example.pdfviewerapp.data.entities.Bookmark
import com.example.pdfviewerapp.data.entities.RecentPdf
import com.example.pdfviewerapp.services.PdfTextService
import com.example.pdfviewerapp.services.SearchMatch
import com.example.pdfviewerapp.services.TranslationService
import com.example.pdfviewerapp.services.TtsService
import com.example.pdfviewerapp.services.TtsState
import com.example.pdfviewerapp.ui.PdfSessionViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.activity.ComponentActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    
    // Database
    val database = remember { AppDatabase.getDatabase(context) }
    
    // State holders
    var pdfRenderer by remember { mutableStateOf<PdfRenderer?>(null) }
    var parcelFileDescriptor by remember { mutableStateOf<ParcelFileDescriptor?>(null) }
    var pageCount by remember { mutableStateOf(0) }
    var documentName by remember { mutableStateOf("Document") }
    
    // Page dimensions cache (pageIndex -> Pair(width, height))
    val pageSizes = remember { mutableStateMapOf<Int, Pair<Int, Int>>() }
    
    // Rendered bitmaps cache
    val bitmapCache = remember { mutableStateMapOf<Int, Bitmap>() }
    
    // Navigation / Scroll State
    val listState = rememberLazyListState()
    val visibleItemsInfo = listState.layoutInfo.visibleItemsInfo
    val currentPageIndex = remember(visibleItemsInfo, pageCount) {
        if (visibleItemsInfo.isEmpty()) 0
        else visibleItemsInfo.first().index.coerceIn(0, maxOf(0, pageCount - 1))
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
    
    // Bottom Sheet: Translation & TTS Reader Mode
    var isReaderModeActive by remember { mutableStateOf(false) }
    var pageTextContent by remember { mutableStateOf("") }
    var isExtractingText by remember { mutableStateOf(false) }
    
    // Translation options
    var targetLanguageCode by remember { mutableStateOf("es") }
    var isTranslating by remember { mutableStateOf(false) }
    var translatedText by remember { mutableStateOf("") }
    
    // TTS State
    val ttsState by ttsService.state.collectAsState()
    
    // Clean up services
    DisposableEffect(Unit) {
        onDispose {
            ttsService.shutdown()
            translationService.closeActiveTranslator()
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
    
    // Load PDF Document
    LaunchedEffect(pdfPath) {
        withContext(Dispatchers.IO) {
            try {
                val uri = Uri.parse(pdfPath)
                documentName = getFileName(context, uri) ?: "Document.pdf"
                
                val pfd = context.contentResolver.openFileDescriptor(uri, "r")
                if (pfd != null) {
                    parcelFileDescriptor = pfd
                    val renderer = PdfRenderer(pfd)
                    pdfRenderer = renderer
                    pageCount = renderer.pageCount
                    
                    // Pre-fill page sizes
                    for (i in 0 until renderer.pageCount) {
                        val page = renderer.openPage(i)
                        pageSizes[i] = Pair(page.width, page.height)
                        page.close()
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
            } catch (e: Exception) {
                Log.e("PdfViewerScreen", "Error loading PDF", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Failed to load PDF file", Toast.LENGTH_LONG).show()
                    onBack()
                }
            }
        }
    }
    
    // Save last page progress to database
    LaunchedEffect(currentPageIndex) {
        if (pageCount > 0) {
            scope.launch {
                val existing = database.recentPdfDao().getRecentPdfByPath(pdfPath)
                if (existing != null) {
                    database.recentPdfDao().insertRecentPdf(
                        existing.copy(lastPage = currentPageIndex, lastOpened = System.currentTimeMillis())
                    )
                }
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
                    
                    // Reader / Translator Pane Toggle
                    Row {
                        IconButton(onClick = {
                            isSearchActive = !isSearchActive
                            if (!isSearchActive) {
                                searchQuery = ""
                            }
                        }) {
                            Icon(imageVector = Icons.Default.Search, contentDescription = "Search text")
                        }
                        IconButton(onClick = {
                            isReaderModeActive = true
                            isExtractingText = true
                            translatedText = ""
                            scope.launch {
                                pageTextContent = pdfTextService.extractTextFromPage(
                                    context,
                                    Uri.parse(pdfPath),
                                    currentPageIndex
                                )
                                isExtractingText = false
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

            // PDF Pages list
            if (pdfRenderer != null && pageCount > 0) {
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
                        var bitmap by remember { mutableStateOf<Bitmap?>(bitmapCache[index]) }
                        
                        LaunchedEffect(index) {
                            if (bitmap == null) {
                                withContext(Dispatchers.IO) {
                                    pdfRenderer?.let { renderer ->
                                        try {
                                            val bmp = renderPageToBitmap(renderer, index)
                                            bitmapCache[index] = bmp
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
        }
    }
    
    // Translation and TTS Drawer / Bottom Sheet
    if (isReaderModeActive) {
        ModalBottomSheet(
            onDismissRequest = {
                isReaderModeActive = false
                ttsService.stop()
            },
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 600.dp)
                    .padding(20.dp)
                    .navigationBarsPadding()
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Reader Mode (Page ${currentPageIndex + 1})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    
                    // TTS Controller Panel
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                when (ttsState) {
                                    TtsState.IDLE -> ttsService.startSpeaking(pageTextContent)
                                    TtsState.SPEAKING -> ttsService.pause()
                                    TtsState.PAUSED -> ttsService.resume()
                                }
                            },
                            enabled = pageTextContent.isNotBlank() && !isExtractingText
                        ) {
                            Icon(
                                imageVector = when (ttsState) {
                                    TtsState.SPEAKING -> Icons.Default.Pause
                                    else -> Icons.Default.PlayArrow
                                },
                                contentDescription = "Play/Pause TTS",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        if (ttsState != TtsState.IDLE) {
                            IconButton(onClick = { ttsService.stop() }) {
                                Icon(
                                    imageVector = Icons.Default.Stop,
                                    contentDescription = "Stop TTS",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Target Translation Selection
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Translate Page to:",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    
                    var expandedLang by remember { mutableStateOf(false) }
                    val currentLangName = remember(targetLanguageCode) {
                        translationService.supportedLanguages.find { it.code == targetLanguageCode }?.name ?: "Spanish"
                    }
                    
                    Box {
                        Button(
                            onClick = { expandedLang = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        ) {
                            Text(text = currentLangName)
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
                                        // Auto-translate if text already loaded
                                        if (pageTextContent.isNotBlank()) {
                                            isTranslating = true
                                            scope.launch {
                                                translatedText = translationService.translate(
                                                    text = pageTextContent,
                                                    targetLangCode = targetLanguageCode
                                                )
                                                isTranslating = false
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Content Areas
                if (isExtractingText) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        // Left / Source Column
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            )
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(12.dp)
                            ) {
                                // Make selectable via TextField (read only)
                                val textValue = remember(pageTextContent) {
                                    TextFieldValue(pageTextContent)
                                }
                                OutlinedTextField(
                                    value = textValue,
                                    onValueChange = {},
                                    readOnly = true,
                                    modifier = Modifier.fillMaxSize(),
                                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
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
                        
                        Spacer(modifier = Modifier.width(12.dp))
                        
                        // Right / Translated Column
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                            )
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(12.dp)
                            ) {
                                if (isTranslating) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.align(Alignment.Center)
                                    )
                                } else if (translatedText.isNotBlank()) {
                                    Column(modifier = Modifier.fillMaxSize()) {
                                        // Header inside translation
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "Translation",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            IconButton(
                                                onClick = { ttsService.startSpeaking(translatedText) },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.VolumeUp,
                                                    contentDescription = "Speak translation",
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        
                                        val transTextValue = remember(translatedText) {
                                            TextFieldValue(translatedText)
                                        }
                                        OutlinedTextField(
                                            value = transTextValue,
                                            onValueChange = {},
                                            readOnly = true,
                                            modifier = Modifier.fillMaxSize(),
                                            textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                                            colors = TextFieldDefaults.colors(
                                                focusedContainerColor = Color.Transparent,
                                                unfocusedContainerColor = Color.Transparent,
                                                disabledContainerColor = Color.Transparent,
                                                focusedIndicatorColor = Color.Transparent,
                                                unfocusedIndicatorColor = Color.Transparent
                                            )
                                        )
                                    }
                                } else {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Button(
                                            onClick = {
                                                isTranslating = true
                                                scope.launch {
                                                    translatedText = translationService.translate(
                                                        text = pageTextContent,
                                                        targetLangCode = targetLanguageCode
                                                    )
                                                    isTranslating = false
                                                }
                                            },
                                            enabled = pageTextContent.isNotBlank()
                                        ) {
                                            Text("Translate Now")
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
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 5f)
                    if (scale > 1f) {
                        offset = offset + pan
                    } else {
                        offset = Offset.Zero
                    }
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
    scale: Float = 2.5f // Set high scale for clear reading text quality
): Bitmap = withContext(Dispatchers.IO) {
    val page = renderer.openPage(pageIndex)
    val width = (page.width * scale).toInt()
    val height = (page.height * scale).toInt()
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    bitmap.eraseColor(android.graphics.Color.WHITE)
    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
    page.close()
    bitmap
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
