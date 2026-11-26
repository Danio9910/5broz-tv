package com.fivebroz.android.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import coil.compose.AsyncImage
import com.fivebroz.android.data.Api
import com.fivebroz.android.data.Db
import com.fivebroz.android.data.Detail
import com.fivebroz.android.data.Episode
import com.fivebroz.android.data.Preview
import com.fivebroz.android.player.PlayArgs
import com.fivebroz.android.player.PlayerActivity
import com.fivebroz.android.player.StreamInfo
import com.fivebroz.android.player.rank
import kotlinx.coroutines.launch

private fun epLabel(e: Episode) = "م${e.season} · ح${e.episode}" + if (e.title.isNotBlank()) " — ${e.title}" else ""

@Composable
fun DetailScreen(type: String, id: String, onOpen: (Preview) -> Unit, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val isTv = LocalIsTv.current
    val g = gutter()
    val scope = rememberCoroutineScope()

    var detail by remember { mutableStateOf<Detail?>(null) }
    var failed by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }
    var finished by remember { mutableStateOf<Set<String>>(emptySet()) }
    var resumeVideo by remember { mutableStateOf<String?>(null) }
    var season by remember { mutableIntStateOf(1) }
    var sourcesFor by remember { mutableStateOf<Pair<String, String>?>(null) } // videoId to label
    val playFr = remember { FocusRequester() }

    LaunchedEffect(type, id) {
        try {
            val d = Api.detail(type, id)
            if (d == null) failed = true else {
                detail = d
                season = d.episodes.firstOrNull { it.season > 0 }?.season ?: 1
            }
        } catch (_: Exception) {
            failed = true
        }
    }

    // Refresh "my list" / progress each time the screen comes back (e.g. after watching).
    fun refresh(d: Detail) {
        scope.launch {
            saved = Db.inLibrary(d.base.id)
            finished = Db.finishedIds(d.base.id)
            resumeVideo = Db.continueWatching().firstOrNull { it.item.id == d.base.id }?.videoId
        }
    }
    LaunchedEffect(detail) { detail?.let { refresh(it) } }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { detail?.let { refresh(it) } }
    LaunchedEffect(detail) { if (detail != null && isTv) runCatching { playFr.requestFocus() } }

    BackHandler(enabled = sourcesFor != null) { sourcesFor = null }

    val d = detail
    if (failed) return Centered("تعذّر تحميل هذا العنوان.")
    if (d == null) return Centered("…")

    val isSeries = d.base.type == "series"
    val episodes = d.episodes.filter { it.season > 0 }
    val canPlay = d.base.id.startsWith("tt")

    // Which episode the main button plays: the one in progress, else the first unwatched.
    val target: Episode? = if (!isSeries) null else
        episodes.firstOrNull { it.id == resumeVideo }
            ?: episodes.firstOrNull { it.id !in finished }
            ?: episodes.firstOrNull()

    fun play(videoId: String, label: String, pick: com.fivebroz.android.data.Stream? = null) {
        PlayerActivity.pendingPick = pick
        val after = if (isSeries) episodes.dropWhile { it.id != videoId }.drop(1).take(30) else emptyList()
        PlayerActivity.start(
            ctx,
            PlayArgs(
                type = d.base.type,
                metaId = d.base.id,
                name = d.base.name,
                poster = d.base.poster,
                videoId = videoId,
                label = label,
                queueIds = ArrayList(after.map { it.id }),
                queueLabels = ArrayList(after.map(::epLabel)),
            ),
        )
    }

    val mainVideo = if (isSeries) target?.id else d.base.id
    val mainLabel = if (isSeries) target?.let(::epLabel) ?: "" else ""
    val mainText = when {
        !canPlay -> null
        isSeries && target == null -> null
        resumeVideo != null && resumeVideo == mainVideo -> "متابعة" + if (isSeries) " م${target!!.season} ح${target.episode}" else ""
        isSeries -> "تشغيل م${target!!.season} ح${target.episode}"
        else -> "تشغيل"
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize()) {
            item("head") {
                Box(Modifier.fillMaxWidth().height(if (isTv) 430.dp else 470.dp)) {
                    AsyncImage(
                        model = d.base.background ?: d.base.poster,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    Box(
                        Modifier.fillMaxSize().background(
                            Brush.verticalGradient(0f to Color.Transparent, 0.5f to C.Ink950.copy(alpha = 0.35f), 1f to C.Ink950),
                        ),
                    )
                    Box(
                        Modifier.fillMaxSize().background(
                            Brush.horizontalGradient(0f to Color.Transparent, 0.45f to Color.Transparent, 1f to C.Ink950.copy(alpha = 0.9f)),
                        ),
                    )
                    Column(
                        Modifier.align(Alignment.BottomStart)
                            .padding(horizontal = g, vertical = 8.dp)
                            .fillMaxWidth(if (isTv) 0.55f else 1f),
                    ) {
                        Text(d.base.name, style = MaterialTheme.typography.displayLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        VSpace(8.dp)
                        Meta(
                            listOf(
                                d.base.year,
                                d.base.rating?.let { "★ $it" },
                                d.runtime,
                                d.certification,
                                d.genres.take(3).joinToString("، ").takeIf { it.isNotBlank() },
                            ),
                        )
                        if (!d.base.description.isNullOrBlank()) {
                            VSpace(10.dp)
                            Text(
                                d.base.description,
                                style = MaterialTheme.typography.bodyLarge,
                                color = C.Paper.copy(alpha = 0.85f),
                                maxLines = if (isTv) 3 else 4,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        VSpace(18.dp)
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (mainText != null && mainVideo != null) {
                                GoldButton(mainText, { play(mainVideo, mainLabel) }, focusRequester = playFr)
                            }
                            GoldButton(if (saved) "✓ في قائمتي" else "+ قائمتي", {
                                val now = !saved
                                saved = now
                                scope.launch { Db.setInLibrary(d.base, now) }
                            }, filled = false, focusRequester = if (mainText == null) playFr else null)
                            if (mainVideo != null && canPlay) {
                                GoldButton("المصادر", { sourcesFor = mainVideo to mainLabel }, filled = false)
                            }
                        }
                        if (!canPlay) {
                            VSpace(10.dp)
                            Text("لا تتوفر مصادر تشغيل لهذا العنوان بعد.", color = C.Muted, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            if (d.cast.isNotEmpty() || d.director.isNotEmpty()) item("credits") {
                Column(Modifier.padding(horizontal = g, vertical = 12.dp).widthIn(max = 760.dp)) {
                    if (d.director.isNotEmpty()) Text("إخراج: " + d.director.joinToString("، "), color = C.Muted, style = MaterialTheme.typography.bodyMedium)
                    if (d.cast.isNotEmpty()) Text("بطولة: " + d.cast.take(6).joinToString("، "), color = C.Muted, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }

            if (isSeries && episodes.isNotEmpty()) {
                val seasons = episodes.map { it.season }.distinct().sorted()
                item("seasons") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = g, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(seasons) { s ->
                            Focusable(onClick = { season = s }, shape = RoundedCornerShape(50), lift = 1.05f) { f ->
                                Text(
                                    "الموسم $s",
                                    modifier = Modifier
                                        .background(if (s == season) C.Gold else if (f) C.Paper.copy(alpha = 0.16f) else C.Ink800)
                                        .padding(horizontal = 16.dp, vertical = 7.dp),
                                    color = if (s == season) C.Ink950 else C.Paper,
                                    style = MaterialTheme.typography.labelLarge,
                                )
                            }
                        }
                    }
                }
                item("episodes") {
                    val list = episodes.filter { it.season == season }
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = g, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        items(list, key = { it.id }) { e ->
                            EpisodeCard(e, e.id in finished, e.id == resumeVideo) { play(e.id, epLabel(e)) }
                        }
                    }
                }
            }

            if (d.collection.size > 1) item("collection") {
                PosterRow(d.collectionName ?: "السلسلة", d.collection, onOpen, g)
            }
            if (d.related.isNotEmpty()) item("related") {
                PosterRow("قد يعجبك أيضاً", d.related, onOpen, g)
            }
            item("end") { VSpace(40.dp) }
        }

        if (!isTv) {
            // Touch devices get an explicit way back.
            Box(Modifier.statusBarsPadding().padding(12.dp)) {
                GoldButton("رجوع", onBack, filled = false)
            }
        }

        sourcesFor?.let { (vid, label) ->
            SourcesPanel(d.base.type, vid, onClose = { sourcesFor = null }) { stream ->
                sourcesFor = null
                play(vid, label, stream)
            }
        }
    }
}

@Composable
private fun EpisodeCard(e: Episode, watched: Boolean, current: Boolean, onClick: () -> Unit) {
    val w = if (LocalIsTv.current) 240.dp else 200.dp
    Column(Modifier.width(w)) {
        Focusable(onClick, Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
            Box(Modifier.fillMaxSize().background(C.Ink800)) {
                if (e.thumbnail != null) {
                    AsyncImage(e.thumbnail, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                }
                if (watched) {
                    Text(
                        "✓ شوهدت",
                        Modifier.align(Alignment.TopStart).padding(8.dp)
                            .background(C.Ink950.copy(alpha = 0.8f), RoundedCornerShape(50))
                            .padding(horizontal = 10.dp, vertical = 2.dp),
                        color = C.Gold,
                        style = MaterialTheme.typography.labelMedium,
                    )
                } else if (current) {
                    Text(
                        "تكملة",
                        Modifier.align(Alignment.TopStart).padding(8.dp)
                            .background(C.Gold, RoundedCornerShape(50))
                            .padding(horizontal = 10.dp, vertical = 2.dp),
                        color = C.Ink950,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
        VSpace(8.dp)
        Text("الحلقة ${e.episode}", color = C.Gold, style = MaterialTheme.typography.labelMedium)
        Text(e.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (!e.overview.isNullOrBlank()) {
            Text(e.overview, color = C.Muted, style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** A side panel listing every playable source, best first. */
@Composable
private fun SourcesPanel(type: String, videoId: String, onClose: () -> Unit, onPick: (com.fivebroz.android.data.Stream) -> Unit) {
    val isTv = LocalIsTv.current
    var list by remember { mutableStateOf<List<StreamInfo>?>(null) }
    val fr = remember { FocusRequester() }
    LaunchedEffect(videoId) {
        list = try {
            rank(Api.streams(type, videoId), isTv)
        } catch (_: Exception) {
            emptyList()
        }
    }
    LaunchedEffect(list) { if (!list.isNullOrEmpty()) runCatching { fr.requestFocus() } }

    Box(Modifier.fillMaxSize().background(C.Ink950.copy(alpha = 0.7f))) {
        Column(
            Modifier.align(Alignment.CenterEnd).fillMaxHeight()
                .width(if (isTv) 440.dp else 340.dp)
                .background(C.Ink900)
                .padding(vertical = 20.dp),
        ) {
            Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("اختر المصدر", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                GoldButton("إغلاق", onClose, filled = false)
            }
            VSpace(12.dp)
            val l = list
            when {
                l == null -> Text("جارٍ البحث…", color = C.Muted, modifier = Modifier.padding(20.dp))
                l.isEmpty() -> Text("لا توجد مصادر الآن.", color = C.Muted, modifier = Modifier.padding(20.dp))
                else -> LazyColumn(
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(l.size) { i ->
                        val s = l[i]
                        Focusable(
                            onClick = { onPick(s.stream) },
                            modifier = Modifier.fillMaxWidth(),
                            lift = 1.02f,
                            focusRequester = if (i == 0) fr else null,
                        ) { f ->
                            Column(
                                Modifier.fillMaxWidth()
                                    .background(if (f) C.Ink700 else C.Ink800)
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(s.quality, color = C.Gold, style = MaterialTheme.typography.labelLarge)
                                    if (s.hdr) Text("HDR", color = C.Paper, style = MaterialTheme.typography.labelMedium)
                                    if (s.size != null) Text(s.size, color = C.Muted, style = MaterialTheme.typography.labelMedium)
                                    if (s.cached) Text("فوري", color = C.Gold, style = MaterialTheme.typography.labelMedium)
                                    if (s.isTorrent) Text("تورنت · ${s.seeders ?: 0} مشارك", color = C.Muted, style = MaterialTheme.typography.labelMedium)
                                    if (i == 0) Text("الأنسب", color = C.Ink950, style = MaterialTheme.typography.labelMedium,
                                        modifier = Modifier.background(C.Gold, RoundedCornerShape(50)).padding(horizontal = 8.dp))
                                }
                                Text(
                                    s.stream.filename ?: s.stream.title.lineSequence().firstOrNull() ?: s.stream.name,
                                    color = C.Muted,
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

