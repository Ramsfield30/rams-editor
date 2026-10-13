package com.rams.editor

import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.TextView

class FloatingPreview(private val context: Context) {

    private var windowManager: WindowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var floatingView: View? = null
    private var webView: WebView? = null
    private var isShowing = false

    private val params = WindowManager.LayoutParams(
    500, 700,
    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
    PixelFormat.TRANSLUCENT
).apply {
    gravity = Gravity.TOP or Gravity.START
    x = 20
    y = 80
}

    fun show(htmlContent: String) {
        if (isShowing) {
            update(htmlContent)
            return
        }

        floatingView = LayoutInflater.from(context)
            .inflate(R.layout.floating_preview, null)

        webView = floatingView?.findViewById(R.id.previewWebView)
        webView?.apply {
            webViewClient = WebViewClient()
            settings.javaScriptEnabled = true
            loadDataWithBaseURL(null, htmlContent, "text/html", "UTF-8", null)
        }

        val btnClose = floatingView?.findViewById<TextView>(R.id.btnClose)
        btnClose?.setOnClickListener { dismiss() }

        val btnFullscreen = floatingView?.findViewById<TextView>(R.id.btnFullscreen)
        btnFullscreen?.setOnClickListener {
            // fullscreen coming in v2
        }

        setupDrag()
        windowManager.addView(floatingView, params)
        isShowing = true
    }

    fun update(htmlContent: String) {
        webView?.loadDataWithBaseURL(
            null, htmlContent, "text/html", "UTF-8", null
        )
    }

    fun dismiss() {
        if (isShowing && floatingView != null) {
            windowManager.removeView(floatingView)
            floatingView = null
            webView = null
            isShowing = false
        }
    }

    fun isVisible() = isShowing

    private fun setupDrag() {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        floatingView?.setOnTouchListener { _, event ->
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
                    windowManager.updateViewLayout(floatingView, params)
                    true
                }
                else -> false
            }
        }
    }
}