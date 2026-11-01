package dev.matchday.core

import java.io.File
fun main(args: Array<String>) {
    val file = File(args.firstOrNull() ?: "feeds/example.json")
    val feed = FeedCodec.decode(file.readText(Charsets.UTF_8))
    check(feed.games.any { it.competition == "KBO" })
    check(feed.games.any { it.competition == "WORLDS" })
    check(feed.favorite("KBO") == "SAM")
    check(feed.favorite("LCK") == "GEN")
    check(feed.rounds("LCK", "UPPER").isNotEmpty())
    check(feed.referenceDay().toString() == "2026-10-30")
    // Tile priority: favorite today -> other team today -> earliest future match.
    val today = java.time.LocalDate.parse("2026-10-30")
    val base = feed.games.first()
    val favorite = base.copy(id = "t-fav", competition = "KBO", dateTime = "2026-10-30T18:30:00+09:00", home = "SAM", away = "KIA", status = "FINAL")
    val other = base.copy(id = "t-other", competition = "KBO", dateTime = "2026-10-30T19:00:00+09:00", home = "LG", away = "KT", status = "LIVE")
    val future = base.copy(id = "t-future", competition = "KBO", dateTime = "2026-10-31T15:00:00+09:00", home = "KIA", away = "SSG", status = "UPCOMING")
    val otherChoice = feed.copy(games = listOf(other, future)).selectTileMatch(listOf("KBO"), "SAM", today)
    check(otherChoice?.game?.id == "t-other" && otherChoice.kind == TileMatchKind.OTHER_TODAY)
    val favoriteChoice = feed.copy(games = listOf(other, favorite, future)).selectTileMatch(listOf("KBO"), "SAM", today)
    check(favoriteChoice?.game?.id == "t-fav" && favoriteChoice.kind == TileMatchKind.FAVORITE_TODAY)
    val futureChoice = feed.copy(games = listOf(future)).selectTileMatch(listOf("KBO"), "SAM", today)
    check(futureChoice?.game?.id == "t-future" && futureChoice.kind == TileMatchKind.NEXT_GAME)
    check(feed.copy(games = emptyList()).selectTileMatch(listOf("KBO"), "SAM", today) == null)
    check(FeedTransfer.decompress(FeedTransfer.compress(file.readText(Charsets.UTF_8))) == file.readText(Charsets.UTF_8))
    check(runCatching { FeedCodec.decode("{}") }.isFailure)
    println("Core smoke test PASS: ${feed.teams.size} teams, ${feed.games.size} games, ${feed.series.size} bracket nodes")
}
