package com.pdfviewerapp.sunuy.services

import android.content.Context
import android.net.Uri
import android.util.Log
import org.jsoup.Jsoup
import java.io.InputStream
import java.net.URLDecoder
import java.util.zip.ZipInputStream

object EpubService {
    private const val TAG = "EpubService"

    /**
     * Parses an EPUB file into plain text.
     */
    fun parseEpubToText(context: Context, uri: Uri): String {
        val entries = mutableMapOf<String, ByteArray>()
        
        val file = if (uri.scheme == "file") {
            java.io.File(uri.path ?: "")
        } else {
            val tempFile = java.io.File(context.cacheDir, "temp_epub_${System.currentTimeMillis()}.epub")
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    java.io.FileOutputStream(tempFile).use { output ->
                        input.copyTo(output)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to copy content Uri to temp file", e)
            }
            tempFile
        }

        if (!file.exists() || file.length() == 0L) {
            return "Error: Epub file does not exist or is empty."
        }

        val isTempFile = uri.scheme != "file"
        try {
            java.util.zip.ZipFile(file).use { zip ->
                val entriesEnum = zip.entries()
                while (entriesEnum.hasMoreElements()) {
                    val entry = entriesEnum.nextElement()
                    val name = entry.name.replace("\\", "/").trimStart('/')
                    Log.d(TAG, "Zip Entry: $name")
                    if (name.endsWith(".xml", ignoreCase = true) ||
                        name.endsWith(".opf", ignoreCase = true) ||
                        name.endsWith(".html", ignoreCase = true) ||
                        name.endsWith(".xhtml", ignoreCase = true) ||
                        name.endsWith(".htm", ignoreCase = true)
                    ) {
                        zip.getInputStream(entry).use { input ->
                            entries[name.lowercase()] = input.readBytes()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading epub zip entries", e)
            return "Error reading epub file: ${e.localizedMessage}"
        } finally {
            if (isTempFile && file.exists()) {
                file.delete()
            }
        }

        // 1. Locate the container.xml which points to the main OPF file
        val containerXml = entries["meta-inf/container.xml"]?.toString(Charsets.UTF_8)
        if (containerXml == null) {
            Log.e(TAG, "Missing container.xml. Available entries: ${entries.keys.joinToString(", ")}")
            return "Error: Invalid EPUB file (missing META-INF/container.xml)."
        }

        val opfPath = try {
            val regex = """full-path\s*=\s*["']([^"']+)["']""".toRegex()
            regex.find(containerXml)?.groups?.get(1)?.value
        } catch (e: Exception) {
            null
        } ?: "OEBPS/content.opf"

        val opfContent = entries[opfPath.lowercase()]
        if (opfContent == null) {
            Log.e(TAG, "Missing OPF manifest file at ${opfPath.lowercase()}. Available entries: ${entries.keys.joinToString(", ")}")
            return "Error: Missing OPF manifest file at $opfPath."
        }

        val opfDir = if (opfPath.contains("/")) {
            opfPath.substringBeforeLast("/") + "/"
        } else {
            ""
        }

        // 2. Parse the OPF XML for the manifest and spine using Regex (compiles universally)
        val manifest = mutableMapOf<String, String>()
        val spine = mutableListOf<String>()

        try {
            val opfString = opfContent.toString(Charsets.UTF_8)
            
            // Parse <item ... /> tags
            val itemRegex = """<item\s+([^>]+)>""".toRegex(RegexOption.IGNORE_CASE)
            val idRegex = """\bid\s*=\s*["']([^"']+)["']""".toRegex(RegexOption.IGNORE_CASE)
            val hrefRegex = """\bhref\s*=\s*["']([^"']+)["']""".toRegex(RegexOption.IGNORE_CASE)
            
            itemRegex.findAll(opfString).forEach { match ->
                val tagBody = match.groups[1]?.value ?: ""
                val id = idRegex.find(tagBody)?.groups?.get(1)?.value
                val href = hrefRegex.find(tagBody)?.groups?.get(1)?.value
                if (id != null && href != null) {
                    manifest[id] = href
                }
            }
            
            // Parse <itemref ... /> tags
            val itemrefRegex = """<itemref\s+([^>]+)>""".toRegex(RegexOption.IGNORE_CASE)
            val idrefRegex = """\bidref\s*=\s*["']([^"']+)["']""".toRegex(RegexOption.IGNORE_CASE)
            
            itemrefRegex.findAll(opfString).forEach { match ->
                val tagBody = match.groups[1]?.value ?: ""
                val idref = idrefRegex.find(tagBody)?.groups?.get(1)?.value
                if (idref != null) {
                    spine.add(idref)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing OPF using regex", e)
        }

        val orderedHrefs = spine.mapNotNull { manifest[it] }
        if (orderedHrefs.isEmpty()) {
            return "Error: EPUB spine (reading order) is empty."
        }

        // 3. Extract clean text from each chapter XHTML/HTML in order
        val fullText = StringBuilder()
        for (href in orderedHrefs) {
            try {
                val decodedHref = URLDecoder.decode(href, "UTF-8")
                val fullPath = normalizePath(opfDir, decodedHref)
                Log.d(TAG, "Attempting to load chapter: $fullPath")
                val htmlBytes = entries[fullPath.lowercase()]
                if (htmlBytes != null) {
                    val html = htmlBytes.toString(Charsets.UTF_8)
                    val doc = Jsoup.parse(html)
                    val title = doc.title().trim()
                    val chapterText = doc.body()?.text() ?: doc.text()
                    
                    if (chapterText.isNotBlank()) {
                        if (title.isNotBlank() && !chapterText.startsWith(title, ignoreCase = true)) {
                            fullText.append("\n\n=== ").append(title).append(" ===\n\n")
                        } else {
                            fullText.append("\n\n")
                        }
                        fullText.append(chapterText)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error extracting chapter text for $href", e)
            }
        }

        return fullText.toString().trim()
    }

    /**
     * Resolves relative paths (e.g. OEBPS/../text/chapter.html -> text/chapter.html)
     */
    private fun normalizePath(base: String, relative: String): String {
        val combined = if (base.isEmpty()) relative else "$base/$relative"
        val parts = combined.replace("\\", "/").split("/")
        val resolvedParts = mutableListOf<String>()
        for (part in parts) {
            if (part == "." || part.isEmpty()) continue
            if (part == "..") {
                if (resolvedParts.isNotEmpty()) {
                    resolvedParts.removeAt(resolvedParts.size - 1)
                }
            } else {
                resolvedParts.add(part)
            }
        }
        return resolvedParts.joinToString("/")
    }
}
