package com.rams.editor

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.webkit.ConsoleMessage
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var codeEditor: CodeEditor
    private lateinit var fileName: TextView
    private lateinit var btnSave: Button
    private lateinit var btnRun: Button
    private lateinit var btnMenu: TextView
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var tabContainer: LinearLayout
    private lateinit var fileTreeRecycler: RecyclerView
    private lateinit var projectName: TextView

    // Added exactly as requested
    private lateinit var snippetRow1: LinearLayout
    private lateinit var snippetRow2: LinearLayout
    private lateinit var snippetBar: SnippetBar
    private lateinit var btnSettings: TextView

    private var floatingPreview: FloatingPreview? = null
    private val autoSaveHandler = Handler(Looper.getMainLooper())
    private var autoSaveRunnable: Runnable? = null
    private var hasUnsavedChanges = false

    // Waits a moment after typing stops before updating the live preview
    private val previewHandler = Handler(Looper.getMainLooper())
    private var previewRunnable: Runnable? = null

    private val openTabs = mutableListOf<File>()
    private var currentTabIndex = -1
    private lateinit var fileTreeAdapter: FileTreeAdapter

    // Track last active HTML file for CSS/JS preview
    private var lastActiveHtmlFile: File? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        codeEditor = findViewById(R.id.codeEditor)
        fileName = findViewById(R.id.fileName)
        btnSave = findViewById(R.id.btnSave)
        btnRun = findViewById(R.id.btnRun)
        btnMenu = findViewById(R.id.btnMenu)
        drawerLayout = findViewById(R.id.drawerLayout)
        tabContainer = findViewById(R.id.tabContainer)
        fileTreeRecycler = findViewById(R.id.fileTreeRecycler)
        projectName = findViewById(R.id.projectName)

        // Added exactly as requested
        btnSettings = findViewById(R.id.btnSettings)

        floatingPreview = FloatingPreview(this)

        setupConsole()

        // Added exactly as requested
        // Setup snippet bar
        snippetRow1 = findViewById(R.id.snippetRow1)
        snippetRow2 = findViewById(R.id.snippetRow2)
        snippetBar = SnippetBar(
            this,
            snippetRow1,
            snippetRow2
        ) { code, cursorOffset ->
            val start = codeEditor.selectionStart.coerceAtLeast(0)
            codeEditor.text?.insert(start, code)
            codeEditor.setSelection((start + cursorOffset).coerceAtMost(
                codeEditor.text?.length ?: 0
            ))
        }

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            if (checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(
                    arrayOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    200
                )
            }
        }

        fileTreeAdapter = FileTreeAdapter(
            emptyList(),
            onFileClick = { file -> openFileInTab(file) },
            onFolderClick = { node ->
                node.isExpanded = !node.isExpanded
                fileTreeAdapter.updateNodes(ProjectManager.refreshTree())
            },
            onNewFileInFolder = { folder: File ->
                ProjectManager.createNewFile(this, folder) { file ->
                    openFileInTab(file)
                    fileTreeAdapter.updateNodes(ProjectManager.refreshTree())
                }
            },
            onNewFolderInFolder = { folder -> createNewFolder(folder) },
            onRename = { file -> renameFile(file) },
            onDelete = { file -> deleteFile(file) }
        )
        fileTreeRecycler.layoutManager = LinearLayoutManager(this)
        fileTreeRecycler.adapter = fileTreeAdapter

        btnMenu.setOnClickListener {
            if (drawerLayout.isDrawerOpen(Gravity.START)) {
                drawerLayout.closeDrawer(Gravity.START)
            } else {
                drawerLayout.openDrawer(Gravity.START)
            }
        }

        findViewById<TextView>(R.id.btnNewFile).setOnClickListener {
            ProjectManager.createNewFile(this) { file ->
                openFileInTab(file)
                fileTreeAdapter.updateNodes(ProjectManager.refreshTree())
                drawerLayout.closeDrawer(Gravity.START)
            }
        }

        findViewById<TextView>(R.id.btnNewFolder).setOnClickListener {
            ProjectManager.createNewProject(this) { name, nodes ->
                projectName.text = name.uppercase()
                fileTreeAdapter.updateNodes(nodes)
                openTabs.clear()
                tabContainer.removeAllViews()
                currentTabIndex = -1
                lastActiveHtmlFile = null
                fileName.text = "untitled.html"
                codeEditor.setCode(getStarterCode())
                drawerLayout.closeDrawer(Gravity.START)
            }
        }

        findViewById<TextView>(R.id.btnOpenProject).setOnClickListener {
            ProjectManager.openProject(this) { name, nodes ->
                projectName.text = name.uppercase()
                fileTreeAdapter.updateNodes(nodes)
                openTabs.clear()
                tabContainer.removeAllViews()
                currentTabIndex = -1
                lastActiveHtmlFile = null
                fileName.text = "untitled.html"
                codeEditor.setCode(getStarterCode())
                drawerLayout.closeDrawer(Gravity.START)
            }
        }

        btnSave.setOnClickListener { saveCurrentFile() }
        btnRun.setOnClickListener { runPreview() }

        // Run menu (▾) — only visible when Settings → Run Button = "Show menu"
        findViewById<TextView>(R.id.btnRunMenu).setOnClickListener { anchor ->
            val menu = android.widget.PopupMenu(this, anchor)
            menu.menu.add(0, 1, 0, "Live Preview")
            menu.menu.add(0, 2, 1, "Full Screen Preview")
            menu.menu.add(0, 3, 2, "Device Emulator")
            menu.menu.add(0, 4, 3, "Open in Browser")
            menu.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    1 -> checkOverlayPermission()
                    2 -> openFullScreenPreview()
                    else -> Toast.makeText(
                        this, "${item.title}: coming soon", Toast.LENGTH_SHORT
                    ).show()
                }
                true
            }
            menu.show()
        }

        // Added exactly as requested
        btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        codeEditor.setOnTextChangedListener { code ->
            hasUnsavedChanges = true
            updateTitleWithDot()
            if (floatingPreview?.isVisible() == true) {
                schedulePreviewUpdate(code)
            }
            scheduleAutoSave(code)
        }

        codeEditor.setCode(getStarterCode())

        Handler(Looper.getMainLooper()).postDelayed({
            restoreLastSession()
        }, 300)
    }

    private fun schedulePreviewUpdate(code: String) {
        previewRunnable?.let { previewHandler.removeCallbacks(it) }
        val tabAtSchedule = currentTabIndex
        previewRunnable = Runnable {
            if (tabAtSchedule == currentTabIndex) updatePreview(code)
        }
        previewHandler.postDelayed(previewRunnable!!, 200)
    }

    private fun updatePreview(code: String) {
        val currentFile = if (currentTabIndex >= 0 && openTabs.isNotEmpty())
            openTabs[currentTabIndex] else null
        val name = currentFile?.name ?: ""

        when {
            name.endsWith(".css") -> floatingPreview?.updateCss(code, name)
            name.endsWith(".js") -> floatingPreview?.updateJs(code)
            else -> floatingPreview?.update(inlineLinkedFiles(code), getBaseUrl())
        }
    }

    private fun getBaseUrl(): String? {
        // Use last active HTML file's directory as base
        val baseFile = lastActiveHtmlFile
            ?: if (currentTabIndex >= 0 && openTabs.isNotEmpty())
                openTabs[currentTabIndex] else null
        return baseFile?.parent?.let { "file://$it/" }
    }

    private fun getPreviewContent(): String {
        val currentFile = if (currentTabIndex >= 0 && openTabs.isNotEmpty())
            openTabs[currentTabIndex] else null

        var html = codeEditor.getCode()

        // For CSS or JS — show the last active HTML file
        if (currentFile != null &&
            (currentFile.name.endsWith(".css") || currentFile.name.endsWith(".js"))
        ) {
            val htmlFile = lastActiveHtmlFile
                ?: currentFile.parentFile?.listFiles()
                    ?.firstOrNull { it.name.endsWith(".html") }
            if (htmlFile != null && htmlFile.exists()) {
                html = htmlFile.readText()
            }
        }
        return inlineLinkedFiles(html)
    }

    // Puts linked stylesheets straight into the page, using what is in the editor
    // for the open file (even unsaved) and the saved file for the others.
    private fun inlineLinkedFiles(html: String): String {
        val baseDir = (lastActiveHtmlFile
            ?: if (currentTabIndex >= 0 && openTabs.isNotEmpty())
                openTabs[currentTabIndex] else null
            )?.parentFile ?: return html

        val linkRegex = Regex("<link\\b[^>]*>", RegexOption.IGNORE_CASE)
        val relRegex = Regex("rel\\s*=\\s*[\"']?stylesheet", RegexOption.IGNORE_CASE)
        val hrefRegex = Regex("href\\s*=\\s*[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)

        return linkRegex.replace(html) { m ->
            val tag = m.value
            val href = hrefRegex.find(tag)?.groupValues?.get(1)
            if (href == null || !relRegex.containsMatchIn(tag) ||
                href.contains("://") || href.startsWith("//") || href.startsWith("data:")
            ) {
                tag
            } else {
                try {
                    val file = File(baseDir, href.substringBefore('?').substringBefore('#'))
                        .canonicalFile
                    if (file.parentFile?.canonicalFile != baseDir.canonicalFile) {
                        tag
                    } else {
                        val current = if (currentTabIndex >= 0 && openTabs.isNotEmpty())
                            openTabs[currentTabIndex] else null
                        val css = when {
                            current != null && current.canonicalFile == file ->
                                codeEditor.getCode()
                            file.exists() -> file.readText()
                            else -> null
                        }
                        if (css == null) tag else "<style data-src=\"$href\">\n$css\n</style>"
                    }
                } catch (e: Exception) {
                    tag
                }
            }
        }
    }

    private fun restoreLastSession() {
        val prefs = getSharedPreferences("rams_session", Context.MODE_PRIVATE)
        val lastProjectPath = prefs.getString("last_project", null)
        val tabPaths = prefs.getString("open_tabs", null)
        val activeTab = prefs.getInt("active_tab", 0)

        if (lastProjectPath != null) {
            val projectDir = File(lastProjectPath)
            if (projectDir.exists()) {
                ProjectManager.setCurrentProject(projectDir)
                projectName.text = projectDir.name.uppercase()
                fileTreeAdapter.updateNodes(ProjectManager.refreshTree())

                // Restore all tabs
                if (tabPaths != null) {
                    tabPaths.split("|").forEach { path ->
                        val file = File(path)
                        if (file.exists()) openFileInTab(file)
                    }
                    // Switch to last active tab
                    if (activeTab >= 0 && activeTab < openTabs.size) {
                        switchToTab(activeTab)
                    }
                }
            }
        }
    }

    private fun saveSession() {
        val prefs = getSharedPreferences("rams_session", Context.MODE_PRIVATE)
        val editor = prefs.edit()
        ProjectManager.getCurrentProject()?.let {
            editor.putString("last_project", it.absolutePath)
        }
        // Save all open tab paths
        val tabPaths = openTabs.joinToString("|") { it.absolutePath }
        editor.putString("open_tabs", tabPaths)
        editor.putInt("active_tab", currentTabIndex)
        editor.apply()
    }

    private fun createNewFolder(parentDir: File) {
        val input = android.widget.EditText(this)
        input.hint = "Folder name"
        val layout = LinearLayout(this)
        layout.setPadding(50, 20, 50, 20)
        layout.addView(input)
        android.app.AlertDialog.Builder(this)
            .setTitle("New Folder")
            .setView(layout)
            .setPositiveButton("Create") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    File(parentDir, name).mkdirs()
                    fileTreeAdapter.updateNodes(ProjectManager.refreshTree())
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun renameFile(file: File) {
        val input = android.widget.EditText(this)
        input.setText(file.name)
        val layout = LinearLayout(this)
        layout.setPadding(50, 20, 50, 20)
        layout.addView(input)
        android.app.AlertDialog.Builder(this)
            .setTitle("Rename")
            .setView(layout)
            .setPositiveButton("Rename") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty()) {
                    val newFile = File(file.parent, newName)
                    file.renameTo(newFile)
                    val tabIndex = openTabs.indexOf(file)
                    if (tabIndex != -1) {
                        openTabs[tabIndex] = newFile
                        tabContainer.getChildAt(tabIndex)
                            ?.findViewById<TextView>(R.id.tabFileName)
                            ?.text = newName
                    }
                    if (lastActiveHtmlFile == file) lastActiveHtmlFile = newFile
                    fileTreeAdapter.updateNodes(ProjectManager.refreshTree())
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteFile(file: File) {
        android.app.AlertDialog.Builder(this)
            .setTitle("Delete ${file.name}?")
            .setMessage("This cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                file.deleteRecursively()
                val tabIndex = openTabs.indexOf(file)
                if (tabIndex != -1) closeTab(tabIndex)
                if (lastActiveHtmlFile == file) lastActiveHtmlFile = null
                fileTreeAdapter.updateNodes(ProjectManager.refreshTree())
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun openFileInTab(file: File) {
        val existingIndex = openTabs.indexOf(file)
        if (existingIndex != -1) {
            switchToTab(existingIndex)
            return
        }
        if (currentTabIndex >= 0 && openTabs.isNotEmpty()) {
            openTabs[currentTabIndex].writeText(codeEditor.getCode())
        }
        openTabs.add(file)
        currentTabIndex = openTabs.size - 1
        addTabView(file, currentTabIndex)
        codeEditor.setCode(file.readText())
        fileName.text = file.name

        // Added exactly as requested
        // Update snippet bar mode based on file type
        val ext = file.extension.lowercase()
        snippetBar.setMode(ext)

        // Track last active HTML file
        if (file.name.endsWith(".html")) {
            lastActiveHtmlFile = file
        }

        hasUnsavedChanges = false
        drawerLayout.closeDrawer(Gravity.START)
    }

    private fun addTabView(file: File, index: Int) {
        val tabView = LayoutInflater.from(this)
            .inflate(R.layout.tab_item, tabContainer, false)
        tabView.findViewById<TextView>(R.id.tabFileName).text = file.name
        tabView.setOnClickListener { switchToTab(index) }
        tabView.findViewById<TextView>(R.id.tabClose).setOnClickListener {
            closeTab(index)
        }
        tabContainer.addView(tabView)
        updateTabHighlight()
    }

    private fun switchToTab(index: Int) {
        if (index < 0 || index >= openTabs.size) return
        if (currentTabIndex >= 0 && openTabs.isNotEmpty()) {
            openTabs[currentTabIndex].writeText(codeEditor.getCode())
        }
        currentTabIndex = index
        val file = openTabs[index]
        codeEditor.setCode(file.readText())
        fileName.text = file.name

        // Added exactly as requested
        val ext = openTabs[index].extension.lowercase()
        snippetBar.setMode(ext)

        // Track last active HTML file
        if (file.name.endsWith(".html")) {
            lastActiveHtmlFile = file
        }

        hasUnsavedChanges = false
        updateTabHighlight()

        // Update preview when switching tabs
        if (floatingPreview?.isVisible() == true) {
            floatingPreview?.show(getPreviewContent(), getBaseUrl())
        }
    }

    private fun closeTab(index: Int) {
        if (index < 0 || index >= openTabs.size) return
        openTabs.removeAt(index)
        tabContainer.removeViewAt(index)
        if (openTabs.isEmpty()) {
            currentTabIndex = -1
            fileName.text = "untitled.html"
            codeEditor.setCode(getStarterCode())
        } else {
            val newIndex = if (index >= openTabs.size) openTabs.size - 1 else index
            switchToTab(newIndex)
        }
        updateTabHighlight()
    }

    private fun updateTabHighlight() {
        for (i in 0 until tabContainer.childCount) {
            val tab = tabContainer.getChildAt(i)
            if (i == currentTabIndex) {
                tab.setBackgroundColor(
                    android.graphics.Color.parseColor("#1E1E1E")
                )
                tab.findViewById<TextView>(R.id.tabFileName)
                    .setTextColor(android.graphics.Color.WHITE)
            } else {
                tab.setBackgroundColor(
                    android.graphics.Color.parseColor("#2D2D2D")
                )
                tab.findViewById<TextView>(R.id.tabFileName)
                    .setTextColor(android.graphics.Color.parseColor("#888888"))
            }
        }
    }

    private fun updateTitleWithDot() {
        val name = if (currentTabIndex >= 0 && openTabs.isNotEmpty())
            openTabs[currentTabIndex].name else "untitled.html"
        if (!fileName.text.toString().startsWith("●")) {
            fileName.text = "● $name"
        }
    }

    private fun scheduleAutoSave(code: String) {
        autoSaveRunnable?.let { autoSaveHandler.removeCallbacks(it) }
        autoSaveRunnable = Runnable {
            if (hasUnsavedChanges && currentTabIndex >= 0 && openTabs.isNotEmpty()) {
                openTabs[currentTabIndex].writeText(code)
                hasUnsavedChanges = false
                fileName.text = openTabs[currentTabIndex].name
            }
        }
        autoSaveHandler.postDelayed(autoSaveRunnable!!, 2000)
    }

    private fun saveCurrentFile() {
        if (currentTabIndex >= 0 && openTabs.isNotEmpty()) {
            openTabs[currentTabIndex].writeText(codeEditor.getCode())
            fileName.text = openTabs[currentTabIndex].name
            hasUnsavedChanges = false
        } else {
            FileManager.saveFile(this, codeEditor.getCode())
            fileName.text = FileManager.getCurrentFileName()
            hasUnsavedChanges = false
        }
    }

    private fun checkOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(
                    this,
                    "Please allow overlay permission for preview",
                    Toast.LENGTH_LONG
                ).show()
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivityForResult(intent, 100)
            } else {
                showPreview()
            }
        } else {
            showPreview()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 100) showPreview()
    }

    private fun setupConsole() {
        val panel = findViewById<android.widget.LinearLayout>(R.id.consolePanel)
        val text = findViewById<TextView>(R.id.consoleText)
        val scroll = findViewById<android.widget.ScrollView>(R.id.consoleScroll)

        fun render() {
            val sb = android.text.SpannableStringBuilder()
            for (e in ConsoleLog.entries) {
                val (prefix, color) = when (e.level) {
                    ConsoleMessage.MessageLevel.ERROR -> "✖ " to 0xFFF48771.toInt()
                    ConsoleMessage.MessageLevel.WARNING -> "⚠ " to 0xFFCCA700.toInt()
                    else -> "" to 0xFFD4D4D4.toInt()
                }
                val start = sb.length
                sb.append(prefix).append(e.text).append("\n")
                sb.setSpan(ForegroundColorSpan(color), start, sb.length, 0)
            }
            text.text = sb
            scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
        }

        ConsoleLog.onChange = { runOnUiThread { render() } }

        findViewById<TextView>(R.id.btnConsole).setOnClickListener {
            if (panel.visibility == View.VISIBLE) {
                panel.visibility = View.GONE
            } else {
                panel.visibility = View.VISIBLE
                render()
            }
        }
        findViewById<TextView>(R.id.consoleClear).setOnClickListener { ConsoleLog.clear() }
        findViewById<TextView>(R.id.consoleClose).setOnClickListener {
            panel.visibility = View.GONE
        }
    }

    private fun runPreview() {
        val action = getSharedPreferences("rams_settings", Context.MODE_PRIVATE)
            .getString("run_action", "fullscreen")
        when (action) {
            "live" -> checkOverlayPermission()
            "emulator", "browser" -> {
                Toast.makeText(
                    this, "Coming soon, opening full screen", Toast.LENGTH_SHORT
                ).show()
                openFullScreenPreview()
            }
            else -> openFullScreenPreview() // "fullscreen" and "menu"
        }
    }

    private fun openFullScreenPreview() {
        // Write pending edits first so the saved files are up to date
        if (hasUnsavedChanges) saveCurrentFile()
        ConsoleLog.clear()
        PreviewHolder.html = getPreviewContent()
        PreviewHolder.baseUrl = getBaseUrl()
        PreviewHolder.fileName = lastActiveHtmlFile?.name ?: "untitled.html"
        startActivity(Intent(this, FullScreenPreviewActivity::class.java))
    }

    private fun showPreview() {
        if (hasUnsavedChanges) saveCurrentFile()
        floatingPreview?.show(getPreviewContent(), getBaseUrl())
    }

    private fun applySettings() {
        val prefs = getSharedPreferences("rams_settings", Context.MODE_PRIVATE)

        codeEditor.setFontSizeSp(prefs.getInt("font_size", 14).coerceIn(10, 24))
        codeEditor.setWordWrap(prefs.getBoolean("word_wrap", false))
        codeEditor.showLineNumbers = prefs.getBoolean("line_numbers", true)
        codeEditor.autoCloseEnabled = prefs.getBoolean("auto_close", true)

        snippetBar.tabSize = prefs.getInt("tab_size", 4)
        val snippetVisibility =
            if (prefs.getBoolean("snippet_bar", true)) View.VISIBLE else View.GONE
        (snippetRow1.parent as View).visibility = snippetVisibility
        (snippetRow2.parent as View).visibility = snippetVisibility

        floatingPreview?.setSizePercent(prefs.getInt("preview_size", 50).coerceIn(25, 75))

        // ▾ menu next to ▶ only appears when chosen in Settings
        val runAction = prefs.getString("run_action", "fullscreen")
        findViewById<TextView>(R.id.btnRunMenu).visibility =
            if (runAction == "menu") View.VISIBLE else View.GONE
    }

    override fun onResume() {
        super.onResume()
        applySettings()
    }

    override fun onPause() {
        super.onPause()
        if (hasUnsavedChanges) saveCurrentFile()
        saveSession()
        floatingPreview?.dismiss()
    }

    override fun onDestroy() {
        previewRunnable?.let { previewHandler.removeCallbacks(it) }
        autoSaveRunnable?.let { autoSaveHandler.removeCallbacks(it) }
        ConsoleLog.onChange = null
        floatingPreview?.dismiss()
        super.onDestroy()
    }

    private fun getStarterCode(): String {
        return """<!DOCTYPE html>
<html>
<head>
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <style>
        body {
            background: #1e1e1e;
            color: white;
            font-family: sans-serif;
            display: flex;
            justify-content: center;
            align-items: center;
            height: 100vh;
            margin: 0;
        }
        h1 { color: #4CAF50; }
    </style>
</head>
<body>
    <h1>Welcome to Rams Editor!</h1>
</body>
</html>"""
    }
}