package com.fivebroz.android.ui

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

@Composable
fun SearchScreen(onOpen: (Preview) -> Unit) {
    var q by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Preview>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }

    LaunchedEffect(q) {
        val term = q.trim()
        if (term.length < 2) {
            results = emptyList()
            return@LaunchedEffect
        }
        delay(450) // wait until typing pauses
        searching = true
        results = try {
            Api.search(term)
        } catch (_: Exception) {
            emptyList()
        }
        searching = false
    }

    PosterGrid(results, onOpen, header = {
        Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
            PageTitle("بحث")
            Field(
                q, { q = it }, "ابحث عن فيلم أو مسلسل…", {},
                Modifier.fillMaxWidth().widthIn(max = 640.dp),
                center = false,
            )
            val msg = when {
                searching -> "جارٍ البحث…"
                q.trim().length >= 2 && results.isEmpty() -> "لا نتائج لـ «${q.trim()}»."
                else -> null
            }
            if (msg != null) {
                Text(msg, color = C.Muted, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 12.dp))
            }
        }
    })
}

@Composable
fun LibraryScreen(onOpen: (Preview) -> Unit) {
    var saved by remember { mutableStateOf<List<Preview>?>(null) }
    var resume by remember { mutableStateOf<List<ResumeItem>>(emptyList()) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { saved = null }
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
