package com.pdfviewerapp.sunuy.services

import android.content.Context
import android.graphics.RectF
import android.net.Uri
import android.util.Log
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import kotlin.math.abs

data class SearchMatch(
    val pageIndex: Int,
    val rects: List<RectF>,
    val contextText: String
)

class PdfTextService {

    companion object {
        private const val TAG = "PdfTextService"
        
        fun init(context: Context) {
            try {
                PDFBoxResourceLoader.init(context.applicationContext)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize PDFBox", e)
            }
        }
    }

    /**
     * Extract plain text from a specific page.
     */
    suspend fun extractTextFromPage(context: Context, uri: Uri, pageIndex: Int): String = withContext(Dispatchers.IO) {
        var document: PDDocument? = null
        var inputStream: InputStream? = null
        try {
            inputStream = context.contentResolver.openInputStream(uri)
            if (inputStream == null) return@withContext ""
            document = PDDocument.load(inputStream)
            if (pageIndex < 0 || pageIndex >= document.numberOfPages) return@withContext ""
            
            val stripper = PDFTextStripper()
            stripper.startPage = pageIndex + 1
            stripper.endPage = pageIndex + 1
            return@withContext stripper.getText(document) ?: ""
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting text from page $pageIndex", e)
            return@withContext ""
        } finally {
            try {
                document?.close()
                inputStream?.close()
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    /**
     * Search for a query inside the PDF, returning matches with coordinate rectangles.
     */
    suspend fun searchInsidePdf(context: Context, uri: Uri, query: String): List<SearchMatch> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val results = mutableListOf<SearchMatch>()
        var document: PDDocument? = null
        var inputStream: InputStream? = null
        
        try {
            inputStream = context.contentResolver.openInputStream(uri)
            if (inputStream == null) return@withContext emptyList()
            document = PDDocument.load(inputStream)
            
            val numPages = document.numberOfPages
            for (i in 0 until numPages) {
                val pageMatchRects = mutableListOf<RectF>()
                val stripper = object : PDFTextStripper() {
                    init {
                        sortByPosition = true
                        startPage = i + 1
                        endPage = i + 1
                    }

                    override fun writeString(text: String, textPositions: List<TextPosition>) {
                        val lowerText = text.lowercase()
                        val lowerQuery = query.lowercase()
                        var index = 0
                        
                        while (true) {
                            index = lowerText.indexOf(lowerQuery, index)
                            if (index == -1) break
                            
                            val length = lowerQuery.length
                            if (index + length <= textPositions.size) {
                                val matchPositions = textPositions.subList(index, index + length)
                                
                                // Group matching characters by line to handle multi-line wraps cleanly
                                val lines = mutableListOf<MutableList<TextPosition>>()
                                for (pos in matchPositions) {
                                    if (lines.isEmpty()) {
                                        lines.add(mutableListOf(pos))
                                    } else {
                                        val lastLine = lines.last()
                                        val lastPos = lastLine.last()
                                        // If Y coordinate differs significantly, it's a new line
                                        if (abs(pos.yDirAdj - lastPos.yDirAdj) > pos.heightDir * 1.5) {
                                            lines.add(mutableListOf(pos))
                                        } else {
                                            lastLine.add(pos)
                                        }
                                    }
                                }
                                
                                // Create a RectF for each line segments
                                for (line in lines) {
                                    var minX = Float.MAX_VALUE
                                    var minY = Float.MAX_VALUE
                                    var maxX = Float.MIN_VALUE
                                    var maxY = Float.MIN_VALUE
                                    
                                    for (pos in line) {
                                        val x = pos.xDirAdj
                                        val y = pos.yDirAdj
                                        val w = pos.widthDirAdj
                                        val h = pos.heightDir
                                        
                                        val charLeft = x
                                        val charTop = y - h
                                        val charRight = x + w
                                        val charBottom = y + h * 0.1f // Slightly extend bottom for readability
                                        
                                        if (charLeft < minX) minX = charLeft
                                        if (charTop < minY) minY = charTop
                                        if (charRight > maxX) maxX = charRight
                                        if (charBottom > maxY) maxY = charBottom
                                    }
                                    pageMatchRects.add(RectF(minX, minY, maxX, maxY))
                                }
                            }
                            index += length
                        }
                    }
                }
                
                // This call triggers writeString for page contents
                val fullPageText = stripper.getText(document) ?: ""
                
                if (pageMatchRects.isNotEmpty()) {
                    // Extract snippet context
                    val queryIndex = fullPageText.lowercase().indexOf(query.lowercase())
                    val contextText = if (queryIndex != -1) {
                        val start = maxOf(0, queryIndex - 30)
                        val end = minOf(fullPageText.length, queryIndex + query.length + 30)
                        val prefix = if (start > 0) "..." else ""
                        val suffix = if (end < fullPageText.length) "..." else ""
                        prefix + fullPageText.substring(start, end).replace('\n', ' ').trim() + suffix
                    } else {
                        "Page ${i + 1}"
                    }
                    
                    results.add(
                        SearchMatch(
                            pageIndex = i,
                            rects = pageMatchRects.toList(),
                            contextText = contextText
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error searching in PDF", e)
        } finally {
            try {
                document?.close()
                inputStream?.close()
            } catch (e: Exception) {
                // Ignore
            }
        }
        return@withContext results
    }
}
