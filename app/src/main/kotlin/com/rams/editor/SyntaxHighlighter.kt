package com.rams.editor

import android.graphics.Color
import android.text.SpannableStringBuilder

// Colors for the editor. The work itself is done by FastHighlighter.
object SyntaxHighlighter {

    // Editor color id = COLOR_BASE + color number
    const val COLOR_BASE = 255

    // Index = color number. Colors sampled from Spck screenshots.
    val PALETTE: IntArray = intArrayOf(
        0,
        Color.parseColor("#7C8CB4"), // 1 comment
        Color.parseColor("#9C9C9C"), // 2 bracket
        Color.parseColor("#52AAF8"), // 3 blue
        Color.parseColor("#ABF1F9"), // 4 cyan
        Color.parseColor("#F3FB8C"), // 5 yellow
        Color.parseColor("#D3A4FA"), // 6 purple
        Color.parseColor("#BB94FA"), // 7 violet
        Color.parseColor("#F8F8D0"), // 8 cream
        Color.parseColor("#1CDCC4"), // 9 teal
        Color.parseColor("#A4FABC"), // 10 mint
        Color.parseColor("#C4ECD4")  // 11 pale mint
    )

    // Kept so older code keeps compiling
    fun setFileName(name: String) {}

    fun build(code: String, name: String): SpannableStringBuilder = SpannableStringBuilder(code)

    // One color number per character. If anything goes wrong, show plain text.
    fun analyze(code: String, name: String): ByteArray = try {
        FastHighlighter.analyze(code, name)
    } catch (t: Throwable) {
        CrashLog.note(t)
        ByteArray(code.length)
    }
}