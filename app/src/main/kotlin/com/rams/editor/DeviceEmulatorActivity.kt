package com.rams.editor

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.PopupMenu
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class DeviceEmulatorActivity : AppCompatActivity() {

    private data class Device(val name: String, val w: Int, val h: Int)

    private val devices = listOf(
        Device("Responsive", 375, 667),
        Device("iPhone SE", 375, 667),
        Device("iPhone XR", 414, 896),
        Device("iPhone 14 Pro Max", 430, 932),
        Device("Pixel 7", 412, 915),
        Device("Galaxy S20 Ultra", 412, 915),
        Device("Galaxy S8+", 360, 740),
        Device("Galaxy Fold", 280, 653),
        Device("Surface Duo", 540, 720),
        Device("iPad Mini", 768, 1024),
        Device("iPad Air", 820, 1180)
    )

    private lateinit var webView: WebView
    private lateinit var stage: FrameLayout
    private lateinit var deviceLabel: TextView
    private lateinit var widthInput: EditText
    private lateinit var heightInput: EditText
    private lateinit var subtitle: TextView

    private var deviceName = "Responsive"
    private var cssW = 375
    private var cssH = 667

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        setContentView(R.layout.activity_device_emulator)

        webView = findViewById(R.id.emuWebView)
        stage = findViewById(R.id.emuStage)
        deviceLabel = findViewById(R.id.emuDevice)
        widthInput = findViewById(R.id.emuWidth)
        heightInput = findViewById(R.id.emuHeight)
        subtitle = findViewById(R.id.emuSubtitle)

        webView.apply {
            webViewClient = WebViewClient()
            webChromeClient = ConsoleLog.chromeClient()
            settings.javaScriptEnabled = true
            settings.allowFileAccess = true
            settings.allowFileAccessFromFileURLs = true
            settings.cacheMode = WebSettings.LOAD_NO_CACHE
        }

        findViewById<View>(R.id.emuBack).setOnClickListener { finish() }
        findViewById<View>(R.id.emuRefresh).setOnClickListener { loadPage() }

        // Device picker
        deviceLabel.setOnClickListener { anchor ->
            val menu = PopupMenu(this, anchor)
            devices.forEachIndexed { i, d -> menu.menu.add(0, i, i, d.name) }
            menu.setOnMenuItemClickListener { item ->
                val d = devices[item.itemId]
                deviceName = d.name
                cssW = d.w
                cssH = d.h
                refreshInputs()
                applySize()
                true
            }
            menu.show()
        }

        // Rotate
        findViewById<View>(R.id.emuRotate).setOnClickListener {
            val t = cssW
            cssW = cssH
            cssH = t
            refreshInputs()
            applySize()
        }

        // S / M / L chips set the width
        mapOf(
            R.id.emuChipS to 320,
            R.id.emuChipM to 375,
            R.id.emuChipL to 435
        ).forEach { (id, w) ->
            findViewById<View>(id).setOnClickListener {
                deviceName = "Responsive"
                cssW = w
                refreshInputs()
                applySize()
            }
        }

        // Typing a custom size
        val onDone = TextView.OnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                readInputs()
                true
            } else false
        }
        widthInput.setOnEditorActionListener(onDone)
        heightInput.setOnEditorActionListener(onDone)

        // Re-fit whenever the stage changes size
        stage.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> applySize() }

        refreshInputs()
        loadPage()
    }

    private fun loadPage() {
        ConsoleLog.clear()
        webView.loadDataWithBaseURL(
            PreviewHolder.baseUrl, PreviewHolder.html,
            "text/html", "UTF-8", null
        )
    }

    private fun readInputs() {
        val w = widthInput.text.toString().toIntOrNull()
        val h = heightInput.text.toString().toIntOrNull()
        if (w != null) cssW = w.coerceIn(200, 3000)
        if (h != null) cssH = h.coerceIn(200, 3000)
        deviceName = "Responsive"

        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(widthInput.windowToken, 0)
        widthInput.clearFocus()
        heightInput.clearFocus()

        refreshInputs()
        applySize()
    }

    private fun refreshInputs() {
        widthInput.setText(cssW.toString())
        heightInput.setText(cssH.toString())
        deviceLabel.text = "$deviceName ▾"
    }

    // Lay the page out at the chosen CSS size, then shrink the view to fit the screen
    private fun applySize() {
        val sw = stage.width
        val sh = stage.height
        if (sw <= 0 || sh <= 0) return

        val d = resources.displayMetrics.density
        val pxW = (cssW * d).toInt()
        val pxH = (cssH * d).toInt()
        val margin = 12 * d
        val scale = minOf(1f, (sw - 2 * margin) / pxW, (sh - 2 * margin) / pxH)

        val lp = webView.layoutParams as FrameLayout.LayoutParams
        if (lp.width != pxW || lp.height != pxH) {
            lp.width = pxW
            lp.height = pxH
            webView.layoutParams = lp
        }
        webView.pivotX = 0f
        webView.pivotY = 0f
        webView.scaleX = scale
        webView.scaleY = scale
        webView.translationX = (sw - pxW * scale) / 2f
        webView.translationY = (sh - pxH * scale) / 2f

        subtitle.text = "$deviceName · ${cssW}×${cssH} · ${(scale * 100).toInt()}%"
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }
}