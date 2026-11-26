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
                    .putStringArrayListExtra("queueLabels", a.queueLabels),
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

    private var candidates: List<Stream> = emptyList()
    private var torrentJob: Job? = null
    private val engine by lazy { Torrents.get(this) }
    private var attempt = 0
    private var startedAt = 0L
    private var saveJob: Job? = null
    private var nextJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
        )
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
                setFractionalTextSize(SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * if (isTv) 1.1f else 1.2f)
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
                }
                if (ui.visible && ui.playing && !ui.subsOpen && System.currentTimeMillis() - ui.lastPoke > 4500) {
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
    }

    override fun seekTo(ms: Long) {
        val p = player ?: return
        p.seekTo(ms.coerceIn(0, (p.duration.takeIf { it > 0 } ?: Long.MAX_VALUE)))
        ui.position = p.currentPosition
        poke()
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

    override fun subtitleOptions(): List<SubOption> {
        val p = player ?: return emptyList()
        val off = p.trackSelectionParameters.disabledTrackTypes.contains(MC.TRACK_TYPE_TEXT)
        val out = mutableListOf(
            SubOption("بدون ترجمة", off) {
                p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
                    .setTrackTypeDisabled(MC.TRACK_TYPE_TEXT, true).build()
            },
        )
        p.currentTracks.groups.filter { it.type == MC.TRACK_TYPE_TEXT }.forEachIndexed { i, g ->
            val f = g.getTrackFormat(0)
            val label = f.label ?: f.language?.let { if (it.startsWith("ar")) "العربية" else it } ?: "ترجمة ${i + 1}"
            out += SubOption(label, !off && g.isSelected) {
                p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
                    .setTrackTypeDisabled(MC.TRACK_TYPE_TEXT, false)
                    .setOverrideForType(androidx.media3.common.TrackSelectionOverride(g.mediaTrackGroup, 0))
                    .build()
            }
        }
        return out
    }

    override fun setSubtitleSize(scale: Float) {
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

    // ---------- playback ----------

    private fun buildPlayer(): ExoPlayer {
        // Torrent pieces can take a while to arrive, so allow long reads.
        val http = OkHttpDataSource.Factory(
            Net.client.newBuilder().readTimeout(150, java.util.concurrent.TimeUnit.SECONDS).build(),
        )
        val selector = DefaultTrackSelector(this).apply {
            setParameters(buildUponParameters().setPreferredTextLanguage("ar"))
        }
        return ExoPlayer.Builder(this)
            .setRenderersFactory(
                DefaultRenderersFactory(this)
                    .setEnableDecoderFallback(true)
                    .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER),
            )
            .setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(this, http)))
            .setTrackSelector(selector)
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
                        if (state == Player.STATE_READY) Log.i("5broz", "PLAYER READY pos=${p.currentPosition}")
                        if (state == Player.STATE_READY && overlay is Overlay.Loading) overlay = Overlay.None
                        if (state == Player.STATE_ENDED) onEnded()
                    }
                })
            }
    }

    private var subs: List<Subtitle> = emptyList()
    private var skipped = false

    private fun begin() {
        overlay = Overlay.Loading("جارٍ تجهيز أفضل مصدر…")
        attempt = 0
        lifecycleScope.launch {
            val subsAsync = async { Api.subtitles(args.type, args.videoId) }
            val ranked = try {
                rank(Api.streams(args.type, args.videoId), isTv).map { it.stream }
            } catch (_: Exception) {
                emptyList()
            }
            val picked = pendingPick.also { pendingPick = null }
            candidates = (listOfNotNull(picked) + ranked)
                .distinctBy { it.url ?: it.infoHash }
                .take(6)
            Log.i("5broz", "candidates=${candidates.size} torrents=${candidates.count { it.url == null }}")
            if (candidates.isEmpty()) {
                overlay = Overlay.Failed("لم نجد مصدراً جاهزاً لهذا العنوان الآن.")
                return@launch
            }
            subs = withTimeoutOrNull(6000) { subsAsync.await() } ?: emptyList()
            val resume = Db.resumePosition(args.videoId)
            play(candidates[0], resume)
        }
    }

    /** Direct links play straight away; torrents go through the built-in engine first. */
    private fun play(stream: Stream, positionSec: Long) {
        torrentJob?.cancel()
        val direct = stream.url
        if (direct != null) {
            stopTorrent()
            playUrl(direct, positionSec)
            return
        }
        val hash = stream.infoHash ?: return tryNext()
        player?.stop()
        overlay = Overlay.Loading("جارٍ الاتصال بشبكة التورنت…")
        torrentJob = lifecycleScope.launch {
            // Live progress while peers are found and the first pieces arrive.
            val ticker = launch {
                while (true) {
                    delay(1000)
                    val st = engine.status()
                    if (overlay is Overlay.Loading) {
                        overlay = Overlay.Loading(
                            if (st.peers == 0) "جارٍ البحث عن مصادر…"
                            else "متصل بـ ${st.peers} مصدر · ${"%.1f".format(st.bytesPerSec / 1_048_576f)} م.ب/ث",
                        )
                    }
                }
            }
            Log.i("5broz", "torrent open $hash idx=${stream.fileIdx}")
            val local = try {
                withContext(Dispatchers.IO) {
                    engine.close()
                    engine.open(TorrentEngine.magnet(hash, stream.trackers), stream.fileIdx)
                }
            } catch (e: Exception) {
                Log.w("5broz", "torrent failed: ${e.message}")
                null
            }
            Log.i("5broz", "torrent ready url=$local file=${engine.fileName} ${engine.status()}")
            ticker.cancel()
            if (local == null) tryNext() else {
                overlay = Overlay.Loading("جارٍ التحميل المسبق…")
                playUrl(local, positionSec)
            }
        }
    }

    private fun stopTorrent() {
        lifecycleScope.launch(Dispatchers.IO) { engine.close() }
    }

    private fun playUrl(url: String, positionSec: Long) {
        player?.release()
        val p = buildPlayer()
        player = p
        playerView.player = p

        val arabic = setOf("ara", "ar", "arabic", "العربية")
        val chosen = subs.filter { it.lang.lowercase() in arabic }.take(4) +
            subs.filter { it.lang.lowercase() in setOf("eng", "en", "english") }.take(2)
        val subConfigs = chosen.mapIndexed { i, s ->
            val ar = s.lang.lowercase() in arabic
            MediaItem.SubtitleConfiguration.Builder(Uri.parse(Api.subtitleProxy(s.url)))
                .setMimeType(MimeTypes.TEXT_VTT)
                .setLanguage(if (ar) "ar" else "en")
                .setLabel(if (ar) "العربية ${i + 1}" else "English")
                .setSelectionFlags(if (i == 0 && ar) MC.SELECTION_FLAG_DEFAULT else 0)
                .build()
        }
        val item = MediaItem.Builder().setUri(url).setSubtitleConfigurations(subConfigs).build()
        p.setMediaItem(item)
        if (positionSec > 30) p.seekTo(positionSec * 1000)
        p.playWhenReady = true
        p.prepare()
        startedAt = System.currentTimeMillis()
        skipped = false
        startSaving()
    }

    private fun tryNext() {
        val pos = (player?.currentPosition ?: 0) / 1000
        attempt++
        if (attempt >= candidates.size) {
            overlay = Overlay.Failed("تعذّر تشغيل المصادر المتاحة على هذا الجهاز.")
            player?.stop()
            return
        }
        overlay = Overlay.Loading("المصدر لم يعمل — نجرّب مصدراً آخر…")
        play(candidates[attempt], pos)
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
        if (overlay is Overlay.Next || overlay is Overlay.Failed || ui.subsOpen) {
            if (ui.subsOpen && event.keyCode == KeyEvent.KEYCODE_BACK) {
                if (event.action == KeyEvent.ACTION_UP) ui.subsOpen = false
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
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                if (down) togglePlay()
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
                    if (down) { togglePlay(); show() }
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

    override fun onDestroy() {
        torrentJob?.cancel()
        Thread { engine.close() }.start()
        saveJob?.cancel()
        nextJob?.cancel()
        player?.release()
        player = null
        super.onDestroy()
    }

    // ---------- overlay UI ----------

    @androidx.compose.runtime.Composable
    private fun OverlayUi() {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            FiveBrozTheme {
                Box(Modifier.fillMaxSize()) {
                    val title = if (args.label.isNotBlank()) "${args.name} — ${args.label}" else args.name
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
                            CircularProgressIndicator(color = C.Gold, strokeWidth = 2.dp)
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
