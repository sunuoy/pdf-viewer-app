package com.pdfsuny.app.utils

import android.content.Context
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient

object WebToPdfUtils {
    fun convertWebToPdf(context: Context, url: String) {
        val webView = WebView(context)
        webView.settings.apply {
            javaScriptEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = false
            displayZoomControls = false
        }

        var isPrinted = false
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                if (isPrinted) return
                isPrinted = true
                
                val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
                val jobName = "Webpage_Document"
                val printAdapter = view.createPrintDocumentAdapter(jobName)

                printManager.print(
                    jobName,
                    printAdapter,
                    PrintAttributes.Builder().build()
                )
            }
        }

        webView.loadUrl(url)
    }
}
