package ua.any2b.tvplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import ua.any2b.tvplayer.R
import kotlinx.coroutines.launch

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TmdbLibraryScreen(state: TmdbLibraryState, onOpen: (TmdbTitle) -> Unit,
    onRetry: (TmdbMediaType) -> Unit, onMore: (String) -> Unit, onBack: () -> Unit,
    animationOnly: Boolean = false,
    onSearchTitle: () -> Unit = {},
    onOpenAllTmdb: () -> Unit = {},
    onSettings: () -> Unit = {},
    onRetryMal: () -> Unit = {},
    onRetryAniList: () -> Unit = {},
    onOpenFavorite: (TmdbTitle) -> Unit = onOpen,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var alignedSection by remember { mutableStateOf<String?>(null) }
    val alignSection: (String) -> Unit = { key ->
        if (alignedSection != key) {
            alignedSection = key
            scope.launch {
                listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key }
                    ?.let { listState.animateScrollToItem(it.index) }
            }
        }
    }
    if (animationOnly && state.initialHomeLoading) {
        AnimariumLoadingScreen()
    } else Column(Modifier.fillMaxSize().background(AppColors.BackgroundDark)
        .padding(horizontal = 32.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically) {
            if (animationOnly) {
                HomeActionButton(FaIcons.SEARCH_PLUS, "Пошук тайтла через TMDB", onClick = onSearchTitle)
                Spacer(Modifier.width(12.dp))
                HomeActionButton(null, "Усі тайтли TMDB", vector = R.drawable.fa_rebel,
                    onClick = onOpenAllTmdb)
                Spacer(Modifier.width(12.dp))
                HomeActionButton(FaIcons.COG, "Налаштування", onClick = onSettings)
            } else OutlinedButton(onClick = onBack) { Text("Назад") }
        }
        LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(24.dp),
            contentPadding = PaddingValues(bottom = 32.dp)) {
            if (animationOnly) {
                state.mainSections.filter { it.enabled }.forEach { section ->
                    when (section.id) {
                        "history" -> if (state.history.isNotEmpty()) item(key = "history") {
                            TmdbLibraryRow(section.title, state.history, onOpen,
                                onMore = { onMore("history") },
                                onSectionFocus = { alignSection("history") })
                        }
                        "favorites" -> if (state.favorites.isNotEmpty()) item(key = "favorites") {
                            TmdbLibraryRow(section.title, state.favorites, onOpenFavorite,
                                onMore = { onMore("favorites") },
                                onSectionFocus = { alignSection("favorites") })
                        }
                        "recommendations" -> if (state.recommendations.isNotEmpty())
                            item(key = "recommendations") {
                                TmdbLibraryRow(section.title, state.recommendations, onOpen,
                                    onMore = { onMore("recommendations") },
                                    onSectionFocus = { alignSection("recommendations") })
                            }
                        "animation_tv" -> item(key = "animation_tv") {
                            TmdbPopularRow(section.title, state.tv, onOpen,
                                onRetry = { onRetry(TmdbMediaType.TV) },
                                onMore = { onMore("animation_tv") },
                                onSectionFocus = { alignSection("animation_tv") })
                        }
                        "animation_movie" -> item(key = "animation_movie") {
                            TmdbPopularRow(section.title, state.movies, onOpen,
                                onRetry = { onRetry(TmdbMediaType.MOVIE) },
                                onMore = { onMore("animation_movie") },
                                onSectionFocus = { alignSection("animation_movie") })
                        }
                        "mal_top" -> item(key = "mal_top") {
                            MalTopRow(section.title, state.malTop, onOpen,
                                onMore = { onMore("mal_top") }, onRetry = onRetryMal,
                                onSectionFocus = { alignSection("mal_top") })
                        }
                        "trending" -> item(key = "trending") {
                            AniListHomeRow(section.title, state.trending, onOpen,
                                onMore = { onMore("trending") }, onRetry = onRetryAniList,
                                onSectionFocus = { alignSection("trending") })
                        }
                        "popular_season" -> item(key = "popular_season") {
                            AniListHomeRow(section.title, state.popularSeason, onOpen,
                                onMore = { onMore("popular_season") }, onRetry = onRetryAniList,
                                onSectionFocus = { alignSection("popular_season") })
                        }
                    }
                }
            } else {
                if (state.history.isNotEmpty()) item(key = "history") {
                    TmdbLibraryRow("Історія переглядів", state.history, onOpen,
                        onMore = { onMore("history") },
                        onSectionFocus = { alignSection("history") })
                }
                item(key = "tv") {
                    TmdbPopularRow("Популярні серіали", state.tv, onOpen,
                        onRetry = { onRetry(TmdbMediaType.TV) }, onMore = { onMore("tv") },
                        onSectionFocus = { alignSection("tv") })
                }
                item(key = "movies") {
                    TmdbPopularRow("Популярні фільми", state.movies, onOpen,
                        onRetry = { onRetry(TmdbMediaType.MOVIE) }, onMore = { onMore("movie") },
                        onSectionFocus = { alignSection("movies") })
                }
                if (state.favorites.isNotEmpty()) item(key = "favorites") {
                    TmdbLibraryRow("Обрані тайтли", state.favorites, onOpenFavorite,
                        onMore = { onMore("favorites") },
                        onSectionFocus = { alignSection("favorites") })
                }
            }
        }
    }
}

@Composable
private fun AniListHomeRow(title: String, state: AniListHomeState,
    onOpen: (TmdbTitle) -> Unit, onMore: () -> Unit, onRetry: () -> Unit,
    onSectionFocus: () -> Unit) {
    when (state) {
        AniListHomeState.Loading -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = Color.White)
            Text("Завантаження…", color = AppColors.TextSecondary)
        }
        is AniListHomeState.Error -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = Color.White)
            Text(state.message, color = AppColors.ErrorRed)
            Button(onClick = onRetry) { Text("Повторити") }
        }
        is AniListHomeState.Results -> if (state.titles.isNotEmpty())
            TmdbLibraryRow(title, state.titles, onOpen, onMore,
                onSectionFocus = onSectionFocus)
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun MalTopRow(title: String, state: MalTopState, onOpen: (TmdbTitle) -> Unit,
    onMore: () -> Unit, onRetry: () -> Unit, onSectionFocus: () -> Unit = {}) {
    when (state) {
        MalTopState.Loading -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = Color.White)
            Text("Завантаження…", color = AppColors.TextSecondary)
        }
        is MalTopState.Error -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = Color.White)
            Text(state.message, color = AppColors.ErrorRed)
            Button(onClick = onRetry) { Text("Повторити") }
        }
        is MalTopState.Results -> if (state.titles.isNotEmpty())
            TmdbLibraryRow(title, state.titles, onOpen, onMore,
                showMore = state.hasMore || state.titles.size > 13,
                onSectionFocus = onSectionFocus)
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TmdbPopularRow(title: String, state: TmdbPopularState,
    onOpen: (TmdbTitle) -> Unit, onRetry: () -> Unit, onMore: () -> Unit,
    onSectionFocus: () -> Unit = {}) {
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
            TmdbLibraryRow(title, state.titles, onOpen, onMore,
                onSectionFocus = onSectionFocus)
    }
}

@Composable
private fun TmdbLibraryRow(title: String, titles: List<TmdbTitle>,
    onOpen: (TmdbTitle) -> Unit, onMore: () -> Unit,
    showMore: Boolean = titles.size > 13, onSectionFocus: () -> Unit = {}) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = Color.White,
            modifier = Modifier.padding(horizontal = 4.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)) {
            items(titles.take(13), key = { "${it.type}:${it.id}" }) { item ->
                TmdbPosterCard(item, onClick = { onOpen(item) }, onFocused = onSectionFocus)
            }
            if (showMore) item(key = "more") { MoreCard(onClick = onMore) }
        }
    }
}
