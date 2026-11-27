package com.fivebroz.android.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fivebroz.android.data.Api
import com.fivebroz.android.data.CastMember
import com.fivebroz.android.data.Preview

/** Round cast photos under a title; each opens the actor's page. */
@Composable
fun CastRow(people: List<CastMember>, g: androidx.compose.ui.unit.Dp, onPerson: (CastMember) -> Unit) {
    if (people.isEmpty()) return
    val tv = LocalIsTv.current
    val d = if (tv) 96.dp else 76.dp
    Column(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        SectionTitle("طاقم التمثيل", Modifier.padding(horizontal = g, vertical = 6.dp))
        androidx.compose.foundation.lazy.LazyRow(
            contentPadding = PaddingValues(horizontal = g, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(if (tv) 20.dp else 14.dp),
        ) {
            items(people.size, key = { people[it].id }) { i ->
                val p = people[i]
                Column(Modifier.width(d + 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Focusable({ onPerson(p) }, shape = CircleShape, lift = 1.08f) {
                        Box(Modifier.size(d).background(C.Ink800), contentAlignment = Alignment.Center) {
                            if (p.photo != null) Img(p.photo, Modifier.fillMaxSize(), p.name)
                            else Text(p.name.take(1), style = MaterialTheme.typography.titleLarge, color = C.Muted)
                        }
                    }
                    Text(p.name, Modifier.padding(top = 8.dp), style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    p.character?.let {
                        Text(it, color = C.Muted, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

/** An actor's page: photo, short biography and the titles they're best known for. */
@Composable
fun PersonScreen(id: String, onOpen: (Preview) -> Unit, onBack: () -> Unit) {
    var person by remember { mutableStateOf<Api.Person?>(null) }
    var failed by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    LaunchedEffect(id) {
        person = Api.person(id)
        failed = person == null
    }
    val tv = LocalIsTv.current
    val g = if (tv) 56.dp else gutter()
    val w = posterWidth()
    val p = person
    val back = remember { FocusRequester() }

    Box(Modifier.fillMaxSize().background(C.Ink950)) {
        if (p == null) {
            if (failed) Centered("تعذّر تحميل صفحة الممثل.") else Centered("…")
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(w),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = g, end = g, top = if (tv) 48.dp else 0.dp, bottom = 40.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Row(
                        Modifier.fillMaxWidth().then(if (tv) Modifier else Modifier.statusBarsPadding()).padding(top = if (tv) 0.dp else 56.dp, bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(if (tv) 28.dp else 18.dp),
                    ) {
                        val ph = if (tv) 180.dp else 110.dp
                        Box(Modifier.size(ph).clip(CircleShape).background(C.Ink800)) {
                            Img(p.photo, Modifier.fillMaxSize(), p.name)
                        }
                        Column(Modifier.weight(1f).widthIn(max = 720.dp)) {
                            Text(p.name, style = if (tv) MaterialTheme.typography.displayLarge else MaterialTheme.typography.headlineMedium)
                            p.born?.let { Text("مواليد $it", color = C.Muted, style = MaterialTheme.typography.labelLarge) }
                            if (!p.bio.isNullOrBlank()) {
                                Spacer(Modifier.size(10.dp))
                                Focusable({ expanded = !expanded }, ring = false, lift = 1f, shape = RoundedCornerShape(4.dp)) {
                                    Text(
                                        p.bio,
                                        Modifier.animateContentSize(),
                                        color = C.Paper.copy(alpha = 0.86f),
                                        style = MaterialTheme.typography.bodyLarge,
                                        maxLines = if (expanded) 40 else if (tv) 5 else 4,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
                if (p.works.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) { SectionTitle("اشتهر في") }
                    items(p.works, key = { it.id }) { Poster(it, w, onClick = { onOpen(it) }) }
                }
            }
        }
        if (!tv) {
            Focusable(onBack, Modifier.statusBarsPadding().padding(12.dp), shape = CircleShape, lift = 1f, ring = false, focusRequester = back) {
                Box(Modifier.size(38.dp).background(C.Ink950.copy(alpha = 0.55f)), contentAlignment = Alignment.Center) {
                    Ic(Icons.Back, 20.dp)
                }
            }
        }
    }
}
