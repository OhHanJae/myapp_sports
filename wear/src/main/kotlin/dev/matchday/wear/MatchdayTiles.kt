package dev.matchday.wear

import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders
import androidx.wear.protolayout.DimensionBuilders
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.SettableFuture
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import dev.matchday.core.Team
import dev.matchday.core.displayLogoUrl
import dev.matchday.core.localBadgeName
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import com.google.common.util.concurrent.ListenableFuture
import dev.matchday.core.Game
import dev.matchday.core.SportsFeed
import dev.matchday.core.TileMatchChoice
import dev.matchday.core.TileMatchKind
import dev.matchday.core.selectTileMatch
import dev.matchday.core.watchLolFeatured
import java.time.LocalDate

/** Two separately selectable tiles; both use the latest phone-synced offline cache. */
class KboTileService : MatchdayTileService("KBO")
class LolTileService : MatchdayTileService("LOL")

abstract class MatchdayTileService(private val sport: String) : TileService() {
    companion object {
        // Resource callbacks are invoked on the main thread. Never download images there.
        private val imageExecutor = Executors.newFixedThreadPool(2)
    }

    private fun chosen(feed: SportsFeed): TileMatchChoice? {
        val comps = if (sport == "KBO") listOf("KBO")
            else listOf("LCK", "MSI", "EWC", "WORLDS", "FST")
        if (sport == "LOL") {
            val game = feed.watchLolFeatured() ?: return null
            val kind = when {
                game.day() > feed.referenceDay() -> TileMatchKind.NEXT_GAME
                game.home == "GEN" || game.away == "GEN" -> TileMatchKind.FAVORITE_TODAY
                else -> TileMatchKind.OTHER_TODAY
            }
            return TileMatchChoice(game, kind)
        }
        return feed.selectTileMatch(comps, "SAM")
    }

    private fun version(feed: SportsFeed): String {
        val game = chosen(feed)?.game
        val home = game?.let { feed.team(it.home) }
        val away = game?.let { feed.team(it.away) }
        val versionKey = listOf(sport, home?.id.orEmpty(), away?.id.orEmpty(),
            home?.displayLogoUrl().orEmpty(), away?.displayLogoUrl().orEmpty()).joinToString(":")
        return "team-logos-v2-${versionKey.hashCode().toUInt().toString(16)}"
    }

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> {
        val feed = WatchFeedStore(this).read()
        val comps = if (sport == "KBO") listOf("KBO") else listOf("LCK", "MSI", "EWC", "WORLDS", "FST")
        val today = feed.referenceDay()
        val daily = feed.games.filter { it.competition in comps && it.day() == today }
        val match = chosen(feed)
        val root = tileLayout(feed, daily.size, match, today)
        val tile = TileBuilders.Tile.Builder()
            .setResourcesVersion(version(feed))
            .setFreshnessIntervalMillis(60 * 1000L)
            .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(root))
            .build()
        return Futures.immediateFuture(tile)
    }

    override fun onTileResourcesRequest(requestParams: RequestBuilders.ResourcesRequest): ListenableFuture<ResourceBuilders.Resources> {
        val feed = WatchFeedStore(this).read()
        val choice = chosen(feed)
        val resourceVersion = version(feed)
        val result = SettableFuture.create<ResourceBuilders.Resources>()
        // Build a two-team image bundle off the UI thread, with short timeouts.
        imageExecutor.execute {
            try {
                val resources = ResourceBuilders.Resources.Builder().setVersion(resourceVersion)
                val selected = choice?.game
                val teams = if (selected == null) emptyList() else
                    listOf(feed.team(selected.home), feed.team(selected.away)).distinctBy { it.id }
                teams.forEach { team ->
                    val imageData = loadRemoteBadge(team.displayLogoUrl())
                    val image = if (imageData != null)
                        ResourceBuilders.ImageResource.Builder()
                            .setInlineResource(ResourceBuilders.InlineImageResource.Builder()
                                .setData(imageData).build()).build()
                    else {
                        val id = this@MatchdayTileService.resources.getIdentifier(team.localBadgeName(), "drawable", packageName)
                            .takeIf { it != 0 } ?: R.drawable.ic_matchday
                        ResourceBuilders.ImageResource.Builder()
                            .setAndroidResourceByResId(ResourceBuilders.AndroidImageResourceByResId.Builder()
                                .setResourceId(id).build()).build()
                    }
                    resources.addIdToImageMapping(team.id, image)
                }
                resources.addIdToImageMapping("TBD", ResourceBuilders.ImageResource.Builder()
                    .setAndroidResourceByResId(ResourceBuilders.AndroidImageResourceByResId.Builder()
                        .setResourceId(R.drawable.ic_matchday).build()).build())
                result.set(resources.build())
            } catch (e: Exception) {
                android.util.Log.w("MatchdayTile", "Logo loading fallback", e)
                val fallback = ResourceBuilders.Resources.Builder().setVersion(resourceVersion)
                val selected = choice?.game
                val ids = if (selected == null) emptyList() else
                    listOf(selected.home, selected.away).distinct()
                for (teamId in ids + "TBD") {
                    val team = feed.team(teamId)
                    val localId = this@MatchdayTileService.resources.getIdentifier(
                        team.localBadgeName(), "drawable", packageName)
                        .takeIf { it != 0 } ?: R.drawable.ic_matchday
                    fallback.addIdToImageMapping(teamId, ResourceBuilders.ImageResource.Builder()
                        .setAndroidResourceByResId(ResourceBuilders.AndroidImageResourceByResId.Builder()
                            .setResourceId(localId).build()).build())
                }
                result.set(fallback.build())
            }
        }
        return result
    }

    /**
     * On-demand logo byte source for ProtoLayout image resources.
     * Official KBO CDN / PandaScore HTTPS only; no third-party URLs, no auth tokens.
     */
    private fun loadRemoteBadge(value: String): ByteArray? {
        if (!value.startsWith("https://", ignoreCase = true)) return null
        val url = runCatching { URL(value) }.getOrNull() ?: return null
        val host = url.host.lowercase()
        if (host != "6ptotvmi5753.edge.naverncp.com" &&
            host != "cdn.pandascore.co" && !host.endsWith(".pandascore.co")) return null
        val conn = runCatching { url.openConnection() as? HttpURLConnection }.getOrNull() ?: return null
        try {
            conn.connectTimeout = 1800
            conn.readTimeout = 1800
            conn.instanceFollowRedirects = false
            if (conn.responseCode !in 200..299) return null
            val bytes = conn.inputStream.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(4096)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    if (output.size() + n > 250_000) return null
                    output.write(buffer, 0, n)
                }
                output.toByteArray()
            }
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            if (options.outWidth <= 0 || options.outHeight <= 0 ||
                options.outWidth > 4096 || options.outHeight > 4096) return null
            val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size,
                BitmapFactory.Options().apply {
                    inSampleSize = maxOf(1, maxOf(options.outWidth, options.outHeight) / 128)
                }) ?: return null
            val scaled = Bitmap.createScaledBitmap(decoded, 80, 80, true)
            val result = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.PNG, 100, result)
            if (scaled !== decoded) scaled.recycle()
            decoded.recycle()
            return result.toByteArray()
        } catch (_: Exception) {
            return null
        } finally {
            conn.disconnect()
        }
    }

    private fun tileLayout(feed: SportsFeed, todaysCount: Int, choice: TileMatchChoice?, today: LocalDate): LayoutElementBuilders.LayoutElement {
        val accent = WatchAppearance.colorCode(WatchAppearance.accent(this), sport).toInt()
        val title = if (sport == "LOL" && choice?.game?.status == "LIVE") "LoL · LIVE" else if (choice?.kind == TileMatchKind.NEXT_GAME) {
            if (sport == "KBO") "KBO · 다음 경기" else "LoL · 다음 경기"
        } else if (sport == "KBO") "KBO · 오늘 ${todaysCount}경기" else "LoL · 오늘 ${todaysCount}경기"
        val clickable = ModifiersBuilders.Clickable.Builder()
            .setId("open_matchday")
            .setOnClick(ActionBuilders.LaunchAction.Builder()
                .setAndroidActivity(ActionBuilders.AndroidActivity.Builder()
                    .setPackageName(packageName)
                    .setClassName(WatchActivity::class.java.name)
                    .addKeyToExtraMapping("sport", ActionBuilders.AndroidStringExtra.Builder().setValue(sport).build())
                    .build())
                .build())
            .build()
        val column = LayoutElementBuilders.Column.Builder()
            .setWidth(DimensionBuilders.expand())
            .setHeight(DimensionBuilders.expand())
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .setModifiers(ModifiersBuilders.Modifiers.Builder().setClickable(clickable).build())
            .addContent(spacer(27f))
            .addContent(text(title, 15f, 0xFFFFFFFF.toInt()))
            .addContent(spacer(13f))

        if (choice == null) {
            column.addContent(text("등록된 경기 일정이 없습니다", 13f, 0xFFFFFFFF.toInt()))
                .addContent(spacer(8f))
                .addContent(text("눌러서 전체 일정 보기", 11f, accent))
        } else {
            val match = choice.game
            val home = feed.team(match.home)
            val away = feed.team(match.away)
            val score = match.scoreText()
            val item = LayoutElementBuilders.Row.Builder()
                .setWidth(DimensionBuilders.wrap())
                .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            item.addContent(image(home.id))
            item.addContent(spacerWidth(7f))
            item.addContent(text(score, 23f, 0xFFFFFFFF.toInt()))
            item.addContent(spacerWidth(7f))
            item.addContent(image(away.id))
            column.addContent(text(when (choice.kind) {
                    TileMatchKind.FAVORITE_TODAY -> "응원팀 오늘 경기"
                    TileMatchKind.OTHER_TODAY -> "오늘의 다른 경기"
                    TileMatchKind.NEXT_GAME -> "가장 가까운 예정 경기"
                }, 11f, accent))
                .addContent(spacer(5f))
                .addContent(item.build())
                .addContent(spacer(5f))
                .addContent(text("${home.short}  VS  ${away.short}", 13f, 0xFFFFFFFF.toInt()))
                .addContent(spacer(6f))
                .addContent(text(if (match.status == "LIVE" && match.inning.isNotBlank())
                    "● LIVE · ${match.inning}" else when(match.status){"LIVE"->"● LIVE";"FINAL"->"경기 종료";"UNKNOWN"->"상태 미확인";else->"경기 예정"}, 13f,
                    if(match.status=="LIVE") 0xFFF65C6D.toInt() else accent))
                .addContent(spacer(5f))
                .addContent(text("${match.day().monthValue}/${match.day().dayOfMonth} · ${match.competition}", 11f, 0xFFB5C7D8.toInt()))
        }
        if (feed.isDemo) column.addContent(spacer(6f)).addContent(text("DEMO · 실제 경기 아님", 9f, 0xFFFFD498.toInt()))
        return column.build()
    }

    private fun text(value: String, size: Float, color: Int) = LayoutElementBuilders.Text.Builder()
        .setText(value)
        .setFontStyle(LayoutElementBuilders.FontStyle.Builder()
            .setSize(DimensionBuilders.sp(size)).setColor(ColorBuilders.argb(color)).build())
        .setMaxLines(1)
        .build()
    private fun image(id: String) = LayoutElementBuilders.Image.Builder().setResourceId(id)
        .setWidth(DimensionBuilders.dp(34f)).setHeight(DimensionBuilders.dp(34f)).build()
    private fun spacer(h: Float) = LayoutElementBuilders.Spacer.Builder().setHeight(DimensionBuilders.dp(h)).build()
    private fun spacerWidth(w: Float) = LayoutElementBuilders.Spacer.Builder().setWidth(DimensionBuilders.dp(w)).build()
}
