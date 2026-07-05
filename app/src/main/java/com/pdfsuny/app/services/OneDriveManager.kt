package com.pdfsuny.app.services

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.pdfsuny.app.data.entities.CloudFile

object OneDriveManager {
    fun startSignIn(context: Context) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://onedrive.live.com"))
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Initiating OneDrive authentication flow", Toast.LENGTH_SHORT).show()
        }
    }

    fun getPdfFiles(onLoaded: (List<CloudFile>) -> Unit) {
        // Mock / Helper returning structured OneDrive cloud files
        val sampleFiles = listOf(
            CloudFile("onedrive_1", "OneDrive_Project_Report.pdf", "one_drive", size = 1840000L),
            CloudFile("onedrive_2", "Financial_Statement_2026.pdf", "one_drive", size = 3200000L)
        )
        onLoaded(sampleFiles)
    }
}
