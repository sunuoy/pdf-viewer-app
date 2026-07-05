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
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Contrast
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
fun MiscOptionsScreen(
    onBack: () -> Unit,
    onPdfSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE) }
    
    val database = remember { AppDatabase.getDatabase(context) }
    val recentPdfs by database.recentPdfDao().getAllRecentPdfs().collectAsState(initial = emptyList())

    var nightModeDefault by remember { mutableStateOf(sharedPrefs.getBoolean("night_mode_default", false)) }

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
                    Text("Misc Options", fontWeight = FontWeight.Bold)
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
            // Theme Options Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Contrast, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Appearance Settings", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Dark Theme Inversion", fontWeight = FontWeight.SemiBold)
                            Text("Automatically invert page colors when launching PDF viewer", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                        }
                        Switch(
                            checked = nightModeDefault,
                            onCheckedChange = { 
                                nightModeDefault = it
                                sharedPrefs.edit().putBoolean("night_mode_default", it).apply()
                                Toast.makeText(context, "Dark Theme Inversion updated.", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            }

            // Tools & Settings Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Additional Reader Features", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Translation Settings Button
                    OutlinedButton(
                        onClick = { launchWithFlag("translation_on_launch") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Translate, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Translation Settings")
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // Offline Translation Models Button
                    OutlinedButton(
                        onClick = { launchWithFlag("models_on_launch") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Offline Translation Models")
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // Ruler Settings Button
                    OutlinedButton(
                        onClick = { launchWithFlag("ruler_on_launch") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Tune, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Ruler Settings")
                    }
                }
            }
        }
    }
}
