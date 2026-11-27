package com.fivebroz.android.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.fivebroz.android.data.Api
import com.fivebroz.android.data.Db
import com.fivebroz.android.data.Home
import com.fivebroz.android.data.Prefs
import com.fivebroz.android.data.Preview
import com.fivebroz.android.data.ResumeItem
import kotlinx.coroutines.delay
import java.util.Calendar

/** Title artwork already looked up this session (null = the title has none). */
private val logoCache = HashMap<String, String?>()

private fun greeting(): String {
    val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when {
        h < 5 -> "ليلة سعيدة"
        h < 12 -> "صباح الخير"
        h < 18 -> "مساء النور"
        else -> "مساء الخير"
    }
}

private fun top10Title(kids: Boolean, family: Boolean) = when {
    kids -> "أفضل 10 للأطفال"
    family -> "أفضل 10 للعائلة"
    else -> "أفضل 10 اليوم"
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onOpen: (Preview) -> Unit, onProfile: () -> Unit) {
    val kids = Prefs.profileKids
    val family = kids || Prefs.profileFamily
    // Open instantly on what this device saw last time; the fresh page replaces it a moment later.
    var home by remember { mutableStateOf<Home?>(Api.cachedHome(family)) }
    var failed by remember { mutableStateOf(false) }
    var resume by remember { mutableStateOf<List<ResumeItem>>(emptyList()) }
    var attempt by remember { mutableStateOf(0) }
    var refreshing by remember { mutableStateOf(false) }

    LaunchedEffect(family, attempt) {
        failed = false
        try {
            home = Api.home(family)
        } catch (_: Exception) {
            if (home == null) failed = true
        }
        refreshing = false
    }
    LaunchedEffect(attempt, ItemEvents.resumeVersion) { resume = Db.continueWatching() }
    // Coming back from the player: refresh continue watching.
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME) { ItemEvents.resumeVersion++ }

    if (failed) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("تعذّر تحميل المحتوى. تأكد من الاتصال بالإنترنت.", color = C.Muted)
                VSpace(16.dp)
                GoldButton("إعادة المحاولة", { attempt++ })
            }
        }
        return
    }
    if (LocalIsTv.current) TvHome(home, resume, kids, family, onOpen)
    else {
        androidx.compose.material3.pulltorefresh.PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { refreshing = true; attempt++ },
        ) { PhoneHome(home, resume, kids, family, onOpen, onProfile) }
    }
}

// ---------------------------------------------------------------- phone

@Composable
private fun PhoneHome(
    home: Home?,
    resume: List<ResumeItem>,
    kids: Boolean,
    family: Boolean,
    onOpen: (Preview) -> Unit,
    onProfile: () -> Unit,
) {
    val g = gutter()
    val list = rememberLazyListState()
    val tablet = LocalIsTablet.current
    val cfg = LocalConfiguration.current
    // Phones get a tall poster; tablets a wide cinematic banner.
    val heroH = if (tablet) (cfg.screenWidthDp * 0.52f).coerceAtMost(cfg.screenHeightDp * 0.72f).dp
    else (cfg.screenWidthDp * 1.32f).dp
    val heroPx = with(LocalDensity.current) { heroH.toPx() }
    val solid by remember {
        derivedStateOf {
            if (list.firstVisibleItemIndex > 0) 1f
            else (list.firstVisibleItemScrollOffset / (heroPx * 0.6f)).coerceIn(0f, 1f)
        }
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(state = list, modifier = Modifier.fillMaxSize()) {
            item("hero") {
                if (home == null) Skeleton(Modifier.fillMaxWidth().height(heroH), RectShape)
                else if (home.heroes.isNotEmpty()) PhoneHero(home.heroes, heroH, tablet, onOpen)
            }
            if (home == null) {
                item("sk") { SkeletonRows(g) }
            } else {
                item("health") { HealthNotice(g) }
                if (resume.isNotEmpty()) item("resume") { ContinueRow(resume, onOpen, g) }
                if (home.top10.isNotEmpty()) item("top10") {
                    PosterRow(top10Title(kids, family), home.top10, onOpen, g, ranked = true)
                }
                home.rows.forEachIndexed { i, row ->
                    item("row$i") { PosterRow(row.title, row.items, onOpen, g) }
                }
                item("end") { VSpace(28.dp) }
            }
        }
        PhoneHeader(solid, onProfile)
    }
}

private val RectShape = androidx.compose.ui.graphics.RectangleShape

/** A quiet one-line notice when part of the platform needs attention. */
private var healthShown = false

@Composable
fun HealthNotice(g: Dp) {
    var msg by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        if (healthShown) return@LaunchedEffect
        val h = Api.health() ?: return@LaunchedEffect
        msg = when {
            !h.catalog -> "قائمة الأفلام لا تستجيب الآن — حاول بعد قليل."
            !h.torrents && !h.debridActive -> "مصادر التشغيل لا تستجيب الآن — حاول بعد قليل."
            h.daysLeft != null && h.daysLeft in 0..5 -> "اشتراك Real-Debrid ينتهي خلال ${h.daysLeft} أيام."
            !h.debridActive && !Prefs.seenDebridNotice -> {
                Prefs.seenDebridNotice = true
                "التشغيل يعمل الآن عبر التورنت — قد يستغرق البدء ثوانٍ أطول."
            }
            else -> null
        }
    }
    val m = msg ?: return
    Row(
        Modifier.padding(horizontal = g, vertical = 6.dp).fillMaxWidth()
            .background(C.Paper.copy(alpha = 0.07f), androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(m, color = C.Paper.copy(alpha = 0.9f), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Focusable({ healthShown = true; msg = null }, shape = CircleShape, lift = 1f, ring = false) {
            Ic(Icons.Close, 18.dp, C.Muted, Modifier.padding(8.dp))
        }
    }
}

@Composable
private fun JoinChip(g: Dp) {
    var open by remember { mutableStateOf(false) }
    Row(Modifier.padding(horizontal = g, vertical = 6.dp)) {
        Focusable({ open = true }, shape = androidx.compose.foundation.shape.RoundedCornerShape(50), lift = 1f, ring = false) {
            Text(
                "انضمام لغرفة مشاهدة",
                Modifier.background(C.Paper.copy(alpha = 0.08f)).padding(horizontal = 14.dp, vertical = 7.dp),
                color = C.Paper.copy(alpha = 0.8f),
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
    if (open) JoinPartyDialog { open = false }
}

/** Swipeable poster hero — tall artwork, title and one clear action, centred. */
@Composable
private fun PhoneHero(items: List<Preview>, height: Dp, wide: Boolean, onOpen: (Preview) -> Unit) {
    val pager = rememberPagerState { items.size }
    LaunchedEffect(pager, items) {
        while (items.size > 1) {
            delay(7000)
            if (!pager.isScrollInProgress) pager.animateScrollToPage((pager.currentPage + 1) % items.size, animationSpec = tween(600))
        }
    }
    Box(Modifier.fillMaxWidth().height(height)) {
        HorizontalPager(pager, Modifier.fillMaxSize()) { page ->
            val it = items[page]
            Box(Modifier.fillMaxSize()) {
                Img(if (wide) it.background ?: it.poster else it.poster ?: it.background, Modifier.fillMaxSize().kenBurns(), it.name)
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            0f to C.Ink950.copy(alpha = 0.35f),
                            0.22f to Color.Transparent,
                            0.55f to Color.Transparent,
                            0.82f to C.Ink950.copy(alpha = 0.85f),
                            1f to C.Ink950,
                        ),
                    ),
                )
                Column(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 28.dp).padding(bottom = 64.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    TitleArt(it.name, it.logo, 96.dp, 300.dp, MaterialTheme.typography.displayLarge, center = true)
                    VSpace(10.dp)
                    Meta(listOf(if (it.type == "series") "مسلسل" else "فيلم", it.year, it.rating?.let { r -> "★ $r" }))
                }
            }
        }
        Row(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            GoldButton("شاهد الآن", { onOpen(items[pager.currentPage]) }, icon = Icons.Play)
            if (items.size > 1) Dots(items.size, pager.currentPage)
        }
    }
}

@Composable
private fun Dots(n: Int, current: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(n) { i ->
            Box(
                Modifier.size(width = if (i == current) 16.dp else 5.dp, height = 5.dp)
                    .clip(CircleShape)
                    .background(if (i == current) C.Paper else C.Paper.copy(alpha = 0.3f)),
            )
        }
    }
}

@Composable
private fun ContinueRow(resume: List<ResumeItem>, onOpen: (Preview) -> Unit, g: Dp, onFocusItem: ((Preview) -> Unit)? = null) {
    val w = if (LocalIsTv.current) 260.dp else 210.dp
    Column(Modifier.fillMaxWidth().padding(bottom = 20.dp)) {
        SectionTitle("متابعة المشاهدة — ${Prefs.profileName}", Modifier.padding(horizontal = g, vertical = 6.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = g, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            itemsIndexed(resume, key = { i, r -> "${r.videoId}#$i" }) { _, r ->
                WideCard(
                    r.item, w, { onOpen(r.item) },
                    progress = r.progress,
                    subtitle = episodeTag(r.videoId),
                    onFocus = onFocusItem?.let { f -> { f(r.item) } },
                )
            }
        }
    }
}

/** "tt123:2:5" -> "الموسم ٢ · الحلقة ٥" */
private fun episodeTag(videoId: String): String? {
    val p = videoId.split(":")
    return if (p.size == 3) "الموسم ${p[1]} · الحلقة ${p[2]}" else null
}

// ---------------------------------------------------------------- TV

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TvHome(
    home: Home?,
    resume: List<ResumeItem>,
    kids: Boolean,
    family: Boolean,
    onOpen: (Preview) -> Unit,
) {
    val g = gutter()
    if (home == null) {
        Column(Modifier.fillMaxSize().padding(top = 40.dp)) {
            Skeleton(Modifier.padding(horizontal = g).size(420.dp, 44.dp))
            VSpace(14.dp)
            Skeleton(Modifier.padding(horizontal = g).size(300.dp, 18.dp))
            VSpace(120.dp)
            SkeletonRows(g, rows = 2)
        }
        return
    }

    // The item under the remote drives the whole backdrop (Apple TV / Google TV feel).
    var focused by remember { mutableStateOf(home.heroes.firstOrNull() ?: home.top10.firstOrNull()) }
    var shown by remember { mutableStateOf(focused) }
    LaunchedEffect(focused) {
        delay(260) // don't flash every card while the remote is held down
        shown = focused
    }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }

    val defaultSpec = LocalBringIntoViewSpec.current
    // Rows snap so the focused row sits at the same height every time.
    val titleRoom = with(LocalDensity.current) { 72.dp.toPx() }
    val pinTop = remember(titleRoom) {
        object : BringIntoViewSpec {
            // Keep the row heading visible above the focused card.
            override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float = offset - titleRoom
        }
    }

    Box(Modifier.fillMaxSize()) {
        Crossfade(shown, animationSpec = tween(500), label = "bg") { p ->
            Box(Modifier.fillMaxSize()) {
                if (p != null) Img((p.background ?: p.poster)?.replace("/w780/", "/w1280/"), Modifier.fillMaxSize().kenBurns())
            }
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(0f to C.Ink950.copy(alpha = 0.15f), 0.45f to C.Ink950.copy(alpha = 0.55f), 1f to C.Ink950),
            ),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(0f to Color.Transparent, 0.35f to C.Ink950.copy(alpha = 0.25f), 0.62f to C.Ink950.copy(alpha = 0.92f), 1f to C.Ink950),
            ),
        )

        Column(Modifier.fillMaxSize()) {
            // Info for the focused title.
            Column(
                Modifier.fillMaxWidth(0.55f).height(230.dp).padding(start = g, top = 44.dp),
            ) {
                val p = shown
                if (p != null) {
                    var logo by remember(p.id) { mutableStateOf(p.logo ?: logoCache[p.id]) }
                    LaunchedEffect(p.id) {
                        if (logo == null && !logoCache.containsKey(p.id)) {
                            val l = Api.logo(p.type, p.id)
                            logoCache[p.id] = l
                            logo = l
                        }
                    }
                    TitleArt(p.name, logo, 110.dp, 420.dp, MaterialTheme.typography.displayLarge)
                    VSpace(8.dp)
                    Meta(listOf(if (p.type == "series") "مسلسل" else "فيلم", p.year, p.rating?.let { "★ $it" }))
                    if (!p.description.isNullOrBlank()) {
                        VSpace(10.dp)
                        Text(
                            p.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = C.Paper.copy(alpha = 0.8f),
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            CompositionLocalProvider(LocalBringIntoViewSpec provides pinTop) {
                LazyColumn(Modifier.fillMaxWidth().weight(1f), contentPadding = PaddingValues(bottom = 200.dp)) {
                    item("health") { HealthNotice(g) }
                    var firstDone = false
                    fun claim(): FocusRequester? = if (!firstDone) { firstDone = true; first } else null

                    if (resume.isNotEmpty()) {
                        val fr = claim()
                        item("resume") {
                            CompositionLocalProvider(LocalBringIntoViewSpec provides defaultSpec) {
                                TvWideRow("متابعة المشاهدة", resume.map { it.item }, resume.map { it.progress }, onOpen, g, fr) { focused = it }
                            }
                        }
                    }
                    if (home.top10.isNotEmpty()) {
                        val fr = claim()
                        item("top10") {
                            CompositionLocalProvider(LocalBringIntoViewSpec provides defaultSpec) {
                                TvPosterRow(top10Title(kids, family), home.top10, onOpen, g, fr, ranked = true) { focused = it }
                            }
                        }
                    }
                    home.rows.forEachIndexed { i, row ->
                        val fr = claim()
                        item("row$i") {
                            CompositionLocalProvider(LocalBringIntoViewSpec provides defaultSpec) {
                                TvPosterRow(row.title, row.items, onOpen, g, fr) { focused = it }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TvPosterRow(
    title: String,
    items: List<Preview>,
    onOpen: (Preview) -> Unit,
    g: Dp,
    firstFocus: FocusRequester?,
    ranked: Boolean = false,
    onFocusItem: (Preview) -> Unit,
) {
    val w = posterWidth()
    Column(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
        SectionTitle(title, Modifier.padding(horizontal = g, vertical = 4.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = g, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(if (ranked) 4.dp else 14.dp),
        ) {
            itemsIndexed(items, key = { i, it -> "${it.id}#$i" }) { i, it ->
                val mod = if (i == 0 && firstFocus != null) Modifier.focusRequesterSafe(firstFocus) else Modifier
                Box(mod) {
                    if (ranked) RankedPoster(i + 1, it, w, { onOpen(it) }) { onFocusItem(it) }
                    else Poster(it, w, { onOpen(it) }, onFocus = { onFocusItem(it) })
                }
            }
        }
    }
}

@Composable
private fun TvWideRow(
    title: String,
    items: List<Preview>,
    progress: List<Float>,
    onOpen: (Preview) -> Unit,
    g: Dp,
    firstFocus: FocusRequester?,
    onFocusItem: (Preview) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
        SectionTitle(title, Modifier.padding(horizontal = g, vertical = 4.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = g, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            itemsIndexed(items, key = { i, it -> "${it.id}#$i" }) { i, it ->
                val mod = if (i == 0 && firstFocus != null) Modifier.focusRequesterSafe(firstFocus) else Modifier
                Box(mod) {
                    WideCard(it, 250.dp, { onOpen(it) }, progress = progress.getOrNull(i), onFocus = { onFocusItem(it) })
                }
            }
        }
    }
}

/** Requesting focus on the wrapper moves it into the card inside. */
private fun Modifier.focusRequesterSafe(fr: FocusRequester): Modifier =
    this.focusRequester(fr).focusGroup()
