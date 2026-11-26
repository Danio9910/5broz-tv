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
        versionCode = 16
        versionName = "2.2.1"
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
        release {
            isMinifyEnabled = false
            if (ksPass.isNotEmpty()) signingConfig = signingConfigs.getByName("family")
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

    // Built-in torrent engine (plays without a debrid service, like Stremio).
    implementation(project(":torrent"))
    val lt = "2.1.0-39"
    implementation("org.libtorrent4j:libtorrent4j-android-arm:$lt")
    implementation("org.libtorrent4j:libtorrent4j-android-arm64:$lt")
    implementation("org.libtorrent4j:libtorrent4j-android-x86_64:$lt")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    val media3 = "1.6.1"
    implementation("androidx.media3:media3-exoplayer:$media3")
    implementation("androidx.media3:media3-exoplayer-hls:$media3")
    implementation("androidx.media3:media3-ui:$media3")
    implementation("androidx.media3:media3-datasource-okhttp:$media3")
}
