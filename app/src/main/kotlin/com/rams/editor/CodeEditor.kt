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
        textSize = 40f
        isAntiAlias = true
        typeface = android.graphics.Typeface.MONOSPACE
    }

    private val lineNumberWidth = 80f
    private var onTextChangedListener: ((String) -> Unit)? = null

    init {
        setPadding(lineNumberWidth.toInt() + 12, 12, 12, 12)
        setTextColor(Color.parseColor("#D4D4D4"))
        setBackgroundColor(Color.parseColor("#1E1E1E"))
        textSize = 14f
        typeface = android.graphics.Typeface.MONOSPACE

        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                s?.let {
                    removeTextChangedListener(this)
                    SyntaxHighlighter.highlight(it)
                    addTextChangedListener(this)
                    onTextChangedListener?.invoke(it.toString())
                }
            }
        })
    }

    override fun onDraw(canvas: Canvas) {
        val text = text.toString()
        val lines = text.split("\n")
        val lineHeight = lineHeight.toFloat()
        val paddingTop = paddingTop.toFloat()

        for (i in lines.indices) {
            val lineNumber = (i + 1).toString()
            val y = paddingTop + (i * lineHeight) + textSize
            canvas.drawText(
                lineNumber,
                4f,
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