package ua.any2b.core.matcher

/** Shared clean and season-qualified title queries for metadata and torrent lookups. */
object SeriesTitleSearch {
    private val seasonMarker = Regex(
        "(?i)(?:\\bseason\\s*(\\d{1,2})|\\bсезон\\s*(\\d{1,2})|\\bсез\\s*(\\d{1,2})|(?<![A-Z0-9])S(\\d{1,2})(?:E\\d{1,3})?|\\b(\\d{1,2})(?:st|nd|rd|th)\\s+season|\\b(\\d{1,2})\\s+сезон)"
    )
    private val trailingSeason = Regex(
        "(?i)[\\s:–—-]*(?:season\\s*\\d{1,2}|сезон\\s*\\d{1,2}|сез\\s*\\d{1,2}|S\\d{1,2}(?:E\\d{1,3})?|\\d{1,2}(?:st|nd|rd|th)\\s+season|\\d{1,2}\\s+сезон)(?:\\s*[-:–—]?\\s*episode\\s*\\d{1,3})?\\s*$"
    )

    fun seasonNumber(title: String): Int? = seasonMarker.find(title)?.groupValues
        ?.drop(1)?.firstNotNullOfOrNull(String::toIntOrNull)

    fun clean(title: String): String = trailingSeason.replace(title.trim(), "")
        .trim().trimEnd(':', '：', '-', '–', '—').trim()

    /** Numbered service spellings first, followed by the clean franchise title. */
    fun fanartQueries(title: String): List<String> {
        val original = title.trim()
        if (original.isBlank()) return emptyList()
        val base = clean(original)
        val season = seasonNumber(original)
        if (season == null || base.isBlank()) return listOf(original).distinct()
        val padded = season.toString().padStart(2, '0')
        return listOf(
            original,
            "$base Season $season",
            "$base S$padded",
            "$base S$season",
            "$base ${season} сезон",
            base,
        ).distinctBy(String::lowercase)
    }
}
