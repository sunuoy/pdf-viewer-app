package com.pdfview.sunuy.services

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import com.pdfview.sunuy.data.entities.CloudFile
import java.io.File
import java.io.FileOutputStream

object GoogleDriveManager {
    
    fun isLoggedIn(context: Context): Boolean {
        val prefs = context.getSharedPreferences("google_drive_prefs", Context.MODE_PRIVATE)
        return prefs.getBoolean("is_logged_in", false)
    }

    fun getEmail(context: Context): String {
        val prefs = context.getSharedPreferences("google_drive_prefs", Context.MODE_PRIVATE)
        return prefs.getString("user_email", "younus@gmail.com") ?: "younus@gmail.com"
    }

    fun signIn(context: Context, email: String) {
        val prefs = context.getSharedPreferences("google_drive_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean("is_logged_in", true)
            .putString("user_email", email)
            .apply()
    }

    fun signOut(context: Context) {
        val prefs = context.getSharedPreferences("google_drive_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean("is_logged_in", false)
            .remove("user_email")
            .apply()
    }

    fun getPdfFiles(onLoaded: (List<CloudFile>) -> Unit) {
        val sampleFiles = listOf(
            CloudFile("gdrive_1", "Sample_Drive_Document.pdf", "google_drive", size = 124000L),
            CloudFile("gdrive_2", "Android_Development_Guide.pdf", "google_drive", size = 88000L)
        )
        onLoaded(sampleFiles)
    }

    fun getDriveFile(context: Context, fileId: String): File? {
        val fileName = if (fileId == "gdrive_1") "Sample_Drive_Document.pdf" else "Android_Development_Guide.pdf"
        val destFile = File(context.filesDir, fileName)
        
        try {
            val pdfDocument = PdfDocument()
            val paint = Paint()
            val pageCount = if (fileId == "gdrive_1") 10 else 3
            
            for (i in 1..pageCount) {
                val pageInfo = PdfDocument.PageInfo.Builder(595, 842, i).create()
                val page = pdfDocument.startPage(pageInfo)
                val canvas = page.canvas
                canvas.drawColor(Color.WHITE)
                
                // Title
                paint.isFakeBoldText = true
                paint.textSize = 28f
                paint.color = Color.BLACK
                canvas.drawText("$fileName - Page $i", 50f, 100f, paint)
                
                // Description
                paint.isFakeBoldText = false
                paint.textSize = 18f
                canvas.drawText("Downloaded from Google Drive: ${getEmail(context)}", 50f, 180f, paint)
                canvas.drawText("This file is being rendered locally inside the PDF Viewer App.", 50f, 220f, paint)
                canvas.drawText("Total pages in this document: $pageCount", 50f, 260f, paint)
                
                // Dummy content lines
                for (line in 1..15) {
                    canvas.drawText("Content line $line on page $i: Interactive cloud-synced document testing.", 50f, 300f + (line * 30f), paint)
                }
                
                pdfDocument.finishPage(page)
            }
            
            FileOutputStream(destFile).use { out ->
                pdfDocument.writeTo(out)
            }
            pdfDocument.close()
            return destFile
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
}
