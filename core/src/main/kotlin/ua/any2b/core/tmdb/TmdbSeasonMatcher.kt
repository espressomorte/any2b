package ua.any2b.core.tmdb

import ua.any2b.core.anilist.AnimeDetails

/** TMDB may store an AniList sequel as a later season of a series with an older start year. */
object TmdbSeasonMatcher {
    fun shouldInspect(anime: AnimeDetails, candidate: TmdbTitle): Boolean {
        if (candidate.type != TmdbMediaType.TV || candidate.year == anime.seasonYear) return false
        val animeTitle = anime.title.english ?: anime.title.romaji ?: return false
        val base = normalize(animeTitle.replace(sequelNumber, ""))
        val candidateTitle = normalize(candidate.englishTitle ?: candidate.title)
        return base.length >= 5 && base == candidateTitle
    }

    fun enrich(anime: AnimeDetails, title: TmdbTitle, seasons: List<TmdbSeason>): TmdbTitle {
        if (title.type != TmdbMediaType.TV || anime.seasonYear == null) return title
        val animeSeason = listOfNotNull(anime.title.english, anime.title.romaji)
            .firstNotNullOfOrNull { name ->
                sequelNumber.find(name)?.groupValues?.get(1)?.toIntOrNull()
            }
        val season = seasons.firstOrNull {
            it.year == anime.seasonYear && it.number > 0 &&
                (animeSeason == null || it.number == animeSeason)
        } ?: return title
        return title.copy(matchedSeasonTitle = season.name, matchedSeasonYear = season.year)
    }

    private val sequelNumber = Regex("(?i)(?:season\\s*)?(\\d{1,2})\\s*$")
    private fun normalize(value: String): String = value.lowercase()
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()
}
