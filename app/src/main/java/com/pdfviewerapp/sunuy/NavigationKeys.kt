package com.pdfviewerapp.sunuy

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable data object Splash : NavKey
@Serializable data object Home : NavKey
@Serializable data object SettingsPage : NavKey
@Serializable data class PdfViewer(val pdfPath: String) : NavKey
@Serializable data class Bookmarks(val pdfPath: String, val currentPageIndex: Int = 0) : NavKey
