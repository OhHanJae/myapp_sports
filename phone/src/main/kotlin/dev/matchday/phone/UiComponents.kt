package dev.matchday.phone

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import dev.matchday.core.Game
import dev.matchday.core.SportsFeed
import dev.matchday.core.Team
import dev.matchday.core.displayLogoUrl
import dev.matchday.core.localBadgeName
import java.time.LocalDate

val KboBlue = Color(0xFF317EFF)
val LolGreen = Color(0xFF0BBE78)
val DarkBackground = Color(0xFF08141F)
val DarkSurface = Color(0xFF102433)
val DarkElevated = Color(0xFF1A3345)

fun statusText(status: String) = when (status) {
    "LIVE" -> "LIVE"
    "FINAL" -> "종료"
    "POSTPONED" -> "연기"
    "UNKNOWN" -> "상태 미확인"
    else -> "예정"
}

@Composable
fun TeamLogo(team: Team, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    val context = LocalContext.current
    val localName = team.localBadgeName()
    val localRes = remember(localName) {
        context.resources.getIdentifier(localName, "drawable", context.packageName)
    }
    val logo = team.displayLogoUrl()
    val fallback: @Composable () -> Unit = {
        if (localRes != 0) {
            Image(painter = painterResource(localRes), contentDescription = "${team.name} 팀 이미지",
                contentScale = ContentScale.Fit, modifier = modifier.size(size))
        } else {
            Box(modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.24f)),
                contentAlignment = Alignment.Center) { Text(team.mark.take(3), style = MaterialTheme.typography.labelSmall) }
        }
    }
    if (logo.startsWith("https://")) {
        // Feed providers may supply official/team-licensed logos. A bundled placeholder always exists.
        androidx.compose.runtime.key(team.id, logo) {
            coil.compose.SubcomposeAsyncImage(model = logo, contentDescription = team.name,
                loading = { fallback() }, error = { fallback() },
                modifier = modifier.size(size), contentScale = ContentScale.Fit)
        }
    } else fallback()
}

@Composable
fun StatusBadge(status: String) {
    val tint = when (status) {
        "LIVE" -> Color(0xFFF15563)
        "FINAL" -> MaterialTheme.colorScheme.onSurfaceVariant
        "POSTPONED" -> Color(0xFFF3A43B)
        "UNKNOWN" -> Color(0xFFE4AD55)
        else -> MaterialTheme.colorScheme.primary
    }
    Box(Modifier.background(tint.copy(alpha = 0.16f), RoundedCornerShape(8.dp)).padding(horizontal = 9.dp, vertical = 5.dp)) {
        Text(statusText(status), color = tint, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun SectionTitle(title: String, trailing: String = "", onTrailing: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        if (trailing.isNotEmpty()) Text(trailing, modifier = Modifier.clickable(enabled = onTrailing != null) { onTrailing?.invoke() },
            color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
fun MatchRowTeams(feed: SportsFeed, game: Game, compact: Boolean = true) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        TeamLogo(feed.team(game.home), size = if (compact) 29.dp else 43.dp)
        Text(feed.team(game.home).short, maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = if (compact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(game.scoreText(), fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleMedium)
            if (!compact) StatusBadge(game.status)
        }
        Text(feed.team(game.away).short, maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = if (compact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.End)
        TeamLogo(feed.team(game.away), size = if (compact) 29.dp else 43.dp)
    }
}

@Composable
fun GameItem(feed: SportsFeed, game: Game, onClick: () -> Unit) {
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(game.stage.ifBlank { game.competition }, style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                StatusBadge(game.status)
            }
            MatchRowTeams(feed, game)
            if (game.status == "LIVE" && (game.inning.isNotBlank() || game.checkedAt.isNotBlank()))
                Text(listOf(game.inning.takeIf { it.isNotBlank() },
                    game.checkedAt.takeIf { it.length >= 16 }?.substring(11, 16)?.let { "확인 $it" })
                    .filterNotNull().joinToString(" · "),
                    color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
            Text("${game.day()} · ${game.venue.ifBlank { "경기장 미정" }}", color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
fun FeaturedMatch(feed: SportsFeed, game: Game?, comp: String) {
    val accent = if (comp == "KBO") KboBlue else LolGreen
    Surface(shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.background(Brush.linearGradient(listOf(accent.copy(alpha = 0.65f), DarkSurface, DarkBackground)))
            .padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (comp == "KBO") "★ MY TEAM   삼성 라이온즈" else "★ MY TEAM   Gen.G",
                color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (game == null) {
                Text("등록된 경기가 없습니다", color = Color.White, style = MaterialTheme.typography.titleLarge)
            } else {
                Text("${game.stage}  ·  ${game.day()}", color = Color(0xFFE5EDF7), style = MaterialTheme.typography.labelMedium)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                        TeamLogo(feed.team(game.home), size = 62.dp)
                        Text(feed.team(game.home).short, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1.1f)) {
                        Text(game.scoreText(), color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
                        Text(statusText(game.status), color = if (game.status == "LIVE") Color(0xFFFF7777) else Color.White)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                        TeamLogo(feed.team(game.away), size = 62.dp)
                        Text(feed.team(game.away).short, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
                Text(game.venue.ifBlank { "경기장 미정" }, color = Color(0xFFCCD8E8), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
