package com.pdfsuny.app.services

import java.io.File
import java.util.zip.ZipFile

object DocxService {
    fun extractText(file: File): String {
        try {
            ZipFile(file).use { zip ->
                val entry = zip.getEntry("word/document.xml") ?: return "Error: Not a valid DOCX file (missing word/document.xml)."
                zip.getInputStream(entry).use { stream ->
                    val xmlContent = stream.bufferedReader().readText()
                    val paragraphs = mutableListOf<String>()
                    val pRegex = """<w:p\b[^>]*>(.*?)</w:p>""".toRegex()
                    val tRegex = """<w:t\b[^>]*>(.*?)</w:t>""".toRegex()
                    
                    pRegex.findAll(xmlContent).forEach { pMatch ->
                        val pBody = pMatch.groups[1]?.value ?: ""
                        val pText = tRegex.findAll(pBody).map { it.groups[1]?.value ?: "" }.joinToString("")
                        if (pText.isNotEmpty()) {
                            paragraphs.add(decodeXmlEntities(pText))
                        }
                    }
                    if (paragraphs.isEmpty()) {
                        val simpleText = tRegex.findAll(xmlContent).map { it.groups[1]?.value ?: "" }.joinToString("")
                        return if (simpleText.isNotEmpty()) decodeXmlEntities(simpleText) else "Empty Document"
                    }
                    return paragraphs.joinToString("\n\n")
                }
            }
        } catch (e: Exception) {
            return "Error reading DOCX file: ${e.message}"
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
