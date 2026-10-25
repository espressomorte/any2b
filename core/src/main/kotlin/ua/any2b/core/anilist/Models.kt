package ua.any2b.core.anilist

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
    val recommendations: List<AnimeCard> = emptyList(),
    val recommendationsHasMore: Boolean = false,
)

data class AnimeCardPage(val items: List<AnimeCard>, val hasNextPage: Boolean)

/** Один горизонтальний ряд на головній. */
data class HomeRow(
    val id: String,
    val title: String,
    val items: List<AnimeCard>,
)

class AniListException(message: String) : Exception(message)
