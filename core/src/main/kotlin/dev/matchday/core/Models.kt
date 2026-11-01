package dev.matchday.core

import java.time.LocalDate
import java.time.OffsetDateTime

/** All dateTime values are ISO-8601 strings with an explicit time zone offset. */
data class Team(
    val id: String, val sport: String, val name: String,
    val short: String, val mark: String, val logoUrl: String = ""
)

data class Game(
    val id: String, val competition: String, val season: String,
    val dateTime: String, val home: String, val away: String,
    val status: String, val homeScore: Int? = null, val awayScore: Int? = null,
    val stage: String = "", val venue: String = "", val seriesId: String = "",
    val inning: String = "", val checkedAt: String = ""
) {
    fun day(): LocalDate = OffsetDateTime.parse(dateTime).toLocalDate()
    fun timeText(): String = OffsetDateTime.parse(dateTime).toLocalTime().toString().take(5)
    fun scoreText(): String = if (homeScore != null && awayScore != null) "$homeScore : $awayScore" else timeText()
}

data class Standing(
    val competition: String, val season: String, val rank: Int, val team: String,
    val played: Int, val won: Int, val lost: Int, val drawn: Int = 0,
    val forScore: Int = 0, val againstScore: Int = 0, val streak: String = ""
) {
    fun summary(sport: String): String = if (sport == "KBO") {
        "${won}승 ${drawn}무 ${lost}패"
    } else "${won}승 ${lost}패 · 세트 ${forScore}-${againstScore}"
    fun ratio(): String = if (won + lost == 0) ".000" else "%.3f".format(java.util.Locale.US, won.toDouble() / (won + lost))
}

data class Series(
    val id: String, val competition: String, val stage: String, val group: String,
    val roundIndex: Int, val left: String, val right: String,
    val leftWins: Int, val rightWins: Int, val status: String,
    val scheduled: String, val bestOf: Int = 5,
    val nextId: String = ""
)

data class SportsFeed(
    val updatedAt: String,
    val isDemo: Boolean,
    val demoDay: String,
    val teams: List<Team>,
    val games: List<Game>,
    val standings: List<Standing>,
    val series: List<Series>
) {
    fun team(id: String): Team = teams.firstOrNull { it.id == id }
        ?: Team(id, "UNKNOWN", id.ifBlank { "미정" }, id.ifBlank { "TBD" }, "?")

    fun matches(competition: String): List<Game> = games.filter { it.competition == competition }.sortedBy { it.dateTime }
    fun rankings(competition: String): List<Standing> = standings.filter { it.competition == competition }.sortedBy { it.rank }
    fun rounds(competition: String, group: String? = null): List<Series> = series
        .filter { it.competition == competition && (group == null || it.group == group) }
        .sortedWith(compareBy<Series> { it.roundIndex }.thenBy { it.id })
    fun referenceDay(): LocalDate = if (isDemo) LocalDate.parse(demoDay) else LocalDate.now(java.time.ZoneId.of("Asia/Seoul"))
    fun favorite(competition: String): String = if (competition == "KBO") "SAM" else "GEN"
    fun nearestFavorite(competition: String, day: LocalDate = referenceDay()): Game? {
        val games = matches(competition).filter { it.home == favorite(competition) || it.away == favorite(competition) }
        return games.firstOrNull { it.day() == day && it.status != "FINAL" }
            ?: games.firstOrNull { it.day() >= day && it.status == "UPCOMING" }
            ?: games.lastOrNull()
    }
    /** Strict enough to reject an accidentally wrong API response before caching it. */
    fun validate(): SportsFeed {
        require(teams.isNotEmpty()) { "팀 목록이 비어 있습니다" }
        require(teams.map { it.id }.distinct().size == teams.size) { "중복 팀 ID" }
        require(games.map { it.id }.distinct().size == games.size) { "중복 경기 ID" }
        val ids = teams.map { it.id }.toSet() + "TBD"
        require(games.all { it.home in ids && it.away in ids && it.status in setOf("UPCOMING", "LIVE", "FINAL", "POSTPONED", "UNKNOWN") }) {
            "경기 팀 ID 또는 상태가 올바르지 않습니다"
        }
        require(series.all { it.left in ids && it.right in ids }) { "대진표 팀 ID 오류" }
        games.forEach { OffsetDateTime.parse(it.dateTime) }
        return this
    }
}
