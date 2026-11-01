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
    // Real-score patch should merge into existing feed, and Wear JSON must roundtrip.
    val source = feed.copy(isDemo = false, updatedAt = "2026-11-01T14:00:00+09:00",
        games = listOf(future.copy(id = "KBO_TEST", competition = "KBO",
            dateTime = "2026-11-01T14:00:00+09:00",
            home = "SAM", away = "KIA", status = "UNKNOWN")))
    val liveJson = """{"schemaVersion":1,"updatedAt":"2026-11-01T16:15:00+09:00",
        "games":[{"id":"NEW_PROVIDER_ID","competition":"KBO","season":"2026",
        "dateTime":"2026-11-01T14:00:00+09:00","home":"SAM","away":"KIA",
        "status":"LIVE","homeScore":4,"awayScore":3,"inning":"3회",
        "checkedAt":"2026-11-01T16:15:00+09:00"}],"teams":[]}"""
    val merged = source.mergeLive(LiveCodec.decode(liveJson))
    check(merged.games.size == 1) // Match by team/day even when the upstream ID differs.
    check(merged.games.single().status == "LIVE")
    check(merged.games.single().homeScore == 4 && merged.games.single().awayScore == 3)
    check(merged.games.single().inning == "3회")
    val serialized = FeedCodec.encode(merged)
    check(FeedCodec.decode(serialized) == merged)
    check(FeedCodec.decode(FeedCodec.encode(feed)) == feed)
    val tooOld = source.mergeLive(LiveCodec.decode(liveJson.replace("2026-11-01T16:15:00+09:00",
        "2026-11-01T13:00:00+09:00")))
    check(tooOld == source)
    check(runCatching { LiveCodec.decode("""{"schemaVersion":1,"games":[]}""") }.isFailure)
    check(runCatching { FeedCodec.decode("{}") }.isFailure)
    println("Core smoke test PASS: ${feed.teams.size} teams, ${feed.games.size} games, ${feed.series.size} bracket nodes")
}
