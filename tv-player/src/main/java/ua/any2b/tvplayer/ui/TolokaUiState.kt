package ua.any2b.tvplayer.ui

import ua.any2b.core.toloka.TolokaHit
import ua.any2b.core.toloka.TorrServerTorrent

sealed interface TolokaSearchState {
    data object Idle : TolokaSearchState
    data object Loading : TolokaSearchState
    data class Results(
        val query: String,
        val hits: List<TolokaHit>,
        val totalFound: Int,
        val selectedId: String? = null,
        val weakHits: List<TolokaHit> = emptyList(),
        val rejectedHits: List<TolokaHit> = emptyList(),
    ) : TolokaSearchState
    data class Error(val message: String) : TolokaSearchState
}

sealed interface TolokaTransferState {
    data object Idle : TolokaTransferState
    data class Loading(val step: String, val lastSuccess: String) : TolokaTransferState
    data class Files(val torrent: TorrServerTorrent) : TolokaTransferState
    data class Error(
        val message: String,
        val lastSuccess: String = "Пошук роздачі",
        val repeatSearch: Boolean = false,
        val retryMetadata: Boolean = false,
    ) : TolokaTransferState
}

enum class Panel { TORRENT, ONLINE }
