package com.fivebroz.android.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.fivebroz.android.data.Api
import com.fivebroz.android.data.Db
import com.fivebroz.android.data.Detail
import com.fivebroz.android.data.Episode
import com.fivebroz.android.data.Preview
import com.fivebroz.android.data.Stream
import com.fivebroz.android.player.PlayArgs
import com.fivebroz.android.player.PlayerActivity
import com.fivebroz.android.player.StreamInfo
import com.fivebroz.android.player.rank
import kotlinx.coroutines.launch

/** Age gate: kids see only kids-rated titles, family profiles skip adult ratings. */
fun blockedForProfile(d: Detail): Boolean {
    val cert = (d.certification ?: "").uppercase()
    val kidsGenres = d.genres.any { it.contains("رسوم") || it.contains("عائل") || it.contains("أطفال") || it.contains("Animation") || it.contains("Family") || it.contains("Kids") }
    val kidsOk = cert in setOf("G", "PG", "TV-Y", "TV-Y7", "TV-Y7-FV", "TV-G", "TV-PG", "0", "6", "7") || (cert.isEmpty() && kidsGenres)
    val adult = cert in setOf("R", "NC-17", "TV-MA", "18", "18+", "R18", "X")
    val g = d.guide
    // The content guide tightens it: kids never get sexual content or strong violence/fear;
    // the family profile skips strong sexual content.
    val kidsGuideBad = (g["sex"] ?: 0) >= 1 || (g["violence"] ?: 0) >= 2 || (g["scary"] ?: 0) >= 2 || (g["drugs"] ?: 0) >= 2
    val familyGuideBad = (g["sex"] ?: 0) >= 2
    return (com.fivebroz.android.data.Prefs.profileKids && (!kidsOk || kidsGuideBad)) ||
        (com.fivebroz.android.data.Prefs.profileFamily && (adult || familyGuideBad))
}

/** "مشاهد غير لائقة: كثير · عنف: متوسط" — only what's present. */
fun guideLine(g: Map<String, Int>): String? {
    val names = listOf("sex" to "مشاهد غير لائقة", "violence" to "عنف", "drugs" to "مخدرات وكحول", "scary" to "مخيف")
    val parts = names.mapNotNull { (k, label) ->
        when (g[k] ?: 0) {
            1 -> "$label: خفيف"
            2 -> "$label: كثير"
            else -> null
        }
    }
    if (g.isEmpty()) return null
    return if (parts.isEmpty()) "دليل المحتوى: مناسب للعائلة" else "دليل المحتوى — " + parts.joinToString(" · ")
}

fun epLabel(e: Episode) = "م${e.season} · ح${e.episode}" + if (e.title.isNotBlank()) " — ${e.title}" else ""

private fun unavailableText(m: DetailModel): String = when {
    m.d.base.id.startsWith("tt") && com.fivebroz.android.data.Prefs.profileKids -> "هذا العنوان غير متاح في ملف الأطفال."
    m.d.base.id.startsWith("tt") && com.fivebroz.android.data.Prefs.profileFamily -> "هذا العنوان غير متاح في الملف العائلي."
    else -> "لا توجد مصادر لهذا العنوان حالياً — غالباً لم يُعرض بعد."
}

/** Personal touches on a title: thumbs rating and "suggest for tonight". */
private class Extras(
    val rating: Int,
    val onRate: (Int) -> Unit,
    val suggested: Boolean,
    val onSuggest: () -> Unit,
    val onPerson: (com.fivebroz.android.data.CastMember) -> Unit,
    /** "rate" or "more" — the small action sheets. */
    val openSheet: (String) -> Unit,
    /** Opens the trailer on YouTube; null when the title has none. */
    val onTrailer: (() -> Unit)?,
)

private fun openTrailer(ctx: android.content.Context, key: String) {
    val app = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("vnd.youtube:$key"))
    val web = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://www.youtube.com/watch?v=$key"))
    try {
        ctx.startActivity(app)
    } catch (_: Exception) {
        try {
            ctx.startActivity(web)
        } catch (_: Exception) {
            android.widget.Toast.makeText(ctx, "ثبّت تطبيق يوتيوب لمشاهدة الإعلان.", android.widget.Toast.LENGTH_LONG).show()
        }
    }
}

/** Everything both layouts need, computed once. */
private class DetailModel(
    val d: Detail,
    val episodes: List<Episode>,
    val seasons: List<Int>,
    val mainVideo: String?,
    val mainLabel: String,
    val mainText: String?,
    val canPlay: Boolean,
)

@Composable
fun DetailScreen(type: String, id: String, onOpen: (Preview) -> Unit, onBack: () -> Unit, onPerson: (String) -> Unit = {}) {
    val ctx = LocalContext.current
    val isTv = LocalIsTv.current
    val scope = rememberCoroutineScope()

    // Often already fetched ahead (focused, or on the home screen): draw it straight away.
    var detail by remember { mutableStateOf<Detail?>(Api.detailNow(type, id)) }
    var failed by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }
    var finished by remember { mutableStateOf<Set<String>>(emptySet()) }
    var resumeVideo by remember { mutableStateOf<String?>(null) }
    var resumeProgress by remember { mutableStateOf(0f) }
    var season by remember { mutableIntStateOf(1) }
    var sourcesFor by remember { mutableStateOf<Pair<String, String>?>(null) } // videoId to label
    var rating by remember { mutableIntStateOf(0) }
    var suggested by remember { mutableStateOf(false) }
    var sheet by remember { mutableStateOf<String?>(null) }

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
            rating = Db.rating(d.base.id)
            finished = Db.finishedIds(d.base.id)
            val r = Db.continueWatching().firstOrNull { it.item.id == d.base.id }
            resumeVideo = r?.videoId
            resumeProgress = r?.progress ?: 0f
        }
    }
    LaunchedEffect(detail) { detail?.let { refresh(it) } }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { detail?.let { refresh(it) } }

    BackHandler(enabled = sourcesFor != null) { sourcesFor = null }

    val d = detail
    if (failed) return Centered("تعذّر تحميل هذا العنوان.")
    if (d == null) return DetailSkeleton()

    val isSeries = d.base.type == "series"
    val episodes = d.episodes.filter { it.season > 0 }
    val blockedByProfile = blockedForProfile(d)
    val canPlay = d.base.id.startsWith("tt") && !blockedByProfile

    // Which episode the main button plays: the one in progress, else the first unwatched.
    val target: Episode? = if (!isSeries) null else
        episodes.firstOrNull { it.id == resumeVideo }
            ?: episodes.firstOrNull { it.id !in finished }
            ?: episodes.firstOrNull()

    val mainVideo = if (isSeries) target?.id else d.base.id
    val mainLabel = if (isSeries) target?.let(::epLabel) ?: "" else ""
    val mainText = when {
        !canPlay -> null
        isSeries && target == null -> null
        resumeVideo != null && resumeVideo == mainVideo -> "متابعة" + if (isSeries) " م${target!!.season} ح${target.episode}" else ""
        isSeries -> "تشغيل م${target!!.season} ح${target.episode}"
        else -> "تشغيل"
    }

    // Check up front whether anything is playable, so we never offer a dead "play".
    var available by remember(mainVideo) { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(mainVideo) {
        if (mainVideo != null && canPlay) {
            // The same request doubles as the prefetch the player uses when play is pressed.
            com.fivebroz.android.player.StreamPrefetch.warm(ctx, d.base.type, mainVideo)
            available = try {
                com.fivebroz.android.player.StreamPrefetch.result(d.base.type, mainVideo)?.isNotEmpty()
            } catch (_: Exception) {
                null
            }
        }
    }

    fun play(videoId: String, label: String, pick: Stream? = null) {
        // "Always let me choose" (Settings): show the sources instead of starting the fastest.
        if (pick == null && com.fivebroz.android.data.Prefs.streamMode == "manual") {
            sourcesFor = videoId to label
            return
        }
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

    val m = DetailModel(
        d, episodes, episodes.map { it.season }.distinct().sorted(),
        mainVideo, mainLabel, if (available == false) null else mainText, canPlay,
    )
    val toggleSaved = {
        val now = !saved
        saved = now
        scope.launch { Db.setInLibrary(d.base, now) }
        // Following a series: that's when new-episode notices become worth asking for.
        if (now && d.base.type == "series") com.fivebroz.android.Notify.askWhenUseful(ctx)
        Unit
    }
    val openSources = { if (mainVideo != null) sourcesFor = mainVideo to mainLabel }
    val extras = Extras(
        rating = rating,
        onRate = { v ->
            val nv = if (rating == v) 0 else v
            rating = nv
            scope.launch { Db.setRating(d.base.id, nv) }
        },
        suggested = suggested,
        onSuggest = {
            if (!suggested) {
                suggested = true
                scope.launch { Db.suggest(d.base) }
            }
        },
        onPerson = { onPerson(it.id) },
        openSheet = { sheet = it },
        onTrailer = d.trailer?.let { key -> { openTrailer(ctx, key) } },
    )

    when (sheet) {
        "rate" -> ActionSheet(
            "قيّم «${d.base.name}»",
            listOf(
                SheetItem(Icons.ThumbUp, "أعجبني", extras.rating == 1) { extras.onRate(1) },
                SheetItem(Icons.ThumbDown, "ليس لي", extras.rating == -1) { extras.onRate(-1) },
            ),
        ) { sheet = null }
        "more" -> {
            val aired = episodes.filter { e -> e.released?.let { it <= java.time.LocalDate.now().toString() } ?: true }
            ActionSheet(
                d.base.name,
                buildList {
                    if (mainVideo != null && canPlay && available != false) add(SheetItem(Icons.Sources, "اختيار المصدر") { openSources() })
                    if (!isTv && mainVideo != null && canPlay && available != false) {
                        add(SheetItem(Icons.Download, "تنزيل للمشاهدة بدون نت") {
                            downloadForOffline(ctx, d.base.type, d.base.id, d.base.name, d.base.poster, mainVideo, mainLabel)
                            scope.launch { ItemEvents.toast = "بدأ التنزيل — تلقاه في «التنزيلات»" }
                        })
                        add(SheetItem(Icons.Tv, "افتحه على التلفزيون") {
                            scope.launch {
                                val tvs = com.fivebroz.android.data.Household.tvs(ctx)
                                val ok = tvs.firstOrNull()?.let { t -> com.fivebroz.android.data.Household.send(t.id, "open", org.json.JSONObject().put("type", d.base.type).put("id", d.base.id)) } ?: false
                                ItemEvents.toast = if (ok) "انفتح على ${tvs.first().name}" else "افتح 5broz على التلفزيون أولاً"
                            }
                        })
                    }
                    add(SheetItem(if (suggested) Icons.Check else Icons.Tonight, if (suggested) "مقترح لليلة" else "اقترح لليلة", suggested) { extras.onSuggest() })
                    if (canPlay && aired.size > 3) add(SheetItem(Icons.Next, "حلقة عشوائية") { aired.random().let { e -> play(e.id, epLabel(e), null) } })
                    add(SheetItem(Icons.Check, "شاهدته") {
                        scope.launch { if (Db.markWatched(d.base)) { ItemEvents.resumeVersion++; refresh(d) } }
                    })
                },
            ) { sheet = null }
        }
    }

    if (isTv) {
        TvDetail(m, saved, finished, resumeVideo, season, { season = it }, available, ::play, toggleSaved, openSources, onOpen, extras)
        sourcesFor?.let { (vid, label) ->
            SourcesSidePanel(d.base.type, vid, onClose = { sourcesFor = null }) { s ->
                sourcesFor = null
                play(vid, label, s)
            }
        }
    } else {
        PhoneDetail(
            m, saved, finished, resumeVideo, resumeProgress, season, { season = it }, available,
            ::play, toggleSaved, openSources, onOpen, onBack, extras,
        )
        sourcesFor?.let { (vid, label) ->
            SourcesSheet(d.base.type, vid, onClose = { sourcesFor = null }) { s ->
                sourcesFor = null
                play(vid, label, s)
            }
        }
    }
}

@Composable
private fun DetailSkeleton() {
    val g = gutter()
    Column(Modifier.fillMaxSize()) {
        Skeleton(Modifier.fillMaxWidth().aspectRatio(16f / 10f), androidx.compose.ui.graphics.RectangleShape)
        VSpace(18.dp)
        Skeleton(Modifier.padding(horizontal = g).size(220.dp, 28.dp))
        VSpace(12.dp)
        Skeleton(Modifier.padding(horizontal = g).size(160.dp, 14.dp))
        VSpace(22.dp)
        Skeleton(Modifier.padding(horizontal = g).fillMaxWidth().height(46.dp), RoundedCornerShape(50))
    }
}

// ---------------------------------------------------------------- phone

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PhoneDetail(
    m: DetailModel,
    saved: Boolean,
    finished: Set<String>,
    resumeVideo: String?,
    resumeProgress: Float,
    season: Int,
    onSeason: (Int) -> Unit,
    available: Boolean?,
    play: (String, String, Stream?) -> Unit,
    toggleSaved: () -> Unit,
    openSources: () -> Unit,
    onOpen: (Preview) -> Unit,
    onBack: () -> Unit,
    x: Extras,
) {
    val d = m.d
    val g = gutter()
    var pickSeason by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    val tablet = LocalIsTablet.current
    val headH = (minOf(LocalConfiguration.current.screenWidthDp.toFloat(), 460f) * 1.2f).dp

    val glow = rememberGlow(Cuts.now(d.base.id)?.poster ?: d.base.poster ?: d.base.background)
    Box(
        Modifier.fillMaxSize().background(C.Ink950).background(
            Brush.verticalGradient(0f to glow.copy(alpha = 0.85f), 0.45f to glow.copy(alpha = 0.18f), 0.8f to C.Ink950),
        ),
    ) {
        val list = androidx.compose.foundation.lazy.rememberLazyListState()
        val density = androidx.compose.ui.platform.LocalDensity.current
        val headPx = with(density) { headH.toPx() }
        // 0 at the top, 1 once the header has scrolled away
        val past by androidx.compose.runtime.remember {
            androidx.compose.runtime.derivedStateOf {
                if (list.firstVisibleItemIndex > 0) 1f else (list.firstVisibleItemScrollOffset / headPx).coerceIn(0f, 1f)
            }
        }
        LazyColumn(Modifier.fillMaxSize(), state = list, contentPadding = PaddingValues(bottom = 28.dp)) {
            item("head") {
                val artKey = remember { Shared.fresh() }
                val resuming = resumeVideo == m.mainVideo && resumeProgress > 0f
                FilmCard(
                    id = d.base.id,
                    poster = d.base.poster ?: d.base.background,
                    glow = glow,
                    playLabel = m.mainText,
                    playSub = if (resuming) remainingText(d, resumeProgress) else null,
                    onPlay = if (m.mainText != null && m.mainVideo != null) ({ play(m.mainVideo, m.mainLabel, null) }) else null,
                    modifier = Modifier.statusBarsPadding().padding(horizontal = g),
                    maxWidth = if (tablet) 460.dp else 440.dp,
                    topRoom = 52.dp,
                    artModifier = Modifier.sharedArt(artKey),
                ) {
                    if (resuming) {
                        Box(Modifier.padding(horizontal = 40.dp)) { ProgressLine(resumeProgress) }
                        VSpace(14.dp)
                    }
                    TitleArt(d.base.name, d.base.logo, 64.dp, 250.dp, MaterialTheme.typography.headlineMedium.copy(fontSize = 26.sp, lineHeight = 34.sp), center = true, modifier = Modifier.padding(horizontal = 20.dp), mono = true)
                    VSpace(8.dp)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        d.base.rating?.let { Text("★ $it", color = C.Gold, style = MaterialTheme.typography.labelLarge) }
                        Meta(listOf(d.base.year, d.runtime, if (m.seasons.size > 1) "${m.seasons.size} مواسم" else null))
                        d.certification?.let { CertBadge(it) }
                    }
                    guideLine(d.guide)?.let { Text(it, Modifier.padding(top = 6.dp, start = 20.dp, end = 20.dp), color = if (d.guide.values.any { v -> v >= 2 }) C.Gold else C.Muted, style = MaterialTheme.typography.labelMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                    if (m.mainText == null && (available == false || !m.canPlay)) {
                        Text(unavailableText(m), Modifier.padding(top = 10.dp, start = 20.dp, end = 20.dp), color = C.Muted, style = MaterialTheme.typography.bodyMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                    VSpace(14.dp)
                    Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                        val w = Modifier.weight(1f)
                        // Four clear actions; the rest live under "more".
                        ActionIcon(if (saved) Icons.Check else Icons.Plus, "قائمتي", toggleSaved, on = saved, modifier = w, fly = d.base.poster)
                        ActionIcon(
                            when (x.rating) { 1 -> Icons.ThumbUp; -1 -> Icons.ThumbDown; else -> Icons.Star },
                            when (x.rating) { 1 -> "أعجبني"; -1 -> "ليس لي"; else -> "تقييم" },
                            { x.openSheet("rate") }, on = x.rating != 0, modifier = w, cheer = x.rating.takeIf { it == 1 },
                        )
                        x.onTrailer?.let { ActionIcon(Icons.Trailer, "الإعلان", it, modifier = w) }
                        ActionIcon(Icons.More, "المزيد", { x.openSheet("more") }, modifier = w)
                    }
                }
            }
            item("info") {
                Column(Modifier.padding(horizontal = g + 4.dp).widthIn(max = 760.dp)) {
                    ratingsLine(d.ratings)?.let { Text(it, Modifier.padding(top = 14.dp), color = C.Muted, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    if (!d.base.description.isNullOrBlank()) {
                        VSpace(16.dp)
                        Focusable({ expanded = !expanded }, ring = false, lift = 1f, shape = RoundedCornerShape(4.dp)) {
                            Text(
                                d.base.description,
                                modifier = Modifier.animateContentSize(),
                                style = MaterialTheme.typography.bodyLarge,
                                color = C.Paper.copy(alpha = 0.86f),
                                maxLines = if (expanded) 40 else 3,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    if (d.cast.isNotEmpty()) {
                        VSpace(10.dp)
                        Text("بطولة: " + d.cast.take(4).joinToString("، "), color = C.Muted, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (d.director.isNotEmpty()) {
                        Text("إخراج: " + d.director.joinToString("، "), color = C.Muted, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    VSpace(10.dp)
                }
            }

            if (m.episodes.isNotEmpty()) {
                item("season") {
                    Row(Modifier.padding(horizontal = g, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (m.seasons.size > 1) {
                            Focusable({ pickSeason = true }, shape = RoundedCornerShape(8.dp), lift = 1f, ring = false) {
                                Row(
                                    Modifier.background(C.Ink800).padding(horizontal = 14.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Text("الموسم $season", style = MaterialTheme.typography.labelLarge)
                                    Ic(Icons.ChevronDown, 16.dp)
                                }
                            }
                        } else {
                            SectionTitle("الحلقات")
                        }
                    }
                }
                episodeItems(m.episodes.filter { it.season == season }, finished, resumeVideo, g) { e -> play(e.id, epLabel(e), null) }
            }
            if (d.people.isNotEmpty() && !com.fivebroz.android.data.Prefs.profileKids) item("cast") {
                VSpace(14.dp)
                CastRow(d.people, g, x.onPerson)
            }
            if (d.collection.size > 1) item("collection") {
                VSpace(14.dp)
                CollectionShelf(d.collectionName ?: "السلسلة", d.collection, onOpen, g)
            }
            if (d.related.isNotEmpty() && !com.fivebroz.android.data.Prefs.profileKids) item("related") {
                VSpace(14.dp)
                PosterRow("قد يعجبك أيضاً", d.related, onOpen, g)
            }
        }

        // compact title bar: the logo settles small at the top once the header is gone
        // Only "is the bar there" is read while composing; how faded it is is read while drawing,
        // so scrolling doesn't rebuild the page every frame.
        val barOn by androidx.compose.runtime.remember { androidx.compose.runtime.derivedStateOf { past > 0.75f } }
        if (barOn) {
            Box(
                Modifier.fillMaxWidth().graphicsLayer { alpha = ((past - 0.75f) * 4f).coerceIn(0f, 1f) }
                    .background(Brush.verticalGradient(0f to C.Ink950, 1f to C.Ink950.copy(alpha = 0.82f)))
                    .statusBarsPadding().height(62.dp).padding(start = 62.dp, end = 16.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Box(Modifier.graphicsLayer { translationY = (1f - ((past - 0.75f) * 4f).coerceIn(0f, 1f)) * 18f }) {
                    TitleArt(d.base.name, d.base.logo, 34.dp, 150.dp, MaterialTheme.typography.titleMedium)
                }
            }
        }
        Focusable(onBack, Modifier.statusBarsPadding().padding(12.dp), shape = CircleShape, lift = 1f, ring = false) {
            Box(Modifier.size(38.dp).background(C.Ink950.copy(alpha = 0.55f)), contentAlignment = Alignment.Center) {
                Ic(Icons.Back, 20.dp)
            }
        }
    }

    if (pickSeason) {
        ModalBottomSheet(
            onDismissRequest = { pickSeason = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = C.Ink900,
        ) {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 12.dp)) {
                m.seasons.forEach { s ->
                    Focusable({ onSeason(s); pickSeason = false }, Modifier.fillMaxWidth(), RoundedCornerShape(0.dp), lift = 1f, ring = false) {
                        Text(
                            "الموسم $s",
                            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 14.dp),
                            style = MaterialTheme.typography.titleMedium,
                            color = if (s == season) C.Gold else C.Paper,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CertBadge(text: String) {
    Text(
        text,
        Modifier.border(1.dp, C.Paper.copy(alpha = 0.35f), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 1.dp),
        style = MaterialTheme.typography.labelMedium,
        color = C.Paper.copy(alpha = 0.8f),
    )
}

@Composable
private fun ActionIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    on: Boolean = false,
    modifier: Modifier = Modifier,
    fly: String? = null, // a poster that flies up and away when this turns on ("added to my list")
    cheer: Any? = null, // celebrate when this changes to a non-null value (e.g. a like chosen from a sheet)
) {
    // Turning something on (my list, a like) gets a small celebration: a pop, a gold sparkle and a tap.
    var armed by remember { mutableStateOf(false) }
    val pop = remember { androidx.compose.animation.core.Animatable(1f) }
    val burst = remember { androidx.compose.animation.core.Animatable(1f) }
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    var firstCheer by remember { mutableStateOf(true) }
    var go by remember { mutableIntStateOf(0) }
    LaunchedEffect(on) { if (on && armed) { armed = false; go++ } }
    LaunchedEffect(cheer) { if (firstCheer) firstCheer = false else if (cheer != null) go++ }
    LaunchedEffect(go) {
        if (go == 0) return@LaunchedEffect
        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
        burst.snapTo(0f)
        launch { burst.animateTo(1f, androidx.compose.animation.core.tween(650)) }
        pop.animateTo(1.35f, androidx.compose.animation.core.tween(120))
        pop.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.4f, stiffness = 400f))
    }
    Focusable({ armed = !on; onClick() }, modifier, shape = RoundedCornerShape(10.dp), lift = 1f, ring = false) {
        Column(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(contentAlignment = Alignment.Center) {
                if (burst.value < 1f) {
                    androidx.compose.foundation.Canvas(Modifier.size(64.dp)) {
                        val t = burst.value
                        val c = center
                        for (k in 0 until 10) {
                            val ang = Math.toRadians(k * 36.0 + 18 * (k % 2))
                            val r = size.minDimension * (0.18f + 0.34f * t) * if (k % 2 == 0) 1f else 0.75f
                            val p = androidx.compose.ui.geometry.Offset(c.x + (Math.cos(ang) * r).toFloat(), c.y + (Math.sin(ang) * r).toFloat())
                            drawCircle(C.Gold.copy(alpha = (1f - t).coerceIn(0f, 1f)), (if (k % 2 == 0) 3.2f else 2.2f) * density * (1f - t * 0.5f), p)
                        }
                    }
                    if (fly != null) {
                        Img(
                            fly,
                            Modifier.size(34.dp, 50.dp).graphicsLayer {
                                val t = burst.value
                                translationY = -t * 260f * density
                                scaleX = 1f - t * 0.6f
                                scaleY = 1f - t * 0.6f
                                alpha = 1f - t
                                rotationZ = -t * 12f
                            }.clipRounded(4.dp),
                        )
                    }
                }
                Box(Modifier.graphicsLayer { scaleX = pop.value; scaleY = pop.value }) { Ic(icon, 24.dp, if (on) C.Gold else C.Paper) }
            }
            Text(label, style = MaterialTheme.typography.labelMedium, color = C.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Vertical episode list — thumbnail, number and title, synopsis. */
private fun LazyListScope.episodeItems(
    list: List<Episode>,
    finished: Set<String>,
    resumeVideo: String?,
    g: Dp,
    onPlay: (Episode) -> Unit,
) {
    items(list, key = { it.id }) { e ->
        Focusable({ onPlay(e) }, Modifier.fillMaxWidth(), RoundedCornerShape(0.dp), lift = 1f, ring = false) {
            Column(Modifier.fillMaxWidth().padding(horizontal = g, vertical = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.width(136.dp).aspectRatio(16f / 9f).clipRounded(6.dp).background(C.Ink800)) {
                        if (e.thumbnail != null) Img(e.thumbnail, Modifier.fillMaxSize().then(if (com.fivebroz.android.data.Prefs.spoilerBlur && e.id !in finished && e.id != resumeVideo) Modifier.blur(14.dp) else Modifier))
                        RatingBadge(e.rating, Modifier.align(Alignment.TopStart))
                        Box(
                            Modifier.align(Alignment.Center).size(30.dp).background(C.Ink950.copy(alpha = 0.55f), CircleShape)
                                .border(1.dp, C.Paper.copy(alpha = 0.8f), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) { Ic(Icons.Play, 13.dp) }
                        if (e.id in finished) ProgressLine(1f, Modifier.align(Alignment.BottomCenter))
                        else if (e.id == resumeVideo) ProgressLine(0.5f, Modifier.align(Alignment.BottomCenter))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("${e.episode}. ${e.title}", style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (e.id in finished) Text("شوهدت", color = C.Muted, style = MaterialTheme.typography.labelMedium)
                        else e.released?.let { Text(it, color = C.Muted, style = MaterialTheme.typography.labelMedium) }
                    }
                }
                if (!e.overview.isNullOrBlank()) {
                    VSpace(8.dp)
                    Text(e.overview, color = C.Muted, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

private fun Modifier.clipRounded(r: Dp) = this.then(Modifier.clip(RoundedCornerShape(r)))

// ---------------------------------------------------------------- TV

@OptIn(ExperimentalLayoutApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun TvDetail(
    m: DetailModel,
    saved: Boolean,
    finished: Set<String>,
    resumeVideo: String?,
    season: Int,
    onSeason: (Int) -> Unit,
    available: Boolean?,
    play: (String, String, Stream?) -> Unit,
    toggleSaved: () -> Unit,
    openSources: () -> Unit,
    onOpen: (Preview) -> Unit,
    x: Extras,
) {
    val d = m.d
    val g = 56.dp
    val playFr = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { playFr.requestFocus() } }

    val glow = rememberGlow(Cuts.now(d.base.id)?.poster ?: d.base.poster ?: d.base.background)
    val artKey = remember { Shared.fresh() }
    Box(Modifier.fillMaxSize().background(C.Ink950)) {
        Img(d.base.background ?: d.base.poster, Modifier.fillMaxSize().graphicsLayer { alpha = 0.35f })
        TrailerBackground(d.trailer, Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(glow.copy(alpha = 0.75f), C.Ink950.copy(alpha = 0.6f)), radius = 1400f)))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.45f to Color.Transparent, 1f to C.Ink950)))

        androidx.compose.runtime.CompositionLocalProvider(androidx.compose.foundation.gestures.LocalBringIntoViewSpec provides MinimalScroll) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 60.dp)) {
            item("head") {
                Row(Modifier.fillMaxWidth().padding(start = g, end = g, top = 28.dp, bottom = 18.dp), verticalAlignment = Alignment.Bottom) {
                    Column(Modifier.weight(1f).padding(bottom = 30.dp)) {
                        TitleArt(d.base.name, d.base.logo, 110.dp, 420.dp, MaterialTheme.typography.displayLarge)
                        VSpace(10.dp)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            d.base.rating?.let { Text("★ $it", color = C.Gold, style = MaterialTheme.typography.labelLarge) }
                            Meta(listOf(d.base.year, d.runtime, d.genres.take(3).joinToString("، ").takeIf { it.isNotBlank() }))
                            d.certification?.let { CertBadge(it) }
                        }
                        ratingsLine(d.ratings)?.let { Text(it, Modifier.padding(top = 4.dp), color = C.Muted, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        guideLine(d.guide)?.let { Text(it, Modifier.padding(top = 4.dp), color = if (d.guide.values.any { v -> v >= 2 }) C.Gold else C.Muted, style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                        if (!d.base.description.isNullOrBlank()) {
                            VSpace(12.dp)
                            Text(d.base.description, style = MaterialTheme.typography.bodyLarge, color = C.Paper.copy(alpha = 0.86f), maxLines = 4, overflow = TextOverflow.Ellipsis)
                        }
                        if (d.cast.isNotEmpty()) {
                            VSpace(8.dp)
                            Text("بطولة: " + d.cast.take(5).joinToString("، "), color = C.Muted, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        VSpace(20.dp)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            GoldButton(
                                if (saved) "في قائمتي" else "قائمتي", toggleSaved, filled = false,
                                icon = if (saved) Icons.Check else Icons.Plus,
                                focusRequester = if (m.mainText == null) playFr else null,
                            )
                            GoldButton(
                                when (x.rating) { 1 -> "أعجبني"; -1 -> "ليس لي"; else -> "تقييم" }, { x.openSheet("rate") }, filled = false,
                                icon = when (x.rating) { 1 -> Icons.ThumbUp; -1 -> Icons.ThumbDown; else -> Icons.Star },
                            )
                            x.onTrailer?.let { GoldButton("الإعلان", it, filled = false, icon = Icons.Trailer) }
                            GoldButton("المزيد", { x.openSheet("more") }, filled = false, icon = Icons.More)
                        }
                        if (available == false || !m.canPlay) {
                            VSpace(12.dp)
                            Text(unavailableText(m), color = C.Muted, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    androidx.compose.foundation.layout.Spacer(Modifier.width(48.dp))
                    FilmCard(
                        id = d.base.id,
                        poster = d.base.poster ?: d.base.background,
                        glow = glow,
                        playLabel = m.mainText,
                        playSub = null,
                        onPlay = if (m.mainText != null && m.mainVideo != null) ({ play(m.mainVideo, m.mainLabel, null) }) else null,
                        modifier = Modifier.width(250.dp),
                        maxWidth = 250.dp,
                        topRoom = 12.dp,
                        artModifier = Modifier.sharedArt(artKey),
                        playFocus = if (m.mainText != null) playFr else null,
                    ) {}
                }
            }
            if (m.episodes.isNotEmpty()) {
                item("seasons") {
                    LazyRow(contentPadding = PaddingValues(horizontal = g, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(m.seasons) { s ->
                            Focusable({ onSeason(s) }, shape = RoundedCornerShape(50), lift = 1.05f, ring = false) { f ->
                                Text(
                                    "الموسم $s",
                                    Modifier.background(if (f) C.Paper else if (s == season) C.Paper.copy(alpha = 0.18f) else Color.Transparent)
                                        .padding(horizontal = 16.dp, vertical = 7.dp),
                                    color = if (f) C.Ink950 else if (s == season) C.Paper else C.Muted,
                                    style = MaterialTheme.typography.labelLarge,
                                )
                            }
                        }
                    }
                }
                item("episodes") {
                    LazyRow(contentPadding = PaddingValues(horizontal = g, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        items(m.episodes.filter { it.season == season }, key = { it.id }) { e ->
                            TvEpisodeCard(e, e.id in finished, e.id == resumeVideo) { play(e.id, epLabel(e), null) }
                        }
                    }
                }
            }
            if (d.people.isNotEmpty() && !com.fivebroz.android.data.Prefs.profileKids) item("cast") { CastRow(d.people, g, x.onPerson) }
            if (d.collection.size > 1) item("collection") { CollectionShelf(d.collectionName ?: "السلسلة", d.collection, onOpen, g) }
            if (d.related.isNotEmpty() && !com.fivebroz.android.data.Prefs.profileKids) item("related") { PosterRow("قد يعجبك أيضاً", d.related, onOpen, g) }
        }
        }
    }
}

/** "Rotten Tomatoes 91% · Metacritic 80 · Letterboxd 4.2" when MDBList is set up. */
private fun ratingsLine(r: Map<String, Double>): String? {
    if (r.isEmpty()) return null
    val names = linkedMapOf("tomatoes" to "Rotten Tomatoes", "popcorn" to "الجمهور", "metacritic" to "Metacritic", "letterboxd" to "Letterboxd", "trakt" to "Trakt")
    val parts = names.mapNotNull { (k, label) ->
        r[k]?.let { v ->
            val shown = when (k) {
                "tomatoes", "popcorn", "trakt" -> "${v.toInt()}%"
                "letterboxd" -> "%.1f".format(java.util.Locale.US, v)
                else -> v.toInt().toString()
            }
            "$label $shown"
        }
    }
    return parts.joinToString(" · ").ifBlank { null }
}

/** "بقي 47 د" for a film in progress. */
private fun remainingText(d: Detail, progress: Float): String? {
    if (d.base.type == "series") return null
    val mins = d.runtime?.filter { it.isDigit() }?.toIntOrNull() ?: return null
    val left = (mins * (1 - progress)).toInt()
    return if (left > 0) "بقي $left د" else null
}

/** Highlights the episodes people rated highest (9+ on TMDB). */
@Composable
private fun RatingBadge(r: Double?, modifier: Modifier) {
    if (r == null || r < 9.0) return
    Text(
        "★ %.1f".format(java.util.Locale.US, r),
        modifier.padding(5.dp).background(C.Gold, RoundedCornerShape(5.dp)).padding(horizontal = 5.dp, vertical = 1.dp),
        color = C.Ink950,
        style = MaterialTheme.typography.labelMedium,
    )
}

@Composable
private fun TvEpisodeCard(e: Episode, watched: Boolean, current: Boolean, onClick: () -> Unit) {
    Column(Modifier.width(250.dp)) {
        Focusable(onClick, Modifier.fillMaxWidth().aspectRatio(16f / 9f), RoundedCornerShape(8.dp)) {
            Box(Modifier.fillMaxSize().background(C.Ink800)) {
                if (e.thumbnail != null) Img(e.thumbnail, Modifier.fillMaxSize().then(if (com.fivebroz.android.data.Prefs.spoilerBlur && !watched && !current) Modifier.blur(14.dp) else Modifier))
                RatingBadge(e.rating, Modifier.align(Alignment.TopStart))
                if (watched) ProgressLine(1f, Modifier.align(Alignment.BottomCenter))
                else if (current) ProgressLine(0.5f, Modifier.align(Alignment.BottomCenter))
            }
        }
        VSpace(8.dp)
        Text("الحلقة ${e.episode}" + if (watched) " — شوهدت" else "", color = C.Muted, style = MaterialTheme.typography.labelMedium)
        Text(e.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (!e.overview.isNullOrBlank()) {
            Text(e.overview, color = C.Muted, style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

// ---------------------------------------------------------------- sources

private class SourcesState(val list: List<StreamInfo>?, val failed: Boolean, val retry: () -> Unit)

@Composable
private fun rememberSources(type: String, videoId: String): SourcesState {
    val isTv = LocalIsTv.current
    var list by remember { mutableStateOf<List<StreamInfo>?>(null) }
    var failed by remember { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }
    LaunchedEffect(videoId, attempt) {
        failed = false
        list = null
        // Reuse what the title page already fetched; otherwise ask, but never wait forever.
        val got = (if (attempt == 0) kotlinx.coroutines.withTimeoutOrNull(6_000) { com.fivebroz.android.player.StreamPrefetch.result(type, videoId) } else null)
            ?: kotlinx.coroutines.withTimeoutOrNull(20_000) { runCatching { Api.streams(type, videoId) }.getOrNull() }
        if (got == null) failed = true else list = rank(got, isTv)
    }
    return SourcesState(list, failed) { attempt++ }
}

@Composable
private fun SourceRow(s: StreamInfo, best: Boolean, fr: FocusRequester?, onPick: (Stream) -> Unit) {
    Focusable({ onPick(s.stream) }, Modifier.fillMaxWidth(), RoundedCornerShape(10.dp), lift = 1.02f, focusRequester = fr, ring = false) { f ->
        Column(Modifier.fillMaxWidth().background(if (f) C.Ink600 else C.Ink800).padding(horizontal = 14.dp, vertical = 11.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(s.quality, color = C.Paper, style = MaterialTheme.typography.labelLarge)
                if (s.hdr) Text("HDR", color = C.Paper, style = MaterialTheme.typography.labelMedium)
                if (s.size != null) Text(s.size, color = C.Muted, style = MaterialTheme.typography.labelMedium)
                if (s.cached) Text("فوري", color = C.Paper.copy(alpha = 0.8f), style = MaterialTheme.typography.labelMedium)
                if (s.isTorrent) Text("${s.seeders ?: 0} مشارك", color = C.Muted, style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.weight(1f))
                if (best) Text("الأنسب", color = C.Ink950, style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.background(C.Paper, RoundedCornerShape(50)).padding(horizontal = 8.dp))
            }
            Text(
                s.stream.filename ?: s.stream.title.lineSequence().firstOrNull() ?: s.stream.name,
                color = C.Muted, style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SourcesSheet(type: String, videoId: String, onClose: () -> Unit, onPick: (Stream) -> Unit) {
    val src = rememberSources(type, videoId)
    val list = src.list
    ModalBottomSheet(onDismissRequest = onClose, containerColor = C.Ink900) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.75f).navigationBarsPadding().padding(horizontal = 16.dp)) {
            Text("اختر المصدر", style = MaterialTheme.typography.titleLarge)
            VSpace(12.dp)
            when {
                src.failed -> Column {
                    Text("تعذّر جلب المصادر. تأكد من الاتصال.", color = C.Muted)
                    VSpace(12.dp)
                    GoldButton("إعادة المحاولة", src.retry)
                }
                list == null -> repeat(4) { Skeleton(Modifier.fillMaxWidth().height(58.dp).padding(bottom = 8.dp)) }
                list.isEmpty() -> Text("لا توجد مصادر الآن.", color = C.Muted)
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(list.size) { i -> SourceRow(list[i], i == 0, null, onPick) }
                }
            }
        }
    }
}

@Composable
private fun SourcesSidePanel(type: String, videoId: String, onClose: () -> Unit, onPick: (Stream) -> Unit) {
    val src = rememberSources(type, videoId)
    val list = src.list
    val fr = remember { FocusRequester() }
    LaunchedEffect(list) { if (!list.isNullOrEmpty()) runCatching { fr.requestFocus() } }
    Box(Modifier.fillMaxSize().background(C.Ink950.copy(alpha = 0.6f))) {
        Column(
            Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(460.dp).background(C.Ink900).padding(vertical = 28.dp),
        ) {
            Row(Modifier.padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("اختر المصدر", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                GoldButton("إغلاق", onClose, filled = false)
            }
            VSpace(14.dp)
            when {
                src.failed -> Column(Modifier.padding(24.dp)) {
                    Text("تعذّر جلب المصادر. تأكد من الاتصال.", color = C.Muted)
                    VSpace(12.dp)
                    GoldButton("إعادة المحاولة", src.retry)
                }
                list == null -> Text("جارٍ البحث…", color = C.Muted, modifier = Modifier.padding(24.dp))
                list.isEmpty() -> Text("لا توجد مصادر الآن.", color = C.Muted, modifier = Modifier.padding(24.dp))
                else -> LazyColumn(contentPadding = PaddingValues(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(list.size) { i -> SourceRow(list[i], i == 0, if (i == 0) fr else null, onPick) }
                }
            }
        }
    }
}
