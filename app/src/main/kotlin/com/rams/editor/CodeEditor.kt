package com.rams.editor

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
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

    init {
        setPadding(lineNumberWidth.toInt() + 16, 16, 16, 16)
        setTextColor(Color.parseColor("#D4D4D4"))
        setBackgroundColor(Color.parseColor("#1E1E1E"))
        textSize = 14f
        typeface = android.graphics.Typeface.MONOSPACE
        highlightColor = Color.parseColor("#264F78")

        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
    if (count == 1 && s != null && start > 0) {
        if (s[start] == '\n') {
            val lines = s.substring(0, start).split("\n")
            val lastLine = lines.lastOrNull() ?: ""
            val indent = lastLine.takeWhile { it == ' ' || it == '\t' }
            if (indent.isNotEmpty()) {
                post {
                    val pos = selectionStart
                    text?.insert(pos, indent)
                }
            }
        }
    }
}
            override fun afterTextChanged(s: Editable?) {
                s?.let {
                    removeTextChangedListener(this)
                    SyntaxHighlighter.highlight(it)
                    addTextChangedListener(this)
                    onTextChangedListener?.invoke(it.toString())
                    updateCurrentLine()
                }
            }
        })
    }

    private fun updateCurrentLine() {
        val pos = selectionStart
        if (pos < 0) return
        val textBefore = text.toString().substring(0, minOf(pos, text?.length ?: 0))
        currentLine = textBefore.count { it == '\n' }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val text = text.toString()
        val lines = text.split("\n")
        val lineHeight = lineHeight.toFloat()
        val paddingTop = paddingTop.toFloat()

        // Draw line number background
        canvas.drawRect(
            0f, 0f,
            lineNumberWidth, height.toFloat(),
            lineNumberBgPaint
        )

        // Draw divider
        canvas.drawLine(
            lineNumberWidth, 0f,
            lineNumberWidth, height.toFloat(),
            lineNumberDividerPaint
        )

        // Draw current line highlight
        val currentLineY = paddingTop + (currentLine * lineHeight)
        canvas.drawRect(
            0f, currentLineY,
            width.toFloat(), currentLineY + lineHeight + 8f,
            currentLinePaint
        )

        // Draw line numbers
        for (i in lines.indices) {
            val lineNumber = (i + 1).toString()
            val y = paddingTop + (i * lineHeight) + textSize * 1.2f

            // Highlight current line number
            if (i == currentLine) {
                lineNumberPaint.color = Color.parseColor("#C6C6C6")
            } else {
                lineNumberPaint.color = Color.parseColor("#858585")
            }

            // Right align line numbers
            val textWidth = lineNumberPaint.measureText(lineNumber)
            canvas.drawText(
                lineNumber,
                lineNumberWidth - textWidth - 12f,
                y,
                lineNumberPaint
            )
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