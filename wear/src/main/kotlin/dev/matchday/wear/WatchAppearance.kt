package dev.matchday.wear

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.wear.tiles.TileService

/** Watch-only preferences: phone Data Layer theme sync must not overwrite them. */
object WatchAppearance {
    const val PREF_THEME = "watch_theme"
    const val PREF_ACCENT = "watch_accent"

    val themes = listOf("SYSTEM" to "시스템", "DARK" to "다크", "LIGHT" to "라이트")
    val accents = listOf(
        "AUTO" to "자동(종목별)", "BLUE" to "블루", "GREEN" to "그린",
        "PURPLE" to "퍼플", "ORANGE" to "오렌지", "PINK" to "핑크"
    )

    fun theme(context: Context): String {
        val prefs = context.getSharedPreferences(WEAR_PREFS, Context.MODE_PRIVATE)
        // Existing phone setting is used only until the watch owner chooses a value.
        return prefs.getString(PREF_THEME, null)
            ?: prefs.getString("theme", "SYSTEM") ?: "SYSTEM"
    }

    fun accent(context: Context): String =
        context.getSharedPreferences(WEAR_PREFS, Context.MODE_PRIVATE)
            .getString(PREF_ACCENT, "AUTO") ?: "AUTO"

    fun colorCode(preference: String, sport: String): Long = when (preference) {
        "BLUE" -> 0xFF438EFF
        "GREEN" -> 0xFF21CE80
        "PURPLE" -> 0xFFB594FF
        "ORANGE" -> 0xFFFFA552
        "PINK" -> 0xFFFF77A9
        else -> if (sport == "KBO") 0xFF438EFF else 0xFF21CE80
    }

    fun accentColor(preference: String, sport: String): Color =
        Color(colorCode(preference, sport))

    fun save(context: Context, key: String, value: String) {
        require((key == PREF_THEME && themes.any { it.first == value }) ||
                (key == PREF_ACCENT && accents.any { it.first == value }))
        context.getSharedPreferences(WEAR_PREFS, Context.MODE_PRIVATE)
            .edit().putString(key, value).apply()
        // Reflect preference changes on existing tiles without reinstalling tiles.
        TileService.getUpdater(context).requestUpdate(KboTileService::class.java)
        TileService.getUpdater(context).requestUpdate(LolTileService::class.java)
    }
}
