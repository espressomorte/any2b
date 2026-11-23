package ua.any2b.core.toloka

/** Searchable title parts from a manually confirmed torrent release. */
object TolokaTitleAliases {
    private val scriptBoundary = Regex(
        "(?<=\\p{IsCyrillic})\\s+(?=[A-Za-z])|(?<=[A-Za-z])\\s+(?=\\p{IsCyrillic})"
    )

    fun extract(releaseTitle: String): Map<String, List<String>> {
        val parts = TolokaReleaseMetadataParser.parse(releaseTitle).titleCandidates
            .flatMap { it.split(scriptBoundary) }.map { it.trim(' ', '-', '–', '—', ':', ';', ',') }
            .filter { it.count(Char::isLetter) >= 3 }.distinctBy(String::lowercase)
        return parts.groupBy { part ->
            when {
                part.any { it in '\u3040'..'\u30ff' || it in '\u4e00'..'\u9fff' } -> "japanese"
                part.any { it in '\u0400'..'\u04ff' } -> "ukrainian"
                else -> "latin"
            }
        }
    }
}
