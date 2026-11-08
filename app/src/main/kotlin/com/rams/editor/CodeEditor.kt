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

    private var currentFileName = ""

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
        currentFileName = name
        val lang = currentLanguage
        if (lang != null) {
            lang.setFile(name)
        } else {
            val created = RamsLanguage(name)
            currentLanguage = created
            setEditorLanguage(created)
        }
    }

    // Typing > after an opening tag adds the closing tag, like <div> -> <div></div>
    override fun commitText(text: CharSequence?, applyAutoIndent: Boolean) {
        super.commitText(text, applyAutoIndent)
        if (autoCloseEnabled && text != null && text.length in 1..30 &&
            !text.contains('\n') && text[text.length - 1] == '>'
        ) {
            closeTagAtCursor()
        }
    }

    private val openTagRegex = Regex("<([a-zA-Z][a-zA-Z0-9-]*)(?:\\s[^<>]*)?>$")
    private val voidTagNames = setOf(
        "br", "hr", "img", "input", "meta", "link", "area", "base",
        "col", "embed", "param", "source", "track", "wbr"
    )

    private fun closeTagAtCursor() {
        val lineIndex = cursor.leftLine
        val col = cursor.leftColumn
        val line = this.text.getLine(lineIndex).toString()
        if (col <= 0 || col > line.length) return
        val before = line.substring(0, col)
        if (before.endsWith("/>")) return
        val name = openTagRegex.find(before)?.groupValues?.get(1) ?: return
        if (name.lowercase() in voidTagNames) return
        if (line.substring(col).startsWith("</$name")) return
        this.text.insert(lineIndex, col, "</$name>")
        setSelection(lineIndex, col)
    }

    // ---- Line tools ----
    private fun lineText(i: Int): String = text.getLine(i).toString()

    private fun hasSelection(): Boolean = cursor.left != cursor.right

    // The first and last line touched by the cursor or the selection
    private fun selectedLines(): Pair<Int, Int> {
        var a = cursor.leftLine
        var b = cursor.rightLine
        if (a > b) {
            val t = a
            a = b
            b = t
        }
        if (a != b && cursor.rightColumn == 0) b--
        return Pair(a, b)
    }

    private fun replaceLines(l1: Int, l2: Int, newText: String) {
        text.replace(l1, 0, l2, lineText(l2).length, newText)
    }

    private fun commentKind(): Int =
        when (currentFileName.substringAfterLast('.', "").lowercase()) {
            "html", "htm", "xml", "svg" -> 1
            "css" -> 2
            else -> 0
        }

    private fun isCommented(line: String, kind: Int): Boolean {
        val t = line.trim()
        return when (kind) {
            1 -> t.startsWith("<!--") && t.endsWith("-->")
            2 -> t.startsWith("/*") && t.endsWith("*/")
            else -> t.startsWith("//")
        }
    }

    private fun commentLine(line: String, kind: Int, indent: Int): String {
        val head = line.substring(0, indent)
        val rest = line.substring(indent)
        return when (kind) {
            1 -> "$head<!-- $rest -->"
            2 -> "$head/* $rest */"
            else -> "$head// $rest"
        }
    }

    private fun uncommentLine(line: String, kind: Int): String {
        val lead = line.length - line.trimStart().length
        val head = line.substring(0, lead)
        var rest = line.substring(lead)
        when (kind) {
            1 -> {
                rest = rest.removePrefix("<!--").removeSuffix("-->")
                rest = rest.removePrefix(" ").removeSuffix(" ")
            }
            2 -> {
                rest = rest.removePrefix("/*").removeSuffix("*/")
                rest = rest.removePrefix(" ").removeSuffix(" ")
            }
            else -> rest = rest.removePrefix("//").removePrefix(" ")
        }
        return head + rest
    }

    fun toggleComment() {
        val (l1, l2) = selectedLines()
        val kind = commentKind()
        val lines = (l1..l2).map { lineText(it) }
        val filled = lines.filter { it.isNotBlank() }
        if (filled.isEmpty()) return
        val allCommented = filled.all { isCommented(it, kind) }
        val indent = filled.minOf { it.length - it.trimStart().length }
        val sel = hasSelection()
        val col = cursor.leftColumn
        val oldLen = lines.first().length
        val out = lines.map { line ->
            when {
                line.isBlank() -> line
                allCommented -> uncommentLine(line, kind)
                else -> commentLine(line, kind, indent)
            }
        }
        replaceLines(l1, l2, out.joinToString("\n"))
        if (sel) {
            setSelectionRegion(l1, 0, l2, lineText(l2).length)
        } else {
            val newLen = lineText(l1).length
            setSelection(l1, (col + newLen - oldLen).coerceIn(0, newLen))
        }
    }

    fun indentLines() {
        val (l1, l2) = selectedLines()
        val unit = " ".repeat(tabSize)
        val sel = hasSelection()
        val col = cursor.leftColumn
        val out = (l1..l2).joinToString("\n") { i ->
            val s = lineText(i)
            if (s.isEmpty()) s else unit + s
        }
        replaceLines(l1, l2, out)
        if (sel) setSelectionRegion(l1, 0, l2, lineText(l2).length)
        else setSelection(l1, col + unit.length)
    }

    fun outdentLines() {
        val (l1, l2) = selectedLines()
        val sel = hasSelection()
        val col = cursor.leftColumn
        var removedOnFirst = 0
        val out = (l1..l2).joinToString("\n") { i ->
            val s = lineText(i)
            var n = 0
            while (n < tabSize && n < s.length && s[n] == ' ') n++
            if (n == 0 && s.startsWith("\t")) n = 1
            if (i == l1) removedOnFirst = n
            s.substring(n)
        }
        replaceLines(l1, l2, out)
        if (sel) setSelectionRegion(l1, 0, l2, lineText(l2).length)
        else setSelection(l1, (col - removedOnFirst).coerceAtLeast(0))
    }

    fun duplicateLines() {
        val (l1, l2) = selectedLines()
        val block = (l1..l2).joinToString("\n") { lineText(it) }
        val cl = cursor.leftLine
        val cc = cursor.leftColumn
        text.insert(l2, lineText(l2).length, "\n" + block)
        setSelection(cl + (l2 - l1 + 1), cc)
    }

    fun deleteLines() {
        val (l1, l2) = selectedLines()
        val last = text.lineCount - 1
        if (l2 < last) {
            text.replace(l1, 0, l2 + 1, 0, "")
            setSelection(l1.coerceAtMost(text.lineCount - 1), 0)
        } else if (l1 > 0) {
            val prevLen = lineText(l1 - 1).length
            text.replace(l1 - 1, prevLen, l2, lineText(l2).length, "")
            setSelection(l1 - 1, prevLen)
        } else {
            text.replace(0, 0, l2, lineText(l2).length, "")
            setSelection(0, 0)
        }
    }

    fun moveLinesUp() {
        val (l1, l2) = selectedLines()
        if (l1 == 0) return
        val prev = lineText(l1 - 1)
        val block = (l1..l2).joinToString("\n") { lineText(it) }
        val sel = hasSelection()
        val cl = cursor.leftLine
        val cc = cursor.leftColumn
        val rl = cursor.rightLine
        val rc = cursor.rightColumn
        text.replace(l1 - 1, 0, l2, lineText(l2).length, block + "\n" + prev)
        if (sel) setSelectionRegion(cl - 1, cc, rl - 1, rc) else setSelection(cl - 1, cc)
    }

    fun moveLinesDown() {
        val (l1, l2) = selectedLines()
        if (l2 >= text.lineCount - 1) return
        val next = lineText(l2 + 1)
        val block = (l1..l2).joinToString("\n") { lineText(it) }
        val sel = hasSelection()
        val cl = cursor.leftLine
        val cc = cursor.leftColumn
        val rl = cursor.rightLine
        val rc = cursor.rightColumn
        text.replace(l1, 0, l2 + 1, next.length, next + "\n" + block)
        if (sel) setSelectionRegion(cl + 1, cc, rl + 1, rc) else setSelection(cl + 1, cc)
    }

    // ---- Emmet ----
    private fun cursorInBlock(tag: String): Boolean {
        val before = text.toString().substring(0, cursor.left.coerceAtMost(text.length))
        val open = before.lastIndexOf("<$tag", ignoreCase = true)
        if (open < 0) return false
        return before.lastIndexOf("</$tag", ignoreCase = true) < open
    }

    // Turns the abbreviation before the cursor into code. Returns false if there is none.
    fun expandEmmet(): Boolean {
        if (hasSelection()) return false
        val ext = currentFileName.substringAfterLast('.', "").lowercase()
        val htmlFile = ext == "html" || ext == "htm" || ext == "xml" || ext == "svg" || ext == "php"
        val cssFile = ext == "css"
        if (!htmlFile && !cssFile) return false
        val css = cssFile || cursorInBlock("style")
        if (!css && cursorInBlock("script")) return false

        val line = cursor.leftLine
        val col = cursor.leftColumn
        val lineStr = lineText(line)
        if (col <= 0 || col > lineStr.length) return false
        val abbr = Emmet.extract(lineStr.substring(0, col), css) ?: return false
        val baseIndent = lineStr.takeWhile { it == ' ' || it == '\t' }
        val result = Emmet.expand(abbr, css, " ".repeat(tabSize), baseIndent) ?: return false

        val startCol = col - abbr.length
        text.replace(line, startCol, line, col, result.text)

        // Put the cursor where the abbreviation says (inside the first empty spot)
        val before = result.text.substring(0, result.cursor)
        val newlines = before.count { it == '\n' }
        val targetLine = line + newlines
        val targetCol = if (newlines == 0) startCol + before.length
        else before.length - before.lastIndexOf('\n') - 1
        setSelection(targetLine, targetCol)
        return true
    }

    // The ↹ key: expand an abbreviation, otherwise indent
    fun tabKey() {
        if (expandEmmet()) return
        if (hasSelection() && cursor.leftLine != cursor.rightLine) {
            indentLines()
            return
        }
        val l = cursor.leftLine
        val c = cursor.leftColumn
        val spaces = " ".repeat(tabSize)
        text.insert(l, c, spaces)
        setSelection(l, c + spaces.length)
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
        // Remember which box you were typing in, so the editor doesn't keep the cursor
        val typingIn = activityOrNull()?.currentFocus
        val a = text.indexer.getCharPosition(matchStarts[matchIndex])
        val b = text.indexer.getCharPosition(matchEnds[matchIndex])
        setSelectionRegion(a.line, a.column, b.line, b.column)
        if (typingIn is android.widget.EditText) {
            typingIn.requestFocus()
            typingIn.post { typingIn.requestFocus() }
        }
    }

    private fun activityOrNull(): android.app.Activity? {
        var c: Context? = context
        while (c is android.content.ContextWrapper) {
            if (c is android.app.Activity) return c
            c = c.baseContext
        }
        return null
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