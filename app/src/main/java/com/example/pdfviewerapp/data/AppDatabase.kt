package com.example.pdfviewerapp.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.pdfviewerapp.data.entities.Bookmark
import com.example.pdfviewerapp.data.entities.RecentPdf

@Database(entities = [RecentPdf::class, Bookmark::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun recentPdfDao(): RecentPdfDao
    abstract fun bookmarkDao(): BookmarkDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "pdf_viewer_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
