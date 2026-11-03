package com.rams.editor

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import io.github.rosemoe.sora.lang.EmptyLanguage
import io.github.rosemoe.sora.lang.analysis.AnalyzeManager
import io.github.rosemoe.sora.lang.analysis.StyleReceiver
import io.github.rosemoe.sora.lang.styling.MappedSpans
import io.github.rosemoe.sora.lang.styling.Span
import io.github.rosemoe.sora.lang.styling.Styles
import io.github.rosemoe.sora.lang.styling.TextStyle
import io.github.rosemoe.sora.text.CharPosition
import io.github.rosemoe.sora.text.ContentReference
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

// Tells the editor how to color a file, using Rams Editor's own highlighter.
class RamsLanguage(fileName: String) : EmptyLanguage() {

    private val analyzer = RamsAnalyzeManager(fileName)

    override fun getAnalyzeManager(): AnalyzeManager = analyzer

    override fun destroy() {
        analyzer.destroy()
        super.destroy()
    }
}

class RamsAnalyzeManager(private val fileName: String) : AnalyzeManager {

    private companion object {
        // One shared background thread for all files
        val worker = Executors.newSingleThreadExecutor()

        // Remembers recent results so reopening a file shows colors at once
        val recent = object : LinkedHashMap<String, Pair<String, ByteArray>>(8, 0.75f, true) {
            override fun removeEldestEntry(
                eldest: MutableMap.MutableEntry<String, Pair<String, ByteArray>>?
            ): Boolean = size > 6
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

    private fun startAnalysis() {
        val ref = content ?: return
        val code = ref.reference.toString()
        val myVersion = version.incrementAndGet()
        val key = fileName + ":" + code.length

        val hit = synchronized(recent) { recent[key] }
        if (hit != null && hit.first == code) {
            receiver?.setStyles(this, buildStyles(code, hit.second))
            return
        }

        worker.execute {
            if (myVersion != version.get()) return@execute
            val kinds = SyntaxHighlighter.analyze(code, fileName)
            synchronized(recent) { recent[key] = Pair(code, kinds) }
            if (myVersion != version.get()) return@execute
            val styles = buildStyles(code, kinds)
            mainHandler.post {
                if (myVersion == version.get()) receiver?.setStyles(this, styles)
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