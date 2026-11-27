package com.fivebroz.android.ui

import kotlinx.coroutines.launch
import com.fivebroz.android.data.Prefs
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.fivebroz.android.data.Api
import com.fivebroz.android.data.Db
import com.fivebroz.android.data.Preview
import com.fivebroz.android.data.ResumeItem
import kotlinx.coroutines.delay

@Composable
private fun PosterGrid(
    items: List<Preview>,
    onOpen: (Preview) -> Unit,
    header: (@Composable () -> Unit)? = null,
) {
    val w = posterWidth()
    val g = gutter()
    LazyVerticalGrid(
        columns = GridCells.Adaptive(w),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = g, end = g, top = if (LocalIsTv.current) 32.dp else 8.dp, bottom = 40.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        if (header != null) item(span = { GridItemSpan(maxLineSpan) }) { header() }
        items(items) { Poster(it, w, onClick = { onOpen(it) }) }
    }
}

private data class Genre(val label: String, val movie: String?, val tv: String?)

private val GENRES = listOf(
    Genre("أكشن", "28", "10759"),
    Genre("كوميديا", "35", "35"),
    Genre("دراما", "18", "18"),
    Genre("جريمة", "80", "80"),
    Genre("خيال علمي", "878", "10765"),
    Genre("إثارة", "53", null),
    Genre("رعب", "27", null),
    Genre("غموض", "9648", "9648"),
    Genre("رومانسي", "10749", null),
    Genre("رسوم متحركة", "16", "16"),
    Genre("عائلي", "10751", "10751"),
    Genre("وثائقي", "99", "99"),
    Genre("حرب", "10752", "10768"),
)

private val KIDS_GENRES = listOf(
    Genre("رسوم متحركة", "16", "16"),
    Genre("عائلي", "10751", "10751"),
    Genre("مغامرة", "12", "10759"),
    Genre("كوميديا", "35", "35"),
)

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(onOpen: (Preview) -> Unit) {
    var q by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Preview>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var trending by remember { mutableStateOf<List<Preview>>(emptyList()) }
    var genre by remember { mutableStateOf<Genre?>(null) }
    var genreItems by remember { mutableStateOf<List<Preview>?>(null) }
    var recent by remember { mutableStateOf(Prefs.recentSearches) }

    LaunchedEffect(Unit) { trending = Api.trending() }
    LaunchedEffect(genre) {
        val gn = genre ?: return@LaunchedEffect
        genreItems = null
        // Films and series of that genre, interleaved.
        val films = gn.movie?.let { Api.genre(it, "movie") }.orEmpty()
        val shows = gn.tv?.let { Api.genre(it, "series") }.orEmpty()
        val mixed = ArrayList<Preview>()
        for (i in 0 until maxOf(films.size, shows.size)) {
            films.getOrNull(i)?.let(mixed::add)
            shows.getOrNull(i)?.let(mixed::add)
        }
        genreItems = mixed
    }
    LaunchedEffect(q) {
        val term = q.trim()
        if (term.length < 2) {
            results = emptyList()
            return@LaunchedEffect
        }
        genre = null
        delay(450) // wait until typing pauses
        searching = true
        results = try {
            Api.search(term)
        } catch (_: Exception) {
            emptyList()
        }
        searching = false
    }

    val typing = q.trim().length >= 2
    val open: (Preview) -> Unit = { p ->
        if (typing) {
            Prefs.rememberSearch(q)
            recent = Prefs.recentSearches
        }
        onOpen(p)
    }
    val items = when {
        typing -> results
        genre != null -> genreItems.orEmpty()
        else -> trending
    }

    PosterGrid(items, open, header = {
        Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
            PageTitle("بحث")
            Field(
                q, { q = it }, "ابحث عن فيلم أو مسلسل…", {},
                Modifier.fillMaxWidth().widthIn(max = 640.dp),
                center = false,
            )
            val msg = when {
                searching -> "جارٍ البحث…"
                typing && results.isEmpty() -> "لا نتائج لـ «${q.trim()}»."
                else -> null
            }
            if (msg != null) {
                Text(msg, color = C.Muted, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 12.dp))
            }
            if (!typing) {
                if (recent.isNotEmpty()) {
                    SectionTitle("عمليات بحث سابقة", Modifier.padding(top = 18.dp, bottom = 8.dp))
                    androidx.compose.foundation.layout.FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        recent.forEach { r -> Chip(r, false) { q = r } }
                    }
                }
                SectionTitle("تصفّح حسب النوع", Modifier.padding(top = 18.dp, bottom = 8.dp))
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    (if (Prefs.profileKids) KIDS_GENRES else GENRES).forEach { gn ->
                        Chip(gn.label, genre == gn) { genre = if (genre == gn) null else gn }
                    }
                }
                SectionTitle(
                    when {
                        genre != null && genreItems == null -> "جارٍ التحميل…"
                        genre != null -> genre!!.label
                        else -> "الأكثر رواجاً هذا الأسبوع"
                    },
                    Modifier.padding(top = 22.dp),
                )
            }
        }
    })
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    Focusable(onClick, shape = RoundedCornerShape(50), lift = 1.05f, ring = false) { f ->
        Text(
            label,
            Modifier.background(if (f || selected) C.Paper else C.Ink800).padding(horizontal = 14.dp, vertical = 7.dp),
            color = if (f || selected) C.Ink950 else C.Paper,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
fun LibraryScreen(onOpen: (Preview) -> Unit) {
    var saved by remember { mutableStateOf<List<Preview>?>(null) }
    var resume by remember { mutableStateOf<List<ResumeItem>>(emptyList()) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { saved = null }
    LaunchedEffect(ItemEvents.libraryVersion, ItemEvents.resumeVersion) { saved = null }
    LaunchedEffect(saved == null) {
        if (saved == null) {
            resume = Db.continueWatching()
            saved = Db.library()
        }
    }

    val s = saved
    if (s == null) return Centered("…")
    if (s.isEmpty() && resume.isEmpty()) {
        return Centered("قائمتك فارغة. اضغط «+ قائمتي» في صفحة أي عنوان ليظهر هنا.")
    }
    PosterGrid(s, onOpen, header = {
        Column {
            PageTitle("قائمتي")
            if (resume.isNotEmpty()) {
                PosterRow("متابعة المشاهدة", resume.map { it.item }, onOpen, 0.dp, progress = { i -> resume.getOrNull(i)?.progress })
            }
            if (s.isNotEmpty() && resume.isNotEmpty()) SectionTitle("المحفوظة", Modifier.padding(vertical = 6.dp))
        }
    })
}

/** Large page heading, clear of the status bar on phones. */
@Composable
fun PageTitle(text: String) {
    Text(
        text,
        modifier = Modifier.then(if (LocalIsTv.current) Modifier else Modifier.statusBarsPadding()).padding(top = 14.dp, bottom = 14.dp),
        style = MaterialTheme.typography.headlineMedium,
    )
}

/** "What shall we watch tonight?" — household suggestions, most votes first. */
@Composable
fun TonightScreen(onOpen: (Preview) -> Unit) {
    var list by remember { mutableStateOf<List<com.fivebroz.android.data.Suggestion>?>(null) }
    var tick by remember { mutableStateOf(0) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val g = gutter()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++ }
    LaunchedEffect(tick) { list = Db.suggestions() }

    androidx.compose.foundation.lazy.LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = g, end = g, top = if (LocalIsTv.current) 32.dp else 0.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item("title") {
            Column {
                PageTitle("نشاهد الليلة")
                Text("اقترحوا وصوّتوا — الأعلى صوتاً يفوز.", color = C.Muted, style = MaterialTheme.typography.bodyMedium)
                VSpace(14.dp)
            }
        }
        val l = list
        when {
            l == null -> items(3) { Skeleton(Modifier.fillMaxWidth().height(96.dp)) }
            l.isEmpty() -> item("empty") {
                Text(
                    "لا اقتراحات بعد. افتح أي عنوان واضغط «اقترح لليلة».",
                    color = C.Muted,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = 30.dp),
                )
            }
            else -> items(l.size, key = { l[it].id }) { i ->
                val s = l[i]
                val leader = i == 0 && s.votes > 0
                androidx.compose.foundation.layout.Row(
                    Modifier.fillMaxWidth()
                        .background(if (leader) C.Gold.copy(alpha = 0.12f) else C.Ink900, androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
                        .padding(10.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Poster(s.item, 54.dp, { onOpen(s.item) })
                    Column(Modifier.weight(1f)) {
                        if (leader) Text("الأعلى تصويتاً", color = C.Gold, style = MaterialTheme.typography.labelMedium)
                        Text(s.item.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        s.addedBy?.let { Text("اقترحه $it", color = C.Muted, style = MaterialTheme.typography.labelMedium) }
                    }
                    Focusable(
                        {
                            val voted = s.votedByMe
                            list = l.map { if (it.id == s.id) it.copy(votedByMe = !voted, votes = it.votes + if (voted) -1 else 1) else it }
                                .sortedByDescending { it.votes }
                            scope.launch { Db.toggleVote(s.id, voted) }
                        },
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(50),
                        lift = 1.06f,
                    ) { f ->
                        val fg = if (f || s.votedByMe) C.Ink950 else C.Paper
                        androidx.compose.foundation.layout.Row(
                            Modifier.background(if (f) C.Paper else if (s.votedByMe) C.Gold else C.Ink700).padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Ic(Icons.Vote, 16.dp, fg)
                            Text("${s.votes}", color = fg, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                    if (s.addedBy != null && s.addedBy == Prefs.profileName) {
                        Focusable({
                            list = l.filter { it.id != s.id }
                            scope.launch { Db.removeSuggestion(s.id) }
                        }, shape = androidx.compose.foundation.shape.CircleShape, lift = 1.06f, ring = false) {
                            Ic(Icons.Close, 18.dp, C.Muted, Modifier.padding(10.dp))
                        }
                    }
                }
            }
        }
    }
}
