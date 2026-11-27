package com.fivebroz.android.player

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color as AColor
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.C as MC
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import androidx.media3.ui.SubtitleView
import com.fivebroz.android.R
import com.fivebroz.android.data.Api
import com.fivebroz.android.data.Db
import com.fivebroz.android.data.Net
import com.fivebroz.android.data.Stream
import com.fivebroz.android.data.Subtitle
import com.fivebroz.torrent.TorrentEngine
import com.fivebroz.android.ui.C
import com.fivebroz.android.ui.kenBurns
import com.fivebroz.android.ui.FiveBrozTheme
import com.fivebroz.android.ui.GoldButton
import com.fivebroz.android.ui.VSpace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Everything the player needs to know about what to play. */
data class PlayArgs(
    val type: String,
    val metaId: String,
    val name: String,
    val poster: String?,
    val videoId: String,
    val label: String,
    val streamUrl: String? = null,
    val queueIds: ArrayList<String> = arrayListOf(),
    val queueLabels: ArrayList<String> = arrayListOf(),
    val partyId: String? = null,
    val partyHost: Boolean = false,
)

private sealed interface Overlay {
    data class Loading(val text: String) : Overlay
    data class Failed(val text: String) : Overlay
    data class Next(val label: String) : Overlay
    data object StillWatching : Overlay
    data object None : Overlay
}

@androidx.annotation.OptIn(UnstableApi::class)
class PlayerActivity : ComponentActivity(), PlayerActions {

    companion object {
        /** A source chosen by hand in the sources panel (handed over in-process). */
        var pendingPick: Stream? = null
        private var current: java.lang.ref.WeakReference<PlayerActivity>? = null

        fun start(ctx: Context, a: PlayArgs) {
            ctx.startActivity(
                Intent(ctx, PlayerActivity::class.java)
                    .putExtra("type", a.type)
                    .putExtra("metaId", a.metaId)
                    .putExtra("name", a.name)
                    .putExtra("poster", a.poster)
                    .putExtra("videoId", a.videoId)
                    .putExtra("label", a.label)
                    .putExtra("streamUrl", a.streamUrl)
                    .putStringArrayListExtra("queueIds", a.queueIds)
                    .putStringArrayListExtra("queueLabels", a.queueLabels)
                    .putExtra("partyId", a.partyId)
                    .putExtra("partyHost", a.partyHost),
            )
        }
    }

    private lateinit var args: PlayArgs
    private lateinit var playerView: PlayerView
    private var player: ExoPlayer? = null
    private var isTv = false

    private var overlay by mutableStateOf<Overlay>(Overlay.Loading("جارٍ تجهيز أفضل مصدر…"))
    private val ui = PlayerUi()
    private var subScale = 1.1f

    // ---- watch party ----
    private var partyId: String? = null
    private var isHost = false
    private var partyJob: Job? = null
    private var lastSeenMsg: String? = null
    private var currentUrl: String? = null
    private var subOffset by mutableStateOf(0f)

    private var candidates: List<Stream> = emptyList()
    private var torrentJob: Job? = null
    private val engine by lazy { Torrents.get(this) }
    private var startedAt = 0L
    private var saveJob: Job? = null
    private var nextJob: Job? = null
    private var session: androidx.media3.session.MediaSession? = null
    private var introSkipped = false
    private var wasInPip = false
    private var autoPipFor: Boolean? = null
    private var volumeLevel = -1f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Only one player at a time: a title opened while another sits in picture-in-picture replaces it.
        current?.get()?.takeIf { it !== this }?.finish()
        current = java.lang.ref.WeakReference(this)
        val i = intent
        args = PlayArgs(
            type = i.getStringExtra("type") ?: "movie",
            metaId = i.getStringExtra("metaId") ?: "",
            name = i.getStringExtra("name") ?: "",
            poster = i.getStringExtra("poster"),
            videoId = i.getStringExtra("videoId") ?: "",
            label = i.getStringExtra("label") ?: "",
            streamUrl = i.getStringExtra("streamUrl"),
            queueIds = i.getStringArrayListExtra("queueIds") ?: arrayListOf(),
            queueLabels = i.getStringArrayListExtra("queueLabels") ?: arrayListOf(),
            partyId = i.getStringExtra("partyId"),
            partyHost = i.getBooleanExtra("partyHost", false),
        )
        partyId = args.partyId
        isHost = args.partyHost
        if (partyId != null) startPartyLoops()
        isTv = packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        val root = FrameLayout(this).apply { setBackgroundColor(AColor.BLACK) }
        playerView = PlayerView(this).apply {
            keepScreenOn = true
            useController = false // our own controls (Controls.kt)
            setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
        }
        applySubStyle()
        root.addView(playerView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        root.addView(
            ComposeView(this).apply { setContent { OverlayUi() } },
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
        )
        setContentView(root)

        begin()
        // Debug builds: if loading ever stalls, write where every thread is stuck to the log.
        if (com.fivebroz.android.BuildConfig.DEBUG) {
            Thread({
                var since = 0L
                while (!isDestroyed) {
                    Thread.sleep(5000)
                    val loading = overlay is Overlay.Loading
                    since = if (loading) (if (since == 0L) System.currentTimeMillis() else since) else 0L
                    if (since > 0 && System.currentTimeMillis() - since > 40_000) {
                        since = System.currentTimeMillis() // again in 40 s
                        Thread.getAllStackTraces().forEach { (t, st) ->
                            if (t.name == "main" || t.name.startsWith("DefaultDispatcher") || t.name.contains("torrent")) {
                                Log.w("5broz", "STALL ${t.name} ${t.state}: " + st.take(14).joinToString(" < ") { "${it.className.substringAfterLast('.')}.${it.methodName}:${it.lineNumber}" })
                            }
                        }
                    }
                }
            }, "stall-watch").apply { isDaemon = true }.start()
        }
        // Keep the on-screen clock and auto-hide in step with playback.
        lifecycleScope.launch {
            while (true) {
                delay(400)
                val p = player
                if (p != null) {
                    ui.position = p.currentPosition
                    ui.duration = p.duration.takeIf { it > 0 } ?: 0L
                    ui.buffered = p.bufferedPosition
                    ui.playing = p.isPlaying
                    ui.buffering = p.playbackState == Player.STATE_BUFFERING || p.playbackState == Player.STATE_IDLE
                    // No intro markers exist for torrents, so offer the skip in the opening minutes of an episode.
                    val sec = ui.position / 1000
                    val canSkip = !ui.inPip && overlay is Overlay.None && (partyId == null || isHost)
                    val t = ui.position / 1000.0
                    val seg = segments
                    val recap = seg?.recap?.takeIf { t >= it.start && t < it.end - 1 && !recapSkipped }
                    val intro = seg?.intro?.takeIf { t >= it.start && t < it.end - 1 && !introSkipped }
                    when {
                        canSkip && recap != null -> { ui.skipLabel = "تخطي الملخص"; ui.showSkipIntro = true }
                        canSkip && intro != null -> { ui.skipLabel = "تخطي المقدمة"; ui.showSkipIntro = true }
                        // No community timing for this episode: offer the old estimate in its first minutes.
                        else -> {
                            ui.skipLabel = "تخطي المقدمة"
                            ui.showSkipIntro = canSkip && seg?.intro == null && seg?.noIntro != true && args.type == "series" &&
                                !introSkipped && sec in 5..180 && ui.duration > 10 * 60_000
                        }
                    }
                    // Credits rolling with another episode queued: offer it now, like Netflix.
                    if (seg != null && ui.marks.isEmpty() && ui.duration > 0) {
                        val d = ui.duration / 1000.0
                        ui.marks = listOfNotNull(seg.recap, seg.intro, seg.outro)
                            .map { (it.start / d).toFloat().coerceIn(0f, 1f) to (it.end / d).toFloat().coerceIn(0f, 1f) }
                    }
                    val outro = seg?.outro
                    if (outro != null && !outroShown && args.queueIds.isNotEmpty() && overlay is Overlay.None && partyId == null &&
                        t >= outro.start && ui.duration > 0 && t < ui.duration / 1000.0 - 5
                    ) {
                        outroShown = true
                        showNext()
                    }
                    // Near the end: get the next episode's sources ready so it starts at once.
                    if (!nextPrefetched && args.queueIds.isNotEmpty() && ui.duration > 0 &&
                        (ui.position > ui.duration * 0.85 || (outro != null && t > outro.start - 90))
                    ) {
                        nextPrefetched = true
                        StreamPrefetch.warm(this@PlayerActivity, args.type, args.queueIds[0])
                    }
                    if (autoPipFor != ui.playing) {
                        autoPipFor = ui.playing
                        updatePipParams()
                    }
                }
                if (ui.visible && ui.playing && !ui.subsOpen && !ui.speedOpen && !ui.audioOpen && System.currentTimeMillis() - ui.lastPoke > 4500) {
                    ui.visible = false
                }
            }
        }
    }

    // ---------- actions used by the controls ----------

    override fun poke() {
        ui.lastPoke = System.currentTimeMillis()
        autoStreak = 0 // someone is there
    }

    private fun show() {
        ui.visible = true
        poke()
    }

    override fun togglePlay() {
        val p = player ?: return
        if (p.isPlaying) p.pause() else p.play()
        ui.playing = !ui.playing
        poke()
        beatNow()
    }

    override fun seekTo(ms: Long) {
        val p = player ?: return
        p.seekTo(ms.coerceIn(0, (p.duration.takeIf { it > 0 } ?: Long.MAX_VALUE)))
        ui.position = p.currentPosition
        poke()
        beatNow()
    }

    private var flashJob: Job? = null
    override fun seekBy(sec: Int) {
        val p = player ?: return
        seekTo(p.currentPosition + sec * 1000L)
        ui.flash = if (sec > 0) "+$sec ث" else "−${-sec} ث"
        flashJob?.cancel()
        flashJob = lifecycleScope.launch {
            delay(650)
            ui.flash = null
        }
    }

    override fun back() = finish()

    override fun finish() {
        // Expanded from picture-in-picture, the player lives in its own task: go back to the app, not the launcher.
        if (wasInPip) {
            runCatching {
                startActivity(Intent(this, com.fivebroz.android.MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
            }
        }
        super.finish()
    }

    // ---------- speed, zoom, intro, gestures ----------

    override fun setSpeed(speed: Float) {
        ui.speed = speed
        player?.setPlaybackSpeed(speed)
        poke()
    }

    override fun skipIntro() {
        ui.showSkipIntro = false
        val p = player ?: return
        val t = p.currentPosition / 1000.0
        val seg = segments
        val recap = seg?.recap?.takeIf { t >= it.start && t < it.end }
        val intro = seg?.intro?.takeIf { t >= it.start && t < it.end }
        when {
            recap != null -> { recapSkipped = true; seekTo((recap.end * 1000).toLong()) }
            intro != null -> { introSkipped = true; seekTo((intro.end * 1000).toLong()) }
            else -> { introSkipped = true; seekTo(p.currentPosition + 85_000) }
        }
    }

    // ---- page data (loading screen, episode picker) ----
    private var backdrop by mutableStateOf<String?>(null)
    private var logo by mutableStateOf<String?>(null)
    private var allEpisodes by mutableStateOf<List<com.fivebroz.android.data.Episode>>(emptyList())

    override val episodes get() = allEpisodes
    override val currentVideoId get() = args.videoId

    override fun playEpisode(e: com.fivebroz.android.data.Episode) {
        saveNow()
        val after = allEpisodes.dropWhile { it.id != e.id }.drop(1).take(30)
        args = args.copy(
            videoId = e.id,
            label = com.fivebroz.android.ui.epLabel(e),
            streamUrl = null,
            queueIds = ArrayList(after.map { it.id }),
            queueLabels = ArrayList(after.map { com.fivebroz.android.ui.epLabel(it) }),
        )
        autoStreak = 0
        begin()
    }

    // ---- intro / recap / credits (IntroDB) and binge behaviour ----
    private var segments: Api.Segments? = null
    private var recapSkipped = false
    private var outroShown = false
    private var nextPrefetched = false
    /** Episodes started automatically in a row with nobody touching anything. */
    private var autoStreak = 0

    override fun hold2x(on: Boolean) {
        val p = player ?: return
        ui.held2x = on
        p.setPlaybackSpeed(if (on) 2f else ui.speed)
    }

    // Night mode: compress loud effects and lift dialogue (Android's dynamics processor).
    private var dynamics: android.media.audiofx.DynamicsProcessing? = null

    override fun setNightMode(on: Boolean) {
        ui.nightMode = on
        com.fivebroz.android.data.Prefs.nightMode = on
        applyNightMode()
    }

    private fun applyNightMode() {
        runCatching { dynamics?.release() }
        dynamics = null
        val p = player ?: return
        if (!ui.nightMode || android.os.Build.VERSION.SDK_INT < 28 || p.audioSessionId == 0) return
        runCatching {
            val cfg = android.media.audiofx.DynamicsProcessing.Config.Builder(
                android.media.audiofx.DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION, 2,
                false, 0, true, 1, false, 0, true,
            ).build()
            val d = android.media.audiofx.DynamicsProcessing(0, p.audioSessionId, cfg)
            for (ch in 0 until 2) {
                val band = d.getMbcBandByChannelIndex(ch, 0)
                band.threshold = -32f
                band.ratio = 4f
                band.attackTime = 3f
                band.releaseTime = 120f
                band.postGain = 10f
                d.setMbcBandByChannelIndex(ch, 0, band)
                val lim = d.getLimiterByChannelIndex(ch)
                lim.threshold = -2f
                d.setLimiterByChannelIndex(ch, lim)
            }
            d.enabled = true
            dynamics = d
        }
    }

    override fun openExternal() {
        val url = currentUrl ?: return
        player?.pause()
        val i = Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(url), "video/*")
            .putExtra("title", args.name)
            .putExtra("position", player?.currentPosition?.toInt() ?: 0)
        try {
            startActivity(Intent.createChooser(i, "افتح في"))
        } catch (_: Exception) {
            flash("لا يوجد مشغّل فيديو آخر على هذا الجهاز")
        }
    }

    /** "1080p · H.264 · 5.1" from what is actually playing. */
    private fun updateBadges(p: Player) {
        val v = (p as? ExoPlayer)?.videoFormat
        val a = (p as? ExoPlayer)?.audioFormat
        val res = v?.height?.takeIf { it > 0 }?.let { h ->
            when {
                h >= 2000 -> "4K"
                h >= 1000 -> "1080p"
                h >= 700 -> "720p"
                else -> "${h}p"
            }
        }
        val codec = when {
            v?.sampleMimeType?.contains("hevc") == true -> "HEVC"
            v?.sampleMimeType?.contains("avc") == true -> "H.264"
            v?.sampleMimeType?.contains("av01") == true -> "AV1"
            v?.sampleMimeType?.contains("vp9") == true -> "VP9"
            else -> null
        }
        val ch = when (a?.channelCount) {
            6 -> "5.1"
            8 -> "7.1"
            2 -> "ستيريو"
            else -> null
        }
        ui.badges = listOfNotNull(res, codec, ch).joinToString(" · ").ifBlank { null }
        v?.frameRate?.takeIf { it > 0 }?.let { matchFrameRate(it) }
    }

    /** TV: switch the display to a refresh rate that is a multiple of the film's (no judder). */
    private var fpsSwitched = false
    private fun matchFrameRate(fps: Float) {
        if (!isTv || fpsSwitched || !com.fivebroz.android.data.Prefs.matchFrameRate) return
        runCatching {
            @Suppress("DEPRECATION")
            val display = windowManager.defaultDisplay
            val cur = display.mode
            val best = display.supportedModes
                .filter { it.physicalWidth == cur.physicalWidth && it.physicalHeight == cur.physicalHeight }
                .minByOrNull { m ->
                    val k = m.refreshRate / fps
                    val mult = Math.round(k)
                    if (mult < 1) 99f else kotlin.math.abs(k - mult) * 10 + (mult - 1) * 0.01f
                } ?: return
            val k = best.refreshRate / fps
            if (kotlin.math.abs(k - Math.round(k)) < 0.01 && best.modeId != cur.modeId) {
                window.attributes = window.attributes.apply { preferredDisplayModeId = best.modeId }
                fpsSwitched = true
                Log.i("5broz", "frame rate match: ${fps}fps -> ${best.refreshRate}Hz")
            }
        }
    }

    override fun setFill(fill: Boolean) {
        ui.fill = fill
        playerView.resizeMode = if (fill) AspectRatioFrameLayout.RESIZE_MODE_ZOOM else AspectRatioFrameLayout.RESIZE_MODE_FIT
        ui.flash = if (fill) "ملء الشاشة" else "الحجم الأصلي"
        flashJob?.cancel()
        flashJob = lifecycleScope.launch { delay(800); ui.flash = null }
        poke()
    }

    override fun adjustBrightness(delta: Float): Float {
        val lp = window.attributes
        var cur = lp.screenBrightness
        if (cur < 0) {
            cur = runCatching {
                android.provider.Settings.System.getInt(contentResolver, android.provider.Settings.System.SCREEN_BRIGHTNESS) / 255f
            }.getOrDefault(0.5f)
        }
        val next = (cur + delta).coerceIn(0.01f, 1f)
        lp.screenBrightness = next
        window.attributes = lp
        return next
    }

    override fun adjustVolume(delta: Float): Float {
        val am = getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
        val max = am.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        if (volumeLevel < 0) volumeLevel = am.getStreamVolume(android.media.AudioManager.STREAM_MUSIC) / max.toFloat()
        volumeLevel = (volumeLevel + delta).coerceIn(0f, 1f)
        val steps = Math.round(volumeLevel * max)
        if (steps != am.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)) {
            runCatching { am.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, steps, 0) }
        }
        return volumeLevel
    }

    // ---------- picture-in-picture ----------

    override val canPip: Boolean
        get() = !isTv && android.os.Build.VERSION.SDK_INT >= 26 &&
            packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)

    @androidx.annotation.RequiresApi(26)
    private fun pipParams(): android.app.PictureInPictureParams {
        val v = player?.videoSize
        val w = v?.width?.takeIf { it > 0 } ?: 16
        val h = v?.height?.takeIf { it > 0 } ?: 9
        // Android only accepts ratios between 1:2.39 and 2.39:1.
        val r = (w.toFloat() / h).coerceIn(1 / 2.3f, 2.3f)
        val b = android.app.PictureInPictureParams.Builder()
            .setAspectRatio(android.util.Rational((r * 1000).toInt(), 1000))
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            // Swiping home while a video plays slides it into a small window, like YouTube.
            b.setAutoEnterEnabled(ui.playing).setSeamlessResizeEnabled(true)
        }
        return b.build()
    }

    private fun updatePipParams() {
        if (!canPip) return
        runCatching { setPictureInPictureParams(pipParams()) }
    }

    override fun enterPip() {
        if (!canPip) return
        ui.visible = false
        runCatching { enterPictureInPictureMode(pipParams()) }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // Android 12+ enters on its own (auto-enter); older versions need the nudge.
        if (android.os.Build.VERSION.SDK_INT < 31 && canPip && player?.isPlaying == true && overlay is Overlay.None) enterPip()
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: android.content.res.Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        ui.inPip = isInPictureInPictureMode
        if (isInPictureInPictureMode) {
            wasInPip = true
            ui.visible = false
            ui.subsOpen = false
            ui.audioOpen = false
            ui.speedOpen = false
            ui.chatOpen = false
        }
    }

    override fun subtitleOptions(): List<SubOption> {
        val p = player ?: return emptyList()
        val off = p.trackSelectionParameters.disabledTrackTypes.contains(MC.TRACK_TYPE_TEXT)
        val out = mutableListOf(
            SubOption("بدون ترجمة", off) {
                com.fivebroz.android.data.Prefs.subLang = "off"
                p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
                    .setTrackTypeDisabled(MC.TRACK_TYPE_TEXT, true).build()
            },
        )
        p.currentTracks.groups.filter { it.type == MC.TRACK_TYPE_TEXT }.forEachIndexed { i, g ->
            val f = g.getTrackFormat(0)
            val label = f.label ?: f.language?.let { if (it.startsWith("ar")) "العربية" else it } ?: "ترجمة ${i + 1}"
            out += SubOption(label, !off && g.isSelected) {
                com.fivebroz.android.data.Prefs.subLang = if (f.language?.startsWith("en") == true) "en" else "ar"
                p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
                    .setTrackTypeDisabled(MC.TRACK_TYPE_TEXT, false)
                    .setOverrideForType(androidx.media3.common.TrackSelectionOverride(g.mediaTrackGroup, 0))
                    .build()
            }
        }
        return out
    }

    override val subtitleOffset: Float get() = subOffset

    override fun resetSubtitleDelay() = shiftSubtitles(-subOffset)

    /** Colour, background box, size and height of the subtitles (per profile). */
    override fun applySubStyle() {
        val color = if (com.fivebroz.android.data.Prefs.subColor == "yellow") AColor.rgb(255, 221, 87) else AColor.WHITE
        val box = com.fivebroz.android.data.Prefs.subBox
        playerView.subtitleView?.apply {
            setStyle(
                CaptionStyleCompat(
                    color, if (box) AColor.argb(170, 0, 0, 0) else AColor.TRANSPARENT, AColor.TRANSPARENT,
                    if (box) CaptionStyleCompat.EDGE_TYPE_NONE else CaptionStyleCompat.EDGE_TYPE_OUTLINE, AColor.BLACK,
                    ResourcesCompat.getFont(this@PlayerActivity, R.font.cairo),
                ),
            )
            setFractionalTextSize(SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * com.fivebroz.android.data.Prefs.subScale)
            setBottomPaddingFraction(com.fivebroz.android.data.Prefs.subPos)
        }
    }

    override val secondLanguage: Boolean get() = com.fivebroz.android.data.Prefs.subSecond

    override fun setSecondLanguage(on: Boolean) {
        com.fivebroz.android.data.Prefs.subSecond = on
        reloadSubtitles()
    }

    /** Rebuild the subtitles with a time shift, keeping the video where it is. */
    override fun shiftSubtitles(deltaSec: Float) {
        subOffset = (Math.round((subOffset + deltaSec) * 10) / 10f).coerceIn(-180f, 180f)
        com.fivebroz.android.data.Prefs.setSubDelay(args.videoId, subOffset)
        val p = player ?: return
        val url = currentUrl ?: return
        val pos = p.currentPosition
        val playing = p.playWhenReady
        p.setMediaItem(buildItem(url), pos)
        p.playWhenReady = playing
        p.prepare()
        poke()
    }

    override fun audioOptions(): List<SubOption> {
        val p = player ?: return emptyList()
        val groups = p.currentTracks.groups.filter { it.type == MC.TRACK_TYPE_AUDIO }
        if (groups.size < 2) return emptyList()
        val ar = java.util.Locale("ar")
        return groups.mapIndexed { i, g ->
            val f = g.getTrackFormat(0)
            val lang = f.language?.takeIf { it != "und" }?.let { java.util.Locale(it).getDisplayLanguage(ar) }
            val ch = when (f.channelCount) {
                6 -> " · 5.1"
                8 -> " · 7.1"
                2 -> " · ستيريو"
                else -> ""
            }
            val label = listOfNotNull(f.label, lang).distinct().joinToString(" — ").ifBlank { "مسار ${i + 1}" } + ch
            SubOption(label, g.isSelected) {
                // Remembered for this series' next episodes.
                if (args.type == "series") com.fivebroz.android.data.Prefs.setAudioLang(args.metaId, f.language?.takeIf { it != "und" })
                p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
                    .setOverrideForType(androidx.media3.common.TrackSelectionOverride(g.mediaTrackGroup, 0))
                    .build()
            }
        }
    }

    override fun setSubtitleSize(scale: Float) {
        com.fivebroz.android.data.Prefs.subScale = scale
        subScale = scale
        playerView.subtitleView?.setFractionalTextSize(SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * scale)
    }

    override fun otherSource() {
        ui.visible = false
        tryNext()
    }

    override val hasNextEpisode: Boolean get() = args.queueIds.isNotEmpty()

    override fun nextEpisode() {
        saveNow()
        playNext()
    }

    // ---------- watch party ----------

    override val inParty: Boolean get() = partyId != null
    override val partyIsHost: Boolean get() = isHost

    override fun startParty() {
        if (partyId != null) {
            ui.chatOpen = true
            return
        }
        lifecycleScope.launch {
            val host = com.fivebroz.android.data.Prefs.profileName.ifBlank { "مضيف" }
            val p = com.fivebroz.android.data.Parties.create(host, args.metaId, args.type, args.videoId)
            if (p == null) {
                ui.flash = "تعذّر إنشاء الغرفة"
                delay(1500)
                ui.flash = null
                return@launch
            }
            partyId = p.id
            isHost = true
            ui.partyCode = p.code
            android.util.Log.i("5broz", "party created code=${p.code}")
            ui.chatOpen = true
            startPartyLoops()
        }
    }

    override fun sendChat(text: String) {
        val id = partyId ?: return
        val t = text.trim()
        if (t.isEmpty()) return
        val me = com.fivebroz.android.data.Prefs.profileName.ifBlank { "ضيف" }
        ui.messages.add(com.fivebroz.android.data.PartyMessage("local-${System.nanoTime()}", me, t))
        lifecycleScope.launch { com.fivebroz.android.data.Parties.send(id, me, t) }
    }

    /** Host broadcasts position every few seconds; guests follow; both read chat. */
    private fun startPartyLoops() {
        partyJob?.cancel()
        partyJob = lifecycleScope.launch {
            var n = 0
            while (true) {
                val id = partyId ?: break
                val p = player
                if (isHost) {
                    if (p != null && n % 3 == 0) {
                        com.fivebroz.android.data.Parties.beat(id, p.currentPosition / 1000.0, p.isPlaying, args.videoId)
                    }
                } else {
                    val party = com.fivebroz.android.data.Parties.get(id)
                    if (party != null) {
                        if (ui.partyCode.isEmpty()) ui.partyCode = party.code
                        val vid = party.videoId
                        if (vid != null && vid != args.videoId) {
                            // Host moved on (e.g. next episode) — follow.
                            args = args.copy(videoId = vid, label = "", streamUrl = null, queueIds = arrayListOf(), queueLabels = arrayListOf())
                            begin()
                        } else if (p != null && overlay is Overlay.None) {
                            val drift = if (party.isPlaying && party.updatedAt > 0)
                                (System.currentTimeMillis() - party.updatedAt).coerceIn(0, 6000) / 1000.0 else 0.0
                            val target = party.positionSec + drift
                            if (kotlin.math.abs(p.currentPosition / 1000.0 - target) > 3.0) {
                                android.util.Log.i("5broz", "party sync: seek to ${target.toInt()}s (playing=${party.isPlaying})")
                                p.seekTo((target * 1000).toLong())
                            }
                            if (party.isPlaying && !p.isPlaying) p.play()
                            if (!party.isPlaying && p.isPlaying) p.pause()
                        }
                    }
                }
                if (n % 3 == 0) {
                    val msgs = com.fivebroz.android.data.Parties.messages(id)
                    if (msgs.isNotEmpty() && msgs.last().id != lastSeenMsg) {
                        val fresh = lastSeenMsg != null
                        lastSeenMsg = msgs.last().id
                        ui.messages.clear()
                        ui.messages.addAll(msgs)
                        if (fresh && !ui.chatOpen) {
                            val m = msgs.last()
                            ui.bubble = "${m.sender ?: "ضيف"}: ${m.body}"
                            launch { delay(5000); ui.bubble = null }
                        }
                    }
                }
                n++
                delay(1000)
            }
        }
    }

    /** Host: push a beat right away after play/pause/seek so guests react quickly. */
    private fun beatNow() {
        val id = partyId ?: return
        if (!isHost) return
        val p = player ?: return
        lifecycleScope.launch { com.fivebroz.android.data.Parties.beat(id, p.currentPosition / 1000.0, p.isPlaying, args.videoId) }
    }

    // ---------- playback ----------

    private fun buildPlayer(headers: Map<String, String> = emptyMap()): ExoPlayer {
        // Torrent pieces can take a while to arrive, so allow long reads.
        val http = OkHttpDataSource.Factory(
            Net.client.newBuilder().readTimeout(150, java.util.concurrent.TimeUnit.SECONDS).build(),
        ).setDefaultRequestProperties(headers)
        // Start with the subtitle choice this profile made last time.
        val lang = com.fivebroz.android.data.Prefs.subLang
        val selector = DefaultTrackSelector(this).apply {
            setParameters(
                buildUponParameters()
                    .setPreferredTextLanguage(if (lang == "en") "en" else "ar")
                    .setTrackTypeDisabled(MC.TRACK_TYPE_TEXT, lang == "off")
                    .apply { com.fivebroz.android.data.Prefs.audioLang(args.metaId)?.let { setPreferredAudioLanguage(it) } },
            )
        }
        return ExoPlayer.Builder(this)
            .setRenderersFactory(
                DefaultRenderersFactory(this)
                    .setEnableDecoderFallback(true)
                    .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER),
            )
            .setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(this, http)))
            .setTrackSelector(selector)
            // Start as soon as one second of video is in (the default waits 2.5 s).
            .setLoadControl(
                androidx.media3.exoplayer.DefaultLoadControl.Builder()
                    .setBufferDurationsMs(15_000, 50_000, 1_000, 2_500)
                    .build(),
            )
            .build()
            .also { p ->
                p.addListener(object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) {
                        Log.w("5broz", "player error: ${error.errorCodeName} ${error.message}")
                        tryNext()
                    }

                    override fun onTracksChanged(tracks: Tracks) {
                        updateBadges(p)
                        if (ui.nightMode && dynamics == null) applyNightMode()
                        // A file whose audio this device can't decode plays silently — skip it.
                        val audio = tracks.groups.filter { it.type == MC.TRACK_TYPE_AUDIO }
                        val early = System.currentTimeMillis() - startedAt < 20_000
                        if (early && !skipped && audio.isNotEmpty() && audio.none { it.isSupported }) {
                            skipped = true
                            tryNext()
                        }
                    }

                    override fun onPlaybackStateChanged(state: Int) {
                        if (state == Player.STATE_READY) Log.i("5broz", "PLAYER READY pos=${p.currentPosition} TIMING tap-to-picture ${System.currentTimeMillis() - launchedAt} ms")
                        if (state == Player.STATE_READY && overlay is Overlay.Loading) overlay = Overlay.None
                        if (state == Player.STATE_READY && syncedFor != currentUrl) {
                            currentKey?.let { com.fivebroz.android.data.Prefs.setLastSource(args.videoId, it) }
                            scrobble("start")
                            autoSync()
                        }
                        if (state == Player.STATE_ENDED) onEnded()
                    }
                })
            }
    }

    private var subs: List<Subtitle> = emptyList()

    // ---- subtitle AutoSync ----
    private var subPrimary: Subtitle? = null
    private var subRef: String? = null
    private var syncedFor: String? = null
    private var currentFile: String? = null
    private var currentHeaders: Map<String, String> = emptyMap()
    /** infoHash or URL of the source now playing. */
    private var currentKey: String? = null
    private var translateOk = false

    /**
     * Once the film is playing: fingerprint the file, ask for subtitles made for it, and
     * either switch to one of those (perfect timing) or align the chosen one to it.
     * Like Nuvio's AutoSync — no AI, only the subtitle text and its timing.
     */
    private fun autoSync() {
        val url = currentUrl ?: return
        syncedFor = url
        val headers = currentHeaders
        val file = currentFile
        lifecycleScope.launch {
            val size = if (url.startsWith("http://127.0.0.1")) engine.fileSize else SubSync.size(url, headers)
            val hash = size?.let { SubSync.hash(url, it, headers) }
            Log.i("5broz", "autosync hash=$hash size=$size file=$file")
            if (hash == null && file == null) return@launch
            val list = Api.subtitles(args.type, args.videoId, hash, size, file)
            if (list.isEmpty() || currentUrl != url) return@launch
            val matched = list.filter { it.hash }
            val merged = (list + subs).distinctBy { it.url }
            val primary = SubSync.best(merged, file) { it.isArabic }
            val ref = (matched.firstOrNull { it.isEnglish } ?: matched.firstOrNull())?.url
            Log.i("5broz", "autosync matched=${matched.size} primary=${primary?.url?.takeLast(12)} hashMatched=${primary?.hash} ref=${ref != null}")
            val changed = primary?.url != subPrimary?.url || (ref != subRef && primary?.hash != true)
            subs = merged
            if (!changed) return@launch
            subPrimary = primary
            subRef = ref.takeIf { primary?.hash != true }
            reloadSubtitles()
            if (com.fivebroz.android.data.Prefs.subLang != "off") flash("تمت مزامنة الترجمة تلقائياً")
        }
    }

    /** Re-applies the subtitle tracks without losing the place in the film. */
    private fun reloadSubtitles() {
        val p = player ?: return
        val url = currentUrl ?: return
        val pos = p.currentPosition
        val playing = p.playWhenReady
        p.setMediaItem(buildItem(url), pos)
        p.playWhenReady = playing
        p.prepare()
    }

    private fun flash(text: String) {
        ui.flash = text
        flashJob?.cancel()
        flashJob = lifecycleScope.launch { delay(1800); ui.flash = null }
    }
    private var skipped = false

    private fun begin() {
        overlay = Overlay.Loading("جارٍ التحضير…")
        tried.clear()
        launchedAt = System.currentTimeMillis()
        subOffset = com.fivebroz.android.data.Prefs.subDelay(args.videoId)
        syncedFor = null
        segments = null
        ui.marks = emptyList()
        introSkipped = false
        recapSkipped = false
        outroShown = false
        nextPrefetched = false
        ui.nightMode = com.fivebroz.android.data.Prefs.nightMode
        val vid = args.videoId
        lifecycleScope.launch {
            val s = Api.segments(vid, args.type != "series")
            if (args.videoId == vid) segments = s
            Log.i("5broz", "introdb $vid intro=${s?.intro} recap=${s?.recap} outro=${s?.outro}")
        }
        lifecycleScope.launch { translateOk = Api.health()?.translate ?: false }
        lifecycleScope.launch {
            subsAsync = async { runCatching { Api.subtitles(args.type, args.videoId) }.getOrDefault(emptyList()) }
            val resumeAsync = async { Db.resumePosition(args.videoId) }
            val ranked = try {
                // Usually already fetched while the title page was open.
                Log.i("5broz", "TIMING begin ${args.type} ${args.videoId}")
                val list = withTimeoutOrNull(6_000) { StreamPrefetch.take(args.type, args.videoId) }
                    ?: withTimeoutOrNull(20_000) { Api.streams(args.type, args.videoId) }
                    ?: withTimeoutOrNull(20_000) { Api.streams(args.type, args.videoId) } // one retry
                    ?: emptyList()
                rank(list, isTv).map { it.stream }
            } catch (_: Exception) {
                emptyList()
            }
            val picked = pendingPick.also { pendingPick = null }
            userPicked = picked != null
            // The source that worked last time for this video goes first (Nuvio's "reuse last link").
            val last = com.fivebroz.android.data.Prefs.lastSource(args.videoId)
            val ordered = ranked.sortedByDescending { (it.url ?: it.infoHash) == last }
            candidates = (listOfNotNull(picked) + ordered)
                .distinctBy { it.url ?: it.infoHash }
                .take(8)
            Log.i("5broz", "TIMING sources in ${System.currentTimeMillis() - launchedAt} ms; candidates=${candidates.size} torrents=${candidates.count { it.url == null }}")
            if (candidates.isEmpty()) {
                overlay = Overlay.Failed("لم نجد مصدراً جاهزاً لهذا العنوان الآن.")
                return@launch
            }
            playNextGroup(resumeAsync.await())
        }
        // The title's page data: backdrop + logo for the loading screen, the episode list for
        // the in-player picker, and (when started from "continue watching") the autoplay queue.
        lifecycleScope.launch {
            val d = runCatching { Api.detail(args.type, args.metaId) }.getOrNull() ?: return@launch
            backdrop = d.base.background
            logo = d.base.logo
            val eps = d.episodes.filter { it.season > 0 }
            allEpisodes = eps
            if (args.type == "series" && args.queueIds.isEmpty() && partyId == null) {
                val after = eps.dropWhile { it.id != args.videoId }.drop(1).take(30)
                if (after.isNotEmpty() && args.queueIds.isEmpty()) {
                    args = args.copy(
                        queueIds = ArrayList(after.map { it.id }),
                        queueLabels = ArrayList(after.map { e -> "م${e.season} · ح${e.episode}" + if (e.title.isNotBlank()) " — ${e.title}" else "" }),
                    )
                }
            }
        }
    }

    // ---- choosing among sources ----
    private val tried = mutableSetOf<Int>()
    private var userPicked = false
    private var launchedAt = 0L
    private var subsAsync: kotlinx.coroutines.Deferred<List<Subtitle>>? = null

    /**
     * The next sources to try: a direct link alone, or up to three torrents raced
     * together (whichever starts sending video first is played — like trying three
     * doors at once instead of waiting at each one).
     */
    private fun nextGroup(): List<Int> {
        val rest = candidates.indices.filter { it !in tried }
        if (rest.isEmpty()) return emptyList()
        val head = rest.first()
        if (candidates[head].url != null) return listOf(head)
        if (userPicked && tried.isEmpty()) return listOf(head) // the person chose this exact source
        return rest.filter { candidates[it].url == null }.take(3)
    }

    private fun playNextGroup(positionSec: Long) {
        torrentJob?.cancel()
        val group = nextGroup()
        if (group.isEmpty()) {
            overlay = Overlay.Failed("تعذّر تشغيل المصادر المتاحة على هذا الجهاز.")
            player?.stop()
            return
        }
        val direct = candidates[group[0]].url
        if (direct != null) {
            tried += group[0]
            currentKey = direct
            stopTorrent()
            val hdrs = candidates[group[0]].headers
            torrentJob = lifecycleScope.launch {
                awaitSubs(1500)
                playUrl(direct, positionSec, hdrs)
                // A direct link that hasn't produced a picture in 15 s is dead or too slow: next.
                delay(15_000)
                if (overlay is Overlay.Loading) {
                    Log.w("5broz", "direct link too slow, trying the next source")
                    tryNext()
                }
            }
            return
        }
        if (!torrentSupported) {
            // A processor the torrent engine wasn't built for: say so instead of crashing.
            overlay = Overlay.Failed("هذا الجهاز لا يدعم التشغيل عبر التورنت.")
            return
        }
        player?.stop()
        if (overlay !is Overlay.Loading) overlay = Overlay.Loading("جارٍ التحضير…")
        torrentJob = lifecycleScope.launch {
            // Live progress while peers are found and the first pieces arrive.
            val ticker = launch {
                while (true) {
                    delay(1000)
                    val st = engine.status()
                    if (overlay is Overlay.Loading && st.peers > 0) {
                        overlay = Overlay.Loading("جارٍ التحضير… ${"%.1f".format(java.util.Locale.US, st.bytesPerSec / 1_048_576f)} م.ب/ث")
                    }
                }
            }
            val items = group.map { i -> candidates[i].let { TorrentEngine.magnet(it.infoHash!!, it.trackers) to it.fileIdx } }
            val t0 = System.currentTimeMillis()
            val result = try {
                // Belt and braces: never sit on "preparing" longer than the race allows.
                // (a detached job, so a stuck native call can't hold the player on this screen)
                @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
                val race = kotlinx.coroutines.GlobalScope.async(Dispatchers.IO) {
                    engine.openFastest(items, timeoutSec = if (group.size > 1) 50 else 40)
                }
                withTimeoutOrNull(if (group.size > 1) 58_000L else 48_000L) { race.await() }
                    ?: run {
                        Log.w("5broz", "race did not return in time — moving on")
                        throw java.io.IOException("race did not return")
                    }
            } catch (e: Exception) {
                Log.w("5broz", "torrent race failed: ${e.message}")
                null
            }
            ticker.cancel()
            if (result == null) {
                tried += group // none of them started
                playNextGroup(positionSec)
                return@launch
            }
            val (w, local) = result
            tried += group[w]
            currentKey = candidates[group[w]].infoHash
            Log.i("5broz", "TIMING first piece in ${System.currentTimeMillis() - t0} ms (race of ${group.size}, winner #${group[w]}) total ${System.currentTimeMillis() - launchedAt} ms file=${engine.fileName}")
            awaitSubs(1200)
            overlay = Overlay.Loading("لحظات…")
            playUrl(local, positionSec)
        }
    }

    private suspend fun awaitSubs(ms: Long) {
        subs = withTimeoutOrNull(ms) { subsAsync?.await() } ?: subs
    }

    private fun stopTorrent() {
        lifecycleScope.launch(Dispatchers.IO) { engine.close() }
    }

    private val torrentSupported: Boolean by lazy {
        try {
            Class.forName("org.libtorrent4j.swig.libtorrent_jni")
            true
        } catch (t: Throwable) {
            Log.w("5broz", "torrent engine unavailable on this CPU: $t")
            false
        }
    }

    private fun buildItem(url: String): MediaItem {
        val file = currentFile
        val primary = subPrimary ?: SubSync.best(subs, file) { it.isArabic }
        val english = SubSync.best(subs, file) { it.isEnglish }
        val second = if (com.fivebroz.android.data.Prefs.subSecond) english?.url else null
        val ref = subRef
        val configs = mutableListOf<MediaItem.SubtitleConfiguration>()
        fun add(src: String, lang: String, label: String, default: Boolean) {
            configs += MediaItem.SubtitleConfiguration.Builder(Uri.parse(src))
                .setMimeType(MimeTypes.TEXT_VTT)
                .setLanguage(lang)
                .setLabel(label)
                .setSelectionFlags(if (default) MC.SELECTION_FLAG_DEFAULT else 0)
                .build()
        }
        fun release(sb: Subtitle) = sb.file?.substringBeforeLast('.')?.take(30)?.let { " · $it" } ?: ""
        if (primary != null) {
            val how = when {
                primary.hash -> " — مطابقة لهذا الملف ✓"
                ref != null -> " — مضبوطة تلقائياً"
                else -> ""
            }
            add(Api.subtitleProxy(primary.url, subOffset, ref.takeIf { !primary.hash }, second), "ar", "العربية$how", true)
        } else if (translateOk && english != null) {
            // No Arabic subtitle exists: translate the best English one.
            add(Api.subtitleProxy(english.url, subOffset, ref.takeIf { !english.hash }, translate = true), "ar", "العربية (ترجمة آلية)", true)
        }
        subs.filter { it.isArabic && it.url != primary?.url }.take(3).forEachIndexed { i, sb ->
            add(Api.subtitleProxy(sb.url, subOffset, ref.takeIf { !sb.hash }), "ar", "العربية ${i + 2}${if (sb.hash) " ✓" else release(sb)}", false)
        }
        subs.filter { it.isEnglish }.sortedByDescending { it.hash }.take(2).forEachIndexed { i, sb ->
            add(Api.subtitleProxy(sb.url, subOffset, ref.takeIf { !sb.hash }), "en", "English${if (sb.hash) " ✓" else release(sb)}", primary == null && !translateOk && i == 0)
        }
        return MediaItem.Builder().setUri(url).setSubtitleConfigurations(configs).build()
    }

    private fun playUrl(url: String, positionSec: Long, headers: Map<String, String> = emptyMap()) {
        if (url != currentUrl) {
            subPrimary = null
            subRef = null
        }
        currentHeaders = headers
        currentFile = if (url.startsWith("http://127.0.0.1")) engine.fileName.takeIf { it.isNotBlank() }
            else candidates.firstOrNull { it.url == url }?.filename ?: Uri.parse(url).lastPathSegment
        player?.release()
        val p = buildPlayer(headers)
        player = p
        playerView.player = p
        p.setPlaybackSpeed(ui.speed)
        // Headset, Bluetooth and remote media keys, and the system media controls, drive this player.
        runCatching {
            val s = session
            if (s == null) session = androidx.media3.session.MediaSession.Builder(this, p).setId("5broz-player").build()
            else s.player = p
        }
        currentUrl = url
        p.setMediaItem(buildItem(url))
        if (positionSec > 30) p.seekTo(positionSec * 1000)
        p.playWhenReady = true
        p.prepare()
        startedAt = System.currentTimeMillis()
        skipped = false
        if (positionSec < 5) introSkipped = false
        startSaving()
    }

    private fun tryNext() {
        val pos = (player?.currentPosition ?: 0) / 1000
        overlay = Overlay.Loading("نجرّب مصدراً آخر…")
        playNextGroup(pos)
    }

    private fun startSaving() {
        saveJob?.cancel()
        saveJob = lifecycleScope.launch {
            while (true) {
                delay(15_000)
                saveNow()
            }
        }
    }

    private fun saveNow() {
        val p = player ?: return
        val pos = p.currentPosition / 1000
        val dur = p.duration.takeIf { it > 0 }?.div(1000) ?: 0
        if (pos < 5) return
        val a = args
        lifecycleScope.launch {
            Db.saveProgress(a.metaId, a.videoId, a.type, a.name, a.poster, pos, dur)
        }
        if (isTv) {
            val app = applicationContext
            lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                WatchNext.update(app, a.type, a.metaId, a.name, a.label, a.poster, pos, dur)
            }
        }
    }

    private fun onEnded() {
        saveNow()
        scrobble("stop")
        if (args.queueIds.isEmpty()) {
            finish()
            return
        }
        if (overlay !is Overlay.Next) showNext()
    }

    private fun showNext() {
        // Three episodes in a row with nobody touching anything: check before carrying on.
        if (autoStreak >= 3 && partyId == null) {
            overlay = Overlay.StillWatching
            player?.pause()
            return
        }
        overlay = Overlay.Next(args.queueLabels.firstOrNull() ?: "الحلقة التالية")
        nextJob?.cancel()
        nextJob = lifecycleScope.launch {
            delay(10_000)
            autoStreak++
            playNext()
        }
    }

    private fun playNext() {
        nextJob?.cancel()
        val ids = args.queueIds
        val labels = args.queueLabels
        if (ids.isEmpty()) return finish()
        args = args.copy(
            videoId = ids[0],
            label = labels.getOrElse(0) { "" },
            streamUrl = null,
            queueIds = ArrayList(ids.drop(1)),
            queueLabels = ArrayList(labels.drop(1)),
        )
        begin()
    }

    // ---------- remote / keys ----------

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (overlay is Overlay.Next || overlay is Overlay.Failed || overlay is Overlay.StillWatching || ui.subsOpen || ui.chatOpen || ui.audioOpen || ui.speedOpen || ui.moreOpen || ui.episodesOpen) {
            if ((ui.subsOpen || ui.chatOpen || ui.audioOpen || ui.speedOpen || ui.moreOpen || ui.episodesOpen) && event.keyCode == KeyEvent.KEYCODE_BACK) {
                if (event.action == KeyEvent.ACTION_UP) {
                    ui.subsOpen = false
                    ui.chatOpen = false
                    ui.audioOpen = false
                    ui.speedOpen = false
                    ui.moreOpen = false
                    ui.episodesOpen = false
                }
                return true
            }
            return super.dispatchKeyEvent(event)
        }
        val down = event.action == KeyEvent.ACTION_DOWN
        when (event.keyCode) {
            KeyEvent.KEYCODE_BACK -> {
                // First back hides the controls; the second leaves.
                if (ui.visible) {
                    if (!down) ui.visible = false
                    return true
                }
                return super.dispatchKeyEvent(event)
            }
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                if (down) togglePlay()
                return true
            }
            // Dedicated play / pause keys (Bluetooth remotes, headsets) only ever do what they say.
            KeyEvent.KEYCODE_MEDIA_PLAY -> {
                if (down && player?.isPlaying == false) togglePlay()
                return true
            }
            KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                if (down && player?.isPlaying == true) togglePlay()
                return true
            }
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> { if (down) seekBy(30); return true }
            KeyEvent.KEYCODE_MEDIA_REWIND -> { if (down) seekBy(-30); return true }
        }
        if (!ui.visible) {
            // Controls hidden: arrows skip (physical left = back), OK pauses — like Netflix on TV.
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT -> { if (down) seekBy(-10); return true }
                KeyEvent.KEYCODE_DPAD_RIGHT -> { if (down) seekBy(10); return true }
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                    // During an episode's opening, OK means "skip intro" (the button is highlighted on screen).
                    if (down) { if (ui.showSkipIntro) skipIntro() else { togglePlay(); show() } }
                    return true
                }
                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_MENU -> {
                    if (down) show()
                    return true
                }
            }
        } else {
            poke()
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onPause() {
        super.onPause()
        saveNow()
        if (!isInPictureInPictureMode) player?.pause()
    }

    /** Tells Trakt what's playing (only does anything once the profile linked Trakt). */
    private fun scrobble(event: String) {
        val p = player ?: return
        val dur = p.duration.takeIf { it > 0 } ?: return
        val progress = p.currentPosition * 100.0 / dur
        val id = args.videoId
        if (!id.startsWith("tt") || partyId != null && !isHost) return
        kotlinx.coroutines.GlobalScope.launch {
            Api.trakt("scrobble", org.json.JSONObject().put("id", id).put("event", event).put("progress", progress))
        }
    }

    override fun onStop() {
        super.onStop()
        scrobble("pause")
        // Leaving the app, or closing the picture-in-picture window, stops the film.
        saveNow()
        player?.pause()
    }

    override fun onDestroy() {
        torrentJob?.cancel()
        partyJob?.cancel()
        Thread { engine.close() }.start()
        saveJob?.cancel()
        nextJob?.cancel()
        session?.release()
        session = null
        runCatching { dynamics?.release() }
        player?.release()
        player = null
        if (fpsSwitched) runCatching { window.attributes = window.attributes.apply { preferredDisplayModeId = 0 } }
        if (current?.get() === this) current = null
        super.onDestroy()
    }

    // ---------- overlay UI ----------

    @androidx.compose.runtime.Composable
    private fun OverlayUi() {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            FiveBrozTheme {
                Box(Modifier.fillMaxSize()) {
                    val title = if (args.label.isNotBlank()) "${args.name} — ${args.label}" else args.name
                    // In picture-in-picture only the picture (and its subtitles) shows.
                    if (ui.inPip) return@Box
                    if (overlay is Overlay.None) PlayerControls(ui, title, isTv, this@PlayerActivity)
                    if (overlay !is Overlay.None) {
                        Box(
                            Modifier.fillMaxWidth()
                                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)))
                                .padding(horizontal = 32.dp, vertical = 22.dp),
                        ) {
                            Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    when (val o = overlay) {
                        // Cinematic wait: the title's backdrop, its logo and a thin moving line.
                        is Overlay.Loading -> Box(Modifier.fillMaxSize()) {
                            val art = backdrop ?: args.poster
                            if (art != null) {
                                com.fivebroz.android.ui.Img(art.replace("/w780/", "/w1280/"), Modifier.fillMaxSize().kenBurns())
                                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.55f), 0.5f to Color.Black.copy(alpha = 0.45f), 1f to Color.Black.copy(alpha = 0.9f))))
                            }
                            Column(
                                Modifier.align(Alignment.Center).padding(horizontal = 40.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                com.fivebroz.android.ui.TitleArt(args.name, logo, 110.dp, 360.dp, MaterialTheme.typography.headlineMedium, center = true)
                                if (args.label.isNotBlank()) {
                                    VSpace(8.dp)
                                    Text(args.label, color = C.Paper.copy(alpha = 0.85f), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                VSpace(26.dp)
                                androidx.compose.material3.LinearProgressIndicator(
                                    Modifier.width(220.dp).height(2.dp),
                                    color = C.Paper,
                                    trackColor = Color.White.copy(alpha = 0.15f),
                                )
                                VSpace(12.dp)
                                Text(o.text, color = C.Paper.copy(alpha = 0.6f), style = MaterialTheme.typography.labelLarge)
                            }
                        }
                        is Overlay.Failed -> {
                            val fr = remember { FocusRequester() }
                            LaunchedEffect(Unit) { runCatching { fr.requestFocus() } }
                            Column(
                                Modifier.align(Alignment.Center).background(C.Ink900.copy(alpha = 0.92f), RoundedCornerShape(18.dp)).padding(28.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(o.text, style = MaterialTheme.typography.titleMedium)
                                VSpace(18.dp)
                                GoldButton("رجوع", { finish() }, focusRequester = fr)
                            }
                        }
                        is Overlay.Next -> {
                            val fr = remember { FocusRequester() }
                            LaunchedEffect(Unit) { runCatching { fr.requestFocus() } }
                            Column(
                                Modifier.align(Alignment.BottomEnd).padding(36.dp)
                                    .background(C.Ink900.copy(alpha = 0.92f), RoundedCornerShape(18.dp)).padding(22.dp),
                            ) {
                                Text("التالي", color = C.Gold, style = MaterialTheme.typography.labelLarge)
                                Text(o.label, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                VSpace(14.dp)
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    GoldButton("شغّل الآن", { autoStreak = 0; playNext() }, focusRequester = fr)
                                    GoldButton("إنهاء", { finish() }, filled = false)
                                }
                            }
                        }
                        Overlay.StillWatching -> {
                            val fr = remember { FocusRequester() }
                            LaunchedEffect(Unit) { runCatching { fr.requestFocus() } }
                            Column(
                                Modifier.align(Alignment.Center).background(C.Ink900.copy(alpha = 0.94f), RoundedCornerShape(18.dp)).padding(28.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text("هل ما زلت تشاهد؟", style = MaterialTheme.typography.titleLarge)
                                VSpace(18.dp)
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    GoldButton("نعم، تابع", { autoStreak = 0; playNext() }, focusRequester = fr)
                                    GoldButton("إنهاء", { finish() }, filled = false)
                                }
                            }
                        }
                        Overlay.None -> Unit
                    }
                }
            }
        }
    }
}
