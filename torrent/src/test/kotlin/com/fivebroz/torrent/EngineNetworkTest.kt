package com.fivebroz.torrent

import org.libtorrent4j.TorrentInfo
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * End-to-end check of the engine on the real BitTorrent network, using the
 * (legal, well-seeded) Ubuntu Server ISO:
 *  1. resolve it from a bare magnet link (DHT + trackers, like a Torrentio result),
 *  2. read the start of the file through the local HTTP server,
 *  3. jump to the middle with a Range request (a "seek"),
 * and verify the bytes are real.
 */
class EngineNetworkTest {

    private fun get(url: String): ByteArray =
        (URL(url).openConnection() as HttpURLConnection).run {
            connectTimeout = 20_000
            readTimeout = 60_000
            inputStream.use { it.readBytes() }
        }

    private fun range(url: String, from: Long, to: Long): Triple<Int, ByteArray, Long> {
        val t0 = System.currentTimeMillis()
        val c = URL(url).openConnection() as HttpURLConnection
        c.setRequestProperty("Range", "bytes=$from-$to")
        c.readTimeout = 180_000
        val body = c.inputStream.use { it.readBytes() }
        return Triple(c.responseCode, body, System.currentTimeMillis() - t0)
    }

    @Test
    fun streamsFromMagnet() {
        // Find the current Ubuntu 24.04 server torrent and take only its info-hash.
        val index = String(get("https://releases.ubuntu.com/24.04/"))
        val name = Regex("""href="(ubuntu-[^"]*live-server-amd64\.iso\.torrent)"""").find(index)!!.groupValues[1]
        val ti = TorrentInfo.bdecode(get("https://releases.ubuntu.com/24.04/$name"))
        val hash = ti.infoHash().toHex()
        println("ENGINE-TEST torrent=$name hash=$hash size=${ti.totalSize()}")

        val engine = TorrentEngine(File("build/engine-test"))
        try {
            val t0 = System.currentTimeMillis()
            val url = engine.open(TorrentEngine.magnet(hash, emptyList()), null, metadataTimeoutSec = 120)
            println("ENGINE-TEST metadata in ${System.currentTimeMillis() - t0} ms, file=${engine.fileName} size=${engine.fileSize}")

            // ISO 9660: the primary volume descriptor sits at byte 32768 and reads "CD001".
            val (code1, head, ms1) = range(url, 32768, 32768 + 2047)
            println("ENGINE-TEST start read: http=$code1 bytes=${head.size} in $ms1 ms; ${engine.status()}")
            assertEquals(206, code1)
            assertEquals("CD001", String(head, 1, 5, Charsets.US_ASCII))

            // Seek to the middle of the file.
            val mid = engine.fileSize / 2
            val (code2, body, ms2) = range(url, mid, mid + 1024 * 1024 - 1)
            println("ENGINE-TEST seek read: http=$code2 bytes=${body.size} in $ms2 ms; ${engine.status()}")
            assertEquals(206, code2)
            assertEquals(1024 * 1024, body.size)
            assertTrue(body.any { it != 0.toByte() }, "middle chunk is all zeros")
            println("ENGINE-TEST PASSED")
        } finally {
            engine.shutdown()
        }
    }
}
