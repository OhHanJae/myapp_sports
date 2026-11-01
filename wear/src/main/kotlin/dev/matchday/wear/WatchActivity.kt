package dev.matchday.wear

import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import coil.compose.SubcomposeAsyncImage
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
    var theme by remember { mutableStateOf(prefs.getString("theme", "SYSTEM") ?: "SYSTEM") }
    DisposableEffect(Unit) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _: SharedPreferences, key: String? ->
            if (key == "feed_json") feed = WatchFeedStore(ctx).read()
            if (key == "theme") theme = prefs.getString("theme", "SYSTEM") ?: "SYSTEM"
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    val startSport = (ctx as? WatchActivity)?.intent?.getStringExtra("sport") ?: "KBO"
    var sport by remember { mutableStateOf(if (startSport == "LOL") "LOL" else "KBO") }
    var compLol by remember { mutableStateOf(feed.activeLolCompetition()) }
    var tab by remember { mutableStateOf("오늘") }
    var offset by remember { mutableIntStateOf(0) }
    var group by remember { mutableStateOf("UPPER") }
    LaunchedEffect(feed) { compLol = feed.activeLolCompetition() }
    val comp = if (sport == "KBO") "KBO" else compLol
    val accent = if (sport == "KBO") blue else green
    val dark = when(theme) { "DARK" -> true; "LIGHT" -> false; else -> isSystemInDarkTheme() }
    val bg = if (dark) Color(0xFF07131E) else Color(0xFFF5F8FB)
    val card = if (dark) Color(0xFF163040) else Color(0xFFE4ECF4)
    val textColor = if (dark) Color.White else Color(0xFF102536)
    val subColor = if (dark) Color(0xFFA6B9C8) else Color(0xFF5B6D7A)
    val day = feed.referenceDay().plusDays(offset.toLong())
    val games = feed.matches(comp)
    val featured = feed.selectTileMatch(listOf(comp), feed.favorite(comp), day)?.game
    // Different tournaments have different brackets: not all have upper/lower groups.
    val bracketGroups = feed.rounds(comp).map { it.group }.distinct()
    val effectiveGroup = if (sport == "KBO") "MAIN" else if (group in bracketGroups) group else bracketGroups.firstOrNull() ?: "MAIN"
    val rounds = feed.rounds(comp, effectiveGroup)
    MaterialTheme {
        LazyColumn(Modifier.fillMaxSize().background(bg),
            contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 20.dp, bottom = 30.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            item { Text("MATCHDAY", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = accent) }
            if (feed.isDemo) item { Text("DEMO · 가상 경기", color = Color(0xFFFFB569), fontSize = 10.sp) }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    WatchChip("KBO", sport == "KBO", blue, card, textColor, Modifier.weight(1f)) { sport = "KBO"; group = "MAIN" }
                    WatchChip("LoL", sport == "LOL", green, card, textColor, Modifier.weight(1f)) { sport = "LOL"; group = "UPPER" }
                }
            }
            if (sport == "LOL") item {
                // Small screens: break a 5-league selector into two rows.
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf("LCK", "MSI", "EWC").forEach { x -> WatchChip(x, compLol == x, green, card, textColor, Modifier.weight(1f)) { compLol = x } }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf("WORLDS", "FST").forEach { x -> WatchChip(if (x == "WORLDS") "롤드컵" else x,
                            compLol == x, green, card, textColor, Modifier.weight(1f)) { compLol = x } }
                    }
                }
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
                    else items(todays, key = { it.id }) { WatchGame(feed, it, card, accent, textColor, subColor) }
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
                    else items(todays, key = { it.id }) { WatchGame(feed, it, card, accent, textColor, subColor) }
                    item { WatchHeader("앞으로의 일정", accent) }
                    items(games.filter { it.day() > day }.take(5), key = { "next_${it.id}" }) { WatchGame(feed, it, card, accent, textColor, subColor) }
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
                            groups.forEach { g -> WatchChip(when(g){"UPPER"->"승자조"; "LOWER"->"패자조"; "MAIN"->"결승"; else->g},
                                effectiveGroup == g, accent, card, textColor, Modifier.weight(1f)) { group = g } }
                        }
                    }
                    if (rounds.isEmpty()) item { WatchEmpty("대진표가 준비되지 않았습니다", card, textColor) }
                    else items(rounds, key = { it.id }) { s -> WatchBracket(feed, s, card, accent, textColor, subColor) }
                }
            }
            item { Text("폰에서 마지막으로 전송된 데이터", color = subColor, fontSize = 9.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 10.dp)) }
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
    val res = remember(t.id) { context.resources.getIdentifier("team_${t.id.lowercase()}", "drawable", context.packageName) }
    if (t.logoUrl.startsWith("https://")) {
        SubcomposeAsyncImage(model = t.logoUrl, contentDescription = t.name,
            loading = { WatchFallback(t, res, size) }, error = { WatchFallback(t, res, size) },
            modifier = Modifier.size(size.dp), contentScale = ContentScale.Fit)
    } else WatchFallback(t, res, size)
}

@Composable
private fun WatchFallback(team: Team, resource: Int, size: Int) {
    if (resource != 0) Image(painterResource(resource), contentDescription = team.name, modifier = Modifier.size(size.dp))
    else Box(Modifier.size(size.dp).background(Color.Gray, CircleShape), contentAlignment = Alignment.Center) {
        Text(team.mark.take(2), color = Color.White, fontSize = 9.sp)
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
