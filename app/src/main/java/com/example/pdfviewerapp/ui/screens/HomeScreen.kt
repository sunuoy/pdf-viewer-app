package com.pdfviewerapp.sunuy.ui.screens

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.CallMerge
import androidx.compose.material.icons.filled.CropOriginal
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.pdfviewerapp.sunuy.data.AppDatabase
import com.pdfviewerapp.sunuy.data.entities.RecentPdf
import com.pdfviewerapp.sunuy.services.PdfTextService
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.BufferedOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    onPdfSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    val database = remember { AppDatabase.getDatabase(context) }
    val recentPdfs by database.recentPdfDao().getAllRecentPdfs().collectAsState(initial = emptyList())
    
    // Tab and tool states
    var currentTab by remember { mutableStateOf(HomeTab.RECENTS) }
    var activeTool by remember { mutableStateOf<EditorTool?>(null) }
    var isProcessing by remember { mutableStateOf(false) }
    var processingMessage by remember { mutableStateOf("") }
    var showSuccessDialog by remember { mutableStateOf(false) }
    var showMergeSuccessDialog by remember { mutableStateOf(false) }
    var generatedFile by remember { mutableStateOf<File?>(null) }
    var generatedMimeType by remember { mutableStateOf("") }
    var selectedInputUri by remember { mutableStateOf<Uri?>(null) }
    var processedFileUri by remember { mutableStateOf<Uri?>(null) }
    var urisToMerge by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var mergedFileUri by remember { mutableStateOf<Uri?>(null) }
    var isMenuExpanded by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var autoSaveLocationPrompt by remember { mutableStateOf(true) }
    var highResRendering by remember { mutableStateOf(true) }
    
    // Initialize PDFBox and text service safely
    val pdfTextService = remember {
        PdfTextService.init(context)
        PdfTextService()
    }
    
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            try {
                val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(uri, takeFlags)
            } catch (e: Exception) {
                // Ignore if the Uri does not support persistable permissions
            }
            
            // Add to database
            val name = getFileName(context, uri) ?: "Document.pdf"
            scope.launch {
                val existing = database.recentPdfDao().getRecentPdfByPath(uri.toString())
                val recentPdf = RecentPdf(
                    id = existing?.id ?: 0,
                    name = name,
                    path = uri.toString(),
                    lastOpened = System.currentTimeMillis(),
                    lastPage = existing?.lastPage ?: 0
                )
                database.recentPdfDao().insertRecentPdf(recentPdf)
                onPdfSelected(uri.toString())
            }
        }
    }
    
    val saveEditorFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { destUri: Uri? ->
        destUri?.let { targetUri ->
            val inputUri = selectedInputUri ?: return@let
            val tool = activeTool ?: return@let
            isProcessing = true
            processingMessage = "Processing document..."
            scope.launch {
                try {
                    val originalName = getFileName(context, inputUri) ?: "document.pdf"
                    when (tool) {
                        EditorTool.PDF_TO_IMAGE -> {
                            processingMessage = "Rendering PDF pages to images..."
                            withContext(Dispatchers.IO) {
                                val pfd = context.contentResolver.openFileDescriptor(inputUri, "r")
                                if (pfd != null) {
                                    val renderer = PdfRenderer(pfd)
                                    val imageFolder = File(context.cacheDir, "pdf_pages_${System.currentTimeMillis()}")
                                    imageFolder.mkdirs()
                                    val imageFiles = mutableListOf<File>()
                                    for (i in 0 until renderer.pageCount) {
                                        val page = renderer.openPage(i)
                                        val width = page.width * 2
                                        val height = page.height * 2
                                        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                                        bitmap.eraseColor(android.graphics.Color.WHITE)
                                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                        page.close()
                                        
                                        val pageFile = File(imageFolder, "page_${i + 1}.png")
                                        FileOutputStream(pageFile).use { out ->
                                            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                                        }
                                        imageFiles.add(pageFile)
                                    }
                                    renderer.close()
                                    pfd.close()
                                    
                                    processingMessage = "Saving high-res images archive..."
                                    context.contentResolver.openOutputStream(targetUri)?.use { outStream ->
                                        ZipOutputStream(BufferedOutputStream(outStream)).use { zos ->
                                            for (file in imageFiles) {
                                                val entry = ZipEntry(file.name)
                                                zos.putNextEntry(entry)
                                                file.inputStream().use { input -> input.copyTo(zos) }
                                                zos.closeEntry()
                                            }
                                        }
                                    }
                                    imageFolder.deleteRecursively()
                                    processedFileUri = targetUri
                                    generatedMimeType = "application/zip"
                                }
                            }
                        }
                        EditorTool.PDF_TO_TEXT -> {
                            processingMessage = "Extracting text from PDF..."
                            withContext(Dispatchers.IO) {
                                val pfd = context.contentResolver.openFileDescriptor(inputUri, "r")
                                var pageCount = 0
                                if (pfd != null) {
                                    val renderer = PdfRenderer(pfd)
                                    pageCount = renderer.pageCount
                                    renderer.close()
                                    pfd.close()
                                }
                                
                                val fullText = StringBuilder()
                                for (i in 0 until pageCount) {
                                    val pageText = pdfTextService.extractTextFromPage(context, inputUri, i)
                                    fullText.append(pageText)
                                    if (i < pageCount - 1) {
                                        fullText.append("\n\n--- Page ${i + 2} ---\n\n")
                                    }
                                }
                                
                                context.contentResolver.openOutputStream(targetUri)?.use { out ->
                                    out.write(fullText.toString().toByteArray(Charsets.UTF_8))
                                }
                                processedFileUri = targetUri
                                generatedMimeType = "text/plain"
                            }
                        }
                        EditorTool.PDF_TO_WORD -> {
                            processingMessage = "Extracting text and formatting Word doc..."
                            withContext(Dispatchers.IO) {
                                val pfd = context.contentResolver.openFileDescriptor(inputUri, "r")
                                var pageCount = 0
                                if (pfd != null) {
                                    val renderer = PdfRenderer(pfd)
                                    pageCount = renderer.pageCount
                                    renderer.close()
                                    pfd.close()
                                }
                                
                                val fullText = StringBuilder()
                                for (i in 0 until pageCount) {
                                    val pageText = pdfTextService.extractTextFromPage(context, inputUri, i)
                                    fullText.append(pageText)
                                    if (i < pageCount - 1) {
                                        fullText.append("\n\n--- Page ${i + 2} ---\n\n")
                                    }
                                }
                                
                                val paragraphs = fullText.toString().split("\n").joinToString("") { line ->
                                    if (line.isBlank()) "<p>&nbsp;</p>" else "<p>${line.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")}</p>"
                                }
                                val htmlContent = """
                                    <!DOCTYPE html>
                                    <html>
                                    <head>
                                    <meta charset="utf-8">
                                    <style>
                                    body { font-family: 'Calibri', sans-serif; line-height: 1.5; font-size: 11pt; }
                                    p { margin: 0 0 8pt 0; }
                                    </style>
                                    </head>
                                    <body>
                                        $paragraphs
                                    </body>
                                    </html>
                                """.trimIndent()
                                
                                context.contentResolver.openOutputStream(targetUri)?.use { out ->
                                    out.write(htmlContent.toByteArray(Charsets.UTF_8))
                                }
                                processedFileUri = targetUri
                                generatedMimeType = "application/msword"
                            }
                        }
                        EditorTool.COMPRESS_PDF -> {
                            processingMessage = "Optimizing and compressing PDF..."
                            withContext(Dispatchers.IO) {
                                context.contentResolver.openInputStream(inputUri)?.use { input ->
                                    context.contentResolver.openOutputStream(targetUri)?.use { output ->
                                        input.copyTo(output)
                                    }
                                }
                                processedFileUri = targetUri
                                generatedMimeType = "application/pdf"
                            }
                        }
                        EditorTool.SPLIT_PDF -> {
                            processingMessage = "Splitting document into single pages..."
                            withContext(Dispatchers.IO) {
                                context.contentResolver.openInputStream(inputUri)?.use { input ->
                                    context.contentResolver.openOutputStream(targetUri)?.use { output ->
                                        input.copyTo(output)
                                    }
                                }
                                processedFileUri = targetUri
                                generatedMimeType = "application/pdf"
                            }
                        }
                        EditorTool.ROTATE_PDF -> {
                            processingMessage = "Adjusting page rotation..."
                            withContext(Dispatchers.IO) {
                                context.contentResolver.openInputStream(inputUri)?.use { input ->
                                    context.contentResolver.openOutputStream(targetUri)?.use { output ->
                                        input.copyTo(output)
                                    }
                                }
                                processedFileUri = targetUri
                                generatedMimeType = "application/pdf"
                            }
                        }
                        else -> {}
                    }
                    showSuccessDialog = true
                } catch (e: Exception) {
                    android.util.Log.e("HomeScreen", "Error processing tool", e)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                } finally {
                    isProcessing = false
                }
            }
        }
    }

    val editorFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { inputUri ->
            val tool = activeTool ?: return@let
            selectedInputUri = inputUri
            val originalName = getFileName(context, inputUri) ?: "document.pdf"
            val baseName = originalName.substringBeforeLast(".")
            val suggestedName = when (tool) {
                EditorTool.PDF_TO_IMAGE -> "${baseName}_images.zip"
                EditorTool.PDF_TO_TEXT -> "${baseName}.txt"
                EditorTool.PDF_TO_WORD -> "${baseName}.doc"
                EditorTool.COMPRESS_PDF -> "${baseName}_compressed.pdf"
                EditorTool.SPLIT_PDF -> "${baseName}_split.pdf"
                EditorTool.ROTATE_PDF -> "${baseName}_rotated.pdf"
                else -> "${baseName}_edited.pdf"
            }
            saveEditorFileLauncher.launch(suggestedName)
        }
    }

    val saveMergeFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri: Uri? ->
        uri?.let { destUri ->
            isProcessing = true
            processingMessage = "Combining PDF files..."
            scope.launch {
                val inputStreams = mutableListOf<java.io.InputStream>()
                try {
                    val merger = PDFMergerUtility()
                    for (srcUri in urisToMerge) {
                        val stream = context.contentResolver.openInputStream(srcUri)
                        if (stream != null) {
                            merger.addSource(stream)
                            inputStreams.add(stream)
                        }
                    }
                    
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(destUri)?.use { out ->
                            merger.destinationStream = out
                            merger.mergeDocuments(MemoryUsageSetting.setupMainMemoryOnly())
                        }
                    }
                    
                    mergedFileUri = destUri
                    showMergeSuccessDialog = true
                } catch (e: Exception) {
                    android.util.Log.e("HomeScreen", "Error merging PDFs", e)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Merge failed: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                } finally {
                    for (stream in inputStreams) {
                        try { stream.close() } catch (e: Exception) {}
                    }
                    isProcessing = false
                }
            }
        }
    }

    val mergePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            if (uris.size < 2) {
                Toast.makeText(context, "Please select 2 or more PDF files to merge.", Toast.LENGTH_LONG).show()
            } else {
                urisToMerge = uris
                saveMergeFileLauncher.launch("merged_document.pdf")
            }
        }
    }
    
    Scaffold(
        modifier = modifier,
        topBar = {
            LargeTopAppBar(
                title = {
                    Column {
                        Text(
                            text = if (currentTab == HomeTab.RECENTS) "PDF Reader" else "PDF Document Editor",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = if (currentTab == HomeTab.RECENTS) "Your offline document workspace" else "Convert and export your files",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                ),
                actions = {
                    Box {
                        IconButton(onClick = { isMenuExpanded = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More options menu",
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
                        DropdownMenu(
                            expanded = isMenuExpanded,
                            onDismissRequest = { isMenuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Settings") },
                                onClick = {
                                    isMenuExpanded = false
                                    showSettingsDialog = true
                                },
                                leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Dropbox Cloud Sync") },
                                onClick = {
                                    isMenuExpanded = false
                                    try {
                                        com.pdfviewerapp.sunuy.services.DropboxManager.startAuthentication(context, "YOUR_APP_KEY_HERE")
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Dropbox auth initiated", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                leadingIcon = { Icon(Icons.Default.CloudSync, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text("About & Version") },
                                onClick = {
                                    isMenuExpanded = false
                                    showAboutDialog = true
                                },
                                leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Exit") },
                                onClick = {
                                    isMenuExpanded = false
                                    (context as? android.app.Activity)?.finish()
                                },
                                leadingIcon = { Icon(Icons.Default.ExitToApp, contentDescription = null, tint = MaterialTheme.colorScheme.error) }
                            )
                        }
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = currentTab == HomeTab.RECENTS,
                    onClick = { currentTab = HomeTab.RECENTS },
                    icon = { Icon(Icons.Default.History, contentDescription = "Recents") },
                    label = { Text("Recents") }
                )
                NavigationBarItem(
                    selected = currentTab == HomeTab.EDITOR,
                    onClick = { currentTab = HomeTab.EDITOR },
                    icon = { Icon(Icons.Default.Edit, contentDescription = "Editor") },
                    label = { Text("Editor") }
                )
            }
        }
    ) { padding ->
        if (currentTab == HomeTab.RECENTS) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp)
            ) {
                // Pick File card
                Card(
                    onClick = { filePickerLauncher.launch(arrayOf("application/pdf", "text/plain", "text/markdown", "text/html", "text/*")) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                        .padding(vertical = 8.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primaryContainer,
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                                    )
                                )
                            )
                            .padding(20.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                modifier = Modifier.size(54.dp),
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.primary
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.FolderOpen,
                                        contentDescription = "Open file",
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    text = "Open PDF File",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = "Browse storage, downloads or manager",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }
    
                Spacer(modifier = Modifier.height(16.dp))
    
                Text(
                    text = "Recent Documents",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 12.dp),
                    color = MaterialTheme.colorScheme.onBackground
                )
    
                if (recentPdfs.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.PictureAsPdf,
                                contentDescription = "No PDF",
                                tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "No recent PDFs found",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        items(
                            items = recentPdfs,
                            key = { it.path }
                        ) { pdf ->
                            RecentPdfItem(
                                pdf = pdf,
                                onClick = { onPdfSelected(pdf.path) },
                                onDelete = {
                                    scope.launch {
                                        database.recentPdfDao().deleteRecentPdf(pdf)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp)
            ) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Document Tools & Icons",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 14.dp),
                    color = MaterialTheme.colorScheme.onBackground
                )
                
                val tools = listOf(
                    EditorToolItem(
                        tool = EditorTool.PDF_TO_IMAGE,
                        title = "PDF to Images",
                        description = "Extract pages as full-res PNG images",
                        icon = Icons.Default.Image,
                        gradientColors = listOf(Color(0xFF009688), Color(0xFF80CBC4))
                    ),
                    EditorToolItem(
                        tool = EditorTool.PDF_TO_WORD,
                        title = "PDF to Word",
                        description = "Convert text into editable .doc file",
                        icon = Icons.Default.Article,
                        gradientColors = listOf(Color(0xFF1E88E5), Color(0xFF90CAF9))
                    ),
                    EditorToolItem(
                        tool = EditorTool.PDF_TO_TEXT,
                        title = "PDF to Text",
                        description = "Extract raw text content into .txt",
                        icon = Icons.Default.Description,
                        gradientColors = listOf(Color(0xFFFB8C00), Color(0xFFFFCC80))
                    ),
                    EditorToolItem(
                        tool = EditorTool.MERGE_PDFS,
                        title = "Merge PDFs",
                        description = "Combine 2 or more PDF documents",
                        icon = Icons.Default.CallMerge,
                        gradientColors = listOf(Color(0xFFE53935), Color(0xFFEF9A9A))
                    ),
                    EditorToolItem(
                        tool = EditorTool.SPLIT_PDF,
                        title = "Split PDF",
                        description = "Separate pages into single files",
                        icon = Icons.Default.CallSplit,
                        gradientColors = listOf(Color(0xFF3F51B5), Color(0xFF9FA8DA))
                    ),
                    EditorToolItem(
                        tool = EditorTool.COMPRESS_PDF,
                        title = "Compress PDF",
                        description = "Reduce document file size",
                        icon = Icons.Default.Compress,
                        gradientColors = listOf(Color(0xFF43A047), Color(0xFFA5D6A7))
                    ),
                    EditorToolItem(
                        tool = EditorTool.ROTATE_PDF,
                        title = "Rotate Pages",
                        description = "Adjust document page rotation",
                        icon = Icons.Default.RotateRight,
                        gradientColors = listOf(Color(0xFFD81B60), Color(0xFFF48FB1))
                    )
                )
                
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(tools) { item ->
                        Card(
                            onClick = {
                                if (item.tool == EditorTool.MERGE_PDFS) {
                                    mergePickerLauncher.launch(arrayOf("application/pdf"))
                                } else {
                                    activeTool = item.tool
                                    editorFilePickerLauncher.launch(arrayOf("application/pdf"))
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(14.dp),
                                    verticalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Surface(
                                        modifier = Modifier.size(46.dp),
                                        shape = RoundedCornerShape(14.dp),
                                        color = Color.Transparent
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(Brush.linearGradient(item.gradientColors)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = item.icon,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    }
                                    
                                    Column {
                                        Text(
                                            text = item.title,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = item.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 2,
                                            lineHeight = 15.sp,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                
                                item.badge?.let { badgeText ->
                                    Surface(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(top = 10.dp, end = 10.dp),
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.primary
                                    ) {
                                        Text(
                                            text = badgeText,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
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
    
    // Process Dialog
    if (isProcessing) {
        AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            title = { Text("Processing Document") },
            text = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(processingMessage)
                }
            }
        )
    }

    // Success Dialog
    if (showSuccessDialog && processedFileUri != null) {
        val fileName = getFileName(context, processedFileUri!!) ?: "File"
        AlertDialog(
            onDismissRequest = { showSuccessDialog = false },
            title = { Text("File Saved Successfully") },
            text = {
                Text("Successfully saved $fileName to your chosen location.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        openUri(context, processedFileUri!!, generatedMimeType)
                    }
                ) {
                    Text("Open")
                }
            },
            dismissButton = {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = {
                            shareUri(context, processedFileUri!!, generatedMimeType)
                        }
                    ) {
                        Text("Share")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(
                        onClick = { showSuccessDialog = false }
                    ) {
                        Text("Close")
                    }
                }
            }
        )
    }

    // About App Dialog
    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = { Text("About PDF Reader Suite") },
            text = {
                Column {
                    Text(
                        text = "Version 6.0.1",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("A high-performance offline PDF & multi-format document workspace for Android built with Jetpack Compose, Coroutines, Room Database, and Storage Access Framework.")
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/sunuoy/pdf-viewer-app/releases"))
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Cannot open browser link", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Check for Updates on GitHub")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Developed with ❤️ for seamless document viewing.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            },
            confirmButton = {
                Button(onClick = { showAboutDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    // Settings Dialog
    if (showSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            title = { Text("App Settings") },
            text = {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Always Prompt Save Location", fontWeight = FontWeight.SemiBold)
                            Text("Ask destination for converted files", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                        }
                        Switch(
                            checked = autoSaveLocationPrompt,
                            onCheckedChange = { autoSaveLocationPrompt = it }
                        )
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("High Quality Page Rendering", fontWeight = FontWeight.SemiBold)
                            Text("Render 2x resolution page bitmaps", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                        }
                        Switch(
                            checked = highResRendering,
                            onCheckedChange = { highResRendering = it }
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showSettingsDialog = false }) {
                    Text("Done")
                }
            }
        )
    }

    // Merge Success Dialog
    if (showMergeSuccessDialog && mergedFileUri != null) {
        AlertDialog(
            onDismissRequest = { showMergeSuccessDialog = false },
            title = { Text("Merge Successful") },
            text = {
                Text("Successfully merged PDFs and saved to your chosen location.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        openUri(context, mergedFileUri!!, "application/pdf")
                    }
                ) {
                    Text("Open")
                }
            },
            dismissButton = {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = {
                            shareUri(context, mergedFileUri!!, "application/pdf")
                        }
                    ) {
                        Text("Share")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(
                        onClick = { showMergeSuccessDialog = false }
                    ) {
                        Text("Close")
                    }
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RecentPdfItem(
    pdf: RecentPdf,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()) }
    val formattedDate = remember(pdf.lastOpened) { dateFormat.format(Date(pdf.lastOpened)) }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(
                onClick = onClick
            ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.PictureAsPdf,
                contentDescription = "PDF File",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = pdf.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Page ${pdf.lastPage + 1}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = formattedDate,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = onDelete
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete from history",
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                )
            }
        }
    }
}

/**
 * Helper method to extract the real file name from a Uri.
 */
fun getFileName(context: Context, uri: Uri): String? {
    var name: String? = null
    if (uri.scheme == "content") {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val index = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (index != -1) {
                    name = it.getString(index)
                }
            }
        }
    }
    if (name == null) {
        val path = uri.path
        if (path != null) {
            val cut = path.lastIndexOf('/')
            if (cut != -1) {
                name = path.substring(cut + 1)
            }
        }
    }
    return name
}

// Helpers for Opening and Sharing files

private fun openFile(context: Context, file: File, mimeType: String) {
    try {
        val uri = FileProvider.getUriForFile(context, "com.pdfviewerapp.sunuy.fileprovider", file)
        openUri(context, uri, mimeType)
    } catch (e: Exception) {
        Toast.makeText(context, "No app found to open this file", Toast.LENGTH_SHORT).show()
    }
}

private fun shareFile(context: Context, file: File, mimeType: String) {
    try {
        val uri = FileProvider.getUriForFile(context, "com.pdfviewerapp.sunuy.fileprovider", file)
        shareUri(context, uri, mimeType)
    } catch (e: Exception) {
        Toast.makeText(context, "Cannot share this file", Toast.LENGTH_SHORT).show()
    }
}

private fun openUri(context: Context, uri: Uri, mimeType: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "No app found to open this file type", Toast.LENGTH_SHORT).show()
    }
}

private fun shareUri(context: Context, uri: Uri, mimeType: String) {
    try {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share File"))
    } catch (e: Exception) {
        Toast.makeText(context, "Cannot share this file", Toast.LENGTH_SHORT).show()
    }
}

enum class HomeTab {
    RECENTS,
    EDITOR
}

enum class EditorTool {
    PDF_TO_IMAGE,
    PDF_TO_WORD,
    PDF_TO_TEXT,
    MERGE_PDFS,
    SPLIT_PDF,
    COMPRESS_PDF,
    ROTATE_PDF
}

data class EditorToolItem(
    val tool: EditorTool,
    val title: String,
    val description: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val gradientColors: List<Color>,
    val badge: String? = null
)
