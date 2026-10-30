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
    check(runCatching { FeedCodec.decode("{}") }.isFailure)
    println("Core smoke test PASS: ${feed.teams.size} teams, ${feed.games.size} games, ${feed.series.size} bracket nodes")
}
