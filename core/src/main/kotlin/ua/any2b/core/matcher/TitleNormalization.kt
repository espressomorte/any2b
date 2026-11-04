package ua.any2b.core.matcher

/** Shared normalization primitives for title matching across AniList, TMDB, and AniTube. */
object TitleNormalization {
    private val bracketedContent = Regex("""\(.*?\)|\[.*?\]""")
    private val latinCyrillicNoise = Regex("[^a-z0-9а-яіїєґ ]")
    private val unicodeNoise = Regex("[^\\p{L}\\p{N}]+")
    private val whitespace = Regex("\\s+")

    /** Matches the historical AniTube/AniList ranking rules. */
    fun latinCyrillic(value: String, removeBracketedContent: Boolean = false): String {
        val source = if (removeBracketedContent) bracketedContent.replace(value, " ") else value
        return whitespace.replace(latinCyrillicNoise.replace(source.lowercase(), " "), " ").trim()
    }

    /** Keeps letters and numbers from every script, as used when matching TMDB season names. */
    fun unicodeWords(value: String): String =
        unicodeNoise.replace(value.lowercase(), " ").trim()
}
