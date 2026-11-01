package dev.matchday.wear

import android.content.Context
import android.util.Log
import androidx.wear.tiles.TileService
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService
import dev.matchday.core.FeedCodec
import dev.matchday.core.FeedTransfer
import dev.matchday.core.SportsFeed

const val WEAR_PREFS = "matchday_watch"

class WatchFeedStore(private val context: Context) {
    private val prefs = context.getSharedPreferences(WEAR_PREFS, Context.MODE_PRIVATE)
    fun raw(): String = prefs.getString("feed_json", null)
        ?: context.assets.open("demo_feed.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
    fun read(): SportsFeed = runCatching { FeedCodec.decode(raw()) }
        .getOrElse { FeedCodec.decode(context.assets.open("demo_feed.json").bufferedReader(Charsets.UTF_8).use { it.readText() }) }
    fun store(json: String, theme: String) {
        FeedCodec.decode(json) // Don't allow malformed phone sync to destroy the watch cache.
        prefs.edit().putString("feed_json", json).putString("theme", theme).apply()
    }
}

/** The phone pushes JSON through Google's Wear Data Layer when paired and reachable. */
class PhoneDataListener : WearableListenerService() {
    override fun onDataChanged(dataEvents: DataEventBuffer) {
        for (event in dataEvents) {
            if (event.type != DataEvent.TYPE_CHANGED || event.dataItem.uri.path != "/matchday/feed") continue
            runCatching {
                val data = DataMapItem.fromDataItem(event.dataItem).dataMap
                val json = data.getString("payloadGzip")?.let(FeedTransfer::decompress)
                    ?: data.getString("payload") ?: return@runCatching
                val theme = data.getString("theme") ?: "SYSTEM"
                WatchFeedStore(this).store(json, theme)
                TileService.getUpdater(this).requestUpdate(KboTileService::class.java)
                TileService.getUpdater(this).requestUpdate(LolTileService::class.java)
            }.onFailure { Log.e("MatchdayWatch", "데이터 동기화 실패", it) }
        }
    }
}
