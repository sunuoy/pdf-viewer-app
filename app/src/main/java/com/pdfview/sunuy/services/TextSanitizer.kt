package com.pdfview.sunuy.services

object TextSanitizer {

    /**
     * Cleans up markdown markup or HTML before sending strings to the TTS engine.
     */
    fun sanitizeForSpeech(rawInput: String): String {
        return rawInput
            .replace(Regex("<[^>]*>"), "") // Strips standard HTML brackets
            .replace(Regex("[\\*\\_\\#\\`\\~]"), "") // Strips common Markdown structures
            .trim()
    }

    /**
     * Splits a content chapter string into short verbalizable sentences.
     */
    fun splitIntoSentences(chapterContent: String): List<String> {
        val sanitized = sanitizeForSpeech(chapterContent)
        return sanitized.split(Regex("(?<=[.!?])\\s+"))
            .filter { it.isNotBlank() }
    }
}
