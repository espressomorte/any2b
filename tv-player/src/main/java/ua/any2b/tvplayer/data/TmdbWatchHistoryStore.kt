package ua.any2b.tvplayer.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import ua.any2b.core.tmdb.TmdbMediaType
import ua.any2b.core.tmdb.TmdbTitle

data class TmdbEpisodeProgress(
    val voiceIndex: Int,
    val episodeIndex: Int,
    val episodeNumber: Int,
    val progress: Float,
    val seasonNumber: Int = 1,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val updatedAt: Long = 0L,
)

data class TmdbTorrentProgress(
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long = 0L,
)

data class TmdbPlaybackSelection(
    val source: Source,
    val topicId: String? = null,
    val fileId: Int? = null,
    val fileName: String? = null,
    val anitubeReleaseId: Int? = null,
    val anitubeSeasonNumber: Int? = null,
    val voiceIndex: Int = 0,
    val voiceName: String? = null,
) {
    enum class Source { TORRENT, ANITUBE }
}

/** Titles whose TorrServer stream actually started in the built-in player. */
class TmdbWatchHistoryStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(
        "tmdb_watch_history", Context.MODE_PRIVATE)

    fun all(): List<TmdbTitle> = synchronized(lock) { read() }

    fun record(title: TmdbTitle) = synchronized(lock) {
        val updated = (listOf(title) + read().filterNot {
            it.type == title.type && it.id == title.id
        }).take(100)
        writeHistory(updated)
    }

    fun removeFromHistory(title: TmdbTitle) = synchronized(lock) {
        writeHistory(read().filterNot { it.type == title.type && it.id == title.id })
    }

    private fun writeHistory(titles: List<TmdbTitle>) {
        prefs.edit().putString("items", JSONArray().apply {
            titles.forEach { item ->
                put(JSONObject().apply {
                    put("type", item.type.path)
                    put("id", item.id)
                    put("title", item.title)
                    put("originalTitle", item.originalTitle)
                    put("year", item.year)
                    put("posterUrl", item.posterUrl)
                    put("rating", item.rating)
                    put("genreIds", JSONArray(item.genreIds.sorted()))
                })
            }
        }.toString()).apply()
    }

    fun saveEpisodeProgress(
        title: TmdbTitle,
        voiceIndex: Int,
        episodeIndex: Int,
        episodeNumber: Int,
        progress: Float,
        seasonNumber: Int = title.matchedSeasonNumber ?: 1,
        positionMs: Long = 0L,
        durationMs: Long = 0L,
    ) = synchronized(lock) {
        record(title)
        val watchlist = TmdbWatchlistStore(appContext)
        if (title.type == TmdbMediaType.MOVIE) {
            if (progress >= 0.98f) watchlist.remove(title)
            else watchlist.onPlaybackStarted(title)
        } else {
            val seasons = watchlist.cachedSeasons(title.id).orEmpty()
                .filter { it.number > 0 && (it.episodeCount ?: 0) > 0 }
            val finalSeason = seasons.maxByOrNull { it.number }
            if (progress >= 0.98f && finalSeason?.number == seasonNumber &&
                episodeNumber >= (finalSeason.episodeCount ?: Int.MAX_VALUE)) {
                watchlist.remove(title)
            } else {
                watchlist.onPlaybackStarted(title.copy(matchedSeasonNumber = seasonNumber))
            }
        }
        val key = TitleKey.Tmdb(title.type, title.id).storageKey
        val values = try { JSONObject(prefs.getString("episode_progress", "{}").orEmpty()) }
            catch (_: Exception) { JSONObject() }
        values.put(key, JSONObject().apply {
            put("voiceIndex", voiceIndex)
            put("episodeIndex", episodeIndex)
            put("episodeNumber", episodeNumber)
            put("seasonNumber", seasonNumber)
            put("progress", progress.coerceIn(0f, 1f).toDouble())
            put("positionMs", positionMs.coerceAtLeast(0L))
            put("durationMs", durationMs.coerceAtLeast(0L))
            put("updatedAt", System.currentTimeMillis())
        })
        prefs.edit().putString("episode_progress", values.toString()).apply()
    }

    fun episodeProgress(type: TmdbMediaType, id: Int): TmdbEpisodeProgress? = synchronized(lock) {
        val key = TitleKey.Tmdb(type, id).storageKey
        try {
            val data = JSONObject(prefs.getString("episode_progress", "{}").orEmpty())
                .optJSONObject(key) ?: return@synchronized null
            TmdbEpisodeProgress(
                voiceIndex = data.optInt("voiceIndex", 0),
                episodeIndex = data.optInt("episodeIndex", 0),
                episodeNumber = data.optInt("episodeNumber", 1),
                progress = data.optDouble("progress", 0.0).toFloat().coerceIn(0f, 1f),
                seasonNumber = data.optInt("seasonNumber", 1).coerceAtLeast(1),
                positionMs = data.optLong("positionMs", 0L).coerceAtLeast(0L),
                durationMs = data.optLong("durationMs", 0L).coerceAtLeast(0L),
                updatedAt = data.optLong("updatedAt", 0L),
            )
        } catch (_: Exception) { null }
    }

    /** Recovers source identity for watch history written before playback selections were stored. */
    fun inferredPlaybackSelection(type: TmdbMediaType, id: Int): TmdbPlaybackSelection? =
        synchronized(lock) {
            data class TorrentCandidate(
                val updatedAt: Long,
                val topicId: String,
                val fileId: Int,
                val positionMs: Long,
                val durationMs: Long,
            )
            val prefix = "${TitleKey.Tmdb(type, id).storageKey}|"
            val torrent = try {
                val values = JSONObject(prefs.getString("torrent_progress", "{}").orEmpty())
                values.keys().asSequence().filter { it.startsWith(prefix) }
                    .mapNotNull { key ->
                        val parts = key.removePrefix(prefix).split('|')
                        if (parts.size != 2) return@mapNotNull null
                        val topic = parts[0]
                        val file = parts[1].toIntOrNull()?.takeIf { it > 0 }
                            ?: return@mapNotNull null
                        val value = values.optJSONObject(key) ?: return@mapNotNull null
                        TorrentCandidate(value.optLong("updatedAt", 0L), topic, file,
                            value.optLong("positionMs", 0L), value.optLong("durationMs", 0L))
                    }.maxByOrNull { it.updatedAt }
            } catch (_: Exception) { null }
            val episode = episodeProgress(type, id)
            val samePlaybackEvidence = torrent != null && episode != null &&
                torrent.positionMs == episode.positionMs && torrent.durationMs == episode.durationMs
            if (torrent != null && (torrent.updatedAt >= (episode?.updatedAt ?: 0L) ||
                    samePlaybackEvidence)) {
                TmdbPlaybackSelection(TmdbPlaybackSelection.Source.TORRENT,
                    topicId = torrent.topicId, fileId = torrent.fileId)
            } else if (episode != null) {
                TmdbPlaybackSelection(TmdbPlaybackSelection.Source.ANITUBE,
                    anitubeSeasonNumber = episode.seasonNumber,
                    voiceIndex = episode.voiceIndex)
            } else null
        }

    fun savePlaybackSelection(type: TmdbMediaType, id: Int, selection: TmdbPlaybackSelection) =
        synchronized(lock) {
            if (id <= 0) return@synchronized
            val key = TitleKey.Tmdb(type, id).storageKey
            val values = try { JSONObject(prefs.getString("playback_selection", "{}").orEmpty()) }
                catch (_: Exception) { JSONObject() }
            values.put(key, JSONObject().apply {
                put("source", selection.source.name)
                put("topicId", selection.topicId)
                put("fileId", selection.fileId)
                put("fileName", selection.fileName)
                put("anitubeReleaseId", selection.anitubeReleaseId)
                put("anitubeSeasonNumber", selection.anitubeSeasonNumber)
                put("voiceIndex", selection.voiceIndex)
                put("voiceName", selection.voiceName)
                put("updatedAt", System.currentTimeMillis())
            })
            prefs.edit().putString("playback_selection", values.toString()).apply()
        }

    fun playbackSelection(type: TmdbMediaType, id: Int): TmdbPlaybackSelection? =
        synchronized(lock) {
            try {
                val value = JSONObject(prefs.getString("playback_selection", "{}").orEmpty())
                    .optJSONObject(TitleKey.Tmdb(type, id).storageKey) ?: return@synchronized null
                val source = TmdbPlaybackSelection.Source.entries.firstOrNull {
                    it.name == value.optString("source")
                } ?: return@synchronized null
                TmdbPlaybackSelection(source,
                    value.optString("topicId").takeIf(String::isNotBlank),
                    value.optInt("fileId").takeIf { it > 0 },
                    value.optString("fileName").takeIf(String::isNotBlank),
                    value.optInt("anitubeReleaseId").takeIf { it > 0 },
                    value.optInt("anitubeSeasonNumber").takeIf { it > 0 },
                    value.optInt("voiceIndex").coerceAtLeast(0),
                    value.optString("voiceName").takeIf(String::isNotBlank))
            } catch (_: Exception) { null }
        }

    fun saveTorrentProgress(
        title: TmdbTitle,
        topicId: String,
        fileId: Int,
        positionMs: Long,
        durationMs: Long,
        episodeNumber: Int? = null,
        seasonNumber: Int? = null,
    ) = synchronized(lock) {
        if (topicId.isBlank() || fileId <= 0 || positionMs < 0 || durationMs <= 0) return@synchronized
        record(title)
        val watchlist = TmdbWatchlistStore(appContext)
        if (title.type == TmdbMediaType.MOVIE && positionMs >= durationMs - MOVIE_FINISH_GUARD_MS) {
            watchlist.remove(title)
        } else if (title.type == TmdbMediaType.TV ||
            positionMs >= MIN_MOVIE_PROGRESS_MS) {
            watchlist.onPlaybackStarted(title)
        }
        val key = torrentKey(title.type, title.id, topicId, fileId)
        val values = try { JSONObject(prefs.getString("torrent_progress", "{}").orEmpty()) }
            catch (_: Exception) { JSONObject() }
        values.put(key, JSONObject().apply {
            put("positionMs", positionMs.coerceAtMost(durationMs))
            put("durationMs", durationMs)
            put("updatedAt", System.currentTimeMillis())
        })
        prefs.edit().putString("torrent_progress", values.toString()).apply()
        if (title.type == TmdbMediaType.TV && episodeNumber != null && episodeNumber > 0) {
            saveEpisodeProgress(title, 0, episodeNumber - 1, episodeNumber,
                (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f),
                seasonNumber?.takeIf { it > 0 } ?: title.matchedSeasonNumber ?: 1,
                positionMs, durationMs)
        }
    }

    fun torrentProgress(
        type: TmdbMediaType,
        id: Int,
        topicId: String,
        fileId: Int,
    ): TmdbTorrentProgress? = synchronized(lock) {
        if (topicId.isBlank() || fileId <= 0) return@synchronized null
        try {
            val value = JSONObject(prefs.getString("torrent_progress", "{}").orEmpty())
                .optJSONObject(torrentKey(type, id, topicId, fileId)) ?: return@synchronized null
            val duration = value.optLong("durationMs", 0L)
            val position = value.optLong("positionMs", 0L)
            if (duration <= 0 || position < 5_000 || position >= duration - 5_000) null
            else TmdbTorrentProgress(position, duration,
                value.optLong("updatedAt", 0L))
        } catch (_: Exception) { null }
    }

    fun completeTorrentPlayback(
        title: TmdbTitle,
        topicId: String,
        fileId: Int,
        episodeNumber: Int? = null,
        seasonNumber: Int? = null,
    ) = synchronized(lock) {
        if (topicId.isBlank() || fileId <= 0) return@synchronized
        val values = try { JSONObject(prefs.getString("torrent_progress", "{}").orEmpty()) }
            catch (_: Exception) { JSONObject() }
        values.remove(torrentKey(title.type, title.id, topicId, fileId))
        prefs.edit().putString("torrent_progress", values.toString()).apply()
        if (title.type == TmdbMediaType.MOVIE) {
            TmdbWatchlistStore(appContext).remove(title)
        } else if (episodeNumber != null && episodeNumber > 0) {
            saveEpisodeProgress(title, 0, episodeNumber - 1, episodeNumber, 1f,
                seasonNumber?.takeIf { it > 0 } ?: title.matchedSeasonNumber ?: 1)
        }
    }

    fun clear() = synchronized(lock) { prefs.edit().clear().apply() }

    private fun read(): List<TmdbTitle> = try {
        val array = JSONArray(prefs.getString("items", "[]"))
        (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val type = TmdbMediaType.entries.firstOrNull {
                it.path == item.optString("type")
            } ?: return@mapNotNull null
            val id = item.optInt("id")
            val title = item.optString("title")
            if (id <= 0 || title.isBlank()) return@mapNotNull null
            TmdbTitle(id, type, title,
                item.optString("originalTitle").takeUnless { it.isBlank() || it == "null" },
                item.optInt("year").takeIf { it > 0 },
                item.optString("posterUrl").takeUnless { it.isBlank() || it == "null" },
                null,
                item.optDouble("rating", Double.NaN).takeIf(Double::isFinite),
                genreIds = item.optJSONArray("genreIds")?.let { genres ->
                    (0 until genres.length()).map { genres.optInt(it) }.filter { it > 0 }.toSet()
                }.orEmpty())
        }
    } catch (_: Exception) {
        emptyList()
    }

    private fun torrentKey(type: TmdbMediaType, id: Int, topicId: String, fileId: Int) =
        "${TitleKey.Tmdb(type, id).storageKey}|$topicId|$fileId"

    private companion object {
        const val MIN_MOVIE_PROGRESS_MS = 15_000L
        const val MOVIE_FINISH_GUARD_MS = 30_000L
        val lock = Any()
    }
}
