package com.rams.editor

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.File

// The Git screen: branch, changed files, commit, push and pull
class GitActivity : AppCompatActivity() {

    private lateinit var dir: File
    private lateinit var branchView: TextView
    private lateinit var remoteView: TextView
    private lateinit var changesTitle: TextView
    private lateinit var changeList: LinearLayout
    private lateinit var commitBox: View
    private lateinit var message: EditText
    private lateinit var statusView: TextView
    private lateinit var btnInit: Button
    private lateinit var btnCommit: Button
    private lateinit var btnPush: Button
    private lateinit var btnPull: Button
    private var currentRemote = ""

    private val prefs by lazy { getSharedPreferences("git", Context.MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_git)
        GitManager.init(this)

        val path = intent.getStringExtra("dir")
        if (path == null) {
            finish()
            return
        }
        dir = File(path)

        branchView = findViewById(R.id.gitBranch)
        remoteView = findViewById(R.id.gitRemote)
        changesTitle = findViewById(R.id.gitChangesTitle)
        changeList = findViewById(R.id.gitChangeList)
        commitBox = findViewById(R.id.gitCommitBox)
        message = findViewById(R.id.gitMessage)
        statusView = findViewById(R.id.gitStatus)
        btnInit = findViewById(R.id.gitInit)
        btnCommit = findViewById(R.id.gitCommit)
        btnPush = findViewById(R.id.gitPush)
        btnPull = findViewById(R.id.gitPull)

        findViewById<TextView>(R.id.gitBack).setOnClickListener { finish() }
        findViewById<TextView>(R.id.gitRefresh).setOnClickListener { refresh() }
        findViewById<TextView>(R.id.gitSettings).setOnClickListener { showCredentials() }

        btnInit.setOnClickListener {
            work("Creating repository…") {
                GitManager.initRepo(dir)
                "Repository created"
            }
        }
        btnCommit.setOnClickListener { doCommit() }
        btnPush.setOnClickListener { doPush() }
        btnPull.setOnClickListener { doPull() }

        refresh()
    }

    // Runs a Git job off the main thread, then shows its result and refreshes the screen
    private fun work(label: String, job: () -> String) {
        setBusy(true)
        statusView.text = label
        Thread {
            val result = try {
                job()
            } catch (e: Exception) {
                "Error: " + friendly(e)
            }
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                statusView.text = result
                setBusy(false)
                refresh()
            }
        }.start()
    }

    private fun setBusy(busy: Boolean) {
        btnInit.isEnabled = !busy
        btnCommit.isEnabled = !busy
        btnPush.isEnabled = !busy
        btnPull.isEnabled = !busy
    }

    private fun friendly(e: Throwable): String {
        val msg = e.message ?: e.javaClass.simpleName
        return when {
            msg.contains("not authorized", true) ||
                msg.contains("Authentication", true) ||
                msg.contains("401") || msg.contains("403") ->
                "Not allowed. Check your token (⚙) and that it can write to this repository."
            msg.contains("UnknownHost", true) ||
                msg.contains("Unable to resolve", true) ->
                "No connection."
            else -> msg
        }
    }

    private fun doCommit() {
        val msg = message.text.toString().trim()
        if (msg.isEmpty()) {
            toast("Write a commit message first")
            return
        }
        val name = (prefs.getString("name", "") ?: "").trim()
        val email = (prefs.getString("email", "") ?: "").trim()
        if (name.isEmpty() || email.isEmpty()) {
            toast("Set your name and email first")
            showCredentials()
            return
        }
        work("Committing…") {
            val r = GitManager.commitAll(dir, msg, name, email)
            if (r.startsWith("Committed")) runOnUiThread { message.setText("") }
            r
        }
    }

    private fun doPush() {
        work("Pushing…") {
            val token = (prefs.getString("token", "") ?: "").trim()
            when {
                token.isEmpty() -> "Add your GitHub token first (⚙)"
                GitManager.remoteUrl(dir).isBlank() -> "Add the repository URL first (⚙)"
                !GitManager.hasCommits(dir) -> "Commit something first"
                else -> GitManager.push(dir, token)
            }
        }
    }

    private fun doPull() {
        work("Pulling…") {
            val token = (prefs.getString("token", "") ?: "").trim()
            when {
                token.isEmpty() -> "Add your GitHub token first (⚙)"
                GitManager.remoteUrl(dir).isBlank() -> "Add the repository URL first (⚙)"
                !GitManager.hasCommits(dir) -> "Commit something first, then pull"
                else -> {
                    val r = GitManager.pull(dir, token)
                    setResult(RESULT_OK)
                    r
                }
            }
        }
    }

    // Reads branch, remote and changes in the background, then updates the screen
    private fun refresh() {
        Thread {
            val repo = GitManager.isRepo(dir)
            var branch = ""
            var remote = ""
            var changes: List<GitChange> = emptyList()
            var err: String? = null
            if (repo) {
                try {
                    branch = GitManager.branch(dir)
                    remote = GitManager.remoteUrl(dir)
                    changes = GitManager.changes(dir)
                } catch (e: Exception) {
                    err = friendly(e)
                }
            }
            runOnUiThread {
                if (!isDestroyed) show(repo, branch, remote, changes, err)
            }
        }.start()
    }

    private fun show(
        repo: Boolean,
        branch: String,
        remote: String,
        changes: List<GitChange>,
        err: String?
    ) {
        currentRemote = remote
        btnInit.visibility = if (repo) View.GONE else View.VISIBLE
        commitBox.visibility = if (repo) View.VISIBLE else View.GONE
        changesTitle.visibility = if (repo) View.VISIBLE else View.GONE
        changeList.removeAllViews()

        if (!repo) {
            branchView.text = "Not a Git repository yet"
            remoteView.text = "Tap Initialize repository to start tracking this project"
            return
        }

        branchView.text = "Branch: $branch"
        remoteView.text = if (remote.isBlank()) "No remote yet. Add one in ⚙" else remote
        changesTitle.text = "CHANGES (${changes.size})"
        if (err != null) statusView.text = "Error: $err"

        if (changes.isEmpty()) {
            changeList.addView(TextView(this).apply {
                text = "No changes"
                setTextColor(0xFF888888.toInt())
                setPadding(dp(16), dp(12), dp(16), dp(12))
            })
        }
        for (c in changes) changeList.addView(changeRow(c))
    }

    private fun changeRow(c: GitChange): View {
        val file = File(c.path)
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(7), dp(16), dp(7))
        }
        val badge = TextView(this).apply {
            text = c.kind.toString()
            textSize = 11f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setBackgroundColor(
                when (c.kind) {
                    'A' -> 0xFF2E7D32.toInt()
                    'M' -> 0xFF1565C0.toInt()
                    'D' -> 0xFFC62828.toInt()
                    else -> 0xFFEF6C00.toInt()
                }
            )
            layoutParams = LinearLayout.LayoutParams(dp(22), dp(22))
        }
        val texts = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
            ).apply { marginStart = dp(12) }
        }
        texts.addView(TextView(this).apply {
            text = file.name
            setTextColor(0xFFDDDDDD.toInt())
            textSize = 14f
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.MIDDLE
        })
        val parent = file.parent
        if (parent != null) {
            texts.addView(TextView(this).apply {
                text = parent
                setTextColor(0xFF888888.toInt())
                textSize = 11f
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.MIDDLE
            })
        }
        row.addView(badge)
        row.addView(texts)
        return row
    }

    private fun showCredentials() {
        fun field(hintText: String, value: String, secret: Boolean = false): EditText {
            val f = EditText(this)
            f.hint = hintText
            f.setText(value)
            f.setSingleLine()
            if (secret) {
                f.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
            return f
        }

        val urlF = field("Repository URL (https://github.com/you/repo.git)", currentRemote)
        val tokenF = field("GitHub token", prefs.getString("token", "") ?: "", true)
        val nameF = field("Your name (for commits)", prefs.getString("name", "") ?: "")
        val emailF = field("Your email (for commits)", prefs.getString("email", "") ?: "")
        val info = TextView(this).apply {
            text = "Make the token on GitHub: Settings, Developer settings, Personal access " +
                "tokens. Give it write access to your repository's contents. " +
                "It is kept only on this phone."
            textSize = 12f
            setPadding(0, dp(12), 0, 0)
        }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), 0)
            addView(urlF)
            addView(tokenF)
            addView(nameF)
            addView(emailF)
            addView(info)
        }

        AlertDialog.Builder(this)
            .setTitle("Git settings")
            .setView(box)
            .setPositiveButton("Save") { _, _ ->
                prefs.edit()
                    .putString("token", tokenF.text.toString().trim())
                    .putString("name", nameF.text.toString().trim())
                    .putString("email", emailF.text.toString().trim())
                    .apply()
                val url = urlF.text.toString().trim()
                Thread {
                    try {
                        if (GitManager.isRepo(dir)) GitManager.setRemote(dir, url)
                    } catch (e: Exception) {
                    }
                    runOnUiThread { if (!isDestroyed) refresh() }
                }.start()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
}