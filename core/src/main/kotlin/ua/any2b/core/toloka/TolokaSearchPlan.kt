package ua.any2b.core.toloka

/** Prefer AniList romaji for anime, then aliases learned from confirmed Toloka releases. */
object TolokaSearchPlan {
    fun primary(romaji: String?, confirmed: List<String>, ukrainian: String?,
        english: String?, fallback: String): String =
        (listOfNotNull(romaji) + confirmed + listOfNotNull(ukrainian, english, fallback))
            .firstOrNull { it.isNotBlank() }?.trim().orEmpty()

    fun fallbacks(primary: String, romaji: String?, confirmed: List<String>,
        ukrainian: String?, english: String?): List<String> =
        (listOfNotNull(romaji) + confirmed + listOfNotNull(ukrainian, english))
            .flatMap { name -> listOf(name) + shortenedSearchNames(name) }
            .map(String::trim).filter(String::isNotBlank)
            .distinctBy(String::lowercase)
            .filterNot { it.equals(primary, ignoreCase = true) }
}
