package com.rams.editor

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.TextView

class FloatingPreview(private val context: Context) {

    private val windowManager: WindowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private var floatingView: View? = null
    private var webView: WebView? = null
    private var isShowing = false

    private val params: WindowManager.LayoutParams = createParams()

    private fun createParams(): WindowManager.LayoutParams {
        val dm = context.resources.displayMetrics

        // ~92% of screen width, ~65% of screen height → phone-like proportions
        val widthPx = (dm.widthPixels * 0.92f).toInt()
        val heightPx = (dm.heightPixels * 0.65f).toInt()

        val windowType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        return WindowManager.LayoutParams(
            widthPx,
            heightPx,
            windowType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 20
            y = 80
        }
    }

    fun show(htmlContent: String) {
        if (isShowing) {
            update(htmlContent)
            return
        }

        val view = LayoutInflater.from(context)
            .inflate(R.layout.floating_preview, null)
        floatingView = view

        webView = view.findViewById<WebView>(R.id.previewWebView).apply {
            webViewClient = WebViewClient()

            settings.javaScriptEnabled = true

            // ✅ These are the KEY changes to stop the "squeezed" look.
            // The WebView now uses its own pixel width as the layout viewport,
            // just like a real phone browser does.
            settings.useWideViewPort = false
            settings.loadWithOverviewMode = false

            settings.setSupportZoom(true)
            settings.builtInZoomControls = true
            settings.displayZoomControls = false

            loadDataWithBaseURL(null, ensureViewport(htmlContent), "text/html", "UTF-8", null)
        }

        view.findViewById<TextView>(R.id.btnClose)?.setOnClickListener { dismiss() }
        view.findViewById<TextView>(R.id.btnFullscreen)?.setOnClickListener {
            // fullscreen coming in v2
        }

        setupDrag(view)

        windowManager.addView(view, params)
        isShowing = true
    }

    fun update(htmlContent: String) {
        webView?.loadDataWithBaseURL(
            null, ensureViewport(htmlContent), "text/html", "UTF-8", null
        )
    }

    fun dismiss() {
        floatingView?.let { view ->
            if (isShowing) {
                windowManager.removeView(view)
                isShowing = false
            }
        }
        webView?.destroy()
        webView = null
        floatingView = null
    }

    fun isVisible() = isShowing

    private fun setupDrag(view: View) {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager.updateViewLayout(view, params)
                    true
                }
                else -> false
            }
        }
    }

    /**
     * If the HTML has no <meta name="viewport">, inject one so the page renders
     * at the WebView's actual width (phone-like) instead of the default 980px
     * desktop viewport, which is what causes the "zoomed out" look.
     */
    private fun ensureViewport(html: String): String {
        if (html.contains("name=\"viewport\"", ignoreCase = true)) return html

        val viewportTag =
            """<meta name="viewport" content="width=device-width, initial-scale=1.0">"""

        return when {
            html.contains("<head>", ignoreCase = true) ->
                html.replaceFirst(
                    Regex("<head>", RegexOption.IGNORE_CASE),
                    "<head>$viewportTag"
                )

            html.contains("<html>", ignoreCase = true) ->
                html.replaceFirst(
                    Regex("<html>", RegexOption.IGNORE_CASE),
                    "<html><head>$viewportTag</head>"
                )

            else -> viewportTag + html
        }
    }
}