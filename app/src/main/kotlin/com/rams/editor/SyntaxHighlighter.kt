package com.rams.editor

import android.graphics.Color
import android.text.Spannable
import android.text.style.ForegroundColorSpan
import java.util.regex.Pattern

object SyntaxHighlighter {

    // Colors sampled from Spck screenshots
    private val C_COMMENT = Color.parseColor("#7C8CB4")
    private val C_BRACKET = Color.parseColor("#9C9C9C")
    private val C_BLUE = Color.parseColor("#52AAF8")
    private val C_CYAN = Color.parseColor("#ABF1F9")
    private val C_YELLOW = Color.parseColor("#F3FB8C")
    private val C_PURPLE = Color.parseColor("#D3A4FA")
    private val C_VIOLET = Color.parseColor("#BB94FA")
    private val C_CREAM = Color.parseColor("#F8F8D0")
    private val C_TEAL = Color.parseColor("#1CDCC4")
    private val C_MINT = Color.parseColor("#A4FABC")
    private val C_PALE_MINT = Color.parseColor("#C4ECD4")

    private const val JS_DECL =
        "\\b(?:var|let|const|function|class|new|typeof|instanceof|this|extends|static)\\b"
    private const val JS_FLOW =
        "\\b(?:if|else|for|while|do|break|continue|return|try|catch|finally|switch|case|default|throw|import|export|async|await|yield|in|of|delete)\\b"
    private const val JS_LITERAL = "\\b(?:true|false|null|undefined)\\b"

    private const val CSS_CYAN_WORDS =
        "\\b(?:block|inline|inline-block|flex|grid|absolute|relative|fixed|sticky|center|left|right|top|bottom|solid|dashed|dotted|bold|italic|column|row|wrap|nowrap|pointer|cover|contain)\\b"
    private const val CSS_VIOLET_WORDS =
        "\\b(?:none|auto|inherit|initial|hidden|visible|transparent|normal|unset)\\b"

    private val STYLE_BLOCK =
        Pattern.compile("<style[^>]*>([\\s\\S]*?)</style>", Pattern.CASE_INSENSITIVE)
    private val SCRIPT_BLOCK =
        Pattern.compile("<script[^>]*>([\\s\\S]*?)</script>", Pattern.CASE_INSENSITIVE)

    private enum class Mode { AUTO, HTML, CSS, JS, NONE }

    private var mode = Mode.AUTO
    private var src = ""
    private val cache = HashMap<String, Pattern>()

    fun setFileName(name: String) {
        mode = when (name.substringAfterLast('.', "").lowercase()) {
            "html", "htm", "xml", "svg" -> Mode.HTML
            "css" -> Mode.CSS
            "js", "mjs", "cjs", "jsx", "ts", "tsx", "json" -> Mode.JS
            else -> Mode.NONE
        }
    }

    fun highlight(text: Spannable) {
        clearSpans(text)
        src = text.toString()
        val m = if (mode == Mode.AUTO) {
            when {
                src.contains("<html") || src.contains("<!DOCTYPE") -> Mode.HTML
                src.contains("{") && src.contains(":") -> Mode.CSS
                else -> Mode.JS
            }
        } else mode

        when (m) {
            Mode.HTML -> highlightHtml(text)
            Mode.CSS -> highlightCss(text, 0, src.length)
            Mode.JS -> highlightJs(text, 0, src.length)
            else -> {}
        }
    }

    private fun clearSpans(text: Spannable) {
        text.getSpans(0, text.length, ForegroundColorSpan::class.java)
            .forEach { text.removeSpan(it) }
    }

    private fun highlightHtml(text: Spannable) {
        val n = src.length
        paint(text, "<!DOCTYPE[^>]*>", C_BRACKET, 0, n)
        paint(text, "</?|/?>", C_BRACKET, 0, n)
        paint(text, "(?:(?<=<)|(?<=</))[a-zA-Z][a-zA-Z0-9-]*", C_BLUE, 0, n)
        paint(text, "\\s[a-zA-Z:@-]+(?==)", C_CYAN, 0, n)
        paint(text, "(?<==)(?:\"[^\"]*\"|'[^']*')", C_YELLOW, 0, n)

        // <style> and <script> blocks inside the HTML file
        val sm = STYLE_BLOCK.matcher(src)
        while (sm.find()) highlightCss(text, sm.start(1), sm.end(1))
        val jm = SCRIPT_BLOCK.matcher(src)
        while (jm.find()) highlightJs(text, jm.start(1), jm.end(1))

        paint(text, "<!--[\\s\\S]*?-->", C_COMMENT, 0, n)
    }

    private fun highlightCss(text: Spannable, s: Int, e: Int) {
        paint(text, "[a-zA-Z-]+(?=\\s*:)", C_CYAN, s, e)
        paint(text, "\\b\\d+(?:\\.\\d+)?\\b", C_CYAN, s, e)
        paint(text, CSS_CYAN_WORDS, C_CYAN, s, e)
        paint(text, CSS_VIOLET_WORDS, C_VIOLET, s, e)
        paint(text, "#[0-9a-fA-F]{3,8}\\b", C_VIOLET, s, e)
        paint(text, "(?:[.#]|::?-?)?[a-zA-Z_][a-zA-Z0-9_-]*(?=[^{};]*\\{)", C_MINT, s, e)
        paint(text, "@[a-zA-Z-]+", C_PURPLE, s, e)
        paint(text, "(?<=\\()[a-zA-Z-]+(?=\\s*:)", C_CYAN, s, e)
        paint(text, "\\d+(?:\\.\\d+)?(?:px|em|rem|vh|vw|%|pt|pc|cm|mm|in|s|ms|deg|fr)", C_PALE_MINT, s, e)
        paint(text, "\"[^\"\\n]*\"|'[^'\\n]*'", C_YELLOW, s, e)
        paint(text, "/\\*[\\s\\S]*?\\*/", C_COMMENT, s, e)
    }

    private fun highlightJs(text: Spannable, s: Int, e: Int) {
        paint(text, "[a-zA-Z_][a-zA-Z0-9_]*", C_CYAN, s, e)
        paint(text, "\\b[A-Z][a-zA-Z0-9_]*\\b", C_TEAL, s, e)
        paint(text, "\\b[a-zA-Z_][a-zA-Z0-9_]*(?=\\s*\\()", C_CREAM, s, e)
        paint(text, JS_DECL, C_BLUE, s, e)
        paint(text, JS_FLOW, C_PURPLE, s, e)
        paint(text, JS_LITERAL, C_VIOLET, s, e)
        paint(text, "\\b\\d+(?:\\.\\d+)?\\b", C_PALE_MINT, s, e)
        paint(text, "\"[^\"\\n]*\"|'[^'\\n]*'|`[^`]*`", C_YELLOW, s, e)
        paint(text, "(?<!:)//[^\\n]*", C_COMMENT, s, e)
        paint(text, "/\\*[\\s\\S]*?\\*/", C_COMMENT, s, e)
    }

    // Later calls win where colors overlap, so order matters above
    private fun paint(text: Spannable, regex: String, color: Int, start: Int, end: Int) {
        if (start >= end) return
        try {
            val p = cache.getOrPut(regex) { Pattern.compile(regex) }
            val m = p.matcher(src)
            m.region(start, end)
            m.useTransparentBounds(true)
            m.useAnchoringBounds(false)
            while (m.find()) {
                if (m.end() > m.start()) {
                    text.setSpan(
                        ForegroundColorSpan(color),
                        m.start(),
                        m.end(),
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}