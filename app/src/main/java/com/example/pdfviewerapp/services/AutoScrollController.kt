package com.pdfviewerapp.sunuy.services

enum class AutoScrollMode(val displayName: String) {
    ROLLING_BLIND("Rolling Blind"),
    BY_PIXEL("By Pixel"),
    BY_LINE("By Line"),
    BY_PAGE("By Page")
}

data class AutoScrollState(
    val isActive: Boolean = false,
    val mode: AutoScrollMode = AutoScrollMode.BY_PIXEL,
    val speedMultiplier: Float = 1.0f // 0.5x to 5.0x
)
