package ua.any2b.tvplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.blur
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.BorderStroke
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import java.io.File
import ua.any2b.core.tmdb.TmdbEpisode
import ua.any2b.core.tmdb.TmdbMediaType
import ua.any2b.core.tmdb.TmdbTitle
import ua.any2b.tvplayer.R
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalFoundationApi::class)
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
    onRemoveFromHistory: (TmdbTitle) -> Unit = {},
    onSelectWatchlistSeason: (TmdbTitle, Int) -> Unit = { _, _ -> },
    onOpenEpisode: (TmdbTitle, TmdbEpisode) -> Unit = { title, _ -> onOpen(title) },
    onContinueWatchlist: (TmdbTitle, TmdbEpisode?) -> Unit = { title, episode ->
        if (episode == null) onOpen(title) else onOpenEpisode(title, episode)
    },
) {
    val listState = rememberLazyListState()
    val settingsFocusRequester = remember { FocusRequester() }
    val watchlistArrowFocusRequester = remember { FocusRequester() }
    val continueFocusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    var historyMenuTitle by remember { mutableStateOf<TmdbTitle?>(null) }
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
    val restoreWatchlist: () -> Unit = {
        alignedSection = "watchlist"
        scope.launch { listState.scrollToItem(0) }
    }
    if (animationOnly && state.initialHomeLoading) {
        AnimariumLoadingScreen()
    } else BoxWithConstraints(Modifier.fillMaxSize().background(AppColors.BackgroundDark)) {
        val watchlistViewportHeight = maxHeight
        Column(Modifier.fillMaxSize().then(if (animationOnly) Modifier
            else Modifier.padding(horizontal = 32.dp, vertical = 24.dp)),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (!animationOnly) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically) {
                HomeActionButton(FaIcons.SEARCH_PLUS, "Пошук тайтла через TMDB", onClick = onSearchTitle)
                Spacer(Modifier.width(12.dp))
                HomeActionButton(FaIcons.COG, "Налаштування", onClick = onSettings)
                Spacer(Modifier.width(12.dp))
                OutlinedButton(onClick = onBack) { Text("Назад") }
            }
            }
            CompositionLocalProvider(LocalBringIntoViewSpec provides
                if (animationOnly) MinimumBringIntoViewSpec else LocalBringIntoViewSpec.current) {
            LazyColumn(Modifier.fillMaxWidth().weight(1f), state = listState,
                verticalArrangement = Arrangement.spacedBy(24.dp),
                contentPadding = PaddingValues(bottom = if (animationOnly) 0.dp else 32.dp)) {
            if (animationOnly) {
                if (state.watchlist.isNotEmpty()) item(key = "watchlist") {
                    WatchlistHomeSection(state.watchlist, onSelectWatchlistSeason,
                        onOpenEpisode, onContinueWatchlist,
                        onSectionFocus = restoreWatchlist,
                        modifier = Modifier.height(watchlistViewportHeight),
                        settingsFocusRequester = settingsFocusRequester,
                        arrowFocusRequester = watchlistArrowFocusRequester,
                        continueFocusRequester = continueFocusRequester)
                }
                state.mainSections.filter { it.enabled }.forEach { section ->
                    when (section.id) {
                        "history" -> if (state.history.isNotEmpty()) item(key = "history") {
                            TmdbLibraryRow(section.title, state.history, onOpen,
                                onMore = { onMore("history") },
                                onLongClick = { historyMenuTitle = it },
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
                if (state.watchlist.isNotEmpty()) item(key = "watchlist") {
                    WatchlistHomeSection(state.watchlist, onSelectWatchlistSeason,
                        onOpenEpisode, onContinueWatchlist,
                        onSectionFocus = { alignSection("watchlist") })
                }
                if (state.history.isNotEmpty()) item(key = "history") {
                    TmdbLibraryRow("Історія переглядів", state.history, onOpen,
                        onMore = { onMore("history") },
                        onLongClick = { historyMenuTitle = it },
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
            historyMenuTitle?.let { title ->
            Dialog(onDismissRequest = {
                historyMenuTitle = null
            }) {
                Column(Modifier.width(480.dp).background(AppColors.SurfaceCard,
                    RoundedCornerShape(16.dp)).padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(title.title, style = MaterialTheme.typography.titleLarge,
                        color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Button(onClick = {
                        onRemoveFromHistory(title)
                        historyMenuTitle = null
                    }, modifier = Modifier.fillMaxWidth()) {
                        Text("Вилучити з історії")
                    }
                    OutlinedButton(onClick = {
                        historyMenuTitle = null
                    },
                        modifier = Modifier.fillMaxWidth()) { Text("Скасувати") }
                }
            }
            }
        }
        if (animationOnly) {
            FloatingIconButton(FaIcons.COG, "Налаштування", onClick = onSettings,
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 18.dp, end = 28.dp),
                focusRequester = settingsFocusRequester,
                navigateDownTo = if (state.watchlist.isEmpty()) null
                    else if (state.watchlist.size > 1) watchlistArrowFocusRequester
                    else continueFocusRequester)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
private object MinimumBringIntoViewSpec : BringIntoViewSpec {
    override fun calculateScrollDistance(
        offset: Float,
        size: Float,
        containerSize: Float,
    ): Float {
        val trailingEdge = offset + size
        return when {
            offset >= 0f && trailingEdge <= containerSize -> 0f
            offset < 0f && trailingEdge > containerSize -> 0f
            abs(offset) < abs(trailingEdge - containerSize) -> offset
            else -> trailingEdge - containerSize
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun FloatingIconButton(
    glyph: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    glyphSize: androidx.compose.ui.unit.TextUnit = 23.sp,
    focusRequester: FocusRequester? = null,
    navigateUpTo: FocusRequester? = null,
    navigateDownTo: FocusRequester? = null,
) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        modifier = modifier
            .then(focusRequester?.let(Modifier::focusRequester) ?: Modifier)
            .focusProperties {
                navigateUpTo?.let { up = it }
                navigateDownTo?.let { down = it }
            }
            .size(50.dp).semantics { contentDescription = description }
            .onFocusChanged { focused = it.isFocused },
        scale = ClickableSurfaceDefaults.scale(scale = 1f, focusedScale = 1f),
        border = ClickableSurfaceDefaults.border(
            border = Border.None,
            focusedBorder = Border(BorderStroke(2.dp, AppColors.NeonCyan), shape = CircleShape)
        ),
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color.Transparent
        )
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val foreground = if (focused) AppColors.NeonCyan else AppColors.TextLight
            if (glyph.firstOrNull()?.code in 0xE000..0xF8FF) {
                FontAwesomeIcon(glyph, color = Color.Black, size = (glyphSize.value + 4f).sp)
                FontAwesomeIcon(glyph, color = foreground, size = glyphSize)
            } else {
                Text(glyph, color = Color.Black, fontSize = (glyphSize.value + 4f).sp)
                Text(glyph, color = foreground, fontSize = glyphSize)
            }
        }
    }
}

@Composable
private fun WatchlistHomeSection(
    items: List<TmdbWatchlistHomeItem>,
    onSelectSeason: (TmdbTitle, Int) -> Unit,
    onOpenEpisode: (TmdbTitle, TmdbEpisode) -> Unit,
    onContinue: (TmdbTitle, TmdbEpisode?) -> Unit,
    onSectionFocus: () -> Unit,
    modifier: Modifier = Modifier,
    settingsFocusRequester: FocusRequester? = null,
    arrowFocusRequester: FocusRequester? = null,
    continueFocusRequester: FocusRequester? = null,
) {
    var selectedKey by remember {
        mutableStateOf(items.firstOrNull()?.let { "${it.entry.title.type}:${it.entry.title.id}" })
    }
    val selectedIndex = items.indexOfFirst {
        "${it.entry.title.type}:${it.entry.title.id}" == selectedKey
    }.takeIf { it >= 0 } ?: 0
    val selected = items[selectedIndex]
    val title = selected.entry.title
    var showSeasonPicker by remember { mutableStateOf(false) }
    val darkFill = Color(0xFF090B0F)
    BoxWithConstraints(modifier.fillMaxWidth().focusGroup().onFocusChanged {
        if (it.hasFocus) onSectionFocus()
    }) {
        val watchlistViewportHeight = maxHeight
        AsyncImage(model = title.backdropUrl, contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.align(Alignment.TopEnd)
                .fillMaxWidth(0.75f).aspectRatio(16f / 9f))
        // Fade the backdrop inside its own bounds so its lower edge dissolves
        // before the episode carousel instead of ending as a visible rectangle.
        Box(Modifier.align(Alignment.TopEnd).fillMaxWidth(0.75f).aspectRatio(16f / 9f)
            .background(Brush.verticalGradient(colorStops = arrayOf(
                0f to Color.Transparent,
                0.58f to Color.Transparent,
                0.86f to darkFill.copy(alpha = 0.72f),
                1f to darkFill,
            ))))
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(
            0f to darkFill,
            0.28f to darkFill,
            0.36f to darkFill.copy(alpha = 0.9f),
            0.43f to darkFill.copy(alpha = 0.35f),
            0.48f to Color.Transparent,
            1f to Color.Transparent)))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
            0f to Color.Transparent,
            0.58f to Color.Transparent,
            0.76f to darkFill.copy(alpha = 0.28f),
            0.9f to darkFill.copy(alpha = 0.82f),
            1f to darkFill)))

        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(Modifier.fillMaxWidth().height(watchlistViewportHeight * 0.58f)) {
                if (items.size > 1) {
                    Row(Modifier.align(Alignment.TopEnd).padding(top = 72.dp, end = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FloatingIconButton("‹", "Попередній тайтл", onClick = {
                            val previous = (selectedIndex - 1 + items.size) % items.size
                            selectedKey = "${items[previous].entry.title.type}:${items[previous].entry.title.id}"
                            onSectionFocus()
                        }, glyphSize = 30.sp, navigateUpTo = settingsFocusRequester)
                        FloatingIconButton("›", "Наступний тайтл", onClick = {
                            val next = (selectedIndex + 1) % items.size
                            selectedKey = "${items[next].entry.title.type}:${items[next].entry.title.id}"
                            onSectionFocus()
                        }, glyphSize = 30.sp, focusRequester = arrowFocusRequester,
                            navigateUpTo = settingsFocusRequester)
                    }
                }
                Column(Modifier.align(Alignment.BottomStart).fillMaxWidth(0.36f)
                    .padding(start = 40.dp, end = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    if (selected.logoUrl != null) {
                        var verticalLogo by remember(selected.logoUrl) { mutableStateOf(false) }
                        // Keep the logo inside the left information column so it never
                        // overlaps the confined backdrop on narrower TV layouts.
                        val logoWidth = if (verticalLogo) 114.dp else 300.dp
                        val logoHeight = if (verticalLogo) 140.dp else 86.dp
                        Box(Modifier.width(logoWidth).height(logoHeight),
                            contentAlignment = Alignment.Center) {
                            AsyncImage(model = selected.logoUrl, contentDescription = null,
                                contentScale = ContentScale.Fit,
                                colorFilter = ColorFilter.tint(AppColors.PrimaryPurple.copy(alpha = 0.44f)),
                                modifier = Modifier.offset(x = 1.dp, y = 2.dp)
                                    .blur(3.dp).fillMaxSize())
                            AsyncImage(model = selected.logoUrl, contentDescription = title.title,
                                contentScale = ContentScale.Fit,
                                onSuccess = { result ->
                                    val image = result.result.drawable
                                    verticalLogo = image.intrinsicHeight > image.intrinsicWidth
                                },
                                modifier = Modifier.fillMaxSize())
                        }
                    }
                    Text(title.ukrainianTitle?.takeIf(String::isNotBlank) ?: title.title,
                        style = MaterialTheme.typography.titleLarge, color = Color.White,
                        fontWeight = FontWeight.Bold, maxLines = 1,
                        overflow = TextOverflow.Ellipsis)
                    Text(listOfNotNull(title.year?.toString(),
                        if (title.type == TmdbMediaType.TV) "Серіал" else "Фільм",
                        if (selected.entry.source == ua.any2b.tvplayer.data.TmdbWatchlistEntry.Source.IN_PROGRESS)
                            "Продовжити перегляд" else "Заплановано")
                        .joinToString(" · "), color = AppColors.TextSecondary,
                        style = MaterialTheme.typography.labelMedium, maxLines = 1,
                        overflow = TextOverflow.Ellipsis)
                    val countryNames = title.originCountries.mapNotNull { code ->
                        Locale.forLanguageTag("und-${code.uppercase(Locale.ROOT)}")
                            .getDisplayCountry(Locale.forLanguageTag("uk"))
                            .takeIf(String::isNotBlank)
                    }
                    val season = selected.seasons.firstOrNull {
                        it.number == selected.selectedSeasonNumber
                    }
                    val seasonStatus = if (title.type == TmdbMediaType.TV && season != null) {
                        val aired = selected.airedEpisodeCount
                        val count = selected.selectedSeasonEpisodeCount ?: season.episodeCount
                        if (aired != null && count != null)
                            "Сезон ${season.number} · $aired з $count в етері"
                        else "Сезон ${season.number}"
                    } else null
                    val titleFacts = (countryNames + listOfNotNull(seasonStatus)).joinToString(" · ")
                    if (titleFacts.isNotBlank()) {
                        Text(titleFacts, color = AppColors.TextSecondary,
                            style = MaterialTheme.typography.labelMedium, maxLines = 1,
                            overflow = TextOverflow.Ellipsis)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = {
                            onContinue(title.copy(matchedSeasonNumber = selected.selectedSeasonNumber),
                                selected.nextEpisodes.firstOrNull()?.episode)
                        }, modifier = Modifier
                            .then(continueFocusRequester?.let(Modifier::focusRequester) ?: Modifier)
                            .focusProperties { settingsFocusRequester?.let { up = it } }) {
                            Text("Продовжити перегляд")
                        }
                        if (title.type == TmdbMediaType.TV && selected.seasons.isNotEmpty()) {
                            OutlinedButton(onClick = { showSeasonPicker = true },
                                modifier = Modifier.focusProperties {
                                    settingsFocusRequester?.let { up = it }
                                }) {
                                Text("Сезон ${selected.selectedSeasonNumber}", maxLines = 1)
                            }
                        }
                    }
                }
            }
            if (title.type == TmdbMediaType.TV) Spacer(Modifier.weight(1f))
            if (title.type == TmdbMediaType.TV) {
                Column(Modifier.fillMaxWidth()) {
                    if (selected.episodesLoading) {
                        Text("Завантаження серій…", color = AppColors.TextSecondary,
                            modifier = Modifier.padding(horizontal = 4.dp))
                    } else if (selected.nextEpisodes.isNotEmpty()) {
                        val firstUnwatched = selected.nextEpisodes.indexOfFirst { !it.watched }
                            .coerceAtLeast(0)
                        val episodeListState = rememberLazyListState()
                        LaunchedEffect(selected.entry.title.id, selected.selectedSeasonNumber,
                            selected.nextEpisodes) {
                            if (firstUnwatched > 0) episodeListState.scrollToItem(firstUnwatched)
                        }
                        LazyRow(state = episodeListState,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp)) {
                            items(selected.nextEpisodes, key = {
                                "${it.episode.seasonNumber}:${it.episode.episodeNumber}"
                            }) { card ->
                                Card(onClick = { onOpenEpisode(title, card.episode) },
                                    scale = CardDefaults.scale(scale = 1f, focusedScale = 1f),
                                    colors = CardDefaults.colors(
                                        containerColor = if (card.watched) AppColors.SurfaceDark
                                            else AppColors.SkeletonBg,
                                        focusedContainerColor = if (card.watched) AppColors.AccentPurple
                                            else AppColors.SkeletonBg),
                                    shape = CardDefaults.shape(RoundedCornerShape(10.dp)),
                                    modifier = Modifier.width(204.dp)) {
                                    Column {
                                        AsyncImage(model = card.cachedImagePath?.let(::File)
                                            ?: card.episode.stillUrl,
                                            contentDescription = card.episode.name,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                                                .background(AppColors.SurfaceCard)
                                                .then(if (card.watched) Modifier.blur(1.dp) else Modifier))
                                        Text(if (card.watched) "Переглянуто · " +
                                            "S${card.episode.seasonNumber} · E${card.episode.episodeNumber}"
                                            else "S${card.episode.seasonNumber} · E${card.episode.episodeNumber}",
                                            color = if (card.watched) AppColors.TextWatched
                                                else AppColors.TextSecondary,
                                            style = MaterialTheme.typography.labelMedium,
                                            modifier = Modifier.padding(start = 8.dp, top = 7.dp))
                                        Text(card.episode.name, color = Color.White,
                                            fontWeight = FontWeight.Medium, maxLines = 2,
                                            minLines = 2, overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                .heightIn(min = 38.dp))
                                    }
                                }
                            }
                        }
                    } else {
                        Text("Немає доступних даних про наступні серії",
                            color = AppColors.TextSecondary,
                            modifier = Modifier.padding(horizontal = 4.dp))
                    }
                }
            }
        }
        if (showSeasonPicker) Dialog(onDismissRequest = { showSeasonPicker = false }) {
            Column(Modifier.width(520.dp).background(AppColors.SurfaceCard,
                RoundedCornerShape(16.dp)).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Оберіть сезон", style = MaterialTheme.typography.titleLarge,
                    color = Color.White)
                LazyColumn(contentPadding = PaddingValues(vertical = 4.dp)) {
                    items(selected.seasons.filter { it.number > 0 }, key = { it.number }) { season ->
                        Button(onClick = {
                            showSeasonPicker = false
                            onSelectSeason(title, season.number)
                        }, modifier = Modifier.fillMaxWidth()) {
                            Text(season.name ?: "Сезон ${season.number}",
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
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
    showMore: Boolean = titles.size > 13, onSectionFocus: () -> Unit = {},
    onLongClick: ((TmdbTitle) -> Unit)? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = Color.White,
            modifier = Modifier.padding(horizontal = 4.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)) {
            items(titles.take(13), key = { "${it.type}:${it.id}" }) { item ->
                TmdbPosterCard(item, onClick = { onOpen(item) }, onFocused = onSectionFocus,
                    onLongClick = onLongClick?.let { callback -> { callback(item) } })
            }
            if (showMore) item(key = "more") { MoreCard(onClick = onMore) }
        }
    }
}
