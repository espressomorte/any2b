package ua.any2b.cli

import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlinx.coroutines.delay
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import ua.any2b.core.anilist.AniListAnimeLookup
import ua.any2b.core.anilist.AniListClient
import ua.any2b.core.anilist.AniListException
import ua.any2b.core.anitube.AnitubeSearchResult
import ua.any2b.core.matcher.SeriesTitleSearch
import ua.any2b.core.tmdb.FribbMappings
import ua.any2b.core.tmdb.TmdbClient
import ua.any2b.core.tmdb.TmdbDetails
import ua.any2b.core.tmdb.TmdbMediaType
import ua.any2b.core.tmdb.TmdbTitle

/** Builds a reviewable AniTube → TMDB season → AniList crosswalk. */
internal class AniTubeMappingGenerator(
    private val tmdbToken: String,
    private val delayMs: Long = 250,
) {
    private val aniList = AniListClient()
    private val tmdb = TmdbClient()
    private val searchCache = mutableMapOf<String, List<TmdbTitle>>()
    private val detailsCache = mutableMapOf<Pair<TmdbMediaType, Int>, TmdbDetails>()
    private val aniListSearchCache = mutableMapOf<String, List<AniListAnimeLookup>>()

    suspend fun generate(
        catalog: List<AnitubeSearchResult>,
        sourceCount: Int,
        sampleSeed: Long?,
        existingItems: List<JSONObject> = emptyList(),
        reviewHints: Map<Int, String> = emptyMap(),
        onCheckpoint: suspend (JSONObject) -> Unit = {},
    ): JSONObject {
        val bridge = downloadAniBridge()
        val fribb = FribbMappings.parseIndex(downloadFribb())
        val bridgeMeta = bridge.optJSONObject("\$meta")
        val validIds = catalog.mapTo(mutableSetOf()) { it.newsId }
        val processedById = existingItems.mapNotNull { row ->
            row.optInt("newsId").takeIf { it in validIds }?.let { it to row }
        }.toMap().toMutableMap()
        val pending = catalog.filterNot { it.newsId in processedById }
        pending.forEachIndexed { indexInRun, item ->
            println("[${processedById.size + 1}/$sourceCount] ${item.title}")
            processedById[item.newsId] = mapTitle(item, bridge, fribb, reviewHints[item.newsId])
            if (processedById.size % CHECKPOINT_INTERVAL == 0) {
                onCheckpoint(buildOutput(catalog, processedById, sourceCount, sampleSeed,
                    bridgeMeta, complete = false))
            }
            if (indexInRun < pending.lastIndex) delay(delayMs)
        }
        val result = buildOutput(catalog, processedById, sourceCount, sampleSeed, bridgeMeta,
            complete = processedById.size == sourceCount)
        onCheckpoint(result)
        return result
    }

    private fun buildOutput(
        catalog: List<AnitubeSearchResult>,
        processedById: Map<Int, JSONObject>,
        sourceCount: Int,
        sampleSeed: Long?,
        bridgeMeta: JSONObject?,
        complete: Boolean,
    ): JSONObject {
        val processed = catalog.mapNotNull { processedById[it.newsId] }
        val counts = processed.groupingBy { it.getString("status") }.eachCount()
        return JSONObject().apply {
            put("\$meta", JSONObject().apply {
                put("schema", "any2b.anilist-tmdb-anitube.v1")
                put("generatedAt", Instant.now().toString())
                put("sourceCatalog", "parseSearchResults.json")
                put("sourceCount", sourceCount)
                put("processedCount", processed.size)
                put("complete", complete)
                put("sampleSeed", sampleSeed ?: JSONObject.NULL)
                put("aniBridgeVersion", bridgeMeta?.optString("schema_version"))
                put("aniBridgeGeneratedAt", bridgeMeta?.optString("generated_on"))
                put("aniBridgeUrl", ANIBRIDGE_URL)
                put("fribbUrl", FRIBB_URL)
                put("statusCounts", JSONObject(counts))
            })
            put("items", JSONArray(processed))
        }
    }

    private suspend fun mapTitle(
        source: AnitubeSearchResult,
        bridge: JSONObject,
        fribb: ua.any2b.core.tmdb.FribbMappingIndex,
        reviewHint: String? = null,
    ): JSONObject {
        val manualLinks = ANILIST_URL.findAll(reviewHint.orEmpty()).toList()
        val manualIds = manualLinks.mapNotNull { it.groupValues.getOrNull(1)?.toIntOrNull() }.toSet()
        val hintedTitles = manualLinks.mapNotNull { match ->
            match.groupValues.getOrNull(2)?.substringBefore('?')?.substringBefore('#')
                ?.replace(Regex("[-_]+"), " ")?.replace(Regex("\\s+"), " ")?.trim()
                ?.takeIf(String::isNotBlank)
        }
        val queries = (listOf(source.title) + hintedTitles).map(::cleanTitle)
            .filter(String::isNotBlank).distinct()
        val query = queries.firstOrNull() ?: cleanTitle(source.title)
        val tmdbMatches = queries.flatMap { searchQuery ->
            searchCached(searchQuery).map { it to titleSimilarity(searchQuery, it) }
        }.groupBy { (candidate, _) -> candidate.type to candidate.id }
            .values.map { matches -> matches.maxBy { it.second } }
            .filter { it.second >= MIN_TITLE_SIMILARITY }
            .sortedByDescending { it.second }

        val directCandidates = queries.flatMap { searchQuery ->
            aniListSearchCache[searchQuery] ?: withAniListRetry {
                aniList.searchAnimeLookups(searchQuery, 20)
            }.also { aniListSearchCache[searchQuery] = it }
        }.distinctBy { it.id }.toMutableList()
        val missingManualIds = manualIds - directCandidates.map { it.id }.toSet()
        if (missingManualIds.isNotEmpty()) {
            directCandidates += withAniListRetry { aniList.animeLookups(missingManualIds.toList()) }
        }
        val directAniList = directCandidates.map { candidate ->
            candidate to if (candidate.id in manualIds) maxOf(aniListRank(source, candidate), MIN_ANILIST_SCORE)
            else aniListRank(source, candidate)
        }
            .filter { it.second >= MIN_ANILIST_SCORE }
            .sortedByDescending { it.second }

        val candidateIds = linkedSetOf<Int>()
        tmdbMatches.take(MAX_TMDB_CANDIDATES).forEach { (candidate, _) ->
            when (candidate.type) {
                TmdbMediaType.MOVIE -> bridgeTargets(bridge, "tmdb_movie:${candidate.id}")
                    .forEach { candidateIds += it.aniListId }
                TmdbMediaType.TV -> {
                    val details = details(candidate)
                    details.seasonEntries.forEach { season ->
                        if (season.number >= 0) bridgeTargets(bridge,
                                "tmdb_show:${candidate.id}:s${season.number}")
                            .forEach { candidateIds += it.aniListId }
                    }
                }
            }
        }
        directAniList.take(5).forEach { (anime, _) -> candidateIds += anime.id }
        val animeById = if (candidateIds.isEmpty()) emptyMap() else
            withAniListRetry { aniList.animeLookups(candidateIds.take(MAX_ANILIST_LOOKUPS).toList()) }
                .associateBy { it.id }

        val mappings = mutableListOf<JSONObject>()
        val unlinked = mutableListOf<JSONObject>()
        tmdbMatches.take(MAX_TMDB_CANDIDATES).forEach { (candidate, nameScore) ->
            when (candidate.type) {
                TmdbMediaType.MOVIE -> {
                    val targets = bridgeTargets(bridge, "tmdb_movie:${candidate.id}")
                    if (targets.isEmpty()) unlinked += unlinkedTmdbCandidate(candidate, nameScore, null)
                    targets.forEach { target ->
                        val anime = animeById[target.aniListId] ?: return@forEach
                        val fribbMatch = fribb.byAniList[anime.id].orEmpty().any {
                            it.type == candidate.type && it.id == candidate.id
                        }
                        mappings += mappingCandidate(source, candidate, null, nameScore,
                            null, null, target, anime, directAniList.any { it.first.id == anime.id },
                            fribbMatch)
                    }
                }
                TmdbMediaType.TV -> {
                    val details = details(candidate)
                    var mappedSeasons = 0
                    details.seasonEntries.forEach { season ->
                        val targets = bridgeTargets(bridge,
                            "tmdb_show:${candidate.id}:s${season.number}")
                        if (targets.isNotEmpty()) mappedSeasons++
                        targets.forEach { target ->
                            val anime = animeById[target.aniListId] ?: return@forEach
                            val fribbMatch = fribb.byAniList[anime.id].orEmpty().any {
                                it.type == candidate.type && it.id == candidate.id && it.season == season.number
                            }
                            mappings += mappingCandidate(source, candidate, season.number, nameScore,
                                season.year, season.episodeCount, target, anime,
                                directAniList.any { it.first.id == anime.id }, fribbMatch)
                        }
                    }
                    if (mappedSeasons == 0) unlinked += unlinkedTmdbCandidate(candidate, nameScore, details)
                }
            }
        }

        // Keep AniList-only candidates visible when either bridge direction or TMDB search is missing.
        directAniList.take(5).forEach { (anime, score) ->
            val existing = mappings.any { it.optInt("anilistId") == anime.id }
            if (!existing) {
                val bridgeTargets = bridgeTargets(bridge, "anilist:${anime.id}")
                bridgeTargets.forEach { target ->
                    val tmdbDescriptor = target.tmdbDescriptor ?: return@forEach
                    val descriptor = parseTmdbDescriptor(tmdbDescriptor) ?: return@forEach
                    val tmdbTitle = detailsCache[descriptor.type to descriptor.id]?.title
                    mappings += JSONObject().apply {
                        put("anilistId", anime.id)
                        put("anilistTitle", anime.title.display)
                        put("anilistRomaji", anime.title.romaji ?: JSONObject.NULL)
                        put("anilistYear", anime.seasonYear ?: JSONObject.NULL)
                        put("anilistEpisodes", anime.episodes ?: JSONObject.NULL)
                        put("anilistFormat", anime.format ?: JSONObject.NULL)
                        put("tmdbType", descriptor.type.path)
                        put("tmdbId", descriptor.id)
                        put("tmdbSeason", descriptor.season ?: JSONObject.NULL)
                        put("tmdbTitle", tmdbTitle?.title ?: JSONObject.NULL)
                        put("mappingSource", "anilist:${anime.id}")
                        put("mappingDirection", "anilist_to_tmdb")
                        put("episodeMappings", target.rangesJson())
                        put("evidence", JSONObject().apply {
                            put("aniListTitleScore", score)
                            put("directAniListSearch", true)
                            put("aniBridge", true)
                            put("fribbMatch", fribb.byAniList[anime.id].orEmpty().any {
                                it.type == descriptor.type && it.id == descriptor.id &&
                                    (descriptor.season == null || it.season == descriptor.season)
                            })
                            put("tmdbTitleMatched", false)
                        })
                        put("matchScore", score)
                    }
                }
            }
            if (mappings.none { it.optInt("anilistId") == anime.id }) {
                unlinked += JSONObject().apply {
                    put("anilistId", anime.id)
                    put("anilistTitle", anime.title.display)
                    put("anilistYear", anime.seasonYear ?: JSONObject.NULL)
                    put("anilistEpisodes", anime.episodes ?: JSONObject.NULL)
                    put("evidence", JSONObject().put("directAniListSearch", true))
                    put("matchScore", score)
                }
            }
        }

        val unique = mappings.distinctBy {
            "${it.optString("tmdbType")}:${it.optInt("tmdbId")}:" +
                "${it.opt("tmdbSeason")}:${it.optInt("anilistId")}"
        }.sortedByDescending { it.optDouble("matchScore") }
        val first = unique.firstOrNull()
        val second = unique.getOrNull(1)
        val wellSupported = first != null && first.optInt("evidenceCount") >= REQUIRED_SIGNALS
        val clearLead = second == null || first!!.optDouble("matchScore") - second.optDouble("matchScore") >= MIN_MATCH_MARGIN
        val status = when {
            first == null && unlinked.isEmpty() -> "not_found"
            first == null -> "needs_review"
            wellSupported && clearLead -> "matched"
            else -> "needs_review"
        }
        return JSONObject().apply {
            put("newsId", source.newsId)
            put("anitubeTitle", source.title)
            put("anitubeUrl", source.pageUrl)
            put("year", source.year ?: JSONObject.NULL)
            put("episodes", source.totalEpisodes ?: source.airedEpisodes ?: JSONObject.NULL)
            put("status", status)
            put("candidates", JSONArray((unique + unlinked).take(MAX_OUTPUT_CANDIDATES)))
            put("reason", when (status) {
                "not_found" -> "Немає кандидата з перехресним мапінгом TMDB ↔ AniList"
                "matched" -> "Найкращий кандидат має щонайменше $REQUIRED_SIGNALS узгоджені сигнали та чітку перевагу"
                else -> "Є кандидат, але бракує незалежних підтверджень або різниці з наступним кандидатом"
            })
        }
    }

    private suspend fun mappingCandidate(
        source: AnitubeSearchResult,
        tmdbTitle: TmdbTitle,
        seasonNumber: Int?,
        tmdbNameScore: Double,
        tmdbSeasonYear: Int?,
        tmdbSeasonEpisodes: Int?,
        target: BridgeTarget,
        anime: AniListAnimeLookup,
        directAniListSearch: Boolean,
        fribbMatch: Boolean,
    ): JSONObject {
        val sourceYear = source.year
        val sourceEpisodes = source.totalEpisodes ?: source.airedEpisodes
        val aniListNameScore = (listOfNotNull(anime.title.english, anime.title.romaji, anime.title.native)
            .plus(anime.synonyms).maxOfOrNull { stringSimilarity(source.title, it) } ?: 0.0)
        val yearMatch = sourceYear != null &&
            (anime.seasonYear?.let { abs(sourceYear - it) <= 1 } == true ||
                tmdbSeasonYear?.let { abs(sourceYear - it) <= 1 } == true)
        val episodeMatch = sourceEpisodes != null &&
            (anime.episodes?.let { abs(sourceEpisodes - it) <= 1 } == true ||
                tmdbSeasonEpisodes?.let { abs(sourceEpisodes - it) <= 1 } == true)
        val tmdbTitleMatch = tmdbNameScore >= 0.75
        val aniListTitleMatch = aniListNameScore >= 0.75
        val signals = listOf(tmdbTitleMatch, aniListTitleMatch, yearMatch, episodeMatch,
            directAniListSearch, fribbMatch).count { it }
        val explicitSeason = SeriesTitleSearch.seasonNumber(source.title)
        val isSpecial = Regex("(?i)\\b(?:ova|ова|special|спешл)\\b").containsMatchIn(source.title)
        val seasonPreference = when {
            explicitSeason != null && explicitSeason == seasonNumber -> 3.0
            explicitSeason != null -> 0.0
            isSpecial && seasonNumber == 0 -> 3.0
            !isSpecial && seasonNumber == 1 -> 3.0
            !isSpecial && seasonNumber == 0 -> -3.0
            else -> 0.0
        }
        val score = signals * 2.0 + maxOf(tmdbNameScore, aniListNameScore) + seasonPreference
        return JSONObject().apply {
            put("anilistId", anime.id)
            put("anilistTitle", anime.title.display)
            put("anilistRomaji", anime.title.romaji ?: JSONObject.NULL)
            put("anilistYear", anime.seasonYear ?: JSONObject.NULL)
            put("anilistEpisodes", anime.episodes ?: JSONObject.NULL)
            put("anilistFormat", anime.format ?: JSONObject.NULL)
            put("tmdbType", tmdbTitle.type.path)
            put("tmdbId", tmdbTitle.id)
            put("tmdbTitle", tmdbTitle.ukrainianTitle ?: tmdbTitle.title)
            put("tmdbSeason", seasonNumber ?: JSONObject.NULL)
            put("tmdbSeasonYear", tmdbSeasonYear ?: JSONObject.NULL)
            put("tmdbSeasonEpisodes", tmdbSeasonEpisodes ?: JSONObject.NULL)
            put("mappingSource", target.fromDescriptor)
            put("mappingDirection", "tmdb_to_anilist")
            put("episodeMappings", target.rangesJson())
            put("evidence", JSONObject().apply {
                put("tmdbTitleScore", tmdbNameScore)
                put("aniListTitleScore", aniListNameScore)
                put("yearMatch", yearMatch)
                put("episodeMatch", episodeMatch)
                put("directAniListSearch", directAniListSearch)
                put("fribbMatch", fribbMatch)
                put("aniBridge", true)
            })
            put("evidenceCount", signals)
            put("matchScore", score)
        }
    }

    private fun unlinkedTmdbCandidate(title: TmdbTitle, similarity: Double, details: TmdbDetails?): JSONObject =
        JSONObject().apply {
            put("tmdbType", title.type.path)
            put("tmdbId", title.id)
            put("tmdbTitle", title.ukrainianTitle ?: title.title)
            put("tmdbYear", title.year ?: JSONObject.NULL)
            put("tmdbSeasons", details?.seasonEntries?.let { seasons -> JSONArray().apply {
                seasons.forEach { season ->
                    put(JSONObject().put("number", season.number).put("year", season.year ?: JSONObject.NULL)
                        .put("episodes", season.episodeCount ?: JSONObject.NULL))
                }
            } } ?: JSONObject.NULL)
            put("evidence", JSONObject().put("tmdbTitleScore", similarity).put("aniBridge", false))
            put("matchScore", similarity)
        }

    private suspend fun details(title: TmdbTitle): TmdbDetails {
        val key = title.type to title.id
        detailsCache[key]?.let { return it }
        return tmdb.details(title.type, title.id, tmdbToken).also { detailsCache[key] = it }
    }

    private suspend fun searchCached(query: String): List<TmdbTitle> =
        searchCache[query] ?: tmdb.search(query, tmdbToken).also { searchCache[query] = it }

    private suspend fun downloadAniBridge(): JSONObject = downloadJson(ANIBRIDGE_URL)

    private suspend fun <T> withAniListRetry(block: suspend () -> T): T {
        var attempts = 0
        while (true) {
            try {
                return block()
            } catch (error: AniListException) {
                val waitSeconds = RETRY_SECONDS.find(error.message.orEmpty())
                    ?.groupValues?.getOrNull(1)?.toLongOrNull() ?: throw error
                if (attempts++ >= MAX_RATE_LIMIT_RETRIES) throw error
                println("AniList обмежив частоту запитів; повторю через ${waitSeconds + 1} с")
                delay((waitSeconds + 1) * 1_000)
            }
        }
    }

    private suspend fun downloadFribb(): String {
        val body = downloadText(FRIBB_URL)
        if (body.isBlank()) error("Fribb повернув порожній файл")
        return body
    }

    private fun bridgeTargets(bridge: JSONObject, descriptor: String): List<BridgeTarget> {
        val targets = bridge.optJSONObject(descriptor) ?: return emptyList()
        val keys = targets.keys()
        val result = mutableListOf<BridgeTarget>()
        while (keys.hasNext()) {
            val targetDescriptor = keys.next()
            val aniId = targetDescriptor.removePrefix("anilist:").takeIf { it != targetDescriptor }
                ?.toIntOrNull()
            if (aniId != null) {
                result += BridgeTarget(aniId, descriptor, null,
                    targets.optJSONObject(targetDescriptor) ?: JSONObject())
            } else if (descriptor.startsWith("anilist:")) {
                result += BridgeTarget(descriptor.substringAfter(':').toIntOrNull() ?: continue,
                    descriptor, targetDescriptor, targets.optJSONObject(targetDescriptor) ?: JSONObject())
            }
        }
        return result
    }

    private suspend fun downloadJson(url: String): JSONObject = JSONObject(downloadText(url))

    private suspend fun downloadText(url: String): String {
        val http = OkHttpClient.Builder().callTimeout(90, TimeUnit.SECONDS).build()
        return http.newCall(Request.Builder().url(url).header("User-Agent", "Any2B catalog mapper").build())
            .execute().use { response ->
                if (!response.isSuccessful) error("Завантаження $url: HTTP ${response.code}")
                response.body?.string() ?: error("Порожня відповідь: $url")
            }
    }

    private fun titleSimilarity(query: String, candidate: TmdbTitle): Double =
        candidate.searchNames(query).maxOfOrNull { stringSimilarity(query, it) } ?: 0.0

    private fun aniListRank(source: AnitubeSearchResult, candidate: AniListAnimeLookup): Double {
        val nameScore = (listOfNotNull(candidate.title.romaji, candidate.title.english, candidate.title.native) +
            candidate.synonyms).maxOfOrNull { stringSimilarity(source.title, it) } ?: 0.0
        val year = source.year?.let { y -> candidate.seasonYear?.let { if (abs(y - it) <= 1) 1.0 else -1.0 } } ?: 0.0
        val episodes = (source.totalEpisodes ?: source.airedEpisodes)?.let { n ->
            candidate.episodes?.let { if (abs(n - it) <= 1) 1.0 else -1.0 }
        } ?: 0.0
        return nameScore * 4 + year + episodes
    }

    private fun cleanTitle(title: String): String = title
        .replace(Regex("(?i)\\s*[\\[(][^\\])]*(?:сезон|season|сез|ова|ova|фільм|movie|film)[^\\])]*[\\])]"), " ")
        .let(SeriesTitleSearch::clean).trim()

    private fun stringSimilarity(first: String, second: String): Double {
        fun normalize(value: String) = value.lowercase()
            .replace(Regex("\\([^)]*\\)|\\[[^]]*]"), " ")
            .replace(Regex("[^a-z0-9а-яіїєґ ]"), " ")
            .replace(Regex("\\s+"), " ").trim()
        val a = normalize(first)
        val b = normalize(second)
        if (a.isBlank() || b.isBlank()) return 0.0
        if (a == b) return 1.0
        if (a in b || b in a) return 0.85
        val aa = a.split(' ').filter(String::isNotBlank).toSet()
        val bb = b.split(' ').filter(String::isNotBlank).toSet()
        return aa.intersect(bb).size.toDouble() / aa.union(bb).size
    }

    private fun parseTmdbDescriptor(descriptor: String): TmdbDescriptor? {
        val match = Regex("^(tmdb_show|tmdb_movie):(\\d+)(?::s(\\d+))?$").matchEntire(descriptor) ?: return null
        return TmdbDescriptor(
            if (match.groupValues[1] == "tmdb_show") TmdbMediaType.TV else TmdbMediaType.MOVIE,
            match.groupValues[2].toInt(), match.groupValues[3].toIntOrNull(),
        )
    }

    private data class TmdbDescriptor(val type: TmdbMediaType, val id: Int, val season: Int?)
    private data class BridgeTarget(
        val aniListId: Int,
        val fromDescriptor: String,
        val tmdbDescriptor: String?,
        val ranges: JSONObject,
    ) {
        fun rangesJson(): JSONArray = JSONArray().apply {
            val keys = ranges.keys()
            while (keys.hasNext()) {
                val sourceRange = keys.next()
                put(JSONObject().put("sourceRange", sourceRange)
                    .put("targetRange", ranges.opt(sourceRange) ?: JSONObject.NULL))
            }
        }
    }

    private companion object {
        val ANILIST_URL = Regex("(?i)https?://(?:www\\.)?anilist\\.co/anime/(\\d+)(?:/([^\\s?#]+))?")
        const val ANIBRIDGE_URL =
            "https://github.com/anibridge/anibridge-mappings/releases/latest/download/mappings.min.json"
        const val FRIBB_URL =
            "https://raw.githubusercontent.com/Fribb/anime-lists/master/anime-list-mini.json"
        const val MIN_TITLE_SIMILARITY = 0.55
        const val MIN_ANILIST_SCORE = 1.5
        const val MAX_TMDB_CANDIDATES = 5
        const val MAX_OUTPUT_CANDIDATES = 10
        const val REQUIRED_SIGNALS = 3
        const val MIN_MATCH_MARGIN = 2.0
        const val MAX_ANILIST_LOOKUPS = 50
        const val CHECKPOINT_INTERVAL = 10
        const val MAX_RATE_LIMIT_RETRIES = 8
        val RETRY_SECONDS = Regex("через\\s+(\\d+)\\s*с")
    }
}
