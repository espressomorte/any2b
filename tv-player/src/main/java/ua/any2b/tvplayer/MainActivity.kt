package ua.any2b.tvplayer

import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ua.any2b.tvplayer.ui.AppTheme
import ua.any2b.tvplayer.ui.TmdbLibraryScreen
import ua.any2b.tvplayer.ui.TmdbLibraryViewModel

/** Unified catalog home. AniList rows are resolved to TMDB identities before display. */
class MainActivity : ComponentActivity() {
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
                        startActivity(TvNavigation.details(this, title, favoritesLayout = true))
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
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshPersonalLists()
    }

}
