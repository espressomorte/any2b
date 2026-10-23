package ua.any2b.tvplayer.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import ua.any2b.tvplayer.anilist.AniListClient
import ua.any2b.tvplayer.anilist.HomeRow

sealed interface HomeState {
    data object Loading : HomeState
    data class Ready(val rows: List<HomeRow>) : HomeState
    data class Error(val message: String) : HomeState
}

class HomeViewModel(
    private val client: AniListClient = AniListClient(),
) : ViewModel() {

    private val _state = MutableStateFlow<HomeState>(HomeState.Loading)
    val state: StateFlow<HomeState> = _state

    init {
        load()
    }

    fun load() {
        _state.value = HomeState.Loading
        viewModelScope.launch {
            _state.value = try {
                HomeState.Ready(client.homeRows())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                HomeState.Error(e.message ?: "Не вдалося завантажити каталог")
            }
        }
    }
}
