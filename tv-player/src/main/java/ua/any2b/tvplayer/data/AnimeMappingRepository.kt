package ua.any2b.tvplayer.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import ua.any2b.core.tmdb.TmdbMediaType

data class EpisodeMapping(val malRange: String, val tmdbRange: String)

data class TmdbAnimeMapping(
    val type: TmdbMediaType,
    val tmdbId: Int,
    val season: Int?,
    val episodeMappings: List<EpisodeMapping>,
)

data class AniTubeEntry(
    val newsId: Int,
    val malId: Int,
    val pageUrl: String,
    val aniListIds: List<Int>,
    val titleUa: String,
    val hikkaTitleUa: String,
    val titleEnglish: List<String>,
    val titleRomaji: List<String>,
    val tmdbMappings: List<TmdbAnimeMapping>,
    val seasonScope: Int? = null,
)

/** Data-source boundary for anime identity mappings. UI and playback use only domain models. */
interface AnimeMappingRepository {
    suspend fun findByTmdb(
        type: TmdbMediaType,
        tmdbId: Int,
        season: Int? = null,
    ): List<AniTubeEntry>

    suspend fun findByAniList(aniListId: Int): List<AniTubeEntry>
    suspend fun findByAniTube(newsId: Int): AniTubeEntry?
    suspend fun findUnscopedNewsIds(type: TmdbMediaType, tmdbId: Int): Set<Int>
    suspend fun search(query: String, limit: Int = 30): List<AniTubeEntry>
}

/** Lazy, process-scoped reader of the generated asset. Indexes are parsed once on first use. */
class LocalAnimeMappingRepository private constructor(context: Context) : AnimeMappingRepository {
    private val appContext = context.applicationContext
    private val loadMutex = Mutex()
    @Volatile private var snapshot: Snapshot? = null

    override suspend fun findByTmdb(
        type: TmdbMediaType,
        tmdbId: Int,
        season: Int?,
    ): List<AniTubeEntry> = withIndex { index ->
        val key = if (type == TmdbMediaType.TV) {
            if (season == null) "tmdb_show:$tmdbId:" else "tmdb_show:$tmdbId:s$season"
        } else "tmdb_movie:$tmdbId"
        val matches = if (type == TmdbMediaType.TV && season == null) {
            index.byTmdb.entries.asSequence().filter { it.key.startsWith(key) }
                .flatMap { it.value.asSequence() }.map(AniTubeEntry::newsId).distinct().toList()
        } else index.byTmdb[key].orEmpty().map(AniTubeEntry::newsId)
        matches.mapNotNull(index.byAniTube::get)
    }

    override suspend fun findByAniList(aniListId: Int): List<AniTubeEntry> =
        withIndex { it.byAniList[aniListId].orEmpty() }

    override suspend fun findByAniTube(newsId: Int): AniTubeEntry? =
        withIndex { it.byAniTube[newsId] }

    override suspend fun findUnscopedNewsIds(type: TmdbMediaType, tmdbId: Int): Set<Int> =
        withIndex { index ->
            val key = if (type == TmdbMediaType.TV) "tmdb_show:$tmdbId"
                else "tmdb_movie:$tmdbId"
            index.unscopedByTmdb[key].orEmpty()
        }

    override suspend fun search(query: String, limit: Int): List<AniTubeEntry> {
        val normalized = normalize(query)
        if (normalized.isBlank() || limit <= 0) return emptyList()
        return withIndex { index ->
            index.byTitle.entries.asSequence().filter { row ->
                val title = row.key.first
                title == normalized || title.startsWith(normalized) || title.contains(normalized)
            }.sortedBy { (title, _) ->
                when {
                    title.first == normalized -> 0
                    title.first.startsWith(normalized) -> 1
                    else -> 2
                }
            }.map { it.value }
                .distinctBy(AniTubeEntry::newsId).take(limit).toList()
        }
    }

    private suspend fun <T> withIndex(block: (Snapshot) -> T): T =
        block(snapshot ?: load())

    private suspend fun load(): Snapshot {
        snapshot?.let { return it }
        return loadMutex.withLock {
            snapshot?.let { return@withLock it }
            withContext(Dispatchers.IO) {
                val root = appContext.assets.open(ASSET_NAME).bufferedReader(Charsets.UTF_8).use {
                    JSONObject(it.readText())
                }
                parse(root).also { snapshot = it }
            }
        }
    }

    private fun parse(root: JSONObject): Snapshot {
        val byTmdb = mutableMapOf<String, MutableList<AniTubeEntry>>()
        val byAniTube = mutableMapOf<Int, AniTubeEntry>()
        val byAniList = mutableMapOf<Int, MutableList<AniTubeEntry>>()
        val byTitle = mutableMapOf<Pair<String, Int>, AniTubeEntry>()
        val unscopedByTmdb = mutableMapOf<String, MutableSet<Int>>()
        val releases = root.optJSONObject("releases") ?: JSONObject()
        val titleRows = root.optJSONArray("byTitle")
        val namesByRelease = mutableMapOf<Int, MutableMap<String, MutableList<String>>>()
        if (titleRows != null) for (i in 0 until titleRows.length()) {
            val row = titleRows.optJSONObject(i) ?: continue
            val newsId = row.optInt("newsId").takeIf { it > 0 } ?: continue
            row.optString("title").takeIf(String::isNotBlank)?.let {
                val language = row.optString("language")
                namesByRelease.getOrPut(newsId, ::mutableMapOf)
                    .getOrPut(language, ::mutableListOf).add(it)
            }
        }

        val tmdbRoot = root.optJSONObject("byTmdb") ?: JSONObject()
        val keys = tmdbRoot.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val targets = tmdbRoot.optJSONArray(key) ?: continue
            for (i in 0 until targets.length()) {
                val row = targets.optJSONObject(i) ?: continue
                val newsId = row.optInt("newsId").takeIf { it > 0 } ?: continue
                val malId = row.optInt("malId").takeIf { it > 0 } ?: continue
                val aniListIds = row.optJSONArray("aniListIds")?.let { ids ->
                    (0 until ids.length()).map { ids.optInt(it) }.filter { it > 0 }
                }.orEmpty()
                val aniTitles = row.optJSONArray("aniListTitles")
                val english = mutableListOf<String>()
                val romaji = mutableListOf<String>()
                if (aniTitles != null) for (titleIndex in 0 until aniTitles.length()) {
                    val titles = aniTitles.optJSONObject(titleIndex)?.optJSONObject("title") ?: continue
                    titles.optString("english").takeIf(String::isNotBlank)?.let(english::add)
                    titles.optString("romaji").takeIf(String::isNotBlank)?.let(romaji::add)
                }
                val type = if (row.optString("tmdbType") == "movie") TmdbMediaType.MOVIE
                    else TmdbMediaType.TV
                val season = if (row.isNull("tmdbSeason")) null else row.optInt("tmdbSeason")
                val ranges = row.optJSONArray("episodeRanges")?.let { values ->
                    (0 until values.length()).mapNotNull { rangeIndex ->
                        values.optJSONObject(rangeIndex)?.let { range ->
                            EpisodeMapping(range.optString("malRange"), range.optString("tmdbRange"))
                        }
                    }
                }.orEmpty()
                val release = releases.optJSONObject(newsId.toString())
                val entry = AniTubeEntry(newsId, malId,
                    release?.optString("pageUrl").orEmpty(), aniListIds,
                    release?.optString("title")?.takeIf(String::isNotBlank)
                        ?: row.optString("anitubeTitleUa"),
                    row.optString("hikkaTitleUa"),
                    english.distinct(), romaji.distinct(),
                    listOf(TmdbAnimeMapping(type, row.optInt("tmdbId"), season, ranges)))
                val prior = byAniTube[newsId]
                val merged = if (prior == null) entry else prior.copy(
                    tmdbMappings = (prior.tmdbMappings + entry.tmdbMappings).distinct())
                byAniTube[newsId] = merged
                byTmdb.getOrPut(key, ::mutableListOf).add(entry)
                aniListIds.forEach { id -> byAniList.getOrPut(id, ::mutableListOf).add(entry) }
            }
        }
        val aniListRoot = root.optJSONObject("byAniList") ?: JSONObject()
        val aniListKeys = aniListRoot.keys()
        while (aniListKeys.hasNext()) {
            val aniListId = aniListKeys.next().toIntOrNull() ?: continue
            val rows = aniListRoot.optJSONArray(aniListId.toString()) ?: continue
            for (i in 0 until rows.length()) {
                val row = rows.optJSONObject(i) ?: continue
                val newsId = row.optInt("newsId").takeIf { it > 0 } ?: continue
                val malId = row.optInt("malId").takeIf { it > 0 } ?: continue
                val release = releases.optJSONObject(newsId.toString())
                val names = namesByRelease[newsId].orEmpty()
                val ids = row.optJSONArray("aniListIds")?.let { values ->
                    (0 until values.length()).map { values.optInt(it) }.filter { it > 0 }
                }.orEmpty().ifEmpty { listOf(aniListId) }
                val titleInfo = mutableListOf<String>()
                val romajiInfo = mutableListOf<String>()
                val titleRows = row.optJSONArray("aniListTitles")
                if (titleRows != null) for (titleIndex in 0 until titleRows.length()) {
                    val title = titleRows.optJSONObject(titleIndex)?.optJSONObject("title") ?: continue
                    title.optString("english").takeIf(String::isNotBlank)?.let(titleInfo::add)
                    title.optString("romaji").takeIf(String::isNotBlank)?.let(romajiInfo::add)
                }
                val mappings = row.optJSONArray("tmdbMappings")?.let { values ->
                    (0 until values.length()).mapNotNull { mappingIndex ->
                        val mapping = values.optJSONObject(mappingIndex) ?: return@mapNotNull null
                        val ranges = mapping.optJSONArray("episodeRanges")?.let { rangeArray ->
                            (0 until rangeArray.length()).mapNotNull { rangeIndex ->
                                rangeArray.optJSONObject(rangeIndex)?.let { range ->
                                    EpisodeMapping(range.optString("malRange"), range.optString("tmdbRange"))
                                }
                            }
                        }.orEmpty()
                        TmdbAnimeMapping(
                            if (mapping.optString("tmdbType") == "movie") TmdbMediaType.MOVIE
                                else TmdbMediaType.TV,
                            mapping.optInt("tmdbId"),
                            if (mapping.isNull("tmdbSeason")) null else mapping.optInt("tmdbSeason"),
                            ranges,
                        )
                    }
                }.orEmpty()
                val entry = AniTubeEntry(
                    newsId = newsId,
                    malId = malId,
                    pageUrl = release?.optString("pageUrl").orEmpty(),
                    aniListIds = ids,
                    titleUa = release?.optString("title")?.takeIf(String::isNotBlank)
                        ?: names["anitube_uk"].orEmpty().firstOrNull().orEmpty(),
                    hikkaTitleUa = names["hikka_uk"].orEmpty().firstOrNull().orEmpty(),
                    titleEnglish = (titleInfo + names["english"].orEmpty()).distinct(),
                    titleRomaji = (romajiInfo + names["romaji"].orEmpty()).distinct(),
                    tmdbMappings = mappings,
                    seasonScope = row.optInt("seasonScope").takeIf { !row.isNull("seasonScope") && it > 0 },
                )
                val prior = byAniTube[newsId]
                val merged = if (prior == null) entry else prior.copy(
                    aniListIds = (prior.aniListIds + ids).distinct(),
                    tmdbMappings = (prior.tmdbMappings + mappings).distinct(),
                    seasonScope = entry.seasonScope ?: prior.seasonScope,
                )
                byAniTube[newsId] = merged
                ids.forEach { id -> byAniList.getOrPut(id, ::mutableListOf).add(merged) }
            }
        }

        val seriesRoot = root.optJSONObject("bySeries") ?: JSONObject()
        val seriesKeys = seriesRoot.keys()
        while (seriesKeys.hasNext()) {
            val key = seriesKeys.next()
            val rows = seriesRoot.optJSONArray(key) ?: continue
            val ids = unscopedByTmdb.getOrPut(key, ::mutableSetOf)
            for (i in 0 until rows.length()) {
                rows.optJSONObject(i)?.optInt("newsId")?.takeIf { it > 0 }?.let(ids::add)
            }
        }
        namesByRelease.forEach { (newsId, namesByLanguage) ->
            val entry = byAniTube[newsId] ?: return@forEach
            namesByLanguage.values.flatten().distinctBy(::normalize).forEach { name ->
                byTitle[normalize(name) to newsId] = entry
            }
        }
        return Snapshot(
            byTmdb.mapValues { (_, entries) -> entries.distinctBy(AniTubeEntry::newsId) },
            byAniList.mapValues { (_, entries) -> entries.distinctBy(AniTubeEntry::newsId) },
            byAniTube,
            byTitle,
            unscopedByTmdb,
        )
    }

    private data class Snapshot(
        val byTmdb: Map<String, List<AniTubeEntry>>,
        val byAniList: Map<Int, List<AniTubeEntry>>,
        val byAniTube: Map<Int, AniTubeEntry>,
        val byTitle: Map<Pair<String, Int>, AniTubeEntry>,
        val unscopedByTmdb: Map<String, Set<Int>>,
    )

    companion object {
        private const val ASSET_NAME = "hikka-anitube-index.json"
        @Volatile private var instance: LocalAnimeMappingRepository? = null
        fun get(context: Context): LocalAnimeMappingRepository = instance ?: synchronized(this) {
            instance ?: LocalAnimeMappingRepository(context).also { instance = it }
        }
    }
}

private fun normalize(value: String): String = value.trim().lowercase()
    .replace('ё', 'е').replace(Regex("[^\\p{L}\\p{N}]+"), " ")
    .trim().replace(Regex("\\s+"), " ")
