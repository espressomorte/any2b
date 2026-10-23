package ua.any2b.tvplayer.anilist

/** Назви тайтла в AniList. Romaji знадобиться пізніше для пошуку на anitube. */
data class Title(
    val romaji: String?,
    val english: String?,
    val native: String?,
) {
    /** Що показуємо на картці. */
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

/** Один горизонтальний ряд на головній. */
data class HomeRow(
    val id: String,
    val title: String,
    val items: List<AnimeCard>,
)

class AniListException(message: String) : Exception(message)
