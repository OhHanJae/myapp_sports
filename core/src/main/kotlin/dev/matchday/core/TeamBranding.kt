package dev.matchday.core

/**
 * Official 2026 KBO team emblems are served by the same image CDN referenced
 * by the KBO scoreboard. No club trademark PNGs or API keys are committed.
 *
 * If the CDN is unavailable, the bundled original badge remains the fallback.
 */
private const val KBO_EMBLEM_BASE =
    "https://6ptotvmi5753.edge.naverncp.com/KBO_IMAGE/emblem/regular/2026/emblem_"

private val kboEmblemCodes = mapOf(
    "KIA" to "HT", "KT" to "KT", "LG" to "LG", "NC" to "NC",
    "SSG" to "SK", "DOO" to "OB", "LOT" to "LT", "SAM" to "SS",
    "KIW" to "WO", "HAN" to "HH"
)

/** Returns a trusted HTTPS emblem URL, or an empty string for a team without one. */
fun Team.displayLogoUrl(): String {
    if (sport == "KBO") {
        val code = kboEmblemCodes[id] ?: return ""
        return "${KBO_EMBLEM_BASE}${code}.png"
    }
    return logoUrl.takeIf { it.startsWith("https://", ignoreCase = true) } ?: ""
}

/** Maps PandaScore numeric team IDs to an existing local fallback badge by acronym. */
fun Team.localBadgeName(): String {
    if (sport == "KBO") return "team_${id.lowercase()}"
    val code = short.trim().uppercase().replace(Regex("[^A-Z0-9]"), "")
    val recognized = when (code) {
        "KT" -> "ktlol"
        "GEN", "GENG" -> "gen"
        "HLE", "T1", "DK", "BFX", "DNS", "NS", "BRO", "DRX",
        "G2", "BLG", "FNC", "C9", "FLY", "TES" -> code.lowercase()
        else -> ""
    }
    return if (recognized.isNotEmpty()) "team_$recognized" else "team_${id.lowercase()}"
}

/**
 * Brand colors for the visible circular border of a Wear tile.
 * KBO is blue and LoL is green regardless of the user's optional accent preset.
 */
fun sportTileBorderColor(sport: String): Long = when (sport.uppercase()) {
    "KBO" -> 0xFF438EFFL
    "LOL" -> 0xFF21CE80L
    else -> 0xFF8A99A8L
}
