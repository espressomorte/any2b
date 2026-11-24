package ua.any2b.tvplayer

import android.os.Bundle
import android.content.Intent
import android.widget.Toast
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ua.any2b.tvplayer.ui.AppTheme
import ua.any2b.tvplayer.ui.TmdbLibraryScreen
import ua.any2b.tvplayer.ui.TmdbLibraryViewModel
import ua.any2b.tvplayer.ui.TmdbPeopleViewModel
import ua.any2b.core.tmdb.TmdbMediaType
import ua.any2b.core.tmdb.TmdbTitle
import ua.any2b.core.tmdb.TmdbEpisode
import ua.any2b.tvplayer.data.TmdbPlaybackSelection
import ua.any2b.tvplayer.data.TmdbWatchHistoryStore
import ua.any2b.tvplayer.data.TmdbWatchlistStore

/** Unified catalog home. AniList rows are resolved to TMDB identities before display. */
class MainActivity : ComponentActivity() {
    companion object {
        private const val TAG = "WatchlistPlayback"
        const val EXTRA_HOME_RESET = "home_reset"
    }
    private var homeResetToken by mutableLongStateOf(0L)
    private var personReturnToken by mutableIntStateOf(0)
    private var homeReturnToken by mutableIntStateOf(0)
    private var returningFromPerson = false
    private var skipReturnRestore = false
    private var hasResumedOnce = false
    private val peopleViewModel: TmdbPeopleViewModel by viewModels()
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
        homeResetToken = intent.getLongExtra(EXTRA_HOME_RESET,
            android.os.SystemClock.elapsedRealtimeNanos())
        setContent {
            AppTheme {
                TmdbLibraryScreen(
                    state = viewModel.state.collectAsStateWithLifecycle().value,
                    onOpen = { title ->
                        startActivity(TvNavigation.details(this, title))
                    },
                    onOpenSpecialListTitle = ::openSpecialListTitle,
                    onOpenFavorite = { title ->
                        openSpecialListTitle(title)
                    },
                    onToggleFavorite = { title ->
                        val added = viewModel.toggleFavorite(title)
                        Toast.makeText(this,
                            if (added) "Додано в обране" else "Вилучено з обраного",
                            Toast.LENGTH_SHORT).show()
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
                    onHome = { homeResetToken = android.os.SystemClock.elapsedRealtimeNanos() },
                    animationOnly = true,
                    homeResetToken = homeResetToken,
                    onSearchTitle = { startActivity(Intent(this, TolokaSearchActivity::class.java)) },
                    onOpenAllTmdb = { startActivity(Intent(this, TmdbLibraryActivity::class.java)) },
                    onSettings = { startActivity(Intent(this, SettingsActivity::class.java)) },
                    onRetryMal = viewModel::refreshMalTop,
                    onRetryAniList = viewModel::refreshAniListHome,
                    onRetryTolokaRss = viewModel::refreshTolokaRss,
                    onLoadTolokaRss = viewModel::loadTolokaRssIfNeeded,
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
                    onSelectWatchlistVoice = viewModel::selectWatchlistVoice,
                    onOpenEpisode = ::openWatchlistPlayback,
                    onContinueWatchlist = ::openWatchlistPlayback,
                    peopleState = peopleViewModel.people.collectAsStateWithLifecycle().value,
                    onLoadPeople = { title ->
                        peopleViewModel.loadPeople(title.type, title.id,
                            includeLiveCast = 16 !in title.genreIds &&
                                (title.type == TmdbMediaType.MOVIE || title.genreIds.isNotEmpty()))
                    },
                    onLoadWatchlistSimilar = viewModel::loadWatchlistSimilar,
                    onRetryPeople = { title ->
                        peopleViewModel.loadPeople(title.type, title.id,
                            includeLiveCast = 16 !in title.genreIds &&
                                (title.type == TmdbMediaType.MOVIE || title.genreIds.isNotEmpty()),
                            force = true)
                    },
                    onOpenPerson = { person ->
                        returningFromPerson = true
                        startActivity(Intent(this, TmdbPersonActivity::class.java).apply {
                            putExtra(TmdbPersonActivity.EXTRA_SOURCE, TmdbPersonActivity.SOURCE_TMDB)
                            putExtra(TmdbPersonActivity.EXTRA_PERSON_ID, person.id)
                        })
                    },
                    personReturnToken = personReturnToken,
                    homeReturnToken = homeReturnToken,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        skipReturnRestore = true
        homeResetToken = intent.getLongExtra(EXTRA_HOME_RESET,
            android.os.SystemClock.elapsedRealtimeNanos())
    }

    override fun onResume() {
        super.onResume()
        if (hasResumedOnce) {
            if (returningFromPerson) {
                returningFromPerson = false
                personReturnToken++
            } else if (skipReturnRestore) skipReturnRestore = false
            else homeReturnToken++
            viewModel.refreshPersonalLists()
        } else {
            // The ViewModel has already loaded personal lists during cold start.
            // Avoid repeating synchronous preference parsing before the first frame.
            hasResumedOnce = true
        }
    }

    private fun openSpecialListTitle(title: TmdbTitle) {
        val season = TmdbWatchHistoryStore(this).episodeProgress(title.type, title.id)?.seasonNumber
        val saved = TmdbWatchHistoryStore(this).playbackSelection(title.type, title.id, season)
        startActivity(TvNavigation.details(this, title, torrentsOnly = true).apply {
            putExtra(TmdbDetailsActivity.EXTRA_SPECIAL_AUTO_PLAY, true)
            putExtra(TmdbDetailsActivity.EXTRA_PLAYBACK_ONLY, true)
            if (saved?.source == TmdbPlaybackSelection.Source.TORRENT)
                saved.topicId?.let { putExtra(TmdbDetailsActivity.EXTRA_AUTO_PLAY_TORRENT_TOPIC, it) }
        })
    }

    private fun openWatchlistPlayback(title: TmdbTitle, episode: TmdbEpisode?) {
        val history = TmdbWatchHistoryStore(this)
        ContinueWatchlistPlayback(history, TmdbWatchlistStore(this)) { targetTitle, selection, episodeNumber ->
        val playbackSeason = targetTitle.matchedSeasonNumber
        val progress = history.episodeProgress(title.type, title.id)
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
        }.start(title, episode)
    }

}
