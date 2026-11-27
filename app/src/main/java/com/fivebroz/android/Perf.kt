package com.fivebroz.android

import android.app.Activity
import android.os.Build
import androidx.metrics.performance.JankStats
import com.fivebroz.android.data.Db
import com.fivebroz.android.data.Net
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * How smooth the app really is on the family's own phones and TVs: every frame's duration is
 * counted per screen (home, title, search, player…), and when the app goes to the background a
 * small summary is sent — device model, screen, frames, janky frames, median/90th/99th frame
 * times. Nothing about what was watched or who watched it.
 */
object Perf {
    @Volatile var screen: String = "start"
    @Volatile var tab: String = "home"

    private class Bucket {
        val hist = IntArray(1001) // frame duration in ms, last bucket = 1000+
        var frames = 0
        var janky = 0
    }

    private val buckets = ConcurrentHashMap<String, Bucket>()
    private val trackers = HashMap<Activity, JankStats>()

    fun track(a: Activity) {
        if (trackers.containsKey(a)) return
        runCatching {
            trackers[a] = JankStats.createAndTrack(a.window) { f ->
                val b = buckets.getOrPut(screen) { Bucket() }
                synchronized(b) {
                    val ms = (f.frameDurationUiNanos / 1_000_000).toInt().coerceIn(0, 1000)
                    b.hist[ms]++
                    b.frames++
                    if (f.isJank) b.janky++
                }
            }
        }
    }

    fun untrack(a: Activity) {
        trackers.remove(a)?.isTrackingEnabled = false
    }

    /** Sends what was collected (screens with enough frames to mean something) and starts over. */
    suspend fun flush(a: Activity, tv: Boolean) {
        val rows = JSONArray()
        val hz = runCatching { if (Build.VERSION.SDK_INT >= 30) a.display?.refreshRate else a.windowManager.defaultDisplay.refreshRate }.getOrNull() ?: 60f
        for ((name, b) in buckets) {
            val copy: IntArray
            val frames: Int
            val janky: Int
            synchronized(b) {
                if (b.frames < 60) continue
                copy = b.hist.copyOf(); frames = b.frames; janky = b.janky
                b.hist.fill(0); b.frames = 0; b.janky = 0
            }
            fun pct(p: Double): Int {
                val target = (frames * p).toInt()
                var acc = 0
                for (i in copy.indices) { acc += copy[i]; if (acc > target) return i }
                return 1000
            }
            rows.put(
                JSONObject()
                    .put("device", "${Build.MANUFACTURER} ${Build.MODEL}".trim())
                    .put("kind", if (tv) "tv" else "phone")
                    .put("android", Build.VERSION.SDK_INT)
                    .put("app_version", BuildConfig.VERSION_NAME + if (BuildConfig.DEBUG) "-debug" else "")
                    .put("screen", name).put("frames", frames).put("janky", janky)
                    .put("p50_ms", pct(0.5)).put("p90_ms", pct(0.9)).put("p99_ms", pct(0.99))
                    .put("refresh_hz", hz.toDouble()),
            )
        }
        if (rows.length() == 0) return
        runCatching { Net.send("POST", "${Db.URL}/perf_reports", rows.toString(), Db.headers + mapOf("Prefer" to "return=minimal")) }
    }
}
