package com.rams.editor

import android.app.AlertDialog
import android.content.Context
import android.os.Environment
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import java.io.File

object FileManager {

    private var currentFile: File? = null

    fun getCurrentFileName(): String {
        return currentFile?.name ?: "untitled.html"
    }

    fun newFile(context: Context, onComplete: (String, String) -> Unit) {
        val input = EditText(context)
        input.hint = "filename.html"

        val layout = LinearLayout(context)
        layout.setPadding(50, 20, 50, 20)
        layout.addView(input)

        AlertDialog.Builder(context)
            .setTitle("New File")
            .setView(layout)
            .setPositiveButton("Create") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isEmpty()) {
                    Toast.makeText(context, "Enter a filename", Toast.LENGTH_SHORT).show()
                } else {
                    val file = File(getEditorDir(), name)
                    file.createNewFile()
                    currentFile = file
                    onComplete(name, "")
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    fun openFile(context: Context, onComplete: (String, String) -> Unit) {
        val dir = getEditorDir()
        val files = dir.listFiles()

        if (files == null || files.isEmpty()) {
            Toast.makeText(context, "No files found", Toast.LENGTH_SHORT).show()
            return
        }

        val fileNames = files.map { it.name }.toTypedArray()

        AlertDialog.Builder(context)
            .setTitle("Open File")
            .setItems(fileNames) { _, index ->
                val file = files[index]
                currentFile = file
                val content = file.readText()
                onComplete(file.name, content)
            }
            .show()
    }

    fun saveFile(context: Context, content: String) {
        if (currentFile == null) {
            val input = EditText(context)
            input.hint = "filename.html"

            val layout = LinearLayout(context)
            layout.setPadding(50, 20, 50, 20)
            layout.addView(input)

            AlertDialog.Builder(context)
                .setTitle("Save As")
                .setView(layout)
                .setPositiveButton("Save") { _, _ ->
                    val name = input.text.toString().trim()
                    if (name.isNotEmpty()) {
                        val file = File(getEditorDir(), name)
                        file.writeText(content)
                        currentFile = file
                        
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        } else {
            currentFile!!.writeText(content)
        }
    }

    private fun getEditorDir(): File {
        val dir = File(
            Environment.getExternalStorageDirectory(),
            "RamsEditor"
        )
        if (!dir.exists()) dir.mkdirs()
        return dir
    }
}