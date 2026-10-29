package ua.any2b.tvplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import ua.any2b.core.tmdb.TmdbMediaType
import ua.any2b.core.tmdb.TmdbTitle

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TmdbLibraryScreen(state: TmdbLibraryState, onOpen: (TmdbTitle) -> Unit,
    onRetry: (TmdbMediaType) -> Unit, onMore: (String) -> Unit, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().background(AppColors.BackgroundDark)
        .padding(horizontal = 32.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = onBack) { Text("Назад") }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(24.dp),
            contentPadding = PaddingValues(bottom = 32.dp)) {
            if (state.history.isNotEmpty()) {
                item(key = "history") {
                    TmdbLibraryRow("Історія переглядів", state.history, onOpen,
                        onMore = { onMore("history") })
                }
            }
            item(key = "tv") {
                TmdbPopularRow("Популярні серіали", state.tv,
                    onOpen, onRetry = { onRetry(TmdbMediaType.TV) }, onMore = { onMore("tv") })
            }
            item(key = "movies") {
                TmdbPopularRow("Популярні фільми", state.movies,
                    onOpen, onRetry = { onRetry(TmdbMediaType.MOVIE) }, onMore = { onMore("movie") })
            }
            if (state.favorites.isNotEmpty()) {
                item(key = "favorites") {
                    TmdbLibraryRow("Обрані тайтли", state.favorites, onOpen,
                        onMore = { onMore("favorites") })
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TmdbPopularRow(title: String, state: TmdbPopularState,
    onOpen: (TmdbTitle) -> Unit, onRetry: () -> Unit, onMore: () -> Unit) {
    when (state) {
        TmdbPopularState.Loading -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = Color.White)
            Text("Завантаження…", color = AppColors.TextSecondary)
        }
        is TmdbPopularState.Error -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = Color.White)
            Text(state.message, color = AppColors.ErrorRed)
            Button(onClick = onRetry) { Text("Повторити") }
        }
        is TmdbPopularState.Results -> if (state.titles.isNotEmpty())
            TmdbLibraryRow(title, state.titles, onOpen, onMore)
    }
}

@Composable
private fun TmdbLibraryRow(title: String, titles: List<TmdbTitle>,
    onOpen: (TmdbTitle) -> Unit, onMore: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = Color.White,
            modifier = Modifier.padding(horizontal = 4.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)) {
            items(titles.take(13), key = { "${it.type}:${it.id}" }) { item ->
                TmdbPosterCard(item, onClick = { onOpen(item) })
            }
            if (titles.size > 13) item(key = "more") { MoreCard(onClick = onMore) }
        }
    }
}
