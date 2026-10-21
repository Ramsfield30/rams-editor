package com.rams.editor

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.EditText
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        supportActionBar?.hide()

        val prefs = getSharedPreferences("rams_settings", Context.MODE_PRIVATE)

        // Back button
        findViewById<TextView>(R.id.btnBack).setOnClickListener {
            finish()
        }

        // Font size
        val fontSizeSlider = findViewById<SeekBar>(R.id.fontSizeSlider)
        val fontSizeValue = findViewById<TextView>(R.id.fontSizeValue)
        val savedFontSize = prefs.getInt("font_size", 14)
        fontSizeSlider.progress = savedFontSize
        fontSizeValue.text = "${savedFontSize}px"
        fontSizeSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                fontSizeValue.text = "${progress}px"
                prefs.edit().putInt("font_size", progress).apply()
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        // Tab size
        val tabSize2 = findViewById<TextView>(R.id.tabSize2)
        val tabSize4 = findViewById<TextView>(R.id.tabSize4)
        val savedTabSize = prefs.getInt("tab_size", 4)
        updateTabSizeUI(tabSize2, tabSize4, savedTabSize)
        tabSize2.setOnClickListener {
            prefs.edit().putInt("tab_size", 2).apply()
            updateTabSizeUI(tabSize2, tabSize4, 2)
        }
        tabSize4.setOnClickListener {
            prefs.edit().putInt("tab_size", 4).apply()
            updateTabSizeUI(tabSize2, tabSize4, 4)
        }

        // Word wrap
        val wordWrapSwitch = findViewById<Switch>(R.id.wordWrapSwitch)
        wordWrapSwitch.isChecked = prefs.getBoolean("word_wrap", false)
        wordWrapSwitch.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean("word_wrap", checked).apply()
        }

        // Line numbers
        val lineNumbersSwitch = findViewById<Switch>(R.id.lineNumbersSwitch)
        lineNumbersSwitch.isChecked = prefs.getBoolean("line_numbers", true)
        lineNumbersSwitch.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean("line_numbers", checked).apply()
        }

        // Auto save
        val autoSaveSwitch = findViewById<Switch>(R.id.autoSaveSwitch)
        autoSaveSwitch.isChecked = prefs.getBoolean("auto_save", true)
        autoSaveSwitch.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean("auto_save", checked).apply()
        }

        // Auto close tags
        val autoCloseSwitch = findViewById<Switch>(R.id.autoCloseSwitch)
        autoCloseSwitch.isChecked = prefs.getBoolean("auto_close", true)
        autoCloseSwitch.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean("auto_close", checked).apply()
        }

        // Theme
        val themeDark = findViewById<TextView>(R.id.themeDark)
        val themeLight = findViewById<TextView>(R.id.themeLight)
        val savedTheme = prefs.getString("theme", "dark")
        updateThemeUI(themeDark, themeLight, savedTheme ?: "dark")
        themeDark.setOnClickListener {
            prefs.edit().putString("theme", "dark").apply()
            updateThemeUI(themeDark, themeLight, "dark")
        }
        themeLight.setOnClickListener {
            prefs.edit().putString("theme", "light").apply()
            updateThemeUI(themeDark, themeLight, "light")
        }

        // Preview size
        val previewSizeSlider = findViewById<SeekBar>(R.id.previewSizeSlider)
        val previewSizeValue = findViewById<TextView>(R.id.previewSizeValue)
        val savedPreviewSize = prefs.getInt("preview_size", 50)
        previewSizeSlider.progress = savedPreviewSize
        previewSizeValue.text = "${savedPreviewSize}%"
        previewSizeSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                previewSizeValue.text = "${progress}%"
                prefs.edit().putInt("preview_size", progress).apply()
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        // Snippet bar
        val snippetBarSwitch = findViewById<Switch>(R.id.snippetBarSwitch)
        snippetBarSwitch.isChecked = prefs.getBoolean("snippet_bar", true)
        snippetBarSwitch.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean("snippet_bar", checked).apply()
        }

        // Git credentials
        val gitUsername = findViewById<EditText>(R.id.gitUsername)
        val gitEmail = findViewById<EditText>(R.id.gitEmail)
        val gitToken = findViewById<EditText>(R.id.gitToken)
        gitUsername.setText(prefs.getString("git_username", ""))
        gitEmail.setText(prefs.getString("git_email", ""))
        gitToken.setText(prefs.getString("git_token", ""))

        // Save git on focus lost
        gitUsername.setOnFocusChangeListener { _, focused ->
            if (!focused) prefs.edit()
                .putString("git_username", gitUsername.text.toString()).apply()
        }
        gitEmail.setOnFocusChangeListener { _, focused ->
            if (!focused) prefs.edit()
                .putString("git_email", gitEmail.text.toString()).apply()
        }
        gitToken.setOnFocusChangeListener { _, focused ->
            if (!focused) prefs.edit()
                .putString("git_token", gitToken.text.toString()).apply()
        }

        // GitHub link
        findViewById<TextView>(R.id.btnGithub).setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW,
                Uri.parse("https://github.com/Ramsfield30/rams-editor")))
        }

        // Share
        findViewById<TextView>(R.id.btnShare).setOnClickListener {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT,
                    "Check out Rams Editor — a powerful code editor for Android!\n" +
                    "github.com/Ramsfield30/rams-editor")
            }
            startActivity(Intent.createChooser(intent, "Share Rams Editor"))
        }
    }

    private fun updateTabSizeUI(btn2: TextView, btn4: TextView, size: Int) {
        if (size == 2) {
            btn2.setBackgroundColor(android.graphics.Color.parseColor("#2196F3"))
            btn2.setTextColor(android.graphics.Color.WHITE)
            btn4.setBackgroundColor(android.graphics.Color.parseColor("#2D2D2D"))
            btn4.setTextColor(android.graphics.Color.parseColor("#888888"))
        } else {
            btn4.setBackgroundColor(android.graphics.Color.parseColor("#2196F3"))
            btn4.setTextColor(android.graphics.Color.WHITE)
            btn2.setBackgroundColor(android.graphics.Color.parseColor("#2D2D2D"))
            btn2.setTextColor(android.graphics.Color.parseColor("#888888"))
        }
    }

    private fun updateThemeUI(dark: TextView, light: TextView, theme: String) {
        if (theme == "dark") {
            dark.setBackgroundColor(android.graphics.Color.parseColor("#2196F3"))
            dark.setTextColor(android.graphics.Color.WHITE)
            light.setBackgroundColor(android.graphics.Color.parseColor("#2D2D2D"))
            light.setTextColor(android.graphics.Color.parseColor("#888888"))
        } else {
            light.setBackgroundColor(android.graphics.Color.parseColor("#2196F3"))
            light.setTextColor(android.graphics.Color.WHITE)
            dark.setBackgroundColor(android.graphics.Color.parseColor("#2D2D2D"))
            dark.setTextColor(android.graphics.Color.parseColor("#888888"))
        }
    }
}