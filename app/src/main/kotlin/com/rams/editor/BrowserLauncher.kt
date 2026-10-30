package com.rams.editor

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import java.io.File

object BrowserLauncher {

    // Starts the local server for the project and opens the page in the chosen browser
    fun openPage(context: Context, rootPath: String?, relPath: String?) {
        if (rootPath == null || relPath == null) {
            Toast.makeText(context, "Open an HTML file first", Toast.LENGTH_SHORT).show()
            return
        }

        val port = LocalServer.start(File(rootPath))
        if (port < 0) {
            Toast.makeText(context, "Could not start the server", Toast.LENGTH_SHORT).show()
            return
        }

        val url = "http://127.0.0.1:$port/" + Uri.encode(relPath, "/")
        val view = Intent(Intent.ACTION_VIEW, Uri.parse(url))

        val chosen = context.getSharedPreferences("rams_settings", Context.MODE_PRIVATE)
            .getString("browser_package", null)
        if (chosen != null) {
            view.setPackage(chosen)
            try {
                context.startActivity(view)
                return
            } catch (e: Exception) {
                Toast.makeText(
                    context, "That browser isn't available, choose another",
                    Toast.LENGTH_SHORT
                ).show()
                view.setPackage(null)
            }
        }
        context.startActivity(Intent.createChooser(view, "Open with"))
    }
}