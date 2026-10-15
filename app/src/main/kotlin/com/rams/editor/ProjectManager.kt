package com.rams.editor

import android.content.Context
import android.os.Environment
import java.io.File

data class FileNode(
    val file: File,
    val depth: Int,
    var isExpanded: Boolean = false
)

object ProjectManager {

    private var currentProjectDir: File? = null
    private var onProjectChangedListener: ((List<FileNode>) -> Unit)? = null

    fun openProject(context: Context, onComplete: (String, List<FileNode>) -> Unit) {
        val rootDir = getRamsEditorDir()
        val dirs = rootDir.listFiles()?.filter { it.isDirectory }

        if (dirs.isNullOrEmpty()) {
            createNewProject(context, onComplete)
            return
        }

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
                    val projectDir = File(getRamsEditorDir(), name)
                    projectDir.mkdirs()
                    // Create default index.html
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

    fun createNewFile(context: Context, onComplete: (File) -> Unit) {
        val dir = currentProjectDir ?: getRamsEditorDir()
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
        val files = dir.listFiles()?.sortedWith(
            compareBy({ !it.isDirectory }, { it.name })
        ) ?: return nodes

        for (file in files) {
            val node = FileNode(file, depth)
            nodes.add(node)
            if (file.isDirectory && node.isExpanded) {
                nodes.addAll(buildFileTree(file, depth + 1))
            }
        }
        return nodes
    }

    fun getCurrentProject(): File? = currentProjectDir

    fun setOnProjectChangedListener(listener: (List<FileNode>) -> Unit) {
        onProjectChangedListener = listener
    }

    fun refreshTree(): List<FileNode> {
        return currentProjectDir?.let { buildFileTree(it) } ?: emptyList()
    }

    private fun getRamsEditorDir(): File {
        val dir = File(
            Environment.getExternalStorageDirectory(),
            "RamsEditor"
        )
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