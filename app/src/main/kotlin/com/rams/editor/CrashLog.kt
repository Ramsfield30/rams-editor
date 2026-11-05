package com.rams.editor

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

// Saves crash reports on the phone so you can read them without a computer.
object CrashLog {
    private const val CRASH_FILE = "last_crash.txt"
    private const val NOTE_FILE = "last_problem.txt"
    private var appContext: Context? = null

    fun install(context: Context) {
        val app = context.applicationContext
        appContext = app
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                File(app.filesDir, CRASH_FILE)
                    .writeText("Thread: ${thread.name}\n" + trace(error))
            } catch (e: Throwable) {
            }
            previous?.uncaughtException(thread, error)
        }
    }

    // A problem that did not crash the app
    fun note(error: Throwable) {
        val ctx = appContext ?: return
        try {
            File(ctx.filesDir, NOTE_FILE).writeText(trace(error))
        } catch (e: Throwable) {
        }
    }

    fun hasCrash(context: Context): Boolean =
        File(context.filesDir, CRASH_FILE).exists()

    fun showIfAny(activity: AppCompatActivity) {
        val crash = File(activity.filesDir, CRASH_FILE)
        val note = File(activity.filesDir, NOTE_FILE)
        val file = when {
            crash.exists() -> crash
            note.exists() -> note
            else -> return
        }
        val title = if (file == crash) "Rams Editor crashed last time"
        else "Rams Editor had a hidden problem"
        val text = file.readText()
        AlertDialog.Builder(activity)
            .setTitle(title)
            .setMessage(text.take(3000))
            .setPositiveButton("Copy") { _, _ ->
                val cm = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("report", text))
                file.delete()
            }
            .setNegativeButton("Close") { _, _ -> file.delete() }
            .setCancelable(false)
            .show()
    }

    private fun trace(error: Throwable): String {
        val sw = StringWriter()
        error.printStackTrace(PrintWriter(sw))
        return sw.toString()
    }
}