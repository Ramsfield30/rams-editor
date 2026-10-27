package com.rams.editor

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.View
import android.webkit.ConsoleMessage
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

object PreviewHolder {
    var html: String = ""
    var baseUrl: String? = null
    var fileName: String = "index.html"
}

class FullScreenPreviewActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private var previousListener: (() -> Unit)? = null
    private var filter = ConsoleLog.FILTER_ALL

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        setContentView(R.layout.activity_full_preview)

        val title = findViewById<TextView>(R.id.previewTitle)
        val subtitle = findViewById<TextView>(R.id.previewSubtitle)

        webView = findViewById(R.id.previewWebView)
        webView.apply {
            webViewClient = WebViewClient()
            webChromeClient = ConsoleLog.chromeClient { t -> title.text = t }
            settings.javaScriptEnabled = true
            settings.allowFileAccess = true
            settings.allowFileAccessFromFileURLs = true
            settings.cacheMode = WebSettings.LOAD_NO_CACHE
            settings.setSupportZoom(true)
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
        }

        findViewById<View>(R.id.btnPreviewBack).setOnClickListener { finish() }

        fun updateSubtitle() {
            val d = resources.displayMetrics.density
            subtitle.text = "${PreviewHolder.fileName} · " +
                "${(webView.width / d).toInt()}×${(webView.height / d).toInt()}"
        }
        webView.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> updateSubtitle() }

        setupConsole()

        findViewById<TextView>(R.id.btnPreviewRefresh).setOnClickListener { loadPage() }

        findViewById<TextView>(R.id.btnPreviewMenu).setOnClickListener { anchor ->
            val menu = PopupMenu(this, anchor)
            menu.menu.add(0, 1, 0, "Open in Browser")
            menu.menu.add(0, 2, 1, "Close")
            menu.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    1 -> Toast.makeText(this, "Coming soon", Toast.LENGTH_SHORT).show()
                    2 -> finish()
                }
                true
            }
            menu.show()
        }

        loadPage()
    }

    private fun loadPage() {
        ConsoleLog.clear()
        webView.loadDataWithBaseURL(
            PreviewHolder.baseUrl, PreviewHolder.html,
            "text/html", "UTF-8", null
        )
    }

    private fun setupConsole() {
        val panel = findViewById<LinearLayout>(R.id.fsConsolePanel)
        val text = findViewById<TextView>(R.id.fsConsoleText)
        val scroll = findViewById<ScrollView>(R.id.fsConsoleScroll)
        val errorDot = findViewById<View>(R.id.consoleErrorDot)

        val chips = listOf(
            findViewById<TextView>(R.id.fsFilterAll) to ConsoleLog.FILTER_ALL,
            findViewById<TextView>(R.id.fsFilterInfo) to ConsoleLog.FILTER_INFO,
            findViewById<TextView>(R.id.fsFilterWarning) to ConsoleLog.FILTER_WARNING,
            findViewById<TextView>(R.id.fsFilterError) to ConsoleLog.FILTER_ERROR
        )

        fun render() {
            text.text = ConsoleLog.render(filter)
            scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }

            chips.forEach { (chip, f) ->
                val selected = f == filter
                chip.setBackgroundColor(if (selected) 0xFF37373D.toInt() else 0x00000000)
                chip.setTextColor(if (selected) 0xFFFFFFFF.toInt() else 0xFFAAAAAA.toInt())
            }

            val hasError = ConsoleLog.entries.any {
                it.level == ConsoleMessage.MessageLevel.ERROR
            }
            errorDot.visibility =
                if (hasError && panel.visibility != View.VISIBLE) View.VISIBLE else View.GONE
        }

        previousListener = ConsoleLog.onChange
        ConsoleLog.onChange = { runOnUiThread { render() } }

        chips.forEach { (chip, f) ->
            chip.setOnClickListener {
                filter = f
                render()
            }
        }

        findViewById<TextView>(R.id.btnPreviewConsole).setOnClickListener {
            if (panel.visibility == View.VISIBLE) {
                panel.visibility = View.GONE
            } else {
                panel.visibility = View.VISIBLE
                render()
            }
        }

        findViewById<TextView>(R.id.fsConsoleCopy).setOnClickListener {
            val toCopy = ConsoleLog.plainText(filter)
            if (toCopy.isEmpty()) {
                Toast.makeText(this, "Nothing to copy", Toast.LENGTH_SHORT).show()
            } else {
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("console", toCopy))
                Toast.makeText(this, "Console copied", Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<TextView>(R.id.fsConsoleClear).setOnClickListener { ConsoleLog.clear() }
        findViewById<TextView>(R.id.fsConsoleClose).setOnClickListener {
            panel.visibility = View.GONE
        }
    }

    override fun onDestroy() {
        ConsoleLog.onChange = previousListener
        webView.destroy()
        super.onDestroy()
    }
}