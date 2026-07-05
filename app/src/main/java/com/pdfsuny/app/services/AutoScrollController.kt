package com.pdfsuny.app.services

enum class AutoScrollMode(val displayName: String) {
    ROLLING_BLIND_PIXEL("Rolling blind by pixel"),
    ROLLING_BLIND_LINE("Rolling blind by line"),
    BY_PIXEL("Scroll by pixel"),
    BY_LINE("Scroll by line"),
    BY_PAGE("Scroll by page"),
    RSVP("Speed read (RSVP)")
}

data class AutoScrollState(
    val isActive: Boolean = false,
    val mode: AutoScrollMode = AutoScrollMode.BY_PIXEL,
    val speedMultiplier: Float = 1.0f
)
