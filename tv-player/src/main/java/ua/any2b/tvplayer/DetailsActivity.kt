package ua.any2b.tvplayer

import android.content.Intent
import android.content.ActivityNotFoundException
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ua.any2b.tvplayer.ui.AppTheme
import ua.any2b.tvplayer.ui.DetailsScreen
import ua.any2b.tvplayer.ui.DetailsViewModel
import ua.any2b.tvplayer.ui.DetailsState
import ua.any2b.tvplayer.ui.genreLabel
import ua.any2b.tvplayer.data.ExternalVideoPlayers
import ua.any2b.tvplayer.data.SettingsManager
import ua.any2b.tvplayer.data.TorrServerAuthStore
import ua.any2b.core.anilist.trailerSearchUrl

class DetailsActivity : ComponentActivity() {

    private var refreshAfterPlayback = false
    private var launchingTolokaPlayer = false

    private val animeId: Int by lazy {
        intent.getIntExtra("ANIME_ID", 1)
    }

    private val viewModel: DetailsViewModel by viewModels {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return DetailsViewModel(animeId, applicationContext) as T
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        refreshAfterPlayback = savedInstanceState?.getBoolean("refresh_after_playback") ?: false
        setContent {
            AppTheme {
                val state = viewModel.state.collectAsStateWithLifecycle().value
                DetailsScreen(
                    state = state,
                    onRetry = viewModel::loadDetails,
                    onRetryVideoSource = viewModel::retryVideoSource,
                    onSelectRelease = viewModel::selectRelease,
                    onManualSearch = viewModel::searchManualRelease,
                    onResetManualSearch = viewModel::resetManualReleaseSearch,
                    onTolokaSearch = viewModel::searchToloka,
                    onSelectTolokaHit = viewModel::selectTolokaHit,
                    onRetryTolokaMetadata = viewModel::retryTolokaMetadata,
                    onPlayTolokaFile = { fileId -> openTolokaFile(fileId) },
                    onToggleFavorite = viewModel::toggleFavorite,
                    onSelectVoice = viewModel::selectVoice,
                    onSelectEpisode = viewModel::selectEpisode,
                    onWatchClick = {
                        viewModel.resolveSelectedStream(
                            onError = { errorMessage ->
                                Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
                            },
                            onSuccess = { streamUrl, voiceIndex, episodeIndex, episodeNumber ->
                                openPlayer(
                                    streamUrl, voiceIndex, episodeIndex, episodeNumber,
                                    (state as? DetailsState.Ready)?.details?.format.equals("MOVIE", ignoreCase = true),
                                )
                            }
                        )
                    },
                    onTrailerClick = {
                        val details = (state as? DetailsState.Ready)?.details
                        if (details != null) {
                            val directUrl = details.trailer?.videoUrl()
                            if (directUrl == null || !openTrailerUrl(directUrl)) {
                                if (!openTrailerUrl(details.trailerSearchUrl())) {
                                    Toast.makeText(this, "Немає застосунку для відкриття трейлера", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    },
                    onBackClick = { finish() },
                    onOpenRelated = { card ->
                        startActivity(Intent(this, DetailsActivity::class.java).apply {
                            putExtra("ANIME_ID", card.id)
                        })
                    },
                    onOpenGenre = { genre ->
                        openDetailsCategory("genre:$genre", genreLabel(genre))
                    },
                    onMoreRelated = { openDetailsCategory("related:$animeId", "Пов’язані релізи") },
                    onMoreRecommendations = { openDetailsCategory("recommendations:$animeId", "Рекомендації") },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        launchingTolokaPlayer = false
        if (refreshAfterPlayback) {
            refreshAfterPlayback = false
            viewModel.refreshVoicesAfterPlayback()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("refresh_after_playback", refreshAfterPlayback)
        super.onSaveInstanceState(outState)
    }

    private fun openPlayer(
        streamUrl: String,
        voiceIndex: Int,
        episodeIndex: Int,
        episodeNumber: Int,
        isMovie: Boolean,
    ) {
        val settings = SettingsManager(this)
        val selectedPackage = settings.selectedPlayerPackage()
        if (selectedPackage != null) {
            val externalIntent = ExternalVideoPlayers(this).intentFor(streamUrl, selectedPackage)
            if (externalIntent != null) {
                try {
                    startActivity(externalIntent)
                    refreshAfterPlayback = true
                    return
                } catch (_: ActivityNotFoundException) {
                    // Плеєр могли видалити або оновити після вибору.
                } catch (_: SecurityException) {
                    // Плеєр більше не дозволяє запуск своєї Activity.
                }
            }
            settings.setSelectedPlayerPackage(null)
            Toast.makeText(this, "Обраний плеєр недоступний. Відкриваємо вбудований", Toast.LENGTH_LONG).show()
        }
        startActivity(Intent(this, PlayerActivity::class.java).apply {
            putExtra(PlayerActivity.EXTRA_STREAM_URL, streamUrl)
            putExtra(PlayerActivity.EXTRA_ANIME_ID, animeId)
            putExtra(PlayerActivity.EXTRA_VOICE_INDEX, voiceIndex)
            putExtra(PlayerActivity.EXTRA_EPISODE_INDEX, episodeIndex)
            putExtra(PlayerActivity.EXTRA_EPISODE_NUMBER, episodeNumber)
            putExtra(PlayerActivity.EXTRA_IS_MOVIE, isMovie)
        })
        refreshAfterPlayback = true
    }

    private fun openTolokaFile(fileId: Int) {
        if (launchingTolokaPlayer) return
        val url = viewModel.tolokaPlaybackUrl(fileId)
        if (url == null) {
            viewModel.reportTolokaPlaybackStatus("Не вдалося сформувати URL потоку для вибраного файлу")
            Toast.makeText(this, "Відеофайл недоступний. Повторіть вибір роздачі", Toast.LENGTH_LONG).show()
            return
        }
        viewModel.reportTolokaPlaybackStatus("URL потоку сформовано для індексу $fileId. Відкриваємо плеєр…")
        launchingTolokaPlayer = true
        val settings = SettingsManager(this)
        val selectedPackage = settings.selectedPlayerPackage()
        val serverRequiresAuth = TorrServerAuthStore(this).read() != null
        if (selectedPackage != null && !serverRequiresAuth) {
            val externalIntent = ExternalVideoPlayers(this).intentForVideo(url, selectedPackage)
            if (externalIntent != null) {
                try {
                    startActivity(externalIntent)
                    viewModel.reportTolokaPlaybackStatus("URL передано зовнішньому плеєру. Результат відтворення перевіряйте в ньому")
                    return
                } catch (_: ActivityNotFoundException) {
                    // The selected player may have been removed.
                } catch (_: SecurityException) {
                    // The selected player no longer accepts explicit launches.
                }
            }
            Toast.makeText(this, "Обраний плеєр недоступний. Відкриваємо вбудований", Toast.LENGTH_LONG).show()
        }
        if (selectedPackage != null && serverRequiresAuth) {
            Toast.makeText(this, "Захищений TorrServer відкриваємо у вбудованому плеєрі", Toast.LENGTH_LONG).show()
        }
        try {
            startActivity(Intent(this, PlayerActivity::class.java).apply {
                putExtra(PlayerActivity.EXTRA_STREAM_URL, url)
                putExtra(PlayerActivity.EXTRA_SOURCE_TYPE, PlayerActivity.SOURCE_TOLOKA)
            })
            viewModel.reportTolokaPlaybackStatus("URL передано вбудованому плеєру")
        } catch (_: Exception) {
            launchingTolokaPlayer = false
            viewModel.reportTolokaPlaybackStatus("Не вдалося запустити плеєр після формування URL потоку")
            Toast.makeText(this, "Не вдалося відкрити плеєр", Toast.LENGTH_LONG).show()
        }
    }

    private fun openDetailsCategory(categoryId: String, title: String) {
        startActivity(Intent(this, CategoryActivity::class.java).apply {
            putExtra(CategoryActivity.EXTRA_CATEGORY_ID, categoryId)
            putExtra(CategoryActivity.EXTRA_CATEGORY_TITLE, title)
        })
    }

    private fun openTrailerUrl(url: String): Boolean = try {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }
}
