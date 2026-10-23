package ua.any2b.tvplayer.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import ua.any2b.tvplayer.data.WatchHistoryEntry
import ua.any2b.tvplayer.data.WatchHistoryManager

class HistoryViewModel(application: Application) : AndroidViewModel(application) {
    private val historyManager = WatchHistoryManager(application)

    private val _history = MutableStateFlow<List<WatchHistoryEntry>>(emptyList())
    val history: StateFlow<List<WatchHistoryEntry>> = _history

    init {
        loadHistory()
    }

    fun loadHistory() {
        viewModelScope.launch {
            _history.value = historyManager.getHistory()
        }
    }
}
