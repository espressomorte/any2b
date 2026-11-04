package ua.any2b.tvplayer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ua.any2b.tvplayer.ui.AppTheme
import ua.any2b.tvplayer.ui.TmdbSearchScreen
import ua.any2b.tvplayer.ui.TmdbSearchViewModel

/** Entry point from the home screen. The torrent search starts later, on TMDB details. */
class TolokaSearchActivity : ComponentActivity() {
    companion object { const val EXTRA_GENERAL_CATALOG = "general_catalog" }
    private val viewModel: TmdbSearchViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val generalCatalog = intent.getBooleanExtra(EXTRA_GENERAL_CATALOG, false)
        viewModel.setGeneralCatalog(generalCatalog)
        setContent {
            AppTheme {
                TmdbSearchScreen(
                    state = viewModel.state.collectAsStateWithLifecycle().value,
                    onQueryChanged = viewModel::onQueryChanged,
                    onSearch = viewModel::searchNow,
                    onSelectRecentTitle = viewModel::selectRecentTitle,
                    onClearHistory = viewModel::clearHistory,
                    onOpen = { title ->
                        val query = viewModel.state.value.query
                        startActivity(TvNavigation.details(this, title, searchQuery = query,
                            preferredTitle = title.searchNames(query).firstOrNull() ?: title.title,
                            originalLanguage = title.originalLanguage,
                            originalTitle = title.originalTitle,
                            torrentsOnly = generalCatalog))
                    },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshRecentTitles()
    }
}
