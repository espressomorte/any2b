package ua.any2b.tvplayer.data

import org.junit.Assert.assertEquals
import org.junit.Test
import ua.any2b.core.anitube.AnitubeEpisode
import ua.any2b.core.anitube.AnitubeSearchResult
import ua.any2b.core.anitube.AnitubeVoice
import ua.any2b.core.tmdb.TmdbMediaType
import ua.any2b.core.tmdb.TmdbTitle

class AniTubeWatchlistPlaybackTest {
    @Test
    fun searchErrorFallsBackToSavedReleaseOnlyOnce() {
        val coordinator = AniTubeWatchlistPlayback()
        val request = request(savedReleaseId = 42, pageUrl = "https://example.test/42")

        assertEquals(AniTubeWatchlistAction.LoadSavedRelease(42, 3,
            "https://example.test/42"),
            coordinator.onWatchlistState(request, AnitubeState.Error("search failed")))
        assertEquals(AniTubeWatchlistAction.Failed("direct failed"),
            coordinator.onWatchlistState(request, AnitubeState.Error("direct failed")))
    }

    @Test
    fun missingSavedVoiceFailsWithoutLaunching() {
        val coordinator = AniTubeWatchlistPlayback()
        val state = found(voiceName = "Other")

        assertEquals(AniTubeWatchlistAction.Failed(
            "Збережену озвучку AniTube більше не знайдено"),
            coordinator.onWatchlistState(request(savedVoiceName = "Далі Буде"), state))
    }

    @Test
    fun missingRequestedEpisodeFailsWithoutLaunching() {
        val coordinator = AniTubeWatchlistPlayback()

        assertEquals(AniTubeWatchlistAction.Failed(
            "У вибраній озвучці не знайдено цю серію"),
            coordinator.onWatchlistState(request(), found(episodeNumber = 1)))
    }

    @Test
    fun repeatedFoundEventProducesOnlyOnePlaybackSelection() {
        val coordinator = AniTubeWatchlistPlayback()
        val request = request()
        val state = found()

        assertEquals(AniTubeWatchlistAction.SelectPlayback(0, 1, null),
            coordinator.onWatchlistState(request, state))
        assertEquals(AniTubeWatchlistAction.None, coordinator.onWatchlistState(request, state))
    }

    @Test
    fun playbackSeasonMappingKeepsTmdbAndAnimeSeasonNumbersSeparate() {
        val title = TmdbTitle(8, TmdbMediaType.TV, "Series", null, 2020,
            null, null, null, matchedSeasonNumber = 2, matchedAnimeSeasonNumber = 6)
        assertEquals(AniTubePlaybackSeasons(tmdbSeason = 2, animeSeason = 5),
            AniTubeWatchlistPlayback().playbackSeasons(title, requestedTmdbSeason = 3,
                requestedAnimeSeason = 5, releaseId = 42, found = null))
    }

    private fun request(
        savedReleaseId: Int? = null,
        pageUrl: String? = null,
        savedVoiceName: String? = null,
    ) = AniTubeWatchlistRequest(
        mediaType = TmdbMediaType.TV,
        tmdbSeason = 3,
        animeSeason = 3,
        episodeNumber = 2,
        savedReleaseId = savedReleaseId,
        savedReleasePageUrl = pageUrl,
        savedVoiceIndex = 0,
        savedVoiceName = savedVoiceName,
        savedProgress = null,
    )

    private fun found(
        voiceName: String = "Далі Буде",
        episodeNumber: Int = 2,
    ) = AnitubeState.Found(
        release = AnitubeSearchResult(42, "Series", "https://example.test/42"),
        voices = listOf(AnitubeVoice("ОЗВУЧЕННЯ", voiceName,
            listOf(AnitubeEpisode("ep1", episodeNumber - 1, "Episode 1", "url1", "player"),
                AnitubeEpisode("ep2", episodeNumber, "Episode 2", "url2", "player")))),
    )
}
