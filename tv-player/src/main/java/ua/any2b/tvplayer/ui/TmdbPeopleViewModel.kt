package ua.any2b.tvplayer.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import ua.any2b.core.tmdb.TmdbMediaType
import ua.any2b.core.tmdb.TmdbPersonCredit
import ua.any2b.core.tmdb.TmdbPersonFilmography
import ua.any2b.tvplayer.data.workerTmdbConnection

sealed interface TmdbPeopleState {
    data object Loading : TmdbPeopleState
    data class Ready(val people: List<TmdbPersonCredit>) : TmdbPeopleState
    data class Error(val message: String) : TmdbPeopleState
}

sealed interface TmdbFilmographyState {
    data object Loading : TmdbFilmographyState
    data class Ready(val data: TmdbPersonFilmography) : TmdbFilmographyState
    data class Error(val message: String) : TmdbFilmographyState
}

class TmdbPeopleViewModel(application: Application) : AndroidViewModel(application) {
    private val _people = MutableStateFlow<TmdbPeopleState>(TmdbPeopleState.Loading)
    val people: StateFlow<TmdbPeopleState> = _people
    private val _filmography = MutableStateFlow<TmdbFilmographyState>(TmdbFilmographyState.Loading)
    val filmography: StateFlow<TmdbFilmographyState> = _filmography
    private var peopleKey: Triple<TmdbMediaType, Int, Boolean>? = null
    private var personId: Int? = null
    private var peopleJob: Job? = null
    private var filmographyJob: Job? = null

    fun loadPeople(type: TmdbMediaType, titleId: Int, includeLiveCast: Boolean,
        force: Boolean = false) {
        val key = Triple(type, titleId, includeLiveCast)
        if (!force && peopleKey == key) return
        peopleKey = key
        peopleJob?.cancel()
        _people.value = TmdbPeopleState.Loading
        peopleJob = viewModelScope.launch {
            try {
                val connection = workerTmdbConnection(getApplication())
                    ?: error("Налаштуйте TMDB у розділі «Інтеграції»")
                val result = connection.client.people(type, titleId, connection.accessKey,
                    includeLiveCast = includeLiveCast)
                if (peopleKey == key) _people.value = TmdbPeopleState.Ready(result)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (peopleKey == key) _people.value =
                    TmdbPeopleState.Error(error.message ?: "Не вдалося завантажити персон")
            }
        }
    }

    fun loadFilmography(id: Int, force: Boolean = false) {
        if (!force && personId == id) return
        personId = id
        filmographyJob?.cancel()
        _filmography.value = TmdbFilmographyState.Loading
        filmographyJob = viewModelScope.launch {
            try {
                val connection = workerTmdbConnection(getApplication())
                    ?: error("Налаштуйте TMDB у розділі «Інтеграції»")
                val result = connection.client.personFilmography(id, connection.accessKey)
                if (personId == id) _filmography.value = TmdbFilmographyState.Ready(result)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (personId == id) _filmography.value = TmdbFilmographyState.Error(
                    error.message ?: "Не вдалося завантажити роботи")
            }
        }
    }
}
