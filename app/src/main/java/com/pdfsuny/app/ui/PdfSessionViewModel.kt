package com.pdfsuny.app.ui

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

class PdfSessionViewModel : ViewModel() {
    private val _jumpToPageEvent = MutableSharedFlow<Int>(extraBufferCapacity = 1)
    val jumpToPageEvent: SharedFlow<Int> = _jumpToPageEvent

    fun jumpToPage(pageIndex: Int) {
        _jumpToPageEvent.tryEmit(pageIndex)
    }
}
