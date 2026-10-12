package com.rams.editor

import android.graphics.Color
import android.text.Spannable
import android.text.style.ForegroundColorSpan
import java.util.regex.Pattern

object SyntaxHighlighter {

    // Colors
    private val COLOR_TAG = Color.parseColor("#569CD6")        // blue
    private val COLOR_ATTRIBUTE = Color.parseColor("#9CDCFE")  // light blue
    private val COLOR_STRING = Color.parseColor("#CE9178")     // orange
    private val COLOR_COMMENT = Color.parseColor("#6A9955")    // green
    private val COLOR_DOCTYPE = Color.parseColor("#808080")    // gray
    private val COLOR_CSS_PROPERTY = Color.parseColor("#9CDCFE")
    private val COLOR_CSS_VALUE = Color.parseColor("#CE9178")
    private val COLOR_JS_KEYWORD = Color.parseColor("#C586C0") // purple
    private val COLOR_JS_STRING = Color.parseColor("#CE9178")
    private val COLOR_JS_NUMBER = Color.parseColor("#B5CEA8")  // light green

    private val JS_KEYWORDS = setOf(
        "var", "let", "const", "function", "return", "if", "else",
        "for", "while", "do", "break", "continue", "new", "this",
        "typeof", "null", "undefined", "true", "false", "class",
        "import", "export", "default", "async", "await"
    )

    fun highlight(text: Spannable) {
        clearSpans(text)
        highlightHtml(text)
        highlightJs(text)
    }

    private fun clearSpans(text: Spannable) {
        text.getSpans(0, text.length, ForegroundColorSpan::class.java)
            .forEach { text.removeSpan(it) }
    }

    private fun highlightHtml(text: Spannable) {
        // Comments
        applyPattern(text, "<!--[\\s\\S]*?-->", COLOR_COMMENT)
        // DOCTYPE
        applyPattern(text, "<!DOCTYPE[^>]*>", COLOR_DOCTYPE)
        // Tags
        applyPattern(text, "</?[a-zA-Z][a-zA-Z0-9]*", COLOR_TAG)
        applyPattern(text, "/?>", COLOR_TAG)
        // Attributes
        applyPattern(text, "\\s[a-zA-Z-]+(?==)", COLOR_ATTRIBUTE)
        // Strings inside tags
        applyPattern(text, "\"[^\"]*\"|'[^']*'", COLOR_STRING)
    }

    private fun highlightJs(text: Spannable) {
        // Single line comments
        applyPattern(text, "//[^\n]*", COLOR_COMMENT)
        // Multi line comments
        applyPattern(text, "/\\*[\\s\\S]*?\\*/", COLOR_COMMENT)
        // Strings
        applyPattern(text, "\"[^\"]*\"|'[^']*'|`[^`]*`", COLOR_JS_STRING)
        // Numbers
        applyPattern(text, "\\b\\d+\\.?\\d*\\b", COLOR_JS_NUMBER)
        // Keywords
        JS_KEYWORDS.forEach { keyword ->
            applyPattern(text, "\\b$keyword\\b", COLOR_JS_KEYWORD)
        }
    }

    private fun applyPattern(text: Spannable, pattern: String, color: Int) {
        try {
            val matcher = Pattern.compile(pattern).matcher(text)
            while (matcher.find()) {
                text.setSpan(
                    ForegroundColorSpan(color),
                    matcher.start(),
                    matcher.end(),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}