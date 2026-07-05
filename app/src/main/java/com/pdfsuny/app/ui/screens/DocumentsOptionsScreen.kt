package com.pdfsuny.app.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.BorderColor
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pdfsuny.app.data.AppDatabase
import com.pdfsuny.app.ui.components.TooltipIconButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentsOptionsScreen(
    onBack: () -> Unit,
    onPdfSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE) }
    
    val database = remember { AppDatabase.getDatabase(context) }
    val recentPdfs by database.recentPdfDao().getAllRecentPdfs().collectAsState(initial = emptyList())

    fun launchWithFlag(flagKey: String) {
        if (recentPdfs.isNotEmpty()) {
            sharedPrefs.edit().putBoolean(flagKey, true).apply()
            val targetPath = recentPdfs.first().path
            onBack()
            onPdfSelected(targetPath)
            Toast.makeText(context, "Opening recent: ${recentPdfs.first().name}", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Please open a PDF file from the Home workspace first.", Toast.LENGTH_LONG).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Documents Options", fontWeight = FontWeight.Bold)
                },
                navigationIcon = {
                    TooltipIconButton(onClick = onBack, tooltipText = "Back") {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Options Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Description, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Document Management", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Open Bookmarks Button
                    Button(
                        onClick = { launchWithFlag("open_bookmarks_on_launch") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Bookmarks, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Open Bookmarks")
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // Text Highlights Button
                    Button(
                        onClick = { launchWithFlag("open_highlights_on_launch") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) {
                        Icon(Icons.Default.BorderColor, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Text Highlights")
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // Edit Page / Document Button
                    Button(
                        onClick = { launchWithFlag("open_editor_on_launch") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                    ) {
                        Icon(Icons.Default.EditNote, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Edit Page / Document")
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // Share Document Button
                    OutlinedButton(
                        onClick = { launchWithFlag("share_on_launch") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Share Document")
                    }
                }
            }
        }
    }
}
