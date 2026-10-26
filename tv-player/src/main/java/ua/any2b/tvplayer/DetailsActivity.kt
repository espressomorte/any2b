package ua.any2b.tvplayer

import android.content.Intent
import android.content.ActivityNotFoundException
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
import ua.any2b.tvplayer.data.ExternalVideoPlayers
import ua.any2b.tvplayer.data.SettingsManager

class DetailsActivity : ComponentActivity() {

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
                    onBackClick = { finish() },
                    onOpenRelated = { card ->
                        startActivity(Intent(this, DetailsActivity::class.java).apply {
                            putExtra("ANIME_ID", card.id)
                        })
                    },
                    onMoreRelated = { openDetailsCategory("related:$animeId", "Пов’язані релізи") },
                    onMoreRecommendations = { openDetailsCategory("recommendations:$animeId", "Рекомендації") },
                )
            }
        }
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
    }

    private fun openDetailsCategory(categoryId: String, title: String) {
        startActivity(Intent(this, CategoryActivity::class.java).apply {
            putExtra(CategoryActivity.EXTRA_CATEGORY_ID, categoryId)
            putExtra(CategoryActivity.EXTRA_CATEGORY_TITLE, title)
        })
    }
}
