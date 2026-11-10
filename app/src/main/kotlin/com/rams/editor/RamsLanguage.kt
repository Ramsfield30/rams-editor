package com.rams.editor

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import io.github.rosemoe.sora.lang.EmptyLanguage
import io.github.rosemoe.sora.lang.analysis.AnalyzeManager
import io.github.rosemoe.sora.lang.analysis.StyleReceiver
import io.github.rosemoe.sora.lang.completion.CompletionPublisher
import io.github.rosemoe.sora.lang.completion.SimpleCompletionItem
import io.github.rosemoe.sora.lang.smartEnter.NewlineHandleResult
import io.github.rosemoe.sora.lang.smartEnter.NewlineHandler
import io.github.rosemoe.sora.lang.styling.MappedSpans
import io.github.rosemoe.sora.lang.styling.Span
import io.github.rosemoe.sora.lang.styling.Styles
import io.github.rosemoe.sora.lang.styling.TextStyle
import io.github.rosemoe.sora.text.CharPosition
import io.github.rosemoe.sora.text.Content
import io.github.rosemoe.sora.text.ContentReference
import io.github.rosemoe.sora.widget.SymbolPairMatch
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

// Tells the editor how to color a file, using Rams Editor's own highlighter.
class RamsLanguage(fileName: String) : EmptyLanguage() {

    private val analyzer = RamsAnalyzeManager(fileName)

    // One set of bracket and quote pairs for this editor only (never share it between editors)
    private val pairs = SymbolPairMatch.DefaultSymbolPairs()

    private val newlineHandlers: Array<NewlineHandler> =
        arrayOf(BraceNewlineHandler(), TagNewlineHandler())

    override fun getSymbolPairs(): SymbolPairMatch = pairs

    override fun getNewlineHandlers(): Array<NewlineHandler> = newlineHandlers

    // The suggestion popup
    override fun requireAutoComplete(
        content: ContentReference,
        position: CharPosition,
        publisher: CompletionPublisher,
        extraArguments: Bundle
    ) {
        try {
            val whole = content.reference.toString()
            val end = position.index.coerceIn(0, whole.length)
            val result = Suggestions.suggest(analyzer.fileName, whole.substring(0, end), whole)
                ?: return
            for (item in Suggestions.top(result)) {
                publisher.addItem(
                    SimpleCompletionItem(
                        item.label, item.detail, result.prefix.length, item.commit
                    )
                )
            }
        } catch (t: Throwable) {
            CrashLog.note(t)
        }
    }

    override fun getAnalyzeManager(): AnalyzeManager = analyzer

    // Switch to another file without replacing the language (keeps the editor steady)
    fun setFile(name: String) {
        analyzer.fileName = name
    }

    override fun destroy() {
        analyzer.destroy()
        super.destroy()
    }
}

class RamsAnalyzeManager(var fileName: String) : AnalyzeManager {

    companion object {
        // One shared background thread for all files
        private val worker = Executors.newSingleThreadExecutor()

        // Remembers recent results so reopening a file shows colors at once
        private val recent = object : LinkedHashMap<String, Pair<String, ByteArray>>(8, 0.75f, true) {
            override fun removeEldestEntry(
                eldest: MutableMap.MutableEntry<String, Pair<String, ByteArray>>?
            ): Boolean = size > 6
        }

        // Called after new colors have been handed to the editor
        var onStylesApplied: (() -> Unit)? = null

        // Work out the colors now, so they are ready the moment the text appears
        fun prime(fileName: String, code: String) {
            val kinds = SyntaxHighlighter.analyze(code, fileName)
            synchronized(recent) { recent[fileName + ":" + code.length] = Pair(code, kinds) }
        }
    }

    private var receiver: StyleReceiver? = null
    private var content: ContentReference? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val version = AtomicInteger(0)

    private val rerunTask = Runnable { startAnalysis() }

    override fun setReceiver(receiver: StyleReceiver?) {
        this.receiver = receiver
    }

    override fun reset(content: ContentReference, extraArguments: Bundle) {
        this.content = content
        schedule(0L)
    }

    override fun insert(start: CharPosition, end: CharPosition, insertedContent: CharSequence) {
        schedule(250L)
    }

    override fun delete(start: CharPosition, end: CharPosition, deletedContent: CharSequence) {
        schedule(250L)
    }

    override fun rerun() {
        schedule(0L)
    }

    override fun destroy() {
        version.incrementAndGet()
        mainHandler.removeCallbacks(rerunTask)
        receiver = null
        content = null
    }

    private fun schedule(delayMs: Long) {
        mainHandler.removeCallbacks(rerunTask)
        mainHandler.postDelayed(rerunTask, delayMs)
    }

    private fun applied() {
        // Posted after the editor's own queued update, so the editor is colored when this runs
        mainHandler.post { onStylesApplied?.invoke() }
    }

    private fun startAnalysis() {
        val ref = content ?: return
        val code = ref.reference.toString()
        val name = fileName
        val myVersion = version.incrementAndGet()
        val key = name + ":" + code.length

        val hit = synchronized(recent) { recent[key] }
        if (hit != null && hit.first == code) {
            try {
                receiver?.setStyles(this, buildStyles(code, hit.second))
                applied()
            } catch (t: Throwable) {
                CrashLog.note(t)
            }
            return
        }

        worker.execute {
            try {
                if (myVersion != version.get()) return@execute
                val kinds = SyntaxHighlighter.analyze(code, name)
                synchronized(recent) { recent[key] = Pair(code, kinds) }
                if (myVersion != version.get()) return@execute
                val styles = buildStyles(code, kinds)
                mainHandler.post {
                    try {
                        if (myVersion == version.get()) {
                            receiver?.setStyles(this, styles)
                            applied()
                        }
                    } catch (t: Throwable) {
                        CrashLog.note(t)
                    }
                }
            } catch (t: Throwable) {
                CrashLog.note(t)
            }
        }
    }

    private fun styleFor(kind: Int): Long {
        val colorId = if (kind == 0) EditorColorScheme.TEXT_NORMAL
        else SyntaxHighlighter.COLOR_BASE + kind
        return TextStyle.makeStyle(colorId)
    }

    // Turns "one color per character" into the line-by-line spans the editor wants
    private fun buildStyles(code: String, kinds: ByteArray): Styles {
        val builder = MappedSpans.Builder()
        var line = 0
        var col = 0
        var prev = -1
        for (i in code.indices) {
            if (code[i] == '\n') {
                if (col == 0) builder.add(line, Span.obtain(0, styleFor(0)))
                line++
                col = 0
                prev = -1
                continue
            }
            val k = kinds[i].toInt()
            if (col == 0 || k != prev) {
                builder.add(line, Span.obtain(col, styleFor(k)))
                prev = k
            }
            col++
        }
        if (col == 0) builder.add(line, Span.obtain(0, styleFor(0)))
        builder.determine(line)
        return Styles(builder.build())
    }
}

// Pressing Enter right after { [ or (  -> indent the next line.
// If the matching closer is already there, put it on its own line below.
private class BraceNewlineHandler : NewlineHandler {

    override fun matchesRequirement(text: Content, position: CharPosition, style: Styles?): Boolean {
        val line = text.getLine(position.line).toString()
        val col = position.column
        if (col <= 0 || col > line.length) return false
        val before = line[col - 1]
        return before == '{' || before == '[' || before == '('
    }

    override fun handleNewline(
        text: Content, position: CharPosition, style: Styles?, tabSize: Int
    ): NewlineHandleResult {
        val line = text.getLine(position.line).toString()
        val col = position.column
        val indent = line.takeWhile { it == ' ' || it == '\t' }
        val unit = " ".repeat(if (tabSize > 0) tabSize else 4)
        val before = line[col - 1]
        val after = if (col < line.length) line[col] else ' '
        val closer = when (before) {
            '{' -> '}'
            '[' -> ']'
            else -> ')'
        }
        val sb = StringBuilder()
        sb.append("\n").append(indent).append(unit)
        if (after == closer) {
            val tail = "\n" + indent
            sb.append(tail)
            return NewlineHandleResult(sb, tail.length)
        }
        return NewlineHandleResult(sb, 0)
    }
}

// Pressing Enter right after an opening HTML tag -> indent the next line.
// If the closing tag is already there, put it on its own line below.
private class TagNewlineHandler : NewlineHandler {

    private val voidTags = setOf(
        "br", "hr", "img", "input", "meta", "link", "area", "base",
        "col", "embed", "param", "source", "track", "wbr"
    )
    private val openTag = Regex("<([a-zA-Z][a-zA-Z0-9-]*)(?:\\s[^<>]*)?>$")

    private fun openedTag(before: String): String? {
        if (before.endsWith("/>")) return null
        val m = openTag.find(before) ?: return null
        val name = m.groupValues[1].lowercase()
        return if (name in voidTags) null else name
    }

    override fun matchesRequirement(text: Content, position: CharPosition, style: Styles?): Boolean {
        val line = text.getLine(position.line).toString()
        val col = position.column
        if (col <= 0 || col > line.length) return false
        return openedTag(line.substring(0, col)) != null
    }

    override fun handleNewline(
        text: Content, position: CharPosition, style: Styles?, tabSize: Int
    ): NewlineHandleResult {
        val line = text.getLine(position.line).toString()
        val col = position.column
        val indent = line.takeWhile { it == ' ' || it == '\t' }
        val unit = " ".repeat(if (tabSize > 0) tabSize else 4)
        val sb = StringBuilder()
        sb.append("\n").append(indent).append(unit)
        if (line.substring(col).startsWith("</")) {
            val tail = "\n" + indent
            sb.append(tail)
            return NewlineHandleResult(sb, tail.length)
        }
        return NewlineHandleResult(sb, 0)
    }
}