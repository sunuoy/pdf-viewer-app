package com.pdfsuny.app.ui.screens

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.RectF
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.pdfsuny.app.ui.components.TooltipIconButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.BlendMode
import java.io.FileOutputStream
import java.io.InputStream

data class ImageItem(
    val id: String,
    val originalUri: Uri,
    val currentUri: Uri, // Points to edited file in cache, or originalUri if not edited
    val name: String,
    val rotation: Float = 0f,
    val isFlippedH: Boolean = false,
    val isFlippedV: Boolean = false,
    val filter: String = "none", // "none", "grayscale", "sepia", "bw", "invert"
    val cropRatio: String = "original" // "original", "1:1", "4:3", "16:9", "3:4", "9:16"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArrangeImagesScreen(
    initialImageUris: List<String>,
    onBack: () -> Unit,
    onNavigateToPdfViewer: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sharedPrefs = remember { context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE) }

    val imageList = remember { mutableStateListOf<ImageItem>() }

    // PDF Configuration Options
    var outputFileName by remember { mutableStateOf("images_compiled") }
    var pageSize by remember { mutableStateOf("auto") } // "auto", "a4", "letter", "legal", "a3"
    var pageOrientation by remember { mutableStateOf("portrait") } // "portrait", "landscape"
    var pageMargins by remember { mutableStateOf(0f) } // 0f, 12f, 24f, 48f
    var borderWidth by remember { mutableStateOf(0f) } // 0f, 1f, 3f, 5f, 10f
    var borderColorHex by remember {
        mutableStateOf(sharedPrefs.getString("default_border_color", "#000000") ?: "#000000")
    }
    var pageNumberStyle by remember {
        mutableStateOf(sharedPrefs.getString("default_page_number_style", "none") ?: "none")
    }
    var grayscalePdf by remember { mutableStateOf(false) }
    var watermarkText by remember { mutableStateOf("") }
    var watermarkColorHex by remember {
        mutableStateOf(sharedPrefs.getString("default_watermark_color", "#D3D3D3") ?: "#D3D3D3")
    }
    var customWidth by remember { mutableStateOf(595f) }
    var customHeight by remember { mutableStateOf(842f) }

    // Dialog Visibilities
    var showPageSizeDialog by remember { mutableStateOf(false) }
    var showBordersMarginsDialog by remember { mutableStateOf(false) }
    var showPageNumberDialog by remember { mutableStateOf(false) }
    var showSubMenu by remember { mutableStateOf(false) }
    var editingImageItem by remember { mutableStateOf<ImageItem?>(null) }

    // UI Loading & Success States
    var isProcessing by remember { mutableStateOf(false) }
    var processingMessage by remember { mutableStateOf("") }
    var showSuccessDialog by remember { mutableStateOf(false) }
    var processedFileUri by remember { mutableStateOf<Uri?>(null) }

    // Load initial images (if passed from navigation)
    LaunchedEffect(initialImageUris) {
        if (imageList.isEmpty() && initialImageUris.isNotEmpty()) {
            isProcessing = true
            processingMessage = "Importing initial images..."
            scope.launch {
                try {
                    val items = withContext(Dispatchers.IO) {
                        initialImageUris.mapIndexed { index, uriStr ->
                            val uri = Uri.parse(uriStr)
                            val name = getFileName(context, uri) ?: "Image_${index + 1}.jpg"
                            ImageItem(
                                id = "init_${index}_${System.currentTimeMillis()}",
                                originalUri = uri,
                                currentUri = uri,
                                name = name
                            )
                        }
                    }
                    imageList.addAll(items)
                } catch (e: Exception) {
                    Toast.makeText(context, "Error importing: ${e.message}", Toast.LENGTH_SHORT).show()
                } finally {
                    isProcessing = false
                }
            }
        }
    }

    // Image Picker for appending more images
    val appendImagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            isProcessing = true
            processingMessage = "Importing images..."
            scope.launch {
                try {
                    val tempItems = withContext(Dispatchers.IO) {
                        uris.mapIndexed { index, uri ->
                            val tempFile = File(context.cacheDir, "temp_img_${index}_${System.currentTimeMillis()}.jpg")
                            if (tempFile.exists()) tempFile.delete()
                            context.contentResolver.openInputStream(uri)?.use { input ->
                                tempFile.outputStream().use { output ->
                                    input.copyTo(output)
                                }
                            }
                            val authority = "com.pdfsuny.app.fileprovider"
                            val cachedUri = androidx.core.content.FileProvider.getUriForFile(context, authority, tempFile)
                            val name = getFileName(context, uri) ?: "Image_${System.currentTimeMillis()}.jpg"
                            ImageItem(
                                id = "pick_${index}_${System.currentTimeMillis()}",
                                originalUri = cachedUri,
                                currentUri = cachedUri,
                                name = name
                            )
                        }
                    }
                    imageList.addAll(tempItems)
                } catch (e: Exception) {
                    android.util.Log.e("ArrangeImagesScreen", "Error appending images", e)
                    Toast.makeText(context, "Failed to load images: ${e.message}", Toast.LENGTH_LONG).show()
                } finally {
                    isProcessing = false
                }
            }
        }
    }

    // PDF Compilation Save Launcher
    val savePdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { destUri: Uri? ->
        destUri?.let { targetUri ->
            isProcessing = true
            processingMessage = "Compiling images to PDF..."
            scope.launch {
                try {
                    compilePdfFromImages(
                        context = context,
                        imageList = imageList,
                        targetUri = targetUri,
                        pageSize = pageSize,
                        pageOrientation = pageOrientation,
                        pageMargins = pageMargins,
                        borderWidth = borderWidth,
                        borderColorHex = borderColorHex,
                        pageNumberStyle = pageNumberStyle,
                        grayscalePdf = grayscalePdf,
                        watermarkText = watermarkText,
                        watermarkColorHex = watermarkColorHex,
                        customWidth = customWidth,
                        customHeight = customHeight
                    )
                    processedFileUri = targetUri
                    showSuccessDialog = true
                } catch (e: Exception) {
                    android.util.Log.e("ArrangeImagesScreen", "Error compiling PDF", e)
                    Toast.makeText(context, "Images compilation failed: ${e.message}", Toast.LENGTH_LONG).show()
                } finally {
                    isProcessing = false
                }
            }
        }
    }

    // Preview PDF function
    val previewPdf: () -> Unit = {
        if (imageList.isNotEmpty()) {
            isProcessing = true
            processingMessage = "Preparing preview PDF..."
            scope.launch {
                try {
                    val tempFile = File(context.cacheDir, "temp_preview_${System.currentTimeMillis()}.pdf")
                    val targetUri = Uri.fromFile(tempFile)
                    compilePdfFromImages(
                        context = context,
                        imageList = imageList,
                        targetUri = targetUri,
                        pageSize = pageSize,
                        pageOrientation = pageOrientation,
                        pageMargins = pageMargins,
                        borderWidth = borderWidth,
                        borderColorHex = borderColorHex,
                        pageNumberStyle = pageNumberStyle,
                        grayscalePdf = grayscalePdf,
                        watermarkText = watermarkText,
                        watermarkColorHex = watermarkColorHex,
                        customWidth = customWidth,
                        customHeight = customHeight
                    )
                    onNavigateToPdfViewer(Uri.fromFile(tempFile).toString())
                } catch (e: Exception) {
                    android.util.Log.e("ArrangeImagesScreen", "Error preparing preview", e)
                    Toast.makeText(context, "Preview failed: ${e.message}", Toast.LENGTH_LONG).show()
                } finally {
                    isProcessing = false
                }
            }
        } else {
            Toast.makeText(context, "No images to preview!", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Arrange Images", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    TooltipIconButton(onClick = onBack, tooltipText = "Back") {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TooltipIconButton(onClick = previewPdf, tooltipText = "Preview PDF") {
                        Icon(Icons.Default.Visibility, contentDescription = "Preview PDF")
                    }
                    Box {
                        IconButton(onClick = { showSubMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More Options")
                        }
                        DropdownMenu(
                            expanded = showSubMenu,
                            onDismissRequest = { showSubMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Preview PDF") },
                                leadingIcon = { Icon(Icons.Default.Visibility, contentDescription = null) },
                                onClick = {
                                    showSubMenu = false
                                    previewPdf()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Page Size & Layout") },
                                leadingIcon = { Icon(Icons.Default.Crop, contentDescription = null) },
                                onClick = {
                                    showSubMenu = false
                                    showPageSizeDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Borders & Margins") },
                                leadingIcon = { Icon(Icons.Default.BorderOuter, contentDescription = null) },
                                onClick = {
                                    showSubMenu = false
                                    showBordersMarginsDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Page Numbering") },
                                leadingIcon = { Icon(Icons.Default.FormatListNumbered, contentDescription = null) },
                                onClick = {
                                    showSubMenu = false
                                    showPageNumberDialog = true
                                }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Clear All") },
                                leadingIcon = { Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    showSubMenu = false
                                    imageList.clear()
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { appendImagePickerLauncher.launch(arrayOf("image/*")) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.AddPhotoAlternate, contentDescription = "Add Images")
            }
        }
    ) { padding ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
            ) {
                if (imageList.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(0.6f)
                                .clickable { appendImagePickerLauncher.launch(arrayOf("image/*")) },
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                            border = BorderStroke(2.dp, Brush.sweepGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary)))
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize().padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AddPhotoAlternate,
                                    contentDescription = "Add Images",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(64.dp)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "Images to PDF Compiler",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Select images to arrange, edit, format, and generate standard PDF files offline.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.secondary,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(20.dp))
                                Button(
                                    onClick = { appendImagePickerLauncher.launch(arrayOf("image/*")) },
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Select Images", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp)
                    ) {
                        gridItemsIndexed(
                            items = imageList,
                            key = { _, item -> item.id }
                        ) { index, item ->
                            Box(
                                modifier = Modifier
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { editingImageItem = item }
                            ) {
                                LocalImageThumbnail(
                                    uri = item.currentUri,
                                    modifier = Modifier.fillMaxSize()
                                )
                                
                                // Translucent circle badge with expand icon in top-right
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(6.dp)
                                        .size(32.dp)
                                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                        .clickable { editingImageItem = item },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ZoomOutMap,
                                        contentDescription = "Edit Image",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                // Index badge in top-left
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .padding(6.dp)
                                        .size(20.dp)
                                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "${index + 1}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }
                    }
                }

                // Configuration Settings Card Summary
                if (imageList.isNotEmpty()) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("PDF Configurations Summary", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                            }
                            
                            val sizeText = when (pageSize) {
                                "auto" -> "Fit (Auto)"
                                "custom" -> "Custom (${customWidth.toInt()}x${customHeight.toInt()} pt)"
                                else -> "${pageSize.uppercase()} (${if (pageOrientation == "portrait") "Port" else "Land"})"
                            }
                            
                            val marginText = when (pageMargins) {
                                0f -> "None"
                                12f -> "Small (12 pt)"
                                24f -> "Medium (24 pt)"
                                48f -> "Large (48 pt)"
                                else -> "$pageMargins pt"
                            }
                            
                            val borderText = if (borderWidth == 0f) "None" else "${borderWidth.toInt()} pt"
                            val pageNumText = when (pageNumberStyle) {
                                "none" -> "Disabled"
                                "page_x_of_n" -> "Page X of N"
                                "x_of_n" -> "X of N"
                                "x" -> "X"
                                else -> pageNumberStyle
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                AssistChip(
                                    onClick = { showPageSizeDialog = true },
                                    label = { Text("Size: $sizeText", fontSize = 10.sp) },
                                    modifier = Modifier.weight(1f)
                                )
                                AssistChip(
                                    onClick = { showBordersMarginsDialog = true },
                                    label = { Text("Layout: $marginText", fontSize = 10.sp) },
                                    modifier = Modifier.weight(1f)
                                )
                                AssistChip(
                                    onClick = { showPageNumberDialog = true },
                                    label = { Text("Page No: $pageNumText", fontSize = 10.sp) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                // File Name & Compile Action
                if (imageList.isNotEmpty()) {
                    OutlinedTextField(
                        value = outputFileName,
                        onValueChange = { outputFileName = it },
                        label = { Text("Output PDF Name") },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        singleLine = true
                    )

                    Button(
                        onClick = {
                            val sanitized = outputFileName.trim().ifBlank { "images_compiled" }
                            val targetName = if (sanitized.lowercase().endsWith(".pdf")) sanitized else "$sanitized.pdf"
                            savePdfLauncher.launch(targetName)
                        },
                        enabled = imageList.isNotEmpty(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Generate PDF Document", fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Options Dialogs
            if (showPageSizeDialog) {
                PageSizeDialog(
                    initialPageSize = pageSize,
                    initialOrientation = pageOrientation,
                    initialCustomWidth = customWidth,
                    initialCustomHeight = customHeight,
                    onDismiss = { showPageSizeDialog = false },
                    onConfirm = { size, orient, w, h ->
                        pageSize = size
                        pageOrientation = orient
                        customWidth = w
                        customHeight = h
                        showPageSizeDialog = false
                    }
                )
            }

            if (showBordersMarginsDialog) {
                BordersMarginsDialog(
                    initialMargin = pageMargins,
                    initialBorderWidth = borderWidth,
                    initialBorderColorHex = borderColorHex,
                    initialGrayscale = grayscalePdf,
                    initialWatermark = watermarkText,
                    initialWatermarkColorHex = watermarkColorHex,
                    onDismiss = { showBordersMarginsDialog = false },
                    onConfirm = { marg, w, col, gray, watermark, watermarkCol ->
                        pageMargins = marg
                        borderWidth = w
                        borderColorHex = col
                        grayscalePdf = gray
                        watermarkText = watermark
                        watermarkColorHex = watermarkCol
                        showBordersMarginsDialog = false
                    }
                )
            }

            if (showPageNumberDialog) {
                ChoosePageNumberStyleDialog(
                    initialStyle = pageNumberStyle,
                    initialSetDefault = sharedPrefs.getString("default_page_number_style", "none") == pageNumberStyle,
                    onDismiss = { showPageNumberDialog = false },
                    onConfirm = { style, setDefault ->
                        pageNumberStyle = style
                        if (setDefault) {
                            sharedPrefs.edit().putString("default_page_number_style", style).apply()
                        }
                        showPageNumberDialog = false
                    },
                    onRemove = {
                        pageNumberStyle = "none"
                        showPageNumberDialog = false
                    }
                )
            }

             // Image Editing Dialog
             editingImageItem?.let { item ->
                 val index = imageList.indexOfFirst { it.id == item.id }
                 EditImageDialog(
                     item = item,
                     onDismiss = { editingImageItem = null },
                     onSave = { rot, fH, fV, filt, ratio ->
                         editingImageItem = null
                         isProcessing = true
                         processingMessage = "Applying image edits..."
                         scope.launch {
                             try {
                                 val newUri = applyEditsAndSave(
                                     context = context,
                                     originalUri = item.originalUri,
                                     rotation = rot,
                                     flipH = fH,
                                     flipV = fV,
                                     filter = filt,
                                     cropRatio = ratio
                                 )
                                 val idx = imageList.indexOfFirst { it.id == item.id }
                                 if (idx != -1) {
                                     imageList[idx] = item.copy(
                                         currentUri = newUri,
                                         rotation = rot,
                                         isFlippedH = fH,
                                         isFlippedV = fV,
                                         filter = filt,
                                         cropRatio = ratio
                                     )
                                 }
                             } catch (e: Exception) {
                                 android.util.Log.e("ArrangeImagesScreen", "Error applying edits", e)
                                 Toast.makeText(context, "Failed to apply edits: ${e.message}", Toast.LENGTH_LONG).show()
                             } finally {
                                 isProcessing = false
                             }
                         }
                     },
                     onMoveLeft = {
                         if (index > 0) {
                             val temp = imageList.removeAt(index)
                             imageList.add(index - 1, temp)
                             editingImageItem = temp
                         }
                     },
                     onMoveRight = {
                         if (index < imageList.size - 1) {
                             val temp = imageList.removeAt(index)
                             imageList.add(index + 1, temp)
                             editingImageItem = temp
                         }
                     },
                     onDelete = {
                         if (index != -1) {
                             imageList.removeAt(index)
                             editingImageItem = null
                         }
                     },
                     isMoveLeftEnabled = index > 0,
                     isMoveRightEnabled = index < imageList.size - 1 && index != -1
                 )
             }

            // Processing Indicator
            if (isProcessing) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(text = processingMessage, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            // Success Dialog
            if (showSuccessDialog && processedFileUri != null) {
                val fileName = getFileName(context, processedFileUri!!) ?: "File.pdf"
                AlertDialog(
                    onDismissRequest = { showSuccessDialog = false },
                    title = { Text("PDF Compiled Successfully", fontWeight = FontWeight.Bold) },
                    text = {
                        Text("Successfully saved $fileName to your chosen folder.")
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(processedFileUri!!, "application/pdf")
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "No app found to open PDF files", Toast.LENGTH_SHORT).show()
                                }
                            }
                        ) {
                            Text("Open PDF")
                        }
                    },
                    dismissButton = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(
                                onClick = {
                                    try {
                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "application/pdf"
                                            putExtra(Intent.EXTRA_STREAM, processedFileUri!!)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(Intent.createChooser(shareIntent, "Share PDF"))
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Cannot share this file", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            ) {
                                Text("Share")
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            TextButton(onClick = { showSuccessDialog = false }) {
                                Text("Close")
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun LocalImageThumbnail(uri: Uri, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var bitmap by remember(uri) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(uri) {
        withContext(Dispatchers.IO) {
            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val options = BitmapFactory.Options().apply {
                        inSampleSize = 4
                    }
                    bitmap = BitmapFactory.decodeStream(stream, null, options)
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
            contentScale = ContentScale.Crop
        )
    } else {
        Box(
            modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
fun LocalImageThumbnailForEdit(
    uri: Uri,
    modifier: Modifier = Modifier,
    colorFilter: ColorFilter? = null
) {
    val context = LocalContext.current
    var bitmap by remember(uri) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(uri) {
        withContext(Dispatchers.IO) {
            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val options = BitmapFactory.Options().apply {
                        inSampleSize = 2
                    }
                    bitmap = BitmapFactory.decodeStream(stream, null, options)
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
            contentScale = ContentScale.Fit,
            colorFilter = colorFilter
        )
    } else {
        Box(
            modifier = modifier.background(Color.LightGray.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
fun getComposeColorFilter(filter: String): ColorFilter? {
    return when (filter) {
        "grayscale" -> ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
        "sepia" -> ColorFilter.colorMatrix(ColorMatrix(floatArrayOf(
            0.393f, 0.769f, 0.189f, 0f, 0f,
            0.349f, 0.686f, 0.168f, 0f, 0f,
            0.272f, 0.534f, 0.131f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )))
        "bw" -> ColorFilter.colorMatrix(ColorMatrix(floatArrayOf(
            1.5f, 1.5f, 1.5f, 0f, -128f,
            1.5f, 1.5f, 1.5f, 0f, -128f,
            1.5f, 1.5f, 1.5f, 0f, -128f,
            0f, 0f, 0f, 1f, 0f
        )))
        "invert" -> ColorFilter.colorMatrix(ColorMatrix(floatArrayOf(
            -1f, 0f, 0f, 0f, 255f,
            0f, -1f, 0f, 0f, 255f,
            0f, 0f, -1f, 0f, 255f,
            0f, 0f, 0f, 1f, 0f
        )))
        else -> null
    }
}

private fun getCroppedBitmap(bitmap: Bitmap, ratioStr: String): Bitmap {
    if (ratioStr == "original") return bitmap
    val w = bitmap.width
    val h = bitmap.height
    val ratio = when (ratioStr) {
        "1:1" -> 1f
        "4:3" -> 4f / 3f
        "16:9" -> 16f / 9f
        "3:4" -> 3f / 4f
        "9:16" -> 9f / 16f
        else -> return bitmap
    }
    val targetW: Int
    val targetH: Int
    if (w.toFloat() / h.toFloat() > ratio) {
        targetH = h
        targetW = (h * ratio).toInt()
    } else {
        targetW = w
        targetH = (w / ratio).toInt()
    }
    val x = (w - targetW) / 2
    val y = (h - targetH) / 2
    return Bitmap.createBitmap(bitmap, x, y, targetW, targetH)
}

private fun getTransformedBitmap(bitmap: Bitmap, rotation: Float, flipH: Boolean, flipV: Boolean): Bitmap {
    if (rotation == 0f && !flipH && !flipV) return bitmap
    val matrix = android.graphics.Matrix()
    if (rotation != 0f) {
        matrix.postRotate(rotation)
    }
    val sx = if (flipH) -1f else 1f
    val sy = if (flipV) -1f else 1f
    if (flipH || flipV) {
        matrix.postScale(sx, sy)
    }
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}

private fun getFilteredBitmap(bitmap: Bitmap, filter: String): Bitmap {
    if (filter == "none") return bitmap
    val filtered = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(filtered)
    val paint = android.graphics.Paint()
    val colorMatrix = android.graphics.ColorMatrix()
    when (filter) {
        "grayscale" -> colorMatrix.setSaturation(0f)
        "sepia" -> {
            val sepiaMatrix = floatArrayOf(
                0.393f, 0.769f, 0.189f, 0f, 0f,
                0.349f, 0.686f, 0.168f, 0f, 0f,
                0.272f, 0.534f, 0.131f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            )
            colorMatrix.set(sepiaMatrix)
        }
        "bw" -> {
            val bwMatrix = floatArrayOf(
                1.5f, 1.5f, 1.5f, 0f, -128f,
                1.5f, 1.5f, 1.5f, 0f, -128f,
                1.5f, 1.5f, 1.5f, 0f, -128f,
                0f, 0f, 0f, 1f, 0f
            )
            colorMatrix.set(bwMatrix)
        }
        "invert" -> {
            val invertMatrix = floatArrayOf(
                -1f, 0f, 0f, 0f, 255f,
                0f, -1f, 0f, 0f, 255f,
                0f, 0f, -1f, 0f, 255f,
                0f, 0f, 0f, 1f, 0f
            )
            colorMatrix.set(invertMatrix)
        }
    }
    paint.colorFilter = android.graphics.ColorMatrixColorFilter(colorMatrix)
    canvas.drawBitmap(bitmap, 0f, 0f, paint)
    return filtered
}

private suspend fun applyEditsAndSave(
    context: Context,
    originalUri: Uri,
    rotation: Float,
    flipH: Boolean,
    flipV: Boolean,
    filter: String,
    cropRatio: String
): Uri = withContext(Dispatchers.IO) {
    var currentBitmap = context.contentResolver.openInputStream(originalUri)?.use { stream ->
        BitmapFactory.decodeStream(stream)
    } ?: throw Exception("Failed to load original image")

    if (cropRatio != "original") {
        val cropped = getCroppedBitmap(currentBitmap, cropRatio)
        if (cropped != currentBitmap) {
            currentBitmap.recycle()
            currentBitmap = cropped
        }
    }

    if (rotation != 0f || flipH || flipV) {
        val transformed = getTransformedBitmap(currentBitmap, rotation, flipH, flipV)
        if (transformed != currentBitmap) {
            currentBitmap.recycle()
            currentBitmap = transformed
        }
    }

    if (filter != "none") {
        val filtered = getFilteredBitmap(currentBitmap, filter)
        if (filtered != currentBitmap) {
            currentBitmap.recycle()
            currentBitmap = filtered
        }
    }

    val cacheFile = File(context.cacheDir, "edited_img_${System.currentTimeMillis()}.jpg")
    FileOutputStream(cacheFile).use { out ->
        currentBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
    }
    currentBitmap.recycle()

    val authority = "com.pdfsuny.app.fileprovider"
    androidx.core.content.FileProvider.getUriForFile(context, authority, cacheFile)
}

private suspend fun compilePdfFromImages(
    context: Context,
    imageList: List<ImageItem>,
    targetUri: Uri,
    pageSize: String,
    pageOrientation: String,
    pageMargins: Float,
    borderWidth: Float,
    borderColorHex: String,
    pageNumberStyle: String,
    grayscalePdf: Boolean,
    watermarkText: String,
    watermarkColorHex: String,
    customWidth: Float,
    customHeight: Float
) = withContext(Dispatchers.IO) {
    val document = com.tom_roush.pdfbox.pdmodel.PDDocument()
    
    val openInputStream = { u: Uri ->
        if (u.scheme == "file") {
            java.io.FileInputStream(java.io.File(u.path ?: ""))
        } else {
            context.contentResolver.openInputStream(u)
        }
    }

    for ((pageIndex, item) in imageList.withIndex()) {
        openInputStream(item.currentUri)?.use { stream ->
            val originalBitmap = BitmapFactory.decodeStream(stream)
            if (originalBitmap != null) {
                val bitmap = if (grayscalePdf) {
                    val converted = getFilteredBitmap(originalBitmap, "grayscale")
                    if (converted != originalBitmap) {
                        originalBitmap.recycle()
                    }
                    converted
                } else {
                    originalBitmap
                }

                val rect = when (pageSize) {
                    "a0" -> com.tom_roush.pdfbox.pdmodel.common.PDRectangle(2384f, 3370f)
                    "a1" -> com.tom_roush.pdfbox.pdmodel.common.PDRectangle(1684f, 2384f)
                    "a2" -> com.tom_roush.pdfbox.pdmodel.common.PDRectangle(1190f, 1684f)
                    "a3" -> com.tom_roush.pdfbox.pdmodel.common.PDRectangle(842f, 1190f)
                    "a4" -> com.tom_roush.pdfbox.pdmodel.common.PDRectangle(595f, 842f)
                    "a5" -> com.tom_roush.pdfbox.pdmodel.common.PDRectangle(420f, 595f)
                    "a6" -> com.tom_roush.pdfbox.pdmodel.common.PDRectangle(298f, 420f)
                    "a7" -> com.tom_roush.pdfbox.pdmodel.common.PDRectangle(210f, 298f)
                    "a8" -> com.tom_roush.pdfbox.pdmodel.common.PDRectangle(147f, 210f)
                    "a9" -> com.tom_roush.pdfbox.pdmodel.common.PDRectangle(105f, 147f)
                    "a10" -> com.tom_roush.pdfbox.pdmodel.common.PDRectangle(74f, 105f)
                    "letter" -> com.tom_roush.pdfbox.pdmodel.common.PDRectangle(612f, 792f)
                    "legal" -> com.tom_roush.pdfbox.pdmodel.common.PDRectangle(612f, 1008f)
                    "custom" -> com.tom_roush.pdfbox.pdmodel.common.PDRectangle(customWidth, customHeight)
                    else -> null // "auto"
                }

                val page = if (rect != null) {
                    if (pageOrientation == "landscape") {
                        com.tom_roush.pdfbox.pdmodel.PDPage(com.tom_roush.pdfbox.pdmodel.common.PDRectangle(rect.height, rect.width))
                    } else {
                        com.tom_roush.pdfbox.pdmodel.PDPage(rect)
                    }
                } else {
                    val w = bitmap.width.toFloat() + (pageMargins * 2)
                    val h = bitmap.height.toFloat() + (pageMargins * 2)
                    com.tom_roush.pdfbox.pdmodel.PDPage(com.tom_roush.pdfbox.pdmodel.common.PDRectangle(w, h))
                }
                
                document.addPage(page)
                val pdImage = com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory.createFromImage(document, bitmap)
                val contentStream = com.tom_roush.pdfbox.pdmodel.PDPageContentStream(document, page)
                
                val pageW = page.mediaBox.width
                val pageH = page.mediaBox.height
                val margin = pageMargins
                
                val printableW = pageW - (margin * 2)
                val printableH = pageH - (margin * 2)
                val imgW = bitmap.width.toFloat()
                val imgH = bitmap.height.toFloat()
                
                val scale = minOf(printableW / imgW, printableH / imgH)
                val drawW = imgW * scale
                val drawH = imgH * scale
                val x = margin + (printableW - drawW) / 2f
                val y = margin + (printableH - drawH) / 2f
                
                contentStream.drawImage(pdImage, x, y, drawW, drawH)
                
                if (borderWidth > 0f) {
                    contentStream.setLineWidth(borderWidth)
                    val c = android.graphics.Color.parseColor(borderColorHex)
                    val r = android.graphics.Color.red(c) / 255f
                    val g = android.graphics.Color.green(c) / 255f
                    val b = android.graphics.Color.blue(c) / 255f
                    contentStream.setStrokingColor(r, g, b)
                    contentStream.addRect(x, y, drawW, drawH)
                    contentStream.stroke()
                }
                
                if (watermarkText.isNotEmpty()) {
                    contentStream.beginText()
                    contentStream.setFont(com.tom_roush.pdfbox.pdmodel.font.PDType1Font.HELVETICA_BOLD, 40f)
                    val c = try {
                        android.graphics.Color.parseColor(watermarkColorHex)
                    } catch (e: Exception) {
                        android.graphics.Color.LTGRAY
                    }
                    val r = android.graphics.Color.red(c) / 255f
                    val g = android.graphics.Color.green(c) / 255f
                    val b = android.graphics.Color.blue(c) / 255f
                    contentStream.setNonStrokingColor(r, g, b)
                    val angle = Math.toRadians(45.0)
                    val approxTextW = watermarkText.length * 20f
                    val textX = (pageW - approxTextW) / 2f
                    val textY = pageH / 2f
                    val matrix = com.tom_roush.pdfbox.util.Matrix.getRotateInstance(angle, textX, textY)
                    contentStream.setTextMatrix(matrix)
                    contentStream.showText(watermarkText)
                    contentStream.endText()
                }
                
                if (pageNumberStyle != "none") {
                    val text = when (pageNumberStyle) {
                        "page_x_of_n" -> "Page ${pageIndex + 1} of ${imageList.size}"
                        "x_of_n" -> "${pageIndex + 1} of ${imageList.size}"
                        "x" -> "${pageIndex + 1}"
                        else -> ""
                    }
                    if (text.isNotEmpty()) {
                        contentStream.beginText()
                        contentStream.setFont(com.tom_roush.pdfbox.pdmodel.font.PDType1Font.HELVETICA, 10f)
                        contentStream.setNonStrokingColor(0f, 0f, 0f)
                        
                        val approxWidth = text.length * 5f
                        val textX = (pageW - approxWidth) / 2f
                        val textY = maxOf(10f, margin / 2f)
                        contentStream.newLineAtOffset(textX, textY)
                        contentStream.showText(text)
                        contentStream.endText()
                    }
                }
                
                contentStream.close()
                bitmap.recycle()
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
    
    openOutputStream(targetUri)?.use { out ->
        document.save(out)
    }
    document.close()
}

@Composable
fun ChoosePageNumberStyleDialog(
    initialStyle: String,
    initialSetDefault: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (style: String, setDefault: Boolean) -> Unit,
    onRemove: () -> Unit
) {
    var selectedStyle by remember { mutableStateOf(if (initialStyle == "none") "page_x_of_n" else initialStyle) }
    var setDefault by remember { mutableStateOf(initialSetDefault) }
    
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFF2C2C2C),
            modifier = Modifier.width(320.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Choose Page Number Style",
                    color = Color.White,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 20.dp)
                )
                
                val options = listOf(
                    "page_x_of_n" to "Page X of N",
                    "x_of_n" to "X of N",
                    "x" to "X"
                )
                
                options.forEach { (style, label) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedStyle = style }
                            .padding(vertical = 10.dp)
                    ) {
                        RadioButton(
                            selected = selectedStyle == style,
                            onClick = { selectedStyle = style },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = Color(0xFF00BFA5),
                                unselectedColor = Color.Gray
                            )
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(text = label, color = Color.White, fontSize = 16.sp)
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = Color.Gray.copy(alpha = 0.3f), modifier = Modifier.padding(vertical = 8.dp))
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { setDefault = !setDefault }
                        .padding(vertical = 10.dp)
                ) {
                    Checkbox(
                        checked = setDefault,
                        onCheckedChange = { setDefault = it },
                        colors = CheckboxDefaults.colors(
                            checkedColor = Color(0xFF00BFA5),
                            checkmarkColor = Color(0xFF2C2C2C),
                            uncheckedColor = Color.Gray
                        )
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(text = "Set as default", color = Color.White, fontSize = 16.sp)
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onRemove) {
                        Text("REMOVE", color = Color(0xFF00BFA5), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                    Row {
                        TextButton(onClick = onDismiss) {
                            Text("CANCEL", color = Color(0xFF00BFA5), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(onClick = { onConfirm(selectedStyle, setDefault) }) {
                            Text("OK", color = Color(0xFF00BFA5), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BordersMarginsDialog(
    initialMargin: Float,
    initialBorderWidth: Float,
    initialBorderColorHex: String,
    initialGrayscale: Boolean,
    initialWatermark: String,
    initialWatermarkColorHex: String,
    onDismiss: () -> Unit,
    onConfirm: (margin: Float, borderWidth: Float, borderColorHex: String, grayscale: Boolean, watermark: String, watermarkColorHex: String) -> Unit
) {
    val context = LocalContext.current
    var margin by remember { mutableStateOf(initialMargin) }
    var borderWidth by remember { mutableStateOf(initialBorderWidth) }
    var borderColorHex by remember { mutableStateOf(initialBorderColorHex) }
    var grayscale by remember { mutableStateOf(initialGrayscale) }
    var watermark by remember { mutableStateOf(initialWatermark) }
    var watermarkColorHex by remember { mutableStateOf(initialWatermarkColorHex) }
    var showColorPicker by remember { mutableStateOf(false) }
    var showBorderColorPicker by remember { mutableStateOf(false) }
    
    val marginOptions = listOf(0f to "None", 12f to "Small", 24f to "Medium", 48f to "Large")
    val borderOptions = listOf(0f to "None", 1f to "Thin", 3f to "Medium", 5f to "Thick", 10f to "X-Thick")

    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Borders & Margins Settings", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column {
                    Text("Page Margin", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.secondary)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        marginOptions.forEach { (valPt, label) ->
                            val isSel = margin == valPt
                            Button(
                                onClick = { margin = valPt },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(label, fontSize = 11.sp, maxLines = 1)
                            }
                        }
                    }
                }
                
                Column {
                    Text("Border Width", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.secondary)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        borderOptions.forEach { (valPt, label) ->
                            val isSel = borderWidth == valPt
                            Button(
                                onClick = { borderWidth = valPt },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(label, fontSize = 11.sp, maxLines = 1)
                            }
                        }
                    }
                }
                
                if (borderWidth > 0f) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Border Color", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.secondary)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showBorderColorPicker = true }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val currentPreviewColor = try {
                                Color(android.graphics.Color.parseColor(if (borderColorHex.startsWith("#")) borderColorHex else "#$borderColorHex"))
                            } catch (e: Exception) {
                                Color.Black
                            }
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(currentPreviewColor)
                                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                val luminance = currentPreviewColor.red * 0.299f + currentPreviewColor.green * 0.587f + currentPreviewColor.blue * 0.114f
                                val tint = if (luminance > 0.5f) Color.Black else Color.White
                                Icon(
                                    Icons.Default.Palette,
                                    contentDescription = "Pick Color",
                                    tint = tint,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Choose Custom Color", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                Text(borderColorHex.uppercase(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                            }
                        }

                        if (showBorderColorPicker) {
                            ColorPickerDialog(
                                initialColorHex = borderColorHex,
                                onDismiss = { showBorderColorPicker = false },
                                onConfirm = { hex, setAsDefault ->
                                    borderColorHex = hex
                                    val sharedPrefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                                    if (setAsDefault) {
                                        sharedPrefs.edit().putString("default_border_color", hex).apply()
                                    }
                                    showBorderColorPicker = false
                                }
                            )
                        }
                    }
                }
                
                // Grayscale Toggle
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), modifier = Modifier.padding(vertical = 4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Grayscale PDF", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text("Convert all pages to black and white", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                    }
                    Switch(
                        checked = grayscale,
                        onCheckedChange = { grayscale = it }
                    )
                }

                // Watermark Text Input
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), modifier = Modifier.padding(vertical = 4.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Add Watermark", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.secondary)
                    OutlinedTextField(
                        value = watermark,
                        onValueChange = { watermark = it },
                        label = { Text("Watermark Text (e.g. DRAFT)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("None") }
                    )
                    
                    Text("Watermark Color", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.secondary)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showColorPicker = true }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val currentPreviewColor = try {
                            Color(android.graphics.Color.parseColor(if (watermarkColorHex.startsWith("#")) watermarkColorHex else "#$watermarkColorHex"))
                        } catch (e: Exception) {
                            Color.LightGray
                        }
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(currentPreviewColor)
                                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            val luminance = currentPreviewColor.red * 0.299f + currentPreviewColor.green * 0.587f + currentPreviewColor.blue * 0.114f
                            val tint = if (luminance > 0.5f) Color.Black else Color.White
                            Icon(
                                Icons.Default.Palette,
                                contentDescription = "Pick Color",
                                tint = tint,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("Choose Custom Color", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text(watermarkColorHex.uppercase(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                        }
                    }

                    if (showColorPicker) {
                        ColorPickerDialog(
                            initialColorHex = watermarkColorHex,
                            onDismiss = { showColorPicker = false },
                            onConfirm = { hex, setAsDefault ->
                                watermarkColorHex = hex
                                val sharedPrefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                                if (setAsDefault) {
                                    sharedPrefs.edit().putString("default_watermark_color", hex).apply()
                                }
                                showColorPicker = false
                            }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(margin, borderWidth, borderColorHex, grayscale, watermark, watermarkColorHex) }) {
                Text("Save Settings")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun PageSizeDialog(
    initialPageSize: String,
    initialOrientation: String,
    initialCustomWidth: Float,
    initialCustomHeight: Float,
    onDismiss: () -> Unit,
    onConfirm: (pageSize: String, orientation: String, customWidth: Float, customHeight: Float) -> Unit
) {
    var selectedSize by remember { mutableStateOf(initialPageSize) }
    var selectedOrientation by remember { mutableStateOf(initialOrientation) }
    var widthInput by remember { mutableStateOf(initialCustomWidth.toInt().toString()) }
    var heightInput by remember { mutableStateOf(initialCustomHeight.toInt().toString()) }
    var dropdownExpanded by remember { mutableStateOf(false) }

    val standardSizes = listOf(
        "a0" to "A0 (841 x 1189 mm)",
        "a1" to "A1 (594 x 841 mm)",
        "a2" to "A2 (420 x 594 mm)",
        "a3" to "A3 (297 x 420 mm)",
        "a4" to "A4 (210 x 297 mm)",
        "a5" to "A5 (148 x 210 mm)",
        "a6" to "A6 (105 x 148 mm)",
        "a7" to "A7 (74 x 105 mm)",
        "a8" to "A8 (52 x 74 mm)",
        "a9" to "A9 (37 x 52 mm)",
        "a10" to "A10 (26 x 37 mm)",
        "letter" to "Letter (8.5 x 11 in)",
        "legal" to "Legal (8.5 x 14 in)"
    )

    val isStandardSize = selectedSize != "auto" && selectedSize != "custom"
    val dropdownLabel = standardSizes.firstOrNull { it.first == selectedSize }?.second ?: "A4 (210 x 297 mm)"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Page Sizing & Orientation", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Select Page Layout", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.secondary)
                    
                    // 1. Fit Image (Auto)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedSize = "auto" }
                            .padding(vertical = 4.dp)
                    ) {
                        RadioButton(
                            selected = selectedSize == "auto",
                            onClick = { selectedSize = "auto" }
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(text = "Fit Image (Auto)", fontSize = 14.sp)
                    }

                    // 2. Standard Size Dropdown
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { 
                                if (!isStandardSize) {
                                    selectedSize = "a4" // Default standard size
                                }
                            }
                            .padding(vertical = 4.dp)
                    ) {
                        RadioButton(
                            selected = isStandardSize,
                            onClick = { 
                                if (!isStandardSize) {
                                    selectedSize = "a4"
                                }
                            }
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Standard Page Size", fontSize = 14.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Box(modifier = Modifier.fillMaxWidth()) {
                                OutlinedButton(
                                    onClick = { 
                                        selectedSize = if (isStandardSize) selectedSize else "a4"
                                        dropdownExpanded = true 
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(text = dropdownLabel, fontSize = 13.sp)
                                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                    }
                                }
                                DropdownMenu(
                                    expanded = dropdownExpanded,
                                    onDismissRequest = { dropdownExpanded = false },
                                    modifier = Modifier.fillMaxWidth(0.7f)
                                ) {
                                    standardSizes.forEach { (size, label) ->
                                        DropdownMenuItem(
                                            text = { Text(label, fontSize = 13.sp) },
                                            onClick = {
                                                selectedSize = size
                                                dropdownExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 3. Custom Size
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedSize = "custom" }
                            .padding(vertical = 4.dp)
                    ) {
                        RadioButton(
                            selected = selectedSize == "custom",
                            onClick = { selectedSize = "custom" }
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(text = "Custom Sizing", fontSize = 14.sp)
                    }
                }

                // Custom Inputs
                if (selectedSize == "custom") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Custom Dimensions (in points)", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.secondary)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedTextField(
                                value = widthInput,
                                onValueChange = { widthInput = it.filter { c -> c.isDigit() } },
                                label = { Text("Width (pt)") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = heightInput,
                                onValueChange = { heightInput = it.filter { c -> c.isDigit() } },
                                label = { Text("Height (pt)") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                        }
                    }
                }

                // Page Orientation (Not for Auto-fit)
                if (selectedSize != "auto") {
                    Column {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), modifier = Modifier.padding(vertical = 8.dp))
                        Text("Page Orientation", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.secondary)
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            val orientationOptions = listOf("portrait" to "Portrait", "landscape" to "Landscape")
                            orientationOptions.forEach { (orient, label) ->
                                val isSel = selectedOrientation == orient
                                Button(
                                    onClick = { selectedOrientation = orient },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                        contentColor = if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(
                                        imageVector = if (orient == "portrait") Icons.Default.Portrait else Icons.Default.Landscape,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(label, fontSize = 12.sp)
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
                    val w = widthInput.toFloatOrNull() ?: 595f
                    val h = heightInput.toFloatOrNull() ?: 842f
                    onConfirm(selectedSize, selectedOrientation, w, h)
                }
            ) {
                Text("Apply Sizing")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun EditImageDialog(
    item: ImageItem,
    onDismiss: () -> Unit,
    onSave: (rotation: Float, flipH: Boolean, flipV: Boolean, filter: String, cropRatio: String) -> Unit,
    onMoveLeft: () -> Unit,
    onMoveRight: () -> Unit,
    onDelete: () -> Unit,
    isMoveLeftEnabled: Boolean,
    isMoveRightEnabled: Boolean
) {
    var rotation by remember { mutableStateOf(item.rotation) }
    var flipH by remember { mutableStateOf(item.isFlippedH) }
    var flipV by remember { mutableStateOf(item.isFlippedV) }
    var filter by remember { mutableStateOf(item.filter) }
    var cropRatio by remember { mutableStateOf(item.cropRatio) }
    
    val filterOptions = listOf(
        "none" to "Original",
        "grayscale" to "Grayscale",
        "sepia" to "Sepia",
        "bw" to "B&W",
        "invert" to "Invert"
    )
    
    val cropOptions = listOf(
        "original" to "Original Ratio",
        "1:1" to "Square (1:1)",
        "4:3" to "Standard (4:3)",
        "16:9" to "Widescreen (16:9)",
        "3:4" to "Portrait (3:4)",
        "9:16" to "Story (9:16)"
    )
    
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.9f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Edit Image",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.05f))
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val previewAspect = when (cropRatio) {
                        "1:1" -> 1f
                        "4:3" -> 4f / 3f
                        "16:9" -> 16f / 9f
                        "3:4" -> 3f / 4f
                        "9:16" -> 9f / 16f
                        else -> null
                    }
                    
                    val modifierWithAspect = if (previewAspect != null) {
                        Modifier
                            .fillMaxHeight(0.8f)
                            .aspectRatio(previewAspect)
                            .clip(RoundedCornerShape(4.dp))
                    } else {
                        Modifier.fillMaxHeight(0.8f)
                    }
                    
                    Box(modifier = modifierWithAspect, contentAlignment = Alignment.Center) {
                        LocalImageThumbnailForEdit(
                            uri = item.originalUri,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer(
                                    rotationZ = rotation,
                                    scaleX = if (flipH) -1f else 1f,
                                    scaleY = if (flipV) -1f else 1f
                                ),
                            colorFilter = getComposeColorFilter(filter)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Rotate & Flip:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.secondary)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            IconButton(
                                onClick = { rotation = (rotation + 90f) % 360f },
                                colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Icon(Icons.Default.RotateRight, contentDescription = "Rotate Right")
                            }
                            IconButton(
                                onClick = { flipH = !flipH },
                                colors = IconButtonDefaults.iconButtonColors(
                                    containerColor = if (flipH) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Icon(Icons.Default.Flip, contentDescription = "Flip Horizontal")
                            }
                            IconButton(
                                onClick = { flipV = !flipV },
                                colors = IconButtonDefaults.iconButtonColors(
                                    containerColor = if (flipV) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Icon(Icons.Default.Flip, contentDescription = "Flip Vertical", modifier = Modifier.graphicsLayer(rotationZ = 90f))
                            }
                        }
                    }
                    
                    Column {
                        Text("Crop Ratio:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.secondary)
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        var expandedCrop by remember { mutableStateOf(false) }
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = { expandedCrop = true },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(cropOptions.firstOrNull { it.first == cropRatio }?.second ?: cropRatio)
                            }
                            DropdownMenu(
                                expanded = expandedCrop,
                                onDismissRequest = { expandedCrop = false },
                                modifier = Modifier.fillMaxWidth(0.8f)
                            ) {
                                cropOptions.forEach { (option, label) ->
                                    DropdownMenuItem(
                                        text = { Text(label) },
                                        onClick = {
                                            cropRatio = option
                                            expandedCrop = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                    
                    Column {
                        Text("Filter Effect:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.secondary)
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            itemsIndexed(filterOptions) { _, (opt, label) ->
                                val isSel = filter == opt
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                        .clickable { filter = opt }
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        color = if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
                
                // Manage Controls
                Spacer(modifier = Modifier.height(12.dp))
                Column {
                    Text("Manage Image", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.secondary)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onMoveLeft,
                            enabled = isMoveLeftEnabled,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            modifier = Modifier.weight(1.5f),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.Default.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Move Left", fontSize = 11.sp)
                        }

                        Button(
                            onClick = onMoveRight,
                            enabled = isMoveRightEnabled,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            modifier = Modifier.weight(1.5f),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text("Move Right", fontSize = 11.sp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                        }

                        Button(
                            onClick = onDelete,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError
                            ),
                            modifier = Modifier.weight(1.2f),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Delete", fontSize = 11.sp)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { onSave(rotation, flipH, flipV, filter, cropRatio) }
                    ) {
                        Text("Save Edits")
                    }
                }
            }
        }
    }
}

@Composable
fun ColorPickerDialog(
    initialColorHex: String,
    onDismiss: () -> Unit,
    onConfirm: (hex: String, setAsDefault: Boolean) -> Unit
) {
    val context = LocalContext.current
    val initialHsv = remember(initialColorHex) {
        val c = try { android.graphics.Color.parseColor(initialColorHex) } catch (e: Exception) { android.graphics.Color.GRAY }
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(c, hsv)
        hsv
    }
    
    var h by remember { mutableFloatStateOf(initialHsv[0]) }
    var s by remember { mutableFloatStateOf(initialHsv[1]) }
    var v by remember { mutableFloatStateOf(initialHsv[2]) }
    var setAsDefault by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = null,
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(h) {
                                    detectDragGestures { change, _ ->
                                        change.consume()
                                        val x = change.position.x.coerceIn(0f, size.width.toFloat())
                                        val y = change.position.y.coerceIn(0f, size.height.toFloat())
                                        s = x / size.width.toFloat()
                                        v = 1f - (y / size.height.toFloat())
                                    }
                                }
                                .pointerInput(h) {
                                    detectTapGestures { offset ->
                                        val x = offset.x.coerceIn(0f, size.width.toFloat())
                                        val y = offset.y.coerceIn(0f, size.height.toFloat())
                                        s = x / size.width.toFloat()
                                        v = 1f - (y / size.height.toFloat())
                                    }
                                }
                        ) {
                            val baseColor = Color(android.graphics.Color.HSVToColor(floatArrayOf(h, 1f, 1f)))
                            drawRect(color = baseColor)
                            
                            val whiteGradient = Brush.horizontalGradient(
                                colors = listOf(Color.White, Color.Transparent)
                            )
                            drawRect(brush = whiteGradient, blendMode = BlendMode.SrcOver)
                            
                            val blackGradient = Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black)
                            )
                            drawRect(brush = blackGradient, blendMode = BlendMode.SrcOver)
                            
                            val cx = s * size.width
                            val cy = (1f - v) * size.height
                            drawCircle(
                                color = Color.White,
                                radius = 8.dp.toPx(),
                                center = Offset(cx, cy),
                                style = Stroke(width = 2.dp.toPx())
                            )
                            drawCircle(
                                color = Color.Black,
                                radius = 9.dp.toPx(),
                                center = Offset(cx, cy),
                                style = Stroke(width = 1.dp.toPx())
                            )
                        }
                    }
                    
                    Box(
                        modifier = Modifier
                            .width(30.dp)
                            .fillMaxHeight()
                    ) {
                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(Unit) {
                                    detectDragGestures { change, _ ->
                                        change.consume()
                                        val y = change.position.y.coerceIn(0f, size.height.toFloat())
                                        h = (y / size.height.toFloat()) * 360f
                                    }
                                }
                                .pointerInput(Unit) {
                                    detectTapGestures { offset ->
                                        val y = offset.y.coerceIn(0f, size.height.toFloat())
                                        h = (y / size.height.toFloat()) * 360f
                                    }
                                }
                        ) {
                            val hueColors = List(360) { Color(android.graphics.Color.HSVToColor(floatArrayOf(it.toFloat(), 1f, 1f))) }
                            val hueGradient = Brush.verticalGradient(hueColors)
                            drawRect(brush = hueGradient)
                            
                            val hy = (h / 360f) * size.height
                            drawRect(
                                color = Color.White,
                                topLeft = Offset(0f, hy - 2.dp.toPx()),
                                size = Size(size.width, 4.dp.toPx()),
                                style = Stroke(width = 1.dp.toPx())
                            )
                            drawRect(
                                color = Color.Black,
                                topLeft = Offset(0f, hy - 3.dp.toPx()),
                                size = Size(size.width, 6.dp.toPx()),
                                style = Stroke(width = 1.dp.toPx())
                            )
                        }
                    }
                }
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable { setAsDefault = !setAsDefault }
                ) {
                    Checkbox(
                        checked = setAsDefault,
                        onCheckedChange = { setAsDefault = it }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Set as default")
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val colorInt = android.graphics.Color.HSVToColor(floatArrayOf(h, s, v))
                    val hex = String.format("#%06X", 0xFFFFFF and colorInt)
                    onConfirm(hex, setAsDefault)
                }
            ) {
                Text("OK", color = Color(0xFF00BFA5), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("CANCEL", color = Color(0xFF00BFA5), fontWeight = FontWeight.Bold)
            }
        }
    )
}
