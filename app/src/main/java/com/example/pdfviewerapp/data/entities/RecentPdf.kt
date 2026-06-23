package com.example.pdfviewerapp.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recent_pdfs")
data class RecentPdf(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val path: String, // File path or Uri string
    val lastOpened: Long,
    val lastPage: Int
)
