package com.fivebroz.torrent

import org.libtorrent4j.Priority
import org.libtorrent4j.SessionHandle
import org.libtorrent4j.SessionManager
import org.libtorrent4j.Sha1Hash
import org.libtorrent4j.TorrentFlags
import org.libtorrent4j.TorrentHandle
import org.libtorrent4j.TorrentInfo
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.net.URLEncoder

/** Live numbers shown while a torrent is starting / buffering. */
data class TorrentStatus(val peers: Int, val bytesPerSec: Int, val progress: Float)

/**
 * Streams a single file out of a torrent, Stremio-style: the file is fetched
 * piece by piece in playback order, and a local HTTP server hands it to the
 * video player as if it were a normal web video (with seeking).
 */
class TorrentEngine(private val workDir: File) {

    companion object {
        /** Public trackers added to every magnet so peers are found quickly. */
        val DEFAULT_TRACKERS = listOf(
            "udp://tracker.opentrackr.org:1337/announce",
            "udp://open.stealth.si:80/announce",
            "udp://tracker.torrent.eu.org:451/announce",
            "udp://exodus.desync.com:6969/announce",
            "udp://open.demonii.com:1337/announce",
            "udp://tracker.openbittorrent.com:6969/announce",
            "udp://explodie.org:6969/announce",
        )

        fun magnet(infoHash: String, trackers: List<String>): String {
            val tr = (trackers + DEFAULT_TRACKERS).distinct()
                .joinToString("") { "&tr=" + URLEncoder.encode(it, "UTF-8") }
            return "magnet:?xt=urn:btih:${infoHash.lowercase()}$tr"
        }

        /** Torrentio lists sources as "tracker:udp://…" / "dht:…". */
        fun trackersFrom(sources: List<String>): List<String> =
            sources.filter { it.startsWith("tracker:") }.map { it.removePrefix("tracker:") }
    }

    private var session: SessionManager? = null
    private var handle: TorrentHandle? = null
    private var server: StreamServer? = null

    @Volatile private var closed = false

    // Geometry of the file being streamed.
    private var fileOffset = 0L
    var fileSize = 0L
        private set
    var fileName = ""
        private set
    private var pieceLength = 1
    private var numPieces = 0
    private var file: File? = null

    @Synchronized
    private fun session(): SessionManager =
        session ?: SessionManager(false).also {
            it.start()
            session = it
        }

    /**
     * Starts the BitTorrent session (and its DHT) ahead of time, so the first
     * film doesn't wait for the network to warm up. Safe to call repeatedly.
     */
    fun warmUp() {
        session()
    }

    /** One candidate torrent while several race to start. */
    private class Slot(val index: Int, val infoHash: String, val fileIdx: Int?) {
        var th: TorrentHandle? = null
        var ready = false // file chosen, first pieces requested
        var idx = 0
        var first = 0
        var last = 0
    }

    /**
     * Resolves the magnet, starts downloading the chosen file in order, and
     * returns a local http:// URL the player can open. Blocks (call off the UI thread).
     */
    fun open(magnet: String, fileIdx: Int?, metadataTimeoutSec: Int = 45): String =
        openFastest(listOf(magnet to fileIdx), metadataTimeoutSec).second

    /**
     * Adds every candidate at once and keeps the one whose first piece of video
     * arrives first; the others are dropped. The same connection that fetched the
     * metadata keeps downloading (no second round of peer discovery).
     * Returns (index of the winner in [items], local URL).
     */
    fun openFastest(items: List<Pair<String, Int?>>, timeoutSec: Int = 60): Pair<Int, String> {
        closeCurrent()
        closed = false
        workDir.mkdirs()
        val s = session()
        val slots = items.mapIndexed { i, (magnet, idx) ->
            val hash = Regex("btih:([0-9a-fA-F]{40})").find(magnet)?.groupValues?.get(1)?.lowercase()
                ?: throw IOException("bad magnet")
            Slot(i, hash, idx).also { s.download(magnet, workDir, TorrentFlags.SEQUENTIAL_DOWNLOAD) }
        }
        val until = System.currentTimeMillis() + timeoutSec * 1000L
        var winner: Slot? = null
        try {
            while (winner == null) {
                if (closed) throw IOException("closed")
                if (System.currentTimeMillis() > until) throw IOException("no source started in ${timeoutSec}s")
                for (sl in slots) {
                    val th = sl.th ?: s.find(Sha1Hash.parseHex(sl.infoHash))?.also { sl.th = it } ?: continue
                    if (!th.isValid) continue
                    if (!sl.ready) {
                        if (!th.status().hasMetadata()) continue
                        val ti = th.torrentFile() ?: continue
                        prepare(sl, th, ti)
                    }
                    if (th.havePiece(sl.first)) {
                        winner = sl
                        break
                    }
                }
                if (winner == null) Thread.sleep(80)
            }
        } finally {
            for (sl in slots) if (sl !== winner) sl.th?.let { runCatching { s.remove(it, SessionHandle.DELETE_FILES) } }
        }
        val w = winner!!
        val ti = w.th!!.torrentFile()
        val fs = ti.files()
        fileOffset = fs.fileOffset(w.idx)
        fileSize = fs.fileSize(w.idx)
        fileName = fs.fileName(w.idx)
        pieceLength = ti.pieceLength()
        numPieces = ti.numPieces()
        file = File(workDir, fs.filePath(w.idx))
        handle = w.th
        val srv = StreamServer(this)
        server = srv
        return w.index to srv.start()
    }

    /** Metadata is in: pick the video file and ask for its first and last pieces. */
    private fun prepare(sl: Slot, th: TorrentHandle, ti: TorrentInfo) {
        val fs = ti.files()
        val n = fs.numFiles()
        // Use the file the addon pointed at; otherwise the largest one.
        val idx = if (sl.fileIdx != null && sl.fileIdx in 0 until n) sl.fileIdx
        else (0 until n).maxByOrNull { fs.fileSize(it) } ?: 0
        th.prioritizeFiles(Array(n) { if (it == idx) Priority.DEFAULT else Priority.IGNORE })
        val off = fs.fileOffset(idx)
        val size = fs.fileSize(idx)
        val pl = ti.pieceLength().toLong()
        val first = (off / pl).toInt()
        val last = ((off + size - 1) / pl).toInt()
        // Video containers keep their index at the start and often at the end:
        // fetch both first so the player can start and seek immediately.
        for (i in 0 until 4) if (first + i <= last) th.setPieceDeadline(first + i, 300 * i)
        th.setPieceDeadline(last, 500)
        if (last - 1 > first) th.setPieceDeadline(last - 1, 600)
        sl.idx = idx
        sl.first = first
        sl.last = last
        sl.ready = true
    }

    fun status(): TorrentStatus {
        val st = handle?.status() ?: return TorrentStatus(0, 0, 0f)
        return TorrentStatus(st.numPeers(), st.downloadPayloadRate(), st.progress())
    }

    fun contentType(): String = when (fileName.substringAfterLast('.').lowercase()) {
        "mp4", "m4v" -> "video/mp4"
        "webm" -> "video/webm"
        "avi" -> "video/x-msvideo"
        else -> "video/x-matroska"
    }

    private fun pieceOf(pos: Long): Int = ((fileOffset + pos) / pieceLength).toInt()

    /**
     * Reads up to [len] bytes at [pos] within the file, waiting for the piece to
     * arrive and asking the swarm for the next few pieces with tight deadlines.
     */
    fun read(pos: Long, buf: ByteArray, len: Int, timeoutMs: Long = 90_000): Int {
        if (pos >= fileSize) return -1
        val th = handle ?: throw IOException("closed")
        val piece = pieceOf(pos)
        if (!th.havePiece(piece)) {
            val ahead = maxOf(4, (16L * 1024 * 1024 / pieceLength).toInt())
            for (i in 0 until ahead) {
                val p = piece + i
                if (p >= numPieces) break
                if (!th.havePiece(p)) th.setPieceDeadline(p, 200 + 150 * i)
            }
            val until = System.currentTimeMillis() + timeoutMs
            while (!th.havePiece(piece)) {
                if (closed) throw IOException("closed")
                if (System.currentTimeMillis() > until) throw IOException("piece $piece timed out")
                Thread.sleep(40)
            }
        }
        // Read no further than the end of the piece we know we have.
        val pieceEnd = (piece.toLong() + 1) * pieceLength - fileOffset
        val n = minOf(len.toLong(), pieceEnd - pos, fileSize - pos).toInt()
        RandomAccessFile(file!!, "r").use { raf ->
            raf.seek(pos)
            return raf.read(buf, 0, n)
        }
    }

    /** Stops the download and deletes what was fetched. */
    fun close() {
        closed = true
        closeCurrent()
    }

    private fun closeCurrent() {
        server?.stop()
        server = null
        val th = handle
        handle = null
        if (th != null) {
            try {
                session?.remove(th, SessionHandle.DELETE_FILES)
            } catch (_: Exception) {
            }
        }
    }

    /** Ends the whole torrent session (app exit). */
    fun shutdown() {
        close()
        try {
            session?.stop()
        } catch (_: Exception) {
        }
        session = null
        workDir.deleteRecursively()
    }
}
