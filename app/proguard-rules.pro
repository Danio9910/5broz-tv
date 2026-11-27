# libtorrent4j: SWIG/JNI bindings are called from native code by name.
-keep class org.libtorrent4j.** { *; }
-dontwarn org.libtorrent4j.**
# Our torrent module (engine is small; keep it readable in crash logs)
-keep class com.fivebroz.torrent.** { *; }
# FFmpeg audio decoders (JNI)
-keep class androidx.media3.decoder.ffmpeg.** { *; }
-keepclasseswithmembernames class * { native <methods>; }
# WebView bridges (trailer player)
-keepclassmembers class * { @android.webkit.JavascriptInterface <methods>; }
# zxing is plain Java; no reflection. OkHttp/Coil/Media3/WorkManager ship their own rules.
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
# Keep line numbers for readable crash reports
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
