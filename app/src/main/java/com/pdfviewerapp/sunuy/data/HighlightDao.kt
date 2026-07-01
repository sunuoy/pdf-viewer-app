package com.pdfviewerapp.sunuy.data

import androidx.room.*
import com.pdfviewerapp.sunuy.data.entities.HighlightAnnotation
import kotlinx.coroutines.flow.Flow

@Dao
interface HighlightDao {
    @Query("SELECT * FROM highlights WHERE pdfPath = :pdfPath ORDER BY timestamp DESC")
    fun getHighlightsForPdf(pdfPath: String): Flow<List<HighlightAnnotation>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHighlight(highlight: HighlightAnnotation)

    @Delete
    suspend fun deleteHighlight(highlight: HighlightAnnotation)

    @Query("DELETE FROM highlights WHERE id = :id")
    suspend fun deleteHighlightById(id: Int)
}
