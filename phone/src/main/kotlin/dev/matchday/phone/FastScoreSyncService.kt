package dev.matchday.phone

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.util.concurrent.atomic.AtomicBoolean

/**
 * An explicit user-started dataSync foreground session. Android 15+ limits
 * this type to 6 background hours per rolling 24 hours. Stop after 5h30m
 * or onTimeout; automatic 15m WorkManager sync is independent.
 */
class FastScoreSyncService : Service() {
    companion object {
        private const val ACTION_START = "dev.matchday.phone.action.FAST_SYNC_START"
        private const val ACTION_STOP = "dev.matchday.phone.action.FAST_SYNC_STOP"
        private const val CHANNEL_ID = "matchday_fast_scores"
        private const val NOTIFICATION_ID = 2201
        private const val LIVE_INTERVAL_MS = 60_000L
        private const val MAX_SESSION_MS = 330L * 60_000L
        private val running = AtomicBoolean(false)

        fun isRunning(): Boolean = running.get()

        /** Invoke from visible Activity only, after user opts in. */
        fun start(context: Context): Result<Unit> = runCatching {
            require(FeedManager(context).liveUrl() != null) {
                "Cloudflare /feed.json 주소를 먼저 저장해 주세요"
            }
            ContextCompat.startForegroundService(
                context, Intent(context, FastScoreSyncService::class.java).setAction(ACTION_START)
            )
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, FastScoreSyncService::class.java))
        }
    }

    private val serviceJob = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + serviceJob)
    private var pollJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "MATCHDAY 경기 중 1분 갱신",
                NotificationManager.IMPORTANCE_LOW).apply {
                    description = "화면을 닫아도 경기 점수를 갱신하여 워치에 전송"
                }
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action != ACTION_START || FeedManager(this).liveUrl() == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (pollJob?.isActive == true) return START_NOT_STICKY
        try {
            val notification = buildNotification("경기 점수 동기화 시작")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (error: Exception) {
            Log.e("MatchdayFastSync", "Cannot promote dataSync foreground service", error)
            stopSelf()
            return START_NOT_STICKY
        }

        running.set(true)
        pollJob = scope.launch {
            val deadline = SystemClock.elapsedRealtime() + MAX_SESSION_MS
            val store = FeedManager(applicationContext)
            var lastFullAttempt = 0L
            try {
                while (isActive && SystemClock.elapsedRealtime() < deadline) {
                    val cycleStart = SystemClock.elapsedRealtime()
                    if (store.liveUrl() == null) break
                    try {
                        // Don't re-download the full season each minute.
                        if (store.isFullRefreshDue(30 * 60_000L) &&
                            (cycleStart - lastFullAttempt >= 30 * 60_000L || lastFullAttempt == 0L)) {
                            lastFullAttempt = cycleStart
                            store.refresh().onFailure {
                                Log.w("MatchdayFastSync", "Full feed retry later", it)
                            }
                        }
                        store.refreshLive().fold(
                            onSuccess = {
                                updateNotification("최근 확인 ${LocalTime.now().toString().take(5)} · 워치 동기화")
                            },
                            onFailure = {
                                Log.w("MatchdayFastSync", "Live score retry later", it)
                                updateNotification("연결 재시도 중 · 저장된 점수 유지")
                            }
                        )
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        Log.w("MatchdayFastSync", "Unexpected polling error", error)
                    }
                    delay((LIVE_INTERVAL_MS -
                        (SystemClock.elapsedRealtime() - cycleStart)).coerceAtLeast(1000L))
                }
            } finally {
                if (isActive) stopSelf()
            }
        }
        // Android must not resurrect a long foreground session without consent.
        return START_NOT_STICKY
    }

    private fun buildNotification(status: String): Notification {
        val launch = PendingIntent.getActivity(this, 11,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 12,
            Intent(this, FastScoreSyncService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_matchday)
            .setContentTitle("MATCHDAY · 백그라운드 1분 갱신")
            .setContentText(status)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setContentIntent(launch)
            .addAction(0, "갱신 중지", stop)
            .build()
    }

    private fun updateNotification(status: String) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification(status))
    }

    /** API 35+: prevent Android's 6-hour dataSync quota timeout crash. */
    override fun onTimeout(startId: Int, fgsType: Int) {
        Log.w("MatchdayFastSync", "Android background dataSync quota exhausted")
        stopSelf()
    }

    override fun onDestroy() {
        running.set(false)
        pollJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }
}
