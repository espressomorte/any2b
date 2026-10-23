package ua.any2b.tvplayer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import ua.any2b.tvplayer.data.SettingsManager
import ua.any2b.tvplayer.ui.AppTheme
import ua.any2b.tvplayer.ui.SettingsScreen

class SettingsViewModel(application: android.app.Application) : AndroidViewModel(application) {
    private val settingsManager = SettingsManager(application)

    private val _autoPlayNext = MutableStateFlow(settingsManager.isAutoPlayNextEnabled())
    val autoPlayNext: StateFlow<Boolean> = _autoPlayNext

    fun setAutoPlayNext(enabled: Boolean) {
        settingsManager.setAutoPlayNextEnabled(enabled)
        _autoPlayNext.value = enabled
    }
}

class SettingsActivity : ComponentActivity() {
    private val viewModel: SettingsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppTheme {
                val autoPlay = viewModel.autoPlayNext.collectAsStateWithLifecycle().value
                SettingsScreen(
                    autoPlayNext = autoPlay,
                    onAutoPlayChanged = viewModel::setAutoPlayNext,
                    onBack = { finish() }
                )
            }
        }
    }
}
