package com.pdfviewerapp.sunuy.services

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

object OneDriveManager {
    fun startSignIn(context: Context) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://onedrive.live.com"))
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Initiating OneDrive authentication flow", Toast.LENGTH_SHORT).show()
        }
    }
}
