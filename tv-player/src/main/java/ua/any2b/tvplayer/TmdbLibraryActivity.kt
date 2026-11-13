package ua.any2b.tvplayer

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import ua.any2b.tvplayer.ui.AppTheme
import ua.any2b.tvplayer.ui.TmdbLibraryScreen
import ua.any2b.tvplayer.ui.TmdbLibraryViewModel
import ua.any2b.tvplayer.data.TmdbWatchHistoryStore

class TmdbLibraryActivity : ComponentActivity() {
    companion object { private const val TAG = "WatchlistPlayback" }
    private val viewModel: TmdbLibraryViewModel by viewModels {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return TmdbLibraryViewModel(application, animationOnly = false) as T
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppTheme {
                TmdbLibraryScreen(viewModel.state.collectAsStateWithLifecycle().value,
                    onOpen = { title ->
                        startActivity(TvNavigation.details(this, title, torrentsOnly = true))
                    },
                    onRetry = viewModel::refreshPopular,
                    onRetryTolokaRss = viewModel::refreshTolokaRss,
                    onMore = { category ->
                        startActivity(Intent(this, TmdbListActivity::class.java).apply {
                            putExtra(TmdbListActivity.EXTRA_CATEGORY, category)
                            putExtra(TmdbListActivity.EXTRA_GENERAL_CATALOG, true)
                        })
                    },
                    onSearchTitle = {
                        startActivity(Intent(this, TolokaSearchActivity::class.java).apply {
                            putExtra(TolokaSearchActivity.EXTRA_GENERAL_CATALOG, true)
                        })
                    },
                    onSettings = {
                        startActivity(Intent(this, SettingsActivity::class.java))
                    },
                    onRemoveFromHistory = viewModel::removeFromHistory,
                    onSelectWatchlistSeason = { title, seasonNumber ->
                        if (viewModel.hasPlayableWatchlistSeason(title, seasonNumber)) {
                            viewModel.selectWatchlistSeason(title, seasonNumber)
                        } else {
                            startActivity(TvNavigation.details(this,
                                title.copy(matchedSeasonNumber = seasonNumber)).apply {
                                putExtra(TmdbDetailsActivity.EXTRA_RETURN_TO_WATCHLIST, true)
                            })
                        }
                    },
                    onOpenEpisode = ::openWatchlistPlayback,
                    onContinueWatchlist = ::openWatchlistPlayback,
                    onBack = ::returnHome,
                    onHome = ::returnHome)
            }
        }

    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshPersonalLists()
    }

    private fun returnHome() {
        startActivity(Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(MainActivity.EXTRA_HOME_RESET, SystemClock.elapsedRealtimeNanos())
        })
        finish()
    }

    private fun openWatchlistPlayback(title: ua.any2b.core.tmdb.TmdbTitle,
        episode: ua.any2b.core.tmdb.TmdbEpisode?) {
        val history = TmdbWatchHistoryStore(this)
        val progress = history.episodeProgress(title.type, title.id)
        val selection = history.playbackSelection(title.type, title.id)
            ?: history.inferredPlaybackSelection(title.type, title.id)
        val source = selection?.source?.name
        val playbackSeason = episode?.seasonNumber ?: title.matchedSeasonNumber
            ?: progress?.seasonNumber ?: selection?.anitubeSeasonNumber
        val requestedEpisode = episode?.episodeNumber ?: progress?.let {
            if (it.progress >= 0.95f) it.episodeNumber + 1 else it.episodeNumber
        } ?: if (title.type == ua.any2b.core.tmdb.TmdbMediaType.TV && selection != null) 1 else null
        val cachedEpisode = if (episode == null && playbackSeason != null && requestedEpisode != null)
            ua.any2b.tvplayer.data.TmdbWatchlistStore(this)
                .cachedEpisodes(title.id, playbackSeason)
                ?.firstOrNull { candidate ->
                    candidate.episodeNumber >= requestedEpisode && candidate.airDate?.let {
                        runCatching { java.time.LocalDate.parse(it) }.getOrNull()
                    }?.let { !it.isAfter(java.time.LocalDate.now()) } == true
                } else null
        val episodeNumber = episode?.episodeNumber ?: cachedEpisode?.episodeNumber ?: requestedEpisode
        val targetTitle = title.copy(matchedSeasonNumber = playbackSeason)
        Log.i(TAG, "Continue requested tmdb=${title.type.path}:${title.id} " +
            "episode=${playbackSeason ?: 0}x${episodeNumber ?: 0} " +
            "progress=${progress?.progress} positionMs=${progress?.positionMs} " +
            "source=$source release=${selection?.anitubeReleaseId} " +
            "voice=${selection?.voiceIndex} topic=${selection?.topicId} file=${selection?.fileId}")
        val movieAniTubeStart = title.type == ua.any2b.core.tmdb.TmdbMediaType.MOVIE &&
            selection?.source == ua.any2b.tvplayer.data.TmdbPlaybackSelection.Source.ANITUBE
        startActivity(TvNavigation.details(this, targetTitle,
            torrentsOnly = selection?.source ==
                ua.any2b.tvplayer.data.TmdbPlaybackSelection.Source.TORRENT).apply {
            putExtra(TmdbDetailsActivity.EXTRA_RETURN_TO_WATCHLIST, true)
            putExtra(TmdbDetailsActivity.EXTRA_PLAYBACK_ONLY, selection != null)
            if (episodeNumber != null && title.type == ua.any2b.core.tmdb.TmdbMediaType.TV) {
                putExtra(TmdbDetailsActivity.EXTRA_AUTO_PLAY_EPISODE, episodeNumber)
                playbackSeason?.let { putExtra(TmdbDetailsActivity.EXTRA_TMDB_SEASON, it) }
            } else if (movieAniTubeStart) {
                putExtra(TmdbDetailsActivity.EXTRA_AUTO_PLAY_EPISODE, 1)
            }
            source?.let { putExtra(TmdbDetailsActivity.EXTRA_AUTO_PLAY_SOURCE, it) }
            selection?.topicId?.let {
                putExtra(TmdbDetailsActivity.EXTRA_AUTO_PLAY_TORRENT_TOPIC, it)
            }
            if (title.type == ua.any2b.core.tmdb.TmdbMediaType.MOVIE &&
                selection?.source == ua.any2b.tvplayer.data.TmdbPlaybackSelection.Source.TORRENT) {
                selection.fileId?.let {
                    putExtra(TmdbDetailsActivity.EXTRA_AUTO_PLAY_TORRENT_FILE, it)
                }
            }
            selection?.anitubeReleaseId?.let {
                putExtra(TmdbDetailsActivity.EXTRA_AUTO_PLAY_ANITUBE_RELEASE, it)
            }
            selection?.anitubeSeasonNumber?.let {
                putExtra(TmdbDetailsActivity.EXTRA_AUTO_PLAY_ANITUBE_SEASON, it)
            }
            putExtra(TmdbDetailsActivity.EXTRA_AUTO_PLAY_ANITUBE_VOICE,
                selection?.voiceIndex ?: 0)
            selection?.voiceName?.let {
                putExtra(TmdbDetailsActivity.EXTRA_AUTO_PLAY_ANITUBE_VOICE_NAME, it)
            }
        })
    }
}
