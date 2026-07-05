package com.pdfsuny.app.ui.screens

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.pdfsuny.app.ui.components.TooltipIconButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pdfsuny.app.data.AppDatabase
import com.pdfsuny.app.data.entities.Bookmark
import com.pdfsuny.app.data.entities.HighlightAnnotation
import com.pdfsuny.app.ui.PdfSessionViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.tom_roush.pdfbox.pdmodel.PDDocument
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ChapterItem(val title: String, val pageIndex: Int)

fun loadPdfChapters(context: Context, pdfUriStr: String): List<ChapterItem> {
    val list = mutableListOf<ChapterItem>()
    try {
        val uri = Uri.parse(pdfUriStr)
        val inputStream = context.contentResolver.openInputStream(uri) ?: return emptyList()
        PDDocument.load(inputStream).use { doc ->
            val outline = doc.documentCatalog.documentOutline
            if (outline != null) {
                var currentItem = outline.firstChild
                while (currentItem != null) {
                    val title = currentItem.title ?: ""
                    var pageIndex = -1
                    try {
                        val page = currentItem.findDestinationPage(doc)
                        if (page != null) {
                            pageIndex = doc.pages.indexOf(page)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    if (pageIndex >= 0) {
                        list.add(ChapterItem(title, pageIndex))
                    }
                    currentItem = currentItem.nextSibling
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return list
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarkScreen(
    pdfPath: String,
    currentPageIndex: Int,
    onBack: () -> Unit,
    onBookmarkClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    val sessionViewModel: PdfSessionViewModel = viewModel(
        viewModelStoreOwner = LocalActivity.current as ComponentActivity
    )
    
    val database = remember { AppDatabase.getDatabase(context) }
    val bookmarks by database.bookmarkDao().getBookmarksForPdf(pdfPath).collectAsState(initial = emptyList())
    val highlights by database.highlightDao().getHighlightsForPdf(pdfPath).collectAsState(initial = emptyList())
    val pdfTextService = remember { com.pdfsuny.app.services.PdfTextService() }
    
    var selectedTab by remember { mutableStateOf(1) } // Default to Bookmarks (tab index 1)
    var chapters by remember { mutableStateOf<List<ChapterItem>>(emptyList()) }
    
    LaunchedEffect(pdfPath) {
        withContext(Dispatchers.IO) {
            chapters = loadPdfChapters(context, pdfPath)
        }
    }
    
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy HH:mm", Locale.getDefault()) }

    Scaffold(
        modifier = modifier,
        topBar = {
            // Custom tab bar exactly matching the user's design reference
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF3F4958)) // Dark Slate background
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
                
                // Chapters Tab
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { selectedTab = 0 }
                        .padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Chapters",
                        color = if (selectedTab == 0) Color.White else Color.LightGray,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    if (selectedTab == 0) {
                        Box(
                            modifier = Modifier
                                .width(80.dp)
                                .height(3.dp)
                                .background(Color(0xFFFF9800)) // Orange indicator
                        )
                    }
                }

                // Bookmarks Tab
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { selectedTab = 1 }
                        .padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Bookmarks",
                        color = if (selectedTab == 1) Color.White else Color.LightGray,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    if (selectedTab == 1) {
                        Box(
                            modifier = Modifier
                                .width(90.dp)
                                .height(3.dp)
                                .background(Color(0xFFFF9800)) // Orange indicator
                        )
                    }
                }

                // Notes Tab
                Column(
                    modifier = Modifier
                        .weight(0.5f)
                        .clickable { selectedTab = 2 }
                        .padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Description, // note sheet icon
                        contentDescription = "Notes",
                        tint = if (selectedTab == 2) Color.White else Color.LightGray
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    if (selectedTab == 2) {
                        Box(
                            modifier = Modifier
                                .width(24.dp)
                                .height(3.dp)
                                .background(Color(0xFFFF9800)) // Orange indicator
                        )
                    }
                }

                // Sort icon on the right
                IconButton(onClick = { /* Toggle sorting */ }) {
                    Icon(
                        imageVector = Icons.Default.SwapVert,
                        contentDescription = "Sort",
                        tint = Color.White
                    )
                }
            }
        },
        bottomBar = {
            // Elegant bottom options menu bar
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFE3E9F5)) // light grey blue
                    .padding(top = 10.dp, bottom = 12.dp, start = 16.dp, end = 16.dp)
            ) {
                // Options list: Bookmark, Note, Highlight
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { selectedTab = 1 }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bookmark,
                            contentDescription = null,
                            tint = Color(0xFF673AB7),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Bookmark", style = MaterialTheme.typography.bodyMedium, color = Color.DarkGray)
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { selectedTab = 1 }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = null,
                            tint = Color(0xFF673AB7),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Note", style = MaterialTheme.typography.bodyMedium, color = Color.DarkGray)
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { selectedTab = 2 }
                    ) {
                        Icon(
                            imageVector = Icons.Default.BorderColor,
                            contentDescription = null,
                            tint = Color(0xFF673AB7),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Highlight", style = MaterialTheme.typography.bodyMedium, color = Color.DarkGray)
                    }

                    Text(">>", style = MaterialTheme.typography.bodyMedium, color = Color.Gray, fontWeight = FontWeight.Bold)
                }

                // Action Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Branch / Tree Icon
                    Icon(
                        imageVector = Icons.Default.AccountTree,
                        contentDescription = "Branch",
                        tint = Color(0xFF673AB7),
                        modifier = Modifier.size(24.dp)
                    )

                    // Wide Add Bookmark Button
                    Button(
                        onClick = {
                            scope.launch {
                                val snippet = try {
                                    pdfTextService.extractTextFromPage(context, Uri.parse(pdfPath), currentPageIndex)
                                        .take(150)
                                        .trim()
                                } catch (e: Exception) {
                                    ""
                                }
                                database.bookmarkDao().insertBookmark(
                                    Bookmark(
                                        pdfPath = pdfPath,
                                        pageNumber = currentPageIndex,
                                        note = if (snippet.isNotBlank()) snippet else "Bookmark added at page ${currentPageIndex + 1}",
                                        timestamp = System.currentTimeMillis()
                                    )
                                )
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF455A64)), // Blue grey button
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 16.dp)
                    ) {
                        Text("ADD NEW BOOKMARK", color = Color.White, fontWeight = FontWeight.Bold)
                    }

                    // Search Icon
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = Color.DarkGray,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))

                    // Settings Icon
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = Color.DarkGray,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFFE8EDF5)) // Light background
        ) {
            when (selectedTab) {
                0 -> { // Chapters Tab
                    if (chapters.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No chapters found in this document Outline.", color = Color.Gray, style = MaterialTheme.typography.bodyMedium)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 16.dp)
                        ) {
                            items(chapters) { chapter ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            sessionViewModel.jumpToPage(chapter.pageIndex)
                                            onBookmarkClick(chapter.pageIndex)
                                        }
                                        .padding(horizontal = 16.dp, vertical = 14.dp)
                                ) {
                                    Text(
                                        text = chapter.title,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.Black
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Page ${chapter.pageIndex + 1}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.Gray
                                    )
                                }
                                HorizontalDivider(color = Color.LightGray.copy(alpha = 0.5f), thickness = 0.5.dp)
                            }
                        }
                    }
                }
                1 -> { // Bookmarks Tab
                    if (bookmarks.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No bookmarks added for this document.", color = Color.Gray, style = MaterialTheme.typography.bodyMedium)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 16.dp)
                        ) {
                            items(bookmarks) { bookmark ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            sessionViewModel.jumpToPage(bookmark.pageNumber)
                                            onBookmarkClick(bookmark.pageNumber)
                                        }
                                        .padding(horizontal = 16.dp, vertical = 12.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Bookmark,
                                            contentDescription = "Bookmark",
                                            tint = Color(0xFFD32F2F), // Red bookmark icon
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = dateFormat.format(Date(bookmark.timestamp)),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.Gray
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(
                                            text = "Page ${bookmark.pageNumber + 1}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.Gray
                                        )
                                        Spacer(modifier = Modifier.weight(1f))
                                        IconButton(
                                            onClick = {
                                                scope.launch {
                                                    database.bookmarkDao().deleteBookmark(bookmark)
                                                }
                                            },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Delete",
                                                tint = Color.Gray.copy(alpha = 0.7f),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = bookmark.note,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color.DarkGray,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                HorizontalDivider(color = Color.LightGray.copy(alpha = 0.5f), thickness = 0.5.dp)
                            }
                        }
                    }
                }
                2 -> { // Notes / Highlights Tab
                    if (highlights.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No highlights or notes found.", color = Color.Gray, style = MaterialTheme.typography.bodyMedium)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 16.dp)
                        ) {
                            items(highlights) { highlight ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 12.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        // Draw small round circle for color preview
                                        val colorVal = try {
                                            Color(android.graphics.Color.parseColor(highlight.colorHex))
                                        } catch (e: Exception) {
                                            Color.Yellow
                                        }
                                        Box(
                                            modifier = Modifier
                                                .size(12.dp)
                                                .clip(CircleShape)
                                                .background(colorVal)
                                                .border(0.5.dp, Color.Gray, CircleShape)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = dateFormat.format(Date(highlight.timestamp)),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.Gray
                                        )
                                        Spacer(modifier = Modifier.weight(1f))
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
                                                tint = Color.Gray.copy(alpha = 0.7f),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = highlight.phrase,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color.Black
                                    )
                                }
                                HorizontalDivider(color = Color.LightGray.copy(alpha = 0.5f), thickness = 0.5.dp)
                            }
                        }
                    }
                }
            }
        }
    }
}
