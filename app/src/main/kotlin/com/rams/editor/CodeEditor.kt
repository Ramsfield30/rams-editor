package com.rams.editor

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.util.AttributeSet
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.event.EventReceiver
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import io.github.rosemoe.sora.widget.schemes.SchemeDarcula
import io.github.rosemoe.sora.widget.CodeEditor as SoraEditor

// Rams Editor's editor, now built on sora-editor.
// It draws only the lines you can see, so long files stay fast.
class CodeEditor @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0
) : SoraEditor(context, attrs, defStyleAttr, defStyleRes) {

    // ---- Settings the rest of the app already uses ----
    var hint: CharSequence? = null

    var tabSize: Int = 4
        set(value) {
            val changed = field != value
            field = value
            if (changed) tabWidth = value
        }

    var showLineNumbers: Boolean = true
        set(value) {
            val changed = field != value
            field = value
            if (changed) isLineNumberEnabled = value
        }

    var showActiveLine: Boolean = true
        set(value) {
            val changed = field != value
            field = value
            if (changed) {
                colorScheme.setColor(
                    EditorColorScheme.CURRENT_LINE,
                    if (value) Color.parseColor("#282828") else Color.TRANSPARENT
                )
                invalidate()
            }
        }

    // Not wired up yet (coming back in a later step)
    var autoCloseEnabled: Boolean = true
    var showIndentGuides: Boolean = true

    init {
        val scheme = SchemeDarcula()
        scheme.setColor(EditorColorScheme.WHOLE_BACKGROUND, Color.parseColor("#1E1E1E"))
        scheme.setColor(EditorColorScheme.TEXT_NORMAL, Color.parseColor("#D8D8D8"))
        scheme.setColor(EditorColorScheme.LINE_NUMBER_BACKGROUND, Color.parseColor("#1A1A1A"))
        scheme.setColor(EditorColorScheme.LINE_NUMBER, Color.parseColor("#858585"))
        scheme.setColor(EditorColorScheme.LINE_DIVIDER, Color.parseColor("#3C3C3C"))
        scheme.setColor(EditorColorScheme.CURRENT_LINE, Color.parseColor("#282828"))
        scheme.setColor(EditorColorScheme.SELECTED_TEXT_BACKGROUND, Color.parseColor("#264F78"))
        for (i in 1 until SyntaxHighlighter.PALETTE.size) {
            scheme.setColor(SyntaxHighlighter.COLOR_BASE + i, SyntaxHighlighter.PALETTE[i])
        }
        colorScheme = scheme
        typefaceText = Typeface.MONOSPACE
        setTextSize(14f)
        isWordwrap = false
        tabWidth = 4
        isLineNumberEnabled = true
        RamsAnalyzeManager.onStylesApplied = { reveal() }
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        if (isEditable != enabled) isEditable = enabled
    }

    private var fontSp = 14

    fun setFontSizeSp(sp: Int) {
        if (sp != fontSp) {
            fontSp = sp
            setTextSize(sp.toFloat())
        }
    }

    fun setWordWrap(wrap: Boolean) {
        if (isWordwrap != wrap) isWordwrap = wrap
    }

    // ---- Text in and out ----
    private var currentLanguage: RamsLanguage? = null

    fun setLanguageForFile(name: String) {
        val lang = currentLanguage
        if (lang != null) {
            lang.setFile(name)
        } else {
            val created = RamsLanguage(name)
            currentLanguage = created
            setEditorLanguage(created)
        }
    }

    fun getCode(): String = text.toString()

    fun setCode(code: String) {
        setText(code)
    }

    // Colors are now handled by the editor itself, so just show the text
    fun setHighlightedCode(colored: SpannableStringBuilder) {
        setText(colored.toString())
    }

    fun setOnTextChangedListener(listener: (String) -> Unit) {
        subscribeEvent(
            ContentChangeEvent::class.java,
            EventReceiver<ContentChangeEvent> { _, _ -> listener(getCode()) }
        )
    }

    // ---- Cursor helpers (the old editor used plain character positions) ----
    val selectionStart: Int
        get() = cursor.left

    val selectionEnd: Int
        get() = cursor.right

    fun setSelection(index: Int) {
        val pos = text.indexer.getCharPosition(index.coerceIn(0, text.length))
        setSelection(pos.line, pos.column)
    }

    fun insertSnippet(code: String, cursorOffset: Int) {
        val start = cursor.left
        text.insert(cursor.leftLine, cursor.leftColumn, code)
        setSelection((start + cursorOffset).coerceAtMost(text.length))
    }

    fun moveCursorLines(dy: Int) {
        val line = (cursor.leftLine + dy).coerceIn(0, text.lineCount - 1)
        val col = cursor.leftColumn.coerceAtMost(text.getColumnCount(line))
        setSelection(line, col)
    }

    fun lineCount(): Int = text.lineCount

    fun goToLine(line: Int) {
        val l = (line - 1).coerceIn(0, text.lineCount - 1)
        requestFocus()
        setSelection(l, 0)
    }

    // ---- Hide the editor while a new file is colored ----
    private var hiding = false

    // Keeps plain text from flashing while a new file gets its colors
    fun hideUntilColored() {
        hiding = true
        alpha = 0f
        postDelayed({ reveal() }, 400)
    }

    fun reveal() {
        if (!hiding) return
        hiding = false
        animate().alpha(1f).setDuration(60).start()
    }

    // ---- Find / Replace: switched off for now, comes back in a later step ----
    var onFindResult: ((Int, Int) -> Unit)? = null
    var findCase = false
    var findWord = false
    var findRegex = false

    fun find(query: String) {
        onFindResult?.invoke(0, 0)
    }

    fun refreshMatches(fromCursor: Boolean = false) {}
    fun findNext() {}
    fun findPrev() {}
    fun replaceCurrent(replacement: String) {}
    fun replaceAll(replacement: String): Int = 0
    fun clearFind() {
        onFindResult?.invoke(0, 0)
    }
}