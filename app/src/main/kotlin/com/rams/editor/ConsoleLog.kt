package com.rams.editor

import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebView

object ConsoleLog {

    data class Entry(val level: ConsoleMessage.MessageLevel, val text: String)

    const val FILTER_ALL = 0
    const val FILTER_INFO = 1
    const val FILTER_WARNING = 2
    const val FILTER_ERROR = 3

    val entries = mutableListOf<Entry>()
    var onChange: (() -> Unit)? = null

    fun add(level: ConsoleMessage.MessageLevel, text: String) {
        entries.add(Entry(level, text))
        if (entries.size > 500) entries.removeAt(0)
        onChange?.invoke()
    }

    fun clear() {
        entries.clear()
        onChange?.invoke()
    }

    private fun matches(e: Entry, filter: Int): Boolean = when (filter) {
        FILTER_INFO ->
            e.level != ConsoleMessage.MessageLevel.WARNING &&
                e.level != ConsoleMessage.MessageLevel.ERROR
        FILTER_WARNING -> e.level == ConsoleMessage.MessageLevel.WARNING
        FILTER_ERROR -> e.level == ConsoleMessage.MessageLevel.ERROR
        else -> true
    }

    private fun prefixFor(level: ConsoleMessage.MessageLevel): String = when (level) {
        ConsoleMessage.MessageLevel.ERROR -> "✖ "
        ConsoleMessage.MessageLevel.WARNING -> "⚠ "
        else -> ""
    }

    fun render(filter: Int = FILTER_ALL): SpannableStringBuilder {
        val sb = SpannableStringBuilder()
        for (e in entries.filter { matches(it, filter) }) {
            val color = when (e.level) {
                ConsoleMessage.MessageLevel.ERROR -> 0xFFF48771.toInt()
                ConsoleMessage.MessageLevel.WARNING -> 0xFFCCA700.toInt()
                else -> 0xFFD4D4D4.toInt()
            }
            val start = sb.length
            sb.append(prefixFor(e.level)).append(e.text).append("\n")
            sb.setSpan(ForegroundColorSpan(color), start, sb.length, 0)
        }
        return sb
    }

    fun plainText(filter: Int = FILTER_ALL): String =
        entries.filter { matches(it, filter) }
            .joinToString("\n") { prefixFor(it.level) + it.text }

    fun chromeClient(onTitle: ((String) -> Unit)? = null) = object : WebChromeClient() {
        override fun onConsoleMessage(m: ConsoleMessage): Boolean {
            add(m.messageLevel(), m.message())
            return true
        }

        override fun onReceivedTitle(view: WebView?, title: String?) {
            if (title != null &&
                !title.startsWith("data:") &&
                !title.startsWith("file:") &&
                !title.startsWith("about:")
            ) {
                onTitle?.invoke(title)
            }
        }
    }
}