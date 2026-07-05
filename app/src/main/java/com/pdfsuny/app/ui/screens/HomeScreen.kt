package com.pdfsuny.app.ui.screens

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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.CallMerge
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.CropOriginal
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.BorderColor
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.pdfsuny.app.data.AppDatabase
import com.pdfsuny.app.ui.components.TooltipIconButton
import com.pdfsuny.app.data.entities.RecentPdf
import com.pdfsuny.app.services.PdfTextService
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
import androidx.compose.foundation.Image
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.asImageBitmap
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.material3.FilterChip

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    onPdfSelected: (String) -> Unit,
    onNavigateToSettings: () -> Unit = {},
    onNavigateToControlOptions: () -> Unit = {},
    onNavigateToMiscOptions: () -> Unit = {},
    onNavigateToDocumentsOptions: () -> Unit = {},
    onNavigateToPdfOptions: () -> Unit = {},
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
    var mergeReorderList = remember { mutableStateListOf<Uri>() }
    var showMergeReorderDialog by remember { mutableStateOf(false) }
    var mergedFileUri by remember { mutableStateOf<Uri?>(null) }
    
    // Split PDF State
    var showSplitPreviewDialog by remember { mutableStateOf(false) }
    val selectedPagesToSplit = remember { mutableStateListOf<Int>() }
    val splitPageBitmaps = remember { mutableStateListOf<Bitmap?>() }
    var splitPageCount by remember { mutableStateOf(0) }
    var isSplitLoading by remember { mutableStateOf(false) }

    // Compress PDF State
    var showCompressDialog by remember { mutableStateOf(false) }
    var compressionPercentage by remember { mutableStateOf(50) }

    // Rotate PDF State
    var showRotateDialog by remember { mutableStateOf(false) }
    var rotateDegrees by remember { mutableStateOf(90) }
    val selectedPagesToRotate = remember { mutableStateListOf<Int>() }
    val rotatePageBitmaps = remember { mutableStateListOf<Bitmap?>() }
    var rotatePageCount by remember { mutableStateOf(0) }
    var isRotateLoading by remember { mutableStateOf(false) }

    // PDF to Image State
    var showPdfToImageDialog by remember { mutableStateOf(false) }
    val selectedPagesToImage = remember { mutableStateListOf<Int>() }
    val pdfToImagePageBitmaps = remember { mutableStateListOf<Bitmap?>() }
    var pdfToImagePageCount by remember { mutableStateOf(0) }
    var isPdfToImageLoading by remember { mutableStateOf(false) }

    // Images to PDF State
    val selectedImageUrisForPdf = remember { mutableStateListOf<Uri>() }
    var showImagesToPdfReorderDialog by remember { mutableStateOf(false) }
    val imagesToPdfReorderList = remember { mutableStateListOf<Uri>() }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var autoSaveLocationPrompt by remember { mutableStateOf(true) }
    var highResRendering by remember { mutableStateOf(true) }
    
    // Initialize PDFBox asynchronously to optimize cold startup speed
    val pdfTextService = remember { PdfTextService() }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            PdfTextService.init(context)
        }
    }

    LaunchedEffect(showSplitPreviewDialog, selectedInputUri) {
        if (showSplitPreviewDialog && selectedInputUri != null) {
            isSplitLoading = true
            splitPageBitmaps.clear()
            selectedPagesToSplit.clear()
            withContext(Dispatchers.IO) {
                try {
                    val pfd = if (selectedInputUri!!.scheme == "file") {
                        android.os.ParcelFileDescriptor.open(java.io.File(selectedInputUri!!.path ?: ""), android.os.ParcelFileDescriptor.MODE_READ_ONLY)
                    } else {
                        context.contentResolver.openFileDescriptor(selectedInputUri!!, "r")
                    }
                    if (pfd != null) {
                        val renderer = PdfRenderer(pfd)
                        val count = renderer.pageCount
                        splitPageCount = count
                        
                        // Default select all pages
                        for (i in 0 until count) {
                            selectedPagesToSplit.add(i)
                            splitPageBitmaps.add(null) // placeholder
                        }
                        
                        // Load small thumbnails
                        for (i in 0 until count) {
                            try {
                                val page = renderer.openPage(i)
                                // scale down to width of 200px for thumbnail preview
                                val targetWidth = 200
                                val targetHeight = (targetWidth * (page.height.toFloat() / page.width.toFloat())).toInt()
                                val bmp = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                                bmp.eraseColor(android.graphics.Color.WHITE)
                                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                page.close()
                                withContext(Dispatchers.Main) {
                                    splitPageBitmaps[i] = bmp
                                }
                            } catch (e: java.lang.Exception) {
                                android.util.Log.e("HomeScreen", "Error rendering thumbnail $i", e)
                            }
                        }
                        renderer.close()
                        pfd.close()
                    }
                } catch (e: java.lang.Exception) {
                    android.util.Log.e("HomeScreen", "Error loading pdf for split", e)
                } finally {
                    isSplitLoading = false
                }
            }
        }
    }

    LaunchedEffect(showRotateDialog, selectedInputUri) {
        if (showRotateDialog && selectedInputUri != null) {
            isRotateLoading = true
            rotatePageBitmaps.clear()
            selectedPagesToRotate.clear()
            withContext(Dispatchers.IO) {
                try {
                    val pfd = if (selectedInputUri!!.scheme == "file") {
                        android.os.ParcelFileDescriptor.open(java.io.File(selectedInputUri!!.path ?: ""), android.os.ParcelFileDescriptor.MODE_READ_ONLY)
                    } else {
                        context.contentResolver.openFileDescriptor(selectedInputUri!!, "r")
                    }
                    if (pfd != null) {
                        val renderer = PdfRenderer(pfd)
                        val count = renderer.pageCount
                        rotatePageCount = count
                        
                        // Default select all pages to rotate
                        for (i in 0 until count) {
                            selectedPagesToRotate.add(i)
                            rotatePageBitmaps.add(null) // placeholder
                        }
                        
                        // Load small thumbnails
                        for (i in 0 until count) {
                            try {
                                val page = renderer.openPage(i)
                                val targetWidth = 200
                                val targetHeight = (targetWidth * (page.height.toFloat() / page.width.toFloat())).toInt()
                                val bmp = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                                bmp.eraseColor(android.graphics.Color.WHITE)
                                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                page.close()
                                withContext(Dispatchers.Main) {
                                    rotatePageBitmaps[i] = bmp
                                }
                            } catch (e: java.lang.Exception) {
                                android.util.Log.e("HomeScreen", "Error rendering rotate thumbnail $i", e)
                            }
                        }
                        renderer.close()
                        pfd.close()
                    }
                } catch (e: java.lang.Exception) {
                    android.util.Log.e("HomeScreen", "Error loading pdf for rotate", e)
                } finally {
                    isRotateLoading = false
                }
            }
        }
    }

    LaunchedEffect(showPdfToImageDialog, selectedInputUri) {
        if (showPdfToImageDialog && selectedInputUri != null) {
            isPdfToImageLoading = true
            pdfToImagePageBitmaps.clear()
            selectedPagesToImage.clear()
            withContext(Dispatchers.IO) {
                try {
                    val pfd = if (selectedInputUri!!.scheme == "file") {
                        android.os.ParcelFileDescriptor.open(java.io.File(selectedInputUri!!.path ?: ""), android.os.ParcelFileDescriptor.MODE_READ_ONLY)
                    } else {
                        context.contentResolver.openFileDescriptor(selectedInputUri!!, "r")
                    }
                    if (pfd != null) {
                        val renderer = PdfRenderer(pfd)
                        val count = renderer.pageCount
                        pdfToImagePageCount = count
                        
                        // Default select all pages to convert
                        for (i in 0 until count) {
                            selectedPagesToImage.add(i)
                            pdfToImagePageBitmaps.add(null) // placeholder
                        }
                        
                        // Load small thumbnails
                        for (i in 0 until count) {
                            try {
                                val page = renderer.openPage(i)
                                val targetWidth = 200
                                val targetHeight = (targetWidth * (page.height.toFloat() / page.width.toFloat())).toInt()
                                val bmp = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                                bmp.eraseColor(android.graphics.Color.WHITE)
                                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                page.close()
                                withContext(Dispatchers.Main) {
                                    pdfToImagePageBitmaps[i] = bmp
                                }
                            } catch (e: java.lang.Exception) {
                                android.util.Log.e("HomeScreen", "Error rendering pdfToImage thumbnail $i", e)
                            }
                        }
                        renderer.close()
                        pfd.close()
                    }
                } catch (e: java.lang.Exception) {
                    android.util.Log.e("HomeScreen", "Error loading pdf for pdfToImage", e)
                } finally {
                    isPdfToImageLoading = false
                }
            }
        }
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
                                val openOutputStream = { uri: Uri ->
                                    if (uri.scheme == "file") {
                                        java.io.FileOutputStream(java.io.File(uri.path ?: ""))
                                    } else {
                                        context.contentResolver.openOutputStream(uri)
                                    }
                                }
                                val pfd = if (inputUri.scheme == "file") {
                                    android.os.ParcelFileDescriptor.open(java.io.File(inputUri.path ?: ""), android.os.ParcelFileDescriptor.MODE_READ_ONLY)
                                } else {
                                    context.contentResolver.openFileDescriptor(inputUri, "r")
                                }
                                if (pfd != null) {
                                    val renderer = PdfRenderer(pfd)
                                    val imageFolder = File(context.cacheDir, "pdf_pages_${System.currentTimeMillis()}")
                                    imageFolder.mkdirs()
                                    val imageFiles = mutableListOf<File>()
                                    for (i in 0 until renderer.pageCount) {
                                        if (i in selectedPagesToImage) {
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
                                    }
                                    renderer.close()
                                    pfd.close()
                                    
                                    processingMessage = "Saving high-res images archive..."
                                    openOutputStream(targetUri)?.use { outStream ->
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
                                var inputStream: java.io.InputStream? = null
                                var document: PDDocument? = null
                                try {
                                    inputStream = if (inputUri.scheme == "file") {
                                        java.io.FileInputStream(java.io.File(inputUri.path ?: ""))
                                    } else {
                                        context.contentResolver.openInputStream(inputUri)
                                    }
                                    if (inputStream != null) {
                                        PdfTextService.init(context)
                                        document = PDDocument.load(inputStream)
                                        val stripper = PDFTextStripper()
                                        val fullText = stripper.getText(document) ?: ""
                                        
                                        context.contentResolver.openOutputStream(targetUri)?.use { out ->
                                            out.write(fullText.toByteArray(Charsets.UTF_8))
                                        }
                                        processedFileUri = targetUri
                                        generatedMimeType = "text/plain"
                                    } else {
                                        throw java.io.FileNotFoundException("Could not open input stream")
                                    }
                                } catch (e: Exception) {
                                    android.util.Log.e("HomeScreen", "Error in PDF_TO_TEXT", e)
                                    throw e
                                } finally {
                                    try {
                                        document?.close()
                                        inputStream?.close()
                                    } catch (ex: Exception) {
                                        // Ignore
                                    }
                                }
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
                                val openInputStream = { uri: Uri ->
                                    if (uri.scheme == "file") {
                                        java.io.FileInputStream(java.io.File(uri.path ?: ""))
                                    } else {
                                        context.contentResolver.openInputStream(uri)
                                    }
                                }
                                val openOutputStream = { uri: Uri ->
                                    if (uri.scheme == "file") {
                                        java.io.FileOutputStream(java.io.File(uri.path ?: ""))
                                    } else {
                                        context.contentResolver.openOutputStream(uri)
                                    }
                                }

                                openInputStream(inputUri)?.use { input ->
                                    val document = com.tom_roush.pdfbox.pdmodel.PDDocument.load(input)
                                    
                                    // Compress images in the document based on target percentage
                                    val quality = (100 - compressionPercentage).coerceIn(5, 95)
                                    
                                    fun compressResources(resources: com.tom_roush.pdfbox.pdmodel.PDResources?) {
                                        if (resources == null) return
                                        for (name in resources.xObjectNames) {
                                            if (resources.isImageXObject(name)) {
                                                try {
                                                    val xobject = resources.getXObject(name)
                                                    if (xobject is com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject) {
                                                        val bmp = xobject.image
                                                        if (bmp != null) {
                                                            val compressedBao = java.io.ByteArrayOutputStream()
                                                            bmp.compress(Bitmap.CompressFormat.JPEG, quality, compressedBao)
                                                            val compressedBytes = compressedBao.toByteArray()
                                                            
                                                            // Create new image from stream
                                                            val newImageXObject = com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory.createFromStream(
                                                                document,
                                                                java.io.ByteArrayInputStream(compressedBytes)
                                                            )
                                                            resources.put(name, newImageXObject)
                                                        }
                                                    }
                                                } catch (e: Exception) {
                                                    android.util.Log.e("HomeScreen", "Error compressing image $name", e)
                                                }
                                            } else {
                                                try {
                                                    val xobject = resources.getXObject(name)
                                                    if (xobject is com.tom_roush.pdfbox.pdmodel.graphics.form.PDFormXObject) {
                                                        compressResources(xobject.resources)
                                                    }
                                                } catch (e: Exception) {
                                                    // Ignore non-form xobjects
                                                }
                                            }
                                        }
                                    }

                                    for (page in document.pages) {
                                        compressResources(page.resources)
                                    }

                                    openOutputStream(targetUri)?.use { output ->
                                        document.save(output)
                                    }
                                    document.close()
                                }
                                processedFileUri = targetUri
                                generatedMimeType = "application/pdf"
                            }
                        }
                        EditorTool.SPLIT_PDF -> {
                            processingMessage = "Extracting selected pages..."
                            withContext(Dispatchers.IO) {
                                val openInputStream = { uri: Uri ->
                                    if (uri.scheme == "file") {
                                        java.io.FileInputStream(java.io.File(uri.path ?: ""))
                                    } else {
                                        context.contentResolver.openInputStream(uri)
                                    }
                                }
                                val openOutputStream = { uri: Uri ->
                                    if (uri.scheme == "file") {
                                        java.io.FileOutputStream(java.io.File(uri.path ?: ""))
                                    } else {
                                        context.contentResolver.openOutputStream(uri)
                                    }
                                }

                                openInputStream(inputUri)?.use { input ->
                                    val document = com.tom_roush.pdfbox.pdmodel.PDDocument.load(input)
                                    val totalPages = document.numberOfPages
                                    // Remove pages in reverse order
                                    for (i in (totalPages - 1) downTo 0) {
                                        if (i !in selectedPagesToSplit) {
                                            document.removePage(i)
                                        }
                                    }
                                    openOutputStream(targetUri)?.use { output ->
                                        document.save(output)
                                    }
                                    document.close()
                                }
                                processedFileUri = targetUri
                                generatedMimeType = "application/pdf"
                            }
                        }
                        EditorTool.ROTATE_PDF -> {
                            processingMessage = "Adjusting page rotation..."
                            withContext(Dispatchers.IO) {
                                val openInputStream = { uri: Uri ->
                                    if (uri.scheme == "file") {
                                        java.io.FileInputStream(java.io.File(uri.path ?: ""))
                                    } else {
                                        context.contentResolver.openInputStream(uri)
                                    }
                                }
                                val openOutputStream = { uri: Uri ->
                                    if (uri.scheme == "file") {
                                        java.io.FileOutputStream(java.io.File(uri.path ?: ""))
                                    } else {
                                        context.contentResolver.openOutputStream(uri)
                                    }
                                }

                                openInputStream(inputUri)?.use { input ->
                                    val document = com.tom_roush.pdfbox.pdmodel.PDDocument.load(input)
                                    
                                    // Rotate only the selected pages by the selected degrees
                                    for (i in 0 until document.numberOfPages) {
                                        if (i in selectedPagesToRotate) {
                                            val page = document.getPage(i)
                                            val currentRotation = page.rotation
                                            page.rotation = (currentRotation + rotateDegrees) % 360
                                        }
                                    }
                                    
                                    openOutputStream(targetUri)?.use { output ->
                                        document.save(output)
                                    }
                                    document.close()
                                }
                                 processedFileUri = targetUri
                                generatedMimeType = "application/pdf"
                            }
                        }
                        EditorTool.ZIP_TO_PDF -> {
                            processingMessage = "Compiling zip images to PDF..."
                            withContext(Dispatchers.IO) {
                                val openInputStream = { uri: Uri ->
                                    if (uri.scheme == "file") {
                                        java.io.FileInputStream(java.io.File(uri.path ?: ""))
                                    } else {
                                        context.contentResolver.openInputStream(uri)
                                    }
                                }
                                val openOutputStream = { uri: Uri ->
                                    if (uri.scheme == "file") {
                                        java.io.FileOutputStream(java.io.File(uri.path ?: ""))
                                    } else {
                                        context.contentResolver.openOutputStream(uri)
                                    }
                                }

                                val document = com.tom_roush.pdfbox.pdmodel.PDDocument()
                                openInputStream(inputUri)?.use { zipInput ->
                                    java.util.zip.ZipInputStream(zipInput).use { zis ->
                                        var entry = zis.nextEntry
                                        while (entry != null) {
                                            if (!entry.isDirectory && (entry.name.lowercase().endsWith(".png") || entry.name.lowercase().endsWith(".jpg") || entry.name.lowercase().endsWith(".jpeg") || entry.name.lowercase().endsWith(".webp"))) {
                                                val bytes = zis.readBytes()
                                                val bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                                if (bitmap != null) {
                                                    val page = com.tom_roush.pdfbox.pdmodel.PDPage(com.tom_roush.pdfbox.pdmodel.common.PDRectangle(bitmap.width.toFloat(), bitmap.height.toFloat()))
                                                    document.addPage(page)
                                                    val pdImage = com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory.createFromImage(document, bitmap)
                                                    val contentStream = com.tom_roush.pdfbox.pdmodel.PDPageContentStream(document, page)
                                                    contentStream.drawImage(pdImage, 0f, 0f)
                                                    contentStream.close()
                                                }
                                            }
                                            entry = zis.nextEntry
                                        }
                                    }
                                }
                                openOutputStream(targetUri)?.use { output ->
                                    document.save(output)
                                }
                                document.close()
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
                    withContext(Dispatchers.IO) {
                        context.cacheDir.listFiles()?.forEach { file ->
                            if (file.name.startsWith("temp_editor_input_") ||
                                file.name.startsWith("temp_img_") ||
                                file.name.startsWith("temp_merge_")) {
                                try { file.delete() } catch (e: java.lang.Exception) {}
                            }
                        }
                    }
                }
            }
        }
    }

    val editorFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { inputUri ->
            val tool = activeTool ?: return@let
            isProcessing = true
            processingMessage = "Loading file..."
            scope.launch {
                try {
                    val originalName = getFileName(context, inputUri) ?: "document.pdf"
                    val baseName = originalName.substringBeforeLast(".")
                    val suggestedName = when (tool) {
                        EditorTool.PDF_TO_IMAGE -> "${baseName}_images.zip"
                        EditorTool.PDF_TO_TEXT -> "${baseName}.txt"
                        EditorTool.PDF_TO_WORD -> "${baseName}.doc"
                        EditorTool.COMPRESS_PDF -> "${baseName}_compressed.pdf"
                        EditorTool.SPLIT_PDF -> "${baseName}_split.pdf"
                        EditorTool.ROTATE_PDF -> "${baseName}_rotated.pdf"
                        EditorTool.ZIP_TO_PDF -> "${baseName}.pdf"
                        else -> "${baseName}_edited.pdf"
                    }

                    val cachedUri = withContext(Dispatchers.IO) {
                        val suffix = if (tool == EditorTool.ZIP_TO_PDF) ".zip" else ".pdf"
                        val tempFile = File(context.cacheDir, "temp_editor_input_${System.currentTimeMillis()}$suffix")
                        if (tempFile.exists()) tempFile.delete()
                        context.contentResolver.openInputStream(inputUri)?.use { input ->
                            tempFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                        val authority = "com.pdfsuny.app.fileprovider"
                        androidx.core.content.FileProvider.getUriForFile(context, authority, tempFile)
                    }

                    selectedInputUri = cachedUri

                    if (tool == EditorTool.SPLIT_PDF) {
                        showSplitPreviewDialog = true
                    } else if (tool == EditorTool.COMPRESS_PDF) {
                        showCompressDialog = true
                    } else if (tool == EditorTool.ROTATE_PDF) {
                        showRotateDialog = true
                    } else if (tool == EditorTool.PDF_TO_IMAGE) {
                        showPdfToImageDialog = true
                    } else {
                        saveEditorFileLauncher.launch(suggestedName)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("HomeScreen", "Error preparing input file", e)
                    Toast.makeText(context, "Failed to load file: ${e.message}", Toast.LENGTH_LONG).show()
                } finally {
                    isProcessing = false
                }
            }
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
                        try { stream.close() } catch (e: java.lang.Exception) {}
                    }
                    isProcessing = false
                    withContext(Dispatchers.IO) {
                        context.cacheDir.listFiles()?.forEach { file ->
                            if (file.name.startsWith("temp_editor_input_") ||
                                file.name.startsWith("temp_img_") ||
                                file.name.startsWith("temp_merge_")) {
                                try { file.delete() } catch (e: java.lang.Exception) {}
                            }
                        }
                    }
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
                isProcessing = true
                processingMessage = "Preparing PDF files..."
                scope.launch {
                    try {
                        val tempUris = withContext(Dispatchers.IO) {
                            uris.mapIndexed { index, uri ->
                                val tempFile = File(context.cacheDir, "temp_merge_${index}_${System.currentTimeMillis()}.pdf")
                                if (tempFile.exists()) tempFile.delete()
                                context.contentResolver.openInputStream(uri)?.use { input ->
                                    tempFile.outputStream().use { output ->
                                        input.copyTo(output)
                                    }
                                }
                                val authority = "com.pdfsuny.app.fileprovider"
                                androidx.core.content.FileProvider.getUriForFile(context, authority, tempFile)
                            }
                        }
                        mergeReorderList.clear()
                        mergeReorderList.addAll(tempUris)
                        showMergeReorderDialog = true
                    } catch (e: Exception) {
                        android.util.Log.e("HomeScreen", "Error preparing files for merge", e)
                        Toast.makeText(context, "Failed to load files: ${e.message}", Toast.LENGTH_LONG).show()
                    } finally {
                        isProcessing = false
                    }
                }
            }
        }
    }

    val saveImagesToPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri: Uri? ->
        uri?.let { destUri ->
            isProcessing = true
            processingMessage = "Compiling images to PDF..."
            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        val document = com.tom_roush.pdfbox.pdmodel.PDDocument()
                        val openInputStream = { u: Uri ->
                            if (u.scheme == "file") {
                                java.io.FileInputStream(java.io.File(u.path ?: ""))
                            } else {
                                context.contentResolver.openInputStream(u)
                            }
                        }
                        for (imgUri in selectedImageUrisForPdf) {
                            openInputStream(imgUri)?.use { stream ->
                                val bitmap = android.graphics.BitmapFactory.decodeStream(stream)
                                if (bitmap != null) {
                                    val page = com.tom_roush.pdfbox.pdmodel.PDPage(com.tom_roush.pdfbox.pdmodel.common.PDRectangle(bitmap.width.toFloat(), bitmap.height.toFloat()))
                                    document.addPage(page)
                                    val pdImage = com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory.createFromImage(document, bitmap)
                                    val contentStream = com.tom_roush.pdfbox.pdmodel.PDPageContentStream(document, page)
                                    contentStream.drawImage(pdImage, 0f, 0f)
                                    contentStream.close()
                                }
                            }
                        }
                        val openOutputStream = { u: Uri ->
                            if (u.scheme == "file") {
                                java.io.FileOutputStream(java.io.File(u.path ?: ""))
                            } else {
                                context.contentResolver.openOutputStream(u)
                            }
                        }
                        openOutputStream(destUri)?.use { out ->
                            document.save(out)
                        }
                        document.close()
                    }
                    processedFileUri = destUri
                    generatedMimeType = "application/pdf"
                    showSuccessDialog = true
                } catch (e: Exception) {
                    android.util.Log.e("HomeScreen", "Error compiling images to PDF", e)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Images compilation failed: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                } finally {
                    isProcessing = false
                    withContext(Dispatchers.IO) {
                        context.cacheDir.listFiles()?.forEach { file ->
                            if (file.name.startsWith("temp_editor_input_") ||
                                file.name.startsWith("temp_img_") ||
                                file.name.startsWith("temp_merge_")) {
                                try { file.delete() } catch (e: java.lang.Exception) {}
                            }
                        }
                    }
                }
            }
        }
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            isProcessing = true
            processingMessage = "Preparing images..."
            scope.launch {
                try {
                    val tempUris = withContext(Dispatchers.IO) {
                        uris.mapIndexed { index, uri ->
                            val tempFile = File(context.cacheDir, "temp_img_${index}_${System.currentTimeMillis()}.jpg")
                            if (tempFile.exists()) tempFile.delete()
                            context.contentResolver.openInputStream(uri)?.use { input ->
                                tempFile.outputStream().use { output ->
                                    input.copyTo(output)
                                }
                            }
                            val authority = "com.pdfsuny.app.fileprovider"
                            androidx.core.content.FileProvider.getUriForFile(context, authority, tempFile)
                        }
                    }
                    selectedImageUrisForPdf.clear()
                    selectedImageUrisForPdf.addAll(tempUris)
                    imagesToPdfReorderList.clear()
                    imagesToPdfReorderList.addAll(tempUris)
                    showImagesToPdfReorderDialog = true
                } catch (e: Exception) {
                    android.util.Log.e("HomeScreen", "Error preparing images", e)
                    Toast.makeText(context, "Failed to load images: ${e.message}", Toast.LENGTH_LONG).show()
                } finally {
                    isProcessing = false
                }
            }
        }
    }
    
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = Color(0xFF161320),
                modifier = Modifier.width(320.dp)
            ) {
                HomeScreenDrawerContent(
                    context = context,
                    scope = scope,
                    recentPdfs = recentPdfs,
                    onPdfSelected = onPdfSelected,
                    onNavigateToSettings = onNavigateToSettings,
                    onNavigateToControlOptions = onNavigateToControlOptions,
                    onNavigateToMiscOptions = onNavigateToMiscOptions,
                    onNavigateToDocumentsOptions = onNavigateToDocumentsOptions,
                    onNavigateToPdfOptions = onNavigateToPdfOptions,
                    onCloseDrawer = {
                        scope.launch { drawerState.close() }
                    }
                )
            }
        }
    ) {
        Scaffold(
            modifier = modifier,
            topBar = {
                TopAppBar(
                    navigationIcon = {
                        TooltipIconButton(
                            onClick = {
                                scope.launch {
                                    drawerState.open()
                                }
                            },
                            tooltipText = "Settings Menu"
                        ) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = "Open Settings",
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    },
                    title = {
                    Column {
                        Text(
                            text = when (currentTab) {
                                HomeTab.RECENTS -> "PDF Reader"
                                HomeTab.EDITOR -> "PDF Document Editor"
                            },
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = when (currentTab) {
                                HomeTab.RECENTS -> "Your offline document workspace"
                                HomeTab.EDITOR -> "Convert and export your files"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                ),
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
                    onClick = { 
                        filePickerLauncher.launch(
                            arrayOf(
                                "application/pdf", 
                                "text/plain", 
                                "text/markdown", 
                                "text/html", 
                                "text/*", 
                                "application/epub+zip",
                                "application/x-cbz",
                                "application/x-cbr",
                                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                                "application/vnd.oasis.opendocument.text",
                                "application/rtf",
                                "text/rtf",
                                "application/x-umd",
                                "application/x-chm"
                            )
                        ) 
                    },
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
                                    text = "Open File",
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
    
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Recent Documents",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    if (recentPdfs.isNotEmpty()) {
                        TextButton(
                            onClick = {
                                scope.launch {
                                    database.recentPdfDao().deleteAllRecentPdfs()
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "Clear All",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
    
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
                        icon = Icons.AutoMirrored.Filled.Article,
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
                        icon = Icons.AutoMirrored.Filled.CallMerge,
                        gradientColors = listOf(Color(0xFFE53935), Color(0xFFEF9A9A))
                    ),
                    EditorToolItem(
                        tool = EditorTool.SPLIT_PDF,
                        title = "Split PDF",
                        description = "Separate pages into single files",
                        icon = Icons.AutoMirrored.Filled.CallSplit,
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
                        icon = Icons.AutoMirrored.Filled.RotateRight,
                        gradientColors = listOf(Color(0xFFD81B60), Color(0xFFF48FB1))
                    ),
                    EditorToolItem(
                        tool = EditorTool.ZIP_TO_PDF,
                        title = "Zip to PDF",
                        description = "Compile images in zip to PDF",
                        icon = Icons.Default.Archive,
                        gradientColors = listOf(Color(0xFF8E24AA), Color(0xFFE1BEE7))
                    ),
                    EditorToolItem(
                        tool = EditorTool.IMAGES_TO_PDF,
                        title = "Images to PDF",
                        description = "Compile multiple images to PDF",
                        icon = Icons.Default.CropOriginal,
                        gradientColors = listOf(Color(0xFF00ACC1), Color(0xFFB2EBF2))
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
                                } else if (item.tool == EditorTool.IMAGES_TO_PDF) {
                                    activeTool = item.tool
                                    imagePickerLauncher.launch(arrayOf("image/*"))
                                } else if (item.tool == EditorTool.ZIP_TO_PDF) {
                                    activeTool = item.tool
                                    editorFilePickerLauncher.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream"))
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

    // Merge PDFs Reorder Dialog
    if (showMergeReorderDialog) {
        AlertDialog(
            onDismissRequest = { showMergeReorderDialog = false },
            title = { Text("Reorder PDFs to Merge", fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("Adjust sequence using Move Up / Down buttons:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                    Spacer(modifier = Modifier.height(12.dp))
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        itemsIndexed(mergeReorderList) { index, uri ->
                            val fileName = getFileName(context, uri) ?: "Document_${index + 1}.pdf"
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${index + 1}.",
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = fileName,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Row {
                                        TooltipIconButton(
                                            onClick = {
                                                if (index > 0) {
                                                    val item = mergeReorderList.removeAt(index)
                                                    mergeReorderList.add(index - 1, item)
                                                }
                                            },
                                            enabled = index > 0,
                                            tooltipText = "Move Up"
                                        ) {
                                            Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Move Up")
                                        }
                                        TooltipIconButton(
                                            onClick = {
                                                if (index < mergeReorderList.size - 1) {
                                                    val item = mergeReorderList.removeAt(index)
                                                    mergeReorderList.add(index + 1, item)
                                                }
                                            },
                                            enabled = index < mergeReorderList.size - 1,
                                            tooltipText = "Move Down"
                                        ) {
                                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Move Down")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (mergeReorderList.size < 2) {
                            Toast.makeText(context, "At least 2 files required to merge.", Toast.LENGTH_SHORT).show()
                        } else {
                            urisToMerge = mergeReorderList.toList()
                            showMergeReorderDialog = false
                            saveMergeFileLauncher.launch("merged_document.pdf")
                        }
                    }
                ) {
                    Text("Merge & Save")
                }
            },
        )
    }

    // Images to PDF Reorder Dialog
    if (showImagesToPdfReorderDialog) {
        AlertDialog(
            onDismissRequest = { showImagesToPdfReorderDialog = false },
            title = { Text("Arrange Images for PDF", fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("Arrange order or remove images before compiling:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                    Spacer(modifier = Modifier.height(12.dp))
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(imagesToPdfReorderList) { index, uri ->
                            val fileName = getFileName(context, uri) ?: "Image_${index + 1}.jpg"
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${index + 1}.",
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    ImageThumbnail(
                                        uri = uri,
                                        modifier = Modifier
                                            .size(50.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = fileName,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Row {
                                        TooltipIconButton(
                                            onClick = {
                                                if (index > 0) {
                                                    val item = imagesToPdfReorderList.removeAt(index)
                                                    imagesToPdfReorderList.add(index - 1, item)
                                                }
                                            },
                                            enabled = index > 0,
                                            tooltipText = "Move Up"
                                        ) {
                                            Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Move Up")
                                        }
                                        TooltipIconButton(
                                            onClick = {
                                                if (index < imagesToPdfReorderList.size - 1) {
                                                    val item = imagesToPdfReorderList.removeAt(index)
                                                    imagesToPdfReorderList.add(index + 1, item)
                                                }
                                            },
                                            enabled = index < imagesToPdfReorderList.size - 1,
                                            tooltipText = "Move Down"
                                        ) {
                                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Move Down")
                                        }
                                        TooltipIconButton(
                                            onClick = {
                                                imagesToPdfReorderList.removeAt(index)
                                            },
                                            tooltipText = "Remove"
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = "Remove", tint = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (imagesToPdfReorderList.isEmpty()) {
                            Toast.makeText(context, "No images selected.", Toast.LENGTH_SHORT).show()
                        } else {
                            selectedImageUrisForPdf.clear()
                            selectedImageUrisForPdf.addAll(imagesToPdfReorderList)
                            showImagesToPdfReorderDialog = false
                            saveImagesToPdfLauncher.launch("images_compiled.pdf")
                        }
                    }
                ) {
                    Text("Save to PDF")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showImagesToPdfReorderDialog = false }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    // Split Preview Dialog
    if (showSplitPreviewDialog && selectedInputUri != null) {
        val originalName = getFileName(context, selectedInputUri!!) ?: "document.pdf"
        AlertDialog(
            onDismissRequest = {
                showSplitPreviewDialog = false
                selectedInputUri = null
            },
            title = {
                Column {
                    Text("Select Pages to Split", fontWeight = FontWeight.Bold)
                    Text(
                        originalName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    if (isSplitLoading && splitPageCount == 0) {
                        Box(
                            modifier = Modifier.fillMaxWidth().height(200.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    } else {
                        // Select all / None buttons
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            TextButton(
                                onClick = {
                                    selectedPagesToSplit.clear()
                                    for (i in 0 until splitPageCount) {
                                        selectedPagesToSplit.add(i)
                                    }
                                }
                            ) {
                                Text("Select All")
                            }
                            TextButton(
                                onClick = {
                                    selectedPagesToSplit.clear()
                                }
                            ) {
                                Text("Deselect All")
                            }
                        }

                        Text(
                            text = "${selectedPagesToSplit.size} of $splitPageCount pages selected",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        // Grid of page thumbnails
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 300.dp)
                        ) {
                            items(splitPageCount) { index ->
                                val isSelected = selectedPagesToSplit.contains(index)
                                val thumbnail = splitPageBitmaps.getOrNull(index)

                                Card(
                                    onClick = {
                                        if (isSelected) {
                                            selectedPagesToSplit.remove(index)
                                        } else {
                                            selectedPagesToSplit.add(index)
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                                    ),
                                    border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.padding(6.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(80.dp)
                                                .background(Color.White, RoundedCornerShape(4.dp)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (thumbnail != null) {
                                                Image(
                                                    bitmap = thumbnail.asImageBitmap(),
                                                    contentDescription = "Page ${index + 1}",
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                            } else {
                                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Center
                                        ) {
                                            Checkbox(
                                                checked = isSelected,
                                                onCheckedChange = { checked ->
                                                    if (checked) {
                                                        if (!selectedPagesToSplit.contains(index)) selectedPagesToSplit.add(index)
                                                    } else {
                                                        selectedPagesToSplit.remove(index)
                                                    }
                                                },
                                                modifier = Modifier.scale(0.8f).size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "${index + 1}",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Bold
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
                Button(
                    onClick = {
                        if (selectedPagesToSplit.isEmpty()) {
                            Toast.makeText(context, "Please select at least 1 page to split.", Toast.LENGTH_SHORT).show()
                        } else {
                            val baseName = originalName.substringBeforeLast(".")
                            showSplitPreviewDialog = false
                            saveEditorFileLauncher.launch("${baseName}_split.pdf")
                        }
                    },
                    enabled = selectedPagesToSplit.isNotEmpty()
                ) {
                    Text("Split & Save")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showSplitPreviewDialog = false
                        selectedInputUri = null
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    // Compress Dialog
    if (showCompressDialog && selectedInputUri != null) {
        val originalName = getFileName(context, selectedInputUri!!) ?: "document.pdf"
        AlertDialog(
            onDismissRequest = {
                showCompressDialog = false
                selectedInputUri = null
            },
            title = {
                Text("Compress PDF", fontWeight = FontWeight.Bold)
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = originalName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    
                    Text(
                        text = "How much would you like to compress this PDF?",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Compression Ratio:", fontWeight = FontWeight.SemiBold)
                        Text(
                            text = "${compressionPercentage}%",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    
                    Slider(
                        value = compressionPercentage.toFloat(),
                        onValueChange = { compressionPercentage = it.toInt() },
                        valueRange = 10f..90f,
                        steps = 7, // 10%, 20%, 30%, 40%, 50%, 60%, 70%, 80%, 90%
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                    )
                    
                    val compressionText = when {
                        compressionPercentage <= 30 -> "Low Compression (Best Quality)"
                        compressionPercentage <= 60 -> "Medium Compression (Balanced)"
                        else -> "High Compression (Smallest File Size)"
                    }
                    
                    Text(
                        text = compressionText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 4.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val baseName = originalName.substringBeforeLast(".")
                        showCompressDialog = false
                        saveEditorFileLauncher.launch("${baseName}_compressed.pdf")
                    }
                ) {
                    Text("Compress & Save")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showCompressDialog = false
                        selectedInputUri = null
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    // Rotate Preview & Angle Dialog
    if (showRotateDialog && selectedInputUri != null) {
        val originalName = getFileName(context, selectedInputUri!!) ?: "document.pdf"
        AlertDialog(
            onDismissRequest = {
                showRotateDialog = false
                selectedInputUri = null
            },
            title = {
                Column {
                    Text("Rotate PDF Pages", fontWeight = FontWeight.Bold)
                    Text(
                        originalName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Option to choose rotation angle
                    Text("Select Rotation Angle:", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(90, 180, 270).forEach { angle ->
                            val label = when (angle) {
                                90 -> "90° CW"
                                180 -> "180°"
                                270 -> "270° CW"
                                else -> "$angle°"
                            }
                            FilterChip(
                                selected = rotateDegrees == angle,
                                onClick = { rotateDegrees = angle },
                                label = { Text(label) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    if (isRotateLoading && rotatePageCount == 0) {
                        Box(
                            modifier = Modifier.fillMaxWidth().height(150.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    } else {
                        // Select all / None buttons
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            TextButton(
                                onClick = {
                                    selectedPagesToRotate.clear()
                                    for (i in 0 until rotatePageCount) {
                                        selectedPagesToRotate.add(i)
                                    }
                                }
                            ) {
                                Text("Select All")
                            }
                            TextButton(
                                onClick = {
                                    selectedPagesToRotate.clear()
                                }
                            ) {
                                Text("Deselect All")
                            }
                        }

                        Text(
                            text = "${selectedPagesToRotate.size} of $rotatePageCount pages selected to rotate",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        // Grid of page thumbnails
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 240.dp)
                        ) {
                            items(rotatePageCount) { index ->
                                val isSelected = selectedPagesToRotate.contains(index)
                                val thumbnail = rotatePageBitmaps.getOrNull(index)

                                Card(
                                    onClick = {
                                        if (isSelected) {
                                            selectedPagesToRotate.remove(index)
                                        } else {
                                            selectedPagesToRotate.add(index)
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                                    ),
                                    border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.padding(6.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(80.dp)
                                                .background(Color.White, RoundedCornerShape(4.dp)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (thumbnail != null) {
                                                // Preview image rotated by the selected angle if selected
                                                Image(
                                                    bitmap = thumbnail.asImageBitmap(),
                                                    contentDescription = "Page ${index + 1}",
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .scale(if (isSelected && rotateDegrees % 180 != 0) 0.7f else 1f) // Scale down to fit inside box when rotated 90/270 degrees
                                                        .drawWithContent {
                                                            if (isSelected) {
                                                                // Draw with rotation angle applied to visual thumbnail!
                                                                drawContext.transform.rotate(rotateDegrees.toFloat(), center)
                                                            }
                                                            drawContent()
                                                        }
                                                )
                                            } else {
                                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Center
                                        ) {
                                            Checkbox(
                                                checked = isSelected,
                                                onCheckedChange = { checked ->
                                                    if (checked) {
                                                        if (!selectedPagesToRotate.contains(index)) selectedPagesToRotate.add(index)
                                                    } else {
                                                        selectedPagesToRotate.remove(index)
                                                    }
                                                },
                                                modifier = Modifier.scale(0.8f).size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "${index + 1}",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Bold
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
                Button(
                    onClick = {
                        if (selectedPagesToRotate.isEmpty()) {
                            Toast.makeText(context, "Please select at least 1 page to rotate.", Toast.LENGTH_SHORT).show()
                        } else {
                            val baseName = originalName.substringBeforeLast(".")
                            showRotateDialog = false
                            saveEditorFileLauncher.launch("${baseName}_rotated.pdf")
                        }
                    },
                    enabled = selectedPagesToRotate.isNotEmpty()
                ) {
                    Text("Rotate & Save")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showRotateDialog = false
                        selectedInputUri = null
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    // PDF to Image Preview & Selection Dialog
    if (showPdfToImageDialog && selectedInputUri != null) {
        val originalName = getFileName(context, selectedInputUri!!) ?: "document.pdf"
        AlertDialog(
            onDismissRequest = {
                showPdfToImageDialog = false
                selectedInputUri = null
            },
            title = {
                Column {
                    Text("PDF to Images", fontWeight = FontWeight.Bold)
                    Text(
                        originalName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Select which pages you want to convert to images:",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    if (isPdfToImageLoading && pdfToImagePageCount == 0) {
                        Box(
                            modifier = Modifier.fillMaxWidth().height(150.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    } else {
                        // Select all / None buttons
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            TextButton(
                                onClick = {
                                    selectedPagesToImage.clear()
                                    for (i in 0 until pdfToImagePageCount) {
                                        selectedPagesToImage.add(i)
                                    }
                                }
                            ) {
                                Text("Select All")
                            }
                            TextButton(
                                onClick = {
                                    selectedPagesToImage.clear()
                                }
                            ) {
                                Text("Deselect All")
                            }
                        }

                        Text(
                            text = "${selectedPagesToImage.size} of $pdfToImagePageCount pages selected to convert",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        // Grid of page thumbnails
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 240.dp)
                        ) {
                            items(pdfToImagePageCount) { index ->
                                val isSelected = selectedPagesToImage.contains(index)
                                val thumbnail = pdfToImagePageBitmaps.getOrNull(index)

                                Card(
                                    onClick = {
                                        if (isSelected) {
                                            selectedPagesToImage.remove(index)
                                        } else {
                                            selectedPagesToImage.add(index)
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                                    ),
                                    border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.padding(6.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(80.dp)
                                                .background(Color.White, RoundedCornerShape(4.dp)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (thumbnail != null) {
                                                Image(
                                                    bitmap = thumbnail.asImageBitmap(),
                                                    contentDescription = "Page ${index + 1}",
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                            } else {
                                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Center
                                        ) {
                                            Checkbox(
                                                checked = isSelected,
                                                onCheckedChange = { checked ->
                                                    if (checked) {
                                                        if (!selectedPagesToImage.contains(index)) selectedPagesToImage.add(index)
                                                    } else {
                                                        selectedPagesToImage.remove(index)
                                                    }
                                                },
                                                modifier = Modifier.scale(0.8f).size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "${index + 1}",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Bold
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
                Button(
                    onClick = {
                        if (selectedPagesToImage.isEmpty()) {
                            Toast.makeText(context, "Please select at least 1 page to convert.", Toast.LENGTH_SHORT).show()
                        } else {
                            val baseName = originalName.substringBeforeLast(".")
                            showPdfToImageDialog = false
                            saveEditorFileLauncher.launch("${baseName}_images.zip")
                        }
                    },
                    enabled = selectedPagesToImage.isNotEmpty()
                ) {
                    Text("Convert & Save")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showPdfToImageDialog = false
                        selectedInputUri = null
                    }
                ) {
                    Text("Cancel")
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
            TooltipIconButton(
                onClick = onDelete,
                tooltipText = "Delete from History"
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
        val uri = FileProvider.getUriForFile(context, "com.pdfsuny.app.fileprovider", file)
        openUri(context, uri, mimeType)
    } catch (e: Exception) {
        Toast.makeText(context, "No app found to open this file", Toast.LENGTH_SHORT).show()
    }
}

private fun shareFile(context: Context, file: File, mimeType: String) {
    try {
        val uri = FileProvider.getUriForFile(context, "com.pdfsuny.app.fileprovider", file)
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
    ROTATE_PDF,
    ZIP_TO_PDF,
    IMAGES_TO_PDF
}

data class EditorToolItem(
    val tool: EditorTool,
    val title: String,
    val description: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val gradientColors: List<Color>,
    val badge: String? = null
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreenDrawerContent(
    context: Context,
    scope: kotlinx.coroutines.CoroutineScope,
    recentPdfs: List<com.pdfsuny.app.data.entities.RecentPdf>,
    onPdfSelected: (String) -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToControlOptions: () -> Unit,
    onNavigateToMiscOptions: () -> Unit,
    onNavigateToDocumentsOptions: () -> Unit,
    onNavigateToPdfOptions: () -> Unit,
    onCloseDrawer: () -> Unit
) {
    val sharedPrefs = remember { context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE) }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF161320)) // Dark aesthetic background
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Title block
        Text(
            text = "Settings & Options",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        HorizontalDivider(color = Color(0xFF2C273F), modifier = Modifier.padding(bottom = 8.dp))
        
        // Helper function for quick recent launching with an action flag
        fun launchWithFlag(flagKey: String) {
            if (recentPdfs.isNotEmpty()) {
                sharedPrefs.edit().putBoolean(flagKey, true).apply()
                val targetPath = recentPdfs.first().path
                onCloseDrawer()
                onPdfSelected(targetPath)
                Toast.makeText(context, "Opening recent: ${recentPdfs.first().name}", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Please open a PDF file from the Home workspace first.", Toast.LENGTH_LONG).show()
            }
        }

        // --- Top-Level Option Buttons ---
        DrawerActionItem(
            icon = Icons.Default.Description,
            label = "Documents Options",
            onClick = {
                onCloseDrawer()
                onNavigateToDocumentsOptions()
            }
        )

        DrawerActionItem(
            icon = Icons.Default.PictureAsPdf,
            label = "PDF Options",
            onClick = {
                onCloseDrawer()
                onNavigateToPdfOptions()
            }
        )

        DrawerActionItem(
            icon = Icons.Default.Tune,
            label = "Control Options",
            onClick = {
                onCloseDrawer()
                onNavigateToControlOptions()
            }
        )

        DrawerActionItem(
            icon = Icons.Default.Settings,
            label = "Misc Options",
            onClick = {
                onCloseDrawer()
                onNavigateToMiscOptions()
            }
        )

        DrawerActionItem(
            icon = Icons.Default.Build,
            label = "Customize Reader Bar",
            onClick = {
                launchWithFlag("customize_bar_on_launch")
            }
        )

        HorizontalDivider(color = Color(0xFF2C273F), modifier = Modifier.padding(vertical = 12.dp))

        DrawerActionItem(
            icon = Icons.Default.Settings,
            label = "Settings",
            onClick = {
                onCloseDrawer()
                onNavigateToSettings()
            }
        )
        DrawerActionItem(
            icon = Icons.AutoMirrored.Filled.ExitToApp,
            label = "Exit",
            onClick = {
                onCloseDrawer()
                (context as? android.app.Activity)?.finish()
            }
        )
    }
}

@Composable
fun DrawerActionItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(vertical = 10.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color(0xFFC5C0DB),
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = label,
            color = Color.White,
            fontSize = 14.sp
        )
    }
}

@Composable
fun ImageThumbnail(uri: Uri, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var bitmap by remember(uri) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(uri) {
        withContext(Dispatchers.IO) {
            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val options = android.graphics.BitmapFactory.Options().apply {
                        inSampleSize = 4 // Decode at 1/4 size to save memory and be fast
                    }
                    bitmap = android.graphics.BitmapFactory.decodeStream(stream, null, options)
                }
            } catch (e: Exception) {
                // Ignore
            }
        }
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap!!.asImageBitmap(),
            contentDescription = null,
            modifier = modifier,
            contentScale = androidx.compose.ui.layout.ContentScale.Crop
        )
    } else {
        Box(
            modifier = modifier.background(Color.Gray),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Image, contentDescription = null, tint = Color.LightGray)
        }
    }
}

@Composable
fun DrawerSelectableItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(vertical = 10.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) Color(0xFF9E92FF) else Color(0xFFC5C0DB),
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = label,
                color = if (selected) Color(0xFF9E92FF) else Color.White,
                fontSize = 14.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
            )
        }
        
        Box(
            modifier = Modifier
                .size(16.dp)
                .clip(CircleShape)
                .background(if (selected) Color(0xFF9E92FF) else Color.Transparent)
                .border(1.5.dp, if (selected) Color(0xFF9E92FF) else Color(0xFFC5C0DB), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF161320))
                )
            }
        }
    }
}
