package dev.matchday.phone

import android.Manifest
import android.os.Build
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.delay
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import dev.matchday.core.Game
import dev.matchday.core.SportsFeed
import dev.matchday.core.activeLolCompetition
import kotlinx.coroutines.launch
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MatchdayPhone() }
    }
}

private val navItems = listOf("오늘", "일정", "대진", "순위")
private val navIcons = listOf(Icons.Default.Home, Icons.Default.CalendarMonth, Icons.Default.AccountTree, Icons.Default.Leaderboard)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatchdayPhone() {
    val context = LocalContext.current
    val manager = remember { FeedManager(context) }
    val pref = remember { context.getSharedPreferences("matchday_settings", 0) }
    var feed by remember { mutableStateOf(manager.load()) }
    var sport by remember { mutableStateOf("KBO") }
    var lolComp by remember { mutableStateOf(feed.activeLolCompetition()) }
    var lolCompManuallySelected by remember { mutableStateOf(false) }
    var tab by remember { mutableStateOf("오늘") }
    var themeMode by remember { mutableStateOf(pref.getString("theme", "SYSTEM") ?: "SYSTEM") }
    var notificationEnabled by remember { mutableStateOf(ReminderSettings.enabled(context)) }
    var reminderMinutes by remember { mutableIntStateOf(ReminderSettings.minutes(context)) }
    var chosenDay by remember { mutableStateOf(feed.referenceDay()) }
    var isBusy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var liveUrl by remember { mutableStateOf(manager.liveUrl()) }
    var liveCheckedAt by remember { mutableStateOf("") }
    var liveError by remember { mutableStateOf("") }
    var fastBackgroundSync by remember { mutableStateOf(FastScoreSyncService.isRunning()) }
    val lifecycle = (context as? ComponentActivity)?.lifecycle
    var isVisible by remember { mutableStateOf(lifecycle?.currentState?.isAtLeast(Lifecycle.State.STARTED) ?: true) }
    DisposableEffect(lifecycle) {
        val listener = LifecycleEventObserver { _, _ ->
            isVisible = lifecycle?.currentState?.isAtLeast(Lifecycle.State.STARTED) ?: true
            fastBackgroundSync = FastScoreSyncService.isRunning()
        }
        lifecycle?.addObserver(listener)
        onDispose { lifecycle?.removeObserver(listener) }
    }
    var detail by remember { mutableStateOf<Game?>(null) }
    val scope = rememberCoroutineScope()
    // Reflect notification Stop, session timeout or OS teardown while the
    // Activity remains visible, so its normal 60s polling resumes immediately.
    LaunchedEffect(isVisible) {
        while (isVisible) {
            fastBackgroundSync = FastScoreSyncService.isRunning()
            delay(5_000L)
        }
    }
    LaunchedEffect(feed) { if (!lolCompManuallySelected) lolComp = feed.activeLolCompetition() }
    val comp = if (sport == "KBO") "KBO" else lolComp
    val accent = if (sport == "KBO") KboBlue else LolGreen
    val dark = when (themeMode) {
        "DARK" -> true
        "LIGHT" -> false
        else -> androidx.compose.foundation.isSystemInDarkTheme()
    }
    val colors = if (dark) darkColorScheme(primary = accent, onPrimary = Color.White,
        background = DarkBackground, onBackground = Color.White, surface = DarkSurface,
        onSurface = Color.White, surfaceVariant = DarkElevated, onSurfaceVariant = Color(0xFFC2CEDD))
    else lightColorScheme(primary = accent, background = Color(0xFFF4F7FB),
        surface = Color.White, surfaceVariant = Color(0xFFE9EEF5))
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) {
            notificationEnabled = false
            ReminderSettings.update(context, false, reminderMinutes)
            ReminderScheduler.update(context, feed)
            message = "알림 권한이 허용되지 않았습니다. 설정에서 다시 켤 수 있습니다."
        }
    }

    // Start a dataSync foreground session only in response to a visible user action.
    val startFastSync: () -> Unit = {
        FastScoreSyncService.start(context).fold(
            onSuccess = {
                fastBackgroundSync = true
                message = "백그라운드 1분 갱신 시작 · 알림에서 중지 가능"
            },
            onFailure = {
                fastBackgroundSync = false
                message = "백그라운드 갱신 시작 실패: ${it.message}"
            }
        )
    }
    val fastPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startFastSync()
        else {
            fastBackgroundSync = false
            message = "알림 권한이 있어야 1분 백그라운드 갱신이 가능합니다"
        }
    }
    fun setFastSync(enabled: Boolean) {
        if (!enabled) {
            FastScoreSyncService.stop(context)
            fastBackgroundSync = false
            message = "1분 백그라운드 갱신 중지 · 15분 자동 동기화 유지"
        } else if (manager.liveUrl() == null) {
            message = "Cloudflare /feed.json URL을 먼저 저장해 주세요"
        } else if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            fastPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            startFastSync()
        }
    }

    fun setReminders(enabled: Boolean, mins: Int) {
        notificationEnabled = enabled
        reminderMinutes = mins
        ReminderSettings.update(context, enabled, mins)
        ReminderScheduler.update(context, feed)
        if (enabled && Build.VERSION.SDK_INT >= 33) permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    fun reload() {
        if (manager.url().isBlank()) {
            feed = manager.load(); message = "예제 피드로 실행 중입니다"
            return
        }
        scope.launch {
            isBusy = true
            manager.refresh().fold(
                onSuccess = { feed = manager.load(); chosenDay = it.referenceDay(); ReminderScheduler.update(context, it); message = "피드 수신 · KBO ${it.matches("KBO").size} / LoL ${it.games.count { g -> g.competition != "KBO" }} 경기 · ${it.updatedAt.take(16)}" },
                onFailure = { message = "동기화 실패: ${it.message} · 저장된 데이터 유지" }
            )
            isBusy = false
        }
    }
    LaunchedEffect(Unit) {
        manager.shareCurrent()
        BackgroundSync.apply(context)
        ReminderScheduler.update(context, feed)
        if (manager.url().isNotBlank()) reload()
    }

    // The 60-second loop exists only while the Activity is visible.
    // WorkManager is used separately for best-effort background refresh.
    LaunchedEffect(isVisible, liveUrl, fastBackgroundSync) {
        // Avoid two 60s timers when the foreground service is already syncing.
        if (!isVisible || liveUrl == null || fastBackgroundSync) return@LaunchedEffect
        while (true) {
            if (!isBusy) {
                manager.refreshLive().fold(
                    onSuccess = {
                        feed = it
                        liveCheckedAt = java.time.LocalTime.now().toString().take(5)
                        liveError = ""
                    },
                    onFailure = { error ->
                        liveError = if (error.message?.contains("404") == true)
                            "Worker v4 코드 배포 필요"
                        else "실시간 점수 조회 지연"
                    }
                )
            }
            delay(60_000L)
        }
    }

    MaterialTheme(colorScheme = colors) {
        Scaffold(
            topBar = {
                Column {
                    TopAppBar(
                        title = { Text("MATCHDAY", fontWeight = FontWeight.ExtraBold) },
                        actions = {
                            IconButton(onClick = { reload() }, enabled = !isBusy) { Icon(Icons.Default.Refresh, "새로고침") }
                            IconButton(onClick = { tab = "설정" }) { Icon(Icons.Default.Settings, "설정") }
                        }
                    )
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = sport == "KBO", onClick = { sport = "KBO" }, label = { Text("KBO · 야구") }, modifier = Modifier.weight(1f))
                        FilterChip(selected = sport == "LOL", onClick = { sport = "LOL" }, label = { Text("LoL · e스포츠") }, modifier = Modifier.weight(1f))
                    }
                    if (sport == "LOL") Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        listOf("LCK", "MSI", "EWC", "WORLDS", "FST").forEach { league ->
                            FilterChip(selected = lolComp == league, onClick = { lolComp = league; lolCompManuallySelected = true },
                                label = { Text(if (league == "WORLDS") "롤드컵" else league) })
                        }
                    }
                }
            },
            bottomBar = {
                NavigationBar {
                    navItems.forEachIndexed { i, name ->
                        NavigationBarItem(selected = tab == name, onClick = { tab = name },
                            icon = { Icon(navIcons[i], null) }, label = { Text(name) })
                    }
                }
            }
        ) { contentPadding ->
            Column(Modifier.fillMaxSize().padding(contentPadding)) {
                if (!feed.isDemo && liveUrl != null) {
                    Text(if (liveError.isNotBlank()) "실시간 스코어 · $liveError"
                        else ("실시간 스코어 · " +
                            (if (fastBackgroundSync) "백그라운드 1분 동기화 중"
                                else "앱 실행 중 약 60초 갱신") +
                            (if (liveCheckedAt.isBlank()) "" else " · 최근 조회 $liveCheckedAt")),
                        color = if (liveError.isNotBlank()) MaterialTheme.colorScheme.error else accent,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                }
                if (feed.isDemo) DemoRibbon()
                if (message.isNotBlank()) {
                    Text(message, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                }
                when (tab) {
                    "오늘" -> HomeScreen(feed, comp, chosenDay, onDetails = { detail = it },
                        onTab = { tab = it })
                    "일정" -> ScheduleScreen(feed, comp, chosenDay, onDay = { chosenDay = it }, onDetails = { detail = it })
                    "대진" -> BracketScreen(feed, comp)
                    "순위" -> StandingsScreen(feed, comp)
                    "설정" -> SettingsScreen(themeMode, onTheme = {
                            themeMode = it; pref.edit().putString("theme", it).apply(); manager.shareCurrent()
                        }, notificationEnabled, reminderMinutes, onReminder = ::setReminders,
                        fastSyncEnabled = fastBackgroundSync, onFastSync = ::setFastSync,
                        existingUrl = manager.url(), onSaveUrl = { link ->
                            if (link.isNotBlank() && !link.startsWith("https://", ignoreCase = true)) {
                                message = "HTTPS 주소만 사용할 수 있습니다"
                            } else {
                                manager.setUrl(link)
                                liveUrl = manager.liveUrl()
                                BackgroundSync.apply(context)
                                if (link.isBlank()) { FastScoreSyncService.stop(context); fastBackgroundSync = false; manager.resetDemo(); feed = manager.load(); chosenDay = feed.referenceDay() }
                                else reload()
                                tab = "오늘"
                            }
                        }, onReset = { FastScoreSyncService.stop(context); fastBackgroundSync = false; manager.resetDemo(); liveUrl = null; feed = manager.load(); chosenDay = feed.referenceDay();
                            BackgroundSync.apply(context); ReminderScheduler.update(context, feed); message = "데모 데이터로 돌아왔습니다" })
                }
            }
        }
        detail?.let { game ->
            AlertDialog(onDismissRequest = { detail = null }, title = { Text("경기 정보 · ${game.stage}") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        MatchRowTeams(feed, game, false)
                        Text("경기: ${game.day()} ${game.timeText()}")
                        Text("장소: ${game.venue.ifBlank { "미정" }}")
                        Text("상태: ${statusText(game.status)}")
                        if (feed.isDemo) Text("예제 경기이며 실제 일정/결과가 아닙니다.", color = MaterialTheme.colorScheme.error)
                    }
                }, confirmButton = { TextButton(onClick = { detail = null }) { Text("닫기") } })
        }
    }
}

@Composable
private fun DemoRibbon() {
    Surface(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.13f)) {
        Text("데모 데이터 · 경기 일정·점수·순위는 가상 예제입니다 (실제 기록 아님)",
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
    }
}
