package dev.matchday.phone

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import dev.matchday.core.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dateFmt = DateTimeFormatter.ofPattern("M/d (E)", Locale.KOREAN)

@Composable
fun HomeScreen(feed: SportsFeed, competition: String, day: LocalDate, onDetails: (Game) -> Unit, onTab: (String) -> Unit) {
    val all = feed.matches(competition)
    val today = all.filter { it.day() == day }
    val favorite = feed.nearestFavorite(competition, day)
    LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text("${competitionLabel(competition)}  ·  ${day.format(dateFmt)}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            FeaturedMatch(feed, favorite, competition)
        }
        item { SectionTitle("오늘의 전체 경기", "${today.size}경기") }
        if (today.isEmpty()) item { EmptyCard("오늘 예정된 경기가 없습니다", "일정 탭에서 다른 날짜를 확인하세요") }
        else items(today, key = { it.id }) { GameItem(feed, it) { onDetails(it) } }
        item { SectionTitle("${competitionLabel(competition)} 순위", "전체 순위  ›") { onTab("순위") } }
        val standings = feed.rankings(competition)
        if (standings.isEmpty()) item { EmptyCard("이 대회에는 순위표가 없습니다", "대진표에서 토너먼트 진행 상황을 확인하세요") }
        else items(standings.take(4), key = { "home_st_${it.competition}_${it.team}" }) { StandingRow(feed, it, competition) }
        item { SectionTitle("플레이오프 · 토너먼트", "전체 대진  ›") { onTab("대진") } }
        items(feed.rounds(competition).take(3), key = { "home_b_${it.id}" }) { series -> SeriesCard(feed, series, onClick = { onTab("대진") }) }
        item { Spacer(Modifier.height(18.dp)) }
    }
}

@Composable
fun ScheduleScreen(feed: SportsFeed, competition: String, day: LocalDate, onDay: (LocalDate) -> Unit, onDetails: (Game) -> Unit) {
    // The shown week is independent of the selected day, preventing double jumps.
    var first by remember(competition) { mutableStateOf(day.minusDays((day.dayOfWeek.value - 1).toLong())) }
    val matches = feed.matches(competition).filter { it.day() == day }
    LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
        item {
            SectionTitle("경기 일정", "${competitionLabel(competition)}")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { first = first.minusWeeks(1) }) { Text("‹ 이전 주") }
                Text(day.format(DateTimeFormatter.ofPattern("yyyy년 M월", Locale.KOREAN)), fontWeight = FontWeight.Bold)
                TextButton(onClick = { first = first.plusWeeks(1) }) { Text("다음 주 ›") }
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                items(7) { i ->
                    val d = first.plusDays(i.toLong())
                    FilterChip(selected = d == day, onClick = { onDay(d) },
                        label = { Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(d.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, Locale.KOREAN), style = MaterialTheme.typography.labelSmall)
                            Text(d.dayOfMonth.toString(), fontWeight = FontWeight.Bold)
                        } })
                }
            }
            TextButton(onClick = { onDay(feed.referenceDay()); first = feed.referenceDay().minusDays((feed.referenceDay().dayOfWeek.value - 1).toLong()) }) { Text(if (feed.isDemo) "데모 기준일로" else "오늘로") }
        }
        item { SectionTitle(day.format(dateFmt) + " 경기", "${matches.size}경기") }
        if (matches.isEmpty()) item { EmptyCard("해당 날짜 경기가 없습니다", "위의 날짜를 선택하거나 주를 이동해 보세요") }
        else items(matches, key = { it.id }) { GameItem(feed, it) { onDetails(it) } }
        item { SectionTitle("다가오는 경기", "최대 5경기") }
        items(feed.matches(competition).filter { it.day() > day }.take(5), key = { "later_${it.id}" }) { GameItem(feed, it) { onDetails(it) } }
        item { Spacer(Modifier.height(18.dp)) }
    }
}

@Composable
fun StandingsScreen(feed: SportsFeed, competition: String) {
    val data = feed.rankings(competition)
    LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        item {
            Text("${competitionLabel(competition)} 순위", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("${if (competition == "KBO" || competition == "LCK") "정규시즌 전적" else "대회 제공 전적"}  ·  ${feed.updatedAt.take(10)}",
                color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("순위", modifier = Modifier.width(36.dp), style = MaterialTheme.typography.labelSmall)
                Text("팀", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall)
                Text("전적", modifier = Modifier.width(100.dp), textAlign = TextAlign.End, style = MaterialTheme.typography.labelSmall)
                Text(if (competition == "KBO") "승률" else "득실", modifier = Modifier.width(53.dp), textAlign = TextAlign.End, style = MaterialTheme.typography.labelSmall)
            }
        }
        if (data.isEmpty()) item { EmptyCard("제공된 순위 데이터가 없습니다", if (competition == "KBO" && !feed.isDemo) "KBO 정규시즌 순위가 아직 수신되지 않았습니다. Cloudflare 피드를 확인하세요" else "대회 API가 순위를 제공하지 않거나, 해당 시즌 데이터가 아직 조회되지 않았습니다") }
        else items(data, key = { "rank_${it.competition}_${it.team}" }) { StandingRow(feed, it, competition) }
        item { Text("로고는 피드의 HTTPS 이미지 URL을 우선 사용하고, 없는 경우 임시 아이콘을 표시합니다.",
            color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(vertical = 15.dp)) }
    }
}

@Composable
fun StandingRow(feed: SportsFeed, s: Standing, comp: String) {
    val team = feed.team(s.team)
    val favorite = s.team == feed.favorite(comp)
    val accent = if (comp == "KBO") KboBlue else LolGreen
    Surface(color = if (favorite) accent.copy(alpha = .18f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .45f),
        shape = RoundedCornerShape(13.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("${s.rank}", modifier = Modifier.width(29.dp), fontWeight = FontWeight.Bold, color = if (favorite) accent else MaterialTheme.colorScheme.onSurface)
            TeamLogo(team, size = 31.dp)
            Column(Modifier.weight(1f)) {
                Text(team.short, fontWeight = if (favorite) FontWeight.Bold else FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(s.streak, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(if (comp == "KBO") "${s.won}승 ${s.lost}패" else "${s.won}-${s.lost}",
                textAlign = TextAlign.End, modifier = Modifier.width(78.dp), style = MaterialTheme.typography.bodySmall)
            Text(if (comp == "KBO") s.ratio() else "%+d".format(s.forScore - s.againstScore),
                textAlign = TextAlign.End, modifier = Modifier.width(49.dp), style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun BracketScreen(feed: SportsFeed, competition: String) {
    val availableGroups = feed.rounds(competition).map { it.group }.distinct()
    var group by remember(competition, availableGroups) { mutableStateOf(if ("UPPER" in availableGroups) "UPPER" else availableGroups.firstOrNull() ?: "MAIN") }
    var selected by remember(competition) { mutableStateOf<String?>(null) }
    val tree = feed.rounds(competition, group)
    val grouped = tree.groupBy { it.roundIndex }.toSortedMap()
    val series = feed.series.firstOrNull { it.id == selected } ?: tree.firstOrNull()
    LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text(if (competition == "KBO") "가을야구 대진표" else "${competitionLabel(competition)} 토너먼트",
                style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("라운드별 실제 제공 대진 · 카드를 누르면 시리즈 결과 확인",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (availableGroups.size > 1) item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(availableGroups) { g ->
                    FilterChip(selected = group == g, onClick = { group = g; selected = null },
                        label = { Text(when (g) { "UPPER" -> "승자조"; "LOWER" -> "패자조"; "MAIN" -> "결승"; "GROUP" -> "그룹"; else -> g }) })
                }
            }
        }
        if (tree.isEmpty()) item { EmptyCard("대진 정보가 없습니다", if (competition == "KBO" && !feed.isDemo) "KBO 정규시즌 데이터와 별개로 포스트시즌 대진 데이터는 아직 제공되지 않습니다" else "대회 API에서 대진을 제공하지 않거나, 해당 시즌 대진이 발표되지 않았습니다") }
        else item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
                val rounds = grouped.entries.toList()
                items(rounds.size) { index ->
                    val round = rounds[index]
                    Column(Modifier.width(220.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(round.value.first().stage, color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        round.value.forEach { s ->
                            SeriesCard(feed, s, selected = selected == s.id || (selected == null && s.id == tree.first().id)) {
                                selected = s.id
                            }
                        }
                    }
                    if (index < rounds.lastIndex) Box(Modifier.width(25.dp).height(100.dp), contentAlignment = Alignment.Center) {
                        Text("→", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleLarge)
                    }
                }
            }
        }
        if (series != null) item {
            SectionTitle("선택한 시리즈 상세", "Bo${series.bestOf}")
            SeriesDetail(feed, series)
        }
        item { Spacer(Modifier.height(15.dp)) }
    }
}

@Composable
fun SeriesCard(feed: SportsFeed, s: Series, selected: Boolean = false, onClick: () -> Unit) {
    val accent = if (s.competition == "KBO") KboBlue else LolGreen
    Surface(modifier = Modifier.fillMaxWidth().then(if (selected) Modifier.border(1.5.dp, accent, RoundedCornerShape(15.dp)) else Modifier)
        .clickable(onClick = onClick), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .62f), shape = RoundedCornerShape(15.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(s.stage, modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                StatusBadge(s.status)
            }
            BracketTeamLine(feed.team(s.left), s.leftWins, s.status, s.leftWins > s.rightWins && s.status == "FINAL")
            BracketTeamLine(feed.team(s.right), s.rightWins, s.status, s.rightWins > s.leftWins && s.status == "FINAL")
            Text(if (s.scheduled.length >= 16) "${s.scheduled.take(10)}  ${s.scheduled.substring(11, 16)}" else "일정 미정", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun BracketTeamLine(t: Team, won: Int, state: String, winner: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        TeamLogo(t, size = 29.dp)
        Text(t.short, modifier = Modifier.weight(1f), fontWeight = if (winner) FontWeight.ExtraBold else FontWeight.Medium,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(if (state == "UPCOMING") "–" else won.toString(), fontWeight = FontWeight.ExtraBold,
            color = if (winner) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun SeriesDetail(feed: SportsFeed, s: Series) {
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .4f), RoundedCornerShape(16.dp))
        .padding(12.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
        Text("${s.stage}  ·  ${s.leftWins} : ${s.rightWins}", fontWeight = FontWeight.Bold)
        Row(verticalAlignment = Alignment.CenterVertically) {
            TeamLogo(feed.team(s.left), size = 38.dp)
            Text(feed.team(s.left).short, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            Text("VS", fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
            Text(feed.team(s.right).short, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            TeamLogo(feed.team(s.right), size = 38.dp)
        }
        val games = feed.games.filter { it.seriesId == s.id }.sortedBy { it.dateTime }
        if (games.isEmpty()) {
            Text("개별 경기 결과가 아직 등록되지 않았습니다", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            games.forEach { game ->
                HorizontalDivider()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${game.day().format(dateFmt)}", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.weight(1f))
                    Text("${feed.team(game.home).short}  ${game.scoreText()}  ${feed.team(game.away).short}",
                        style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(8.dp))
                    StatusBadge(game.status)
                }
            }
        }
        if (s.nextId.isNotBlank()) Text("다음 단계 → ${feed.series.firstOrNull { it.id == s.nextId }?.stage ?: "미정"}",
            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
fun SettingsScreen(
    theme: String, onTheme: (String) -> Unit,
    notifications: Boolean, reminderMinutes: Int, onReminder: (Boolean, Int) -> Unit,
    existingUrl: String, onSaveUrl: (String) -> Unit, onReset: () -> Unit
) {
    var url by remember(existingUrl) { mutableStateOf(existingUrl) }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("설정", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
        item { SectionTitle("화면 테마", "종목별 포인트 색상 유지") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("SYSTEM" to "기기 설정", "LIGHT" to "화이트", "DARK" to "다크").forEach { (value, label) ->
                    FilterChip(selected = value == theme, onClick = { onTheme(value) }, label = { Text(label) })
                }
            }
        }
        item { SectionTitle("경기 시작 알림", "기본: 미사용") }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("삼성 / Gen.G 경기 알림", modifier = Modifier.weight(1f))
                Switch(checked = notifications, onCheckedChange = { onReminder(it, reminderMinutes) })
            }
            Text("WorkManager가 알림을 예약합니다. OS 절전 정책에 따라 시간이 지연될 수 있습니다.",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (notifications) item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = reminderMinutes == 30, onClick = { onReminder(true, 30) }, label = { Text("30분 전") })
                FilterChip(selected = reminderMinutes == 0, onClick = { onReminder(true, 0) }, label = { Text("경기 시작 시") })
            }
        }
        item { SectionTitle("데이터 동기화", "HTTPS JSON") }
        item {
            OutlinedTextField(value = url, onValueChange = { url = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                label = { Text("데이터 피드 URL") }, placeholder = { Text("https://raw.githubusercontent.com/...") })
            Text("Cloudflare Worker v4 연결 시 앱 실행 중 약 60초마다 스코어 조회 · 백그라운드는 최소 15분 단위로 운영체제 정책에 따라 지연될 수 있습니다.",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item { Button(onClick = { onSaveUrl(url) }, modifier = Modifier.fillMaxWidth()) { Text("피드 저장 및 새로고침") } }
        item { OutlinedButton(onClick = onReset, modifier = Modifier.fillMaxWidth()) { Text("데모 데이터로 초기화") } }
        item { Text("폰에서 받은 피드는 Wear Data Layer로 워치에 전달됩니다. 타일은 워치의 마지막 캐시를 표시합니다.",
            style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun EmptyCard(title: String, sub: String) {
    Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .5f)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

fun competitionLabel(competition: String): String = when (competition) {
    "KBO" -> "KBO"
    "LCK" -> "LCK"
    "MSI" -> "MSI"
    "EWC" -> "EWC"
    "WORLDS" -> "롤드컵"
    "FST" -> "FST"
    else -> competition
}

@Preview(showBackground = true, name = "Bracket match card")
@Composable
private fun BracketPreview() {
    val s = Series("demo", "KBO", "플레이오프", "MAIN", 2, "SAM", "LG", 2, 1,
        "LIVE", "2026-10-30T18:30:00+09:00")
    val feed = SportsFeed("2026-10-30", true, "2026-10-30",
        teams = listOf(Team("SAM", "KBO", "삼성 라이온즈", "삼성", "SL"), Team("LG", "KBO", "LG 트윈스", "LG", "LG")),
        games = emptyList(), standings = emptyList(), series = listOf(s))
    MaterialTheme { Box(Modifier.padding(16.dp)) { SeriesCard(feed, s) { } } }
}
