package ua.any2b.core.anilist

import ua.any2b.core.tmdb.TmdbSeason

/** A TMDB season and the AniList release known to represent it, when verified. */
data class NumberedSeason(val number: Int, val episodeCount: Int?, val animeId: Int?)

/** Only explicit TV sequel/prequel edges can supply another playable AniList season. */
fun AnimeDetails.numberedSeasons(tmdbSeasons: List<TmdbSeason>): List<NumberedSeason> {
    val seasons = tmdbSeasons.filter { it.number > 0 }.sortedBy { it.number }
    if (seasons.isEmpty() || format !in setOf("TV", "TV_SHORT")) return emptyList()

    fun seasonNumber(title: Title, year: Int?): Int? {
        val names = listOfNotNull(title.english, title.romaji)
        val explicit = names.firstNotNullOfOrNull { name ->
            Regex("(?i)(?:season|сезон)\\s*(\\d{1,2})|(?:\\b(\\d{1,2})(?:nd|rd|th)\\s+season)")
                .find(name)?.groupValues?.drop(1)?.firstNotNullOfOrNull { it.toIntOrNull() }
                ?: Regex("(?i)\\b(\\d{1,2})\\s*$").find(name)?.groupValues?.get(1)?.toIntOrNull()
        }
        if (explicit != null) return explicit.takeIf { number -> seasons.any { it.number == number } }
        return seasons.singleOrNull { it.year != null && it.year == year }?.number
            ?: if (seasons.first().year == year) seasons.first().number else null
    }

    val currentNumber = seasonNumber(title, seasonYear)
    fun baseNames(value: Title): Set<String> = listOfNotNull(value.english, value.romaji)
        .map { seriesBaseName(it).lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim() }
        .filter { it.length >= 4 }.toSet()
    val rootNames = baseNames(title)
    val known = mutableMapOf<Int, Int>()
    if (currentNumber != null) known[currentNumber] = id
    seasonRelations.forEach { relation ->
        if (relation.type != "PREQUEL" && relation.type != "SEQUEL") return@forEach
        val card = relation.card
        if (card.format !in setOf("TV", "TV_SHORT")) return@forEach
        if (rootNames.intersect(baseNames(card.title)).isEmpty()) return@forEach
        val number = seasonNumber(card.title, card.seasonYear) ?: return@forEach
        if (number != currentNumber) known.putIfAbsent(number, card.id)
    }
    return seasons.map { NumberedSeason(it.number, it.episodeCount, known[it.number]) }
}
