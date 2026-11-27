package com.fivebroz.android

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import com.fivebroz.android.data.Household
import com.fivebroz.android.data.Prefs
import com.fivebroz.android.data.Signal
import com.fivebroz.android.player.PlayArgs
import com.fivebroz.android.player.PlayerActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** What another device asked the screens to do (collected by the navigation). */
sealed interface RemoteEvent {
    data class Search(val text: String) : RemoteEvent
    data class Open(val type: String, val id: String) : RemoteEvent
}

object RemoteBus {
    val events = MutableSharedFlow<RemoteEvent>(extraBufferCapacity = 8)
}

/**
 * Keeps this device visible to the rest of the house while the app is open, and on a TV
 * carries out what a phone asks: remote-control keys, typed searches, "open this", and
 * "continue this film here".
 */
class FiveBrozApp : Application() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var started = 0
    private var loop: Job? = null
    @Volatile var top: Activity? = null
        private set
    private val isTv by lazy {
        packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
            !packageManager.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)
    }

    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityStarted(a: Activity) {
                started++
                if (started == 1) startLoop()
            }
            override fun onActivityStopped(a: Activity) {
                started = (started - 1).coerceAtLeast(0)
                if (started == 0) loop?.cancel()
            }
            override fun onActivityResumed(a: Activity) {
                top = a
                Perf.track(a)
                if (a is MainActivity && Perf.screen == "player") Perf.screen = Perf.tab
            }
            override fun onActivityPaused(a: Activity) {
                if (top === a) top = null
                // send the smoothness summary when the app leaves the screen
                if (!a.isChangingConfigurations) scope.launch { Perf.flush(a, isTv) }
            }
            override fun onActivityCreated(a: Activity, b: Bundle?) {}
            override fun onActivitySaveInstanceState(a: Activity, b: Bundle) {}
            override fun onActivityDestroyed(a: Activity) { Perf.untrack(a) }
        })
    }

    private fun startLoop() {
        loop?.cancel()
        loop = scope.launch {
            // let the first screen draw before talking to the network
            delay(2500)
            var lastBeat = 0L
            while (isActive) {
                val now = SystemClock.elapsedRealtime()
                if (now - lastBeat > 60_000) {
                    lastBeat = now
                    launch { Household.heartbeat(this@FiveBrozApp, isTv) }
                }
                // Only TVs take requests; phones just announce themselves.
                if (isTv && Prefs.profileId != null) {
                    for (s in Household.take(this@FiveBrozApp)) handle(s)
                    delay(1200)
                } else {
                    delay(30_000)
                }
            }
        }
    }

    private fun handle(s: Signal) {
        val p = s.payload
        when (s.kind) {
            "key" -> {
                val code = when (p.optString("key")) {
                    "up" -> KeyEvent.KEYCODE_DPAD_UP
                    "down" -> KeyEvent.KEYCODE_DPAD_DOWN
                    "left" -> KeyEvent.KEYCODE_DPAD_LEFT
                    "right" -> KeyEvent.KEYCODE_DPAD_RIGHT
                    "ok" -> KeyEvent.KEYCODE_DPAD_CENTER
                    "back" -> KeyEvent.KEYCODE_BACK
                    "playpause" -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
                    "ff" -> KeyEvent.KEYCODE_MEDIA_FAST_FORWARD
                    "rew" -> KeyEvent.KEYCODE_MEDIA_REWIND
                    else -> return
                }
                val a = top ?: return
                val t = SystemClock.uptimeMillis()
                a.dispatchKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0))
                a.dispatchKeyEvent(KeyEvent(t, t + 40, KeyEvent.ACTION_UP, code, 0))
            }
            "text" -> {
                bringMainToFront()
                RemoteBus.events.tryEmit(RemoteEvent.Search(p.optString("text")))
            }
            "open" -> {
                bringMainToFront()
                RemoteBus.events.tryEmit(RemoteEvent.Open(p.optString("type", "movie"), p.optString("id")))
            }
            "play" -> {
                // "Continue on the TV": same film, same second.
                (top as? PlayerActivity)?.finish()
                PlayerActivity.start(
                    this,
                    PlayArgs(
                        type = p.optString("type", "movie"),
                        metaId = p.optString("metaId"),
                        name = p.optString("name"),
                        poster = p.optString("poster").ifBlank { null },
                        videoId = p.optString("videoId"),
                        label = p.optString("label"),
                        startSec = p.optLong("pos", -1),
                    ),
                )
            }
        }
    }

    private fun bringMainToFront() {
        (top as? PlayerActivity)?.finish()
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT),
        )
    }
}
