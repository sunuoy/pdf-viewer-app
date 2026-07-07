package com.pdfsuny.app.services

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
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

data class SearchMatch(
    val pageIndex: Int,
    val rects: List<RectF>,
    val contextText: String
)

data class PageTextAndPositions(
    val text: String,
    val positions: List<TextPosition?>
)

class PdfTextService {

    private fun openInputStream(context: Context, uri: Uri): InputStream? {
        return if (uri.scheme == "file") {
            java.io.FileInputStream(java.io.File(uri.path ?: ""))
        } else {
            context.contentResolver.openInputStream(uri)
        }
    }

    private fun openOutputStream(context: Context, uri: Uri): java.io.OutputStream? {
        return if (uri.scheme == "file") {
            java.io.FileOutputStream(java.io.File(uri.path ?: ""))
        } else {
            context.contentResolver.openOutputStream(uri, "rwt") ?: context.contentResolver.openOutputStream(uri)
        }
    }

    private val textCache = ConcurrentHashMap<String, MutableMap<Int, String>>()
    private val positionCache = ConcurrentHashMap<String, ConcurrentHashMap<Int, PageTextAndPositions>>()

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

    suspend fun getPageTextAndPositions(context: Context, uri: Uri, pageIndex: Int): PageTextAndPositions = withContext(Dispatchers.IO) {
        val uriStr = uri.toString()
        positionCache[uriStr]?.get(pageIndex)?.let { return@withContext it }

        var document: PDDocument? = null
        var inputStream: InputStream? = null
        try {
            inputStream = openInputStream(context, uri)
            if (inputStream == null) return@withContext PageTextAndPositions("", emptyList())
            document = PDDocument.load(inputStream)
            if (pageIndex < 0 || pageIndex >= document.numberOfPages) return@withContext PageTextAndPositions("", emptyList())
            
            val stripper = object : PDFTextStripper() {
                val charList = mutableListOf<Char>()
                val posList = mutableListOf<TextPosition?>()
                
                init {
                    sortByPosition = true
                    startPage = pageIndex + 1
                    endPage = pageIndex + 1
                }
                
                override fun writeString(text: String, textPositions: List<TextPosition>) {
                    for (i in text.indices) {
                        charList.add(text[i])
                        posList.add(textPositions.getOrNull(i))
                    }
                    super.writeString(text, textPositions)
                }
                
                override fun writeLineSeparator() {
                    val lineSep = lineSeparator ?: "\n"
                    for (c in lineSep) {
                        charList.add(c)
                        posList.add(null)
                    }
                    super.writeLineSeparator()
                }
                
                override fun writeWordSeparator() {
                    val wordSep = wordSeparator ?: " "
                    for (c in wordSep) {
                        charList.add(c)
                        posList.add(null)
                    }
                    super.writeWordSeparator()
                }
            }
            
            val text = stripper.getText(document) ?: ""
            val result = PageTextAndPositions(text, stripper.posList)
            positionCache.getOrPut(uriStr) { ConcurrentHashMap() }[pageIndex] = result
            textCache.getOrPut(uriStr) { ConcurrentHashMap() }[pageIndex] = text
            return@withContext result
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting text and positions for page $pageIndex", e)
            return@withContext PageTextAndPositions("", emptyList())
        } finally {
            try {
                document?.close()
                inputStream?.close()
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    fun getRectsForRange(pageData: PageTextAndPositions, start: Int, end: Int): List<RectF> {
        val rects = mutableListOf<RectF>()
        if (start < 0 || end > pageData.positions.size || start >= end) return rects
        
        val subPositions = pageData.positions.subList(start, end).filterNotNull()
        if (subPositions.isEmpty()) return rects
        
        val lines = mutableListOf<MutableList<TextPosition>>()
        for (pos in subPositions) {
            if (lines.isEmpty()) {
                lines.add(mutableListOf(pos))
            } else {
                val lastLine = lines.last()
                val lastPos = lastLine.last()
                if (abs(pos.yDirAdj - lastPos.yDirAdj) > pos.heightDir * 1.5) {
                    lines.add(mutableListOf(pos))
                } else {
                    lastLine.add(pos)
                }
            }
        }
        
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
                val charBottom = y + h * 0.1f
                
                if (charLeft < minX) minX = charLeft
                if (charTop < minY) minY = charTop
                if (charRight > maxX) maxX = charRight
                if (charBottom > maxY) maxY = charBottom
            }
            if (minX < maxX && minY < maxY) {
                rects.add(RectF(minX, minY, maxX, maxY))
            }
        }
        return rects
    }

    /**
     * Extract plain text from a specific page.
     */
    suspend fun extractTextFromPage(context: Context, uri: Uri, pageIndex: Int): String = withContext(Dispatchers.IO) {
        val uriStr = uri.toString()
        textCache[uriStr]?.get(pageIndex)?.let { return@withContext it }

        var document: PDDocument? = null
        var inputStream: InputStream? = null
        try {
            inputStream = openInputStream(context, uri)
            if (inputStream == null) return@withContext ""
            document = PDDocument.load(inputStream)
            if (pageIndex < 0 || pageIndex >= document.numberOfPages) return@withContext ""
            
            val stripper = PDFTextStripper()
            stripper.startPage = pageIndex + 1
            stripper.endPage = pageIndex + 1
            val text = stripper.getText(document) ?: ""
            
            textCache.getOrPut(uriStr) { ConcurrentHashMap() }[pageIndex] = text
            return@withContext text
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
        val uriStr = uri.toString()
        
        try {
            inputStream = openInputStream(context, uri)
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
                
                // Cache the extracted page text
                textCache.getOrPut(uriStr) { ConcurrentHashMap() }[i] = fullPageText
                
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

    suspend fun editTextOnPage(
        context: Context,
        uri: Uri,
        pageIndex: Int,
        targetWord: String,
        replacementText: String
    ): Boolean = withContext(Dispatchers.IO) {
        if (targetWord.isBlank()) return@withContext false
        
        var document: PDDocument? = null
        var inputStream: java.io.InputStream? = null
        val tempFile = java.io.File(context.cacheDir, "temp_edit_${System.currentTimeMillis()}.pdf")
        
        try {
            inputStream = openInputStream(context, uri)
            if (inputStream == null) return@withContext false
            document = PDDocument.load(inputStream)
            if (pageIndex < 0 || pageIndex >= document.numberOfPages) return@withContext false
            
            val page = document.getPage(pageIndex)
            val pageHeight = page.mediaBox.height
            
            // Search for targetWord occurrences on this specific page to get coordinates
            val matchRects = mutableListOf<Pair<RectF, Float>>() // Pair of Bounding Rect and baseline Y
            
            val stripper = object : PDFTextStripper() {
                init {
                    sortByPosition = true
                    startPage = pageIndex + 1
                    endPage = pageIndex + 1
                }

                override fun writeString(text: String, textPositions: List<TextPosition>) {
                    val lowerText = text.lowercase()
                    val lowerQuery = targetWord.lowercase()
                    var index = 0
                    
                    while (true) {
                        index = lowerText.indexOf(lowerQuery, index)
                        if (index == -1) break
                        
                        val length = lowerQuery.length
                        if (index + length <= textPositions.size) {
                            val matchPositions = textPositions.subList(index, index + length)
                            
                            // Group matching characters by line
                            val lines = mutableListOf<MutableList<TextPosition>>()
                            for (pos in matchPositions) {
                                if (lines.isEmpty()) {
                                    lines.add(mutableListOf(pos))
                                } else {
                                    val lastLine = lines.last()
                                    val lastPos = lastLine.last()
                                    if (abs(pos.yDirAdj - lastPos.yDirAdj) > pos.heightDir * 1.5) {
                                        lines.add(mutableListOf(pos))
                                    } else {
                                        lastLine.add(pos)
                                    }
                                }
                            }
                            
                            for (line in lines) {
                                var minX = Float.MAX_VALUE
                                var minY = Float.MAX_VALUE
                                var maxX = Float.MIN_VALUE
                                var maxY = Float.MIN_VALUE
                                var baselineYSum = 0f
                                var charCount = 0
                                
                                for (pos in line) {
                                    val x = pos.xDirAdj
                                    val y = pos.yDirAdj
                                    val w = pos.widthDirAdj
                                    val h = pos.heightDir
                                    
                                    val charLeft = x
                                    val charTop = y - h
                                    val charRight = x + w
                                    val charBottom = y + h * 0.1f
                                    
                                    if (charLeft < minX) minX = charLeft
                                    if (charTop < minY) minY = charTop
                                    if (charRight > maxX) maxX = charRight
                                    if (charBottom > maxY) maxY = charBottom
                                    
                                    baselineYSum += y
                                    charCount++
                                }
                                
                                if (minX < maxX && minY < maxY && charCount > 0) {
                                    val avgBaselineY = baselineYSum / charCount
                                    matchRects.add(Pair(RectF(minX, minY, maxX, maxY), avgBaselineY))
                                }
                            }
                        }
                        index += length
                    }
                }
            }
            
            // Triggers the text parsing and populates matchRects
            stripper.getText(document)
            
            if (matchRects.isEmpty()) {
                document.close()
                return@withContext false
            }
            
            // Open content stream to append drawing commands
            val contentStream = com.tom_roush.pdfbox.pdmodel.PDPageContentStream(
                document,
                page,
                com.tom_roush.pdfbox.pdmodel.PDPageContentStream.AppendMode.APPEND,
                true,
                true
            )
            
            for (match in matchRects) {
                val rect = match.first
                val baselineY = match.second
                
                val rectLeft = rect.left
                val rectWidth = rect.right - rect.left
                val rectHeight = rect.bottom - rect.top
                val rectBottom = pageHeight - rect.bottom
                
                // Draw white rectangle to whiteout old text
                contentStream.setNonStrokingColor(1f, 1f, 1f)
                contentStream.addRect(rectLeft, rectBottom, rectWidth, rectHeight)
                contentStream.fill()
                
                // Draw replacement text
                contentStream.beginText()
                // Use HELVETICA font
                contentStream.setFont(com.tom_roush.pdfbox.pdmodel.font.PDType1Font.HELVETICA, rectHeight * 0.8f)
                contentStream.setNonStrokingColor(0f, 0f, 0f) // Black text color
                contentStream.newLineAtOffset(rectLeft, pageHeight - baselineY)
                contentStream.showText(replacementText)
                contentStream.endText()
            }
            
            contentStream.close()
            
            // Save to temp file
            document.save(tempFile)
            document.close()
            document = null
            
            // Clear caches
            val uriStr = uri.toString()
            textCache.remove(uriStr)
            positionCache.remove(uriStr)
            
            // Copy back to original uri
            var outputStream: java.io.OutputStream? = null
            try {
                outputStream = openOutputStream(context, uri)
                if (outputStream != null) {
                    tempFile.inputStream().use { input ->
                        input.copyTo(outputStream)
                    }
                    return@withContext true
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error writing edited PDF back to original source", e)
            } finally {
                outputStream?.close()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error editing text on PDF page $pageIndex", e)
        } finally {
            try {
                document?.close()
                inputStream?.close()
            } catch (e: Exception) {
                // Ignore
            }
            if (tempFile.exists()) {
                tempFile.delete()
            }
        }
        return@withContext false
    }

    suspend fun addSignatureOnPage(
        context: Context,
        uri: Uri,
        pageIndex: Int,
        signaturePaths: List<List<android.graphics.PointF>>,
        alignment: String,
        inkColor: Int
    ): Boolean = withContext(Dispatchers.IO) {
        if (signaturePaths.isEmpty()) return@withContext false
        
        var document: PDDocument? = null
        var inputStream: java.io.InputStream? = null
        val tempFile = java.io.File(context.cacheDir, "temp_sig_${System.currentTimeMillis()}.pdf")
        
        try {
            inputStream = openInputStream(context, uri)
            if (inputStream == null) return@withContext false
            document = PDDocument.load(inputStream)
            if (pageIndex < 0 || pageIndex >= document.numberOfPages) return@withContext false
            
            val page = document.getPage(pageIndex)
            val mediaBox = page.mediaBox
            
            val sigWidth = 400
            val sigHeight = 150
            val bitmap = android.graphics.Bitmap.createBitmap(sigWidth, sigHeight, android.graphics.Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)
            bitmap.eraseColor(android.graphics.Color.TRANSPARENT)
            
            val paint = android.graphics.Paint().apply {
                color = inkColor
                strokeWidth = 6f
                style = android.graphics.Paint.Style.STROKE
                isAntiAlias = true
                strokeCap = android.graphics.Paint.Cap.ROUND
                strokeJoin = android.graphics.Paint.Join.ROUND
            }
            
            var minX = Float.MAX_VALUE
            var maxX = Float.MIN_VALUE
            var minY = Float.MAX_VALUE
            var maxY = Float.MIN_VALUE
            signaturePaths.forEach { path ->
                path.forEach { pt ->
                    if (pt.x < minX) minX = pt.x
                    if (pt.x > maxX) maxX = pt.x
                    if (pt.y < minY) minY = pt.y
                    if (pt.y > maxY) maxY = pt.y
                }
            }
            
            val pathW = maxX - minX
            val pathH = maxY - minY
            if (pathW > 0f && pathH > 0f) {
                val scaleX = 360f / pathW
                val scaleY = 120f / pathH
                val scale = Math.min(scaleX, scaleY)
                
                val offsetX = 20f - minX * scale + (360f - pathW * scale) / 2f
                val offsetY = 20f - minY * scale + (120f - pathH * scale) / 2f
                
                signaturePaths.forEach { path ->
                    if (path.size > 1) {
                        val androidPath = android.graphics.Path()
                        androidPath.moveTo(path[0].x * scale + offsetX, path[0].y * scale + offsetY)
                        for (i in 1 until path.size) {
                            androidPath.lineTo(path[i].x * scale + offsetX, path[i].y * scale + offsetY)
                        }
                        canvas.drawPath(androidPath, paint)
                    }
                }
            }
            
            val pdImage = com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory.createFromImage(document, bitmap)
            
            val w = 120f
            val h = 45f
            val margin = 30f
            
            val x = when (alignment) {
                "bottom_left" -> margin
                "bottom_center" -> (mediaBox.width - w) / 2f
                else -> mediaBox.width - w - margin
            }
            val y = margin
            
            val contentStream = com.tom_roush.pdfbox.pdmodel.PDPageContentStream(
                document,
                page,
                com.tom_roush.pdfbox.pdmodel.PDPageContentStream.AppendMode.APPEND,
                true,
                true
            )
            contentStream.drawImage(pdImage, x, y, w, h)
            contentStream.close()
            
            document.save(tempFile)
            document.close()
            document = null
            
            val uriStr = uri.toString()
            textCache.remove(uriStr)
            positionCache.remove(uriStr)
            
            var outputStream: java.io.OutputStream? = null
            try {
                outputStream = openOutputStream(context, uri)
                if (outputStream != null) {
                    tempFile.inputStream().use { input ->
                        input.copyTo(outputStream)
                    }
                    return@withContext true
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error writing signature back to PDF", e)
            } finally {
                outputStream?.close()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error adding signature to PDF page $pageIndex", e)
        } finally {
            try {
                document?.close()
                inputStream?.close()
            } catch (e: Exception) {
                // Ignore
            }
            if (tempFile.exists()) tempFile.delete()
        }
        return@withContext false
    }

    suspend fun addStampOnPage(
        context: Context,
        uri: Uri,
        pageIndex: Int,
        stampType: Int,
        stampText: String,
        stampColor: Int,
        importedImageUri: Uri?,
        alignment: String
    ): Boolean = withContext(Dispatchers.IO) {
        var document: PDDocument? = null
        var inputStream: java.io.InputStream? = null
        val tempFile = java.io.File(context.cacheDir, "temp_stamp_${System.currentTimeMillis()}.pdf")
        
        try {
            inputStream = openInputStream(context, uri)
            if (inputStream == null) return@withContext false
            document = PDDocument.load(inputStream)
            if (pageIndex < 0 || pageIndex >= document.numberOfPages) return@withContext false
            
            val page = document.getPage(pageIndex)
            val mediaBox = page.mediaBox
            
            val stampWidth = 240
            val stampHeight = 100
            val bitmap = android.graphics.Bitmap.createBitmap(stampWidth, stampHeight, android.graphics.Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)
            bitmap.eraseColor(android.graphics.Color.TRANSPARENT)
            
            if (stampType == 1 && importedImageUri != null) {
                try {
                    context.contentResolver.openInputStream(importedImageUri)?.use { imgIn ->
                        val options = android.graphics.BitmapFactory.Options()
                        val loadedBmp = android.graphics.BitmapFactory.decodeStream(imgIn, null, options)
                        if (loadedBmp != null) {
                            val srcW = loadedBmp.width
                            val srcH = loadedBmp.height
                            val scaleX = 240f / srcW
                            val scaleY = 100f / srcH
                            val scale = Math.min(scaleX, scaleY)
                            val destW = (srcW * scale).toInt()
                            val destH = (srcH * scale).toInt()
                            val scaledBmp = android.graphics.Bitmap.createScaledBitmap(loadedBmp, destW, destH, true)
                            
                            canvas.drawBitmap(
                                scaledBmp,
                                (240 - destW) / 2f,
                                (100 - destH) / 2f,
                                null
                            )
                            scaledBmp.recycle()
                            loadedBmp.recycle()
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error loading imported stamp image in service", e)
                }
            } else {
                val strokePaint = android.graphics.Paint().apply {
                    this.color = stampColor
                    style = android.graphics.Paint.Style.STROKE
                    strokeWidth = 6f
                    isAntiAlias = true
                }
                val fillPaint = android.graphics.Paint().apply {
                    this.color = stampColor
                    alpha = 25
                    style = android.graphics.Paint.Style.FILL
                }
                
                val rect = android.graphics.RectF(10f, 10f, 230f, 90f)
                canvas.drawRoundRect(rect, 15f, 15f, fillPaint)
                canvas.drawRoundRect(rect, 15f, 15f, strokePaint)
                
                val textPaint = android.graphics.Paint().apply {
                    this.color = stampColor
                    textSize = 28f
                    isAntiAlias = true
                    typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
                    textAlign = android.graphics.Paint.Align.CENTER
                }
                
                val textY = 50f - (textPaint.descent() + textPaint.ascent()) / 2f
                canvas.drawText(stampText.uppercase(), 120f, textY, textPaint)
            }
            
            val pdImage = com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory.createFromImage(document, bitmap)
            
            val w = 140f
            val h = 58f
            val margin = 40f
            
            val x = when (alignment) {
                "bottom_left" -> margin
                "bottom_right" -> mediaBox.width - w - margin
                "top_left" -> margin
                "top_right" -> mediaBox.width - w - margin
                else -> (mediaBox.width - w) / 2f
            }
            val y = when (alignment) {
                "top_left", "top_right" -> mediaBox.height - h - margin
                "bottom_left", "bottom_right" -> margin
                else -> (mediaBox.height - h) / 2f
            }
            
            val contentStream = com.tom_roush.pdfbox.pdmodel.PDPageContentStream(
                document,
                page,
                com.tom_roush.pdfbox.pdmodel.PDPageContentStream.AppendMode.APPEND,
                true,
                true
            )
            contentStream.drawImage(pdImage, x, y, w, h)
            contentStream.close()
            
            document.save(tempFile)
            document.close()
            document = null
            
            val uriStr = uri.toString()
            textCache.remove(uriStr)
            positionCache.remove(uriStr)
            
            var outputStream: java.io.OutputStream? = null
            try {
                outputStream = openOutputStream(context, uri)
                if (outputStream != null) {
                    tempFile.inputStream().use { input ->
                        input.copyTo(outputStream)
                    }
                    return@withContext true
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error writing stamp back to PDF", e)
            } finally {
                outputStream?.close()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error adding stamp to PDF page $pageIndex", e)
        } finally {
            try {
                document?.close()
                inputStream?.close()
            } catch (e: Exception) {
                // Ignore
            }
            if (tempFile.exists()) tempFile.delete()
        }
        return@withContext false
    }
}
