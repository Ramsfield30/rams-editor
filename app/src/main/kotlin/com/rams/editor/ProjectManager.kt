package com.rams.editor

import android.content.Context
import java.io.File

data class FileNode(
    val file: File,
    val depth: Int,
    var isExpanded: Boolean = false
)

object ProjectManager {

    private var currentProjectDir: File? = null
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun openProject(context: Context, onComplete: (String, List<FileNode>) -> Unit) {
        init(context)
        val rootDir = getRamsEditorDir(context)
        val dirs = rootDir.listFiles()?.filter { it.isDirectory } ?: emptyList()

        val labels = ArrayList<String>()
        if (dirs.isNotEmpty()) labels.add("Open Existing")
        labels.add("Create New")
        labels.add("Clone from Git")

        android.app.AlertDialog.Builder(context)
            .setTitle("Projects")
            .setItems(labels.toTypedArray()) { _, which ->
                when (labels[which]) {
                    "Open Existing" -> {
                        val names = dirs.map { it.name }.toTypedArray()
                        android.app.AlertDialog.Builder(context)
                            .setTitle("Open Project")
                            .setItems(names) { _, index ->
                                currentProjectDir = dirs[index]
                                val nodes = buildFileTree(dirs[index])
                                onComplete(dirs[index].name, nodes)
                            }
                            .setNegativeButton("Cancel", null)
                            .show()
                    }
                    "Create New" -> createNewProject(context, onComplete)
                    "Clone from Git" -> cloneProject(context, onComplete)
                }
            }
            .show()
    }

    fun cloneProject(context: Context, onComplete: (String, List<FileNode>) -> Unit) {
        init(context)
        GitManager.init(context)
        val prefs = context.getSharedPreferences("git", Context.MODE_PRIVATE)

        val urlInput = android.widget.EditText(context)
        urlInput.hint = "https://github.com/you/repo.git"
        urlInput.setSingleLine()

        val tokenInput = android.widget.EditText(context)
        tokenInput.hint = "Token (only for private repos)"
        tokenInput.setSingleLine()
        tokenInput.inputType = android.text.InputType.TYPE_CLASS_TEXT or
            android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        tokenInput.setText(prefs.getString("token", "") ?: "")

        val layout = android.widget.LinearLayout(context)
        layout.orientation = android.widget.LinearLayout.VERTICAL
        layout.setPadding(50, 20, 50, 20)
        layout.addView(urlInput)
        layout.addView(tokenInput)

        android.app.AlertDialog.Builder(context)
            .setTitle("Clone from Git")
            .setView(layout)
            .setPositiveButton("Clone") { _, _ ->
                var url = urlInput.text.toString().trim()
                val token = tokenInput.text.toString().trim()
                if (url.isEmpty()) return@setPositiveButton
                if (!url.contains("://")) url = "https://$url"
                val base = url.trimEnd('/').substringAfterLast('/')
                    .removeSuffix(".git")
                    .ifBlank { "project" }
                val root = getRamsEditorDir(context)
                var dest = File(root, base)
                var n = 2
                while (dest.exists()) {
                    dest = File(root, "$base-$n")
                    n++
                }
                runClone(context, url, token, dest, prefs, onComplete)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun runClone(
        context: Context,
        url: String,
        token: String,
        dest: File,
        prefs: android.content.SharedPreferences,
        onComplete: (String, List<FileNode>) -> Unit
    ) {
        val progress = android.app.AlertDialog.Builder(context)
            .setTitle("Cloning…")
            .setMessage(url)
            .setCancelable(false)
            .show()
        val main = android.os.Handler(android.os.Looper.getMainLooper())
        Thread {
            var error: String? = null
            try {
                GitManager.clone(url, token, dest)
            } catch (e: Exception) {
                error = e.message ?: e.javaClass.simpleName
                dest.deleteRecursively()
            }
            val err = error
            main.post {
                progress.dismiss()
                if (err != null) {
                    android.app.AlertDialog.Builder(context)
                        .setTitle("Clone failed")
                        .setMessage(err)
                        .setPositiveButton("OK", null)
                        .show()
                } else {
                    // Remember the token if none was saved yet
                    if (token.isNotEmpty() && prefs.getString("token", "").isNullOrEmpty()) {
                        prefs.edit().putString("token", token).apply()
                    }
                    currentProjectDir = dest
                    onComplete(dest.name, buildFileTree(dest))
                }
            }
        }.start()
    }
    
    fun setCurrentProject(dir: File) {
    currentProjectDir = dir
}

    fun createNewProject(context: Context, onComplete: (String, List<FileNode>) -> Unit) {
        val input = android.widget.EditText(context)
        input.hint = "Project name"

        val layout = android.widget.LinearLayout(context)
        layout.setPadding(50, 20, 50, 20)
        layout.addView(input)

        android.app.AlertDialog.Builder(context)
            .setTitle("New Project")
            .setView(layout)
            .setPositiveButton("Create") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    val projectDir = File(getRamsEditorDir(context), name)
                    projectDir.mkdirs()
                    val indexFile = File(projectDir, "index.html")
                    indexFile.writeText(getDefaultHtml())
                    currentProjectDir = projectDir
                    val nodes = buildFileTree(projectDir)
                    onComplete(name, nodes)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

   fun createNewFile(context: Context, parentDir: File? = null, onComplete: (File) -> Unit) {
    val dir = parentDir ?: currentProjectDir ?: getRamsEditorDir(context)
        val input = android.widget.EditText(context)
        input.hint = "filename.html"

        val layout = android.widget.LinearLayout(context)
        layout.setPadding(50, 20, 50, 20)
        layout.addView(input)

        android.app.AlertDialog.Builder(context)
            .setTitle("New File")
            .setView(layout)
            .setPositiveButton("Create") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    val file = File(dir, name)
                    file.createNewFile()
                    onComplete(file)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

   fun buildFileTree(dir: File, depth: Int = 0): List<FileNode> {
    val nodes = mutableListOf<FileNode>()
    val files = dir.listFiles()?.filter { it.name != ".git" }?.sortedWith(
        compareBy({ !it.isDirectory }, { it.name })
    ) ?: return nodes

    for (file in files) {
        // Auto expand first level
        val node = FileNode(file, depth, isExpanded = depth == 0 && file.isDirectory)
        nodes.add(node)
        if (file.isDirectory && node.isExpanded) {
            nodes.addAll(buildFileTree(file, depth + 1))
        }
    }
    return nodes
}

    fun getCurrentProject(): File? = currentProjectDir

    fun refreshTree(): List<FileNode> {
        return currentProjectDir?.let { buildFileTree(it) } ?: emptyList()
    }

    // App-scoped storage — no permission needed
    // Path: /sdcard/Android/data/com.rams.editor/files/RamsEditor/
    fun getRamsEditorDir(context: Context): File {
        val dir = File(context.getExternalFilesDir(null), "RamsEditor")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun getDefaultHtml(): String {
        return """<!DOCTYPE html>
<html>
<head>
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>My Project</title>
    <style>
        body {
            font-family: sans-serif;
            padding: 16px;
        }
    </style>
</head>
<body>
    <h1>Hello World</h1>
</body>
</html>"""
    }
}