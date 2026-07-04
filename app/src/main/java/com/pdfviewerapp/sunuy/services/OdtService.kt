package com.pdfviewerapp.sunuy.services

import java.io.File
import java.util.zip.ZipFile

object OdtService {
    fun extractText(file: File): String {
        try {
            ZipFile(file).use { zip ->
                val entry = zip.getEntry("content.xml") ?: return "Error: Not a valid ODT file (missing content.xml)."
                zip.getInputStream(entry).use { stream ->
                    val xmlContent = stream.bufferedReader().readText()
                    val paragraphs = mutableListOf<String>()
                    val pRegex = """<text:p\b[^>]*>(.*?)</text:p>""".toRegex()
                    
                    pRegex.findAll(xmlContent).forEach { pMatch ->
                        val pBody = pMatch.groups[1]?.value ?: ""
                        val cleanText = pBody.replace(Regex("<[^>]+>"), "")
                        if (cleanText.isNotEmpty()) {
                            paragraphs.add(decodeXmlEntities(cleanText))
                        }
                    }
                    if (paragraphs.isEmpty()) {
                        return "Empty Document"
                    }
                    return paragraphs.joinToString("\n\n")
                }
            }
        } catch (e: Exception) {
            return "Error reading ODT file: ${e.message}"
        }
    }

    private fun decodeXmlEntities(input: String): String {
        return input
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
    }
}
