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
import com.google.common.util.concurrent.ListenableFuture
import dev.matchday.core.Game
import dev.matchday.core.SportsFeed
import dev.matchday.core.TileMatchChoice
import dev.matchday.core.TileMatchKind
import dev.matchday.core.selectTileMatch
import java.time.LocalDate

/** Two separately selectable tiles; both use the latest phone-synced offline cache. */
class KboTileService : MatchdayTileService("KBO")
class LolTileService : MatchdayTileService("LOL")

abstract class MatchdayTileService(private val sport: String) : TileService() {
    private val resourcesVersion = "logos_1"

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> {
        val feed = WatchFeedStore(this).read()
        val comps = if (sport == "KBO") listOf("KBO") else listOf("LCK", "MSI", "EWC", "WORLDS", "FST")
        val today = feed.referenceDay()
        val daily = feed.games.filter { it.competition in comps && it.day() == today }
        val fav = if (sport == "KBO") "SAM" else "GEN"
        val match = feed.selectTileMatch(comps, fav, today)
        val root = tileLayout(feed, daily.size, match, today)
        val tile = TileBuilders.Tile.Builder()
            .setResourcesVersion(resourcesVersion)
            .setFreshnessIntervalMillis(15 * 60 * 1000L)
            .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(root))
            .build()
        return Futures.immediateFuture(tile)
    }

    override fun onTileResourcesRequest(requestParams: RequestBuilders.ResourcesRequest): ListenableFuture<ResourceBuilders.Resources> {
        val resource = ResourceBuilders.Resources.Builder().setVersion(resourcesVersion)
        // Register every built-in team badge for image elements referenced by the layout.
        WatchFeedStore(this).read().teams.forEach { team ->
            val id = resources.getIdentifier("team_${team.id.lowercase()}", "drawable", packageName)
            if (id != 0) resource.addIdToImageMapping(team.id, ResourceBuilders.ImageResource.Builder()
                .setAndroidResourceByResId(ResourceBuilders.AndroidImageResourceByResId.Builder().setResourceId(id).build())
                .build())
        }
        resource.addIdToImageMapping("TBD", ResourceBuilders.ImageResource.Builder()
            .setAndroidResourceByResId(ResourceBuilders.AndroidImageResourceByResId.Builder().setResourceId(R.drawable.ic_matchday).build())
            .build())
        return Futures.immediateFuture(resource.build())
    }

    private fun tileLayout(feed: SportsFeed, todaysCount: Int, choice: TileMatchChoice?, today: LocalDate): LayoutElementBuilders.LayoutElement {
        val accent = if (sport == "KBO") 0xFF428BFF.toInt() else 0xFF1DCE7A.toInt()
        val title = if (choice?.kind == TileMatchKind.NEXT_GAME) {
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
            val homeRes = resources.getIdentifier("team_${home.id.lowercase()}", "drawable", packageName)
            val awayRes = resources.getIdentifier("team_${away.id.lowercase()}", "drawable", packageName)
            if (homeRes != 0) item.addContent(image(home.id))
            item.addContent(spacerWidth(7f))
            item.addContent(text(score, 23f, 0xFFFFFFFF.toInt()))
            item.addContent(spacerWidth(7f))
            if (awayRes != 0) item.addContent(image(away.id))
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
                .addContent(text(when(match.status){"LIVE"->"● LIVE";"FINAL"->"경기 종료";else->"경기 예정"}, 13f,
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
