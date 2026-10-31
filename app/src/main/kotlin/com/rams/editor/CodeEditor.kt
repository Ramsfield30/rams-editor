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

    private var cachedLines: List<String> = listOf("")

    private val highlightRunnable = Runnable {
        val t = text ?: return@Runnable
        SyntaxHighlighter.highlight(t)
    }

    private fun scheduleHighlight() {
        removeCallbacks(highlightRunnable)
        postDelayed(highlightRunnable, 120)
    }

    var showIndentGuides = true
        set(value) { field = value; invalidate() }

    var showActiveLine = true
        set(value) { field = value; invalidate() }

    private val indentGuidePaint = Paint().apply {
        color = Color.parseColor("#404040")
        strokeWidth = 2f
    }

    fun setFontSizeSp(sp: Int) {
        textSize = sp.toFloat()
        lineNumberPaint.textSize = textSize * 0.95f
        invalidate()
    }

    fun setWordWrap(wrap: Boolean) {
        setHorizontallyScrolling(!wrap)
    }

    // ---------- Find ----------
    var onFindResult: ((Int, Int) -> Unit)? = null
    private var findQuery = ""
    private var matchStarts: List<Int> = emptyList()
    private var matchIndex = -1
    private val matchColor = Color.parseColor("#614D1A")
    private val currentMatchColor = Color.parseColor("#A07A12")

    private class MatchSpan(color: Int) : android.text.style.BackgroundColorSpan(color)

    fun find(query: String) {
        findQuery = query
        refreshMatches(fromCursor = true)
    }

    fun refreshMatches(fromCursor: Boolean = false) {
        val s = text?.toString() ?: return
        val found = ArrayList<Int>()
        if (findQuery.isNotEmpty()) {
            var i = s.indexOf(findQuery, 0, ignoreCase = true)
            while (i >= 0 && found.size < 5000) {
                found.add(i)
                i = s.indexOf(findQuery, i + findQuery.length, ignoreCase = true)
            }
        }
        matchStarts = found
        matchIndex = when {
            found.isEmpty() -> -1
            fromCursor -> found.indexOfFirst { it >= selectionStart }
                .let { if (it < 0) 0 else it }
            else -> matchIndex.coerceIn(0, found.size - 1)
        }
        paintMatches()
        if (fromCursor && matchIndex >= 0) revealCurrentMatch()
        onFindResult?.invoke(matchIndex + 1, found.size)
    }

    private fun paintMatches() {
        val t = text ?: return
        t.getSpans(0, t.length, MatchSpan::class.java).forEach { t.removeSpan(it) }
        for ((n, start) in matchStarts.withIndex()) {
            val color = if (n == matchIndex) currentMatchColor else matchColor
            t.setSpan(
                MatchSpan(color), start, start + findQuery.length,
                android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
    }

    private fun revealCurrentMatch() {
        val start = matchStarts[matchIndex]
        setSelection(start, start + findQuery.length)
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

    fun clearFind() {
        findQuery = ""
        matchStarts = emptyList()
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
                scheduleHighlight()
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