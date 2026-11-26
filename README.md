# 5broz — Android app

Native Android app (Kotlin + Jetpack Compose + Media3) for phones, tablets and TVs.
Talks to the same backend as https://5broz.com (`/api/*`) and the same Supabase
household database, so profiles, progress and "My List" are shared everywhere.

Every push to `main` builds an APK on GitHub Actions. When the repository secret
`KEYSTORE_PASSWORD` is set, the APK is signed and published to:
https://github.com/Danio9910/5broz-tv/releases/download/latest/5broz.apk

The app checks `https://5broz.com/api/appversion` on launch and offers newer builds.
Bump `versionCode` in `app/build.gradle.kts` and in the website's `api/appversion.ts` together.
