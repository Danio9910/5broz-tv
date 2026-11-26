package com.fivebroz.android.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.fivebroz.android.data.Api
import com.fivebroz.android.data.Db
import com.fivebroz.android.data.Home
import com.fivebroz.android.data.Prefs
import com.fivebroz.android.data.Preview
import com.fivebroz.android.data.ResumeItem
import kotlinx.coroutines.delay
import java.util.Calendar

private fun greeting(): String {
    val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when {
        h < 5 -> "ليلة سعيدة"
        h < 12 -> "صباح الخير"
        h < 18 -> "مساء النور"
        else -> "مساء الخير"
    }
}

@Composable
fun HomeScreen(onOpen: (Preview) -> Unit) {
    val kids = Prefs.profileKids
    val family = kids || Prefs.profileFamily
    var home by remember { mutableStateOf<Home?>(null) }
    var failed by remember { mutableStateOf(false) }
    var resume by remember { mutableStateOf<List<ResumeItem>>(emptyList()) }
    val g = gutter()

    LaunchedEffect(family) {
        try {
            home = Api.home(family)
        } catch (_: Exception) {
            failed = true
        }
    }
    LaunchedEffect(Unit) { resume = Db.continueWatching() }

    val h = home
    when {
        failed -> Centered("تعذّر تحميل المحتوى. تأكد من الاتصال بالإنترنت.")
        h == null -> Centered("…")
        else -> LazyColumn(Modifier.fillMaxSize()) {
            if (h.heroes.isNotEmpty()) item("hero") { Hero(h.heroes, onOpen) }
            item("greet") {
                if (Prefs.profileName.isNotBlank()) {
                    Text(
                        "${greeting()}، ${Prefs.profileName}",
                        modifier = Modifier.padding(horizontal = g, vertical = 6.dp),
                        color = C.Muted,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            if (resume.isNotEmpty()) item("resume") {
                PosterRow(
                    "متابعة المشاهدة",
                    resume.map { it.item },
                    onOpen,
                    g,
                    progress = { i -> resume.getOrNull(i)?.progress },
                )
            }
            if (h.top10.isNotEmpty()) item("top10") {
                val t = when {
                    kids -> "أفضل ١٠ للأطفال"
                    family -> "أفضل ١٠ للعائلة"
                    else -> "أفضل ١٠ اليوم"
                }
                PosterRow(t, h.top10, onOpen, g, ranked = true)
            }
            h.rows.forEachIndexed { i, row ->
                item("row$i") { PosterRow(row.title, row.items, onOpen, g) }
            }
            item("end") { VSpace(40.dp) }
        }
    }
}

@Composable
private fun Hero(items: List<Preview>, onOpen: (Preview) -> Unit) {
    val isTv = LocalIsTv.current
    var index by remember { mutableIntStateOf(0) }
    val fr = remember { FocusRequester() }
    val g = gutter()

    LaunchedEffect(items) {
        if (isTv) runCatching { fr.requestFocus() }
        while (items.size > 1) {
            delay(9000)
            index = (index + 1) % items.size
        }
    }

    Box(Modifier.fillMaxWidth().height(if (isTv) 360.dp else 430.dp)) {
        Crossfade(items[index], animationSpec = tween(700), label = "hero") { it ->
            Box(Modifier.fillMaxSize()) {
                AsyncImage(
                    model = it.background ?: it.poster,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                // Fade into the page at the bottom and behind the text.
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.45f to C.Ink950.copy(alpha = 0.25f),
                            1f to C.Ink950,
                        ),
                    ),
                )
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.horizontalGradient(
                            0f to C.Ink950.copy(alpha = 0f),
                            0.5f to C.Ink950.copy(alpha = 0f),
                            1f to C.Ink950.copy(alpha = 0.85f),
                        ),
                    ),
                )
                Column(
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = g, end = g, bottom = if (isTv) 74.dp else 84.dp)
                        .fillMaxWidth(if (isTv) 0.5f else 1f),
                ) {
                    Text(
                        it.name,
                        style = MaterialTheme.typography.displayLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    VSpace(6.dp)
                    Meta(listOf(it.year, it.rating?.let { r -> "★ $r" }, if (it.type == "series") "مسلسل" else "فيلم"))
                    if (!it.description.isNullOrBlank()) {
                        VSpace(8.dp)
                        Text(
                            it.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = C.Paper.copy(alpha = 0.8f),
                            maxLines = if (isTv) 3 else 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        Row(
            Modifier.align(Alignment.BottomStart).padding(start = g, end = g, bottom = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            GoldButton("شاهد الآن", { onOpen(items[index]) }, focusRequester = fr)
            if (items.size > 1) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items.indices.forEach { i ->
                        Box(
                            Modifier
                                .size(width = if (i == index) 18.dp else 6.dp, height = 6.dp)
                                .clip(CircleShape)
                                .background(if (i == index) C.Gold else C.Paper.copy(alpha = 0.3f)),
                        )
                    }
                }
            }
        }
    }
}
