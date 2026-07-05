package com.pdfsuny.app.services

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.github.junrar.Archive
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipFile

object ComicService {

    data class ComicPage(
        val name: String,
        val archiveType: String, // "ZIP" or "RAR"
        val archivePath: String
    )

    fun getPages(file: File): List<ComicPage> {
        val pages = mutableListOf<ComicPage>()
        val ext = file.extension.lowercase()
        if (ext == "cbz" || ext == "zip") {
            try {
                ZipFile(file).use { zip ->
                    val entries = zip.entries()
                    while (entries.hasMoreElements()) {
                        val entry = entries.nextElement()
                        if (!entry.isDirectory && isImageFile(entry.name)) {
                            pages.add(ComicPage(entry.name, "ZIP", file.absolutePath))
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else if (ext == "cbr" || ext == "rar") {
            try {
                Archive(file).use { archive ->
                    val headers = archive.fileHeaders
                    for (header in headers) {
                        if (!header.isDirectory && isImageFile(header.fileName)) {
                            pages.add(ComicPage(header.fileName, "RAR", file.absolutePath))
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        pages.sortBy { it.name.lowercase() }
        return pages
    }

    fun loadPageBitmap(page: ComicPage): Bitmap? {
        try {
            val file = File(page.archivePath)
            if (page.archiveType == "ZIP") {
                ZipFile(file).use { zip ->
                    val entry = zip.getEntry(page.name) ?: return null
                    zip.getInputStream(entry).use { stream ->
                        return BitmapFactory.decodeStream(stream)
                    }
                }
            } else if (page.archiveType == "RAR") {
                Archive(file).use { archive ->
                    val header = archive.fileHeaders.find { it.fileName == page.name } ?: return null
                    val bos = ByteArrayOutputStream()
                    archive.extractFile(header, bos)
                    val bytes = bos.toByteArray()
                    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    private fun isImageFile(name: String): Boolean {
        val lower = name.lowercase()
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") ||
               lower.endsWith(".webp") || lower.endsWith(".gif") || lower.endsWith(".bmp")
    }
}
