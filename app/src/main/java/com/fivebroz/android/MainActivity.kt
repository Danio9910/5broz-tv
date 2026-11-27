package com.fivebroz.android

import android.content.Intent
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.fivebroz.android.data.Prefs
import com.fivebroz.android.ui.AppNav
import com.fivebroz.android.ui.C
import com.fivebroz.android.ui.FiveBrozTheme
import com.fivebroz.android.ui.LocalIsTablet
import com.fivebroz.android.ui.LocalIsTv

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        // Draw behind the status bar like native streaming apps; screens pad themselves.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.rgb(10, 10, 11)),
        )
        Prefs.init(applicationContext)
        com.fivebroz.android.ui.Look.load()
        // Start the torrent engine's network while the person is still browsing.
        com.fivebroz.android.player.StreamPrefetch.warmEngine(this)
        // Daily check for new episodes of followed series; refresh the home-screen widget.
        // (WorkManager opens a database: done off the main thread, after the first screen.)
        val app = applicationContext
        Thread {
            Thread.sleep(3000)
            runCatching { NewEpisodesWorker.schedule(app); ContinueWidget.refresh(app) }
        }.start()
        // A title link (TV home screen) only opens for a device that's already set up.
        // Benchmark builds only (CI comparisons): start as the guest profile with no access-code screen.
        if (BuildConfig.BENCH && intent?.getBooleanExtra("bench", false) == true) {
            Prefs.gatePassed = true
            if (Prefs.profileId == null) { Prefs.profileId = "guest"; Prefs.profileName = "اختبار" }
        }
        takeRemoteLink(intent)
        // Passed the access code before household tokens existed: once the server issues them,
        // ask for the code one more time (the answer brings this device its token).
        if (Prefs.gatePassed && Prefs.houseToken.isBlank() && !BuildConfig.BENCH) {
            lifecycleScope.launch {
                val h = com.fivebroz.android.data.Api.health()
                if (h?.house == true && Prefs.houseToken.isBlank()) {
                    Prefs.gatePassed = false
                    recreate()
                }
            }
        }
        if (intent?.data != null && (!Prefs.gatePassed || Prefs.profileId == null)) intent = intent.setData(null)
        // Phones browse upright like other streaming apps (the player rotates on its own).
        if (packageManager.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN) &&
            !packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) &&
            resources.configuration.smallestScreenWidthDp < 600
        ) {
            requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        val isTv = packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
            !packageManager.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)

        setContent {
            // Arabic-first: the whole interface is right-to-left regardless of device language.
            CompositionLocalProvider(
                LocalLayoutDirection provides LayoutDirection.Rtl,
                LocalIsTv provides isTv,
                LocalIsTablet provides (!isTv && resources.configuration.smallestScreenWidthDp >= 600),
            ) {
                FiveBrozTheme {
                    Box(Modifier.fillMaxSize().background(C.Ink950)) {
                        AppNav()
                        com.fivebroz.android.ui.ToastHost(
                            Modifier.align(androidx.compose.ui.Alignment.BottomCenter).padding(bottom = 120.dp, start = 16.dp, end = 16.dp),
                        )
                        var intro by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(savedInstanceState == null) }
                        if (intro) com.fivebroz.android.ui.LaunchIntro { intro = false }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        takeRemoteLink(intent)
    }

    /** fivebroz://remote/<tv id> (from the code on a TV screen): open that TV's remote. */
    private fun takeRemoteLink(i: Intent?) {
        val d = i?.data ?: return
        if (d.scheme == "fivebroz" && d.host == "remote") {
            com.fivebroz.android.ui.PendingRemote.tv = d.pathSegments.firstOrNull()
            i.data = null
        }
    }

}
