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
            subtitleView?.apply {
                setStyle(
                    CaptionStyleCompat(
                        AColor.WHITE, AColor.TRANSPARENT, AColor.TRANSPARENT,
                        CaptionStyleCompat.EDGE_TYPE_OUTLINE, AColor.BLACK,
                        ResourcesCompat.getFont(this@PlayerActivity, R.font.cairo),
                    ),
                )
                setFractionalTextSize(SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * com.fivebroz.android.data.Prefs.subScale)
            }
        }
        root.addView(playerView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        root.addView(
            ComposeView(this).apply { setContent { OverlayUi() } },
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
        )
        setContentView(root)

        begin()
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
                    ui.showSkipIntro = args.type == "series" && !introSkipped && !ui.inPip && overlay is Overlay.None &&
                        (partyId == null || isHost) && sec in 5..180 && ui.duration > 10 * 60_000
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
        introSkipped = true
        ui.showSkipIntro = false
        val p = player ?: return
        seekTo(p.currentPosition + 85_000)
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

    /** Rebuild the subtitles with a time shift, keeping the video where it is. */
    override fun shiftSubtitles(deltaSec: Float) {
        subOffset = ((subOffset + deltaSec) * 10).toInt() / 10f
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

    private fun buildPlayer(): ExoPlayer {
        // Torrent pieces can take a while to arrive, so allow long reads.
        val http = OkHttpDataSource.Factory(
            Net.client.newBuilder().readTimeout(150, java.util.concurrent.TimeUnit.SECONDS).build(),
        )
        // Start with the subtitle choice this profile made last time.
        val lang = com.fivebroz.android.data.Prefs.subLang
        val selector = DefaultTrackSelector(this).apply {
            setParameters(
                buildUponParameters()
                    .setPreferredTextLanguage(if (lang == "en") "en" else "ar")
                    .setTrackTypeDisabled(MC.TRACK_TYPE_TEXT, lang == "off"),
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
                        if (state == Player.STATE_ENDED) onEnded()
                    }
                })
            }
    }

    private var subs: List<Subtitle> = emptyList()
    private var skipped = false

    private fun begin() {
        overlay = Overlay.Loading("جارٍ التحضير…")
        tried.clear()
        launchedAt = System.currentTimeMillis()
        lifecycleScope.launch {
            subsAsync = async { runCatching { Api.subtitles(args.type, args.videoId) }.getOrDefault(emptyList()) }
            val resumeAsync = async { Db.resumePosition(args.videoId) }
            val ranked = try {
                // Usually already fetched while the title page was open.
                rank(StreamPrefetch.take(args.type, args.videoId) ?: Api.streams(args.type, args.videoId), isTv).map { it.stream }
            } catch (_: Exception) {
                emptyList()
            }
            val picked = pendingPick.also { pendingPick = null }
            userPicked = picked != null
            candidates = (listOfNotNull(picked) + ranked)
                .distinctBy { it.url ?: it.infoHash }
                .take(8)
            Log.i("5broz", "TIMING sources in ${System.currentTimeMillis() - launchedAt} ms; candidates=${candidates.size} torrents=${candidates.count { it.url == null }}")
            if (candidates.isEmpty()) {
                overlay = Overlay.Failed("لم نجد مصدراً جاهزاً لهذا العنوان الآن.")
                return@launch
            }
            playNextGroup(resumeAsync.await())
        }
        // Started from "continue watching": work out the following episodes for autoplay.
        if (args.type == "series" && args.queueIds.isEmpty() && partyId == null) {
            lifecycleScope.launch {
                val d = runCatching { Api.detail(args.type, args.metaId) }.getOrNull() ?: return@launch
                val eps = d.episodes.filter { it.season > 0 }
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
            stopTorrent()
            torrentJob = lifecycleScope.launch {
                awaitSubs(1500)
                playUrl(direct, positionSec)
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
                withContext(Dispatchers.IO) { engine.openFastest(items, timeoutSec = if (group.size > 1) 50 else 40) }
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
        val arabic = setOf("ara", "ar", "arabic", "العربية")
        val chosen = subs.filter { it.lang.lowercase() in arabic }.take(4) +
            subs.filter { it.lang.lowercase() in setOf("eng", "en", "english") }.take(2)
        var nAr = 0
        var nEn = 0
        val subConfigs = chosen.mapIndexed { i, s ->
            val ar = s.lang.lowercase() in arabic
            val label = if (ar) "العربية ${++nAr}" else "English ${++nEn}"
            MediaItem.SubtitleConfiguration.Builder(Uri.parse(Api.subtitleProxy(s.url, subOffset)))
                .setMimeType(MimeTypes.TEXT_VTT)
                .setLanguage(if (ar) "ar" else "en")
                .setLabel(label)
                .setSelectionFlags(if (i == 0 && ar) MC.SELECTION_FLAG_DEFAULT else 0)
                .build()
        }
        return MediaItem.Builder().setUri(url).setSubtitleConfigurations(subConfigs).build()
    }

    private fun playUrl(url: String, positionSec: Long) {
        player?.release()
        val p = buildPlayer()
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
        if (args.queueIds.isEmpty()) {
            finish()
            return
        }
        overlay = Overlay.Next(args.queueLabels.firstOrNull() ?: "الحلقة التالية")
        nextJob?.cancel()
        nextJob = lifecycleScope.launch {
            delay(10_000)
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
        if (overlay is Overlay.Next || overlay is Overlay.Failed || ui.subsOpen || ui.chatOpen || ui.audioOpen || ui.speedOpen) {
            if ((ui.subsOpen || ui.chatOpen || ui.audioOpen || ui.speedOpen) && event.keyCode == KeyEvent.KEYCODE_BACK) {
                if (event.action == KeyEvent.ACTION_UP) {
                    ui.subsOpen = false
                    ui.chatOpen = false
                    ui.audioOpen = false
                    ui.speedOpen = false
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

    override fun onStop() {
        super.onStop()
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
        player?.release()
        player = null
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
                        is Overlay.Loading -> Column(
                            Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            CircularProgressIndicator(color = C.Paper, strokeWidth = 2.dp)
                            VSpace(16.dp)
                            Text(o.text, color = C.Muted, style = MaterialTheme.typography.bodyLarge)
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
                                    GoldButton("شغّل الآن", { playNext() }, focusRequester = fr)
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
