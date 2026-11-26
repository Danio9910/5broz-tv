package com.fivebroz.torrent

import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import kotlin.concurrent.thread

/**
 * A minimal HTTP/1.1 server on 127.0.0.1 that serves the torrent file with
 * Range support, so the player can start, seek and resume like a normal video.
 */
class StreamServer(private val engine: TorrentEngine) {
    private var socket: ServerSocket? = null
    @Volatile private var running = false

    fun start(): String {
        val s = ServerSocket(0, 16, InetAddress.getByName("127.0.0.1"))
        socket = s
        running = true
        thread(name = "torrent-http", isDaemon = true) {
            while (running) {
                val c = try {
                    s.accept()
                } catch (_: SocketException) {
                    break
                }
                thread(name = "torrent-conn", isDaemon = true) { serve(c) }
            }
        }
        return "http://127.0.0.1:${s.localPort}/stream"
    }

    fun stop() {
        running = false
        try {
            socket?.close()
        } catch (_: Exception) {
        }
    }

    private fun serve(c: Socket) {
        c.use { sock ->
            try {
                val input = BufferedReader(InputStreamReader(sock.getInputStream(), Charsets.ISO_8859_1))
                val requestLine = input.readLine() ?: return
                val method = requestLine.substringBefore(' ')
                var range: String? = null
                while (true) {
                    val line = input.readLine() ?: break
                    if (line.isEmpty()) break
                    if (line.startsWith("range:", ignoreCase = true)) range = line.substringAfter(':').trim()
                }
                val out = sock.getOutputStream()
                val size = engine.fileSize

                var start = 0L
                var end = size - 1
                val partial = range != null && range.startsWith("bytes=")
                if (partial) {
                    val spec = range!!.removePrefix("bytes=").substringBefore(',')
                    val a = spec.substringBefore('-')
                    val b = spec.substringAfter('-')
                    if (a.isEmpty()) {
                        start = maxOf(0, size - (b.toLongOrNull() ?: 0))
                    } else {
                        start = a.toLongOrNull() ?: 0
                        if (b.isNotEmpty()) end = minOf(size - 1, b.toLongOrNull() ?: end)
                    }
                }
                if (start >= size) {
                    write(out, "HTTP/1.1 416 Range Not Satisfiable\r\nContent-Range: bytes */$size\r\nContent-Length: 0\r\n\r\n")
                    return
                }
                val length = end - start + 1
                val head = buildString {
                    append(if (partial) "HTTP/1.1 206 Partial Content\r\n" else "HTTP/1.1 200 OK\r\n")
                    append("Content-Type: ${engine.contentType()}\r\n")
                    append("Accept-Ranges: bytes\r\n")
                    append("Content-Length: $length\r\n")
                    if (partial) append("Content-Range: bytes $start-$end/$size\r\n")
                    append("Connection: close\r\n\r\n")
                }
                write(out, head)
                if (method == "HEAD") return

                val buf = ByteArray(256 * 1024)
                var pos = start
                while (pos <= end) {
                    val want = minOf(buf.size.toLong(), end - pos + 1).toInt()
                    val n = engine.read(pos, buf, want)
                    if (n <= 0) break
                    out.write(buf, 0, n)
                    pos += n
                }
                out.flush()
            } catch (_: Exception) {
                // Player closed the connection (seek / stop) or the torrent was closed.
            }
        }
    }

    private fun write(out: OutputStream, s: String) {
        out.write(s.toByteArray(Charsets.ISO_8859_1))
        out.flush()
    }
}
