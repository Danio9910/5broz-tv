package com.fivebroz.android

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
import androidx.compose.ui.Modifier
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
                    }
                }
            }
        }
    }
}
