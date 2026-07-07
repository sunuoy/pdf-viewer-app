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
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Copyright
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Check
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.text.TextStyle

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
    onNavigateToArrangeImages: (List<String>) -> Unit = {},
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

    // Add Password State
    var showAddPasswordDialog by remember { mutableStateOf(false) }
    var addPasswordInput by remember { mutableStateOf("") }
    var addPasswordVisible by remember { mutableStateOf(false) }

    // Remove Password State
    var showRemovePasswordDialog by remember { mutableStateOf(false) }
    var removePasswordInput by remember { mutableStateOf("") }
    var removePasswordVisible by remember { mutableStateOf(false) }
    var removePasswordError by remember { mutableStateOf<String?>(null) }

    // Add Watermark State
    var showAddWatermarkDialog by remember { mutableStateOf(false) }
    var watermarkText by remember { mutableStateOf("CONFIDENTIAL") }
    var watermarkFontSize by remember { mutableStateOf(45f) }
    var watermarkRotation by remember { mutableStateOf(45f) }
    var watermarkColorStr by remember { mutableStateOf("gray") }
    var watermarkOpacity by remember { mutableStateOf(0.3f) }

    // Signature State
    var showSignatureDialog by remember { mutableStateOf(false) }
    val signaturePaths = remember { mutableStateListOf<List<Offset>>() }
    var signaturePageOption by remember { mutableStateOf("last") }
    var signatureAlignment by remember { mutableStateOf("bottom_right") }

    // Stamp State
    var showStampDialog by remember { mutableStateOf(false) }
    var stampText by remember { mutableStateOf("APPROVED") }
    var stampTypeTab by remember { mutableStateOf(0) }
    var stampColorStr by remember { mutableStateOf("red") }
    var stampAlignment by remember { mutableStateOf("center") }
    var stampPageOption by remember { mutableStateOf("first") }
    var importedStampUri by remember { mutableStateOf<Uri?>(null) }


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
    
    val stampImagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        importedStampUri = uri
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
                                                    bitmap.recycle()
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
                        EditorTool.ADD_PASSWORD -> {
                            processingMessage = "Securing PDF with password..."
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
                                    val ap = com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission()
                                    val spp = com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy(
                                        addPasswordInput,
                                        addPasswordInput,
                                        ap
                                    )
                                    spp.encryptionKeyLength = 128
                                    spp.permissions = ap
                                    document.protect(spp)
                                    
                                    openOutputStream(targetUri)?.use { output ->
                                        document.save(output)
                                    }
                                    document.close()
                                }
                                processedFileUri = targetUri
                                generatedMimeType = "application/pdf"
                            }
                        }
                        EditorTool.REMOVE_PASSWORD -> {
                            processingMessage = "Removing password from PDF..."
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
                                    val document = com.tom_roush.pdfbox.pdmodel.PDDocument.load(input, removePasswordInput)
                                    document.setAllSecurityToBeRemoved(true)
                                    openOutputStream(targetUri)?.use { output ->
                                        document.save(output)
                                    }
                                    document.close()
                                }
                                processedFileUri = targetUri
                                generatedMimeType = "application/pdf"
                            }
                        }
                        EditorTool.ADD_WATERMARK -> {
                            processingMessage = "Adding watermark to PDF..."
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
                                    
                                    val color = when (watermarkColorStr) {
                                        "red" -> android.graphics.Color.RED
                                        "blue" -> android.graphics.Color.BLUE
                                        "green" -> android.graphics.Color.GREEN
                                        else -> android.graphics.Color.GRAY
                                    }
                                    
                                    val r = android.graphics.Color.red(color) / 255f
                                    val g = android.graphics.Color.green(color) / 255f
                                    val b = android.graphics.Color.blue(color) / 255f
                                    
                                    for (page in document.pages) {
                                        val contentStream = com.tom_roush.pdfbox.pdmodel.PDPageContentStream(
                                            document,
                                            page,
                                            com.tom_roush.pdfbox.pdmodel.PDPageContentStream.AppendMode.APPEND,
                                            true,
                                            true
                                        )
                                        
                                        try {
                                             val extGState = com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState()
                                             extGState.nonStrokingAlphaConstant = watermarkOpacity
                                             contentStream.setGraphicsStateParameters(extGState)
                                         } catch (e: Exception) {
                                             // Fallback
                                         }
                                        
                                        contentStream.beginText()
                                        contentStream.setFont(com.tom_roush.pdfbox.pdmodel.font.PDType1Font.HELVETICA_BOLD, watermarkFontSize)
                                        contentStream.setNonStrokingColor(r, g, b)
                                        
                                        val mediaBox = page.mediaBox
                                        val x = mediaBox.width / 2f
                                        val y = mediaBox.height / 2f
                                        
                                        val matrix = com.tom_roush.pdfbox.util.Matrix.getRotateInstance(
                                            Math.toRadians(watermarkRotation.toDouble()),
                                            x,
                                            y
                                        )
                                        contentStream.setTextMatrix(matrix)
                                        
                                        contentStream.showText(watermarkText)
                                        contentStream.endText()
                                        contentStream.close()
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
                        EditorTool.ADD_SIGNATURE -> {
                            processingMessage = "Applying signature to PDF..."
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
                                
                                val sigWidth = 400
                                val sigHeight = 150
                                val bitmap = android.graphics.Bitmap.createBitmap(sigWidth, sigHeight, android.graphics.Bitmap.Config.ARGB_8888)
                                val canvas = android.graphics.Canvas(bitmap)
                                bitmap.eraseColor(android.graphics.Color.TRANSPARENT)
                                
                                val paint = android.graphics.Paint().apply {
                                    color = android.graphics.Color.BLACK
                                    strokeWidth = 6f
                                    style = android.graphics.Paint.Style.STROKE
                                    isAntiAlias = true
                                    strokeCap = android.graphics.Paint.Cap.ROUND
                                    strokeJoin = android.graphics.Paint.Join.ROUND
                                }
                                
                                if (signaturePaths.isNotEmpty()) {
                                    var minX = Float.MAX_VALUE
                                    var maxX = Float.MIN_VALUE
                                    var minY = Float.MAX_VALUE
                                    var maxY = Float.MIN_VALUE
                                    signaturePaths.forEach { path ->
                                        path.forEach { pt ->
                                            if (pt.x < minX) minX = pt.x
                                            if (pt.x > maxX) maxX = pt.x
                                            if (pt.y < minY) minY = pt.y
                                            if (pt.y > maxY) maxY = pt.y
                                        }
                                    }
                                    
                                    val pathW = maxX - minX
                                    val pathH = maxY - minY
                                    if (pathW > 0f && pathH > 0f) {
                                        val scaleX = 360f / pathW
                                        val scaleY = 120f / pathH
                                        val scale = Math.min(scaleX, scaleY)
                                        
                                        val offsetX = 20f - minX * scale + (360f - pathW * scale) / 2f
                                        val offsetY = 20f - minY * scale + (120f - pathH * scale) / 2f
                                        
                                        signaturePaths.forEach { path ->
                                            if (path.size > 1) {
                                                val androidPath = android.graphics.Path()
                                                androidPath.moveTo(path[0].x * scale + offsetX, path[0].y * scale + offsetY)
                                                for (i in 1 until path.size) {
                                                    androidPath.lineTo(path[i].x * scale + offsetX, path[i].y * scale + offsetY)
                                                }
                                                canvas.drawPath(androidPath, paint)
                                            }
                                        }
                                    }
                                }
                                
                                openInputStream(inputUri)?.use { input ->
                                    val document = com.tom_roush.pdfbox.pdmodel.PDDocument.load(input)
                                    val pdImage = com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory.createFromImage(document, bitmap)
                                    
                                    val totalPages = document.numberOfPages
                                    val targetIndices = when (signaturePageOption) {
                                        "first" -> listOf(0)
                                        "all" -> (0 until totalPages).toList()
                                        else -> listOf(totalPages - 1)
                                    }
                                    
                                    for (idx in targetIndices) {
                                        if (idx in 0 until totalPages) {
                                            val page = document.getPage(idx)
                                            val mediaBox = page.mediaBox
                                            
                                            val w = 120f
                                            val h = 45f
                                            val margin = 30f
                                            
                                            val x = when (signatureAlignment) {
                                                "bottom_left" -> margin
                                                "bottom_center" -> (mediaBox.width - w) / 2f
                                                else -> mediaBox.width - w - margin
                                            }
                                            val y = margin
                                            
                                            val contentStream = com.tom_roush.pdfbox.pdmodel.PDPageContentStream(
                                                document,
                                                page,
                                                com.tom_roush.pdfbox.pdmodel.PDPageContentStream.AppendMode.APPEND,
                                                true,
                                                true
                                            )
                                            contentStream.drawImage(pdImage, x, y, w, h)
                                            contentStream.close()
                                        }
                                    }
                                    
                                    openOutputStream(targetUri)?.use { output ->
                                        document.save(output)
                                    }
                                    document.close()
                                }
                                bitmap.recycle()
                                processedFileUri = targetUri
                                generatedMimeType = "application/pdf"
                            }
                        }
                        EditorTool.ADD_STAMP -> {
                            processingMessage = "Applying stamp to PDF..."
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
                                
                                val stampWidth = 240
                                val stampHeight = 100
                                val bitmap = android.graphics.Bitmap.createBitmap(stampWidth, stampHeight, android.graphics.Bitmap.Config.ARGB_8888)
                                val canvas = android.graphics.Canvas(bitmap)
                                bitmap.eraseColor(android.graphics.Color.TRANSPARENT)
                                
                                if (stampTypeTab == 1 && importedStampUri != null) {
                                    try {
                                        context.contentResolver.openInputStream(importedStampUri!!)?.use { imgIn ->
                                            val options = android.graphics.BitmapFactory.Options().apply {
                                                inJustDecodeBounds = false
                                            }
                                            val loadedBmp = android.graphics.BitmapFactory.decodeStream(imgIn, null, options)
                                            if (loadedBmp != null) {
                                                val srcW = loadedBmp.width
                                                val srcH = loadedBmp.height
                                                val scaleX = 240f / srcW
                                                val scaleY = 100f / srcH
                                                val scale = Math.min(scaleX, scaleY)
                                                val destW = (srcW * scale).toInt()
                                                val destH = (srcH * scale).toInt()
                                                val scaledBmp = android.graphics.Bitmap.createScaledBitmap(loadedBmp, destW, destH, true)
                                                
                                                canvas.drawBitmap(
                                                    scaledBmp,
                                                    (240 - destW) / 2f,
                                                    (100 - destH) / 2f,
                                                    null
                                                )
                                                scaledBmp.recycle()
                                                loadedBmp.recycle()
                                            }
                                        }
                                    } catch (e: Exception) {
                                        android.util.Log.e("HomeScreen", "Error loading imported stamp image", e)
                                    }
                                } else {
                                    val color = when (stampColorStr) {
                                        "blue" -> android.graphics.Color.BLUE
                                        "green" -> android.graphics.Color.rgb(46, 125, 50)
                                        else -> android.graphics.Color.RED
                                    }
                                    
                                    val strokePaint = android.graphics.Paint().apply {
                                        this.color = color
                                        style = android.graphics.Paint.Style.STROKE
                                        strokeWidth = 6f
                                        isAntiAlias = true
                                    }
                                    
                                    val fillPaint = android.graphics.Paint().apply {
                                        this.color = color
                                        alpha = 25
                                        style = android.graphics.Paint.Style.FILL
                                    }
                                    
                                    val rect = android.graphics.RectF(10f, 10f, 230f, 90f)
                                    canvas.drawRoundRect(rect, 15f, 15f, fillPaint)
                                    canvas.drawRoundRect(rect, 15f, 15f, strokePaint)
                                    
                                    val textPaint = android.graphics.Paint().apply {
                                        this.color = color
                                        textSize = 28f
                                        isAntiAlias = true
                                        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
                                        textAlign = android.graphics.Paint.Align.CENTER
                                    }
                                    
                                    val textY = 50f - (textPaint.descent() + textPaint.ascent()) / 2f
                                    canvas.drawText(stampText.uppercase(), 120f, textY, textPaint)
                                }
                                
                                openInputStream(inputUri)?.use { input ->
                                    val document = com.tom_roush.pdfbox.pdmodel.PDDocument.load(input)
                                    val pdImage = com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory.createFromImage(document, bitmap)
                                    
                                    val totalPages = document.numberOfPages
                                    val targetIndices = when (stampPageOption) {
                                        "last" -> listOf(totalPages - 1)
                                        "all" -> (0 until totalPages).toList()
                                        else -> listOf(0)
                                    }
                                    
                                    for (idx in targetIndices) {
                                        if (idx in 0 until totalPages) {
                                            val page = document.getPage(idx)
                                            val mediaBox = page.mediaBox
                                            
                                            val w = 140f
                                            val h = 58f
                                            val margin = 40f
                                            
                                            val x = when (stampAlignment) {
                                                "bottom_left" -> margin
                                                "bottom_right" -> mediaBox.width - w - margin
                                                "top_left" -> margin
                                                "top_right" -> mediaBox.width - w - margin
                                                else -> (mediaBox.width - w) / 2f
                                            }
                                            val y = when (stampAlignment) {
                                                "top_left", "top_right" -> mediaBox.height - h - margin
                                                "bottom_left", "bottom_right" -> margin
                                                else -> (mediaBox.height - h) / 2f
                                            }
                                            
                                            val contentStream = com.tom_roush.pdfbox.pdmodel.PDPageContentStream(
                                                document,
                                                page,
                                                com.tom_roush.pdfbox.pdmodel.PDPageContentStream.AppendMode.APPEND,
                                                true,
                                                true
                                            )
                                            contentStream.drawImage(pdImage, x, y, w, h)
                                            contentStream.close()
                                        }
                                    }
                                    
                                    openOutputStream(targetUri)?.use { output ->
                                        document.save(output)
                                    }
                                    document.close()
                                }
                                bitmap.recycle()
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
                    } else if (tool == EditorTool.ADD_PASSWORD) {
                        addPasswordInput = ""
                        showAddPasswordDialog = true
                    } else if (tool == EditorTool.ADD_WATERMARK) {
                        watermarkText = "CONFIDENTIAL"
                        showAddWatermarkDialog = true
                    } else if (tool == EditorTool.ADD_SIGNATURE) {
                        signaturePaths.clear()
                        showSignatureDialog = true
                    } else if (tool == EditorTool.ADD_STAMP) {
                        importedStampUri = null
                        showStampDialog = true
                    } else if (tool == EditorTool.REMOVE_PASSWORD) {
                        isProcessing = true
                        processingMessage = "Checking PDF encryption status..."
                        val isEncrypted = withContext(Dispatchers.IO) {
                            var encrypted = false
                            try {
                                val tempFile = File(context.cacheDir, "temp_check_${System.currentTimeMillis()}.pdf")
                                context.contentResolver.openInputStream(inputUri)?.use { input ->
                                    tempFile.outputStream().use { output ->
                                        input.copyTo(output)
                                    }
                                }
                                try {
                                    com.tom_roush.pdfbox.pdmodel.PDDocument.load(tempFile).close()
                                    encrypted = false
                                } catch (e: Exception) {
                                    if (e.javaClass.simpleName == "InvalidPasswordException" || 
                                        e.message?.contains("password", ignoreCase = true) == true || 
                                        e.message?.contains("encrypted", ignoreCase = true) == true) {
                                        encrypted = true
                                    }
                                } finally {
                                    tempFile.delete()
                                }
                            } catch (e: Exception) {
                                // Ignore
                            }
                            encrypted
                        }
                        isProcessing = false
                        if (isEncrypted) {
                            removePasswordInput = ""
                            removePasswordError = null
                            showRemovePasswordDialog = true
                        } else {
                            selectedInputUri = null
                            Toast.makeText(context, "This PDF is not password protected!", Toast.LENGTH_LONG).show()
                        }
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
                    onNavigateToArrangeImages(tempUris.map { it.toString() })
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
                    text = "Document Tools",
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
                    ),
                    EditorToolItem(
                        tool = EditorTool.ADD_PASSWORD,
                        title = "Add Password",
                        description = "Secure document with encryption",
                        icon = Icons.Default.Lock,
                        gradientColors = listOf(Color(0xFFD84315), Color(0xFFFF8A65))
                    ),
                    EditorToolItem(
                        tool = EditorTool.REMOVE_PASSWORD,
                        title = "Remove Password",
                        description = "Decrypt and unlock document",
                        icon = Icons.Default.LockOpen,
                        gradientColors = listOf(Color(0xFF2E7D32), Color(0xFF81C784))
                    ),
                    EditorToolItem(
                        tool = EditorTool.ADD_WATERMARK,
                        title = "Add Watermark",
                        description = "Overlay custom text watermark",
                        icon = Icons.Default.Copyright,
                        gradientColors = listOf(Color(0xFF6A1B9A), Color(0xFFBA68C8))
                    ),
                    EditorToolItem(
                        tool = EditorTool.ADD_SIGNATURE,
                        title = "Signature",
                        description = "Add handwritten digital signature",
                        icon = Icons.Default.Edit,
                        gradientColors = listOf(Color(0xFF004D40), Color(0xFF00BFA5))
                    ),
                    EditorToolItem(
                        tool = EditorTool.ADD_STAMP,
                        title = "Stamp",
                        description = "Add custom stamp approval or logo",
                        icon = Icons.Default.Check,
                        gradientColors = listOf(Color(0xFF880E4F), Color(0xFFFF4081))
                    )
                )

                
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(tools) { item ->
                        Card(
                            onClick = {
                                if (item.tool == EditorTool.MERGE_PDFS) {
                                    mergePickerLauncher.launch(arrayOf("application/pdf"))
                                } else if (item.tool == EditorTool.IMAGES_TO_PDF) {
                                    onNavigateToArrangeImages(emptyList())
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
                                .height(130.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Surface(
                                        modifier = Modifier.size(36.dp),
                                        shape = RoundedCornerShape(10.dp),
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
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                    
                                    Column {
                                        Text(
                                            text = item.title,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = item.description,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 2,
                                            fontSize = 11.sp,
                                            lineHeight = 14.sp,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                
                                item.badge?.let { badgeText ->
                                    Surface(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(top = 6.dp, end = 6.dp),
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.primary
                                    ) {
                                        Text(
                                            text = badgeText,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                            fontSize = 8.sp
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

    // Add Password Dialog
    if (showAddPasswordDialog && selectedInputUri != null) {
        val originalName = getFileName(context, selectedInputUri!!) ?: "document.pdf"
        AlertDialog(
            onDismissRequest = {
                showAddPasswordDialog = false
                selectedInputUri = null
            },
            title = {
                Text("Add Password Protection", fontWeight = FontWeight.Bold)
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
                    OutlinedTextField(
                        value = addPasswordInput,
                        onValueChange = { addPasswordInput = it },
                        label = { Text("Password") },
                        visualTransformation = if (addPasswordVisible) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        trailingIcon = {
                            val image = if (addPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff
                            IconButton(onClick = { addPasswordVisible = !addPasswordVisible }) {
                                Icon(imageVector = image, contentDescription = "Toggle password visibility")
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (addPasswordInput.isBlank()) {
                            Toast.makeText(context, "Password cannot be empty", Toast.LENGTH_SHORT).show()
                        } else {
                            val baseName = originalName.substringBeforeLast(".")
                            showAddPasswordDialog = false
                            saveEditorFileLauncher.launch("${baseName}_protected.pdf")
                        }
                    }
                ) {
                    Text("Encrypt & Save")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showAddPasswordDialog = false
                        selectedInputUri = null
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    // Remove Password Dialog
    if (showRemovePasswordDialog && selectedInputUri != null) {
        val originalName = getFileName(context, selectedInputUri!!) ?: "document.pdf"
        AlertDialog(
            onDismissRequest = {
                showRemovePasswordDialog = false
                selectedInputUri = null
            },
            title = {
                Text("Enter PDF Password", fontWeight = FontWeight.Bold)
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "This document is encrypted. Enter password to decrypt and save it unprotected.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    OutlinedTextField(
                        value = removePasswordInput,
                        onValueChange = { removePasswordInput = it },
                        label = { Text("Password") },
                        visualTransformation = if (removePasswordVisible) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        trailingIcon = {
                            val image = if (removePasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff
                            IconButton(onClick = { removePasswordVisible = !removePasswordVisible }) {
                                Icon(imageVector = image, contentDescription = "Toggle password visibility")
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        isError = removePasswordError != null
                    )
                    if (removePasswordError != null) {
                        Text(
                            text = removePasswordError ?: "",
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
                        isProcessing = true
                        processingMessage = "Verifying password..."
                        scope.launch {
                            val correct = withContext(Dispatchers.IO) {
                                var isValid = false
                                try {
                                    val tempFile = File(context.cacheDir, "temp_verify_${System.currentTimeMillis()}.pdf")
                                    context.contentResolver.openInputStream(selectedInputUri!!)?.use { input ->
                                        tempFile.outputStream().use { output ->
                                            input.copyTo(output)
                                        }
                                    }
                                    try {
                                        com.tom_roush.pdfbox.pdmodel.PDDocument.load(tempFile, removePasswordInput).close()
                                        isValid = true
                                    } catch (e: Exception) {
                                        isValid = false
                                    } finally {
                                        tempFile.delete()
                                    }
                                } catch (e: Exception) {
                                    isValid = false
                                }
                                isValid
                            }
                            isProcessing = false
                            if (correct) {
                                val baseName = originalName.substringBeforeLast(".")
                                showRemovePasswordDialog = false
                                saveEditorFileLauncher.launch("${baseName}_decrypted.pdf")
                            } else {
                                removePasswordError = "Incorrect password. Please try again."
                            }
                        }
                    }
                ) {
                    Text("Decrypt & Save")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showRemovePasswordDialog = false
                        selectedInputUri = null
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    // Add Watermark Dialog
    if (showAddWatermarkDialog && selectedInputUri != null) {
        val originalName = getFileName(context, selectedInputUri!!) ?: "document.pdf"
        AlertDialog(
            onDismissRequest = {
                showAddWatermarkDialog = false
                selectedInputUri = null
            },
            title = {
                Text("Add PDF Watermark", fontWeight = FontWeight.Bold)
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = originalName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    OutlinedTextField(
                        value = watermarkText,
                        onValueChange = { watermarkText = it },
                        label = { Text("Watermark Text") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Font Size:", fontWeight = FontWeight.SemiBold)
                            Text("${watermarkFontSize.toInt()} pt", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                        Slider(
                            value = watermarkFontSize,
                            onValueChange = { watermarkFontSize = it },
                            valueRange = 12f..96f,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Rotation Angle:", fontWeight = FontWeight.SemiBold)
                            Text("${watermarkRotation.toInt()}°", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                        Slider(
                            value = watermarkRotation,
                            onValueChange = { watermarkRotation = it },
                            valueRange = -90f..90f,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Opacity:", fontWeight = FontWeight.SemiBold)
                            Text("${(watermarkOpacity * 100).toInt()}%", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                        Slider(
                            value = watermarkOpacity,
                            onValueChange = { watermarkOpacity = it },
                            valueRange = 0.1f..1.0f,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text("Watermark Color:", fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val colorsList = listOf(
                                "gray" to ("Gray" to Color.Gray),
                                "red" to ("Red" to Color.Red),
                                "blue" to ("Blue" to Color.Blue),
                                "green" to ("Green" to Color(0xFF2E7D32))
                            )
                            colorsList.forEach { (colorId, pair) ->
                                val (label, displayColor) = pair
                                val isSelected = watermarkColorStr == colorId
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                        .clickable { watermarkColorStr = colorId }
                                        .padding(vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(12.dp)
                                                .background(displayColor, CircleShape)
                                                .border(1.dp, Color.White, CircleShape)
                                        )
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
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (watermarkText.isBlank()) {
                            Toast.makeText(context, "Watermark text cannot be empty", Toast.LENGTH_SHORT).show()
                        } else {
                            val baseName = originalName.substringBeforeLast(".")
                            showAddWatermarkDialog = false
                            saveEditorFileLauncher.launch("${baseName}_watermarked.pdf")
                        }
                    }
                ) {
                    Text("Add Watermark")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showAddWatermarkDialog = false
                        selectedInputUri = null
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    // Signature Dialog
    if (showSignatureDialog && selectedInputUri != null) {
        val originalName = getFileName(context, selectedInputUri!!) ?: "document.pdf"
        AlertDialog(
            onDismissRequest = {
                showSignatureDialog = false
                selectedInputUri = null
            },
            title = {
                Text("Sign PDF Document", fontWeight = FontWeight.Bold)
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Draw your signature inside the box below:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
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
                                        color = Color.Black,
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
                    
                    Text("Select Target Pages:", style = MaterialTheme.typography.titleSmall)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val placementOptions = listOf(
                            "last" to "Last Page",
                            "first" to "First Page",
                            "all" to "All Pages"
                        )
                        placementOptions.forEach { (optionId, label) ->
                            val isSelected = signaturePageOption == optionId
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable { signaturePageOption = optionId }
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
                Button(
                    onClick = {
                        if (signaturePaths.isEmpty()) {
                            Toast.makeText(context, "Please sign on the pad first", Toast.LENGTH_SHORT).show()
                        } else {
                            val baseName = originalName.substringBeforeLast(".")
                            showSignatureDialog = false
                            saveEditorFileLauncher.launch("${baseName}_signed.pdf")
                        }
                    }
                ) {
                    Text("Apply Signature")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showSignatureDialog = false
                        selectedInputUri = null
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    // Stamp Dialog
    if (showStampDialog && selectedInputUri != null) {
        val originalName = getFileName(context, selectedInputUri!!) ?: "document.pdf"
        AlertDialog(
            onDismissRequest = {
                showStampDialog = false
                selectedInputUri = null
            },
            title = {
                Text("Add Stamp to PDF", fontWeight = FontWeight.Bold)
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
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
                        
                        Text("Stamp Color:", style = MaterialTheme.typography.titleSmall)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val colorsList = listOf(
                                "red" to ("Red" to Color.Red),
                                "blue" to ("Blue" to Color.Blue),
                                "green" to ("Green" to Color(0xFF2E7D32))
                            )
                            colorsList.forEach { (colorId, pair) ->
                                val (label, displayColor) = pair
                                val isSelected = stampColorStr == colorId
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                        .clickable { stampColorStr = colorId }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(12.dp)
                                                .background(displayColor, CircleShape)
                                                .border(1.5.dp, Color.White, CircleShape)
                                        )
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
                            if (importedStampUri != null) {
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
                    
                    Text("Target Pages:", style = MaterialTheme.typography.titleSmall)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val placementOptions = listOf(
                            "first" to "First Page",
                            "last" to "Last Page",
                            "all" to "All Pages"
                        )
                        placementOptions.forEach { (optionId, label) ->
                            val isSelected = stampPageOption == optionId
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable { stampPageOption = optionId }
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
                Button(
                    onClick = {
                        if (stampTypeTab == 0 && stampText.isBlank()) {
                            Toast.makeText(context, "Stamp text cannot be empty", Toast.LENGTH_SHORT).show()
                        } else if (stampTypeTab == 1 && importedStampUri == null) {
                            Toast.makeText(context, "Please select an image stamp first", Toast.LENGTH_SHORT).show()
                        } else {
                            val baseName = originalName.substringBeforeLast(".")
                            showStampDialog = false
                            saveEditorFileLauncher.launch("${baseName}_stamped.pdf")
                        }
                    }
                ) {
                    Text("Apply Stamp")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showStampDialog = false
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
    IMAGES_TO_PDF,
    ADD_PASSWORD,
    REMOVE_PASSWORD,
    ADD_WATERMARK,
    ADD_SIGNATURE,
    ADD_STAMP
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
