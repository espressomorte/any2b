package ua.any2b.core.anilist

/** Only numbered TV seasons share a catalog tile. Movies, OVA and specials keep their own IDs. */
fun groupNumberedTvSeasons(cards: List<AnimeCard>): List<AnimeCard> {
    val groups = linkedMapOf<String, MutableList<AnimeCard>>()
    cards.forEach { card ->
        val key = if (card.format == "TV" || card.format == "TV_SHORT") {
            val base = seriesBaseName(card.title.english ?: card.title.romaji ?: card.title.native.orEmpty())
            "series:${base.lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()}"
        } else "release:${card.id}"
        groups.getOrPut(key) { mutableListOf() }.add(card)
    }
    return groups.values.flatMap { group ->
        if (group.first().format !in setOf("TV", "TV_SHORT") ||
            group.none { hasNumberedSeasonSuffix(it.title.display) }) {
            group
        } else {
            val representative = group.filter { it.status == "RELEASING" }
                .maxWithOrNull(compareBy<AnimeCard> { it.seasonYear ?: 0 }.thenBy { it.id })
                ?: group.minWithOrNull(compareBy<AnimeCard> { it.seasonYear ?: Int.MAX_VALUE }.thenBy { it.id })!!
            listOf(representative.copy(title = Title(
                representative.title.romaji?.let(::seriesBaseName),
                representative.title.english?.let(::seriesBaseName),
                representative.title.native?.let(::seriesBaseName),
            )))
        }
    }
}

private val seasonSuffix = Regex(
    "(?i)(?:[\\s:–-]+(?:season\\s*\\d{1,2}|\\d{1,2}(?:st|nd|rd|th)\\s+season|\\d{1,2}\\s*сезон)|\\s*\\(\\s*\\d{1,2}\\s*сезон\\s*\\)|\\s+\\d{1,2})$"
)

fun hasNumberedSeasonSuffix(name: String): Boolean = seasonSuffix.containsMatchIn(name.trim())

fun seriesBaseName(name: String): String = name.trim().replace(seasonSuffix, "").trim()
