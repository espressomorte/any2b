package ua.any2b.tvplayer.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import ua.any2b.core.tmdb.TmdbEpisode
import ua.any2b.core.tmdb.TmdbMediaType
import ua.any2b.core.tmdb.TmdbSeason
import ua.any2b.core.tmdb.TmdbTitle
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class TmdbWatchlistEntry(
    val title: TmdbTitle,
    val source: Source,
    val addedAt: Long,
) {
    enum class Source { MANUAL, IN_PROGRESS }
}

class TmdbWatchlistStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("tmdb_watchlist", Context.MODE_PRIVATE)
    private val stillsRoot = File(appContext.filesDir, "watchlist_episode_stills")

    fun all(): List<TmdbWatchlistEntry> = synchronized(lock) { readEntries() }

    fun contains(type: TmdbMediaType, id: Int): Boolean = synchronized(lock) {
        readEntries().any { it.title.type == type && it.title.id == id }
    }

    fun addManual(title: TmdbTitle): Boolean = synchronized(lock) {
        val key = titleKey(title.type, title.id)
        val entries = readEntries().associateBy { titleKey(it.title.type, it.title.id) }.toMutableMap()
        if (entries.containsKey(key)) return@synchronized false
        entries[key] = TmdbWatchlistEntry(title, TmdbWatchlistEntry.Source.MANUAL,
            System.currentTimeMillis())
        persistEntries(entries.values.toList())
        true
    }

    internal fun remove(title: TmdbTitle) = synchronized(lock) {
        val entries = readEntries().filterNot {
            it.title.type == title.type && it.title.id == title.id
        }
        persistEntries(entries)
        clearTitleCache(title.type, title.id)
    }

    /** Called once when playback actually starts; history alone does not add a title. */
    fun onPlaybackStarted(title: TmdbTitle) = synchronized(lock) {
        val entries = readEntries().associateBy { titleKey(it.title.type, it.title.id) }.toMutableMap()
        val key = titleKey(title.type, title.id)
        val existing = entries[key]
        val enrichedTitle = title.copy(
            originalTitle = title.originalTitle ?: existing?.title?.originalTitle,
            year = title.year ?: existing?.title?.year,
            posterUrl = title.posterUrl ?: existing?.title?.posterUrl,
            backdropUrl = title.backdropUrl ?: existing?.title?.backdropUrl,
            overview = title.overview ?: existing?.title?.overview,
            rating = title.rating ?: existing?.title?.rating,
            genreIds = title.genreIds.ifEmpty { existing?.title?.genreIds.orEmpty() },
        )
        entries[key] = if (existing?.source == TmdbWatchlistEntry.Source.MANUAL) {
            existing.copy(title = enrichedTitle)
        } else {
            TmdbWatchlistEntry(enrichedTitle, TmdbWatchlistEntry.Source.IN_PROGRESS,
                existing?.addedAt ?: System.currentTimeMillis())
        }
        persistEntries(entries.values.toList())
    }

    fun cachedEpisodes(seriesId: Int, seasonNumber: Int): List<TmdbEpisode>? = synchronized(lock) {
        val key = episodeKey(seriesId, seasonNumber)
        val json = prefs.getString(key, null) ?: return@synchronized null
        try {
            val array = JSONArray(json)
            (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                TmdbEpisode(item.optInt("id"), item.optInt("seasonNumber", seasonNumber),
                    item.optInt("episodeNumber"), item.optString("name"),
                    item.optString("overview").takeIf(String::isNotBlank),
                    item.optString("stillUrl").takeIf(String::isNotBlank),
                    item.optString("airDate").takeIf(String::isNotBlank))
            }
        } catch (_: Exception) { null }
    }

    fun saveEpisodes(seriesId: Int, seasonNumber: Int, episodes: List<TmdbEpisode>) = synchronized(lock) {
        prefs.edit().putString(episodeKey(seriesId, seasonNumber), JSONArray().apply {
            episodes.forEach { episode -> put(JSONObject().apply {
                put("id", episode.id)
                put("seasonNumber", episode.seasonNumber)
                put("episodeNumber", episode.episodeNumber)
                put("name", episode.name)
                put("overview", episode.overview)
                put("stillUrl", episode.stillUrl)
                put("airDate", episode.airDate)
            }) }
        }.toString()).apply()
    }

    fun cachedSeasons(seriesId: Int): List<TmdbSeason>? = synchronized(lock) {
        val json = prefs.getString(seasonKey(seriesId), null) ?: return@synchronized null
        try {
            val array = JSONArray(json)
            (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                TmdbSeason(item.optInt("number"), item.optString("name").takeIf(String::isNotBlank),
                    item.optInt("year").takeIf { it > 0 },
                    item.optInt("episodeCount").takeIf { it > 0 })
            }
        } catch (_: Exception) { null }
    }

    fun saveSeasons(seriesId: Int, seasons: List<TmdbSeason>) = synchronized(lock) {
        prefs.edit().putString(seasonKey(seriesId), JSONArray().apply {
            seasons.forEach { season -> put(JSONObject().apply {
                put("number", season.number)
                put("name", season.name)
                put("year", season.year)
                put("episodeCount", season.episodeCount)
            }) }
        }.toString()).apply()
    }

    fun cachedLogo(type: TmdbMediaType, id: Int): String? = synchronized(lock) {
        prefs.getString(logoKey(type, id), null)?.takeIf(String::isNotBlank)
    }

    fun hasCachedLogo(type: TmdbMediaType, id: Int): Boolean = synchronized(lock) {
        prefs.contains(logoKey(type, id))
    }

    fun saveLogo(type: TmdbMediaType, id: Int, logoUrl: String?) = synchronized(lock) {
        prefs.edit().putString(logoKey(type, id), logoUrl.orEmpty()).apply()
    }

    fun updateTitleMetadata(title: TmdbTitle) = synchronized(lock) {
        val entries = readEntries().map { entry ->
            if (entry.title.type == title.type && entry.title.id == title.id)
                entry.copy(title = entry.title.copy(
                    backdropUrl = title.backdropUrl ?: entry.title.backdropUrl,
                    posterUrl = title.posterUrl ?: entry.title.posterUrl,
                    overview = title.overview ?: entry.title.overview,
                    originalTitle = title.originalTitle ?: entry.title.originalTitle,
                    ukrainianTitle = title.ukrainianTitle ?: entry.title.ukrainianTitle,
                    originCountries = title.originCountries.ifEmpty { entry.title.originCountries },
                    year = title.year ?: entry.title.year,
                    genreIds = title.genreIds.ifEmpty { entry.title.genreIds },
                )) else entry
        }
        persistEntries(entries)
    }

    fun cachedStill(seriesId: Int, episode: TmdbEpisode): File? = File(stillsRoot,
        "$seriesId/${episode.seasonNumber}_${episode.episodeNumber}.jpg")
        .takeIf { it.isFile && it.length() > 0 }

    suspend fun cacheStills(seriesId: Int, episodes: List<TmdbEpisode>) = withContext(Dispatchers.IO) {
        val directory = File(stillsRoot, seriesId.toString())
        episodes.asSequence().filter { it.stillUrl != null }.take(MAX_CACHED_STILLS).forEach { episode ->
            val destination = File(directory, "${episode.seasonNumber}_${episode.episodeNumber}.jpg")
            if (destination.isFile && destination.length() > 0) return@forEach
            try {
                val connection = URL(episode.stillUrl).openConnection() as HttpURLConnection
                connection.connectTimeout = 12_000
                connection.readTimeout = 20_000
                connection.setRequestProperty("User-Agent", "Any2B Android TV")
                connection.connect()
                if (connection.responseCode in 200..299) {
                    directory.mkdirs()
                    val temporary = File(directory, destination.name + ".tmp")
                    connection.inputStream.use { input -> temporary.outputStream().use(input::copyTo) }
                    if (!temporary.renameTo(destination)) temporary.delete()
                }
                connection.disconnect()
            } catch (_: Exception) {
                // The TMDB still URL remains cached; Coil can fetch it when online.
            }
        }
    }

    private fun clearTitleCache(type: TmdbMediaType, id: Int) {
        prefs.edit().remove(logoKey(type, id)).apply()
        if (type != TmdbMediaType.TV) return
        val prefix = "episodes_${id}_"
        val editor = prefs.edit()
        prefs.all.keys.filter { it.startsWith(prefix) }.forEach(editor::remove)
        editor.remove(seasonKey(id))
        editor.apply()
        File(stillsRoot, id.toString()).deleteRecursively()
    }

    private fun readEntries(): List<TmdbWatchlistEntry> = try {
        val array = JSONArray(prefs.getString(ENTRIES_KEY, "[]"))
        (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val type = TmdbMediaType.entries.firstOrNull { it.path == item.optString("type") }
                ?: return@mapNotNull null
            val id = item.optInt("id")
            val title = item.optString("title")
            if (id <= 0 || title.isBlank()) return@mapNotNull null
            val media = TmdbTitle(id, type, title,
                item.optString("originalTitle").takeIf(String::isNotBlank),
                item.optInt("year").takeIf { it > 0 },
                item.optString("posterUrl").takeIf(String::isNotBlank),
                item.optString("overview").takeIf(String::isNotBlank),
                item.optDouble("rating", Double.NaN).takeIf(Double::isFinite),
                ukrainianTitle = item.optString("ukrainianTitle").takeIf(String::isNotBlank),
                genreIds = item.optJSONArray("genreIds")?.let { genres ->
                    (0 until genres.length()).map { genres.optInt(it) }.filter { it > 0 }.toSet()
                }.orEmpty(),
                originCountries = item.optJSONArray("originCountries")?.let { countries ->
                    (0 until countries.length()).map { countries.optString(it) }.filter(String::isNotBlank)
                }?.toSet().orEmpty(),
                backdropUrl = item.optString("backdropUrl").takeIf(String::isNotBlank))
            val source = TmdbWatchlistEntry.Source.entries.firstOrNull {
                it.name == item.optString("source")
            } ?: TmdbWatchlistEntry.Source.IN_PROGRESS
            TmdbWatchlistEntry(media, source, item.optLong("addedAt"))
        }.sortedByDescending { it.addedAt }
    } catch (_: Exception) { emptyList() }

    private fun persistEntries(entries: List<TmdbWatchlistEntry>) {
        val retained = TmdbWatchlistEntry.Source.entries.flatMap { source ->
            val limit = if (source == TmdbWatchlistEntry.Source.IN_PROGRESS) MAX_HISTORY_ENTRIES
                else MAX_MANUAL_ENTRIES
            entries.filter { it.source == source }.sortedByDescending { it.addedAt }.take(limit)
        }.sortedByDescending { it.addedAt }
        val retainedKeys = retained.map { titleKey(it.title.type, it.title.id) }.toSet()
        val evicted = entries.filterNot { titleKey(it.title.type, it.title.id) in retainedKeys }
        prefs.edit().putString(ENTRIES_KEY, JSONArray().apply {
            retained.forEach { entry -> put(JSONObject().apply {
                val title = entry.title
                put("type", title.type.path)
                put("id", title.id)
                put("title", title.title)
                put("originalTitle", title.originalTitle)
                put("ukrainianTitle", title.ukrainianTitle)
                put("year", title.year)
                put("posterUrl", title.posterUrl)
                put("backdropUrl", title.backdropUrl)
                put("overview", title.overview)
                put("rating", title.rating)
                put("genreIds", JSONArray(title.genreIds.sorted()))
                put("originCountries", JSONArray(title.originCountries.sorted()))
                put("source", entry.source.name)
                put("addedAt", entry.addedAt)
            }) }
        }.toString()).apply()
        evicted.forEach { clearTitleCache(it.title.type, it.title.id) }
    }

    private fun titleKey(type: TmdbMediaType, id: Int) = "${type.path}_$id"
    private fun episodeKey(seriesId: Int, seasonNumber: Int) = "episodes_${seriesId}_$seasonNumber"
    private fun seasonKey(seriesId: Int) = "seasons_$seriesId"
    private fun logoKey(type: TmdbMediaType, id: Int) = "logo_${type.path}_$id"

    private companion object {
        const val ENTRIES_KEY = "entries"
        const val MAX_CACHED_STILLS = 12
        const val MAX_HISTORY_ENTRIES = 4
        const val MAX_MANUAL_ENTRIES = 5
        val lock = Any()
    }
}
