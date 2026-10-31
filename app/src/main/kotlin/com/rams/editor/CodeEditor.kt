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

    fun setFontSizeSp(sp: Int) {
        textSize = sp.toFloat()
        lineNumberPaint.textSize = textSize * 0.95f
        invalidate()
    }

    fun setWordWrap(wrap: Boolean) {
        setHorizontallyScrolling(!wrap)
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
        setTextColor(Color.parseColor("#D4D4D4"))
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

                            when {
                                endsWithOpenTag && nextIsClosingTag -> {
                                    // Cursor between <head> and </head>
                                    isAutoCompleting = true
                                    post {
                                        val pos = selectionStart
                                        val innerIndent = indent + "    "
                                        text?.insert(pos, innerIndent + "\n" + indent)
                                        setSelection(pos + innerIndent.length)
                                        isAutoCompleting = false
                                    }
                                }
                                indent.isNotEmpty() || endsWithOpenTag -> {
                                    val extraIndent = if (endsWithOpenTag) "    " else ""
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
                // Tell the preview and auto-save first, so coloring can't delay them
                onTextChangedListener?.invoke(s.toString())
                if (isAutoCompleting) return
                removeTextChangedListener(this)
                SyntaxHighlighter.highlight(s)
                addTextChangedListener(this)
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
        val text = text.toString()
        val lines = text.split("\n")
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
        canvas.drawRect(
            0f, currentLineY,
            width.toFloat(), currentLineY + lineHeight + 8f,
            currentLinePaint
        )

        if (showLineNumbers) {
            // Line numbers
            for (i in lines.indices) {
                val lineNumber = (i + 1).toString()
                val y = paddingTop + (i * lineHeight) + textSize * 1.2f
                lineNumberPaint.color = if (i == currentLine)
                    Color.parseColor("#C6C6C6")
                else
                    Color.parseColor("#858585")
                val textWidth = lineNumberPaint.measureText(lineNumber)
                canvas.drawText(
                    lineNumber,
                    lineNumberWidth - textWidth - 12f,
                    y,
                    lineNumberPaint
                )
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