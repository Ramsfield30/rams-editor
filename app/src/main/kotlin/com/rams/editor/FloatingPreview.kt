package com.rams.editor

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FloatingPreview(private val context: Context) {

    private var windowManager: WindowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var floatingView: View? = null
    private var webView: WebView? = null
    var onFocusReturn: (() -> Unit)? = null
    private var holder: FrameLayout? = null
    private var statusBar: ViewGroup? = null
    private var homeBar: View? = null
    private var pendingView: WebView? = null
    private var spareView: WebView? = null
    private var loadId = 0
    private var isShowing = false
    private var isInteractive = false
    private var currentBaseUrl: String? = null
    private val clockHandler = Handler(Looper.getMainLooper())
    private var clockRunnable: Runnable? = null
    private var restoreScrollY = 0
    private var firstHtml: String? = null

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
        ConsoleLog.clear()
        currentBaseUrl = baseUrl
        if (isShowing) {
            update(htmlContent, baseUrl, keepScroll = false)
            return
        }

        floatingView = LayoutInflater.from(context)
            .inflate(R.layout.floating_preview, null)

        webView = floatingView?.findViewById(R.id.previewWebView)
        holder = floatingView?.findViewById(R.id.previewWebHolder)
        statusBar = floatingView?.findViewById(R.id.previewStatusBar)
        homeBar = floatingView?.findViewById(R.id.previewHomeBar)
        holder?.addOnLayoutChangeListener { _, left, top, right, bottom, _, _, _, _ ->
            applyVirtualScreen(right - left, bottom - top)
        }
        webView?.apply {
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    val y = restoreScrollY
                    if (y > 0) {
                        view?.evaluateJavascript("window.scrollTo(0,$y)", null)
                        // Images and fonts can change the height a moment later
                        view?.postDelayed({
                            view.evaluateJavascript("window.scrollTo(0,$y)", null)
                        }, 250)
                    }
                    applyBarColors(view)
                }
            }
            webChromeClient = ConsoleLog.chromeClient()
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = false
            settings.setSupportZoom(true)
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            settings.textZoom = 100
            settings.allowFileAccess = true
            settings.allowFileAccessFromFileURLs = true
            settings.cacheMode = WebSettings.LOAD_DEFAULT
            clipToOutline = true
            // Wait until the view has its real size, then load the page
            firstHtml = injectViewport(htmlContent)
            val targetW = context.resources.displayMetrics.widthPixels
            addOnLayoutChangeListener(object : View.OnLayoutChangeListener {
                override fun onLayoutChange(
                    v: View, left: Int, top: Int, right: Int, bottom: Int,
                    oldLeft: Int, oldTop: Int, oldRight: Int, oldBottom: Int
                ) {
                    val html = firstHtml ?: return
                    if (right - left != targetW) return
                    firstHtml = null
                    v.removeOnLayoutChangeListener(this)
                    v.post {
                        (v as WebView).loadDataWithBaseURL(
                            currentBaseUrl, html, "text/html", "UTF-8", null
                        )
                    }
                }
            })
            // Safety net in case the size never matches
            postDelayed({
                val html = firstHtml ?: return@postDelayed
                firstHtml = null
                loadDataWithBaseURL(currentBaseUrl, html, "text/html", "UTF-8", null)
            }, 600)
        }

        val btnClose = floatingView?.findViewById<TextView>(R.id.btnClose)
        btnClose?.setOnClickListener { dismiss() }

        val timeView = floatingView?.findViewById<TextView>(R.id.previewTime)
        val batteryView = floatingView?.findViewById<ImageView>(R.id.previewBattery)
        clockRunnable = object : Runnable {
            override fun run() {
                // Follow the phone's own 12h / 24h setting
                val pattern =
                    if (android.text.format.DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm"
                timeView?.text = SimpleDateFormat(pattern, Locale.getDefault()).format(Date())
                updateBattery(batteryView)
                clockHandler.postDelayed(this, 30000)
            }
        }
        clockHandler.post(clockRunnable!!)

        setupTouch()
        windowManager.addView(floatingView, params)
        isShowing = true
    }

    fun update(htmlContent: String, baseUrl: String? = null, keepScroll: Boolean = true) {
        ConsoleLog.clear()
        if (baseUrl != null) currentBaseUrl = baseUrl
        val front = webView ?: return
        val host = holder ?: return
        val html = injectViewport(htmlContent)
        val base = currentBaseUrl
        val id = ++loadId

        val startLoad: (Int) -> Unit = { scrollY ->
            if (id == loadId && webView === front) {
                val back = pendingView ?: (spareView ?: newBackView()).also {
                    spareView = null
                    matchFront(it, front)
                    host.addView(it, 0)
                }
                pendingView = back
                back.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        val v = view ?: return
                        if (id != loadId || v !== pendingView) return
                        if (scrollY > 0) v.evaluateJavascript("window.scrollTo(0,$scrollY)", null)
                        val swap = Runnable {
                            if (id != loadId || v !== pendingView) return@Runnable
                            host.removeView(front)
                            spareView = front
                            webView = v
                            pendingView = null
                            applyVirtualScreen(host.width, host.height)
                            applyBarColors(v)
                        }
                        if (android.os.Build.VERSION.SDK_INT >= 23) {
                            // Swap exactly when the new page has been drawn
                            v.postVisualStateCallback(
                                id.toLong(),
                                object : WebView.VisualStateCallback() {
                                    override fun onComplete(requestId: Long) {
                                        swap.run()
                                    }
                                }
                            )
                        } else {
                            v.postDelayed(swap, 120)
                        }
                    }
                }
                back.loadDataWithBaseURL(base, html, "text/html", "UTF-8", null)
            }
        }

        if (!keepScroll) {
            startLoad(0)
            return
        }
        front.evaluateJavascript(
            "(function(){return Math.round(window.pageYOffset||document.documentElement.scrollTop||0)})()"
        ) { value ->
            startLoad(value?.trim('"')?.toDoubleOrNull()?.toInt() ?: 0)
        }
    }

    private fun newBackView(): WebView {
        return WebView(context).apply {
            webChromeClient = ConsoleLog.chromeClient()
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = false
            settings.setSupportZoom(true)
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            settings.textZoom = 100
            settings.allowFileAccess = true
            settings.allowFileAccessFromFileURLs = true
            settings.cacheMode = WebSettings.LOAD_DEFAULT
            clipToOutline = true
        }
    }

    private fun matchFront(back: WebView, front: WebView) {
        val flp = front.layoutParams as FrameLayout.LayoutParams
        back.layoutParams = FrameLayout.LayoutParams(flp.width, flp.height)
        back.pivotX = 0f
        back.pivotY = 0f
        back.scaleX = front.scaleX
        back.scaleY = front.scaleY
    }

    fun updateCss(cssContent: String, fileName: String) {
        val escaped = cssContent
            .replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\n", "\\n")
            .replace("\r", "")
        val name = fileName.replace("\\", "\\\\").replace("'", "\\'")
        val id = "rams-live-css-" + fileName.replace(Regex("[^A-Za-z0-9_-]"), "_")
        webView?.evaluateJavascript(
            """
            (function() {
                document.querySelectorAll('link[rel~="stylesheet"], style[data-src]').forEach(function(n) {
                    var ref = n.getAttribute('data-src') || n.getAttribute('href') || '';
                    if (ref.split('?')[0].split('/').pop() === '$name') { n.disabled = true; }
                });
                var style = document.getElementById('$id');
                if (!style) {
                    style = document.createElement('style');
                    style.id = '$id';
                    document.head.appendChild(style);
                }
                style.textContent = '$escaped';
            })();
            """.trimIndent(),
            null
        )
        webView?.postDelayed({ applyBarColors(webView) }, 200)
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
        if (!interactive) {
            webView?.clearFocus()
            onFocusReturn?.invoke()
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

    fun setSizePercent(percent: Int) {
        val screenWidth = context.resources.displayMetrics.widthPixels
        val w = screenWidth * percent / 100
        params.width = w
        params.height = (w * 600f / 420f).toInt()
        if (isShowing) windowManager.updateViewLayout(floatingView, params)
    }

    // Lay the page out at your real screen width, then shrink the view to fit
    private fun applyVirtualScreen(holderW: Int, holderH: Int) {
        val wv = webView ?: return
        if (holderW <= 0 || holderH <= 0) return

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

    // Status bar and home bar take the page's own background color, like a real phone
    private fun applyBarColors(wv: WebView?) {
        wv ?: return
        wv.evaluateJavascript(
            """
            (function() {
                function bg(el) { return getComputedStyle(el).backgroundColor; }
                function clear(c) { return !c || c === 'transparent' || c === 'rgba(0, 0, 0, 0)'; }
                var c = document.body ? bg(document.body) : '';
                if (clear(c)) c = bg(document.documentElement);
                if (clear(c)) c = 'rgb(255, 255, 255)';
                return c;
            })()
            """.trimIndent()
        ) { value ->
            val nums = Regex("[0-9.]+").findAll(value ?: "").map { it.value.toFloat() }.toList()
            if (nums.size < 3) return@evaluateJavascript
            val color = Color.rgb(nums[0].toInt(), nums[1].toInt(), nums[2].toInt())
            val lum = (0.299 * nums[0] + 0.587 * nums[1] + 0.114 * nums[2]) / 255.0
            val fg = if (lum > 0.6) Color.BLACK else Color.WHITE
            statusBar?.let { bar ->
                bar.setBackgroundColor(color)
                for (i in 0 until bar.childCount) {
                    when (val child = bar.getChildAt(i)) {
                        is TextView -> child.setTextColor(fg)
                        is ImageView -> child.setColorFilter(fg)
                    }
                }
            }
            homeBar?.setBackgroundColor(color)
        }
    }

    // Battery icon in the fake status bar follows the real battery level
    private fun updateBattery(view: ImageView?) {
        val intent = context.registerReceiver(
            null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        ) ?: return
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        if (level < 0 || scale <= 0) return
        view?.setImageLevel(level * 10000 / scale)
    }

    fun dismiss() {
        if (isShowing && floatingView != null) {
            clockRunnable?.let { clockHandler.removeCallbacks(it) }
            windowManager.removeView(floatingView)
            floatingView = null
            pendingView?.let { holder?.removeView(it); it.destroy() }
            pendingView = null
            spareView?.destroy()
            spareView = null
            holder = null
            statusBar = null
            homeBar = null
            webView = null
            isShowing = false
            isInteractive = false
        }
    }

    fun isVisible() = isShowing
}