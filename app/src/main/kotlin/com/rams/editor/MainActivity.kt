package com.rams.editor

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var codeEditor: CodeEditor
    private lateinit var fileName: TextView
    private lateinit var btnNew: Button
    private lateinit var btnOpen: Button
    private lateinit var btnSave: Button
    private lateinit var btnRun: Button

    private var floatingPreview: FloatingPreview? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        codeEditor = findViewById(R.id.codeEditor)
        fileName = findViewById(R.id.fileName)
        btnNew = findViewById(R.id.btnNew)
        btnOpen = findViewById(R.id.btnOpen)
        btnSave = findViewById(R.id.btnSave)
        btnRun = findViewById(R.id.btnRun)

        floatingPreview = FloatingPreview(this)

        // Set default starter code
        codeEditor.setCode(getStarterCode())

        // Request storage permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.data = Uri.parse("package:$packageName")
                startActivityForResult(intent, 200)
            }
        } else {
            requestPermissions(
                arrayOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE),
                200
            )
        }

        btnNew.setOnClickListener {
            FileManager.newFile(this) { name, content ->
                fileName.text = name
                codeEditor.setCode(content)
            }
        }

        btnOpen.setOnClickListener {
            FileManager.openFile(this) { name, content ->
                fileName.text = name
                codeEditor.setCode(content)
            }
        }

        btnSave.setOnClickListener {
            FileManager.saveFile(this, codeEditor.getCode())
            fileName.text = FileManager.getCurrentFileName()
        }

        btnRun.setOnClickListener {
            checkOverlayPermission()
        }

        codeEditor.setOnTextChangedListener { code ->
            if (floatingPreview?.isVisible() == true) {
                floatingPreview?.update(code)
            }
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
        if (requestCode == 100) {
            showPreview()
        }
    }

    private fun showPreview() {
        floatingPreview?.show(codeEditor.getCode())
    }

    override fun onDestroy() {
        floatingPreview?.dismiss()
        super.onDestroy()
    }

    private fun getStarterCode(): String {
        return """<!DOCTYPE html>
<html>
<head>
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