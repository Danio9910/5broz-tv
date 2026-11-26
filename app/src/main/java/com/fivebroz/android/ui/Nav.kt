package com.fivebroz.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import com.fivebroz.android.data.Preview
import com.fivebroz.android.data.Prefs
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
    NavHost(nav, startDestination = start) {
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
            MainScreen(
                onOpen = { nav.openTitle(it) },
                onSwitchProfile = {
                    Prefs.clearProfile()
                    nav.navigate("profiles") { popUpTo(0) { inclusive = true } }
                },
            )
        }
        composable("title/{type}/{id}") { e ->
            val type = e.arguments?.getString("type") ?: "movie"
            val id = dec(e.arguments?.getString("id") ?: "")
            DetailScreen(
                type = type,
                id = id,
                onOpen = { nav.openTitle(it) },
                onBack = { nav.popBackStack() },
            )
        }
    }
}

private enum class Tab(val label: String, val icon: ImageVector) {
    Home("الرئيسية", Icons.Home),
    Search("بحث", Icons.Search),
    Library("قائمتي", Icons.Library),
}

/** Horizontal page margin: wide on TV (overscan-safe), tight on phones. */
@Composable
fun gutter(): Dp = if (LocalIsTv.current) 48.dp else 18.dp

@Composable
private fun MainScreen(onOpen: (Preview) -> Unit, onSwitchProfile: () -> Unit) {
    val isTv = LocalIsTv.current
    var tab by rememberSaveable { mutableStateOf(Tab.Home) }
    var askExitCode by remember { mutableStateOf(false) }

    val switchProfile = {
        // Leaving a kids profile needs the household code (parental lock).
        if (Prefs.profileKids) askExitCode = true else onSwitchProfile()
    }

    Column(Modifier.fillMaxSize()) {
        if (isTv) TvTopBar(tab, { tab = it }, switchProfile)
        else PhoneTopBar(switchProfile)

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (tab) {
                Tab.Home -> HomeScreen(onOpen)
                Tab.Search -> SearchScreen(onOpen)
                Tab.Library -> LibraryScreen(onOpen)
            }
        }
        if (!isTv) PhoneBottomBar(tab) { tab = it }
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

@Composable
private fun TvTopBar(tab: Tab, onTab: (Tab) -> Unit, onProfile: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 48.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Wordmark(30)
        Spacer(Modifier.width(28.dp))
        Tab.entries.forEach { t ->
            Focusable(
                onClick = { onTab(t) },
                shape = RoundedCornerShape(50),
                lift = 1.04f,
            ) { f ->
                Text(
                    t.label,
                    modifier = Modifier
                        .background(if (f) C.Paper.copy(alpha = 0.12f) else C.Ink950)
                        .padding(horizontal = 18.dp, vertical = 7.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (t == tab) C.Gold else if (f) C.Paper else C.Muted,
                )
            }
        }
        Spacer(Modifier.weight(1f))
        ProfileChip(onProfile)
    }
}

@Composable
private fun PhoneTopBar(onProfile: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Wordmark(26)
        Spacer(Modifier.weight(1f))
        ProfileChip(onProfile)
    }
}

@Composable
private fun ProfileChip(onClick: () -> Unit) {
    Focusable(onClick = onClick, shape = RoundedCornerShape(50), lift = 1.04f) { f ->
        Row(
            Modifier
                .background(if (f) C.Paper.copy(alpha = 0.12f) else C.Ink950)
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(Prefs.profileName, style = MaterialTheme.typography.labelLarge, color = C.Paper)
            Text("تبديل", style = MaterialTheme.typography.labelMedium, color = C.Muted)
        }
    }
}

@Composable
private fun PhoneBottomBar(tab: Tab, onTab: (Tab) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(C.Ink900)
            .navigationBarsPadding()
            .height(62.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Tab.entries.forEach { t ->
            val on = t == tab
            Focusable(onClick = { onTab(t) }, lift = 1f, modifier = Modifier.weight(1f)) {
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(t.icon, t.label, Modifier.size(22.dp), tint = if (on) C.Gold else C.Muted)
                    Text(t.label, fontSize = 11.sp, color = if (on) C.Gold else C.Muted, fontFamily = Cairo)
                }
            }
        }
    }
}
