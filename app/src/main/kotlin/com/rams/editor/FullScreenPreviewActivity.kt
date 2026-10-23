package com.rams.editor

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

object PreviewHolder {
    var html: String = ""
    var baseUrl: String? = null
}

class FullScreenPreviewActivity : AppCompatActivity() {

    private lateinit var webView: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()

        webView = WebView(this).apply {
            webViewClient = WebViewClient()
            webChromeClient = ConsoleLog.chromeClient()
            settings.javaScriptEnabled = true
            settings.allowFileAccess = true
            settings.allowFileAccessFromFileURLs = true
            settings.cacheMode = WebSettings.LOAD_NO_CACHE
            settings.setSupportZoom(true)
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
        }

        val density = resources.displayMetrics.density
        val size = (40 * density).toInt()
        val margin = (12 * density).toInt()

        val closeBtn = TextView(this).apply {
            text = "✕"
            textSize = 18f
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0x99000000.toInt())
            gravity = Gravity.CENTER
            setOnClickListener { finish() }
        }

        val root = FrameLayout(this)
        root.addView(
            webView,
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        root.addView(
            closeBtn,
            FrameLayout.LayoutParams(size, size, Gravity.TOP or Gravity.END).apply {
                setMargins(margin, margin, margin, margin)
            }
        )
        setContentView(root)

        webView.loadDataWithBaseURL(
            PreviewHolder.baseUrl, PreviewHolder.html,
            "text/html", "UTF-8", null
        )
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }
}