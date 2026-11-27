package com.fivebroz.android

import android.content.Context
import android.os.Build
import com.fivebroz.android.data.Db
import com.fivebroz.android.data.Net
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * If the app crashes on someone's phone or TV, the error is written to a small file at that
 * moment and sent the next time the app opens: device model, app version, and the error with
 * its code location. Nothing about what was being watched.
 */
object Crashes {
    private fun file(ctx: Context) = File(ctx.filesDir, "last-crash.json")

    fun install(ctx: Context) {
        val app = ctx.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            runCatching {
                val trace = android.util.Log.getStackTraceString(e).take(8000)
                file(app).writeText(
                    JSONObject()
                        .put("device", "${Build.MANUFACTURER} ${Build.MODEL}".trim())
                        .put("android", Build.VERSION.SDK_INT)
                        .put("app_version", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})" + if (BuildConfig.DEBUG) "-debug" else "")
                        .put("thread", t.name)
                        .put("error", "${e.javaClass.name}: ${e.message}".take(500))
                        .put("stack", trace)
                        .toString(),
                )
            }
            previous?.uncaughtException(t, e)
        }
    }

    suspend fun sendPending(ctx: Context, tv: Boolean) {
        val f = file(ctx)
        if (!f.exists()) return
        runCatching {
            val o = JSONObject(f.readText()).put("kind", if (tv) "tv" else "phone")
            Net.send("POST", "${Db.URL}/crash_reports", JSONArray().put(o).toString(), Db.headers + mapOf("Prefer" to "return=minimal"))
            f.delete()
        }
    }
}
