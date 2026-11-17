package ua.any2b.tvplayer.data

import ua.any2b.core.tmdb.TmdbMediaType

/** Toloka-only file resolution rules for a watchlist playback request. */
internal class TolokaWatchlistPlayback {
    fun episodeContext(
        fileSeason: Int?,
        fileEpisodeStart: Int?,
        fileEpisodeEnd: Int?,
        folderSeason: Int?,
        requestedSeason: Int,
        requestedEpisode: Int?,
        matchedSeason: Int?,
        mediaType: TmdbMediaType?,
        mappedEpisodeForSeason: (Int) -> Int?,
    ): TolokaEpisodeContext {
        val singleFileEpisode = fileEpisodeStart?.takeIf {
            fileEpisodeEnd == null || fileEpisodeEnd == it
        }
        val season = fileSeason ?: folderSeason
            ?: requestedSeason.takeIf { requestedEpisode != null }
            ?: matchedSeason
            ?: 1.takeIf { mediaType == TmdbMediaType.TV }
        val episode = singleFileEpisode ?: requestedEpisode
            ?: season?.let(mappedEpisodeForSeason)
        return TolokaEpisodeContext(season, episode)
    }

    fun resolveFile(
        torrentFilesReady: Boolean,
        savedFileId: Int?,
        savedFileAvailable: Boolean,
        episodeFileId: Int?,
    ): TolokaFileDecision {
        if (!torrentFilesReady) return TolokaFileDecision.Wait
        savedFileId?.takeIf { it > 0 && savedFileAvailable }?.let {
            return TolokaFileDecision.Start(it)
        }
        episodeFileId?.takeIf { it > 0 }?.let { return TolokaFileDecision.Start(it) }
        return TolokaFileDecision.Missing
    }

    fun classifyServerError(error: Throwable): String = when {
        error is IllegalArgumentException -> "Перевірте адресу TorrServer у налаштуваннях."
        error is IllegalStateException && error.message?.contains("авторизац", ignoreCase = true) == true ->
            "TorrServer відхилив авторизацію (HTTP 401). Перевірте логін і пароль."
        error is IllegalStateException -> error.message ?: "TorrServer повернув помилку."
        error is java.io.InterruptedIOException ->
            "TorrServer не відповів за 8 секунд. Перевірте, чи сервер запущений."
        error is java.net.ConnectException ->
            "Не вдалося встановити з’єднання з TorrServer. Перевірте адресу та чи запущений сервер."
        else -> "Не вдалося з’єднатися з TorrServer. Перевірте, чи сервер запущений і доступний за цією адресою."
    }
}

internal data class TolokaEpisodeContext(val season: Int?, val episode: Int?)

internal sealed interface TolokaFileDecision {
    data object Wait : TolokaFileDecision
    data object Missing : TolokaFileDecision
    data class Start(val fileId: Int) : TolokaFileDecision
}
