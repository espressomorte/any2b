package ua.any2b.tvplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.flow.collect
import ua.any2b.core.anilist.AnimeCard

@Composable
fun CategoryScreen(
    title: String,
    state: CategoryState,
    onOpen: (AnimeCard) -> Unit,
    onLoadMore: () -> Unit,
    onBack: () -> Unit,
    showRelatedBadge: Boolean = false,
    onFavoriteChanged: () -> Unit = {},
) {
    val gridState = rememberLazyGridState()
    LaunchedEffect(gridState, state.items.size, state.hasMore, state.loadingMore, state.error) {
        if (state.hasMore && !state.loadingMore && state.error == null && state.items.isNotEmpty()) {
            snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
                .collect { lastVisible ->
                    if (lastVisible >= state.items.size - 5) onLoadMore()
                }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFF0A0B0E)).padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Button(onClick = onBack) { Text("← Назад") }
            Text(title, style = MaterialTheme.typography.headlineLarge)
        }
        if (state.error != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(state.error, color = Color(0xFFFFB4AB))
                Button(onClick = onLoadMore) { Text("Повторити") }
            }
        }
        if (state.items.isEmpty() && !state.loadingMore && state.error == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("У цій категорії поки що немає тайтлів", color = Color(0xFF94A3B8))
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(5),
                state = gridState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalArrangement = Arrangement.spacedBy(28.dp),
            ) {
                items(state.items, key = { it.id }) { card ->
                    PosterCard(
                        card,
                        modifier = Modifier.fillMaxWidth(),
                        showRelatedBadge = showRelatedBadge,
                        onClick = { onOpen(card) },
                        onFavoriteChanged = onFavoriteChanged,
                    )
                }
                if (state.loadingMore) {
                    item(key = "loading") { Text("Завантаження...", color = Color(0xFF94A3B8)) }
                }
            }
        }
    }
}
