package com.rams.editor

import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient

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

    fun chromeClient() = object : WebChromeClient() {
        override fun onConsoleMessage(m: ConsoleMessage): Boolean {
            add(m.messageLevel(), m.message())
            return true
        }
    }
}