package ua.any2b.tvplayer

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ua.any2b.tvplayer.ui.AppTheme
import ua.any2b.tvplayer.ui.HomeScreen
import ua.any2b.tvplayer.ui.HomeViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: HomeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppTheme {
                val state = viewModel.state.collectAsStateWithLifecycle().value
                HomeScreen(
                    state = state,
                    onRetry = viewModel::load,
                    onOpen = { card ->
                        // Наступний крок: екран деталей. Поки лише показуємо, що картка натиснулась.
                        Toast.makeText(
                            this,
                            "${card.title.display} (AniList #${card.id})",
                            Toast.LENGTH_SHORT,
                        ).show()
                    },
                )
            }
        }
    }
}
