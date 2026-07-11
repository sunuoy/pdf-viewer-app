package com.pdfsuny.app.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.pdfsuny.app.MainActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import androidx.core.content.FileProvider

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DisplaySettings
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.pdfsuny.app.ui.components.TooltipIconButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE) }

    var autoSaveLocationPrompt by remember { mutableStateOf(sharedPrefs.getBoolean("auto_save_location_prompt", true)) }
    var highResRendering by remember { mutableStateOf(sharedPrefs.getBoolean("high_res_rendering", true)) }
    var nightModeDefault by remember { mutableStateOf(sharedPrefs.getBoolean("night_mode_default", false)) }
    var isVerticalScroll by remember { mutableStateOf(sharedPrefs.getBoolean("is_vertical_scroll", true)) }
    var useOpenGlEngine by remember { mutableStateOf(sharedPrefs.getBoolean("use_opengl_engine", false)) }

    val scope = rememberCoroutineScope()
    var isCheckingUpdate by remember { mutableStateOf(false) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    var showNoUpdateDialog by remember { mutableStateOf(false) }
    var isDownloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableFloatStateOf(0f) }
    var latestVersionName by remember { mutableStateOf("") }
    var downloadUrlPath by remember { mutableStateOf("") }

    val appInfo = remember {
        try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            val code = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                pInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                pInfo.versionCode.toLong()
            }
            Pair(pInfo.versionName ?: "1.4.0", code)
        } catch (e: Exception) {
            Pair("1.4.0", 40L)
        }
    }

    val appOpenTimeFormatted = remember {
        try {
            SimpleDateFormat("hh:mm:ss a", Locale.getDefault()).format(Date(MainActivity.appOpenTimeMillis))
        } catch (e: Exception) {
            "N/A"
        }
    }

    var uptimeText by remember { mutableStateOf("0s") }

    LaunchedEffect(Unit) {
        while (true) {
            val durationMs = System.currentTimeMillis() - MainActivity.appOpenTimeMillis
            val seconds = (durationMs / 1000) % 60
            val minutes = (durationMs / (1000 * 60)) % 60
            val hours = (durationMs / (1000 * 60 * 60))
            uptimeText = when {
                hours > 0 -> "${hours}h ${minutes}m ${seconds}s"
                minutes > 0 -> "${minutes}m ${seconds}s"
                else -> "${seconds}s"
            }
            delay(1000L)
        }
    }


    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Settings", fontWeight = FontWeight.Bold)
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
            // Document Reader Preferences Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.DisplaySettings, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Reading & Rendering", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("High Quality Page Rendering", fontWeight = FontWeight.SemiBold)
                            Text("Render 2x resolution page bitmaps for ultra crisp text", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                        }
                        Switch(
                            checked = highResRendering,
                            onCheckedChange = { 
                                highResRendering = it
                                sharedPrefs.edit().putBoolean("high_res_rendering", it).apply()
                            }
                        )
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Default Dark Mode Inversion", fontWeight = FontWeight.SemiBold)
                            Text("Automatically invert colors when launching document viewer", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                        }
                        Switch(
                            checked = nightModeDefault,
                            onCheckedChange = { 
                                nightModeDefault = it
                                sharedPrefs.edit().putBoolean("night_mode_default", it).apply()
                            }
                        )
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Vertical Scrolling Direction", fontWeight = FontWeight.SemiBold)
                            Text("Scroll pages up and down continuously (disable for horizontal flipping)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                        }
                        Switch(
                            checked = isVerticalScroll,
                            onCheckedChange = { 
                                isVerticalScroll = it
                                sharedPrefs.edit().putBoolean("is_vertical_scroll", it).apply()
                            }
                        )
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("OpenGL Reading Engine", fontWeight = FontWeight.SemiBold)
                            Text("GPU-Accelerated page rendering for ultra fast zooming and scrolling", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                        }
                        Switch(
                            checked = useOpenGlEngine,
                            onCheckedChange = { 
                                useOpenGlEngine = it
                                sharedPrefs.edit().putBoolean("use_opengl_engine", it).apply()
                                Toast.makeText(context, if (it) "OpenGL GPU Engine Enabled" else "OpenGL GPU Engine Disabled (Normal View)", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            }

            // Storage & Conversion Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.FolderSpecial, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Storage & Export Options", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Always Prompt Save Location", fontWeight = FontWeight.SemiBold)
                            Text("Prompt for destination folder before saving exported tools", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                        }
                        Switch(
                            checked = autoSaveLocationPrompt,
                            onCheckedChange = { 
                                autoSaveLocationPrompt = it
                                sharedPrefs.edit().putBoolean("auto_save_location_prompt", it).apply()
                            }
                        )
                    }
                }
            }

            // About & App Information Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                elevation = CardDefaults.cardElevation(0.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("About PDF Reader Suite", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Version", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                            Text("${appInfo.first} (Build ${appInfo.second})", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Launched At", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                            Text(appOpenTimeFormatted, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            Text("Session Uptime", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                            Text(uptimeText, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "A high-performance offline PDF & multi-format document workspace for Android built with Jetpack Compose, Coroutines, Room Database, and Storage Access Framework.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = {
                            if (!isCheckingUpdate && !isDownloading) {
                                isCheckingUpdate = true
                                scope.launch {
                                    try {
                                        val result = withContext(Dispatchers.IO) {
                                            val url = URL("https://api.github.com/repos/sunuoy/pdf-viewer-app/releases/latest")
                                            val connection = url.openConnection() as HttpURLConnection
                                            connection.requestMethod = "GET"
                                            connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
                                            connection.setRequestProperty("User-Agent", "Mozilla/5.0")
                                            connection.connectTimeout = 10000
                                            connection.readTimeout = 10000
                                            connection.connect()

                                            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                                                val response = connection.inputStream.bufferedReader().use { it.readText() }
                                                val json = JSONObject(response)
                                                val tag = json.optString("tag_name", "")
                                                val assets = json.optJSONArray("assets")
                                                var apkUrl = ""
                                                if (assets != null) {
                                                    for (i in 0 until assets.length()) {
                                                        val asset = assets.getJSONObject(i)
                                                        val name = asset.optString("name", "")
                                                        if (name.endsWith(".apk")) {
                                                            apkUrl = asset.optString("browser_download_url", "")
                                                            break
                                                        }
                                                    }
                                                }
                                                Triple(true, tag, apkUrl)
                                            } else {
                                                Triple(false, "HTTP ${connection.responseCode}", "")
                                            }
                                        }

                                        if (result.first) {
                                            val tag = result.second
                                            val apkUrl = result.third
                                            val currentVer = appInfo.first
                                            if (tag.isNotEmpty() && isNewerVersion(currentVer, tag)) {
                                                latestVersionName = tag
                                                downloadUrlPath = apkUrl
                                                showUpdateDialog = true
                                            } else {
                                                showNoUpdateDialog = true
                                            }
                                        } else {
                                            Toast.makeText(context, "Failed to check update: ${result.second}", Toast.LENGTH_LONG).show()
                                        }
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                        Toast.makeText(context, "Error checking update: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                                    } finally {
                                        isCheckingUpdate = false
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isCheckingUpdate && !isDownloading
                    ) {
                        if (isCheckingUpdate) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Checking for Updates...")
                        } else {
                            Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Check for Updates")
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Developed with ❤️ for seamless document viewing.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
            
            if (showUpdateDialog) {
                AlertDialog(
                    onDismissRequest = { showUpdateDialog = false },
                    title = { Text("Update Available", fontWeight = FontWeight.Bold) },
                    text = {
                        Text("A new version ($latestVersionName) is available. Would you like to download and install it now?")
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                showUpdateDialog = false
                                if (downloadUrlPath.isNotEmpty()) {
                                    isDownloading = true
                                    downloadProgress = 0f
                                    scope.launch {
                                        val file = downloadApkFile(context, downloadUrlPath) { progress ->
                                            downloadProgress = progress
                                        }
                                        isDownloading = false
                                        if (file != null) {
                                            triggerApkInstallation(context, file)
                                        } else {
                                            Toast.makeText(context, "Failed to download update APK.", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                } else {
                                    Toast.makeText(context, "No APK asset found in the latest release.", Toast.LENGTH_LONG).show()
                                }
                            }
                        ) {
                            Text("Update")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showUpdateDialog = false }) {
                            Text("Later")
                        }
                    }
                )
            }

            if (showNoUpdateDialog) {
                AlertDialog(
                    onDismissRequest = { showNoUpdateDialog = false },
                    title = { Text("Up to Date", fontWeight = FontWeight.Bold) },
                    text = {
                        Text("You are using the latest version of the app (${appInfo.first}).")
                    },
                    confirmButton = {
                        Button(onClick = { showNoUpdateDialog = false }) {
                            Text("OK")
                        }
                    }
                )
            }

            if (isDownloading) {
                AlertDialog(
                    onDismissRequest = {}, // Disable dismiss during download
                    title = { Text("Downloading Update", fontWeight = FontWeight.Bold) },
                    text = {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("Please wait while the update is downloading...")
                            LinearProgressIndicator(
                                progress = { downloadProgress },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                text = "${(downloadProgress * 100).toInt()}%",
                                modifier = Modifier.align(Alignment.End),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    },
                    confirmButton = {} // Auto-closes when download finishes
                )
            }
        }
    }
}

private fun isNewerVersion(current: String, latest: String): Boolean {
    val cleanCurrent = current.removePrefix("v").removePrefix("V").substringBefore("-").trim()
    val cleanLatest = latest.removePrefix("v").removePrefix("V").substringBefore("-").trim()
    if (cleanCurrent == cleanLatest) return false
    val currentParts = cleanCurrent.split(".").mapNotNull { it.toIntOrNull() }
    val latestParts = cleanLatest.split(".").mapNotNull { it.toIntOrNull() }
    val maxLen = maxOf(currentParts.size, latestParts.size)
    for (i in 0 until maxLen) {
        val currVal = currentParts.getOrNull(i) ?: 0
        val latVal = latestParts.getOrNull(i) ?: 0
        if (latVal > currVal) return true
        if (currVal > latVal) return false
    }
    return false
}

private suspend fun downloadApkFile(
    context: Context,
    urlString: String,
    onProgress: (Float) -> Unit
): File? = withContext(Dispatchers.IO) {
    var inputStream: InputStream? = null
    var outputStream: FileOutputStream? = null
    try {
        val url = URL(urlString)
        val connection = url.openConnection() as HttpURLConnection
        connection.connectTimeout = 15000
        connection.readTimeout = 20000
        connection.requestMethod = "GET"
        connection.connect()

        if (connection.responseCode != HttpURLConnection.HTTP_OK) {
            return@withContext null
        }

        val fileLength = connection.contentLength
        val tempFile = File(context.cacheDir, "app-update.apk")
        if (tempFile.exists()) tempFile.delete()

        inputStream = connection.inputStream
        outputStream = FileOutputStream(tempFile)

        val data = ByteArray(4096)
        var total = 0L
        var count: Int
        while (inputStream.read(data).also { count = it } != -1) {
            total += count
            if (fileLength > 0) {
                onProgress(total.toFloat() / fileLength.toFloat())
            }
            outputStream.write(data, 0, count)
        }
        outputStream.flush()
        return@withContext tempFile
    } catch (e: Exception) {
        e.printStackTrace()
        return@withContext null
    } finally {
        try {
            inputStream?.close()
            outputStream?.close()
        } catch (e: Exception) {}
    }
}

private fun triggerApkInstallation(context: Context, apkFile: File) {
    try {
        val authority = "${context.packageName}.fileprovider"
        val apkUri = FileProvider.getUriForFile(context, authority, apkFile)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        e.printStackTrace()
        Toast.makeText(context, "Installer failed: ${e.message}", Toast.LENGTH_LONG).show()
    }
}
