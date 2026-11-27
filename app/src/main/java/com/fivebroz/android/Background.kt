package com.fivebroz.android

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Build
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.fivebroz.android.data.Api
import com.fivebroz.android.data.Db
import com.fivebroz.android.data.Prefs
import com.fivebroz.android.player.PlayerActivity
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * Once a day: has a series this profile follows (My List or watching) released an episode in the
 * last two days? Then one notification per new episode: «نزلت الحلقة 5 من …».
 */
class NewEpisodesWorker(ctx: Context, p: WorkerParameters) : CoroutineWorker(ctx, p) {
    override suspend fun doWork(): Result {
        Prefs.init(applicationContext)
        if (Prefs.profileId == null) return Result.success()
        val followed = (Db.library() + Db.continueWatching().map { it.item })
            .filter { it.type == "series" }
            .distinctBy { it.id }
            .take(25)
        val today = LocalDate.now()
        val sp = applicationContext.getSharedPreferences("5broz", Context.MODE_PRIVATE)
        for (s in followed) {
            val d = runCatching { Api.detail("series", s.id) }.getOrNull() ?: continue
            val fresh = d.episodes.filter { e ->
                val day = runCatching { LocalDate.parse(e.released?.take(10)) }.getOrNull() ?: return@filter false
                !day.isAfter(today) && day.isAfter(today.minusDays(3))
            }
            for (e in fresh) {
                val key = "notified.${e.id}"
                if (sp.getBoolean(key, false)) continue
                sp.edit().putBoolean(key, true).apply()
                Notify.newEpisode(applicationContext, d.base.name, e.season, e.episode, e.title, d.base.type, d.base.id)
            }
        }
        return Result.success()
    }

    companion object {
        fun schedule(ctx: Context) {
            val req = PeriodicWorkRequestBuilder<NewEpisodesWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(2, TimeUnit.HOURS)
                .build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork("new-episodes", ExistingPeriodicWorkPolicy.KEEP, req)
        }
    }
}

object Notify {
    private const val CHANNEL = "episodes"

    /**
     * Ask for notification permission at the moment it makes sense (following a series,
     * starting a download) — never on first launch. Android 13+ only, once.
     */
    fun askWhenUseful(ctx: Context) {
        if (Build.VERSION.SDK_INT < 33) return
        val a = ctx as? android.app.Activity ?: return
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
        val sp = ctx.getSharedPreferences("5broz", Context.MODE_PRIVATE)
        if (sp.getBoolean("asked_notif", false)) return
        sp.edit().putBoolean("asked_notif", true).apply()
        a.requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 41)
    }

    fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL) == null) {
                nm.createNotificationChannel(NotificationChannel(CHANNEL, "حلقات جديدة", NotificationManager.IMPORTANCE_DEFAULT))
            }
        }
    }

    fun newEpisode(ctx: Context, show: String, season: Int, episode: Int, title: String, type: String, id: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        ensureChannel(ctx)
        val open = PendingIntent.getActivity(
            ctx, id.hashCode(),
            Intent(Intent.ACTION_VIEW, Uri.parse("fivebroz://title/$type/${Uri.encode(id)}")).setPackage(ctx.packageName),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_5)
            .setContentTitle("نزلت الحلقة $episode من $show")
            .setContentText("الموسم $season" + if (title.isNotBlank()) " — $title" else "")
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(ctx).notify(("$id:$season:$episode").hashCode(), n) }
    }
}

/** Home-screen widget: the last thing this profile was watching, one tap to carry on. */
class ContinueWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) = refresh(ctx)

    companion object {
        fun refresh(ctx: Context) {
            val ids = AppWidgetManager.getInstance(ctx).getAppWidgetIds(ComponentName(ctx, ContinueWidget::class.java))
            if (ids.isEmpty()) return
            WorkManager.getInstance(ctx).enqueueUniqueWork(
                "widget", ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<WidgetWorker>().build(),
            )
        }
    }
}

class WidgetWorker(ctx: Context, p: WorkerParameters) : CoroutineWorker(ctx, p) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        Prefs.init(ctx)
        val mgr = AppWidgetManager.getInstance(ctx)
        val ids = mgr.getAppWidgetIds(ComponentName(ctx, ContinueWidget::class.java))
        if (ids.isEmpty()) return Result.success()
        val v = RemoteViews(ctx.packageName, R.layout.widget_continue)
        val r = if (Prefs.profileId != null) Db.continueWatching().firstOrNull() else null
        if (r == null) {
            v.setTextViewText(R.id.w_title, "5broz")
            v.setTextViewText(R.id.w_sub, "ابدأ فيلم، ويطلع هنا تكمّله بضغطة")
            v.setImageViewResource(R.id.w_poster, R.mipmap.ic_launcher)
            v.setTextViewText(R.id.w_button, "افتح")
            val open = PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
            v.setOnClickPendingIntent(R.id.w_root, open)
        } else {
            v.setTextViewText(R.id.w_title, r.item.name)
            v.setTextViewText(R.id.w_sub, "بقي ${(100 - (r.progress * 100).toInt()).coerceIn(1, 99)}٪ — ${Prefs.profileName}")
            v.setTextViewText(R.id.w_button, "كمّل")
            v.setProgressBar(R.id.w_progress, 100, (r.progress * 100).toInt(), false)
            poster(ctx, r.item.poster)?.let { v.setImageViewBitmap(R.id.w_poster, it) }
            val play = Intent(ctx, PlayerActivity::class.java)
                .putExtra("type", r.item.type).putExtra("metaId", r.item.id).putExtra("name", r.item.name)
                .putExtra("poster", r.item.poster).putExtra("videoId", r.videoId).putExtra("label", "")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            v.setOnClickPendingIntent(R.id.w_root, PendingIntent.getActivity(ctx, 1, play, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        }
        mgr.updateAppWidget(ids, v)
        return Result.success()
    }

    private suspend fun poster(ctx: Context, url: String?): Bitmap? {
        url ?: return null
        val req = coil.request.ImageRequest.Builder(ctx).data(url.replace("/w500/", "/w342/")).allowHardware(false).size(240, 360).build()
        return (coil.Coil.imageLoader(ctx).execute(req).drawable as? BitmapDrawable)?.bitmap
    }
}
