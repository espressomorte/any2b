package ua.any2b.tvplayer.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import ua.any2b.core.anilist.AnimeDetails

data class WatchHistoryEntry(
    val animeId: Int,
    val titleDisplay: String,
    val romaji: String?,
    val english: String?,
    val native: String?,
    val cover: String?,
    val score: Int?,
    val episodes: Int?,
    val format: String?,
    val status: String?,
    val seasonYear: Int?,
    val episodeIndex: Int, // 0-based
    val episodeNumber: Int, // 1-based
    val voiceIndex: Int, // saved voice index
    val progress: Float, // 0.0 to 1.0
    val lastWatchedTimestamp: Long
)

class WatchHistoryManager(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("watch_history_prefs", Context.MODE_PRIVATE)

    fun getHistory(): List<WatchHistoryEntry> {
        val jsonString = prefs.getString("history_list", "[]") ?: "[]"
        val list = mutableListOf<WatchHistoryEntry>()
        try {
            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    WatchHistoryEntry(
                        animeId = obj.getInt("animeId"),
                        titleDisplay = obj.getString("titleDisplay"),
                        romaji = obj.optString("romaji").takeIf { !it.isNullOrBlank() },
                        english = obj.optString("english").takeIf { !it.isNullOrBlank() },
                        native = obj.optString("native").takeIf { !it.isNullOrBlank() },
                        cover = obj.optString("cover").takeIf { !it.isNullOrBlank() },
                        score = if (obj.has("score")) obj.getInt("score") else null,
                        episodes = if (obj.has("episodes")) obj.getInt("episodes") else null,
                        format = obj.optString("format").takeIf { !it.isNullOrBlank() },
                        status = obj.optString("status").takeIf { !it.isNullOrBlank() },
                        seasonYear = if (obj.has("seasonYear")) obj.getInt("seasonYear") else null,
                        episodeIndex = obj.getInt("episodeIndex"),
                        episodeNumber = obj.getInt("episodeNumber"),
                        voiceIndex = if (obj.has("voiceIndex")) obj.getInt("voiceIndex") else 0,
                        progress = if (obj.has("progress")) obj.getDouble("progress").toFloat() else 1.0f,
                        lastWatchedTimestamp = obj.getLong("lastWatchedTimestamp")
                    )
                )
            }
        } catch (_: Exception) {}
        return list.sortedByDescending { it.lastWatchedTimestamp }
    }

    fun getEntry(animeId: Int): WatchHistoryEntry? {
        return getHistory().find { item -> item.animeId == animeId }
    }

    fun saveProgress(
        details: AnimeDetails,
        episodeIndex: Int,
        voiceIndex: Int,
        progress: Float = 1.0f,
        episodeNumber: Int = episodeIndex + 1,
    ) {
        val list = getHistory().toMutableList()
        val existingIndex = list.indexOfFirst { it.animeId == details.id }
        val entry = WatchHistoryEntry(
            animeId = details.id,
            titleDisplay = details.title.display,
            romaji = details.title.romaji,
            english = details.title.english,
            native = details.title.native,
            cover = details.cover,
            score = details.score,
            episodes = details.episodes,
            format = details.format,
            status = details.status,
            seasonYear = details.seasonYear,
            episodeIndex = episodeIndex,
            episodeNumber = episodeNumber,
            voiceIndex = voiceIndex,
            progress = progress,
            lastWatchedTimestamp = System.currentTimeMillis()
        )
        if (existingIndex >= 0) {
            list.removeAt(existingIndex)
        }
        list.add(0, entry)
        persist(list)
    }

    private fun persist(list: List<WatchHistoryEntry>) {
        val jsonArray = JSONArray()
        for (item in list) {
            val obj = JSONObject().apply {
                put("animeId", item.animeId)
                put("titleDisplay", item.titleDisplay)
                item.romaji?.let { put("romaji", it) }
                item.english?.let { put("english", it) }
                item.native?.let { put("native", it) }
                item.cover?.let { put("cover", it) }
                item.score?.let { put("score", it) }
                item.episodes?.let { put("episodes", it) }
                item.format?.let { put("format", it) }
                item.status?.let { put("status", it) }
                item.seasonYear?.let { put("seasonYear", it) }
                put("episodeIndex", item.episodeIndex)
                put("episodeNumber", item.episodeNumber)
                put("voiceIndex", item.voiceIndex)
                put("progress", item.progress.toDouble())
                put("lastWatchedTimestamp", item.lastWatchedTimestamp)
            }
            jsonArray.put(obj)
        }
        prefs.edit().putString("history_list", jsonArray.toString()).apply()
    }
}
