package ua.any2b.core.toloka

/** Searchable title parts from a manually confirmed torrent release. */
object TolokaTitleAliases {
    private val separator = Regex("\\s+[|/]\\s+|\\s+[—–]\\s+")
    private val scriptBoundary = Regex(
        "(?<=\\p{IsCyrillic})\\s+(?=[A-Za-z])|(?<=[A-Za-z])\\s+(?=\\p{IsCyrillic})"
    )
    private val year = Regex("\\s*\\b(?:19|20)\\d{2}\\b\\s*$")

    fun extract(releaseTitle: String): Map<String, List<String>> {
        val clean = TolokaReleaseMetadataParser.parse(releaseTitle).cleanTitle
        val parts = clean.split(separator).flatMap { it.split(scriptBoundary) }.map { part ->
            part.replace(year, "").trim(' ', '-', '–', '—', ':', ';', ',')
                .replace(Regex("\\s+"), " ")
        }.filter { part -> part.count(Char::isLetter) >= 3 }
            .distinctBy(String::lowercase)
        return parts.groupBy { part ->
            when {
                part.any { it in '\u3040'..'\u30ff' || it in '\u4e00'..'\u9fff' } -> "japanese"
                part.any { it in '\u0400'..'\u04ff' } -> "ukrainian"
                else -> "latin"
            }
        }
    }
}
