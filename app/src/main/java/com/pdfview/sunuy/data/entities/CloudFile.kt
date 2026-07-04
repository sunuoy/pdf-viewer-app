package com.pdfview.sunuy.data.entities

data class CloudFile(
    val id: String,
    val name: String,
    val source: String, // "google_drive" or "one_drive"
    val thumbnailUrl: String? = null,
    val size: Long = 0,
    val mimeType: String = "application/pdf"
) {
    fun getFormattedSize(): String {
        if (size <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
        return String.format("%.1f %s", size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
    }
}
