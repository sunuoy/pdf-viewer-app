package com.pdfviewerapp.sunuy.services

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

object GoogleDriveManager {
    fun startSignIn(context: Context) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://drive.google.com"))
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Initiating Google Drive authentication flow", Toast.LENGTH_SHORT).show()
        }
    }
}
