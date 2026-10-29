package com.rams.editor

import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder

// A tiny web server that serves one project folder. It only listens on this
// phone's own loopback address, so nothing else on the network can reach it.
object LocalServer {

    private var serverSocket: ServerSocket? = null
    private var root: File? = null
    var port = 0
        private set

    val isRunning: Boolean
        get() = serverSocket?.isClosed == false

    @Synchronized
    fun start(rootDir: File): Int {
        root = rootDir
        if (isRunning) return port

        for (p in 8080..8090) {
            try {
                val s = ServerSocket(p, 50, InetAddress.getByName("127.0.0.1"))
                serverSocket = s
                port = p
                Thread { acceptLoop(s) }.apply { isDaemon = true }.start()
                return p
            } catch (e: IOException) {
                // port busy, try the next one
            }
        }
        return -1
    }

    @Synchronized
    fun stop() {
        try {
            serverSocket?.close()
        } catch (e: IOException) {
        }
        serverSocket = null
    }

    private fun acceptLoop(s: ServerSocket) {
        while (!s.isClosed) {
            try {
                val client = s.accept()
                Thread { handle(client) }.apply { isDaemon = true }.start()
            } catch (e: IOException) {
                break
            }
        }
    }

    private fun handle(socket: Socket) {
        try {
            socket.use {
                it.soTimeout = 10000
                val reader = BufferedReader(
                    InputStreamReader(it.getInputStream(), Charsets.ISO_8859_1)
                )
                val requestLine = reader.readLine() ?: return
                // skip the request headers
                while (true) {
                    val line = reader.readLine() ?: break
                    if (line.isEmpty()) break
                }

                val parts = requestLine.split(" ")
                if (parts.size < 2) return
                val method = parts[0]
                if (method != "GET" && method != "HEAD") {
                    respond(it, 405, "Method Not Allowed", "text/plain", "405".toByteArray(), true)
                    return
                }

                val rawPath = parts[1].substringBefore('?').substringBefore('#')
                val path = try {
                    URLDecoder.decode(rawPath.replace("+", "%2B"), "UTF-8")
                } catch (e: Exception) {
                    rawPath
                }

                val base = root
                if (base == null) {
                    respond(it, 500, "Server Error", "text/plain", "No project".toByteArray(), method == "HEAD")
                    return
                }

                var file = File(base, path.trimStart('/'))
                if (file.isDirectory) file = File(file, "index.html")

                val canonBase = base.canonicalFile
                val canon = file.canonicalFile
                val inside = canon.path == canonBase.path ||
                    canon.path.startsWith(canonBase.path + File.separator)

                when {
                    !inside ->
                        respond(it, 403, "Forbidden", "text/plain", "403".toByteArray(), method == "HEAD")
                    !canon.isFile ->
                        respond(
                            it, 404, "Not Found", "text/plain",
                            "404 - ${canon.name} not found".toByteArray(), method == "HEAD"
                        )
                    else ->
                        respond(it, 200, "OK", mimeFor(canon), canon.readBytes(), method == "HEAD")
                }
            }
        } catch (e: Exception) {
            // ignore broken connections
        }
    }

    private fun respond(
        socket: Socket, code: Int, text: String,
        type: String, body: ByteArray, headOnly: Boolean
    ) {
        val header = "HTTP/1.1 $code $text\r\n" +
            "Content-Type: $type\r\n" +
            "Content-Length: ${body.size}\r\n" +
            "Cache-Control: no-store\r\n" +
            "Connection: close\r\n\r\n"
        val out = socket.getOutputStream()
        out.write(header.toByteArray(Charsets.ISO_8859_1))
        if (!headOnly) out.write(body)
        out.flush()
    }

    private fun mimeFor(file: File): String = when (file.extension.lowercase()) {
        "html", "htm" -> "text/html; charset=utf-8"
        "css" -> "text/css; charset=utf-8"
        "js", "mjs" -> "application/javascript; charset=utf-8"
        "json" -> "application/json; charset=utf-8"
        "txt", "md" -> "text/plain; charset=utf-8"
        "svg" -> "image/svg+xml"
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "ico" -> "image/x-icon"
        "woff" -> "font/woff"
        "woff2" -> "font/woff2"
        "ttf" -> "font/ttf"
        "otf" -> "font/otf"
        "mp3" -> "audio/mpeg"
        "mp4" -> "video/mp4"
        "webm" -> "video/webm"
        "xml" -> "application/xml"
        else -> "application/octet-stream"
    }
}