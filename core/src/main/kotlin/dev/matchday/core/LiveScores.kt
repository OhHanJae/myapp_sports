package dev.matchday.core

import java.time.Instant
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit

/** Compact /live.json payload. Only documented, confirmed score fields are applied. */
data class LiveScores(
    val updatedAt: String,
    val games: List<Game>,
    val teams: List<Team>
)

object LiveCodec {
    fun decode(json: String): LiveScores {
        val root = MiniJson(json).value() as? Map<*, *> ?: error("실시간 피드 형식 오류")
        require(root.number("schemaVersion") == 1) { "실시간 피드 버전 오류" }
        val updated = root.string("updatedAt")
        OffsetDateTime.parse(updated)
        fun rows(name: String): List<Map<*, *>> = (root[name] as? List<*>)?.map {
            it as? Map<*, *> ?: error("$name 목록 오류")
        } ?: emptyList()
        val games = rows("games").map { g ->
            val game = Game(
                id = g.string("id"), competition = g.string("competition"),
                season = g.string("season"), dateTime = g.string("dateTime"),
                home = g.string("home"), away = g.string("away"),
                status = g.string("status"), homeScore = g.optionalInt("homeScore"),
                awayScore = g.optionalInt("awayScore"), stage = g.string("stage"),
                venue = g.string("venue"), seriesId = g.string("seriesId"),
                inning = g.string("inning"), checkedAt = g.string("checkedAt")
            )
            require(game.id.isNotBlank() && game.home.isNotBlank() && game.away.isNotBlank()) { "경기 식별자 누락" }
            require(game.status in setOf("UPCOMING", "LIVE", "FINAL", "POSTPONED", "UNKNOWN")) { "경기 상태 오류" }
            OffsetDateTime.parse(game.dateTime)
            game
        }
        require(games.map { it.id }.distinct().size == games.size) { "중복 실시간 경기 ID" }
        val teams = rows("teams").map {
            Team(it.string("id"), it.string("sport"), it.string("name"),
                it.string("short"), it.string("mark"), it.string("logoUrl"))
        }.filter { it.id.isNotBlank() }
        return LiveScores(updated, games, teams)
    }
    private fun Map<*, *>.string(name: String): String = this[name] as? String ?: ""
    private fun Map<*, *>.number(name: String): Int = (this[name] as? Number)?.toInt() ?: 0
    private fun Map<*, *>.optionalInt(name: String): Int? = (this[name] as? Number)?.toInt()
}

/** Live scores are only valid after the base feed version they supersede. */
fun SportsFeed.mergeLive(live: LiveScores): SportsFeed {
    if (isDemo) return this
    val observed = runCatching { OffsetDateTime.parse(live.updatedAt).toInstant() }.getOrNull() ?: return this
    val baseTime = runCatching { OffsetDateTime.parse(updatedAt).toInstant() }.getOrNull()
    if (baseTime != null && observed.isBefore(baseTime.minusSeconds(10))) return this
    val names = (teams + live.teams).associateBy { it.id }
    val originals = games
    val merged = originals.map { original ->
        val candidate = live.games.firstOrNull { incoming ->
            incoming.id == original.id || (
                incoming.competition == original.competition &&
                incoming.home == original.home && incoming.away == original.away &&
                incoming.dateTime.take(10) == original.dateTime.take(10) &&
                runCatching {
                    val diff = kotlin.math.abs(
                        java.time.Duration.between(
                            OffsetDateTime.parse(incoming.dateTime),
                            OffsetDateTime.parse(original.dateTime)
                        ).toMinutes()
                    )
                    // Official KBO scoreboard IDs/timestamps may differ from
                    // the schedule entry (e.g. the schedule still says TBD).
                    // The scoreboard's observation time is not kickoff time.
                    // Match the same teams on the same day ONLY if both feeds
                    // have exactly one such fixture; never merge doubleheaders.
                    diff <= 60 || (
                        original.competition == "KBO" &&
                        originals.count { g -> g.competition == "KBO" &&
                            g.home == original.home && g.away == original.away &&
                            g.dateTime.take(10) == original.dateTime.take(10) } == 1 &&
                        live.games.count { g -> g.competition == "KBO" &&
                            g.home == original.home && g.away == original.away &&
                            g.dateTime.take(10) == original.dateTime.take(10) } == 1
                    )
                }.getOrDefault(false))
        } ?: return@map original
        val nextStatus = when {
            original.status == "FINAL" && candidate.status != "FINAL" -> "FINAL"
            original.status == "LIVE" && candidate.status in setOf("UNKNOWN", "UPCOMING") -> "LIVE"
            else -> candidate.status
        }
        original.copy(
            status = nextStatus,
            homeScore = candidate.homeScore ?: original.homeScore,
            awayScore = candidate.awayScore ?: original.awayScore,
            inning = candidate.inning.ifBlank { original.inning },
            checkedAt = candidate.checkedAt.ifBlank { live.updatedAt }
        )
    }.toMutableList()
    // If a late-added fixture wasn't present in the last full schedule, include
    // it only when its teams can be resolved and its date/status are valid.
    for (candidate in live.games) {
        if (merged.none { it.id == candidate.id || (it.competition == candidate.competition &&
                it.home == candidate.home && it.away == candidate.away &&
                it.dateTime.take(10) == candidate.dateTime.take(10)) } &&
            (candidate.home == "TBD" || names.containsKey(candidate.home)) &&
            (candidate.away == "TBD" || names.containsKey(candidate.away))) merged.add(candidate)
    }
    val updatedSeries = series.map { seriesItem ->
        val played = merged.filter { it.seriesId == seriesItem.id }
        if (played.isEmpty()) return@map seriesItem
        val finals = played.filter { it.status == "FINAL" && it.homeScore != null &&
            it.awayScore != null && it.homeScore != it.awayScore }
        val left = finals.count {
            (it.home == seriesItem.left && it.homeScore!! > it.awayScore!!) ||
                (it.away == seriesItem.left && it.awayScore!! > it.homeScore!!)
        }
        val right = finals.count {
            (it.home == seriesItem.right && it.homeScore!! > it.awayScore!!) ||
                (it.away == seriesItem.right && it.awayScore!! > it.homeScore!!)
        }
        seriesItem.copy(
            leftWins = maxOf(seriesItem.leftWins, left),
            rightWins = maxOf(seriesItem.rightWins, right),
            status = when {
                seriesItem.status == "FINAL" -> "FINAL"
                played.any { it.status == "LIVE" } -> "LIVE"
                seriesItem.status == "UNKNOWN" && played.any { it.status == "UNKNOWN" } -> "UNKNOWN"
                else -> seriesItem.status
            }
        )
    }
    return copy(updatedAt = live.updatedAt, games = merged,
        teams = names.values.toList(), series = updatedSeries).validate()
}
