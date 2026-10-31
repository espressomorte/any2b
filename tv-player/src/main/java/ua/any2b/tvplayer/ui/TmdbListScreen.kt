package ua.any2b.tvplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import kotlinx.coroutines.flow.collect
import ua.any2b.core.tmdb.TmdbListSort
import ua.any2b.core.tmdb.TmdbTitle

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TmdbListScreen(state: TmdbListState, onOpen: (TmdbTitle) -> Unit,
    onSort: (TmdbListSort) -> Unit, onLoadMore: () -> Unit, onBack: () -> Unit) {
    val gridState = rememberLazyGridState()
    LaunchedEffect(state.category, state.sort) { gridState.scrollToItem(0) }
    LaunchedEffect(gridState, state.titles.size, state.hasMore, state.loading, state.error) {
        if (state.hasMore && !state.loading && state.error == null) {
            snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
                .collect { lastVisible -> if (lastVisible >= state.titles.size - 5) onLoadMore() }
        }
    }
    val heading = when (state.category) {
        "history" -> "Історія переглядів"
        "recommendations" -> "Рекомендації"
        "tv" -> "Популярні серіали"
        "movie" -> "Популярні фільми"
        "animation_tv" -> "Популярні анімаційні серіали"
        "animation_movie" -> "Популярні повнометражні анімаційні фільми"
        "mal_top" -> "Аніме з найвищими оцінками"
        else -> "Обрані тайтли"
    }
    Column(Modifier.fillMaxSize().background(AppColors.BackgroundDark)
        .padding(horizontal = 32.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Text(heading, style = MaterialTheme.typography.headlineLarge, color = Color.White)
            OutlinedButton(onClick = onBack) { Text("Назад") }
        }
        if (state.category != "favorites" && state.category != "history" &&
            state.category != "mal_top" &&
            state.category != "recommendations") {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf(TmdbListSort.POPULARITY to "Популярність",
                    TmdbListSort.RATING to "Рейтинг",
                    TmdbListSort.YEAR to "Рік випуску").forEach { (sort, label) ->
                    Button(onClick = { onSort(sort) },
                        enabled = state.sort != sort) { Text(label) }
                }
            }
        }
        if (state.error != null) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(state.error, color = AppColors.ErrorRed)
                Button(onClick = onLoadMore) { Text("Повторити") }
            }
        }
        if (state.titles.isEmpty() && !state.loading && state.error == null) {
            Text("У цьому списку поки немає тайтлів", color = AppColors.TextSecondary)
        }
        LazyVerticalGrid(columns = GridCells.Fixed(5), state = gridState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp)) {
            items(state.titles, key = { "${it.type}:${it.id}" }) { title ->
                TmdbPosterCard(title, onClick = { onOpen(title) })
            }
            if (state.loading) item(key = "loading") {
                Text("Завантаження…", color = AppColors.TextSecondary)
            }
        }
    }
}
