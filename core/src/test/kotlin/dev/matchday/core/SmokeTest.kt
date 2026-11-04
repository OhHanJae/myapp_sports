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
    // Regression: production-shaped 2026-11-04 KT vs Samsung response had
    // a synthetic match ID, a 20:21 observation time (111m after kickoff),
    // and teams:[] because the full feed owns the team catalog.
    val observed = """{"schemaVersion":1,"updatedAt":"2026-11-04T20:21:45.221+09:00",
        "games":[{"id":"NPS_20261104_SAM_KT","competition":"KBO","season":"2026",
        "dateTime":"2026-11-04T18:30:00+09:00","home":"KT","away":"SAM",
        "status":"LIVE","homeScore":1,"awayScore":1,"stage":"한국시리즈",
        "seriesId":"KBOPS_2026_3_KT_SAM","gameId":"20261104SSKT0",
        "inning":"6회말","checkedAt":"2026-11-04T20:21:45.221+09:00"}],
        "teams":[],"diagnostics":{"counts":{"KBO":1,"LOL":0},"warnings":[]}}"""
    val reference = feed.copy(isDemo = false, updatedAt = "2026-11-04T18:30:00+09:00",
        games = listOf(base.copy(id = "NPS_20261104SSKT0",
            competition = "KBO", dateTime = "2026-11-04T18:30:00+09:00",
            home = "KT", away = "SAM", status = "UNKNOWN",
            homeScore = null, awayScore = null, stage = "한국시리즈")))
    val reported = reference.mergeLive(LiveCodec.decode(observed))
    check(reported.games.size == 1)
    check(reported.games.single().id == "NPS_20261104SSKT0")
    check(reported.games.single().status == "LIVE")
    check(reported.games.single().homeScore == 1 && reported.games.single().awayScore == 1)
    check(reported.games.single().inning == "6회말")
    check(reported.teams.size == reference.teams.size)
    // The HTML-only direct scoreboard fallback may report observation time
    // rather than kickoff. The same unique teams + date still must merge.
    val observationTime = observed.replace(
        "\"dateTime\":\"2026-11-04T18:30:00+09:00\"",
        "\"dateTime\":\"2026-11-04T20:21:45.221+09:00\"")
    val mergedObservation = reference.mergeLive(LiveCodec.decode(observationTime))
    check(mergedObservation.games.size == 1)
    check(mergedObservation.games.single().homeScore == 1)
    // A doubleheader cannot be joined by team/day alone.
    val doubleheader = reference.copy(games = listOf(
        reference.games.single(),
        reference.games.single().copy(id = "DH_2",
            dateTime = "2026-11-04T15:00:00+09:00")))
    check(doubleheader.mergeLive(LiveCodec.decode(observationTime))
        .games.single { it.id == "NPS_20261104SSKT0" }.homeScore == null)
    check(FeedCodec.decode(serialized) == merged)
    check(FeedCodec.decode(FeedCodec.encode(feed)) == feed)
    val tooOld = source.mergeLive(LiveCodec.decode(liveJson.replace("2026-11-01T16:15:00+09:00",
        "2026-11-01T13:00:00+09:00")))
    check(tooOld == source)
    check(runCatching { LiveCodec.decode("""{"schemaVersion":1,"games":[]}""") }.isFailure)
    // Official logos are sourced from 2026 KBO CDN, never fictional debug badges.
    val samsung = feed.team("SAM")
    check(samsung.displayLogoUrl().endsWith("/emblem_SS.png"))
    check(feed.team("KIA").displayLogoUrl().endsWith("/emblem_HT.png"))
    check(feed.teams.filter { it.sport == "KBO" }.all { it.displayLogoUrl().startsWith("https://") })
    // Tile outlines are sport-specific even when a watch accent preset changes.
    check(sportTileBorderColor("KBO") == 0xFF438EFFL)
    check(sportTileBorderColor("LOL") == 0xFF21CE80L)
    check(sportTileBorderColor("KBO") != sportTileBorderColor("LOL"))
    check(feed.team("SAM").localBadgeName() == "team_sam")
    check(feed.team("GEN").localBadgeName() == "team_gen")
    check(Team("PS_12", "LOL", "T1", "T1", "T1", "").localBadgeName() == "team_t1")
    check(Team("PS_13", "LOL", "KT Rolster", "KT", "KT", "").localBadgeName() == "team_ktlol")
    // The watch's combined LoL feed prefers currently LIVE games over an
    // already completed Gen.G game from a different competition.
    val liveLol = base.copy(id = "lol-live", competition = "WORLDS",
        dateTime = "2026-10-30T19:00:00+09:00", home = "T1", away = "BLG", status = "LIVE")
    val completedGen = base.copy(id = "lol-done", competition = "LCK",
        dateTime = "2026-10-30T17:00:00+09:00", home = "GEN", away = "HLE", status = "FINAL")
    val watchLive = feed.copy(games = listOf(completedGen, liveLol))
    check(watchLive.watchLolFeatured(today)?.id == "lol-live")
    check(watchLive.watchLolCompetition(today) == "WORLDS")
    val ongoingGen = liveLol.copy(id = "gen-live", home = "GEN", away = "T1",
        dateTime = "2026-10-30T20:00:00+09:00")
    check(watchLive.copy(games = listOf(liveLol, ongoingGen)).watchLolFeatured(today)?.id == "gen-live")
    check(feed.copy(games = emptyList()).watchLolFeatured(today) == null)
    // The same feed access key (including encoded special characters) must be
    // retained byte-for-byte when changing only /feed.json to /live.json.
    val fullUrl = "https://matchday.example.workers.dev/feed.json?key=A%2BB%26C%3DD&scope=test"
    check(FeedEndpointUrls.liveFromFeed(fullUrl) ==
        "https://matchday.example.workers.dev/live.json?key=A%2BB%26C%3DD&scope=test")
    check(FeedEndpointUrls.liveFromFeed("https://host.workers.dev/feed.json?key=plain") ==
        "https://host.workers.dev/live.json?key=plain")
    check(FeedEndpointUrls.liveFromFeed("https://host.workers.dev/feed.json") == null)
    check(FeedEndpointUrls.liveFromFeed("https://host.workers.dev/live.json?key=x") == null)
    check(FeedEndpointUrls.liveFromFeed("https://host.workers.dev/feed.json?key=") == null)
    check(FeedEndpointUrls.liveFromFeed("https://host.workers.dev/feed.json?key=x&key=y") == null)
    check(FeedEndpointUrls.liveFromFeed("http://host.workers.dev/feed.json?key=x") == null)
    check(FeedEndpointUrls.liveFromFeed("https://host.workers.dev/feed.json?key=x#abc") == null)
    check(!FeedEndpointUrls.AUTH_HELP.contains("A%2BB%26C%3DD"))
    check(runCatching { FeedCodec.decode("{}") }.isFailure)
    println("Core smoke test PASS: ${feed.teams.size} teams, ${feed.games.size} games, ${feed.series.size} bracket nodes")
}
