package com.rams.editor

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Outline
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class DeviceEmulatorActivity : AppCompatActivity() {

    private data class Device(val name: String, val w: Int, val h: Int)

    // icon: 0 = phone, 1 = tablet, 2 = laptop, 3 = 4K badge
    private data class Preset(val label: String, val w: Int, val icon: Int)

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

    private val presets = listOf(
        Preset("S - 320px", 320, 0),
        Preset("M - 375px", 375, 0),
        Preset("L - 435px", 435, 0),
        Preset("768px", 768, 1),
        Preset("1024px", 1024, 2),
        Preset("L - 1440px", 1440, 2),
        Preset("2560px", 2560, 3)
    )

    private lateinit var webView: WebView
    private lateinit var stage: FrameLayout
    private lateinit var frame: FrameLayout
    private lateinit var deviceLabel: TextView
    private lateinit var widthInput: EditText
    private lateinit var heightInput: EditText
    private lateinit var handleRight: View
    private lateinit var handleBottom: View
    private lateinit var chipRow: LinearLayout
    private val chipViews = ArrayList<View>()

    private var deviceName = "Responsive"
    private var cssW = 375
    private var cssH = 667
    private var currentScale = 1f

    private var density = 1f

    @SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        setContentView(R.layout.activity_device_emulator)
        density = resources.displayMetrics.density

        webView = findViewById(R.id.emuWebView)
        stage = findViewById(R.id.emuStage)
        frame = findViewById(R.id.emuFrame)
        deviceLabel = findViewById(R.id.emuDevice)
        widthInput = findViewById(R.id.emuWidth)
        heightInput = findViewById(R.id.emuHeight)
        handleRight = findViewById(R.id.emuHandleRight)
        handleBottom = findViewById(R.id.emuHandleBottom)
        chipRow = findViewById(R.id.emuChips)

        webView.apply {
            webViewClient = WebViewClient()
            webChromeClient = ConsoleLog.chromeClient()
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = true
            settings.allowFileAccessFromFileURLs = true
            settings.cacheMode = WebSettings.LOAD_NO_CACHE
        }

        // Rounded frame with a shadow
        frame.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, 10 * density)
            }
        }
        frame.clipToOutline = true

        // White rounded boxes for the device picker and the size fields
        val box = rounded(0xFFFFFFFF.toInt(), 0xFFB9BFC9.toInt(), 1f, 10f)
        deviceLabel.background = box
        widthInput.background = rounded(0xFFFFFFFF.toInt(), 0xFFB9BFC9.toInt(), 1f, 10f)
        heightInput.background = rounded(0xFFFFFFFF.toInt(), 0xFFB9BFC9.toInt(), 1f, 10f)

        findViewById<View>(R.id.emuClose).setOnClickListener { finish() }
        findViewById<View>(R.id.emuHelp).setOnClickListener { showHelp() }
        findViewById<View>(R.id.emuMore).setOnClickListener { anchor -> showMenu(anchor) }
        findViewById<View>(R.id.emuApply).setOnClickListener { readInputs() }

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

        // Typing a custom size
        val onDone = TextView.OnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                readInputs()
                true
            } else false
        }
        widthInput.setOnEditorActionListener(onDone)
        heightInput.setOnEditorActionListener(onDone)

        buildChips()
        loadBreakpoints()
        setupHandles()

        // Re-fit whenever the stage changes size
        stage.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> applySize() }

        refreshInputs()
        loadPage()
    }

    private fun rounded(fill: Int, strokeColor: Int, strokeDp: Float, radiusDp: Float): GradientDrawable {
        return GradientDrawable().apply {
            setColor(fill)
            cornerRadius = radiusDp * density
            if (strokeDp > 0f) setStroke((strokeDp * density).toInt().coerceAtLeast(1), strokeColor)
        }
    }

    // Size presets along the bottom
    private fun buildChips() {
        chipRow.removeAllViews()
        chipViews.clear()
        presets.forEach { p ->
            val chip = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding((14 * density).toInt(), 0, (14 * density).toInt(), 0)
                background = rounded(0xFFFFFFFF.toInt(), 0xFFD3D7DE.toInt(), 1f, 10f)
                elevation = 2 * density
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, (46 * density).toInt()
                ).apply { marginEnd = (10 * density).toInt() }
            }

            if (p.icon == 3) {
                val badge = TextView(this).apply {
                    text = "4K"
                    textSize = 11f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    setTextColor(0xFF1F2430.toInt())
                    gravity = Gravity.CENTER
                    background = rounded(0x00000000, 0xFF1F2430.toInt(), 1.5f, 4f)
                    setPadding((4 * density).toInt(), 0, (4 * density).toInt(), 0)
                    layoutParams = LinearLayout.LayoutParams(
                        (28 * density).toInt(), (20 * density).toInt()
                    )
                }
                chip.addView(badge)
            } else {
                val icon = ImageView(this).apply {
                    setImageResource(
                        when (p.icon) {
                            0 -> R.drawable.ic_emu_phone
                            1 -> R.drawable.ic_emu_tablet
                            else -> R.drawable.ic_emu_laptop
                        }
                    )
                    layoutParams = LinearLayout.LayoutParams(
                        (24 * density).toInt(), (24 * density).toInt()
                    )
                }
                chip.addView(icon)
            }

            val divider = View(this).apply {
                setBackgroundColor(0xFFC5CAD3.toInt())
                layoutParams = LinearLayout.LayoutParams(
                    (1 * density).toInt().coerceAtLeast(1), (26 * density).toInt()
                ).apply {
                    marginStart = (10 * density).toInt()
                    marginEnd = (10 * density).toInt()
                }
            }
            chip.addView(divider)

            val label = TextView(this).apply {
                text = p.label
                textSize = 15f
                setTextColor(0xFF1F2430.toInt())
            }
            chip.addView(label)

            chip.setOnClickListener {
                deviceName = "Responsive"
                cssW = p.w
                refreshInputs()
                applySize()
            }
            chipRow.addView(chip)
            chipViews.add(chip)
        }
    }

    // Looks for @media widths in your CSS and adds a chip for each one
    private fun loadBreakpoints() {
        Thread {
            val found = java.util.TreeSet<Int>()
            val re = Regex(
                "\\(\\s*(?:min|max)-width\\s*:\\s*(\\d+(?:\\.\\d+)?)\\s*(px|em|rem)\\s*\\)",
                RegexOption.IGNORE_CASE
            )
            fun scan(text: String) {
                re.findAll(text).forEach { m ->
                    val v = m.groupValues[1].toDouble()
                    val px = if (m.groupValues[2].lowercase() == "px") v else v * 16
                    val w = px.toInt()
                    if (w in 200..4000) found.add(w)
                }
            }
            try {
                scan(PreviewHolder.html)
                val root = LocalServer.rootDir
                if (root != null) {
                    var count = 0
                    root.walkTopDown()
                        .maxDepth(4)
                        .onEnter {
                            it == root || (it.name != "node_modules" && !it.name.startsWith("."))
                        }
                        .forEach { f ->
                            if (f.isFile && f.name.endsWith(".css") &&
                                f.length() < 600_000 && count < 30
                            ) {
                                count++
                                scan(f.readText())
                            }
                        }
                }
            } catch (e: Exception) {
                CrashLog.note(e)
            }
            runOnUiThread { if (!isFinishing) addBreakpointChips(found.toList()) }
        }.start()
    }

    private fun addBreakpointChips(list: List<Int>) {
        if (list.isEmpty()) return
        // A thin divider between the size presets and your own breakpoints
        val sep = View(this).apply {
            setBackgroundColor(0xFF8A94A6.toInt())
            layoutParams = LinearLayout.LayoutParams(
                (2 * density).toInt(), (30 * density).toInt()
            ).apply {
                marginStart = (4 * density).toInt()
                marginEnd = (14 * density).toInt()
            }
        }
        chipRow.addView(sep)
        list.forEach { bp ->
            val chip = TextView(this).apply {
                text = "⇔ $bp"
                textSize = 15f
                setTextColor(0xFF1F2430.toInt())
                gravity = Gravity.CENTER
                setPadding((14 * density).toInt(), 0, (14 * density).toInt(), 0)
                background = rounded(0xFFFFFFFF.toInt(), 0xFF2196F3.toInt(), 1.5f, 10f)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, (46 * density).toInt()
                ).apply { marginEnd = (10 * density).toInt() }
                setOnClickListener {
                    deviceName = "Responsive"
                    cssW = if (cssW == bp) bp - 1 else bp
                    refreshInputs()
                    applySize()
                }
            }
            chipRow.addView(chip)
        }
    }

    // The chip that matches the current width gets a dark outline
    private fun updateChips() {
        chipViews.forEachIndexed { i, v ->
            v.background = if (presets[i].w == cssW)
                rounded(0xFFFFFFFF.toInt(), 0xFF1F2430.toInt(), 2f, 10f)
            else
                rounded(0xFFFFFFFF.toInt(), 0xFFD3D7DE.toInt(), 1f, 10f)
        }
    }

    // Drag the bars on the edge of the screen to resize it
    @SuppressLint("ClickableViewAccessibility")
    private fun setupHandles() {
        var startRaw = 0f
        var startSize = 0
        var startScale = 1f

        handleRight.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startRaw = e.rawX
                    startSize = cssW
                    startScale = currentScale
                    v.parent.requestDisallowInterceptTouchEvent(true)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val delta = ((e.rawX - startRaw) / (startScale * density)).toInt()
                    cssW = (startSize + delta).coerceIn(200, 3000)
                    deviceName = "Responsive"
                    refreshInputs()
                    applySize(startScale)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    applySize()
                    true
                }
                else -> false
            }
        }

        handleBottom.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startRaw = e.rawY
                    startSize = cssH
                    startScale = currentScale
                    v.parent.requestDisallowInterceptTouchEvent(true)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val delta = ((e.rawY - startRaw) / (startScale * density)).toInt()
                    cssH = (startSize + delta).coerceIn(200, 3000)
                    deviceName = "Responsive"
                    refreshInputs()
                    applySize(startScale)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    applySize()
                    true
                }
                else -> false
            }
        }
    }

    private fun showMenu(anchor: View) {
        val menu = PopupMenu(this, anchor)
        menu.menu.add(0, 1, 0, "Rotate")
        menu.menu.add(0, 2, 1, "Refresh page")
        menu.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> {
                    val t = cssW
                    cssW = cssH
                    cssH = t
                    refreshInputs()
                    applySize()
                }
                2 -> loadPage()
            }
            true
        }
        menu.show()
    }

    private fun showHelp() {
        AlertDialog.Builder(this)
            .setTitle("Device Emulator")
            .setMessage(
                "See your page at different screen sizes.\n\n" +
                    "• Pick a device from the list, or tap a size at the bottom.\n" +
                    "• Type a width and height, then tap ▶.\n" +
                    "• Drag the bars on the right and bottom edges to resize.\n" +
                    "• Use ⋮ to rotate or refresh."
            )
            .setPositiveButton("OK", null)
            .show()
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

    // Lay the page out at the chosen CSS size, then shrink the frame to fit the screen
    private fun applySize(fixedScale: Float? = null) {
        val sw = stage.width
        val sh = stage.height
        if (sw <= 0 || sh <= 0) return

        val pxW = (cssW * density).toInt()
        val pxH = (cssH * density).toInt()
        val sideMargin = 30 * density
        val topMargin = 20 * density
        val bottomMargin = 44 * density
        val scale = fixedScale ?: minOf(
            1f,
            (sw - 2 * sideMargin) / pxW,
            (sh - topMargin - bottomMargin) / pxH
        )
        currentScale = scale

        val lp = frame.layoutParams as FrameLayout.LayoutParams
        if (lp.width != pxW || lp.height != pxH) {
            lp.width = pxW
            lp.height = pxH
            frame.layoutParams = lp
        }
        frame.pivotX = 0f
        frame.pivotY = 0f
        frame.scaleX = scale
        frame.scaleY = scale
        val tx = (sw - pxW * scale) / 2f
        val ty = topMargin
        frame.translationX = tx
        frame.translationY = ty

        // Handles sit just outside the right and bottom edges
        val frameW = pxW * scale
        val frameH = pxH * scale
        handleRight.translationX = tx + frameW + 2 * density
        handleRight.translationY = ty + frameH / 2f - (36 * density)
        handleBottom.translationX = tx + frameW / 2f - (36 * density)
        handleBottom.translationY = ty + frameH + 2 * density

        updateChips()
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }
}