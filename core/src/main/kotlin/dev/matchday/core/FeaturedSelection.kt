package dev.matchday.core

import java.time.LocalDate

/** Pure match selection: favorite today > another game today > nearest future game. */
enum class TileMatchKind { FAVORITE_TODAY, OTHER_TODAY, NEXT_GAME }

data class TileMatchChoice(val game: Game, val kind: TileMatchKind)

/** Both KBO and LoL tiles share this logic. A finished game today still outranks tomorrow's game. */
fun SportsFeed.selectTileMatch(
    competitions: Collection<String>,
    favoriteTeam: String,
    today: LocalDate = referenceDay()
): TileMatchChoice? {
    val eligible = games.filter { it.competition in competitions }
    val priority = mapOf("LIVE" to 0, "UPCOMING" to 1, "FINAL" to 2, "POSTPONED" to 3)
    val todays = eligible.filter { it.day() == today }
        .sortedWith(compareBy<Game> { priority[it.status] ?: 4 }.thenBy { it.dateTime })
    val favorite = todays.firstOrNull { it.home == favoriteTeam || it.away == favoriteTeam }
    if (favorite != null) return TileMatchChoice(favorite, TileMatchKind.FAVORITE_TODAY)
    if (todays.isNotEmpty()) return TileMatchChoice(todays.first(), TileMatchKind.OTHER_TODAY)
    val future = eligible.filter { it.day() > today && it.status == "UPCOMING" }
        .minByOrNull { it.dateTime } ?: return null
    return TileMatchChoice(future, TileMatchKind.NEXT_GAME)
}

/** Select a useful initial LoL competition when LCK is in its off-season. */
fun SportsFeed.activeLolCompetition(today: LocalDate = referenceDay()): String {
    val allowed = listOf("LCK", "MSI", "EWC", "WORLDS", "FST")
    val candidates = games.filter { it.competition in allowed }
    if (candidates.isEmpty()) return "LCK"
    val current = candidates.filter { it.day() == today }
    val todayFavorite = current.firstOrNull { it.home == "GEN" || it.away == "GEN" }
    if (todayFavorite != null) return todayFavorite.competition
    if (current.isNotEmpty()) return current.first().competition
    val upcoming = candidates.filter { it.day() > today && it.status == "UPCOMING" }.minByOrNull { it.dateTime }
    if (upcoming != null) return upcoming.competition
    return candidates.maxByOrNull { it.dateTime }?.competition ?: "LCK"
}
