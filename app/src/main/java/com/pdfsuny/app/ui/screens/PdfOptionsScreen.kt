package com.pdfsuny.app.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pdfsuny.app.data.AppDatabase
import com.pdfsuny.app.ui.components.TooltipIconButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfOptionsScreen(
    onBack: () -> Unit,
    onPdfSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE) }
    
    val database = remember { AppDatabase.getDatabase(context) }
    val recentPdfs by database.recentPdfDao().getAllRecentPdfs().collectAsState(initial = emptyList())

    var useOpenGlEngine by remember { mutableStateOf(sharedPrefs.getBoolean("use_opengl_engine", false)) }

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
                    Text("PDF Options", fontWeight = FontWeight.Bold)
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
            // PDF Tools Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("PDF Utilities", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Edit PDF Text Button
                    Button(
                        onClick = { launchWithFlag("open_word_editor_on_launch") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Edit PDF Text")
                    }
                }
            }

            // Rendering Engine Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Reading Engine configuration", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // OpenGL Reading Engine Selector Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                useOpenGlEngine = true
                                sharedPrefs.edit().putBoolean("use_opengl_engine", true).apply()
                                Toast.makeText(context, "OpenGL GPU Engine Enabled", Toast.LENGTH_SHORT).show()
                            }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = useOpenGlEngine,
                            onClick = {
                                useOpenGlEngine = true
                                sharedPrefs.edit().putBoolean("use_opengl_engine", true).apply()
                                Toast.makeText(context, "OpenGL GPU Engine Enabled", Toast.LENGTH_SHORT).show()
                            }
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("OpenGL Reading Engine", fontWeight = FontWeight.SemiBold)
                            Text("GPU-Accelerated page scaling & smooth gestures", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    // Normal/Standard View Selector Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                useOpenGlEngine = false
                                sharedPrefs.edit().putBoolean("use_opengl_engine", false).apply()
                                Toast.makeText(context, "Standard Reading Engine Enabled", Toast.LENGTH_SHORT).show()
                            }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = !useOpenGlEngine,
                            onClick = {
                                useOpenGlEngine = false
                                sharedPrefs.edit().putBoolean("use_opengl_engine", false).apply()
                                Toast.makeText(context, "Standard Reading Engine Enabled", Toast.LENGTH_SHORT).show()
                            }
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Normal/Standard View", fontWeight = FontWeight.SemiBold)
                            Text("Standard canvas-based bitmap rendering layout", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                        }
                    }
                }
            }
        }
    }
}
