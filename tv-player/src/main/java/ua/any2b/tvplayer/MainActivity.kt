package ua.any2b.tvplayer

import android.os.Bundle
import android.content.Intent
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ua.any2b.tvplayer.ui.AppTheme
import ua.any2b.tvplayer.ui.TmdbLibraryScreen
import ua.any2b.tvplayer.ui.TmdbLibraryViewModel
import ua.any2b.core.tmdb.TmdbMediaType
import ua.any2b.core.tmdb.TmdbTitle
import ua.any2b.core.tmdb.TmdbEpisode
import ua.any2b.tvplayer.data.TmdbPlaybackSelection
import ua.any2b.tvplayer.data.TmdbWatchHistoryStore
import ua.any2b.tvplayer.data.TmdbWatchlistStore

/** Unified catalog home. AniList rows are resolved to TMDB identities before display. */
class MainActivity : ComponentActivity() {
    companion object { private const val TAG = "WatchlistPlayback" }
    private val viewModel: TmdbLibraryViewModel by viewModels {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return TmdbLibraryViewModel(application, animationOnly = true) as T
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppTheme {
                TmdbLibraryScreen(
                    state = viewModel.state.collectAsStateWithLifecycle().value,
                    onOpen = { title ->
                        startActivity(TvNavigation.details(this, title))
                    },
                    onOpenFavorite = { title ->
                        startActivity(TvNavigation.details(this, title))
                    },
                    onRetry = viewModel::refreshPopular,
                    onMore = { category ->
                        if (category == "trending" || category == "popular_season" ||
                            category == "anime_favorites") {
                            val title = when (category) {
                                "trending" -> "Аніме у тренді"
                                "popular_season" -> "Аніме популярне цього сезону"
                                else -> "Обране аніме"
                            }
                            if (category == "anime_favorites") {
                                startActivity(Intent(this, TmdbListActivity::class.java).apply {
                                    putExtra(TmdbListActivity.EXTRA_CATEGORY, "favorites")
                                })
                            } else startActivity(Intent(this, CategoryActivity::class.java).apply {
                                putExtra(CategoryActivity.EXTRA_CATEGORY_ID, category)
                                putExtra(CategoryActivity.EXTRA_CATEGORY_TITLE, title)
                            })
                        } else startActivity(Intent(this, TmdbListActivity::class.java).apply {
                            putExtra(TmdbListActivity.EXTRA_CATEGORY, category)
                        })
                    },
                    onBack = {},
                    animationOnly = true,
                    onSearchTitle = { startActivity(Intent(this, TolokaSearchActivity::class.java)) },
                    onOpenAllTmdb = { startActivity(Intent(this, TmdbLibraryActivity::class.java)) },
                    onSettings = { startActivity(Intent(this, SettingsActivity::class.java)) },
                    onRetryMal = viewModel::refreshMalTop,
                    onRetryAniList = viewModel::refreshAniListHome,
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
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshPersonalLists()
    }

    private fun openWatchlistPlayback(title: TmdbTitle, episode: TmdbEpisode?) {
        val history = TmdbWatchHistoryStore(this)
        val progress = history.episodeProgress(title.type, title.id)
        val selection = history.playbackSelection(title.type, title.id)
            ?: history.inferredPlaybackSelection(title.type, title.id)
        val playbackSeason = episode?.seasonNumber ?: title.matchedSeasonNumber
            ?: progress?.seasonNumber ?: selection?.anitubeSeasonNumber
        val requestedEpisode = episode?.episodeNumber ?: progress?.let {
            if (it.progress >= 0.95f) it.episodeNumber + 1 else it.episodeNumber
        } ?: if (title.type == TmdbMediaType.TV && selection != null) 1 else null
        val cachedEpisode = if (episode == null && playbackSeason != null && requestedEpisode != null)
            TmdbWatchlistStore(this).cachedEpisodes(title.id, playbackSeason)
                ?.firstOrNull { candidate -> candidate.episodeNumber >= requestedEpisode &&
                    candidate.airDate?.let {
                        runCatching { java.time.LocalDate.parse(it) }.getOrNull()
                    }?.let { !it.isAfter(java.time.LocalDate.now()) } == true }
        else null
        val episodeNumber = episode?.episodeNumber ?: cachedEpisode?.episodeNumber ?: requestedEpisode
        val targetTitle = title.copy(matchedSeasonNumber = playbackSeason)
        Log.i(TAG, "Continue requested tmdb=${title.type.path}:${title.id} " +
            "episode=${playbackSeason ?: 0}x${episodeNumber ?: 0} " +
            "progress=${progress?.progress} positionMs=${progress?.positionMs} " +
            "source=${selection?.source} release=${selection?.anitubeReleaseId} " +
            "voice=${selection?.voiceIndex} topic=${selection?.topicId} file=${selection?.fileId}")
        val movieAniTubeStart = title.type == TmdbMediaType.MOVIE &&
            selection?.source == TmdbPlaybackSelection.Source.ANITUBE
        startActivity(TvNavigation.details(this, targetTitle,
            torrentsOnly = selection?.source == TmdbPlaybackSelection.Source.TORRENT).apply {
            putExtra(TmdbDetailsActivity.EXTRA_RETURN_TO_WATCHLIST, true)
            putExtra(TmdbDetailsActivity.EXTRA_PLAYBACK_ONLY, selection != null)
            if (episodeNumber != null && title.type == TmdbMediaType.TV) {
                putExtra(TmdbDetailsActivity.EXTRA_AUTO_PLAY_EPISODE, episodeNumber)
                playbackSeason?.let { putExtra(TmdbDetailsActivity.EXTRA_TMDB_SEASON, it) }
            } else if (movieAniTubeStart) {
                putExtra(TmdbDetailsActivity.EXTRA_AUTO_PLAY_EPISODE, 1)
            }
            selection?.source?.name?.let {
                putExtra(TmdbDetailsActivity.EXTRA_AUTO_PLAY_SOURCE, it)
            }
            selection?.topicId?.let {
                putExtra(TmdbDetailsActivity.EXTRA_AUTO_PLAY_TORRENT_TOPIC, it)
            }
            selection?.fileId?.takeIf { title.type == TmdbMediaType.MOVIE &&
                selection.source == TmdbPlaybackSelection.Source.TORRENT }?.let {
                putExtra(TmdbDetailsActivity.EXTRA_AUTO_PLAY_TORRENT_FILE, it)
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
