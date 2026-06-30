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

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    // Clear cache asynchronously on startup to clean up leftover files
    clearCacheAsync()

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
      clearCacheAsync()
    }
  }

  private fun clearCacheAsync() {
    CoroutineScope(Dispatchers.IO).launch {
      try {
        cacheDir.deleteContents()
        externalCacheDir?.deleteContents()
      } catch (e: Exception) {
        // Ignore
      }
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
