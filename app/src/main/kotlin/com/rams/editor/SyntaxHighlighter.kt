package com.rams.editor

import android.graphics.Color
import android.text.Spannable
import android.text.style.ForegroundColorSpan
import java.util.regex.Pattern

object SyntaxHighlighter {

    private val COLOR_HTML_TAG = Color.parseColor("#569CD6")
    private val COLOR_HTML_ATTR = Color.parseColor("#9CDCFE")
    private val COLOR_STRING = Color.parseColor("#CE9178")
    private val COLOR_COMMENT = Color.parseColor("#6A9955")
    private val COLOR_DOCTYPE = Color.parseColor("#808080")
    private val COLOR_CSS_SELECTOR = Color.parseColor("#D7BA7D")
    private val COLOR_CSS_PROPERTY = Color.parseColor("#9CDCFE")
    private val COLOR_CSS_VALUE = Color.parseColor("#CE9178")
    private val COLOR_CSS_UNIT = Color.parseColor("#B5CEA8")
    private val COLOR_JS_KEYWORD = Color.parseColor("#C586C0")
    private val COLOR_JS_STRING = Color.parseColor("#CE9178")
    private val COLOR_JS_NUMBER = Color.parseColor("#B5CEA8")
    private val COLOR_JS_FUNCTION = Color.parseColor("#DCDCAA")
    private val COLOR_JS_COMMENT = Color.parseColor("#6A9955")

    private val JS_KEYWORDS = setOf(
        "var", "let", "const", "function", "return", "if", "else",
        "for", "while", "do", "break", "continue", "new", "this",
        "typeof", "null", "undefined", "true", "false", "class",
        "import", "export", "default", "async", "await", "try",
        "catch", "finally", "switch", "case", "throw", "instanceof"
    )

    fun highlight(text: Spannable) {
        clearSpans(text)
        val content = text.toString()
        when {
            content.contains("<html") || content.contains("<!DOCTYPE") -> {
                highlightHtml(text)
                highlightCssInStyle(text)
                highlightJsInScript(text)
            }
            content.contains("{") && content.contains(":") -> highlightCss(text)
            else -> highlightJs(text)
        }
    }

    private fun clearSpans(text: Spannable) {
        text.getSpans(0, text.length, ForegroundColorSpan::class.java)
            .forEach { text.removeSpan(it) }
    }

    private fun highlightHtml(text: Spannable) {
        applyPattern(text, "<!--[\\s\\S]*?-->", COLOR_COMMENT)
        applyPattern(text, "<!DOCTYPE[^>]*>", COLOR_DOCTYPE)
        applyPattern(text, "</?[a-zA-Z!][a-zA-Z0-9]*", COLOR_HTML_TAG)
        applyPattern(text, "/?>", COLOR_HTML_TAG)
        applyPattern(text, "\\s[a-zA-Z-]+(?==)", COLOR_HTML_ATTR)
        applyPattern(text, "\"[^\"]*\"|'[^']*'", COLOR_STRING)
    }

    private fun highlightCss(text: Spannable) {
        // Comments
        applyPattern(text, "/\\*[\\s\\S]*?\\*/", COLOR_COMMENT)
        // Selectors
        applyPattern(text, "[a-zA-Z][a-zA-Z0-9-]*(?=\\s*\\{)", COLOR_CSS_SELECTOR)
        applyPattern(text, "#[a-zA-Z][a-zA-Z0-9-]*(?=\\s*\\{)", COLOR_CSS_SELECTOR)
        applyPattern(text, "\\.[a-zA-Z][a-zA-Z0-9-]*(?=\\s*\\{)", COLOR_CSS_SELECTOR)
        // Properties
        applyPattern(text, "[a-zA-Z-]+(?=\\s*:)", COLOR_CSS_PROPERTY)
        // Values with units
        applyPattern(text, "\\d+(\\.\\d+)?(px|em|rem|vh|vw|%|pt|pc|cm|mm|in)", COLOR_CSS_UNIT)
        // Color values
        applyPattern(text, "#[0-9a-fA-F]{3,6}", COLOR_CSS_VALUE)
        // Strings
        applyPattern(text, "\"[^\"]*\"|'[^']*'", COLOR_STRING)
        // Keywords like solid, bold, flex, center etc
        applyPattern(text, ":\\s*(none|auto|block|inline|flex|grid|absolute|relative|fixed|bold|italic|normal|center|left|right|top|bottom|solid|dashed|dotted|hidden|visible|transparent|inherit|initial)", COLOR_CSS_VALUE)
    }

    private fun highlightCssInStyle(text: Spannable) {
        val content = text.toString()
        val styleStart = content.indexOf("<style")
        val styleEnd = content.indexOf("</style>")
        if (styleStart == -1 || styleEnd == -1) return
        val cssStart = content.indexOf(">", styleStart) + 1
        if (cssStart >= styleEnd) return
        val subText = text.subSequence(cssStart, styleEnd) as? Spannable ?: return
        highlightCss(subText)
    }

    private fun highlightJsInScript(text: Spannable) {
        val content = text.toString()
        val scriptStart = content.indexOf("<script")
        val scriptEnd = content.indexOf("</script>")
        if (scriptStart == -1 || scriptEnd == -1) return
        val jsStart = content.indexOf(">", scriptStart) + 1
        if (jsStart >= scriptEnd) return
        val subText = text.subSequence(jsStart, scriptEnd) as? Spannable ?: return
        highlightJs(subText)
    }

    private fun highlightJs(text: Spannable) {
        applyPattern(text, "//[^\n]*", COLOR_JS_COMMENT)
        applyPattern(text, "/\\*[\\s\\S]*?\\*/", COLOR_JS_COMMENT)
        applyPattern(text, "\"[^\"]*\"|'[^']*'|`[^`]*`", COLOR_JS_STRING)
        applyPattern(text, "\\b\\d+\\.?\\d*\\b", COLOR_JS_NUMBER)
        applyPattern(text, "\\b[a-zA-Z_][a-zA-Z0-9_]*(?=\\s*\\()", COLOR_JS_FUNCTION)
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