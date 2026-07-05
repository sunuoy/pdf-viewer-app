package com.pdfsuny.app.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "highlights")
data class HighlightAnnotation(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val pdfPath: String, // Links to a PDF by its path/Uri string
    val phrase: String, // The exact word or phrase to highlight
    val colorHex: String, // The hex color code (e.g. #FFEB3B for yellow)
    val timestamp: Long
)
