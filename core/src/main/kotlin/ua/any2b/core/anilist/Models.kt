package ua.any2b.core.anilist

import java.net.URLEncoder

/** Назви тайтла в AniList. Romaji використовується для пошуку на AniTube. */
data class Title(
    val romaji: String?,
    val english: String?,
    val native: String?,
) {
    /** Що показуємо на картці або екрані деталей. */
    val display: String get() = english ?: romaji ?: native ?: "—"
}

/** Мінімальні дані для картки в рядку каталогу. */
data class AnimeCard(
    val id: Int,
    val title: Title,
    val cover: String?,
    val score: Int?,          // averageScore, 0..100
    val episodes: Int?,
    val format: String?,      // TV, MOVIE, OVA ...
    val status: String?,      // RELEASING, FINISHED, NOT_YET_RELEASED ...
    val seasonYear: Int?,
    val nextEpisode: Int?,    // номер найближчої серії, якщо виходить зараз
)

/** Повні деталі тайтла для екрана інформації. */
data class AnimeDetails(
    val id: Int,
    val title: Title,
    val cover: String?,
    val banner: String?,
    val description: String?,
    val score: Int?,
    val episodes: Int?,
    val durationMinutes: Int?,
    val format: String?,
    val status: String?,
    val seasonYear: Int?,
    val genres: List<String>,
    val studios: List<String>,
    val related: List<AnimeCard> = emptyList(),
    val seasonRelations: List<AnimeRelation> = emptyList(),
    val recommendations: List<AnimeCard> = emptyList(),
    val recommendationsHasMore: Boolean = false,
    val trailer: MediaTrailer? = null,
    val synonyms: List<String> = emptyList(),
    val nextEpisode: Int? = null,
)

/** Explicit AniList sequel/prequel edge. Other relations stay in Related. */
data class AnimeRelation(val type: String, val card: AnimeCard)

/** AniList currently provides YouTube and Dailymotion trailer IDs. */
data class MediaTrailer(val id: String, val site: String) {
    fun videoUrl(): String? = when {
        site.equals("youtube", ignoreCase = true) && id.matches(Regex("[A-Za-z0-9_-]{11}")) ->
            "https://www.youtube.com/watch?v=$id"
        site.equals("dailymotion", ignoreCase = true) && id.matches(Regex("[A-Za-z0-9]+")) ->
            "https://www.dailymotion.com/video/$id"
        else -> null
    }
}

fun AnimeDetails.trailerSearchUrl(): String {
    val name = listOf(title.english, title.romaji, title.native)
        .firstOrNull { !it.isNullOrBlank() } ?: title.display
    val query = listOfNotNull(name, seasonYear?.toString(), "trailer").joinToString(" ")
    return "https://www.youtube.com/results?search_query=${URLEncoder.encode(query, "UTF-8")}"
}

data class AnimeCardPage(val items: List<AnimeCard>, val hasNextPage: Boolean)

/** Один горизонтальний ряд на головній. */
data class HomeRow(
    val id: String,
    val title: String,
    val items: List<AnimeCard>,
)

class AniListException(message: String) : Exception(message)
