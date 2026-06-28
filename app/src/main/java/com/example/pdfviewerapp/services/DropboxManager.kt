package com.pdfviewerapp.sunuy.services

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.dropbox.core.DbxRequestConfig
import com.dropbox.core.v2.DbxClientV2
import com.dropbox.core.v2.files.WriteMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

object DropboxManager {
    private var client: DbxClientV2? = null
    private var accessToken: String? = null

    /**
     * Triggers the Dropbox OAuth web flow
     */
    fun startAuthentication(context: Context, appKey: String) {
        val authUrl = "https://www.dropbox.com/oauth2/authorize?client_id=$appKey&response_type=token"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(authUrl))
        context.startActivity(intent)
    }

    /**
     * Initialize DbxClientV2 with an OAuth access token
     */
    fun initClient(token: String): Boolean {
        return try {
            val config = DbxRequestConfig.newBuilder("LibraryRequirementsApp/1.0").build()
            client = DbxClientV2(config, token)
            accessToken = token
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Checks whether client is authenticated
     */
    fun isAuthenticated(): Boolean {
        return client != null
    }

    /**
     * Uploads a book file to Dropbox (Best for files under 150MB)
     */
    suspend fun uploadBook(localFile: File, dropboxPath: String): Boolean = withContext(Dispatchers.IO) {
        val dbxClient = client ?: return@withContext false
        try {
            FileInputStream(localFile).use { inputStream ->
                dbxClient.files()
                    .uploadBuilder(dropboxPath)
                    .withMode(WriteMode.OVERWRITE)
                    .uploadAndFinish(inputStream)
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Downloads a book file from Dropbox to local cache storage
     */
    suspend fun downloadBook(dropboxPath: String, localDestinationFile: File): Boolean = withContext(Dispatchers.IO) {
        val dbxClient = client ?: return@withContext false
        try {
            FileOutputStream(localDestinationFile).use { outputStream ->
                dbxClient.files()
                    .download(dropboxPath)
                    .download(outputStream)
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Fetches metadata for all files in a specific Dropbox folder
     */
    suspend fun fetchRemoteBookList(folderPath: String = ""): List<Pair<String, String>> = withContext(Dispatchers.IO) {
        val dbxClient = client ?: return@withContext emptyList()
        try {
            val result = dbxClient.files().listFolder(folderPath)
            result.entries.map { entry ->
                Pair(entry.name, entry.pathDisplay ?: entry.name)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
}
