package com.pdfviewerapp.sunuy.services

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.pdfviewerapp.sunuy.data.entities.CloudFile

object GoogleDriveManager {
    fun startSignIn(context: Context) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://drive.google.com"))
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Initiating Google Drive authentication flow", Toast.LENGTH_SHORT).show()
        }
    }

    fun getPdfFiles(onLoaded: (List<CloudFile>) -> Unit) {
        // Mock / Helper returning structured Google Drive cloud files
        val sampleFiles = listOf(
            CloudFile("gdrive_1", "Sample_Drive_Document.pdf", "google_drive", size = 2450000L),
            CloudFile("gdrive_2", "Android_Development_Guide.pdf", "google_drive", size = 5120000L)
        )
        onLoaded(sampleFiles)
    }
}
