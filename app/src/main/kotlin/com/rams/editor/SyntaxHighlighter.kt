package com.rams.editor

import android.graphics.Color
import android.text.SpannableStringBuilder
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

// Works out a color number for every character of a file (0 = plain text).
// Runs on a background thread; RamsLanguage turns the result into editor colors.
object SyntaxHighlighter {

    // Color numbers. The editor color id is COLOR_BASE + number.
    const val COLOR_BASE = 255
    private const val K_COMMENT: Byte = 1
    private const val K_BRACKET: Byte = 2
    private const val K_BLUE: Byte = 3
    private const val K_CYAN: Byte = 4
    private const val K_YELLOW: Byte = 5
    private const val K_PURPLE: Byte = 6
    private const val K_VIOLET: Byte = 7
    private const val K_CREAM: Byte = 8
    private const val K_TEAL: Byte = 9
    private const val K_MINT: Byte = 10
    private const val K_PALE_MINT: Byte = 11

    // Index = color number. Colors sampled from Spck screenshots.
    val PALETTE: IntArray = intArrayOf(
        0,
        Color.parseColor("#7C8CB4"), // 1 comment
        Color.parseColor("#9C9C9C"), // 2 bracket
        Color.parseColor("#52AAF8"), // 3 blue
        Color.parseColor("#ABF1F9"), // 4 cyan
        Color.parseColor("#F3FB8C"), // 5 yellow
        Color.parseColor("#D3A4FA"), // 6 purple
        Color.parseColor("#BB94FA"), // 7 violet
        Color.parseColor("#F8F8D0"), // 8 cream
        Color.parseColor("#1CDCC4"), // 9 teal
        Color.parseColor("#A4FABC"), // 10 mint
        Color.parseColor("#C4ECD4")  // 11 pale mint
    )

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
    private val JS_HINT = Regex("\\b(?:function|const|let)\\s|=>")

    private enum class Mode { AUTO, HTML, CSS, JS, NONE }

    private val cache = ConcurrentHashMap<String, Pattern>()

    private fun modeFor(name: String): Mode =
        when (name.substringAfterLast('.', "").lowercase()) {
            "html", "htm", "xml", "svg" -> Mode.HTML
            "css" -> Mode.CSS
            "js", "mjs", "cjs", "jsx", "ts", "tsx", "json" -> Mode.JS
            else -> Mode.NONE
        }

    // Kept so older code keeps compiling
    fun setFileName(name: String) {}

    fun build(code: String, name: String): SpannableStringBuilder = SpannableStringBuilder(code)

    // One color number per character
    fun analyze(code: String, name: String): ByteArray {
        val out = ByteArray(code.length)
        var mode = modeFor(name)
        if (mode == Mode.AUTO) {
            mode = when {
                code.contains("<html") || code.contains("<!DOCTYPE") -> Mode.HTML
                JS_HINT.containsMatchIn(code) -> Mode.JS
                code.contains("{") && code.contains(":") -> Mode.CSS
                else -> Mode.JS
            }
        }
        when (mode) {
            Mode.HTML -> html(out, code)
            Mode.CSS -> css(out, code, 0, code.length)
            Mode.JS -> js(out, code, 0, code.length)
            else -> {}
        }
        return out
    }

    private fun html(out: ByteArray, src: String) {
        val n = src.length
        paint(out, src, "<!DOCTYPE[^>]*>", K_BRACKET, 0, n)
        paint(out, src, "</?|/?>", K_BRACKET, 0, n)
        paint(out, src, "(?:(?<=<)|(?<=</))[a-zA-Z][a-zA-Z0-9-]*", K_BLUE, 0, n)
        paint(out, src, "\\s[a-zA-Z:@-]+(?==)", K_CYAN, 0, n)
        paint(out, src, "(?<==)(?:\"[^\"]*\"|'[^']*')", K_YELLOW, 0, n)

        // <style> and <script> blocks inside the HTML file
        val sm = STYLE_BLOCK.matcher(src)
        while (sm.find()) css(out, src, sm.start(1), sm.end(1))
        val jm = SCRIPT_BLOCK.matcher(src)
        while (jm.find()) js(out, src, jm.start(1), jm.end(1))

        paint(out, src, "<!--[\\s\\S]*?-->", K_COMMENT, 0, n)
    }

    private fun css(out: ByteArray, src: String, s: Int, e: Int) {
        paint(out, src, "[a-zA-Z-]+(?=\\s*:)", K_CYAN, s, e)
        paint(out, src, "\\b\\d+(?:\\.\\d+)?\\b", K_CYAN, s, e)
        paint(out, src, CSS_CYAN_WORDS, K_CYAN, s, e)
        paint(out, src, CSS_VIOLET_WORDS, K_VIOLET, s, e)
        paint(out, src, "#[0-9a-fA-F]{3,8}\\b", K_VIOLET, s, e)
        paint(out, src, "(?:[.#]|::?-?)?[a-zA-Z_][a-zA-Z0-9_-]*(?=[^{};]*\\{)", K_BLUE, s, e)
        paint(out, src, "@[a-zA-Z-]+", K_PURPLE, s, e)
        paint(out, src, "(?<=\\()[a-zA-Z-]+(?=\\s*:)", K_CYAN, s, e)
        paint(out, src, "\\d+(?:\\.\\d+)?(?:px|em|rem|vh|vw|%|pt|pc|cm|mm|in|s|ms|deg|fr)", K_PALE_MINT, s, e)
        paint(out, src, "\"[^\"\\n]*\"|'[^'\\n]*'", K_YELLOW, s, e)
        paint(out, src, "/\\*[\\s\\S]*?\\*/", K_COMMENT, s, e)
    }

    private fun js(out: ByteArray, src: String, s: Int, e: Int) {
        paint(out, src, "[a-zA-Z_][a-zA-Z0-9_]*", K_CYAN, s, e)
        paint(out, src, "\\b[A-Z][a-zA-Z0-9_]*\\b", K_TEAL, s, e)
        paint(out, src, "\\b[a-zA-Z_][a-zA-Z0-9_]*(?=\\s*\\()", K_CREAM, s, e)
        paint(out, src, JS_DECL, K_BLUE, s, e)
        paint(out, src, JS_FLOW, K_PURPLE, s, e)
        paint(out, src, JS_LITERAL, K_VIOLET, s, e)
        paint(out, src, "\\b\\d+(?:\\.\\d+)?\\b", K_PALE_MINT, s, e)
        paint(out, src, "\"[^\"\\n]*\"|'[^'\\n]*'|`[^`]*`", K_YELLOW, s, e)
        paint(out, src, "(?<!:)//[^\\n]*", K_COMMENT, s, e)
        paint(out, src, "/\\*[\\s\\S]*?\\*/", K_COMMENT, s, e)
    }

    // Later calls win where colors overlap, so the order above matters
    private fun paint(out: ByteArray, src: String, regex: String, kind: Byte, start: Int, end: Int) {
        if (start >= end) return
        try {
            val p = cache.getOrPut(regex) { Pattern.compile(regex) }
            val m = p.matcher(src)
            m.region(start, end)
            m.useTransparentBounds(true)
            m.useAnchoringBounds(false)
            while (m.find()) {
                if (m.end() > m.start()) {
                    java.util.Arrays.fill(out, m.start(), m.end(), kind)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}