package com.fivebroz.android

import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
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
import com.fivebroz.android.ui.LocalIsTv

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Prefs.init(applicationContext)
        val isTv = packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
            !packageManager.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)

        setContent {
            // Arabic-first: the whole interface is right-to-left regardless of device language.
            CompositionLocalProvider(
                LocalLayoutDirection provides LayoutDirection.Rtl,
                LocalIsTv provides isTv,
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
