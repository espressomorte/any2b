package ua.any2b.tvplayer

import android.content.Intent
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
    private val viewModel: TmdbSearchViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppTheme {
                TmdbSearchScreen(
                    state = viewModel.state.collectAsStateWithLifecycle().value,
                    onQueryChanged = viewModel::onQueryChanged,
                    onSearch = viewModel::searchNow,
                    onSelectRecentTitle = viewModel::selectRecentTitle,
                    onClearHistory = viewModel::clearHistory,
                    onOpen = { title ->
                        startActivity(Intent(this, TmdbDetailsActivity::class.java).apply {
                            putExtra(TmdbDetailsActivity.EXTRA_TMDB_ID, title.id)
                            putExtra(TmdbDetailsActivity.EXTRA_TMDB_TYPE, title.type.path)
                            putExtra(TmdbDetailsActivity.EXTRA_SEARCH_QUERY, viewModel.state.value.query)
                            putExtra(TmdbDetailsActivity.EXTRA_PREFERRED_TITLE,
                                title.searchNames(viewModel.state.value.query).firstOrNull() ?: title.title)
                            putExtra(TmdbDetailsActivity.EXTRA_ORIGINAL_LANGUAGE, title.originalLanguage)
                            putExtra(TmdbDetailsActivity.EXTRA_ORIGINAL_TITLE, title.originalTitle)
                        })
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
