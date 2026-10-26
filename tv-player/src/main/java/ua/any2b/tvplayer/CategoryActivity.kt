package ua.any2b.tvplayer

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ua.any2b.tvplayer.ui.AppTheme
import ua.any2b.tvplayer.ui.CategoryScreen
import ua.any2b.tvplayer.ui.CategoryViewModel

class CategoryActivity : ComponentActivity() {
    companion object {
        const val EXTRA_CATEGORY_ID = "CATEGORY_ID"
        const val EXTRA_CATEGORY_TITLE = "CATEGORY_TITLE"
    }

    private val categoryId by lazy { intent.getStringExtra(EXTRA_CATEGORY_ID).orEmpty() }
    private val categoryTitle by lazy { intent.getStringExtra(EXTRA_CATEGORY_TITLE).orEmpty() }
    private val viewModel: CategoryViewModel by viewModels {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return CategoryViewModel(application, categoryId) as T
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppTheme {
                val state = viewModel.state.collectAsStateWithLifecycle().value
                CategoryScreen(
                    title = categoryTitle,
                    state = state,
                    onOpen = { card ->
                        startActivity(Intent(this, DetailsActivity::class.java).apply { putExtra("ANIME_ID", card.id) })
                    },
                    onLoadMore = viewModel::loadMore,
                    onBack = { finish() },
                    showRelatedBadge = categoryId.startsWith("related:"),
                    onFavoriteChanged = viewModel::refreshLocal,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshLocal()
    }
}
