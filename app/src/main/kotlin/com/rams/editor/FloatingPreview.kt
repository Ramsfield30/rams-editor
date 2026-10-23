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
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.TextView
import java.util.Calendar

class FloatingPreview(private val context: Context) {

    private var windowManager: WindowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var floatingView: View? = null
    private var webView: WebView? = null
    private var isShowing = false
    private var isInteractive = false
    private var currentBaseUrl: String? = null
    private val clockHandler = Handler(Looper.getMainLooper())
    private var clockRunnable: Runnable? = null

    private val params = WindowManager.LayoutParams(
        420, 600,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
        WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        x = 20
        y = 80
    }

    fun show(htmlContent: String, baseUrl: String? = null) {
        currentBaseUrl = baseUrl
        if (isShowing) {
            update(htmlContent, baseUrl)
            return
        }

        floatingView = LayoutInflater.from(context)
            .inflate(R.layout.floating_preview, null)

        webView = floatingView?.findViewById(R.id.previewWebView)
        
        floatingView?.findViewById<FrameLayout>(R.id.previewWebHolder)
            ?.addOnLayoutChangeListener { _, left, top, right, bottom, _, _, _, _ ->
                applyVirtualScreen(right - left, bottom - top)
            }
            
        webView?.apply {
            webViewClient = WebViewClient()
            settings.javaScriptEnabled = true
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = false
            settings.setSupportZoom(true)
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            settings.textZoom = 100
            settings.allowFileAccess = true
            settings.allowFileAccessFromFileURLs = true
            settings.cacheMode = WebSettings.LOAD_NO_CACHE
            clipToOutline = true
            loadDataWithBaseURL(
                currentBaseUrl,
                injectViewport(htmlContent),
                "text/html",
                "UTF-8",
                null
            )
        }

        val btnClose = floatingView?.findViewById<TextView>(R.id.btnClose)
        btnClose?.setOnClickListener { dismiss() }

        val btnFullscreen = floatingView?.findViewById<TextView>(R.id.btnFullscreen)
        btnFullscreen?.setOnClickListener { }

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

    fun update(htmlContent: String, baseUrl: String? = null) {
        if (baseUrl != null) currentBaseUrl = baseUrl
        webView?.loadDataWithBaseURL(
            currentBaseUrl,
            injectViewport(htmlContent),
            "text/html",
            "UTF-8",
            null
        )
    }

    fun updateCss(cssContent: String) {
        val escaped = cssContent
            .replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\n", "\\n")
            .replace("\r", "")
        webView?.evaluateJavascript(
            """
            var style = document.getElementById('rams-live-css');
            if (!style) {
                style = document.createElement('style');
                style.id = 'rams-live-css';
                document.head.appendChild(style);
            }
            style.textContent = '$escaped';
            """.trimIndent(),
            null
        )
    }

    fun updateJs(jsContent: String) {
        val escaped = jsContent
            .replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\n", "\\n")
            .replace("\r", "")
        webView?.evaluateJavascript(
            """
            try {
                var script = document.getElementById('rams-live-js');
                if (script) script.remove();
                script = document.createElement('script');
                script.id = 'rams-live-js';
                script.textContent = '$escaped';
                document.body.appendChild(script);
            } catch(e) {
                console.error(e);
            }
            """.trimIndent(),
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
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
        } else {
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
        }
        if (isShowing) windowManager.updateViewLayout(floatingView, params)
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
                    if (!isDragging) setInteractive(!isInteractive)
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

    private fun applyVirtualScreen(holderW: Int, holderH: Int) {
        val wv = webView ?: return
        if (holderW <= 0 || holderH <= 0) return

        // Page lays out at your real screen width, then the view is shrunk to fit
        val virtualW = context.resources.displayMetrics.widthPixels
        val virtualH = (virtualW.toFloat() * holderH / holderW).toInt()
        val scale = holderW.toFloat() / virtualW

        val lp = wv.layoutParams as FrameLayout.LayoutParams
        if (lp.width == virtualW && lp.height == virtualH && wv.scaleX == scale) return

        lp.width = virtualW
        lp.height = virtualH
        wv.layoutParams = lp
        wv.pivotX = 0f
        wv.pivotY = 0f
        wv.scaleX = scale
        wv.scaleY = scale
    }

    fun setSizePercent(percent: Int) {
        val dm = context.resources.displayMetrics
        val w = dm.widthPixels * percent / 100
        val inner = w - (12 * dm.density).toInt()      // 6dp margin each side
        val chrome = (80 * dm.density).toInt()         // toolbar + margins + status bar + home bar
        params.width = w
        params.height = (inner * dm.heightPixels.toFloat() / dm.widthPixels).toInt() + chrome
        if (isShowing) windowManager.updateViewLayout(floatingView, params)
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