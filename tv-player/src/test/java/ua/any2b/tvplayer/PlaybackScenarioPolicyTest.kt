package ua.any2b.tvplayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ua.any2b.core.tmdb.TmdbMediaType
import ua.any2b.core.tmdb.TmdbTitle
import ua.any2b.tvplayer.data.TmdbEpisodeProgress
import ua.any2b.domain.playback.AutoPlaybackDecision
import ua.any2b.domain.playback.AutoPlaybackRequest
import ua.any2b.domain.playback.PlaybackHostEffect
import ua.any2b.domain.playback.PlaybackPreparationRequest
import ua.any2b.domain.playback.PlaybackRequest
import ua.any2b.domain.playback.PlaybackSource
import ua.any2b.domain.playback.decideAutoPlayback
import ua.any2b.domain.playback.watchlistEpisode
import ua.any2b.domain.playback.watchlistSeason

class PlaybackScenarioPolicyTest {
    @Test
    fun coordinatorReturnsHostEffectsForSourcePreparationAndFailureTransitions() {
        val coordinator = PlaybackCoordinator()
        val id = coordinator.begin()

        assertEquals(PlaybackHostEffect.PrepareSource(
            PlaybackPreparationRequest.AniTubeEpisode),
            coordinator.decideAutoPlayback(AutoPlaybackRequest(PlaybackSource.ANITUBE, 3),
                detailsReady = true, torrentFilesReady = false, savedFileAvailable = false,
                episodeFileId = null, alreadyStarted = false, failed = false))
        assertEquals(PlaybackHostEffect.ShowError("Не знайдено серію"),
            coordinator.transition(PlaybackScenarioEvent.Failed(id, "Не знайдено серію"))
                .effects.single())
        assertEquals(PlaybackHostEffect.RetrySource(PlaybackSource.TORRENT),
            coordinator.transition(PlaybackScenarioEvent.RetryRequested(id,
                PlaybackSource.TORRENT)).effects.single())
        assertEquals(PlaybackHostEffect.Finish,
            coordinator.transition(PlaybackScenarioEvent.Finished(id)).effects.single())
    }

    @Test
    fun coordinatorLaunchEffectCarriesTypedRequestAndRejectsStaleRequestId() {
        val coordinator = PlaybackCoordinator()
        coordinator.begin()
        val requestId = coordinator.beginLaunch()!!
        val request = PlaybackRequest.TorrentFile(
            streamUrl = "http://localhost:8090/stream",
            topicId = "42",
            fileId = 7,
            fileName = "episode.mkv",
        )

        assertEquals(PlaybackHostEffect.LaunchPlayback(request),
            coordinator.launchPlaybackRequest(requestId, request))
        coordinator.launchFailed(requestId)
        assertNull(coordinator.launchPlaybackRequest(requestId, request))
    }

    @Test
    fun coordinatorRejectsEventsFromOlderScenarioAndDuplicateLaunches() {
        val coordinator = PlaybackCoordinator()
        val oldId = coordinator.begin()
        val currentId = coordinator.begin()

        assertEquals(false, coordinator.dispatch(PlaybackScenarioEvent.DetailsReady(oldId)))
        assertEquals(PlaybackScenarioPhase.WAITING_FOR_DETAILS, coordinator.phase)
        assertEquals(true, coordinator.dispatch(PlaybackScenarioEvent.DetailsReady(currentId)))
        assertEquals(true, coordinator.dispatch(PlaybackScenarioEvent.PlaybackLaunched(currentId)))
        assertEquals(false, coordinator.dispatch(PlaybackScenarioEvent.PlaybackLaunched(currentId)))
        assertEquals(PlaybackScenarioPhase.PLAYER_ACTIVE, coordinator.phase)
    }

    @Test
    fun coordinatorTracksTorrentRetryAndReturnFromPlayer() {
        val coordinator = PlaybackCoordinator()
        val id = coordinator.begin()

        assertEquals(true, coordinator.dispatch(PlaybackScenarioEvent.DetailsReady(id)))
        assertEquals(true, coordinator.dispatch(PlaybackScenarioEvent.WaitingForTorrent(id)))
        assertEquals(true, coordinator.dispatch(PlaybackScenarioEvent.Failed(id)))
        assertEquals(true, coordinator.dispatch(PlaybackScenarioEvent.RetryRequested(id)))
        assertEquals(true, coordinator.dispatch(PlaybackScenarioEvent.TorrentFilesReady(id)))
        val requestId = coordinator.beginLaunch()!!
        assertEquals(null, coordinator.beginLaunch())
        assertEquals(true, coordinator.playbackLaunched(id, requestId,
            PlaybackSource.TORRENT))
        assertEquals(null, coordinator.beginLaunch())
        assertEquals(true, coordinator.playerReturned(id))
        assertEquals(PlaybackScenarioPhase.RETURNING_FROM_PLAYER, coordinator.phase)
        assertEquals(true, coordinator.beginLaunch() != null)
        assertEquals(PlaybackScenarioPhase.PREPARING_SOURCE, coordinator.phase)
    }

    @Test
    fun staleAsyncPlaybackRequestCannotLaunchAfterRetry() {
        val coordinator = PlaybackCoordinator()
        val scenarioId = coordinator.begin()
        coordinator.dispatch(PlaybackScenarioEvent.DetailsReady(scenarioId))
        val oldRequest = coordinator.beginLaunch()!!
        coordinator.launchFailed(oldRequest)
        val retryRequest = coordinator.beginLaunch()!!

        assertEquals(false, coordinator.isCurrentRequest(oldRequest))
        assertEquals(true, coordinator.isCurrentRequest(retryRequest))
        assertEquals(false, coordinator.playbackLaunched(scenarioId, oldRequest,
            PlaybackSource.TORRENT))
        assertEquals(true, coordinator.isCurrentRequest(retryRequest))
    }

    @Test
    fun externalPlayerReturnBecomesTypedProgressOrCompletionEffect() {
        val coordinator = PlaybackCoordinator()
        val title = TmdbTitle(7, TmdbMediaType.TV, "Series", null, 2020, null, null, null)
        val context = ExternalPlaybackContext(title, PlaybackSource.ANITUBE,
            episodeNumber = 4, seasonNumber = 2, voiceIndex = 1, episodeIndex = 3)

        assertEquals(ExternalPlaybackEffect.SaveEpisodeProgress(title, 1, 3, 4, 0.5f,
            2, 600_000L, 1_200_000L), coordinator.resolveExternalPlayerReturn(context,
            ExternalPlayerReturnEvent(0, 600_000L, 1_200_000L)))
        assertEquals(ExternalPlaybackEffect.CompleteEpisode(title, 1, 3, 4, 2),
            coordinator.resolveExternalPlayerReturn(context, ExternalPlayerReturnEvent(1)))
        assertEquals(ExternalPlaybackEffect.Finish,
            coordinator.resolveExternalPlayerReturn(context, ExternalPlayerReturnEvent(0, -1, 0)))
    }

    @Test
    fun explicitSourceRoutesToItsOwnProvider() {
        assertEquals(AutoPlaybackDecision.None, decideAutoPlayback(
            AutoPlaybackRequest(source = null, episodeNumber = 5), detailsReady = true,
            torrentFilesReady = true, savedFileAvailable = false, episodeFileId = 10,
            alreadyStarted = false, failed = false))
    }

    @Test
    fun torrentAutoplayWaitsForFilesThenUsesSavedFileBeforeEpisodeMatch() {
        val request = AutoPlaybackRequest(PlaybackSource.TORRENT,
            episodeNumber = 5, savedFileId = 22)
        assertEquals(AutoPlaybackDecision.Wait,
            decideAutoPlayback(request, detailsReady = true, torrentFilesReady = false,
                savedFileAvailable = false, episodeFileId = null, alreadyStarted = false,
                failed = false))
        assertEquals(AutoPlaybackDecision.StartTorrent(22),
            decideAutoPlayback(request, detailsReady = true, torrentFilesReady = true,
                savedFileAvailable = true, episodeFileId = 35, alreadyStarted = false,
                failed = false))
    }

    @Test
    fun torrentAutoplayFallsBackToEpisodeMatchAndFailsOnlyAfterFileListArrives() {
        val request = AutoPlaybackRequest(PlaybackSource.TORRENT,
            episodeNumber = 5)
        assertEquals(AutoPlaybackDecision.StartTorrent(35),
            decideAutoPlayback(request, detailsReady = true, torrentFilesReady = true,
                savedFileAvailable = false, episodeFileId = 35, alreadyStarted = false,
                failed = false))
        assertEquals(AutoPlaybackDecision.MissingTorrentFile,
            decideAutoPlayback(request, detailsReady = true, torrentFilesReady = true,
                savedFileAvailable = false, episodeFileId = null, alreadyStarted = false,
                failed = false))
    }

    @Test
    fun anitubeAutoplayDoesNotWaitForTorrentPreparation() {
        val request = AutoPlaybackRequest(PlaybackSource.ANITUBE,
            episodeNumber = 5)
        assertEquals(AutoPlaybackDecision.StartAniTube,
            decideAutoPlayback(request, detailsReady = true, torrentFilesReady = false,
                savedFileAvailable = false, episodeFileId = null, alreadyStarted = false,
                failed = false))
    }

    @Test
    fun autoplayWaitsForDetailsAndNeverStartsTwice() {
        val request = AutoPlaybackRequest(PlaybackSource.ANITUBE,
            episodeNumber = 5)
        assertEquals(AutoPlaybackDecision.Wait,
            decideAutoPlayback(request, detailsReady = false, torrentFilesReady = false,
                savedFileAvailable = false, episodeFileId = null, alreadyStarted = false,
                failed = false))
        assertEquals(AutoPlaybackDecision.None,
            decideAutoPlayback(request, detailsReady = true, torrentFilesReady = true,
                savedFileAvailable = false, episodeFileId = null, alreadyStarted = true,
                failed = false))
    }

    @Test
    fun watchlistEpisodeClickOverridesProgressAndUsesTmdbSeason() {
        val progress = TmdbEpisodeProgress(voiceIndex = 2, episodeIndex = 3,
            episodeNumber = 4, progress = 0.4f, seasonNumber = 2,
            animeSeasonNumber = 5)
        assertEquals(2, watchlistSeason(explicitSeason = null, matchedSeason = null,
            progressSeason = progress.seasonNumber))
        assertEquals(8, watchlistEpisode(explicitEpisode = 8,
            progressEpisode = progress.episodeNumber, progressFraction = progress.progress,
            isTv = true, hasSavedSelection = true))
    }

    @Test
    fun watchlistContinueResumesCurrentEpisodeOrAdvancesWhenNearlyComplete() {
        val inProgress = TmdbEpisodeProgress(0, 0, 4, 0.4f, seasonNumber = 2)
        val nearlyComplete = inProgress.copy(progress = 0.95f)
        assertEquals(4, watchlistEpisode(null, inProgress.episodeNumber, inProgress.progress, true, true))
        assertEquals(5, watchlistEpisode(null, nearlyComplete.episodeNumber, nearlyComplete.progress, true, true))
        assertEquals(null, watchlistEpisode(null, null, null, false, false))
        assertEquals(1, watchlistEpisode(null, null, null, true, true))
    }
}
