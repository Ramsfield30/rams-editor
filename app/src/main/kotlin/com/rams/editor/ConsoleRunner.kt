package com.rams.editor

import android.webkit.ConsoleMessage
import android.webkit.WebView
import org.json.JSONObject
import org.json.JSONTokener

// Runs a line of JavaScript typed into the console, inside the previewed page.
object ConsoleRunner {

    private val history = ArrayList<String>()
    private var position = 0

    fun run(webView: WebView, command: String) {
        val text = command.trim()
        if (text.isEmpty()) return
        if (history.lastOrNull() != text) history.add(text)
        if (history.size > 50) history.removeAt(0)
        position = history.size

        ConsoleLog.add(ConsoleMessage.MessageLevel.LOG, "› $text")

        // Runs your line in the page's own scope and turns the answer into readable text
        val script = "(function(){try{var r=(0,eval)(" + JSONObject.quote(text) + ");" +
            "if(typeof r==='undefined')return 'undefined';" +
            "if(typeof r==='function')return String(r);" +
            "if(r&&r.nodeType===1)return r.outerHTML.slice(0,800);" +
            "if(typeof r==='object'&&r!==null){" +
            "try{return JSON.stringify(r,null,2).slice(0,3000)}catch(e){return String(r)}}" +
            "return String(r);" +
            "}catch(e){return '✖ '+e.name+': '+e.message}})()"

        webView.evaluateJavascript(script) { value ->
            val shown = try {
                JSONTokener(value ?: "null").nextValue()?.toString() ?: "null"
            } catch (e: Exception) {
                value ?: "null"
            }
            val level = if (shown.startsWith("✖ ")) ConsoleMessage.MessageLevel.ERROR
            else ConsoleMessage.MessageLevel.LOG
            ConsoleLog.add(level, shown)
        }
    }

    fun previous(): String? {
        if (history.isEmpty()) return null
        if (position > 0) position--
        return history[position]
    }

    fun next(): String? {
        if (history.isEmpty()) return null
        if (position < history.size - 1) {
            position++
            return history[position]
        }
        position = history.size
        return ""
    }
}