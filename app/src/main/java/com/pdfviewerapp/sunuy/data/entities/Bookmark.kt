package com.pdfviewerapp.sunuy.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "bookmarks")
data class Bookmark(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val pdfPath: String, // Links to a PDF by its path/Uri string
    val pageNumber: Int,
    val note: String,
    val timestamp: Long
)
