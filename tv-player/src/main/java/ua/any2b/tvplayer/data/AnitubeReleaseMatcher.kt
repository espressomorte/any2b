package ua.any2b.tvplayer.data

import ua.any2b.core.anilist.AniListAnimeLookup
import ua.any2b.core.anilist.AnimeDetails
import ua.any2b.core.anilist.Title
import ua.any2b.core.anitube.AnitubeSearchResult
import ua.any2b.core.matcher.TitleMatcher
import kotlin.math.abs

internal val ANITUBE_CHAIN_RELATIONS = setOf("PREQUEL", "SEQUEL")

internal data class RankedAnitubeCandidate(
    val aniListId: Int,
    val score: Double,
    val titleSimilarity: Double,
    val metadataMatches: Int,
    val conflictingMetadata: Boolean,
    val format: String?,
)

/** Pure AniTube name preparation and release-to-anime scoring rules. */
internal class AnitubeReleaseMatcher {
    fun prioritizedSearchNames(
        preferredNames: List<String>,
        allNames: List<String>,
        tmdbNames: List<String>,
    ): List<String> = buildList {
        // AniList's canonical titles and synonyms lead; TMDB names serve as aliases.
        (preferredNames + allNames + tmdbNames).forEach { name ->
            val cleaned = cleanSearchName(name)
            add(cleaned)
            val base = stripSeasonSuffix(cleaned)
            if (base != cleaned) add(base)
        }
    }.map(String::trim).filter(String::isNotBlank)
        .distinctBy(String::lowercase)
        .take(MAX_SEARCH_NAMES)

    fun bestMatch(
        release: AnitubeSearchResult,
        anime: Collection<AniListAnimeLookup>,
        tmdbNames: List<String>,
    ): RankedAnitubeCandidate? = anime.mapNotNull { entry ->
        val details = entry.asDetails()
        TitleMatcher.match(details, listOf(release), tmdbNames).firstOrNull()?.let { match ->
            RankedAnitubeCandidate(entry.id, match.score, match.titleSimilarity,
                match.metadataMatches, match.conflictingMetadata, entry.format)
        }
    }.maxByOrNull(RankedAnitubeCandidate::score)

    fun orderSeasonChain(
        entries: List<AniListAnimeLookup>,
        all: Map<Int, AniListAnimeLookup>,
    ): List<AniListAnimeLookup> {
        val ids = entries.map(AniListAnimeLookup::id).toSet()
        val outgoing = ids.associateWith { mutableSetOf<Int>() }.toMutableMap()
        val indegree = ids.associateWith { 0 }.toMutableMap()
        all.values.forEach { current ->
            current.relations.filter { it.type in ANITUBE_CHAIN_RELATIONS }.forEach { relation ->
                val relatedId = relation.anime.id
                if (current.id !in ids || relatedId !in ids) return@forEach
                val (before, after) = when (relation.type) {
                    "PREQUEL" -> relatedId to current.id
                    else -> current.id to relatedId
                }
                if (outgoing.getValue(before).add(after))
                    indegree[after] = indegree.getValue(after) + 1
            }
        }
        val byId = entries.associateBy(AniListAnimeLookup::id)
        val ready = entries.filter { indegree[it.id] == 0 }.toMutableList()
        val ordered = mutableListOf<AniListAnimeLookup>()
        fun dateKey(anime: AniListAnimeLookup) = anime.startDate ?: anime.seasonYear?.toString().orEmpty()
        while (ready.isNotEmpty()) {
            ready.sortWith(compareBy<AniListAnimeLookup>({ dateKey(it) }, { it.id }))
            val current = ready.removeAt(0)
            ordered += current
            outgoing.getValue(current.id).forEach { next ->
                indegree[next] = indegree.getValue(next) - 1
                if (indegree[next] == 0) ready += byId.getValue(next)
            }
        }
        if (ordered.size < entries.size) {
            ordered += (entries - ordered.toSet()).sortedWith(compareBy({ dateKey(it) }, { it.id }))
        }
        return ordered
    }

    fun assignSeasonNumbers(
        ordered: List<AniListAnimeLookup>,
        mappedSeasonNumbers: Map<Int, Int?>,
    ): Map<Int, Int> {
        val anchors = ordered.mapIndexedNotNull { index, anime ->
            (mappedSeasonNumbers[anime.id]?.takeIf { it > 0 }
                ?: explicitSeason(anime.title, anime.synonyms))?.let { index to it }
        }
        val yearNumbers = ordered.mapNotNull(AniListAnimeLookup::seasonYear).distinct().sorted()
            .mapIndexed { index, year -> year to index + 1 }.toMap()
        return ordered.mapIndexed { index, anime ->
            val sameYearAnchor = anime.seasonYear?.let { year ->
                anchors.filter { (anchorIndex, _) -> ordered[anchorIndex].seasonYear == year }
                    .minByOrNull { abs(it.first - index) }
            }
            val prior = anchors.lastOrNull { it.first <= index }
            val following = anchors.firstOrNull { it.first > index }
            val number = when {
                sameYearAnchor != null -> sameYearAnchor.second
                prior != null -> prior.second + index - prior.first
                following != null -> following.second - (following.first - index)
                else -> anime.seasonYear?.let(yearNumbers::get) ?: (index + 1)
            }.coerceAtLeast(1)
            anime.id to number
        }.toMap()
    }

    fun explicitSeason(title: String, synonyms: List<String> = emptyList()): Int? =
        (listOf(title) + synonyms).firstNotNullOfOrNull { name ->
            Regex("(?i)(?:\\bseason\\s*|\\bсезон\\s*|\\bs)(\\d{1,2})\\b|\\b(\\d{1,2})(?:st|nd|rd|th)\\s+season")
                .find(name)?.groupValues?.drop(1)?.firstNotNullOfOrNull(String::toIntOrNull)
        }

    fun explicitSeason(title: Title, synonyms: List<String>): Int? = explicitSeason(
        listOfNotNull(title.english, title.romaji, title.native).firstOrNull().orEmpty(),
        synonyms + listOfNotNull(title.english, title.romaji, title.native),
    )

    private fun cleanSearchName(value: String): String = value
        .replace(Regex("(?i)(?<![\\p{L}\\p{N}])(?:movie|film|фільм|фильм|кіно)(?![\\p{L}\\p{N}])"), " ")
        .replace(Regex("\\s+"), " ").trim().trim(':', '-', '–', '—', '.', ' ')

    private fun stripSeasonSuffix(name: String) = name.replace(
        Regex("(?i)[\\s:,-]*(?:season\\s*\\d+|сезон\\s*\\d+|s\\d+|\\d+(?:st|nd|rd|th)\\s+season).*$"), "",
    ).trim()

    private fun AniListAnimeLookup.asDetails() = AnimeDetails(
        id = id,
        title = title,
        cover = null,
        banner = null,
        description = null,
        score = null,
        episodes = episodes,
        durationMinutes = durationMinutes,
        format = format,
        status = status,
        seasonYear = seasonYear,
        genres = emptyList(),
        studios = emptyList(),
        synonyms = synonyms,
    )

    private companion object {
        const val MAX_SEARCH_NAMES = 16
    }
}
