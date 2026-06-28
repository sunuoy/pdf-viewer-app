package com.pdfviewerapp.sunuy.services

import androidx.compose.ui.graphics.Color

sealed class ReadingTheme(
    val id: String,
    val isDark: Boolean,
    val backgroundColor: Color,
    val textColor: Color,
    val accentColor: Color,
    val webCssOverride: String
) {
    object Day : ReadingTheme(
        id = "DAY",
        isDark = false,
        backgroundColor = Color(0xFFFFFFFF),
        textColor = Color(0xFF1A1A1A),
        accentColor = Color(0xFF0066CC),
        webCssOverride = "body { background-color: #ffffff !important; color: #1a1a1a !important; } a { color: #0066cc !important; }"
    )

    object Night : ReadingTheme(
        id = "NIGHT",
        isDark = true,
        backgroundColor = Color(0xFF1E1E1E),
        textColor = Color(0xFFE5E5E5),
        accentColor = Color(0xFFBB86FC),
        webCssOverride = "body { background-color: #1e1e1e !important; color: #e5e5e5 !important; } a { color: #bb86fc !important; }"
    )

    object Sepia : ReadingTheme(
        id = "SEPIA",
        isDark = false,
        backgroundColor = Color(0xFFFBF0D9),
        textColor = Color(0xFF433422),
        accentColor = Color(0xFF8B4513),
        webCssOverride = "body { background-color: #fbf0d9 !important; color: #433422 !important; } a { color: #8b4513 !important; }"
    )

    object Solarized : ReadingTheme(
        id = "SOLARIZED",
        isDark = false,
        backgroundColor = Color(0xFFFDF6E3),
        textColor = Color(0xFF657B83),
        accentColor = Color(0xFF2AA198),
        webCssOverride = "body { background-color: #fdf6e3 !important; color: #657b83 !important; } a { color: #2aa198 !important; }"
    )

    object Nord : ReadingTheme(
        id = "NORD",
        isDark = true,
        backgroundColor = Color(0xFF2E3440),
        textColor = Color(0xFFD8DEE9),
        accentColor = Color(0xFF88C0D0),
        webCssOverride = "body { background-color: #2e3440 !important; color: #d8dee9 !important; } a { color: #88c0d0 !important; }"
    )

    object OledForest : ReadingTheme(
        id = "OLED_FOREST",
        isDark = true,
        backgroundColor = Color(0xFF000000),
        textColor = Color(0xFF8FBC8F),
        accentColor = Color(0xFF2E8B57),
        webCssOverride = "body { background-color: #000000 !important; color: #8fbc8f !important; } a { color: #2e8b57 !important; }"
    )

    companion object {
        val allThemes = listOf(Day, Night, Sepia, Solarized, Nord, OledForest)
    }
}
