package com.rams.editor

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
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

    private var floatingPreview: FloatingPreview? = null
    private val autoSaveHandler = Handler(Looper.getMainLooper())
    private var autoSaveRunnable: Runnable? = null
    private var hasUnsavedChanges = false

    private val openTabs = mutableListOf<File>()
    private var currentTabIndex = -1
    private lateinit var fileTreeAdapter: FileTreeAdapter

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

        floatingPreview = FloatingPreview(this)

        // Setup file tree
        fileTreeAdapter = FileTreeAdapter(
            emptyList(),
            onFileClick = { file -> openFileInTab(file) },
            onFolderClick = { node ->
                node.isExpanded = !node.isExpanded
                fileTreeAdapter.updateNodes(ProjectManager.refreshTree())
            },
            onNewFileInFolder = { folder ->
                ProjectManager.createNewFile(this, folder) { file ->
                    openFileInTab(file)
                    fileTreeAdapter.updateNodes(ProjectManager.refreshTree())
                }
            },
            onNewFolderInFolder = { folder ->
                createNewFolder(folder)
            },
            onRename = { file -> renameFile(file) },
            onDelete = { file -> deleteFile(file) }
        )
        fileTreeRecycler.layoutManager = LinearLayoutManager(this)
        fileTreeRecycler.adapter = fileTreeAdapter

        // Hamburger menu
        btnMenu.setOnClickListener {
            if (drawerLayout.isDrawerOpen(Gravity.START)) {
                drawerLayout.closeDrawer(Gravity.START)
            } else {
                drawerLayout.openDrawer(Gravity.START)
            }
        }

        // New file from drawer
        findViewById<TextView>(R.id.btnNewFile).setOnClickListener {
            ProjectManager.createNewFile(this) { file ->
                openFileInTab(file)
                fileTreeAdapter.updateNodes(ProjectManager.refreshTree())
                drawerLayout.closeDrawer(Gravity.START)
            }
        }

        // New project from drawer
        findViewById<TextView>(R.id.btnNewFolder).setOnClickListener {
            ProjectManager.openProject(this) { name, nodes ->
                projectName.text = name.uppercase()
                fileTreeAdapter.updateNodes(nodes)
                drawerLayout.closeDrawer(Gravity.START)
            }
        }

        btnSave.setOnClickListener {
            saveCurrentFile()
        }

        btnRun.setOnClickListener {
            checkOverlayPermission()
        }

        codeEditor.setOnTextChangedListener { code ->
            hasUnsavedChanges = true
            updateTitleWithDot()
            if (floatingPreview?.isVisible() == true) {
                val baseUrl = if (currentTabIndex >= 0 && openTabs.isNotEmpty()) {
                    "file://${openTabs[currentTabIndex].parent}/"
                } else null
                floatingPreview?.update(code, baseUrl)
            }
            scheduleAutoSave(code)
        }

        codeEditor.setCode(getStarterCode())

        // Restore last session silently
        Handler(Looper.getMainLooper()).postDelayed({
            restoreLastSession()
        }, 300)
    }

    private fun restoreLastSession() {
        val prefs = getSharedPreferences("rams_session", Context.MODE_PRIVATE)
        val lastProjectPath = prefs.getString("last_project", null)
        val lastFilePath = prefs.getString("last_file", null)

        if (lastProjectPath != null) {
            val projectDir = File(lastProjectPath)
            if (projectDir.exists()) {
                ProjectManager.setCurrentProject(projectDir)
                projectName.text = projectDir.name.uppercase()
                fileTreeAdapter.updateNodes(ProjectManager.refreshTree())
                if (lastFilePath != null) {
                    val lastFile = File(lastFilePath)
                    if (lastFile.exists()) {
                        openFileInTab(lastFile)
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
        if (currentTabIndex >= 0 && openTabs.isNotEmpty()) {
            editor.putString("last_file", openTabs[currentTabIndex].absolutePath)
        }
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
                    // Update open tabs
                    val tabIndex = openTabs.indexOf(file)
                    if (tabIndex != -1) {
                        openTabs[tabIndex] = newFile
                        tabContainer.getChildAt(tabIndex)
                            ?.findViewById<TextView>(R.id.tabFileName)
                            ?.text = newName
                    }
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
        hasUnsavedChanges = false
        updateTabHighlight()
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

    private fun showPreview() {
        val baseUrl = if (currentTabIndex >= 0 && openTabs.isNotEmpty()) {
            "file://${openTabs[currentTabIndex].parent}/"
        } else null
        floatingPreview?.show(codeEditor.getCode(), baseUrl)
    }

    override fun onPause() {
        super.onPause()
        saveSession()
        floatingPreview?.dismiss()
    }

    override fun onDestroy() {
        autoSaveRunnable?.let { autoSaveHandler.removeCallbacks(it) }
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