package com.rams.editor

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

// Checks GitHub Releases for a newer build, downloads it and starts the install
object Updater {

    private const val REPO = "Ramsfield30/rams-editor"
    private const val CHECK_EVERY_MS = 12L * 60L * 60L * 1000L

    private class Release(
        val build: Int,
        val name: String,
        val apkUrl: String,
        val size: Long
    )

    private val main = Handler(Looper.getMainLooper())

    @Suppress("DEPRECATION")
    fun currentBuild(context: Context): Int {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        return if (Build.VERSION.SDK_INT >= 28) info.longVersionCode.toInt() else info.versionCode
    }

    fun currentVersionName(context: Context): String =
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""

    private fun token(context: Context): String =
        (context.getSharedPreferences("rams_settings", Context.MODE_PRIVATE)
            .getString("git_token", "") ?: "").trim()

    // From Settings: always tells you the result
    fun checkNow(activity: Activity) {
        Toast.makeText(activity, "Checking for updates…", Toast.LENGTH_SHORT).show()
        runCheck(activity, quiet = false)
    }

    // On app start: only speaks up when there is an update, at most twice a day
    fun checkOnStart(activity: Activity) {
        val prefs = activity.getSharedPreferences("updater", Context.MODE_PRIVATE)
        val last = prefs.getLong("lastCheck", 0L)
        if (System.currentTimeMillis() - last < CHECK_EVERY_MS) return
        runCheck(activity, quiet = true)
    }

    private fun runCheck(activity: Activity, quiet: Boolean) {
        Thread {
            try {
                val rel = fetchLatest(activity)
                activity.getSharedPreferences("updater", Context.MODE_PRIVATE).edit()
                    .putLong("lastCheck", System.currentTimeMillis()).apply()
                main.post {
                    if (activity.isFinishing || activity.isDestroyed) return@post
                    if (rel.build > currentBuild(activity)) {
                        showUpdateDialog(activity, rel)
                    } else if (!quiet) {
                        Toast.makeText(
                            activity,
                            "You have the latest version (" + currentVersionName(activity) + ")",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            } catch (e: Exception) {
                if (!quiet) {
                    main.post {
                        Toast.makeText(
                            activity,
                            "Couldn't check for updates: " + (e.message ?: "unknown error"),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }.start()
    }

    // Asks GitHub for the newest release. Runs off the main thread.
    private fun fetchLatest(context: Context): Release {
        val conn = URL("https://api.github.com/repos/$REPO/releases/latest")
            .openConnection() as HttpURLConnection
        conn.connectTimeout = 15000
        conn.readTimeout = 15000
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        val t = token(context)
        if (t.isNotEmpty()) conn.setRequestProperty("Authorization", "Bearer $t")
        try {
            if (conn.responseCode != 200) throw Exception("GitHub answered " + conn.responseCode)
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val tag = json.getString("tag_name")
            val build = Regex("\\d+").findAll(tag).lastOrNull()?.value?.toIntOrNull()
                ?: throw Exception("Unknown release tag: $tag")
            val assets = json.getJSONArray("assets")
            var apkUrl = ""
            var size = 0L
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.getString("name").endsWith(".apk")) {
                    apkUrl = a.getString("url")
                    size = a.optLong("size")
                    break
                }
            }
            if (apkUrl.isEmpty()) throw Exception("This release has no APK")
            return Release(build, json.optString("name", tag), apkUrl, size)
        } finally {
            conn.disconnect()
        }
    }

    private fun showUpdateDialog(activity: Activity, rel: Release) {
        val mb = String.format(Locale.US, "%.1f MB", rel.size / 1048576.0)
        AlertDialog.Builder(activity)
            .setTitle("Update available")
            .setMessage(rel.name + " is ready (" + mb + ").\nYou have " +
                currentVersionName(activity) + ".")
            .setPositiveButton("Update") { _, _ -> download(activity, rel) }
            .setNegativeButton("Later", null)
            .show()
    }

    private fun download(activity: Activity, rel: Release) {
        // Android asks once for permission to install apps from this app
        if (Build.VERSION.SDK_INT >= 26 && !activity.packageManager.canRequestPackageInstalls()) {
            AlertDialog.Builder(activity)
                .setTitle("Allow installing")
                .setMessage("Android needs your permission to install updates from Rams Editor. " +
                    "Turn it on, come back, and tap Update again.")
                .setPositiveButton("Open settings") { _, _ ->
                    activity.startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:" + activity.packageName)
                        )
                    )
                }
                .setNegativeButton("Cancel", null)
                .show()
            return
        }

        val dir = File(activity.getExternalFilesDir(null), "updates")
        dir.mkdirs()
        dir.listFiles()?.forEach { it.delete() } // old downloads
        val file = File(dir, "rams-editor-" + rel.build + ".apk")

        val dialog = AlertDialog.Builder(activity)
            .setTitle("Downloading update")
            .setMessage("Starting…")
            .setCancelable(false)
            .show()
        val t = token(activity)

        Thread {
            try {
                val conn = URL(rel.apkUrl).openConnection() as HttpURLConnection
                conn.connectTimeout = 15000
                conn.readTimeout = 30000
                conn.setRequestProperty("Accept", "application/octet-stream")
                if (t.isNotEmpty()) conn.setRequestProperty("Authorization", "Bearer $t")
                if (conn.responseCode != 200) {
                    throw Exception("Download failed (" + conn.responseCode + ")")
                }
                val total = if (rel.size > 0) rel.size else conn.contentLengthLong
                var done = 0L
                var lastPct = -1
                conn.inputStream.use { input ->
                    file.outputStream().use { out ->
                        val buf = ByteArray(32 * 1024)
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            done += n
                            if (total > 0) {
                                val pct = (done * 100 / total).toInt()
                                if (pct != lastPct) {
                                    lastPct = pct
                                    main.post { dialog.setMessage("$pct%") }
                                }
                            }
                        }
                    }
                }
                conn.disconnect()
                main.post {
                    dialog.dismiss()
                    install(activity, file)
                }
            } catch (e: Exception) {
                file.delete()
                main.post {
                    dialog.dismiss()
                    Toast.makeText(
                        activity,
                        "Update failed: " + (e.message ?: "unknown error"),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }.start()
    }

    private fun install(activity: Activity, file: File) {
        val uri = FileProvider.getUriForFile(activity, activity.packageName + ".fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        activity.startActivity(intent)
    }
}