package com.ganim.scanfire.transfer

import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.net.URLDecoder
import java.security.SecureRandom
import java.util.concurrent.Executors

/** What a request resolved to. */
sealed interface Reply {
    data class Html(val body: String) : Reply
    /** [inline] shows the file in the browser (thumbnails) instead of downloading it. */
    data class FileDownload(val file: File, val mime: String, val downloadName: String, val inline: Boolean = false) : Reply
    data object NotFound : Reply
}

/**
 * A tiny read-only web server for sending documents to a computer on the same Wi-Fi. No cloud:
 * the computer's browser talks straight to the phone. Every path must start with a random
 * per-session token, so other people on the network can't browse the library, and the server
 * only lives while its screen is open. Plain JVM so request handling is unit-tested.
 */
class TransferServer(
    private val route: (path: String) -> Reply,
    val token: String = newToken(),
) {
    private var socket: ServerSocket? = null
    private val pool = Executors.newFixedThreadPool(4)

    val port: Int get() = socket?.localPort ?: -1

    fun start(preferredPort: Int = 8080) {
        if (socket != null) return
        val server = (preferredPort until preferredPort + 20).firstNotNullOfOrNull { p ->
            runCatching { ServerSocket(p, 16, InetAddress.getByName("0.0.0.0")) }.getOrNull()
        } ?: ServerSocket(0)
        socket = server
        Thread({
            while (!server.isClosed) {
                val client = try { server.accept() } catch (e: SocketException) { break }
                pool.execute { handle(client) }
            }
        }, "scanfire-transfer").apply { isDaemon = true }.start()
    }

    fun stop() {
        runCatching { socket?.close() }
        socket = null
        pool.shutdownNow()
    }

    private fun handle(client: Socket) = client.use { s ->
        s.soTimeout = 15_000
        val path = readRequestPath(BufferedInputStream(s.getInputStream())) ?: return@use
        val out = s.getOutputStream()
        val reply = resolve(path)
        runCatching { write(out, reply) }
    }

    /** Checks the token, then hands the rest of the path to [route]. */
    fun resolve(rawPath: String): Reply {
        val path = rawPath.substringBefore('?')
        val prefix = "/$token"
        if (path != prefix && !path.startsWith("$prefix/")) return Reply.NotFound
        val rest = path.removePrefix(prefix).ifEmpty { "/" }
        return runCatching { route(URLDecoder.decode(rest, "UTF-8")) }.getOrDefault(Reply.NotFound)
    }

    private fun write(out: OutputStream, reply: Reply) {
        when (reply) {
            is Reply.Html -> {
                val bytes = reply.body.toByteArray(Charsets.UTF_8)
                out.write(header(200, "OK", "text/html; charset=utf-8", bytes.size.toLong()))
                out.write(bytes)
            }
            is Reply.FileDownload -> {
                val name = reply.downloadName.replace("\"", "")
                out.write(header(200, "OK", reply.mime, reply.file.length(), "Content-Disposition: ${if (reply.inline) "inline" else "attachment"}; filename=\"$name\"; filename*=UTF-8''${java.net.URLEncoder.encode(name, "UTF-8").replace("+", "%20")}\r\n"))
                reply.file.inputStream().use { it.copyTo(out) }
            }
            Reply.NotFound -> {
                val bytes = "Not found".toByteArray()
                out.write(header(404, "Not Found", "text/plain", bytes.size.toLong()))
                out.write(bytes)
            }
        }
        out.flush()
    }

    private fun header(code: Int, text: String, mime: String, length: Long, extra: String = "") =
        ("HTTP/1.1 $code $text\r\nContent-Type: $mime\r\nContent-Length: $length\r\nCache-Control: no-store\r\n" +
            "X-Content-Type-Options: nosniff\r\nConnection: close\r\n$extra\r\n").toByteArray()

    companion object {
        /** Path of a GET request, or null for anything else (only GET is served). */
        fun readRequestPath(input: InputStream): String? {
            val line = StringBuilder()
            while (true) {
                val c = input.read()
                if (c < 0 || c == '\n'.code) break
                if (c != '\r'.code) line.append(c.toChar())
                if (line.length > 4096) return null
            }
            val parts = line.split(' ')
            if (parts.size < 2 || parts[0] != "GET") return null
            return parts[1].takeIf { it.startsWith("/") }
        }

        fun newToken(): String {
            val alphabet = "abcdefghjkmnpqrstuvwxyz23456789" // no look-alikes, easy to type
            val random = SecureRandom()
            return (1..6).map { alphabet[random.nextInt(alphabet.length)] }.joinToString("")
        }

        fun escape(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
    }
}
