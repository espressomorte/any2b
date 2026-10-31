package ua.any2b.tvplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.shape.RoundedCornerShape
import kotlinx.coroutines.flow.collect
import ua.any2b.core.tmdb.TmdbListSort
import ua.any2b.core.tmdb.TmdbTitle

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TmdbListScreen(state: TmdbListState, onOpen: (TmdbTitle) -> Unit,
    onSort: (TmdbListSort) -> Unit, onYearRange: (IntRange?) -> Unit,
    onLoadMore: () -> Unit, onBack: () -> Unit) {
    val gridState = rememberLazyGridState()
    LaunchedEffect(state.category, state.sort, state.yearAscending, state.yearRange) {
        gridState.scrollToItem(0)
    }
    LaunchedEffect(gridState, state.titles.size, state.hasMore, state.loading, state.error) {
        if (state.hasMore && !state.loading && state.error == null) {
            snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
                .collect { lastVisible -> if (lastVisible >= state.titles.size - 5) onLoadMore() }
        }
    }
    val heading = when (state.category) {
        "history" -> "Історія переглядів"
        "recommendations" -> "Варто переглянути"
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
        if (state.category !in setOf("favorites", "history", "recommendations", "upcoming")) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf(TmdbListSort.POPULARITY to "Популярність",
                    TmdbListSort.RATING to "Рейтинг").forEach { (sort, label) ->
                    Button(onClick = { onSort(sort) },
                        enabled = state.sort != sort) { Text(label) }
                }
                YearSortButton(ascending = state.yearAscending, yearRange = state.yearRange,
                    selected = state.sort == TmdbListSort.YEAR,
                    onSort = { onSort(TmdbListSort.YEAR) }, onYearRange = onYearRange)
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

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun YearSortButton(ascending: Boolean, selected: Boolean, yearRange: IntRange?,
    onSort: () -> Unit, onYearRange: (IntRange?) -> Unit) {
    var showPeriods by remember { mutableStateOf(false) }
    var decade by remember { mutableStateOf<IntRange?>(null) }
    var focused by remember { mutableStateOf(false) }
    val currentYear = remember { java.util.Calendar.getInstance().get(java.util.Calendar.YEAR) }
    val label = buildString {
        append("Рік випуску ")
        append(if (ascending && selected) "↑" else "↓")
        yearRange?.let { append(" · ${it.first}–${it.last}") }
    }
    Card(onClick = onSort, onLongClick = { showPeriods = true },
        colors = CardDefaults.colors(containerColor = if (selected) AppColors.PrimaryPurple
            else AppColors.SurfaceCard,
            focusedContainerColor = AppColors.NeonCyanLight),
        modifier = Modifier.heightIn(min = 44.dp).onFocusChanged { focused = it.isFocused }) {
        Text(label, modifier = Modifier.padding(horizontal = 18.dp, vertical = 11.dp),
            color = if (focused) AppColors.ButtonDarkText else Color.White)
    }
    if (showPeriods) Dialog(onDismissRequest = { showPeriods = false; decade = null }) {
        Column(Modifier.width(380.dp).heightIn(max = 570.dp)
            .background(AppColors.SurfaceDark, RoundedCornerShape(16.dp)).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (decade == null) "Виберіть період" else
                "Виберіть рік · ${decade!!.first}–${decade!!.last}",
                style = MaterialTheme.typography.titleLarge, color = Color.White)
            if (decade == null) Text("OK — період · утримуйте OK — рік",
                color = AppColors.TextSecondary)
            Column(Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (decade == null) {
                    YearChoice("Усі роки · 1920–$currentYear", {
                        onYearRange(null); showPeriods = false
                    })
                    for (start in (currentYear / 10 * 10) downTo 1920 step 10) {
                        val range = start..minOf(start + 9, currentYear)
                        YearChoice("${range.first}–${range.last}", {
                            onYearRange(range); showPeriods = false
                        }, onLongClick = { decade = range })
                    }
                } else {
                    for (year in decade!!.last downTo decade!!.first) {
                        YearChoice(year.toString(), {
                            onYearRange(year..year); showPeriods = false; decade = null
                        })
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun YearChoice(label: String, onClick: () -> Unit, onLongClick: () -> Unit = {}) {
    var focused by remember { mutableStateOf(false) }
    Card(onClick = onClick, onLongClick = onLongClick,
        colors = CardDefaults.colors(containerColor = AppColors.SurfaceCard,
            focusedContainerColor = AppColors.NeonCyanLight),
        modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused }) {
        Text(label, modifier = Modifier.padding(12.dp),
            color = if (focused) AppColors.ButtonDarkText else Color.White)
    }
}
