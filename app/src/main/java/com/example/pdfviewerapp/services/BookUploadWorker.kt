package com.pdfviewerapp.sunuy.services

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import java.io.File

class BookUploadWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val filePath = inputData.getString("KEY_LOCAL_PATH") ?: return Result.failure()
        val remotePath = inputData.getString("KEY_REMOTE_PATH") ?: return Result.failure()
        
        val fileToUpload = File(filePath)
        if (!fileToUpload.exists()) return Result.failure()

        val success = DropboxManager.uploadBook(fileToUpload, remotePath)
        
        return if (success) {
            Result.success()
        } else {
            Result.retry()
        }
    }
}
