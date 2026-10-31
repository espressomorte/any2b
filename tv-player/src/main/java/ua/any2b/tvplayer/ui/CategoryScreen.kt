package ua.any2b.tvplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.tv.material3.*
import kotlinx.coroutines.flow.collect
import ua.any2b.core.anilist.AnimeCard
import ua.any2b.core.tmdb.TmdbListSort

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun CategoryScreen(
    title: String,
    state: CategoryState,
    onOpen: (AnimeCard) -> Unit,
    onLoadMore: () -> Unit,
    onSort: (TmdbListSort) -> Unit,
    onYearRange: (IntRange?) -> Unit,
    onBack: () -> Unit,
    showRelatedBadge: Boolean = false,
    onFavoriteChanged: () -> Unit = {},
) {
    val gridState = rememberLazyGridState()
    LaunchedEffect(state.sort, state.yearAscending, state.yearRange) {
        gridState.scrollToItem(0)
    }
    LaunchedEffect(gridState, state.items.size, state.hasMore, state.loadingMore, state.error) {
        if (state.hasMore && !state.loadingMore && state.error == null && state.items.isNotEmpty()) {
            snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
                .collect { lastVisible ->
                    if (lastVisible >= state.items.size - 5) onLoadMore()
                }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(AppColors.BackgroundDark).padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            var isBackFocused by remember { mutableStateOf(false) }
            Button(
                onClick = onBack,
                scale = ButtonDefaults.scale(scale = 1f, focusedScale = 1f),
                shape = ButtonDefaults.shape(RoundedCornerShape(12.dp)),
                glow = ButtonDefaults.glow(glow = Glow.None, focusedGlow = Glow(elevationColor = AppColors.NeonCyan, elevation = 10.dp)),
                colors = ButtonDefaults.colors(
                    containerColor = AppColors.PrimaryPurple,
                    contentColor = Color.White,
                    focusedContainerColor = AppColors.PrimaryPurple,
                    focusedContentColor = Color.White
                ),
                modifier = Modifier
                    .zIndex(if (isBackFocused) 1f else 0f)
                    .onFocusChanged { isBackFocused = it.isFocused }
                    .neonGlow(focused = isBackFocused, borderRadius = 12.dp)
            ) { Text("← Назад") }
            Text(title, style = MaterialTheme.typography.headlineLarge)
        }
        if (state.sortable) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf(TmdbListSort.POPULARITY to "Популярність",
                    TmdbListSort.RATING to "Рейтинг").forEach { (sort, label) ->
                    Button(onClick = { onSort(sort) }, enabled = state.sort != sort) {
                        Text(label)
                    }
                }
                YearSortButton(ascending = state.yearAscending,
                    selected = state.sort == TmdbListSort.YEAR,
                    yearRange = state.yearRange,
                    onSort = { onSort(TmdbListSort.YEAR) }, onYearRange = onYearRange)
            }
        }
        if (state.error != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(state.error, color = AppColors.ErrorRed)
                var isRetryFocused by remember { mutableStateOf(false) }
                Button(
                    onClick = onLoadMore,
                    scale = ButtonDefaults.scale(scale = 1f, focusedScale = 1f),
                    shape = ButtonDefaults.shape(RoundedCornerShape(12.dp)),
                    glow = ButtonDefaults.glow(glow = Glow.None, focusedGlow = Glow(elevationColor = AppColors.NeonCyan, elevation = 10.dp)),
                    colors = ButtonDefaults.colors(
                        containerColor = AppColors.PrimaryPurple,
                        contentColor = Color.White,
                        focusedContainerColor = AppColors.PrimaryPurple,
                        focusedContentColor = Color.White
                    ),
                    modifier = Modifier
                        .zIndex(if (isRetryFocused) 1f else 0f)
                        .onFocusChanged { isRetryFocused = it.isFocused }
                        .neonGlow(focused = isRetryFocused, borderRadius = 12.dp)
                ) { Text("Повторити") }
            }
        }
        if (state.items.isEmpty() && !state.loadingMore && state.error == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("У цій категорії поки що немає тайтлів", color = AppColors.TextSecondary)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(5),
                state = gridState,
                modifier = Modifier.weight(1f).graphicsLayer { clip = false },
                contentPadding = PaddingValues(top = 12.dp, bottom = 32.dp),
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
                    item(key = "loading") { Text("Завантаження...", color = AppColors.TextSecondary) }
                }
            }
        }
    }
}
