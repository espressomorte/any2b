package ua.any2b.tvplayer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ua.any2b.tvplayer.ui.AppTheme
import ua.any2b.tvplayer.ui.TmdbListScreen
import ua.any2b.tvplayer.ui.TmdbListViewModel

class TmdbListActivity : ComponentActivity() {
    private val viewModel: TmdbListViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val generalCatalog = intent.getBooleanExtra(EXTRA_GENERAL_CATALOG, false)
        viewModel.open(intent.getStringExtra(EXTRA_CATEGORY).orEmpty(), generalCatalog)
        setContent {
            AppTheme {
                TmdbListScreen(viewModel.state.collectAsStateWithLifecycle().value,
                    onOpen = { title ->
                        startActivity(TvNavigation.details(this, title,
                            favoritesLayout = intent.getStringExtra(EXTRA_CATEGORY) == "favorites",
                            torrentsOnly = generalCatalog))
                    },
                    onSort = viewModel::changeSort,
                    onYearRange = viewModel::changeYearRange,
                    onMinimumRating = viewModel::changeMinimumRating,
                    onLoadMore = viewModel::loadMore,
                    onBack = ::finish)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshPersonalList()
    }

    companion object {
        const val EXTRA_CATEGORY = "category"
        const val EXTRA_GENERAL_CATALOG = "general_catalog"
    }
}
