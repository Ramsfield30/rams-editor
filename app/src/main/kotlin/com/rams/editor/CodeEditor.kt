package com.rams.editor

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatEditText

class CodeEditor @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : AppCompatEditText(context, attrs) {

    private val lineNumberPaint = Paint().apply {
        color = Color.parseColor("#858585")
        textSize = 42f
        isAntiAlias = true
        typeface = android.graphics.Typeface.MONOSPACE
    }

    private val lineNumberBgPaint = Paint().apply {
        color = Color.parseColor("#1A1A1A")
    }

    private val lineNumberDividerPaint = Paint().apply {
        color = Color.parseColor("#3C3C3C")
        strokeWidth = 2f
    }

    private val currentLinePaint = Paint().apply {
        color = Color.parseColor("#282828")
    }

    private val lineNumberWidth = 90f
    private var onTextChangedListener: ((String) -> Unit)? = null
    private var currentLine = 0
    private var isAutoCompleting = false

    var showLineNumbers = true
        set(value) {
            field = value
            setPadding(if (value) lineNumberWidth.toInt() + 16 else 16, 16, 16, 16)
            invalidate()
        }

    var autoCloseEnabled = true

    var tabSize = 4
    private fun indentUnit(): String = " ".repeat(tabSize)

    var showIndentGuides = true
        set(value) { field = value; invalidate() }

    var showActiveLine = true
        set(value) { field = value; invalidate() }

    private val indentGuidePaint = Paint().apply {
        color = Color.parseColor("#404040")
        strokeWidth = 2f
    }

    private var cachedLines: List<String> = listOf("")
    private var skipNextHighlight = false

    private val highlightRunnable = Runnable {
        val t = text ?: return@Runnable
        SyntaxHighlighter.highlight(t)
    }

    private fun scheduleHighlight() {
        removeCallbacks(highlightRunnable)
        val t = text
        val pos = selectionStart
        val endedWord = t != null && pos > 0 && pos <= t.length &&
            !(t[pos - 1].isLetterOrDigit() || t[pos - 1] == '_')
        // finished a word (space, ( . ; Enter...) -> color soon; mid-word -> wait for a pause
        postDelayed(highlightRunnable, if (endedWord) 100L else 700L)
    }

    // Shows text that was already colored on a background thread
    fun setHighlightedCode(colored: android.text.SpannableStringBuilder) {
        skipNextHighlight = true
        removeCallbacks(highlightRunnable)
        setText(colored)
    }

    fun setFontSizeSp(sp: Int) {
        textSize = sp.toFloat()
        lineNumberPaint.textSize = textSize * 0.95f
        invalidate()
    }

    fun setWordWrap(wrap: Boolean) {
        setHorizontallyScrolling(!wrap)
    }

    // ---------- Find / Replace ----------
    var onFindResult: ((Int, Int) -> Unit)? = null
    var findCase = false
    var findWord = false
    var findRegex = false
    private var findQuery = ""
    private var findPattern: java.util.regex.Pattern? = null
    private var matchStarts: List<Int> = emptyList()
    private var matchEnds: List<Int> = emptyList()
    private var matchIndex = -1
    private val matchColor = Color.parseColor("#614D1A")
    private val currentMatchColor = Color.parseColor("#A07A12")

    private class MatchSpan(color: Int) : android.text.style.BackgroundColorSpan(color)

    private fun buildFindPattern(): java.util.regex.Pattern? {
        if (findQuery.isEmpty()) return null
        return try {
            var p = if (findRegex) findQuery else java.util.regex.Pattern.quote(findQuery)
            if (findWord) p = "\\b(?:$p)\\b"
            java.util.regex.Pattern.compile(
                p, if (findCase) 0 else java.util.regex.Pattern.CASE_INSENSITIVE
            )
        } catch (e: Exception) {
            null
        }
    }

    fun find(query: String) {
        findQuery = query
        refreshMatches(fromCursor = true)
    }

    fun refreshMatches(fromCursor: Boolean = false) {
        val s = text?.toString() ?: return
        val p = buildFindPattern()
        findPattern = p
        val starts = ArrayList<Int>()
        val ends = ArrayList<Int>()
        if (p != null) {
            val m = p.matcher(s)
            while (m.find() && starts.size < 5000) {
                if (m.end() > m.start()) {
                    starts.add(m.start())
                    ends.add(m.end())
                }
            }
        }
        matchStarts = starts
        matchEnds = ends
        matchIndex = when {
            starts.isEmpty() -> -1
            fromCursor -> starts.indexOfFirst { it >= selectionStart }
                .let { if (it < 0) 0 else it }
            else -> matchIndex.coerceIn(0, starts.size - 1)
        }
        paintMatches()
        if (fromCursor && matchIndex >= 0) revealCurrentMatch()
        val invalid = findQuery.isNotEmpty() && p == null
        onFindResult?.invoke(matchIndex + 1, if (invalid) -1 else starts.size)
    }

    private fun paintMatches() {
        val t = text ?: return
        t.getSpans(0, t.length, MatchSpan::class.java).forEach { t.removeSpan(it) }
        for (n in matchStarts.indices) {
            val color = if (n == matchIndex) currentMatchColor else matchColor
            t.setSpan(
                MatchSpan(color), matchStarts[n], matchEnds[n],
                android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
    }

    private fun revealCurrentMatch() {
        val start = matchStarts[matchIndex]
        setSelection(start, matchEnds[matchIndex])
        val l = layout ?: return
        val y = l.getLineTop(l.getLineForOffset(start)) - lineHeight * 3
        (parent as? android.widget.ScrollView)?.smoothScrollTo(0, y.coerceAtLeast(0))
    }

    fun findNext() = stepMatch(1)
    fun findPrev() = stepMatch(-1)

    private fun stepMatch(dir: Int) {
        if (matchStarts.isEmpty()) return
        matchIndex = (matchIndex + dir + matchStarts.size) % matchStarts.size
        paintMatches()
        revealCurrentMatch()
        onFindResult?.invoke(matchIndex + 1, matchStarts.size)
    }

    fun replaceCurrent(replacement: String) {
        val p = findPattern ?: return
        if (matchIndex < 0 || matchIndex >= matchStarts.size) return
        val t = text ?: return
        val start = matchStarts[matchIndex]
        val end = matchEnds[matchIndex]
        val found = t.subSequence(start, end).toString()
        val out = if (findRegex) {
            try { p.matcher(found).replaceFirst(replacement) } catch (e: Exception) { replacement }
        } else replacement
        t.replace(start, end, out)
        setSelection(start + out.length)
        refreshMatches(fromCursor = true)
    }

    fun replaceAll(replacement: String): Int {
        val p = findPattern ?: return 0
        val t = text ?: return 0
        val m = p.matcher(t.toString())
        val buf = StringBuffer()
        var n = 0
        try {
            val repl = if (findRegex) replacement
                else java.util.regex.Matcher.quoteReplacement(replacement)
            while (m.find()) {
                if (m.end() == m.start()) continue
                n++
                m.appendReplacement(buf, repl)
            }
            if (n == 0) return 0
            m.appendTail(buf)
        } catch (e: Exception) {
            return 0
        }
        t.replace(0, t.length, buf.toString())
        refreshMatches(fromCursor = true)
        return n
    }

    fun clearFind() {
        findQuery = ""
        findPattern = null
        matchStarts = emptyList()
        matchEnds = emptyList()
        matchIndex = -1
        paintMatches()
        onFindResult?.invoke(0, 0)
    }

    fun lineCount(): Int = (text?.count { it == '\n' } ?: 0) + 1

    fun goToLine(line: Int) {
        val t = text ?: return
        val target = line.coerceIn(1, lineCount())
        var offset = 0
        var current = 1
        while (current < target) {
            offset = t.indexOf('\n', offset) + 1
            current++
        }
        requestFocus()
        setSelection(offset)
        val l = layout ?: return
        val y = l.getLineTop(l.getLineForOffset(offset))
        (parent as? android.widget.ScrollView)?.smoothScrollTo(0, y)
    }

    // Self-closing tags that should NOT get closing tag
    private val selfClosingTags = setOf(
        "br", "hr", "img", "input", "meta", "link",
        "area", "base", "col", "embed", "param", "source",
        "track", "wbr", "!DOCTYPE"
    )

    init {
        setPadding(lineNumberWidth.toInt() + 16, 16, 16, 16)
        setTextColor(Color.parseColor("#D8D8D8"))
        setBackgroundColor(Color.parseColor("#1E1E1E"))
        textSize = 14f
        typeface = android.graphics.Typeface.MONOSPACE
        highlightColor = Color.parseColor("#264F78")

        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(
                s: CharSequence?, start: Int,
                count: Int, after: Int
            ) {}

            override fun onTextChanged(
                s: CharSequence?, start: Int,
                before: Int, count: Int
            ) {
                if (isAutoCompleting) return
                if (count == 1 && s != null) {
                    val char = s[start]
                    when {
                        // Auto indent on Enter
                        char == '\n' -> {
                            val textBefore = s.substring(0, start)
                            val textAfter =
                                if (start + 1 < s.length) s.substring(start + 1) else ""
                            val lines = textBefore.split("\n")
                            val lastLine = lines.lastOrNull() ?: ""
                            val indent = lastLine.takeWhile { it == ' ' || it == '\t' }
                            val trimmed = lastLine.trimEnd()

                            val endsWithOpenTag = trimmed.endsWith(">") &&
                                !trimmed.matches(Regex(".*</[^>]+>$")) &&
                                !trimmed.matches(
                                    Regex(".*<(${selfClosingTags.joinToString("|")})[^>]*/?>$")
                                )

                            val nextIsClosingTag = textAfter.trimStart().startsWith("</")

                            val opensBracket = trimmed.endsWith("{") ||
                                trimmed.endsWith("[") || trimmed.endsWith("(")
                            val nextIsClosingBracket =
                                textAfter.isNotEmpty() && textAfter[0] in "}])"

                            when {
                                (endsWithOpenTag && nextIsClosingTag) ||
                                    (opensBracket && nextIsClosingBracket) -> {
                                    // Cursor between <head> and </head>
                                    isAutoCompleting = true
                                    post {
                                        val pos = selectionStart
                                        val innerIndent = indent + indentUnit()
                                        text?.insert(pos, innerIndent + "\n" + indent)
                                        setSelection(pos + innerIndent.length)
                                        isAutoCompleting = false
                                    }
                                }
                                indent.isNotEmpty() || endsWithOpenTag || opensBracket -> {
                                    val extraIndent = if (endsWithOpenTag || opensBracket) indentUnit() else ""
                                    isAutoCompleting = true
                                    post {
                                        val pos = selectionStart
                                        text?.insert(pos, indent + extraIndent)
                                        isAutoCompleting = false
                                    }
                                }
                            }
                        }

                        // Auto close HTML tags when > is typed
                        char == '>' && autoCloseEnabled -> {
                            val textBefore = s.substring(0, start + 1)
                            val tagMatch = Regex("<([a-zA-Z][a-zA-Z0-9]*)([^>]*)>$")
                                .find(textBefore)
                            if (tagMatch != null) {
                                val tagName = tagMatch.groupValues[1].lowercase()
                                val attrs = tagMatch.groupValues[2]
                                if (tagName !in selfClosingTags &&
                                    !attrs.endsWith("/")
                                ) {
                                    isAutoCompleting = true
                                    post {
                                        val pos = selectionStart
                                        text?.insert(pos, "</$tagName>")
                                        setSelection(pos)
                                        isAutoCompleting = false
                                    }
                                }
                            }
                        }

                        // Auto close braces
                        char == '{' && autoCloseEnabled -> {
                            isAutoCompleting = true
                            post {
                                val pos = selectionStart
                                text?.insert(pos, "}")
                                setSelection(pos)
                                isAutoCompleting = false
                            }
                        }

                        // Auto close brackets
                        char == '(' && autoCloseEnabled -> {
                            isAutoCompleting = true
                            post {
                                val pos = selectionStart
                                text?.insert(pos, ")")
                                setSelection(pos)
                                isAutoCompleting = false
                            }
                        }

                        // Auto close quotes
                        char == '"' && autoCloseEnabled -> {
                            isAutoCompleting = true
                            post {
                                val pos = selectionStart
                                text?.insert(pos, "\"")
                                setSelection(pos)
                                isAutoCompleting = false
                            }
                        }
                    }
                }
            }

            override fun afterTextChanged(s: Editable?) {
                if (s == null) return
                val str = s.toString()
                cachedLines = str.split("\n")
                onTextChangedListener?.invoke(str)
                if (isAutoCompleting) return
                if (skipNextHighlight) skipNextHighlight = false else scheduleHighlight()
                updateCurrentLine()
            }
        })
    }

    private fun updateCurrentLine() {
        val pos = selectionStart
        if (pos < 0) return
        val textBefore = text.toString()
            .substring(0, minOf(pos, text?.length ?: 0))
        currentLine = textBefore.count { it == '\n' }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val lines = cachedLines
        val lineHeight = lineHeight.toFloat()
        val paddingTop = paddingTop.toFloat()

        if (showLineNumbers) {
            // Line number background
            canvas.drawRect(
                0f, 0f,
                lineNumberWidth, height.toFloat(),
                lineNumberBgPaint
            )

            // Divider
            canvas.drawLine(
                lineNumberWidth, 0f,
                lineNumberWidth, height.toFloat(),
                lineNumberDividerPaint
            )
        }

        // Current line highlight
        val lay = layout
        val row = lay?.getLineForOffset(selectionStart) ?: currentLine
        val currentLineY = paddingTop + (lay?.getLineTop(row)?.toFloat() ?: (currentLine * lineHeight))
        if (showActiveLine) {
            canvas.drawRect(
                0f, currentLineY,
                width.toFloat(), currentLineY + lineHeight + 8f,
                currentLinePaint
            )
        }

        if (showLineNumbers) {
            // Line numbers
            val lay = layout
            if (lay != null) {
                val clip = canvas.clipBounds
                var offset = 0
                for (i in lines.indices) {
                    val top = paddingTop + lay.getLineTop(lay.getLineForOffset(offset))
                    if (top > clip.bottom) break
                    if (top + lineHeight >= clip.top) {
                        val lineNumber = (i + 1).toString()
                        lineNumberPaint.color = if (i == currentLine)
                            Color.parseColor("#C6C6C6")
                        else
                            Color.parseColor("#858585")
                        val textWidth = lineNumberPaint.measureText(lineNumber)
                        canvas.drawText(
                            lineNumber,
                            lineNumberWidth - textWidth - 12f,
                            top + textSize * 1.2f,
                            lineNumberPaint
                        )
                    }
                    offset += lines[i].length + 1
                }
            }
        }

        if (showIndentGuides) {
            val lay = layout
            if (lay != null) {
                val clip = canvas.clipBounds
                val charW = paint.measureText(" ")
                var offset = 0
                for (line in lines) {
                    val top = paddingTop + lay.getLineTop(lay.getLineForOffset(offset))
                    if (top > clip.bottom) break
                    val bottom = paddingTop +
                        lay.getLineBottom(lay.getLineForOffset(offset + line.length))
                    if (bottom >= clip.top) {
                        val spaces = line.takeWhile { it == ' ' }.length
                        var col = 0
                        while (col < spaces) {
                            val x = paddingLeft + col * charW
                            canvas.drawLine(x, top, x, bottom, indentGuidePaint)
                            col += tabSize
                        }
                    }
                    offset += line.length + 1
                }
            }
        }

        super.onDraw(canvas)
    }

    fun setOnTextChangedListener(listener: (String) -> Unit) {
        onTextChangedListener = listener
    }

    fun getCode(): String = text.toString()

    fun setCode(code: String) {
        setText(code)
    }
}