package ua.any2b.core.anitube

import ua.any2b.core.extractor.StreamResolver

private val extraTitle = Regex("спешл|special|трейлер|trailer|тизер", RegexOption.IGNORE_CASE)
private val movieTitle = Regex("фільм|фильм|movie|повнометраж", RegexOption.IGNORE_CASE)
private val seriesTitle = Regex("серія|серии|епізод|episode", RegexOption.IGNORE_CASE)

/** Leaves one playable source per episode and one title per voice for movies. */
fun playableVoices(
    voices: List<AnitubeVoice>,
    format: String?,
    resolver: StreamResolver = StreamResolver(),
): List<AnitubeVoice> = voices.mapNotNull { voice ->
    val playable = voice.episodes.filter { resolver.canHandle(it.fileUrl) }
    if (playable.isEmpty()) return@mapNotNull null

    val selected = if (format.equals("MOVIE", ignoreCase = true)) {
        val mainTitles = playable.filterNot { extraTitle.containsMatchIn(it.title) || seriesTitle.containsMatchIn(it.title) }
        val movie = mainTitles.firstOrNull { movieTitle.containsMatchIn(it.title) }
            ?: mainTitles.firstOrNull()
            ?: playable.singleOrNull()
            ?: return@mapNotNull null
        listOf(movie.copy(episodeNumber = 1))
    } else {
        val mainEpisodes = playable.filterNot { extraTitle.containsMatchIn(it.title) || movieTitle.containsMatchIn(it.title) }
            .ifEmpty { playable }
        mainEpisodes.distinctBy { it.episodeNumber }.sortedBy { it.episodeNumber }
    }
    voice.copy(episodes = selected)
}
