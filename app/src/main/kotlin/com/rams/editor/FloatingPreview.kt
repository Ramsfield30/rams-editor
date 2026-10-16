package com.rams.editor

import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.TextView
import java.util.Calendar

class FloatingPreview(private val context: Context) {

    private var windowManager: WindowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var floatingView: View? = null
    private var webView: WebView? = null
    private var isShowing = false
    private var isInteractive = false
    private val clockHandler = Handler(Looper.getMainLooper())
    private var clockRunnable: Runnable? = null

    private val params = WindowManager.LayoutParams(
        360, 500,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
        WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
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
            // Full size — scroll don't scale
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = false
            settings.setSupportZoom(true)
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            settings.textZoom = 100
            clipToOutline = true
            loadDataWithBaseURL(
                null,
                injectViewport(htmlContent),
                "text/html",
                "UTF-8",
                null
            )
        }

        val btnClose = floatingView?.findViewById<TextView>(R.id.btnClose)
        btnClose?.setOnClickListener { dismiss() }

        val btnFullscreen = floatingView?.findViewById<TextView>(R.id.btnFullscreen)
        btnFullscreen?.setOnClickListener {
            // fullscreen in v2
        }

        val timeView = floatingView?.findViewById<TextView>(R.id.previewTime)
        clockRunnable = object : Runnable {
            override fun run() {
                val cal = Calendar.getInstance()
                val h = cal.get(Calendar.HOUR_OF_DAY)
                val m = cal.get(Calendar.MINUTE)
                timeView?.text = String.format("%02d:%02d", h, m)
                clockHandler.postDelayed(this, 30000)
            }
        }
        clockHandler.post(clockRunnable!!)

        setupTouch()
        windowManager.addView(floatingView, params)
        isShowing = true
    }

    fun update(htmlContent: String) {
        webView?.loadDataWithBaseURL(
            null,
            injectViewport(htmlContent),
            "text/html",
            "UTF-8",
            null
        )
    }

    private fun injectViewport(html: String): String {
        val viewport = """<meta name="viewport" content="width=device-width, initial-scale=1.0">"""
        return when {
            html.contains("<head>", ignoreCase = true) -> {
                html.replaceFirst(
                    Regex("<head>", RegexOption.IGNORE_CASE),
                    "<head>\n$viewport"
                )
            }
            html.contains("<html>", ignoreCase = true) -> {
                html.replaceFirst(
                    Regex("<html>", RegexOption.IGNORE_CASE),
                    "<html>\n<head>$viewport</head>"
                )
            }
            else -> "$viewport\n$html"
        }
    }

    private fun setInteractive(interactive: Boolean) {
        if (isInteractive == interactive) return
        isInteractive = interactive
        params.flags = if (interactive) {
            // Allow forms and buttons to work
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
        } else {
            // Give keyboard back to editor
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
        }
        if (isShowing) {
            windowManager.updateViewLayout(floatingView, params)
        }
    }

    private fun setupTouch() {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDragging = false

        floatingView?.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY
                    if (Math.abs(dx) > 10 || Math.abs(dy) > 10) {
                        isDragging = true
                        params.x = initialX + dx.toInt()
                        params.y = initialY + dy.toInt()
                        windowManager.updateViewLayout(floatingView, params)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) {
                        setInteractive(!isInteractive)
                    }
                    true
                }
                MotionEvent.ACTION_OUTSIDE -> {
                    setInteractive(false)
                    false
                }
                else -> false
            }
        }
    }

    fun dismiss() {
        if (isShowing && floatingView != null) {
            clockRunnable?.let { clockHandler.removeCallbacks(it) }
            windowManager.removeView(floatingView)
            floatingView = null
            webView = null
            isShowing = false
            isInteractive = false
        }
    }

    fun isVisible() = isShowing
}