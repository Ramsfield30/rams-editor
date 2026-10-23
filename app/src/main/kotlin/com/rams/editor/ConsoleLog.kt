package com.rams.editor

import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebView

object ConsoleLog {

    data class Entry(val level: ConsoleMessage.MessageLevel, val text: String)

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

    fun render(): SpannableStringBuilder {
        val sb = SpannableStringBuilder()
        for (e in entries) {
            val (prefix, color) = when (e.level) {
                ConsoleMessage.MessageLevel.ERROR -> "✖ " to 0xFFF48771.toInt()
                ConsoleMessage.MessageLevel.WARNING -> "⚠ " to 0xFFCCA700.toInt()
                else -> "" to 0xFFD4D4D4.toInt()
            }
            val start = sb.length
            sb.append(prefix).append(e.text).append("\n")
            sb.setSpan(ForegroundColorSpan(color), start, sb.length, 0)
        }
        return sb
    }

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