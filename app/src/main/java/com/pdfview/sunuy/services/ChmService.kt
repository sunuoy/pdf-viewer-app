package com.pdfview.sunuy.services

import java.io.File
import java.nio.ByteBuffer
import org.jchmlib.ChmFile
import org.jchmlib.ChmEnumerator
import org.jchmlib.ChmUnitInfo
import org.jsoup.Jsoup

object ChmService {
    fun extractText(file: File): String {
        try {
            val chmFile = ChmFile(file.absolutePath)
            val entries = mutableListOf<ChmUnitInfo>()
            
            chmFile.enumerate(ChmFile.CHM_ENUMERATE_ALL) { ui ->
                val path = ui.path
                if (path != null && (path.endsWith(".html", ignoreCase = true) || path.endsWith(".htm", ignoreCase = true))) {
                    entries.add(ui)
                }
            }
            
            entries.sortBy { it.path }
            
            val sb = StringBuilder()
            entries.forEach { ui ->
                val buffer = chmFile.retrieveObject(ui, 0L, ui.length)
                if (buffer != null) {
                    val bytes = ByteArray(buffer.remaining())
                    buffer.get(bytes)
                    
                    var htmlContent = String(bytes, Charsets.UTF_8)
                    if (htmlContent.contains("charset=gb2312", ignoreCase = true) || 
                        htmlContent.contains("charset=gbk", ignoreCase = true)) {
                        try {
                            htmlContent = String(bytes, java.nio.charset.Charset.forName("GBK"))
                        } catch (e: Exception) {}
                    }
                    
                    val document = Jsoup.parse(htmlContent)
                    val bodyText = document.body()?.text() ?: ""
                    if (bodyText.isNotEmpty()) {
                        sb.append(bodyText).append("\n\n")
                    }
                }
            }
            
            if (sb.isEmpty()) {
                return "Empty Document or Unsupported CHM format."
            }
            return sb.toString().trim()
        } catch (e: Exception) {
            return "Error reading CHM file: ${e.message}"
        }
    }
}
