package com.pdfviewerapp.sunuy

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Parcelable
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.pdfviewerapp.sunuy.theme.PDFViewerAppTheme
import com.pdfviewerapp.sunuy.data.AppDatabase
import com.pdfviewerapp.sunuy.data.entities.RecentPdf
import com.pdfviewerapp.sunuy.ui.screens.getFileName
import androidx.navigation3.runtime.NavKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    // Defer non-critical startup tasks until after the app has launched and displayed to the user
    deferStartupTasks()

    // Pre-initialize Room database in background to avoid Main thread disk/IPC blockages
    preInitializeDatabaseAsync()

    enableEdgeToEdge()

    // Handle incoming intent for shared/opened PDF
    var sharedPdfUri: String? = null
    val action = intent?.action
    val type = intent?.type
    
    if (Intent.ACTION_VIEW == action && type == "application/pdf") {
      intent.data?.let { uri ->
        sharedPdfUri = uri.toString()
        persistUriAndInsertRecent(uri)
      }
    } else if (Intent.ACTION_SEND == action && type == "application/pdf") {
      (intent.getParcelableExtra<Parcelable>(Intent.EXTRA_STREAM) as? Uri)?.let { uri ->
        sharedPdfUri = uri.toString()
        persistUriAndInsertRecent(uri)
      }
    }

    setContent {
      PDFViewerAppTheme { 
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { 
          if (sharedPdfUri != null) {
            MainNavigation(startDestination = PdfViewer(sharedPdfUri!!))
          } else {
            MainNavigation()
          }
        } 
      }
    }
  }

  override fun onDestroy() {
    super.onDestroy()
    if (isFinishing) {
      clearCache()
    }
  }

  private fun preInitializeDatabaseAsync() {
    CoroutineScope(Dispatchers.IO).launch {
      try {
        AppDatabase.getDatabase(applicationContext)
      } catch (e: Exception) {
        // Ignore
      }
    }
  }

  private fun deferStartupTasks() {
    CoroutineScope(Dispatchers.IO).launch {
      try {
        // Wait 3 seconds for app launch to settle before executing disk operations
        kotlinx.coroutines.delay(3000L)
        clearCache()
        copyAssetsToFilesOnUpdate()
      } catch (e: Exception) {
        // Ignore
      }
    }
  }

  private fun copyAssetsToFilesOnUpdate() {
    try {
      val sharedPrefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
      val currentVersionCode = try {
        val pInfo = packageManager.getPackageInfo(packageName, 0)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
          pInfo.longVersionCode
        } else {
          @Suppress("DEPRECATION")
          pInfo.versionCode.toLong()
        }
      } catch (e: Exception) {
        -1L
      }

      val lastVersionCode = sharedPrefs.getLong("last_version_code", -1L)

      // If it's a new install or update, copy/overwrite files from assets
      if (currentVersionCode != lastVersionCode) {
        assets.list("")?.forEach { assetName ->
          if (assetName.endsWith(".pdf") || assetName.endsWith(".txt") || assetName.endsWith(".md") || assetName.endsWith(".html")) {
            val outFile = File(filesDir, assetName)
            assets.open(assetName).use { input ->
              FileOutputStream(outFile).use { output ->
                input.copyTo(output)
              }
            }
          }
        }
        sharedPrefs.edit().putLong("last_version_code", currentVersionCode).apply()
      }
    } catch (e: Exception) {
      android.util.Log.e("MainActivity", "Error copying assets", e)
    }
  }

  private fun clearCache() {
    try {
      cacheDir.deleteContents()
      externalCacheDir?.deleteContents()
    } catch (e: Exception) {
      // Ignore
    }
  }

  private fun File.deleteContents() {
    if (isDirectory) {
      listFiles()?.forEach { file ->
        file.deleteRecursively()
      }
    }
  }

  private fun persistUriAndInsertRecent(uri: Uri) {
    try {
      val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION
      contentResolver.takePersistableUriPermission(uri, takeFlags)
    } catch (e: Exception) {
      // Ignore
    }
    
    CoroutineScope(Dispatchers.IO).launch {
      val database = AppDatabase.getDatabase(applicationContext)
      val name = getFileName(applicationContext, uri) ?: "Document.pdf"
      val existing = database.recentPdfDao().getRecentPdfByPath(uri.toString())
      val recentPdf = RecentPdf(
          id = existing?.id ?: 0,
          name = name,
          path = uri.toString(),
          lastOpened = System.currentTimeMillis(),
          lastPage = existing?.lastPage ?: 0
      )
      database.recentPdfDao().insertRecentPdf(recentPdf)
    }
  }
}
