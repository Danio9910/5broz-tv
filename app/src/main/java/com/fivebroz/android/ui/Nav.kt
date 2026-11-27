package com.fivebroz.android.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.onPreviewKeyEvent
import com.fivebroz.android.data.Api
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.fivebroz.android.data.Prefs
import com.fivebroz.android.data.Preview
import java.net.URLDecoder
import java.net.URLEncoder

private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
private fun dec(s: String) = URLDecoder.decode(s, "UTF-8")

fun NavHostController.openTitle(p: Preview) = navigate("title/${p.type}/${enc(p.id)}")

@Composable
fun AppNav() {
    val nav = rememberNavController()
    val start = when {
        !Prefs.gatePassed -> "gate"
        Prefs.profileId == null -> "profiles"
        else -> "main"
    }
    NavHost(
        nav,
        startDestination = start,
        // Pages slide in a little from the reading side and fade — like a native stack.
        enterTransition = { fadeIn(tween(240)) + slideInHorizontally(tween(280)) { -it / 10 } },
        exitTransition = { fadeOut(tween(180)) },
        popEnterTransition = { fadeIn(tween(220)) },
        popExitTransition = { fadeOut(tween(200)) + slideOutHorizontally(tween(240)) { -it / 10 } },
    ) {
        composable("gate") {
            GateScreen(onPass = {
                nav.navigate("profiles") { popUpTo("gate") { inclusive = true } }
            })
        }
        composable("profiles") {
            ProfilesScreen(onChosen = {
                nav.navigate("main") { popUpTo(0) { inclusive = true } }
            })
        }
        composable("main") {
            ItemMenuHost({ nav.openTitle(it) }) {
            MainScreen(
                onOpen = { nav.openTitle(it) },
                onSwitchProfile = {
                    Prefs.clearProfile()
                    nav.navigate("profiles") { popUpTo(0) { inclusive = true } }
                },
            )
            }
        }
        composable(
            "title/{type}/{id}",
            deepLinks = listOf(androidx.navigation.navDeepLink { uriPattern = "fivebroz://title/{type}/{id}" }),
        ) { e ->
            val type = e.arguments?.getString("type") ?: "movie"
            val id = dec(e.arguments?.getString("id") ?: "")
            ItemMenuHost({ nav.openTitle(it) }) {
                DetailScreen(
                    type = type, id = id, onOpen = { nav.openTitle(it) }, onBack = { nav.popBackStack() },
                    onPerson = { nav.navigate("person/${enc(it)}") },
                )
            }
        }
        composable("person/{id}") { e ->
            ItemMenuHost({ nav.openTitle(it) }) {
                PersonScreen(dec(e.arguments?.getString("id") ?: ""), onOpen = { nav.openTitle(it) }, onBack = { nav.popBackStack() })
            }
        }
    }
}

enum class Tab(val label: String, val icon: ImageVector) {
    Home("الرئيسية", Icons.Home),
    Search("بحث", Icons.Search),
    Library("قائمتي", Icons.Library),
    Tonight("الليلة", Icons.Tonight),
}

/** Horizontal page margin. On TV the side rail takes the first 80dp. */
@Composable
fun gutter(): Dp = when {
    LocalIsTv.current -> 40.dp
    LocalIsTablet.current -> 28.dp
    else -> 16.dp
}

private val RAIL = 80.dp

@Composable
private fun MainScreen(onOpen: (Preview) -> Unit, onSwitchProfile: () -> Unit) {
    val isTv = LocalIsTv.current
    var tab by rememberSaveable { mutableStateOf(Tab.Home) }
    var askExitCode by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var join by remember { mutableStateOf(false) }
    var addons by remember { mutableStateOf(false) }
    var settings by remember { mutableStateOf(false) }
    // Back from another tab returns home first, like every streaming app.
    androidx.activity.compose.BackHandler(enabled = tab != Tab.Home) { tab = Tab.Home }

    val switchProfile = {
        // Leaving a kids profile needs the household code (parental lock).
        if (Prefs.profileKids) askExitCode = true else onSwitchProfile()
    }

    val pages: @Composable (Tab) -> Unit = { t ->
        when (t) {
            Tab.Home -> HomeScreen(onOpen) { menu = true }
            Tab.Search -> SearchScreen(onOpen)
            Tab.Library -> LibraryScreen(onOpen)
            Tab.Tonight -> TonightScreen(onOpen)
        }
    }

    // TV screensaver after a few idle minutes (any key wakes it).
    var lastKey by remember { mutableStateOf(System.currentTimeMillis()) }
    var saving by remember { mutableStateOf(false) }
    if (isTv) {
        androidx.compose.runtime.LaunchedEffect(Unit) {
            while (true) {
                kotlinx.coroutines.delay(15_000)
                if (Prefs.screensaver && !saving && System.currentTimeMillis() - lastKey > 4 * 60_000) saving = true
            }
        }
    }
    if (isTv) {
        Box(
            Modifier.fillMaxSize().onPreviewKeyEvent {
                lastKey = System.currentTimeMillis()
                if (saving) {
                    saving = false
                    true
                } else {
                    false
                }
            },
        ) {
            Box(Modifier.fillMaxSize().padding(start = RAIL)) {
                AnimatedContent(tab, transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) }, label = "tab") { pages(it) }
            }
            TvRail(tab, { tab = it }, switchProfile)
            if (saving) {
                val items = remember { Api.cachedHome(Prefs.profileKids || Prefs.profileFamily)?.let { it.heroes + it.top10 + it.rows.flatMap { r -> r.items } } ?: emptyList() }
                Screensaver(items)
            }
        }
    } else {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                AnimatedContent(tab, transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(140)) }, label = "tab") { pages(it) }
            }
            PhoneBottomBar(tab) { tab = it }
        }
    }

    if (menu) {
        ProfileMenu(
            onDismiss = { menu = false },
            onSwitch = { menu = false; switchProfile() },
            onJoin = { menu = false; join = true },
            onAddons = { menu = false; addons = true },
            onSettings = { menu = false; settings = true },
        )
    }
    if (join) JoinPartyDialog { join = false }
    if (addons) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { addons = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        ) { AddonsScreen { addons = false } }
    }
    if (settings) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { settings = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        ) { SettingsScreen(onBack = { settings = false }, onAddons = { settings = false; addons = true }) }
    }
    if (askExitCode) {
        CodeDialog(
            title = "الخروج من وضع الأطفال",
            onDismiss = { askExitCode = false },
            onOk = {
                askExitCode = false
                onSwitchProfile()
            },
        )
    }
    UpdateCheck()
}

@Composable
fun Wordmark(size: Int = 26) {
    Text(
        buildAnnotatedString {
            append("5")
            withStyle(SpanStyle(color = C.Gold)) { append("broz") }
        },
        fontFamily = Cormorant,
        fontWeight = FontWeight.SemiBold,
        fontSize = size.sp,
        color = C.Paper,
    )
}

/** Google-TV-style rail: icons only, opens with labels while it holds focus. */
@Composable
private fun TvRail(tab: Tab, onTab: (Tab) -> Unit, onProfile: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    val w by animateDpAsState(if (open) 230.dp else RAIL, tween(220), label = "rail")
    Column(
        Modifier
            .fillMaxHeight()
            .width(w)
            .background(
                if (open) Brush.horizontalGradient(0f to C.Ink950, 0.8f to C.Ink950, 1f to C.Ink950.copy(alpha = 0.0f))
                else Brush.horizontalGradient(listOf(C.Ink950.copy(alpha = 0.85f), C.Ink950.copy(alpha = 0.0f))),
            )
            .onFocusChanged { open = it.hasFocus }
            .padding(vertical = 28.dp, horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.height(44.dp).padding(start = 8.dp), contentAlignment = Alignment.CenterStart) {
            if (open) Wordmark(26) else Text("5", fontFamily = Cormorant, fontWeight = FontWeight.SemiBold, fontSize = 30.sp, color = C.Gold)
        }
        Spacer(Modifier.height(18.dp))
        Tab.entries.forEach { t ->
            RailItem(t.label, t == tab, open, { onTab(t) }) {
                Icon(t.icon, null, Modifier.size(22.dp), tint = it)
            }
        }
        Spacer(Modifier.weight(1f))
        var join by remember { mutableStateOf(false) }
        RailItem("انضمام لغرفة", false, open, { join = true }) {
            Icon(Icons.Join, null, Modifier.size(22.dp), tint = it)
        }
        if (join) JoinPartyDialog { join = false }
        var settings by remember { mutableStateOf(false) }
        RailItem("الإعدادات", false, open, { settings = true }) {
            Icon(Icons.Edit, null, Modifier.size(22.dp), tint = it)
        }
        if (settings) {
            androidx.compose.ui.window.Dialog(
                onDismissRequest = { settings = false },
                properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
            ) { SettingsScreen(onBack = { settings = false }, onAddons = { settings = false }) }
        }
        if (!Prefs.profileKids) {
            var addons by remember { mutableStateOf(false) }
            RailItem("الإضافات", false, open, { addons = true }) {
                Icon(Icons.Sources, null, Modifier.size(22.dp), tint = it)
            }
            if (addons) {
                androidx.compose.ui.window.Dialog(
                    onDismissRequest = { addons = false },
                    properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
                ) { AddonsScreen { addons = false } }
            }
        }
        RailItem(Prefs.profileName, false, open, onProfile) {
            Avatar(Prefs.current(), 26.dp)
        }
    }
}

@Composable
private fun RailItem(label: String, selected: Boolean, open: Boolean, onClick: () -> Unit, icon: @Composable (Color) -> Unit) {
    Focusable(onClick, Modifier.fillMaxWidth(), RoundedCornerShape(12.dp), lift = 1.02f, ring = false) { f ->
        val tint = when {
            f -> C.Ink950
            selected -> C.Gold
            else -> C.Muted
        }
        Row(
            Modifier.fillMaxWidth()
                .background(if (f) C.Paper else Color.Transparent)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            icon(tint)
            if (open) Text(label, style = MaterialTheme.typography.labelLarge, color = if (f) C.Ink950 else if (selected) C.Paper else C.Muted, maxLines = 1)
        }
    }
}

/** Translucent header that sits over the hero and firms up as you scroll. */
@Composable
fun PhoneHeader(solid: Float, onProfile: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    // fully opaque once scrolled, so nothing from the banner shows through
                    listOf(C.Ink950.copy(alpha = 0.55f + 0.45f * solid), C.Ink950.copy(alpha = solid)),
                ),
            )
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Wordmark(26)
        Spacer(Modifier.weight(1f))
        Focusable(onProfile, shape = RoundedCornerShape(8.dp), lift = 1f, ring = false) {
            Avatar(Prefs.current(), 30.dp)
        }
    }
}

@Composable
private fun PhoneBottomBar(tab: Tab, onTab: (Tab) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(C.Ink950)
            .background(Brush.verticalGradient(listOf(C.Paper.copy(alpha = 0.06f), Color.Transparent)), RoundedCornerShape(0.dp))
            .navigationBarsPadding()
            .height(60.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Tab.entries.forEach { t ->
            val on = t == tab
            Focusable(onClick = { onTab(t) }, lift = 1f, ring = false, modifier = Modifier.weight(1f)) {
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(t.icon, t.label, Modifier.size(23.dp), tint = if (on) C.Paper else C.Muted)
                    Spacer(Modifier.height(3.dp))
                    Text(t.label, fontSize = 10.5.sp, color = if (on) C.Paper else C.Muted, fontFamily = Cairo, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal)
                }
            }
        }
    }
}

/** Phone: tapping your avatar opens a small sheet instead of leaving straight away. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun ProfileMenu(onDismiss: () -> Unit, onSwitch: () -> Unit, onJoin: () -> Unit, onAddons: () -> Unit, onSettings: () -> Unit) {
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss, containerColor = C.Ink900) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Avatar(Prefs.current(), 52.dp)
                Column {
                    Text(Prefs.profileName, style = MaterialTheme.typography.titleLarge)
                    Text("الإصدار ${com.fivebroz.android.BuildConfig.VERSION_NAME}", color = C.Muted, style = MaterialTheme.typography.labelMedium)
                }
            }
            Spacer(Modifier.height(18.dp))
            MenuRow(Icons.Join, "انضمام لغرفة مشاهدة", onJoin)
            MenuRow(Icons.Party, "تبديل الملف", onSwitch)
            if (!Prefs.profileKids) MenuRow(Icons.Sources, "الإضافات", onAddons)
            MenuRow(Icons.Edit, "الإعدادات", onSettings)
        }
    }
}

@Composable
internal fun MenuRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Focusable(onClick, Modifier.fillMaxWidth(), RoundedCornerShape(12.dp), lift = 1f, ring = false) { f ->
        Row(
            Modifier.fillMaxWidth().background(if (f) C.Ink700 else Color.Transparent).padding(horizontal = 6.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Ic(icon, 22.dp)
            Text(label, style = MaterialTheme.typography.bodyLarge)
        }
    }
}
