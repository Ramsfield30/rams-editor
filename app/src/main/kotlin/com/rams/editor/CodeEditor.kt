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

    // ---- Find / Replace ----
    var onFindResult: ((Int, Int) -> Unit)? = null
    var findCase = false
    var findWord = false
    var findRegex = false
    private var findQuery = ""
    private var findPattern: java.util.regex.Pattern? = null
    private var matchStarts: List<Int> = emptyList()
    private var matchEnds: List<Int> = emptyList()
    private var matchIndex = -1

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
        val s = text.toString()
        val p = buildFindPattern()
        findPattern = p
        val starts = ArrayList<Int>()
        val ends = ArrayList<Int>()
        if (p != null) {
            val m = p.matcher(s)
            while (m.find() && starts.size < 20000) {
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
        if (fromCursor && matchIndex >= 0) selectCurrentMatch()
        val invalid = findQuery.isNotEmpty() && p == null
        onFindResult?.invoke(matchIndex + 1, if (invalid) -1 else starts.size)
    }

    // Selects the current match and scrolls it into view
    private fun selectCurrentMatch() {
        if (matchIndex < 0 || matchIndex >= matchStarts.size) return
        val a = text.indexer.getCharPosition(matchStarts[matchIndex])
        val b = text.indexer.getCharPosition(matchEnds[matchIndex])
        setSelectionRegion(a.line, a.column, b.line, b.column)
    }

    fun findNext() = stepMatch(1)
    fun findPrev() = stepMatch(-1)

    private fun stepMatch(dir: Int) {
        if (matchStarts.isEmpty()) return
        matchIndex = (matchIndex + dir + matchStarts.size) % matchStarts.size
        selectCurrentMatch()
        onFindResult?.invoke(matchIndex + 1, matchStarts.size)
    }

    fun replaceCurrent(replacement: String) {
        val p = findPattern ?: return
        if (matchIndex < 0 || matchIndex >= matchStarts.size) return
        val s = matchStarts[matchIndex]
        val e = matchEnds[matchIndex]
        val found = text.subSequence(s, e).toString()
        val out = if (findRegex) {
            try { p.matcher(found).replaceFirst(replacement) } catch (ex: Exception) { replacement }
        } else replacement
        val a = text.indexer.getCharPosition(s)
        val b = text.indexer.getCharPosition(e)
        text.replace(a.line, a.column, b.line, b.column, out)
        setSelection(s + out.length)
        refreshMatches(fromCursor = true)
    }

    fun replaceAll(replacement: String): Int {
        val p = findPattern ?: return 0
        val m = p.matcher(text.toString())
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
        } catch (ex: Exception) {
            return 0
        }
        val end = text.indexer.getCharPosition(text.length)
        text.replace(0, 0, end.line, end.column, buf.toString())
        refreshMatches(fromCursor = true)
        return n
    }

    fun clearFind() {
        findQuery = ""
        findPattern = null
        matchStarts = emptyList()
        matchEnds = emptyList()
        matchIndex = -1
        onFindResult?.invoke(0, 0)
    }
}