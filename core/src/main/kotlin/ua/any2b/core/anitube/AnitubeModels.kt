package ua.any2b.core.anitube

/** Результат пошуку з AniTube. */
data class AnitubeSearchResult(
    val newsId: Int,
    val title: String,
    val pageUrl: String,
    val posterUrl: String? = null,
)

/** Серія конкретної студії озвучення та плеєра. */
data class AnitubeEpisode(
    val id: String,
    val episodeNumber: Int,        // 1, 2, 3...
    val title: String,             // напр. "1 серія"
    val fileUrl: String,           // посилання на fenixplay або ashdi
    val playerName: String,        // "ПЛЕЄР ASHDI", "ПЛЕЄР FENIX" тощо
)

/** Студія або варіант озвучення / субтитрів (напр. "FanVoxUA", "Amanogawa", "Рідний Голос"). */
data class AnitubeVoice(
    val category: String,          // "ОЗВУЧЕННЯ" або "СУБТИТРИ"
    val name: String,              // "FanVoxUA"
    val episodes: List<AnitubeEpisode>,
) {
    val displayName: String get() = if (category.isNotBlank() && category != "ОЗВУЧЕННЯ") "$name ($category)" else name
}
