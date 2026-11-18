package ua.any2b.domain.playback.toloka

import kotlin.test.Test
import kotlin.test.assertEquals
import java.net.ConnectException

class TolokaWatchlistPlaybackTest {
    private val playback = TolokaWatchlistPlayback()

    @Test
    fun waitsForTorrentFileListBeforeResolvingSavedOrMatchedEpisode() {
        assertEquals(TolokaFileDecision.Wait,
            playback.resolveFile(false, savedFileId = 7, savedFileAvailable = true,
                episodeFileId = 9))
    }

    @Test
    fun savedFileWinsThenEpisodeMatchIsFallback() {
        assertEquals(TolokaFileDecision.Start(7),
            playback.resolveFile(true, 7, savedFileAvailable = true, episodeFileId = 9))
        assertEquals(TolokaFileDecision.Start(9),
            playback.resolveFile(true, 7, savedFileAvailable = false, episodeFileId = 9))
        assertEquals(TolokaFileDecision.Missing,
            playback.resolveFile(true, null, savedFileAvailable = false, episodeFileId = null))
    }

    @Test
    fun torrServerErrorsAreClassifiedForUi() {
        assertEquals("Не вдалося встановити з’єднання з TorrServer. Перевірте адресу та чи запущений сервер.",
            playback.classifyServerError(ConnectException("offline")))
        assertEquals("TorrServer відхилив авторизацію (HTTP 401). Перевірте логін і пароль.",
            playback.classifyServerError(IllegalStateException("помилка авторизації")))
    }

    @Test
    fun episodeContextPrefersFileMetadataAndUsesPlaylistFallbacks() {
        assertEquals(TolokaEpisodeContext(4, 12), playback.episodeContext(
            fileSeason = 4, fileEpisodeStart = 12, fileEpisodeEnd = null,
            folderSeason = 2, requestedSeason = 3, requestedEpisode = 8,
            matchedSeason = 1, isTv = true,
            mappedEpisodeForSeason = { 14 }))
        assertEquals(TolokaEpisodeContext(1, 14), playback.episodeContext(
            fileSeason = null, fileEpisodeStart = null, fileEpisodeEnd = null,
            folderSeason = null, requestedSeason = 3, requestedEpisode = null,
            matchedSeason = 1, isTv = true,
            mappedEpisodeForSeason = { 14 }))
    }
}
