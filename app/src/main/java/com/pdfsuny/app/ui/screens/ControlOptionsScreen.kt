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
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.ControlCamera
import androidx.compose.material.icons.filled.PlayArrow
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
fun ControlOptionsScreen(
    onBack: () -> Unit,
    onPdfSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE) }
    
    val database = remember { AppDatabase.getDatabase(context) }
    val recentPdfs by database.recentPdfDao().getAllRecentPdfs().collectAsState(initial = emptyList())

    var isVerticalScroll by remember { mutableStateOf(sharedPrefs.getBoolean("is_vertical_scroll", true)) }
    var pageColorType by remember { mutableStateOf(sharedPrefs.getString("reader_page_color_type", "original") ?: "original") }

    fun launchWithFlag(flagKey: String) {
        if (recentPdfs.isNotEmpty()) {
            sharedPrefs.edit().putBoolean(flagKey, true).apply()
            val targetPath = recentPdfs.first().path
            onBack() // Navigate back to clear navigation backstack before showing pdf viewer
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
                    Text("Control Options", fontWeight = FontWeight.Bold)
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
            // Document Control Preferences Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.ControlCamera, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Reading Controls", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Auto Scroll Action Item
                    Button(
                        onClick = { launchWithFlag("auto_scroll_on_launch") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Start Auto Scroll")
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // TTS Action Item
                    Button(
                        onClick = { launchWithFlag("tts_on_launch") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Start TTS Reading")
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

                    // Scroll Direction Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Vertical Scrolling Direction", fontWeight = FontWeight.SemiBold)
                            Text("Scroll pages vertically (ON) or horizontally (OFF)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                        }
                        Switch(
                            checked = isVerticalScroll,
                            onCheckedChange = { 
                                isVerticalScroll = it
                                sharedPrefs.edit().putBoolean("is_vertical_scroll", it).apply()
                                Toast.makeText(context, "Scroll direction updated.", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            }

            // Reader Theme Color Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Reader Page Color Mode", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val pageColors = listOf(
                            "original" to ("Original" to Color.White),
                            "sepia" to ("Sepia" to Color(0xFFF4ECD8)),
                            "mint" to ("Mint" to Color(0xFFE8F5E9)),
                            "warm" to ("Warm" to Color(0xFFFDF2E9))
                        )
                        pageColors.forEach { (colorId, pair) ->
                            val (label, displayColor) = pair
                            val isSelected = pageColorType == colorId
                            
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                                    .border(
                                        width = 2.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .clickable {
                                        pageColorType = colorId
                                        sharedPrefs.edit().putString("reader_page_color_type", colorId).apply()
                                        Toast.makeText(context, "Page color set to $label.", Toast.LENGTH_SHORT).show()
                                    }
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(20.dp)
                                            .clip(CircleShape)
                                            .background(displayColor)
                                            .border(
                                                width = 1.dp,
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray,
                                                shape = CircleShape
                                            )
                                    )
                                    Text(
                                        text = label,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
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
