package com.rams.editor

import android.content.Context
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.MergeResult
import org.eclipse.jgit.transport.RefSpec
import org.eclipse.jgit.transport.RemoteRefUpdate
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import java.io.File

// One changed file. kind: 'A' new, 'M' modified, 'D' deleted, 'U' conflict
data class GitChange(val path: String, val kind: Char)

// All the Git work. Every function blocks, so call them off the main thread.
object GitManager {

    // JGit looks for a home folder for its settings. Android has none, so give it one.
    fun init(context: Context) {
        System.setProperty("user.home", context.filesDir.absolutePath)
    }

    fun isRepo(dir: File): Boolean = File(dir, ".git").isDirectory

    fun initRepo(dir: File) {
        Git.init().setDirectory(dir).setInitialBranch("main").call().close()
    }

    // Downloads a repository into a new folder. The token is only needed for private repos.
    fun clone(url: String, token: String, dest: File) {
        val cmd = Git.cloneRepository().setURI(url).setDirectory(dest)
        if (token.isNotBlank()) {
            cmd.setCredentialsProvider(UsernamePasswordCredentialsProvider("token", token))
        }
        cmd.call().close()
    }

    fun branch(dir: File): String =
        Git.open(dir).use { it.repository.branch ?: "" }

    fun hasCommits(dir: File): Boolean =
        Git.open(dir).use { it.repository.resolve("HEAD") != null }

    fun remoteUrl(dir: File): String =
        Git.open(dir).use { it.repository.config.getString("remote", "origin", "url") ?: "" }

    fun setRemote(dir: File, url: String) {
        Git.open(dir).use { git ->
            val cfg = git.repository.config
            if (url.isBlank()) {
                cfg.unsetSection("remote", "origin")
            } else {
                cfg.setString("remote", "origin", "url", url)
                cfg.setString("remote", "origin", "fetch", "+refs/heads/*:refs/remotes/origin/*")
            }
            cfg.save()
        }
    }

    // Files that changed since the last commit
    fun changes(dir: File): List<GitChange> = Git.open(dir).use { git ->
        val st = git.status().call()
        val out = ArrayList<GitChange>()
        st.conflicting.forEach { out.add(GitChange(it, 'U')) }
        (st.untracked + st.added).forEach { out.add(GitChange(it, 'A')) }
        (st.modified + st.changed).forEach { out.add(GitChange(it, 'M')) }
        (st.missing + st.removed).forEach { out.add(GitChange(it, 'D')) }
        out.distinctBy { it.path }.sortedBy { it.path }
    }

    // Stages everything (new, changed and deleted files) and commits it
    fun commitAll(dir: File, message: String, name: String, email: String): String =
        Git.open(dir).use { git ->
            if (git.status().call().isClean) return@use "Nothing to commit"
            git.add().addFilepattern(".").call()
            git.add().setUpdate(true).addFilepattern(".").call()
            val commit = git.commit()
                .setMessage(message)
                .setAuthor(name, email)
                .setCommitter(name, email)
                .call()
            "Committed " + commit.name.substring(0, 7)
        }

    fun push(dir: File, token: String): String = Git.open(dir).use { git ->
        val b = git.repository.branch
        val results = git.push()
            .setRemote("origin")
            .setRefSpecs(RefSpec("refs/heads/$b:refs/heads/$b"))
            .setCredentialsProvider(UsernamePasswordCredentialsProvider("token", token))
            .call()
        var msg = "Pushed to origin/$b"
        for (r in results) {
            for (u in r.remoteUpdates) {
                msg = when (u.status) {
                    RemoteRefUpdate.Status.OK -> "Pushed to origin/$b"
                    RemoteRefUpdate.Status.UP_TO_DATE -> "Already up to date"
                    RemoteRefUpdate.Status.REJECTED_NONFASTFORWARD ->
                        "Rejected: the remote has newer commits. Pull first."
                    else -> "Push failed: " + u.status + (u.message?.let { " ($it)" } ?: "")
                }
            }
        }
        msg
    }

    fun pull(dir: File, token: String): String = Git.open(dir).use { git ->
        val b = git.repository.branch
        val res = git.pull()
            .setRemote("origin")
            .setRemoteBranchName(b)
            .setCredentialsProvider(UsernamePasswordCredentialsProvider("token", token))
            .call()
        val merge = res.mergeResult
        when {
            merge != null && merge.mergeStatus == MergeResult.MergeStatus.ALREADY_UP_TO_DATE ->
                "Already up to date"
            merge != null && merge.mergeStatus == MergeResult.MergeStatus.CONFLICTING ->
                "Merge conflicts in " + (merge.conflicts?.size ?: 0) +
                    " file(s). Open them, fix the <<<<<<< markers, then commit."
            merge != null && merge.mergeStatus == MergeResult.MergeStatus.CHECKOUT_CONFLICT ->
                "Commit your changes first, then pull."
            !res.isSuccessful -> "Pull did not finish: " + (merge?.mergeStatus ?: "unknown")
            else -> "Pulled from origin/$b"
        }
    }
}