package com.pdfviewerapp.sunuy.data

import androidx.room.*
import com.pdfviewerapp.sunuy.data.entities.RecentPdf
import kotlinx.coroutines.flow.Flow

@Dao
interface RecentPdfDao {
    @Query("SELECT * FROM recent_pdfs ORDER BY lastOpened DESC")
    fun getAllRecentPdfs(): Flow<List<RecentPdf>>

    @Query("SELECT * FROM recent_pdfs WHERE path = :path LIMIT 1")
    suspend fun getRecentPdfByPath(path: String): RecentPdf?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecentPdf(recentPdf: RecentPdf)

    @Delete
    suspend fun deleteRecentPdf(recentPdf: RecentPdf)

    @Query("DELETE FROM recent_pdfs WHERE path = :path")
    suspend fun deleteRecentPdfByPath(path: String)

    @Query("DELETE FROM recent_pdfs")
    suspend fun deleteAllRecentPdfs()
}
