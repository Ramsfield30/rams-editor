package com.rams.editor

import android.content.Context
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView

class SnippetBar(
    private val context: Context,
    private val row1: LinearLayout,
    private val row2: LinearLayout,
    private val onInsert: (String, Int) -> Unit
) {

    var tabSize = 4

    // Fixed symbols — always shown in row 2
    private val fixedSnippets = listOf(
        "<" to "<",
        ">" to ">",
        "</>" to "</>",
        "=\"\"" to "=\"\"",
        "{}" to "{}",
        "[]" to "[]",
        "()" to "()",
        ";" to ";",
        "→|" to "    "
    )

    // Context snippets for row 1
    private val htmlSnippets = listOf(
        "<div>" to "<div></div>",
        "<p>" to "<p></p>",
        "<span>" to "<span></span>",
        "<a>" to "<a href=\"\"></a>",
        "<img>" to "<img src=\"\" alt=\"\">",
        "<input>" to "<input type=\"text\">",
        "<button>" to "<button></button>",
        "<h1>" to "<h1></h1>",
        "<ul>" to "<ul>\n    <li></li>\n</ul>",
        "<form>" to "<form>\n    \n</form>"
    )

    private val cssSnippets = listOf(
        "margin:" to "margin: ;",
        "padding:" to "padding: ;",
        "color:" to "color: ;",
        "background:" to "background: ;",
        "display:" to "display: ;",
        "flex" to "display: flex;",
        "font-size:" to "font-size: ;",
        "border:" to "border: ;",
        "width:" to "width: ;",
        "height:" to "height: ;"
    )

    private val jsSnippets = listOf(
        "const" to "const  = ",
        "let" to "let  = ",
        "function" to "function name() {\n    \n}",
        "=>" to "() => {\n    \n}",
        "console.log" to "console.log()",
        "if" to "if () {\n    \n}",
        "for" to "for (let i = 0; i < ; i++) {\n    \n}",
        "document" to "document.",
        "addEventListener" to "addEventListener('', () => {\n    \n})",
        "querySelector" to "querySelector('')"
    )

    init {
        setupFixedRow()
        setMode("html") // default
    }

    fun setMode(fileExtension: String) {
        row1.removeAllViews()
        val snippets = when (fileExtension) {
            "css" -> cssSnippets
            "js" -> jsSnippets
            else -> htmlSnippets
        }
        snippets.forEach { (label, code) ->
            addChip(row1, label, code)
        }
    }

    private fun setupFixedRow() {
        row2.removeAllViews()
        fixedSnippets.forEach { (label, code) ->
            addChip(row2, label, code)
        }
    }

    private fun addChip(container: LinearLayout, label: String, code: String) {
        val chip = TextView(context).apply {
            text = label
            textSize = 13f
            setTextColor(android.graphics.Color.parseColor("#CCCCCC"))
            setPadding(20, 0, 20, 0)
            gravity = android.view.Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
            setBackgroundResource(android.R.drawable.btn_default_small)
            background = android.graphics.drawable.ColorDrawable(
                android.graphics.Color.TRANSPARENT
            )
            setOnClickListener {
                val finalCode = if (label == "→|") " ".repeat(tabSize) else code
                onInsert(finalCode, getCursorOffset(finalCode))
            }
        }

        // Divider between chips
        val divider = android.view.View(context).apply {
            layoutParams = LinearLayout.LayoutParams(1, LinearLayout.LayoutParams.MATCH_PARENT)
            setBackgroundColor(android.graphics.Color.parseColor("#333333"))
        }

        container.addView(chip)
        container.addView(divider)
    }

    private fun getCursorOffset(code: String): Int {
        // Position cursor smartly inside the inserted snippet
        return when {
            code.contains("()") -> code.indexOf("()") + 1
            code.contains("\"\"") -> code.indexOf("\"\"") + 1
            code.contains("></") -> code.indexOf("></")
            code.endsWith(";") && code.contains(": ") -> code.length - 1
            else -> code.length
        }
    }
}