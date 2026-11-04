package dev.matchday.phone

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dev.matchday.core.SportsFeed
import java.time.Duration
import java.time.OffsetDateTime
import java.time.Instant
import java.util.concurrent.TimeUnit

private const val CHANNEL = "game_reminders"
private const val PREFS = "matchday_settings"

object ReminderSettings {
    fun enabled(context: Context): Boolean = context.getSharedPreferences(PREFS, 0).getBoolean("reminders", false)
    fun minutes(context: Context): Int = context.getSharedPreferences(PREFS, 0).getInt("remind_min", 30)
    fun update(context: Context, enabled: Boolean, minutes: Int) {
        context.getSharedPreferences(PREFS, 0).edit().putBoolean("reminders", enabled).putInt("remind_min", minutes).apply()
    }
}

/** Android's scheduler may delay reminders; they are not exact real-time alarms. */
object ReminderScheduler {
    fun update(context: Context, feed: SportsFeed) {
        val wm = WorkManager.getInstance(context)
        // Cancel only our reminders (not unrelated app work).
        wm.cancelAllWorkByTag("matchday_reminder")
        if (!ReminderSettings.enabled(context)) return
        val now = Instant.now()
        val favoriteGames = feed.games.filter { it.home in setOf("SAM", "GEN") || it.away in setOf("SAM", "GEN") }
            .filter { it.status == "UPCOMING" }
        favoriteGames.forEach { game ->
            val at = runCatching { OffsetDateTime.parse(game.dateTime).toInstant() }.getOrNull() ?: return@forEach
            val alarmAt = at.minusSeconds(ReminderSettings.minutes(context) * 60L)
            if (alarmAt.isAfter(now)) {
                val wait = Duration.between(now, alarmAt).toMillis()
                val request = OneTimeWorkRequestBuilder<GameNotificationWorker>()
                    .setInitialDelay(wait, TimeUnit.MILLISECONDS)
                    .setInputData(workDataOf("gameId" to game.id))
                    .addTag("matchday_reminder")
                    .build()
                wm.enqueueUniqueWork("game_${game.id}", ExistingWorkPolicy.REPLACE, request)
            }
        }
    }
}

class GameNotificationWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (!ReminderSettings.enabled(applicationContext)) return Result.success()
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return Result.success()
        val feed = FeedManager(applicationContext).load()
        val game = feed.games.firstOrNull { it.id == inputData.getString("gameId") } ?: return Result.success()
        if (game.status != "UPCOMING") return Result.success()
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "경기 시작 알림", NotificationManager.IMPORTANCE_DEFAULT))
        val intent = Intent(applicationContext, MainActivity::class.java)
        val pending = PendingIntent.getActivity(applicationContext, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val delay = ReminderSettings.minutes(applicationContext)
        val body = "${feed.team(game.home).short} vs ${feed.team(game.away).short} · ${game.timeText()}"
        manager.notify(game.id.hashCode(), NotificationCompat.Builder(applicationContext, CHANNEL)
            .setSmallIcon(R.drawable.ic_matchday)
            .setContentTitle(if (delay == 0) "경기 시작 예정" else "${delay}분 뒤 경기 시작")
            .setContentText(body).setAutoCancel(true).setContentIntent(pending).build())
        return Result.success()
    }
}

/** Opt-in URL is fetched at best-effort intervals (the OS can postpone background jobs). */
class FeedSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val store = FeedManager(applicationContext)
        if (store.url().isBlank()) return Result.success()
        // Keep schedules reasonably fresh even if the app has not been opened.
        // Score changes are a separate compact API; send them to Wear Data Layer.
        val fullDue = store.isFullRefreshDue(30 * 60_000L)
        val refreshFull = if (fullDue) store.refresh()
            else kotlin.Result.success(store.load())
        val refreshed = refreshFull.getOrElse { store.load() }
        // Reminder rescheduling is expensive; do it only after a new full feed.
        if (fullDue && refreshFull.isSuccess && !refreshed.isDemo)
            ReminderScheduler.update(applicationContext, refreshed)

        // An active foreground session handles live scores every minute.
        val refreshScores = if (FastScoreSyncService.isRunning())
            kotlin.Result.success(store.load())
        else if (store.liveUrl() != null) store.refreshLive()
        else kotlin.Result.success(store.load())
        // If the very first full sync failed, do not pretend a demo feed is
        // successfully synchronized simply because the live endpoint returned.
        return if ((refreshed.isDemo && refreshFull.isFailure) ||
            (refreshFull.isFailure && refreshScores.isFailure)) Result.retry()
        else Result.success()
    }
}

object BackgroundSync {
    fun apply(context: Context) {
        val wm = WorkManager.getInstance(context)
        if (FeedManager(context).url().isBlank()) {
            wm.cancelUniqueWork("matchday_periodic_sync")
            return
        }
        val request = PeriodicWorkRequestBuilder<FeedSyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.MINUTES)
            .build()
        wm.enqueueUniquePeriodicWork("matchday_periodic_sync", ExistingPeriodicWorkPolicy.KEEP, request)
    }
}
