package com.pdfviewerapp.sunuy.services

import java.io.File

object RtfService {
    fun extractText(file: File): String {
        try {
            val rtf = file.readText(Charsets.US_ASCII)
            val sb = StringBuilder()
            var i = 0
            val len = rtf.length
            
            val ignoreStack = mutableListOf<Boolean>()
            var currentIgnore = false
            
            while (i < len) {
                val c = rtf[i]
                if (c == '{') {
                    ignoreStack.add(currentIgnore)
                    i++
                    if (i < len && rtf[i] == '\\') {
                        i++
                        val startWord = i
                        while (i < len && rtf[i].isLetterOrDigit()) {
                            i++
                        }
                        val word = rtf.substring(startWord, i)
                        if (word == "*" || word == "fonttbl" || word == "colortbl" || 
                            word == "stylesheet" || word == "info" || word == "generator" || 
                            word == "themeData" || word == "colorschememapping") {
                            currentIgnore = true
                        }
                        i = startWord - 1
                    }
                } else if (c == '}') {
                    if (ignoreStack.isNotEmpty()) {
                        currentIgnore = ignoreStack.removeAt(ignoreStack.size - 1)
                    } else {
                        currentIgnore = false
                    }
                    i++
                } else if (c == '\\') {
                    i++
                    if (i >= len) break
                    val next = rtf[i]
                    if (next == '\\' || next == '{' || next == '}') {
                        if (!currentIgnore) {
                            sb.append(next)
                        }
                        i++
                    } else if (next == '\'') {
                        if (i + 2 < len) {
                            val hex = rtf.substring(i + 1, i + 3)
                            if (!currentIgnore) {
                                try {
                                    sb.append(hex.toInt(16).toChar())
                                } catch (e: Exception) {}
                            }
                            i += 3
                        } else {
                            i++
                        }
                    } else {
                        val startWord = i
                        while (i < len && rtf[i].isLetterOrDigit()) {
                            i++
                        }
                        val word = rtf.substring(startWord, i)
                        
                        val paramStart = i
                        if (i < len && rtf[i] == '-') {
                            i++
                        }
                        while (i < len && rtf[i].isDigit()) {
                            i++
                        }
                        val param = if (paramStart < i) rtf.substring(paramStart, i).toIntOrNull() else null
                        
                        if (!currentIgnore) {
                            if (word == "par" || word == "line" || word == "row") {
                                sb.append("\n")
                            } else if (word == "tab") {
                                sb.append("\t")
                            } else if (word == "u" && param != null) {
                                sb.append(param.toChar())
                            }
                        }
                        
                        if (i < len && rtf[i] == ' ') {
                            i++
                        }
                    }
                } else {
                    if (!currentIgnore && c != '\r' && c != '\n') {
                        sb.append(c)
                    }
                    i++
                }
            }
            return sb.toString().trim()
        } catch (e: Exception) {
            return "Error reading RTF file: ${e.message}"
        }
    }
}
