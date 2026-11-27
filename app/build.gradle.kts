plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.fivebroz.android"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.fivebroz.android"
        minSdk = 26
        targetSdk = 35
        versionCode = 35
        versionName = "4.6"
    }

    // The keystore is committed; its password lives only in the CI secret
    // KEYSTORE_PASSWORD. Without it the build still compiles, but unsigned.
    val ksPass = System.getenv("KEYSTORE_PASSWORD").orEmpty()
    signingConfigs {
        create("family") {
            storeFile = file("5broz.jks")
            storePassword = ksPass
            keyAlias = "5broz"
            keyPassword = ksPass
        }
    }

    buildTypes {
        // What the family installs: shrunk and optimised by R8, with a start-up profile
        // (src/main/baseline-prof.txt) so Android pre-compiles the hot paths at install.
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (ksPass.isNotEmpty()) signingConfig = signingConfigs.getByName("family")
            buildConfigField("boolean", "BENCH", "false")
        }
        // Same as release, test-signed, for measuring against other apps on CI emulators.
        create("bench") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
            buildConfigField("boolean", "BENCH", "true")
        }
        debug {
            buildConfigField("boolean", "BENCH", "false")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

// Test (debug) builds report version 1, so the emulator test always gets offered
// the published update and can exercise the real download flow.
androidComponents {
    onVariants(selector().withBuildType("debug")) { v ->
        v.outputs.forEach { it.versionCode.set(1) }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.05.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")

    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.0")
    implementation("androidx.navigation:navigation-compose:2.9.0")

    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("androidx.palette:palette-ktx:1.0.0")

    // Built-in torrent engine (plays without a debrid service, like Stremio).
    implementation(project(":torrent"))
    val lt = "2.1.0-39"
    implementation("org.libtorrent4j:libtorrent4j-android-arm:$lt")
    implementation("org.libtorrent4j:libtorrent4j-android-arm64:$lt")
    implementation("org.libtorrent4j:libtorrent4j-android-x86_64:$lt")
    // 32-bit x86 only matters for the Android TV emulator in CI — kept out of the release APK.
    debugImplementation("org.libtorrent4j:libtorrent4j-android-x86:$lt")
    "benchImplementation"("org.libtorrent4j:libtorrent4j-android-x86:$lt")
    // names each composable in performance traces (benchmark builds only)
    "benchImplementation"("androidx.compose.runtime:runtime-tracing")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    val media3 = "1.6.1"
    implementation("androidx.media3:media3-exoplayer:$media3")
    implementation("androidx.media3:media3-exoplayer-hls:$media3")
    implementation("androidx.media3:media3-ui:$media3")
    implementation("androidx.media3:media3-session:$media3")
    // Software audio decoders (AC3, E-AC3, DTS, TrueHD…) so "5.1" releases play on every device, like VLC.
    implementation("org.jellyfin.media3:media3-ffmpeg-decoder:$media3+1")
    implementation("androidx.tvprovider:tvprovider:1.0.0")
    // QR on the TV so a phone can become its remote
    implementation("com.google.zxing:core:3.5.3")
    // daily new-episode check and the home-screen widget refresh
    implementation("androidx.work:work-runtime-ktx:2.10.1")
    // installs the start-up profiles (ours and the libraries') on sideloaded installs too
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")
    // real-device frame timing per screen (JankStats)
    implementation("androidx.metrics:metrics-performance:1.0.0-beta01")
    implementation("androidx.media3:media3-datasource-okhttp:$media3")
}
