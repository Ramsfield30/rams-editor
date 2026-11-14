package com.rams.editor

import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.widget.PopupMenu
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

// The bottom key row: cursor arrows, Go to line, Find, Undo/Redo, line tools, Tab
class KeyRow(
    private val activity: AppCompatActivity,
    private val codeEditor: CodeEditor,
    private val onGoToLine: () -> Unit,
    private val onFind: () -> Unit
) {

    fun setup() {
        fun move(dx: Int, dy: Int) {
            val text = codeEditor.text ?: return
            val pos = codeEditor.selectionStart.coerceAtLeast(0)
            if (dy == 0) {
                codeEditor.setSelection((pos + dx).coerceIn(0, text.length))
                return
            }
            codeEditor.moveCursorLines(dy)
        }

        bindRepeat(activity.findViewById<View>(R.id.keyLeft)) { move(-1, 0) }
        bindRepeat(activity.findViewById<View>(R.id.keyRight)) { move(1, 0) }
        bindRepeat(activity.findViewById<View>(R.id.keyUp)) { move(0, -1) }
        bindRepeat(activity.findViewById<View>(R.id.keyDown)) { move(0, 1) }
        activity.findViewById<TextView>(R.id.keyGoto).setOnClickListener { onGoToLine() }
        onFind()
        activity.findViewById<TextView>(R.id.keyUndo).setOnClickListener { codeEditor.undo() }
        activity.findViewById<TextView>(R.id.keyRedo).setOnClickListener { codeEditor.redo() }

        activity.findViewById<TextView>(R.id.keyTools).setOnClickListener { anchor ->
            val menu = PopupMenu(activity, anchor)
            menu.menu.add(0, 1, 0, "Toggle comment")
            menu.menu.add(0, 2, 1, "Duplicate line")
            menu.menu.add(0, 3, 2, "Delete line")
            menu.menu.add(0, 4, 3, "Move line up")
            menu.menu.add(0, 5, 4, "Move line down")
            menu.menu.add(0, 6, 5, "Indent")
            menu.menu.add(0, 7, 6, "Outdent")
            menu.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    1 -> codeEditor.toggleComment()
                    2 -> codeEditor.duplicateLines()
                    3 -> codeEditor.deleteLines()
                    4 -> codeEditor.moveLinesUp()
                    5 -> codeEditor.moveLinesDown()
                    6 -> codeEditor.indentLines()
                    7 -> codeEditor.outdentLines()
                }
                true
            }
            menu.show()
        }

        activity.findViewById<TextView>(R.id.keyTab).setOnClickListener { codeEditor.tabKey() }
    }

    // One tap moves once. Holding the key repeats it.
    private fun bindRepeat(view: View, action: () -> Unit) {
        val handler = Handler(Looper.getMainLooper())
        val repeat = object : Runnable {
            override fun run() {
                action()
                handler.postDelayed(this, 70)
            }
        }
        view.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    v.isPressed = true
                    action()
                    handler.postDelayed(repeat, 400)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.isPressed = false
                    handler.removeCallbacks(repeat)
                }
            }
            true
        }
    }
}