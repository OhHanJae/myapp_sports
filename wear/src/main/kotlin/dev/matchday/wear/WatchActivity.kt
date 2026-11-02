package dev.matchday.wear

import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import dev.matchday.core.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val blue = Color(0xFF438EFF)
private val green = Color(0xFF21CE80)

class WatchActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { WatchApp() }
    }
}

@Composable
fun WatchApp() {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences(WEAR_PREFS, 0) }
    var feed by remember { mutableStateOf(WatchFeedStore(ctx).read()) }
    var theme by remember { mutableStateOf(WatchAppearance.theme(ctx)) }
    var colorPreset by remember { mutableStateOf(WatchAppearance.accent(ctx)) }
    DisposableEffect(Unit) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _: SharedPreferences, key: String? ->
            if (key == "feed_json") feed = WatchFeedStore(ctx).read()
            if (key == "watch_theme" || key == "theme") theme = WatchAppearance.theme(ctx)
            if (key == "watch_accent") colorPreset = WatchAppearance.accent(ctx)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    val startSport = (ctx as? WatchActivity)?.intent?.getStringExtra("sport") ?: "KBO"
    var sport by remember { mutableStateOf(if (startSport == "LOL") "LOL" else "KBO") }
    var tab by remember { mutableStateOf("오늘") }
    var offset by remember { mutableIntStateOf(0) }
    var group by remember { mutableStateOf("UPPER") }
    val comp = if (sport == "KBO") "KBO" else feed.watchLolCompetition()
    val accent = WatchAppearance.accentColor(colorPreset, sport)
    val dark = when(theme) { "DARK" -> true; "LIGHT" -> false; else -> isSystemInDarkTheme() }
    val bg = if (dark) Color(0xFF07131E) else Color(0xFFF5F8FB)
    val card = if (dark) Color(0xFF163040) else Color(0xFFE4ECF4)
    val textColor = if (dark) Color.White else Color(0xFF102536)
    val subColor = if (dark) Color(0xFFA6B9C8) else Color(0xFF5B6D7A)
    val day = feed.referenceDay().plusDays(offset.toLong())
    // Every LoL tournament is visible together; remove per-league filters.
    // Dedup IDs before using them as lazy keys to avoid a duplicate-key crash.
    val games = remember(feed, sport) {
        (if (sport == "KBO") feed.matches("KBO") else
            feed.games.filter { it.competition in LOL_COMPETITIONS }.sortedBy { it.dateTime })
            .distinctBy { it.id }
    }
    val featured = if (sport == "KBO")
        feed.selectTileMatch(listOf("KBO"), "SAM", day)?.game else feed.watchLolFeatured(day)
    // Different tournaments have different brackets: not all have upper/lower groups.
    val bracketGroups = feed.rounds(comp).map { it.group }.distinct()
    val effectiveGroup = if (sport == "KBO") "MAIN" else if (group in bracketGroups) group else bracketGroups.firstOrNull() ?: "MAIN"
    val rounds = feed.rounds(comp, effectiveGroup).distinctBy { it.id }
    val listState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    // Request rotary focus every time a sport or tab changes. Keep touch scrolling too.
    LaunchedEffect(sport, tab) {
        listState.scrollToItem(0)
        focusRequester.requestFocus()
    }
    MaterialTheme {
        LazyColumn(
            modifier = Modifier.fillMaxSize().background(bg)
                .onRotaryScrollEvent { event ->
                    scope.launch { listState.scrollBy(event.verticalScrollPixels * 0.55f) }
                    true
                }
                .focusRequester(focusRequester).focusable(),
            state = listState,
            contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 20.dp, bottom = 30.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            item { Text("MATCHDAY", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = accent) }
            if (feed.isDemo) item { Text("DEMO · 가상 경기", color = Color(0xFFFFB569), fontSize = 10.sp) }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    WatchChip("KBO", sport == "KBO", accent, card, textColor, Modifier.weight(1f)) { sport = "KBO"; offset = 0; group = "MAIN" }
                    WatchChip("LoL", sport == "LOL", accent, card, textColor, Modifier.weight(1f)) { sport = "LOL"; offset = 0; group = "UPPER" }
                }
            }
            if (sport == "LOL") item {
                Text("리그 자동 선택 · ${if (featured?.status == "LIVE") "진행 중" else "다음 경기"}: ${comp}",
                    color = subColor, fontSize = 10.sp, maxLines = 1)
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    listOf("오늘", "일정", "대진", "순위").forEach { x ->
                        WatchChip(x, tab == x, accent, card, textColor, Modifier.weight(1f)) { tab = x }
                    }
                }
            }
            when (tab) {
                "오늘" -> {
                    item { WatchHeader(if (featured != null && featured.home != feed.favorite(comp) && featured.away != feed.favorite(comp)) "선택한 대회의 경기" else "응원팀 경기", accent) }
                    if (featured != null) item { WatchGame(feed, featured, card, accent, textColor, subColor, featured = true) }
                    else item { WatchEmpty("등록된 경기가 없습니다", card, textColor) }
                    item { WatchHeader("${day.monthValue}/${day.dayOfMonth} · 전체 경기", accent) }
                    val todays = games.filter { it.day() == day }
                    if (todays.isEmpty()) item { WatchEmpty("오늘 경기가 없습니다", card, textColor) }
                    else items(todays, key = { "today_${it.id}" }) { WatchGame(feed, it, card, accent, textColor, subColor) }
                }
                "일정" -> {
                    item {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            WatchChip("‹", false, accent, card, textColor) { offset-- }
                            Text("${day.monthValue}/${day.dayOfMonth} (${day.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, Locale.KOREAN)})", color = textColor, fontSize = 13.sp)
                            WatchChip("›", false, accent, card, textColor) { offset++ }
                        }
                    }
                    val todays = games.filter { it.day() == day }
                    if (todays.isEmpty()) item { WatchEmpty("선택한 날짜에 경기 없음", card, textColor) }
                    else items(todays, key = { "schedule_${it.id}" }) { WatchGame(feed, it, card, accent, textColor, subColor) }
                    item { WatchHeader("앞으로의 일정", accent) }
                    items(games.filter { it.day() > day }.take(8), key = { "next_${it.id}" }) { WatchGame(feed, it, card, accent, textColor, subColor) }
                }
                "순위" -> {
                    item { WatchHeader("$comp 순위", accent) }
                    val table = feed.rankings(comp)
                    if (table.isEmpty()) item { WatchEmpty("현재 순위 정보가 없습니다", card, textColor) }
                    else items(table, key = { "rank_${it.team}" }) { s ->
                        Row(Modifier.fillMaxWidth().background(
                            if (s.team == feed.favorite(comp)) accent.copy(alpha = .22f) else card, RoundedCornerShape(13.dp))
                            .padding(7.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("${s.rank}", color = accent, fontSize = 12.sp, modifier = Modifier.width(19.dp), fontWeight = FontWeight.Bold)
                            WatchLogo(feed.team(s.team), 25)
                            Spacer(Modifier.width(4.dp))
                            Column(Modifier.weight(1f)) {
                                Text(feed.team(s.team).short, color = textColor, fontSize = 12.sp, maxLines = 1)
                                Text(if (comp == "KBO") s.ratio() else "득실 ${s.forScore-s.againstScore}", color = subColor, fontSize = 10.sp)
                            }
                            Text("${s.won}-${s.lost}", color = textColor, fontSize = 11.sp)
                        }
                    }
                }
                "대진" -> {
                    item { WatchHeader("$comp 대진", accent) }
                    if (sport == "LOL") item {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            val groups = feed.rounds(comp).map { it.group }.distinct()
                            groups.distinct().take(3).forEach { g -> WatchChip(when(g){"UPPER"->"승자조"; "LOWER"->"패자조"; "MAIN"->"결승"; else->g},
                                effectiveGroup == g, accent, card, textColor, Modifier.weight(1f)) { group = g } }
                        }
                    }
                    if (rounds.isEmpty()) item { WatchEmpty("대진표가 준비되지 않았습니다", card, textColor) }
                    else items(rounds, key = { it.id }) { s -> WatchBracket(feed, s, card, accent, textColor, subColor) }
                }
                "설정" -> {
                    item { WatchHeader("워치 색상 테마", accent) }
                    item { Text("표시 모드", color = subColor, fontSize = 11.sp) }
                    item {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            WatchAppearance.themes.forEach { (id, label) ->
                                WatchChip(label, theme == id, accent, card, textColor, Modifier.weight(1f)) {
                                    WatchAppearance.save(ctx, WatchAppearance.PREF_THEME, id)
                                }
                            }
                        }
                    }
                    item { WatchHeader("강조 색상", accent) }
                    items(WatchAppearance.accents.chunked(2)) { pair ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            pair.forEach { (id, label) ->
                                WatchChip(label, colorPreset == id, accent, card, textColor,
                                    Modifier.weight(1f)) {
                                    WatchAppearance.save(ctx, WatchAppearance.PREF_ACCENT, id)
                                }
                            }
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                    item { Text("폰에서 동기화해도 워치 테마는 유지됩니다", color = subColor, fontSize = 10.sp, textAlign = TextAlign.Center) }
                }

            }
            item {
                WatchChip(if (tab == "설정") "오늘 화면으로" else "⚙ 설정 · 테마 변경",
                    tab == "설정", accent, card, textColor, Modifier.fillMaxWidth()) {
                    tab = if (tab == "설정") "오늘" else "설정"
                }
            }
            if (tab != "설정") item {
                Text("휴대폰에서 동기화한 경기 정보", color = subColor, fontSize = 9.sp,
                    textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

@Composable
private fun WatchChip(text: String, selected: Boolean, accent: Color, card: Color, textColor: Color,
                      modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(modifier.clip(RoundedCornerShape(50)).background(if (selected) accent else card).clickable(onClick = onClick)
        .padding(horizontal = 6.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
        Text(text, color = if (selected) Color.White else textColor, fontSize = 10.sp, maxLines = 1, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
private fun WatchHeader(text: String, accent: Color) {
    Text(text, color = accent, fontWeight = FontWeight.Bold, fontSize = 13.sp,
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
}

@Composable
private fun WatchEmpty(title: String, card: Color, textColor: Color) {
    Box(Modifier.fillMaxWidth().background(card, RoundedCornerShape(16.dp)).padding(14.dp), contentAlignment = Alignment.Center) {
        Text(title, color = textColor, fontSize = 11.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun WatchLogo(t: Team, size: Int = 32) {
    val context = LocalContext.current
    val localName = t.localBadgeName()
    val res = remember(localName) {
        context.resources.getIdentifier(localName, "drawable", context.packageName)
    }
    val logo = t.displayLogoUrl()
    val fallback = painterResource(if (res != 0) res else R.drawable.ic_matchday)
    // AsyncImage avoids heavy per-item subcomposition of SubcomposeAsyncImage in LazyColumn.
    // Missing logos, disconnected watch, or HTTP failures fall back to an offline badge.
    if (logo.isNotEmpty()) {
        AsyncImage(
            model = logo, contentDescription = t.name,
            placeholder = fallback, error = fallback, fallback = fallback,
            modifier = Modifier.size(size.dp), contentScale = ContentScale.Fit
        )
    } else {
        Image(fallback, contentDescription = t.name, modifier = Modifier.size(size.dp))
    }
}

@Composable
private fun WatchGame(feed: SportsFeed, g: Game, card: Color, accent: Color, textColor: Color, subColor: Color, featured: Boolean = false) {
    Column(Modifier.fillMaxWidth().background(if (featured) accent.copy(alpha = .20f) else card, RoundedCornerShape(15.dp))
        .padding(9.dp), verticalArrangement = Arrangement.spacedBy(5.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("${g.stage.ifBlank { g.competition }} · ${when(g.status){"LIVE"->"● LIVE";"FINAL"->"종료";"POSTPONED"->"연기";"UNKNOWN"->"상태 미확인";else->"예정"}}",
            color = if (g.status == "LIVE") Color(0xFFFF6C77) else subColor, fontSize = 10.sp)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                WatchLogo(feed.team(g.home), if (featured) 41 else 29)
                Text(feed.team(g.home).short, color = textColor, fontSize = 11.sp, maxLines = 1)
            }
            Text(g.scoreText(), color = accent, fontWeight = FontWeight.Bold, fontSize = if (featured) 19.sp else 15.sp)
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                WatchLogo(feed.team(g.away), if (featured) 41 else 29)
                Text(feed.team(g.away).short, color = textColor, fontSize = 11.sp, maxLines = 1)
            }
        }
        Text("${g.day().monthValue}/${g.day().dayOfMonth} · " +
            if (g.status == "LIVE") "${g.inning.ifBlank { "진행 중" }} · ${g.checkedAt.takeIf { it.length >= 16 }?.substring(11,16) ?: "갱신 대기"}" else g.venue, color = subColor, fontSize = 9.sp,
            textAlign = TextAlign.Center, maxLines = 1)
    }
}

@Composable
private fun WatchBracket(feed: SportsFeed, s: Series, card: Color, accent: Color, textColor: Color, subColor: Color) {
    // Keep the bracket glanceable; tapping reveals each individual game result.
    var expanded by remember(s.id) { mutableStateOf(false) }
    val games = remember(feed, s.id) { feed.games.filter { it.seriesId == s.id }.sortedBy { it.dateTime } }
    Column(Modifier.fillMaxWidth().background(card, RoundedCornerShape(15.dp))
        .clickable { expanded = !expanded }.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(s.stage, color = accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text(when(s.status){"LIVE"->"LIVE";"FINAL"->"종료";"UNKNOWN"->"상태 미확인";else->"예정"},
                color = if (s.status == "LIVE") Color.Red else subColor, fontSize = 10.sp)
        }
        listOf(s.left to s.leftWins, s.right to s.rightWins).forEach { (id,wins) ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                WatchLogo(feed.team(id), 26)
                Spacer(Modifier.width(7.dp))
                Text(feed.team(id).short, color = textColor, fontSize = 12.sp, modifier = Modifier.weight(1f), maxLines = 1)
                Text(if (s.status == "UPCOMING") "–" else wins.toString(), color = accent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
        Text("Bo${s.bestOf} · ${s.scheduled.take(10)}", color = subColor, fontSize = 9.sp)
        if (games.isNotEmpty()) {
            Text(if (expanded) "차전별 점수 접기 ▲" else "${games.size}개 차전 결과 펼치기 ▼", color = accent, fontSize = 10.sp)
            if (expanded) games.forEach { game ->
                Text("${game.day().monthValue}/${game.day().dayOfMonth}  " +
                    "${feed.team(game.home).short} ${game.scoreText()} ${feed.team(game.away).short} · " +
                    when (game.status) { "LIVE" -> "LIVE"; "FINAL" -> "종료"; "UNKNOWN" -> "상태 미확인"; else -> "예정" },
                    color = textColor, fontSize = 10.sp, maxLines = 2)
            }
        }
        if (s.nextId.isNotBlank()) Text("↓ ${feed.series.firstOrNull { it.id == s.nextId }?.stage ?: "다음 라운드"}", color = subColor, fontSize = 10.sp)
    }
}

@Preview(widthDp = 192, heightDp = 192, showBackground = true)
@Composable
private fun WatchPreview() {
    MaterialTheme {
        Box(Modifier.size(192.dp).background(Color(0xFF08141F)), contentAlignment = Alignment.Center) {
            Text("MATCHDAY\nKBO · LoL", color = blue, textAlign = TextAlign.Center)
        }
    }
}
