package ua.any2b.core.tmdb

import kotlin.math.abs
import kotlin.math.max
import ua.any2b.core.anilist.AnimeDetails
import ua.any2b.core.matcher.TitleNormalization

/** A shared TMDB ID can include specials and spin-offs; verify the actual AniList entry. */
object AniListRomajiSelector {
    fun select(details: TmdbDetails, candidates: List<AnimeDetails>): String? {
        val ranked = candidates.mapNotNull { anime ->
            if ((details.title.type == TmdbMediaType.MOVIE) !=
                anime.format.equals("MOVIE", ignoreCase = true)) return@mapNotNull null
            val runtime = details.runtimeMinutes
            val duration = anime.durationMinutes
            if (runtime != null && duration != null &&
                abs(runtime - duration) >= max(10, runtime / 2)) return@mapNotNull null
            val expectedYear = details.title.matchedSeasonYear ?: details.title.year
            if (expectedYear != null && anime.seasonYear != null &&
                abs(expectedYear - anime.seasonYear) > 1) return@mapNotNull null
            val tmdbNames = listOfNotNull(details.title.englishTitle, details.title.originalTitle,
                details.title.title, details.title.matchedSeasonTitle)
                .map(TitleNormalization::unicodeWords).filter(String::isNotBlank)
            val animeNames = listOfNotNull(anime.title.english, anime.title.romaji,
                anime.title.native).map(TitleNormalization::unicodeWords)
                .filter(String::isNotBlank)
            val similarity = animeNames.maxOfOrNull { name ->
                tmdbNames.maxOfOrNull { tmdb ->
                    when {
                        name == tmdb -> 1.0
                        name.length >= 8 && tmdb.length >= 8 &&
                            (name in tmdb || tmdb in name) ->
                            minOf(name.length, tmdb.length).toDouble() /
                                maxOf(name.length, tmdb.length)
                        else -> 0.0
                    }
                } ?: 0.0
            } ?: 0.0
            if (similarity < 0.75) return@mapNotNull null
            val romaji = anime.title.romaji?.trim()?.takeIf(String::isNotBlank)
                ?: return@mapNotNull null
            val yearBonus = if (expectedYear != null && expectedYear == anime.seasonYear) 1.0 else 0.0
            romaji to (similarity * 4 + yearBonus)
        }.sortedByDescending { it.second }
        val best = ranked.firstOrNull() ?: return null
        if (ranked.getOrNull(1)?.let { best.second - it.second < 1.5 } == true) return null
        return best.first
    }
}
