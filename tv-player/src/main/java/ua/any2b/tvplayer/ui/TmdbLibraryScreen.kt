package ua.any2b.tvplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.ui.tooling.preview.Preview
import ua.any2b.tvplayer.data.HomeSectionSetting
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.draw.blur
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.BorderStroke
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Glow
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.OutlinedButtonDefaults
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
import kotlin.math.roundToInt

internal val LocalHomeRowPosterWidth = compositionLocalOf { 160.dp }

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TmdbLibraryScreen(state: TmdbLibraryState, onOpen: (TmdbTitle) -> Unit,
    onRetry: (TmdbMediaType) -> Unit, onMore: (String) -> Unit, onBack: () -> Unit,
    onHome: () -> Unit = onBack,
    animationOnly: Boolean = false,
    homeResetToken: Long = 0L,
    onSearchTitle: () -> Unit = {},
    onOpenAllTmdb: () -> Unit = {},
    onSettings: () -> Unit = {},
    onRetryMal: () -> Unit = {},
    onRetryAniList: () -> Unit = {},
    onRetryTolokaRss: () -> Unit = {},
    onLoadTolokaRss: () -> Unit = onRetryTolokaRss,
    onOpenFavorite: (TmdbTitle) -> Unit = onOpen,
    onRemoveFromHistory: (TmdbTitle) -> Unit = {},
    onSelectWatchlistSeason: (TmdbTitle, Int) -> Unit = { _, _ -> },
    onSelectWatchlistVoice: (TmdbTitle, Int, Int) -> Unit = { _, _, _ -> },
    onOpenEpisode: (TmdbTitle, TmdbEpisode) -> Unit = { title, _ -> onOpen(title) },
    onContinueWatchlist: (TmdbTitle, TmdbEpisode?) -> Unit = { title, episode ->
        if (episode == null) onOpen(title) else onOpenEpisode(title, episode)
    },
    peopleState: TmdbPeopleState = TmdbPeopleState.Loading,
    onLoadPeople: (TmdbTitle) -> Unit = {},
    onRetryPeople: (TmdbTitle) -> Unit = {},
    onOpenPerson: (ua.any2b.core.tmdb.TmdbPersonCredit) -> Unit = {},
    personReturnToken: Int = 0,
    homeReturnToken: Int = 0,
) {
    val listState = remember(homeResetToken) { LazyListState() }
    val drawerHomeFocusRequester = remember { FocusRequester() }
    val drawerMoviesFocusRequester = remember { FocusRequester() }
    val drawerSearchFocusRequester = remember { FocusRequester() }
    val drawerSettingsFocusRequester = remember { FocusRequester() }
    var drawerReturnFocusRequester by remember { mutableStateOf<FocusRequester?>(null) }
    var drawerOpen by rememberSaveable { mutableStateOf(false) }
    var selectedWatchlistKey by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedPersonId by rememberSaveable { mutableStateOf<Int?>(null) }
    var lastEpisodeFocusKey by rememberSaveable { mutableStateOf<String?>(null) }
    var lastEpisodeFocusNumber by rememberSaveable { mutableStateOf<Int?>(null) }
    val selectedWatchlistTitle = state.watchlist.firstOrNull { item ->
        "${item.entry.title.type}:${item.entry.title.id}" == selectedWatchlistKey
    }?.entry?.title ?: state.watchlist.firstOrNull()?.entry?.title
    LaunchedEffect(selectedWatchlistTitle?.type, selectedWatchlistTitle?.id,
        selectedWatchlistTitle?.genreIds) {
        selectedWatchlistTitle?.let(onLoadPeople)
    }
    val homeEntryFocusRequester = remember { FocusRequester() }
    var focusedHomeResetToken by remember { mutableStateOf<Long?>(null) }
    var focusedHomeTargetKey by remember { mutableStateOf<String?>(null) }
    val peopleVisible = state.watchlist.isNotEmpty() &&
        (peopleState !is TmdbPeopleState.Ready || peopleState.people.isNotEmpty())
    val lazyItemKeys = homeLazyItemKeys(state, animationOnly, peopleVisible)
    val homeFocusTargetKey = if (animationOnly && state.watchlist.isNotEmpty()) "watchlist"
        else if (animationOnly) state.mainSections.firstOrNull { section ->
            section.enabled && state.hasHomeRowContent(section.id)
        }?.id
        else listOf("toloka_video", "history", "tv", "movies", "favorites")
            .firstOrNull { state.hasHomeRowContent(it) }
    LaunchedEffect(homeResetToken, homeFocusTargetKey, lazyItemKeys,
        state.initialHomeLoading) {
        if (animationOnly && state.initialHomeLoading) return@LaunchedEffect
        val targetKey = homeFocusTargetKey ?: return@LaunchedEffect
        if (focusedHomeResetToken == homeResetToken && focusedHomeTargetKey == targetKey)
            return@LaunchedEffect
        val targetIndex = lazyItemKeys.indexOf(targetKey)
        if (targetIndex < 0) return@LaunchedEffect
        listState.scrollToItem(targetIndex)
        repeat(3) {
            withFrameNanos { }
            try {
                homeEntryFocusRequester.requestFocus()
                focusedHomeResetToken = homeResetToken
                focusedHomeTargetKey = targetKey
                return@LaunchedEffect
            } catch (_: IllegalStateException) {
                // Wait for the lazy row and its first card to attach to the focus tree.
            }
        }
    }
    val continueFocusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    var historyMenuTitle by remember { mutableStateOf<TmdbTitle?>(null) }
    var alignedSection by remember { mutableStateOf<String?>(null) }
    val alignSection: (String) -> Unit = { key ->
        if (alignedSection != key) alignedSection = key
    }
    val safeTopPx = with(LocalDensity.current) { 16.dp.roundToPx() }
    LaunchedEffect(alignedSection, homeResetToken) {
        val key = alignedSection ?: return@LaunchedEffect
        val index = lazyItemKeys.indexOf(key)
        if (index < 0) return@LaunchedEffect
        // Let focus search finish before positioning the entire LazyColumn item.
        withFrameNanos { }
        listState.animateScrollToItem(index, if (index == 0) 0 else -safeTopPx)
    }
    LaunchedEffect(homeReturnToken) {
        if (homeReturnToken <= 0) return@LaunchedEffect
        val index = lazyItemKeys.indexOf(alignedSection).takeIf { it >= 0 } ?: return@LaunchedEffect
        listState.scrollToItem(index, if (index == 0) 0 else -safeTopPx)
        repeat(3) {
            withFrameNanos { }
            try {
                drawerReturnFocusRequester?.requestFocus()
                return@LaunchedEffect
            } catch (_: IllegalStateException) { /* Wait for the card to attach. */ }
        }
    }
    val restoreWatchlist: () -> Unit = { alignSection("watchlist") }
    if (animationOnly && state.initialHomeLoading) {
        AnimariumLoadingScreen()
    } else BoxWithConstraints(Modifier.fillMaxSize().background(AppColors.BackgroundDark)) {
        val watchlistViewportHeight = maxHeight
        val posterWidth = ((watchlistViewportHeight - 164.dp) / 1.5f)
            .coerceIn(104.dp, 160.dp)
        Column(Modifier.fillMaxSize().then(if (animationOnly) Modifier
            else Modifier.padding(horizontal = 32.dp, vertical = 24.dp)),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            CompositionLocalProvider(
                LocalHomeRowPosterWidth provides posterWidth,
                LocalBringIntoViewSpec provides MinimumBringIntoViewSpec) {
            LazyColumn(Modifier.fillMaxWidth().weight(1f).onFocusChanged {
                if (it.hasFocus) drawerOpen = false
            }, state = listState,
                verticalArrangement = Arrangement.spacedBy(24.dp),
                contentPadding = PaddingValues(bottom = if (animationOnly)
                    watchlistViewportHeight else 32.dp)) {
            if (animationOnly) {
                if (state.watchlist.isNotEmpty()) item(key = "watchlist") {
                    WatchlistHomeSection(state.watchlist, onSelectWatchlistSeason,
                        onSelectWatchlistVoice,
                        onOpenEpisode, onContinueWatchlist, onOpen,
                        selectedKey = selectedWatchlistKey,
                        onSelectedKeyChange = { selectedWatchlistKey = it },
                        onSectionFocus = restoreWatchlist,
                        modifier = Modifier.height(if (selectedWatchlistTitle?.type == TmdbMediaType.MOVIE)
                            watchlistViewportHeight * 0.82f else watchlistViewportHeight),
                        drawerFocusRequester = drawerHomeFocusRequester,
                        onContentFocusRequester = { drawerReturnFocusRequester = it },
                        lastEpisodeFocusKey = lastEpisodeFocusKey,
                        lastEpisodeFocusNumber = lastEpisodeFocusNumber,
                        onEpisodeFocused = { key, number ->
                            lastEpisodeFocusKey = key
                            lastEpisodeFocusNumber = number
                        },
                        continueFocusRequester = homeEntryFocusRequester.takeIf {
                            homeFocusTargetKey == "watchlist"
                        } ?: continueFocusRequester)
                }
                if (peopleVisible)
                    item(key = "watchlist-people") {
                        TmdbPeopleRow(peopleState, selectedPersonId,
                            onSectionFocus = { alignSection("watchlist-people") },
                            onSelectedPerson = { selectedPersonId = it },
                            onOpen = onOpenPerson,
                            onRetry = { selectedWatchlistTitle?.let(onRetryPeople) },
                            onContentFocusRequester = { drawerReturnFocusRequester = it },
                            returnToken = personReturnToken)
                    }
                state.mainSections.filter { it.enabled }.forEach { section ->
                    when (section.id) {
                        "history" -> if (state.history.isNotEmpty()) item(key = "history") {
                            TmdbLibraryRow(section.title, state.history, onOpen,
                                onMore = { onMore("history") },
                                onLongClick = { historyMenuTitle = it },
                                homeEntryFocusRequester = homeEntryFocusRequester.takeIf {
                                    homeFocusTargetKey == section.id
                                },
                                onSectionFocus = { alignSection("history") },
                                drawerFocusRequester = drawerHomeFocusRequester,
                                onContentFocusRequester = { drawerReturnFocusRequester = it })
                        }
                        "favorites" -> if (state.favorites.isNotEmpty()) item(key = "favorites") {
                            TmdbLibraryRow(section.title, state.favorites, onOpenFavorite,
                                onMore = { onMore("favorites") },
                                homeEntryFocusRequester = homeEntryFocusRequester.takeIf {
                                    homeFocusTargetKey == section.id
                                },
                                onSectionFocus = { alignSection("favorites") },
                                drawerFocusRequester = drawerHomeFocusRequester,
                                onContentFocusRequester = { drawerReturnFocusRequester = it })
                        }
                        "recommendations" -> if (state.recommendations.isNotEmpty())
                            item(key = "recommendations") {
                                TmdbLibraryRow(section.title, state.recommendations, onOpen,
                                    onMore = { onMore("recommendations") },
                                    homeEntryFocusRequester = homeEntryFocusRequester.takeIf {
                                        homeFocusTargetKey == section.id
                                    },
                                    onSectionFocus = { alignSection("recommendations") },
                                    drawerFocusRequester = drawerHomeFocusRequester,
                                    onContentFocusRequester = { drawerReturnFocusRequester = it })
                            }
                        "animation_tv" -> item(key = "animation_tv") {
                            TmdbPopularRow(section.title, state.tv, onOpen,
                                onRetry = { onRetry(TmdbMediaType.TV) },
                                onMore = { onMore("animation_tv") },
                                homeEntryFocusRequester = homeEntryFocusRequester.takeIf {
                                    homeFocusTargetKey == section.id
                                },
                                onSectionFocus = { alignSection("animation_tv") },
                                drawerFocusRequester = drawerHomeFocusRequester,
                                onContentFocusRequester = { drawerReturnFocusRequester = it })
                        }
                        "animation_movie" -> item(key = "animation_movie") {
                            TmdbPopularRow(section.title, state.movies, onOpen,
                                onRetry = { onRetry(TmdbMediaType.MOVIE) },
                                onMore = { onMore("animation_movie") },
                                homeEntryFocusRequester = homeEntryFocusRequester.takeIf {
                                    homeFocusTargetKey == section.id
                                },
                                onSectionFocus = { alignSection("animation_movie") },
                                drawerFocusRequester = drawerHomeFocusRequester,
                                onContentFocusRequester = { drawerReturnFocusRequester = it })
                        }
                        "mal_top" -> item(key = "mal_top") {
                            MalTopRow(section.title, state.malTop, onOpen,
                                onMore = { onMore("mal_top") }, onRetry = onRetryMal,
                                homeEntryFocusRequester = homeEntryFocusRequester.takeIf {
                                    homeFocusTargetKey == section.id
                                },
                                onSectionFocus = { alignSection("mal_top") },
                                drawerFocusRequester = drawerHomeFocusRequester,
                                onContentFocusRequester = { drawerReturnFocusRequester = it })
                        }
                        "trending" -> item(key = "trending") {
                            AniListHomeRow(section.title, state.trending, onOpen,
                                onMore = { onMore("trending") }, onRetry = onRetryAniList,
                                homeEntryFocusRequester = homeEntryFocusRequester.takeIf {
                                    homeFocusTargetKey == section.id
                                },
                                onSectionFocus = { alignSection("trending") },
                                drawerFocusRequester = drawerHomeFocusRequester,
                                onContentFocusRequester = { drawerReturnFocusRequester = it })
                        }
                        "popular_season" -> item(key = "popular_season") {
                            AniListHomeRow(section.title, state.popularSeason, onOpen,
                                onMore = { onMore("popular_season") }, onRetry = onRetryAniList,
                                homeEntryFocusRequester = homeEntryFocusRequester.takeIf {
                                    homeFocusTargetKey == section.id
                                },
                                onSectionFocus = { alignSection("popular_season") },
                                drawerFocusRequester = drawerHomeFocusRequester,
                                onContentFocusRequester = { drawerReturnFocusRequester = it })
                        }
                        "toloka_animation" -> item(key = "toloka_animation") {
                    LaunchedEffect(Unit) { onLoadTolokaRss() }
                            TolokaRssRow(section.title, state.tolokaReleases,
                                animated = true, onOpen = onOpen,
                                onMore = { onMore("toloka_animation") },
                                homeEntryFocusRequester = homeEntryFocusRequester.takeIf {
                                    homeFocusTargetKey == section.id
                                },
                                onRetry = onRetryTolokaRss,
                                onSectionFocus = { alignSection("toloka_animation") },
                                drawerFocusRequester = drawerHomeFocusRequester,
                                onContentFocusRequester = { drawerReturnFocusRequester = it })
                        }
                    }
                }
            } else {
                item(key = "toloka_video") {
                    LaunchedEffect(Unit) { onLoadTolokaRss() }
                    TolokaRssRow("Фільми й серіали Toloka", state.tolokaReleases,
                        animated = false, onOpen = onOpen,
                        onMore = { onMore("toloka_video") },
                        homeEntryFocusRequester = homeEntryFocusRequester.takeIf {
                            homeFocusTargetKey == "toloka_video"
                        },
                        onRetry = onRetryTolokaRss,
                        onSectionFocus = { alignSection("toloka_video") },
                        drawerFocusRequester = drawerHomeFocusRequester,
                        onContentFocusRequester = { drawerReturnFocusRequester = it })
                }
                if (state.history.isNotEmpty()) item(key = "history") {
                    TmdbLibraryRow("Історія переглядів", state.history, onOpen,
                        onMore = { onMore("history") },
                        onLongClick = { historyMenuTitle = it },
                        homeEntryFocusRequester = homeEntryFocusRequester.takeIf {
                            homeFocusTargetKey == "history"
                        },
                        onSectionFocus = { alignSection("history") },
                        drawerFocusRequester = drawerHomeFocusRequester,
                        onContentFocusRequester = { drawerReturnFocusRequester = it })
                }
                item(key = "tv") {
                    TmdbPopularRow("Популярні серіали", state.tv, onOpen,
                        onRetry = { onRetry(TmdbMediaType.TV) }, onMore = { onMore("tv") },
                        homeEntryFocusRequester = homeEntryFocusRequester.takeIf {
                            homeFocusTargetKey == "tv"
                        },
                        onSectionFocus = { alignSection("tv") },
                        drawerFocusRequester = drawerHomeFocusRequester,
                        onContentFocusRequester = { drawerReturnFocusRequester = it })
                }
                item(key = "movies") {
                    TmdbPopularRow("Популярні фільми", state.movies, onOpen,
                        onRetry = { onRetry(TmdbMediaType.MOVIE) }, onMore = { onMore("movie") },
                        homeEntryFocusRequester = homeEntryFocusRequester.takeIf {
                            homeFocusTargetKey == "movies"
                        },
                        onSectionFocus = { alignSection("movies") },
                        drawerFocusRequester = drawerHomeFocusRequester,
                        onContentFocusRequester = { drawerReturnFocusRequester = it })
                }
                if (state.favorites.isNotEmpty()) item(key = "favorites") {
                    TmdbLibraryRow("Обрані тайтли", state.favorites, onOpenFavorite,
                        onMore = { onMore("favorites") },
                        homeEntryFocusRequester = homeEntryFocusRequester.takeIf {
                            homeFocusTargetKey == "favorites"
                        },
                        onSectionFocus = { alignSection("favorites") },
                        drawerFocusRequester = drawerHomeFocusRequester,
                        onContentFocusRequester = { drawerReturnFocusRequester = it })
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
        NavigationDrawer(
            homeFocusRequester = drawerHomeFocusRequester,
            moviesFocusRequester = drawerMoviesFocusRequester,
            searchFocusRequester = drawerSearchFocusRequester,
            settingsFocusRequester = drawerSettingsFocusRequester,
            returnFocusRequester = drawerReturnFocusRequester,
            onReturnToContent = {
                scope.launch {
                    val target = lazyItemKeys.indexOf(homeFocusTargetKey).coerceAtLeast(0)
                    listState.scrollToItem(target)
                    repeat(4) {
                        withFrameNanos { }
                        try {
                            homeEntryFocusRequester.requestFocus()
                            return@launch
                        } catch (_: IllegalStateException) { /* Wait for row composition. */ }
                    }
                }
            },
            expanded = drawerOpen,
            onExpandedChange = { drawerOpen = it },
            onHome = {
                drawerReturnFocusRequester = null
                historyMenuTitle = null
                alignedSection = if (animationOnly) "watchlist" else null
                onHome()
            },
            onMovies = onOpenAllTmdb,
            onSearch = onSearchTitle,
            onSettings = onSettings,
            modifier = Modifier.align(Alignment.CenterStart),
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun NavigationDrawer(
    homeFocusRequester: FocusRequester,
    moviesFocusRequester: FocusRequester,
    searchFocusRequester: FocusRequester,
    settingsFocusRequester: FocusRequester,
    returnFocusRequester: FocusRequester?,
    onReturnToContent: () -> Unit,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onHome: () -> Unit,
    onMovies: () -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val progress by animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 240),
        label = "navigationDrawer",
    )
    val widthPx = with(LocalDensity.current) { 76.dp.roundToPx() }
    Column(
        modifier = modifier
            .offset { IntOffset((-widthPx * (1f - progress)).roundToInt(), 0) }
            .width(76.dp)
            .fillMaxHeight()
            .background(Brush.horizontalGradient(listOf(
                AppColors.BackgroundDark.copy(alpha = 0.98f),
                AppColors.BackgroundDark.copy(alpha = 0.94f),
                AppColors.BackgroundDark.copy(alpha = 0.72f),
            )))
            .padding(top = 20.dp, bottom = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        DrawerIcon(FaIcons.HOME, "Додому", homeFocusRequester, returnFocusRequester,
            onReturnToContent = onReturnToContent,
            onFocus = { onExpandedChange(true) }, onClick = {
                onExpandedChange(false)
                onHome()
            })
        DrawerIcon(FaIcons.FILM, "Фільми TMDB", moviesFocusRequester, returnFocusRequester,
            onReturnToContent = onReturnToContent,
            onFocus = { onExpandedChange(true) }, onClick = {
                onExpandedChange(false)
                onMovies()
            })
        DrawerIcon(FaIcons.SEARCH, "Пошук", searchFocusRequester, returnFocusRequester,
            onReturnToContent = onReturnToContent,
            onFocus = { onExpandedChange(true) }, onClick = {
                onExpandedChange(false)
                onSearch()
            })
        DrawerIcon(FaIcons.COG, "Налаштування", settingsFocusRequester, returnFocusRequester,
            onReturnToContent = onReturnToContent,
            onFocus = { onExpandedChange(true) }, onClick = {
                onExpandedChange(false)
                onSettings()
            })
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun DrawerIcon(
    glyph: String,
    description: String,
    focusRequester: FocusRequester,
    returnFocusRequester: FocusRequester?,
    onReturnToContent: () -> Unit,
    onFocus: () -> Unit,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    Surface(
        onClick = onClick,
        modifier = Modifier
            .size(50.dp)
            .focusRequester(focusRequester)
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionRight) {
                    if (returnFocusRequester != null) {
                        // A LazyRow can dispose the item that last held focus while
                        // the drawer remains focused. Its remembered requester then
                        // has no attached node, so fall back to normal focus search.
                        var restored = false
                        try {
                            returnFocusRequester.requestFocus()
                            restored = true
                        } catch (_: IllegalStateException) {
                            // The remembered list item was disposed.
                        }
                        if (!restored && !focusManager.moveFocus(FocusDirection.Right))
                            onReturnToContent()
                        true
                    } else {
                        if (!focusManager.moveFocus(FocusDirection.Right)) onReturnToContent()
                        true
                    }
                } else false
            }
            .semantics { contentDescription = description }
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocus()
            }
            .neonGlow(focused, borderRadius = 28.dp, blurRadius = 13.dp, alpha = 0.48f),
        scale = ClickableSurfaceDefaults.scale(scale = 1f, focusedScale = 1.08f),
        border = ClickableSurfaceDefaults.border(border = Border.None, focusedBorder = Border.None),
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color.Transparent,
        ),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            FontAwesomeIcon(glyph, color = if (focused) AppColors.NeonCyan
                else AppColors.TextLight.copy(alpha = 0.86f), size = 22.sp)
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
    focusRequester: FocusRequester? = null,
    navigateUpTo: FocusRequester? = null,
    navigateDownTo: FocusRequester? = null,
    navigateLeftTo: FocusRequester? = null,
    navigateRightTo: FocusRequester? = null,
    onFocused: ((FocusRequester) -> Unit)? = null,
) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        modifier = modifier
            .then(focusRequester?.let(Modifier::focusRequester) ?: Modifier)
            .focusProperties {
                navigateUpTo?.let { up = it }
                navigateDownTo?.let { down = it }
                navigateLeftTo?.let { left = it }
                navigateRightTo?.let { right = it }
            }
            .size(40.dp)
            .semantics { contentDescription = description }
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) focusRequester?.let { requester -> onFocused?.invoke(requester) }
            },
        scale = ClickableSurfaceDefaults.scale(scale = 1f, focusedScale = 1f),
        border = ClickableSurfaceDefaults.border(
            border = Border.None,
            focusedBorder = Border.None
        ),
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color.Transparent
        )
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val foreground = if (focused) AppColors.IceWhite else AppColors.TextLight
            Text(glyph, color = foreground,
                style = MaterialTheme.typography.titleLarge.copy(
                    shadow = if (focused) Shadow(AppColors.NeonCyan.copy(alpha = 0.7f),
                        androidx.compose.ui.geometry.Offset.Zero, 20f) else null))
        }
    }
}

@Composable
private fun WatchlistSeasonArrow(
    glyph: String,
    description: String,
    enabled: Boolean,
    downTo: FocusRequester?,
    onClick: () -> Unit,
) {
    var focused by remember(description) { mutableStateOf(false) }
    Box(
        modifier = Modifier.size(40.dp)
            .onFocusChanged { focused = it.isFocused }
            .then(Modifier.clickable(enabled = enabled, onClick = onClick)
                .focusable(enabled = enabled)
                .focusProperties { downTo?.let { down = it } })
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(glyph, color = when {
            !enabled -> AppColors.TextSecondary.copy(alpha = 0.4f)
            focused -> AppColors.IceWhite
            else -> AppColors.TextLight
        }, style = MaterialTheme.typography.titleLarge.copy(
            shadow = if (focused) Shadow(AppColors.NeonCyan.copy(alpha = 0.7f),
                androidx.compose.ui.geometry.Offset.Zero, 20f) else null))
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun WatchlistHomeSection(
    items: List<TmdbWatchlistHomeItem>,
    onSelectSeason: (TmdbTitle, Int) -> Unit,
    onSelectVoice: (TmdbTitle, Int, Int) -> Unit,
    onOpenEpisode: (TmdbTitle, TmdbEpisode) -> Unit,
    onContinue: (TmdbTitle, TmdbEpisode?) -> Unit,
    onOpenDetails: (TmdbTitle) -> Unit,
    selectedKey: String?,
    onSelectedKeyChange: (String) -> Unit,
    onSectionFocus: () -> Unit,
    modifier: Modifier = Modifier,
    drawerFocusRequester: FocusRequester? = null,
    onContentFocusRequester: (FocusRequester) -> Unit = {},
    lastEpisodeFocusKey: String? = null,
    lastEpisodeFocusNumber: Int? = null,
    onEpisodeFocused: (String, Int) -> Unit = { _, _ -> },
    continueFocusRequester: FocusRequester? = null,
) {
    // Selection is owned by the screen, above the LazyColumn item, so navigating
    // to Details and back cannot recreate this section at the first title.
    val effectiveSelectedKey = selectedKey ?: items.firstOrNull()?.let {
        "${it.entry.title.type}:${it.entry.title.id}"
    }
    val selectedIndex = items.indexOfFirst {
        "${it.entry.title.type}:${it.entry.title.id}" == effectiveSelectedKey
    }.takeIf { it >= 0 } ?: 0
    val selected = items[selectedIndex]
    val title = selected.entry.title
    val episodeFocusKey = "${title.type.path}:${title.id}:${selected.selectedSeasonNumber}"
    val availableSeasons = selected.seasons.filter {
        it.number > 0 && (it.episodeCount ?: 0) > 0
    }.sortedBy { it.number }
    val selectedSeasonIndex = availableSeasons.indexOfFirst {
        it.number == selected.selectedSeasonNumber
    }
    val previousSeason = availableSeasons.getOrNull(selectedSeasonIndex - 1)
    val nextAvailableSeason = availableSeasons.getOrNull(selectedSeasonIndex + 1)
    var voiceMenuExpanded by remember(title.type, title.id, selected.selectedSeasonNumber) {
        mutableStateOf(false)
    }
    val selectedVoiceOption = selected.anitubeVoices.firstOrNull {
        selected.selectedVoiceIndex in it.variantIndices
    } ?: selected.anitubeVoices.firstOrNull()
    val defaultDetailsFocusRequester = remember(title.type, title.id) { FocusRequester() }
    val detailsFocusRequester = defaultDetailsFocusRequester
    val seasonEpisodeFocusRequester = remember(title.type, title.id,
        selected.selectedSeasonNumber) { FocusRequester() }
    var detailsFocused by remember(title.type, title.id) { mutableStateOf(false) }
    val detailsFocusProgress by animateFloatAsState(
        targetValue = if (detailsFocused) 1f else 0f,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 220),
        label = "watchlistDetailsFocus",
    )
    val darkFill = Color(0xFF090B0F)
    val displayTitle = title.matchedSeasonTitle?.takeIf(String::isNotBlank)
        ?: title.ukrainianTitle?.takeIf(String::isNotBlank) ?: title.title
    val countryNames = title.originCountries.mapNotNull { code ->
        Locale.forLanguageTag("und-${code.uppercase(Locale.ROOT)}")
            .getDisplayCountry(Locale.forLanguageTag("uk"))
            .takeIf(String::isNotBlank)
    }
    val titleMetadata = listOfNotNull(
        title.year?.toString(),
        if (title.type == TmdbMediaType.TV) "Серіал" else "Фільм",
        countryNames.joinToString(", ").takeIf(String::isNotBlank),
        title.rating?.let { "TMDB: ${String.format(Locale.US, "%.1f", it)}" },
    ).joinToString(" · ")
    val continueLabel = if (selected.entry.source ==
        ua.any2b.tvplayer.data.TmdbWatchlistEntry.Source.IN_PROGRESS) {
        "Продовжити перегляд"
    } else "Дивитись"
    var continueFocused by remember(title.type, title.id) { mutableStateOf(false) }
    val season = selected.seasons.firstOrNull {
        it.number == selected.selectedSeasonNumber
    }
    val nextSeason = selected.seasons
        .filter { it.number > (selected.selectedSeasonNumber ?: 0) }
        .minByOrNull { it.number }
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
            // Keep the movie header at the same absolute height as the series
            // header; only the area below it is shorter than the episode row.
            Box(Modifier.fillMaxWidth().height(watchlistViewportHeight *
                if (title.type == TmdbMediaType.MOVIE) (0.58f / 0.82f) else 0.58f)) {
                if (items.size > 1) {
                    Row(Modifier.align(Alignment.TopStart).padding(top = 18.dp, start = 28.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val previousArrowFocusRequester = remember { FocusRequester() }
                        FloatingIconButton("<", "Попередній тайтл", onClick = {
                            val previous = (selectedIndex - 1 + items.size) % items.size
                            onSelectedKeyChange("${items[previous].entry.title.type}:${items[previous].entry.title.id}")
                            onSectionFocus()
                        }, focusRequester = previousArrowFocusRequester,
                            navigateDownTo = detailsFocusRequester,
                            navigateLeftTo = drawerFocusRequester,
                            onFocused = onContentFocusRequester)
                        FloatingIconButton(">", "Наступний тайтл", onClick = {
                            val next = (selectedIndex + 1) % items.size
                            onSelectedKeyChange("${items[next].entry.title.type}:${items[next].entry.title.id}")
                            onSectionFocus()
                        }, navigateDownTo = detailsFocusRequester)
                    }
                }
                Column(Modifier.align(Alignment.BottomStart).fillMaxWidth(0.36f)
                    .padding(start = 40.dp, end = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Surface(
                        onClick = {
                            onOpenDetails(title.copy(matchedSeasonNumber = selected.selectedSeasonNumber))
                        },
                        modifier = Modifier.fillMaxWidth()
                            .focusRequester(detailsFocusRequester)
                            .focusProperties { drawerFocusRequester?.let { left = it } }
                            .semantics { contentDescription = "Відкрити деталі: $displayTitle" }
                            .onFocusChanged {
                                detailsFocused = it.isFocused
                                if (it.isFocused) onContentFocusRequester(detailsFocusRequester)
                            },
                        scale = ClickableSurfaceDefaults.scale(scale = 1f, focusedScale = 1.03f),
                        border = ClickableSurfaceDefaults.border(
                            border = Border.None,
                            focusedBorder = Border.None),
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = Color.Transparent,
                            focusedContainerColor = Color.Transparent),
                    ) {
                        Column(Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            if (selected.logoUrl != null) {
                                var verticalLogo by remember(selected.logoUrl) { mutableStateOf(false) }
                                // Keep the logo inside the left information column so it never
                                // overlaps the confined backdrop on narrower TV layouts.
                                val logoHeight = if (verticalLogo) 118.dp else 70.dp
                                var logoAspectRatio by remember(selected.logoUrl) {
                                    mutableStateOf(2.4f)
                                }
                                val logoWidth = if (verticalLogo) 96.dp
                                    else (logoHeight * logoAspectRatio).coerceAtMost(240.dp)
                                Box(Modifier.width(logoWidth).height(logoHeight),
                                    contentAlignment = Alignment.CenterStart) {
                                    AsyncImage(model = selected.logoUrl, contentDescription = title.title,
                                        contentScale = ContentScale.Fit,
                                        onSuccess = { result ->
                                            val image = result.result.drawable
                                            verticalLogo = image.intrinsicHeight > image.intrinsicWidth
                                            if (image.intrinsicHeight > 0)
                                                logoAspectRatio = image.intrinsicWidth.toFloat() /
                                                    image.intrinsicHeight
                                        },
                                        modifier = Modifier.fillMaxSize())
                                }
                            }
                            val titleAccentProgress = if (selected.logoUrl == null)
                                detailsFocusProgress else 0f
                            Text(displayTitle,
                                style = MaterialTheme.typography.titleLarge.copy(
                                    shadow = Shadow(AppColors.NeonCyan.copy(
                                        alpha = 0.34f * titleAccentProgress), blurRadius = 10f)),
                                color = lerp(Color(0xFFD1D5DB), Color.White, detailsFocusProgress),
                                fontWeight = FontWeight.Bold, maxLines = 1,
                                overflow = TextOverflow.Ellipsis)
                            Text(titleMetadata, color = lerp(AppColors.TextSecondary,
                                Color.White, detailsFocusProgress),
                                style = MaterialTheme.typography.labelMedium, maxLines = 2,
                                overflow = TextOverflow.Ellipsis)
                        }
                    }
                    if (selected.playbackProgress != null &&
                        selected.playbackProgress < 0.98f) {
                        Column(Modifier.fillMaxWidth(0.82f).padding(end = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(watchProgressLabel(title.type == TmdbMediaType.TV,
                                selected.displaySeasonNumber ?: selected.selectedSeasonNumber,
                                selected.playbackEpisodeNumber, selected.playbackRemainingMs),
                                color = AppColors.TextSecondary,
                                style = MaterialTheme.typography.labelSmall, maxLines = 1,
                                overflow = TextOverflow.Ellipsis)
                            Box(Modifier.fillMaxWidth().height(3.dp)
                                .background(Color.White.copy(alpha = 0.2f),
                                    RoundedCornerShape(2.dp))) {
                                Box(Modifier.fillMaxWidth(selected.playbackProgress.coerceIn(0f, 1f))
                                    .height(3.dp).background(AppColors.NeonCyan,
                                        RoundedCornerShape(2.dp)))
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Button(onClick = {
                            val resumeEpisode = selected.nextEpisodes
                                .firstOrNull { !it.watched }?.episode
                                ?: selected.nextEpisodes.firstOrNull()?.episode
                            onContinue(title.copy(matchedSeasonNumber = selected.selectedSeasonNumber),
                                resumeEpisode)
                        }, scale = ButtonDefaults.scale(scale = 1f, focusedScale = 1f),
                            shape = ButtonDefaults.shape(RoundedCornerShape(12.dp)),
                            glow = ButtonDefaults.glow(glow = Glow.None,
                                focusedGlow = Glow(elevationColor = AppColors.NeonCyan, elevation = 8.dp)),
                            colors = ButtonDefaults.colors(
                                containerColor = AppColors.SurfaceCard,
                                contentColor = Color.White,
                                focusedContainerColor = AppColors.NeonCyan,
                                focusedContentColor = AppColors.ButtonDarkText),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                            modifier = Modifier.height(48.dp)
                            .then(continueFocusRequester?.let(Modifier::focusRequester) ?: Modifier)
                            .focusProperties {
                                up = detailsFocusRequester
                                drawerFocusRequester?.let { left = it }
                            }
                            .onFocusChanged { continueFocused = it.isFocused }
                            .neonGlow(focused = continueFocused, borderRadius = 12.dp)
                            .onFocusChanged {
                                if (it.isFocused) continueFocusRequester?.let(onContentFocusRequester)
                            }) {
                            Text(continueLabel, color = if (continueFocused)
                                AppColors.ButtonDarkText else Color.White)
                        }
                        if (selectedVoiceOption != null) {
                            OutlinedButton(onClick = { voiceMenuExpanded = true },
                                scale = OutlinedButtonDefaults.scale(scale = 1f, focusedScale = 1f),
                                shape = OutlinedButtonDefaults.shape(RoundedCornerShape(12.dp)),
                                border = OutlinedButtonDefaults.border(
                                    border = Border(BorderStroke(1.dp, AppColors.TextMuted)),
                                    focusedBorder = Border(BorderStroke(2.dp, AppColors.NeonCyan)),
                                ),
                                colors = OutlinedButtonDefaults.colors(
                                    containerColor = Color.Transparent,
                                    contentColor = Color.White,
                                    focusedContainerColor = AppColors.NeonCyanLight,
                                    focusedContentColor = AppColors.ButtonDarkText,
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                modifier = Modifier.height(48.dp).widthIn(max = 210.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Column(horizontalAlignment = Alignment.Start) {
                                        Text("Озвучення", style = MaterialTheme.typography.labelSmall,
                                            color = AppColors.TextSecondary, maxLines = 1)
                                        Text(selectedVoiceOption.name,
                                            style = MaterialTheme.typography.labelMedium,
                                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    Text("⌄", style = MaterialTheme.typography.titleMedium)
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            Box(Modifier.fillMaxWidth().height(50.dp)) {
                if (title.type == TmdbMediaType.TV) {
                    Row(Modifier.align(Alignment.CenterStart).padding(start = 28.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            WatchlistSeasonArrow("<", "Попередній сезон",
                                enabled = previousSeason != null,
                                downTo = seasonEpisodeFocusRequester.takeIf {
                                    selected.nextEpisodes.isNotEmpty()
                                },
                                onClick = { previousSeason?.let { onSelectSeason(title, it.number) } })
                            Text("Сезон ${selected.displaySeasonNumber
                                ?: selected.selectedSeasonNumber ?: season?.number ?: 1}",
                                modifier = Modifier.width(100.dp),
                                color = Color.White, style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold, maxLines = 1,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                overflow = TextOverflow.Ellipsis)
                            WatchlistSeasonArrow(">", "Наступний сезон",
                                enabled = nextAvailableSeason != null,
                                downTo = seasonEpisodeFocusRequester.takeIf {
                                    selected.nextEpisodes.isNotEmpty()
                                },
                                onClick = { nextAvailableSeason?.let { onSelectSeason(title, it.number) } })
                    }
                }
                Box(Modifier.align(Alignment.Center)) {
                    WatchlistPositionIndicator(items.size, selectedIndex)
                }
            }
            if (voiceMenuExpanded) Dialog(onDismissRequest = { voiceMenuExpanded = false }) {
                Column(Modifier.width(480.dp).background(AppColors.SurfaceCard,
                    RoundedCornerShape(16.dp)).padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Доріжка · Сезон ${selected.displaySeasonNumber
                        ?: selected.selectedSeasonNumber ?: 1}",
                        style = MaterialTheme.typography.titleLarge, color = Color.White)
                    selected.anitubeVoices.forEachIndexed { index, voice ->
                        Button(onClick = {
                            voiceMenuExpanded = false
                            onSelectVoice(title, selected.selectedSeasonNumber ?: 1, index)
                        }, modifier = Modifier.fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                PlaybackTrackIcon(voice.category, Modifier.size(18.dp))
                                if (selected.selectedVoiceIndex in voice.variantIndices)
                                    Text("✓", fontWeight = FontWeight.Bold)
                                Text(voice.name)
                            }
                        }
                    }
                }
            }
            if (title.type == TmdbMediaType.TV) {
                Column(Modifier.fillMaxWidth().height(176.dp)) {
                    if (selected.episodesLoading) {
                        Text("Завантаження серій…", color = AppColors.TextSecondary,
                            modifier = Modifier.padding(horizontal = 4.dp))
                    } else if (selected.nextEpisodes.isNotEmpty() || nextSeason != null) {
                        val firstUnwatched = selected.nextEpisodes.indexOfFirst { !it.watched }
                            .coerceAtLeast(0)
                        val lastFocusedIndex = if (lastEpisodeFocusKey == episodeFocusKey)
                            selected.nextEpisodes.indexOfFirst {
                                it.episode.episodeNumber == lastEpisodeFocusNumber
                            } else -1
                        val targetEpisodeIndex = lastFocusedIndex.takeIf { it >= 0 }
                            ?: firstUnwatched
                        val episodeListState = rememberLazyListState()
                        LaunchedEffect(selected.entry.title.id, selected.selectedSeasonNumber,
                            selected.nextEpisodes) {
                            if (targetEpisodeIndex > 0)
                                episodeListState.scrollToItem(targetEpisodeIndex)
                        }
                        val nextSeason = selected.seasons
                            .filter { it.number > (selected.selectedSeasonNumber ?: 0) &&
                                (it.episodeCount ?: 0) > 0 }
                            .minByOrNull { it.number }
                        LazyRow(state = episodeListState,
                                modifier = Modifier.fillMaxWidth().weight(1f)
                                    .focusGroup(),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            contentPadding = PaddingValues(start = 40.dp, end = 40.dp)) {
                            items(selected.nextEpisodes, key = {
                                "${it.episode.seasonNumber}:${it.episode.episodeNumber}"
                            }) { card ->
                                val episodeFocusRequester = remember(card.episode.id) { FocusRequester() }
                                val isSeasonFocusTarget = card ==
                                    selected.nextEpisodes.getOrNull(targetEpisodeIndex)
                                val cardFocusRequester = if (isSeasonFocusTarget)
                                    seasonEpisodeFocusRequester else episodeFocusRequester
                                Card(onClick = { onOpenEpisode(title, card.episode) },
                                    scale = CardDefaults.scale(scale = 1f, focusedScale = 1f),
                                    colors = CardDefaults.colors(
                                        containerColor = if (card.watched) AppColors.SurfaceDark
                                            else AppColors.SkeletonBg,
                                        focusedContainerColor = if (card.watched) AppColors.AccentPurple
                                            else AppColors.SkeletonBg),
                                    shape = CardDefaults.shape(RoundedCornerShape(10.dp)),
                                    modifier = Modifier.width(204.dp).height(164.dp)
                                        .focusRequester(cardFocusRequester)
                                        .focusProperties {
                                            if (card == selected.nextEpisodes.firstOrNull()) {
                                                drawerFocusRequester?.let { left = it }
                                            }
                                        }
                                        .onFocusChanged {
                                            if (it.isFocused) {
                                                onContentFocusRequester(cardFocusRequester)
                                                onEpisodeFocused(episodeFocusKey,
                                                    card.episode.episodeNumber)
                                            }
                                        }) {
                                    Column {
                                        AsyncImage(model = card.cachedImagePath?.let(::File)
                                            ?: card.episode.stillUrl,
                                            contentDescription = card.episode.name,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                                                .background(AppColors.SurfaceCard)
                                                .then(if (card.watched) Modifier.blur(1.dp) else Modifier))
                                        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp,
                                            vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Text("${if (card.watched) "✓ " else ""}E${card.episode.episodeNumber}",
                                                color = if (card.watched) AppColors.TextWatched
                                                    else AppColors.TextSecondary,
                                                style = MaterialTheme.typography.labelSmall,
                                                maxLines = 1)
                                            Text(card.episode.name, color = Color.White,
                                                fontWeight = FontWeight.Medium, maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f),
                                                style = MaterialTheme.typography.labelLarge)
                                        }
                                    }
                                }
                            }
                            if (nextSeason != null) item(key = "next-season-${nextSeason.number}") {
                                Card(onClick = { onSelectSeason(title, nextSeason.number) },
                                    scale = CardDefaults.scale(scale = 1f, focusedScale = 1f),
                                    colors = CardDefaults.colors(
                                        containerColor = AppColors.SurfaceDark,
                                        focusedContainerColor = AppColors.AccentPurple),
                                    shape = CardDefaults.shape(RoundedCornerShape(10.dp)),
                                    modifier = Modifier.width(204.dp).height(164.dp)) {
                                    Column(Modifier.fillMaxSize().padding(16.dp),
                                        verticalArrangement = Arrangement.Center,
                                        horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("Перейти до наступного сезону",
                                            color = Color.White, fontWeight = FontWeight.Medium,
                                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        Text("Сезон ${nextSeason.number}  ›",
                                            color = AppColors.NeonCyan,
                                            style = MaterialTheme.typography.labelLarge,
                                            modifier = Modifier.padding(top = 8.dp))
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
            } else {
                Box(Modifier.fillMaxWidth().height(112.dp)
                    .padding(start = 40.dp, end = 40.dp, top = 12.dp),
                    contentAlignment = Alignment.TopStart) {
                    title.overview?.takeIf(String::isNotBlank)?.let { overview ->
                        Text(overview,
                            modifier = Modifier.fillMaxWidth(0.75f),
                            color = Color(0xFFD1D5DB),
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaybackTrackIcon(category: String, modifier: Modifier = Modifier) {
    val isSubtitle = category.equals("СУБТИТРИ", ignoreCase = true)
    FontAwesomeIcon(if (isSubtitle) FaIcons.COMMENTING_O else FaIcons.MICROPHONE,
        color = Color.White, size = 13.sp, modifier = modifier)
}

private fun watchProgressLabel(isTv: Boolean, season: Int?, episode: Int?, remainingMs: Long?): String {
    val parts = buildList {
        if (isTv) {
            season?.let { add("Сезон $it") }
            episode?.let { add("Серія $it") }
        }
        remainingMs?.takeIf { it > 0L }?.let { millis ->
            val minutes = kotlin.math.ceil(millis / 60_000.0).toInt()
            val hours = minutes / 60
            val rest = minutes % 60
            add(when {
                hours > 0 && rest > 0 -> "$hours год $rest хв залишилось"
                hours > 0 -> "$hours год залишилось"
                else -> "$minutes хв залишилось"
            })
        }
    }
    return parts.joinToString(" · ")
}

@Composable
private fun WatchlistPositionIndicator(itemCount: Int, selectedIndex: Int) {
    val visibleCount = itemCount.coerceAtMost(9)
    val firstIndex = if (itemCount <= visibleCount) 0 else
        (selectedIndex - visibleCount / 2).coerceIn(0, itemCount - visibleCount)
    Row(Modifier.semantics {
        contentDescription = "Тайтл ${selectedIndex + 1} з $itemCount"
    }, horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically) {
        repeat(visibleCount) { dotIndex ->
            val active = firstIndex + dotIndex == selectedIndex
            Box(Modifier.size(if (active) 8.dp else 6.dp)
                .background(if (active) Color.White else Color(0xFF69717D), CircleShape))
        }
    }
}

private fun TmdbLibraryState.hasHomeRowContent(sectionId: String): Boolean = when (sectionId) {
    "watchlist" -> watchlist.isNotEmpty()
    "history" -> history.isNotEmpty()
    "favorites" -> favorites.isNotEmpty()
    "recommendations" -> recommendations.isNotEmpty()
    "animation_tv", "tv" -> (tv as? TmdbPopularState.Results)?.titles?.isNotEmpty() == true
    "animation_movie", "movies", "movie" ->
        (movies as? TmdbPopularState.Results)?.titles?.isNotEmpty() == true
    "mal_top" -> (malTop as? MalTopState.Results)?.titles?.isNotEmpty() == true
    "trending" -> (trending as? AniListHomeState.Results)?.titles?.isNotEmpty() == true
    "popular_season" -> (popularSeason as? AniListHomeState.Results)?.titles?.isNotEmpty() == true
    "toloka_animation" -> (tolokaReleases as? TolokaRssState.Results)
        ?.rows?.animated?.isNotEmpty() == true
    "toloka_video" -> (tolokaReleases as? TolokaRssState.Results)
        ?.rows?.general?.isNotEmpty() == true
    else -> false
}

private fun homeLazyItemKeys(state: TmdbLibraryState, animationOnly: Boolean,
    peopleVisible: Boolean): List<String> =
    if (!animationOnly) buildList {
        add("toloka_video")
        if (state.history.isNotEmpty()) add("history")
        add("tv")
        add("movies")
        if (state.favorites.isNotEmpty()) add("favorites")
    } else buildList {
        if (state.watchlist.isNotEmpty()) add("watchlist")
        if (peopleVisible) add("watchlist-people")
        state.mainSections.filter { it.enabled }.forEach { section ->
            when (section.id) {
                "history" -> if (state.history.isNotEmpty()) add(section.id)
                "favorites" -> if (state.favorites.isNotEmpty()) add(section.id)
                "recommendations" -> if (state.recommendations.isNotEmpty()) add(section.id)
                "animation_tv", "animation_movie", "mal_top", "trending",
                "popular_season", "toloka_animation" -> add(section.id)
            }
        }
    }

@Composable
private fun AniListHomeRow(title: String, state: AniListHomeState,
    onOpen: (TmdbTitle) -> Unit, onMore: () -> Unit, onRetry: () -> Unit,
    onSectionFocus: () -> Unit, drawerFocusRequester: FocusRequester? = null,
    homeEntryFocusRequester: FocusRequester? = null,
    onContentFocusRequester: (FocusRequester) -> Unit = {}) {
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
                onSectionFocus = onSectionFocus, drawerFocusRequester = drawerFocusRequester,
                homeEntryFocusRequester = homeEntryFocusRequester,
                onContentFocusRequester = onContentFocusRequester)
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun MalTopRow(title: String, state: MalTopState, onOpen: (TmdbTitle) -> Unit,
    onMore: () -> Unit, onRetry: () -> Unit, onSectionFocus: () -> Unit = {},
    drawerFocusRequester: FocusRequester? = null,
    homeEntryFocusRequester: FocusRequester? = null,
    onContentFocusRequester: (FocusRequester) -> Unit = {}) {
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
                onSectionFocus = onSectionFocus, drawerFocusRequester = drawerFocusRequester,
                homeEntryFocusRequester = homeEntryFocusRequester,
                onContentFocusRequester = onContentFocusRequester)
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TmdbPopularRow(title: String, state: TmdbPopularState,
    onOpen: (TmdbTitle) -> Unit, onRetry: () -> Unit, onMore: () -> Unit,
    onSectionFocus: () -> Unit = {}, drawerFocusRequester: FocusRequester? = null,
    homeEntryFocusRequester: FocusRequester? = null,
    onContentFocusRequester: (FocusRequester) -> Unit = {}) {
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
                onSectionFocus = onSectionFocus, drawerFocusRequester = drawerFocusRequester,
                homeEntryFocusRequester = homeEntryFocusRequester,
                onContentFocusRequester = onContentFocusRequester)
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TolokaRssRow(title: String, state: TolokaRssState, animated: Boolean,
    onOpen: (TmdbTitle) -> Unit, onMore: () -> Unit, onRetry: () -> Unit,
    onSectionFocus: () -> Unit, drawerFocusRequester: FocusRequester?,
    homeEntryFocusRequester: FocusRequester? = null,
    onContentFocusRequester: (FocusRequester) -> Unit) {
    val titles = (state as? TolokaRssState.Results)?.rows?.let {
        if (animated) it.animated else it.general
    }.orEmpty()
    if (titles.isNotEmpty()) {
        TmdbLibraryRow(title, titles, onOpen, onMore = onMore,
            onSectionFocus = onSectionFocus,
            drawerFocusRequester = drawerFocusRequester,
            homeEntryFocusRequester = homeEntryFocusRequester,
            onContentFocusRequester = onContentFocusRequester)
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = Color.White,
                modifier = Modifier.padding(horizontal = 4.dp))
            when (state) {
                TolokaRssState.Loading -> Text("Завантаження…", color = AppColors.TextSecondary)
                is TolokaRssState.Error -> {
                Text(state.message, color = AppColors.ErrorRed)
                Button(onClick = onRetry) { Text("Повторити") }
                }
                is TolokaRssState.Results ->
                    Text("Новинок поки немає", color = AppColors.TextSecondary)
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun TmdbLibraryRow(title: String, titles: List<TmdbTitle>,
    onOpen: (TmdbTitle) -> Unit, onMore: () -> Unit,
    showMore: Boolean = titles.size > 13, onSectionFocus: () -> Unit = {},
    onLongClick: ((TmdbTitle) -> Unit)? = null,
    homeEntryFocusRequester: FocusRequester? = null,
    drawerFocusRequester: FocusRequester? = null,
    onContentFocusRequester: (FocusRequester) -> Unit = {}) {
    // Multiple AniList seasons can resolve to the same TMDB show. A LazyRow key
    // identifies a card, so keep only the first card per TMDB title here.
    val rowTitles = titles.distinctBy { it.type to it.id }
    val rowState = rememberLazyListState()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = Color.White,
            modifier = Modifier.padding(horizontal = 4.dp))
        LazyRow(state = rowState, modifier = Modifier.focusGroup(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)) {
            items(rowTitles.take(13), key = { "${it.type}:${it.id}" }) { item ->
                TmdbPosterCard(item, onClick = { onOpen(item) }, onFocused = onSectionFocus,
                    focusRequester = homeEntryFocusRequester.takeIf {
                        item == rowTitles.firstOrNull()
                    },
                    onLongClick = onLongClick?.let { callback -> { callback(item) } },
                    navigateLeftTo = if (item == rowTitles.firstOrNull()) drawerFocusRequester else null,
                    onFocusTarget = onContentFocusRequester)
            }
            if (showMore && rowTitles.size > 13) item(key = "more") { MoreCard(onClick = onMore) }
        }
    }
}

@Preview(device = "id:tv_1080p")
@Composable
private fun TmdbLibraryScreenPreview() {
    AppTheme {
        TmdbLibraryScreen(
            state = TmdbLibraryState(
                mainSections = listOf(
                    HomeSectionSetting("animation_tv", "Популярні анімаційні серіали", true),
                    HomeSectionSetting("animation_movie", "Популярні повнометражні фільми", true)
                ),
                tv = TmdbPopularState.Results(
                    listOf(
                        TmdbTitle(1, TmdbMediaType.TV, "Attack on Titan", "Атака титанів", 2013, null, null, 9.0, "Атака титанів", "Attack on Titan", "ja")
                    )
                ),
                initialHomeLoading = false
            ),
            animationOnly = true,
            onOpen = {},
            onRetry = {},
            onMore = {},
            onBack = {}
        )
    }
}
