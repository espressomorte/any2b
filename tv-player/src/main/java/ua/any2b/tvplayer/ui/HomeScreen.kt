package ua.any2b.tvplayer.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import coil.compose.AsyncImage
import ua.any2b.core.anilist.AnimeCard
import ua.any2b.core.anilist.HomeRow
import ua.any2b.tvplayer.R
import ua.any2b.tvplayer.data.FavoritesManager

val IbmPlexMono = FontFamily(
    Font(R.font.ibm_plex_mono_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_mono_medium, FontWeight.Medium),
    Font(R.font.ibm_plex_mono_bold, FontWeight.Bold),
)
val IbmPlexSans = FontFamily(
    Font(R.font.ibm_plex_sans_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_sans_light, FontWeight.Light),
)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val tvTypography = Typography(
        displayLarge = TextStyle(fontFamily = IbmPlexSans),
        displayMedium = TextStyle(fontFamily = IbmPlexSans),
        displaySmall = TextStyle(fontFamily = IbmPlexSans),
        headlineLarge = TextStyle(fontFamily = IbmPlexSans),
        headlineMedium = TextStyle(fontFamily = IbmPlexSans),
        headlineSmall = TextStyle(fontFamily = IbmPlexSans),
        titleLarge = TextStyle(fontFamily = IbmPlexSans),
        titleMedium = TextStyle(fontFamily = IbmPlexSans),
        titleSmall = TextStyle(fontFamily = IbmPlexSans),
        bodyLarge = TextStyle(fontFamily = IbmPlexSans),
        bodyMedium = TextStyle(fontFamily = IbmPlexSans),
        bodySmall = TextStyle(fontFamily = IbmPlexSans),
        labelLarge = TextStyle(fontFamily = IbmPlexSans),
        labelMedium = TextStyle(fontFamily = IbmPlexSans),
        labelSmall = TextStyle(fontFamily = IbmPlexSans),
    )

    MaterialTheme(
        colorScheme = darkColorScheme(),
        typography = tvTypography
    ) {
        CompositionLocalProvider(
            LocalTextStyle provides TextStyle(fontFamily = IbmPlexSans)
        ) {
            Surface(modifier = Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().background(Color(0xFF0F1115))) {
                    content()
                }
            }
        }
    }
}

@Composable
fun HomeScreen(
    state: HomeState,
    onRetry: () -> Unit,
    onOpen: (AnimeCard) -> Unit,
    onMore: (HomeRow) -> Unit,
    onFavoriteChanged: () -> Unit,
) {
    val context = LocalContext.current

    when (state) {
        HomeState.Loading -> AnimariumLoadingScreen()
        is HomeState.Error, is HomeState.Ready -> {
            Column(modifier = Modifier.fillMaxSize().background(Color(0xFF0A0B0E))) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 20.dp, end = 48.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HomeActionButton(FaIcons.SEARCH, "Пошук") {
                        val intent = Intent(context, ua.any2b.tvplayer.SearchActivity::class.java)
                        context.startActivity(intent)
                    }
                    Spacer(Modifier.width(12.dp))
                    HomeActionButton(FaIcons.COG, "Налаштування") {
                        val intent = Intent(context, ua.any2b.tvplayer.SettingsActivity::class.java)
                        context.startActivity(intent)
                    }
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    when (state) {
                        is HomeState.Error -> ErrorBlock(state.message, onRetry)
                        is HomeState.Ready -> Rows(state.rows, state.catalogError, onRetry, onOpen, onMore, onFavoriteChanged)
                        else -> {}
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun HomeActionButton(icon: String, label: String, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }

    Surface(
        onClick = onClick,
        modifier = Modifier
            .size(48.dp)
            .semantics { contentDescription = label }
            .onFocusChanged { isFocused = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (isFocused) Color(0xFF4F378B).copy(alpha = 0.6f) else Color(0xFF20232D),
            focusedContainerColor = Color(0xFF4F378B).copy(alpha = 0.8f)
        )
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            FontAwesomeIcon(icon)
        }
    }
}

@Composable
private fun AnimariumLoadingScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0B0E)),
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = R.drawable.animarium_splash,
            contentDescription = "Splash",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun Rows(
    rows: List<HomeRow>,
    catalogError: String?,
    onRetry: () -> Unit,
    onOpen: (AnimeCard) -> Unit,
    onMore: (HomeRow) -> Unit,
    onFavoriteChanged: () -> Unit,
) {
    val firstCardId = rows.firstNotNullOfOrNull { it.items.firstOrNull()?.id }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(rows) {
        try {
            focusRequester.requestFocus()
        } catch (_: Exception) {}
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 24.dp, bottom = 48.dp, start = 36.dp, end = 48.dp),
        verticalArrangement = Arrangement.spacedBy(32.dp),
    ) {
        if (catalogError != null) {
            item(key = "catalog_error") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(catalogError, color = Color(0xFFFFB4AB))
                    Button(onClick = onRetry) { Text("Повторити завантаження каталогу") }
                }
            }
        }
        items(rows, key = { it.id }) { row ->
            Column {
                Text(
                    text = row.title,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(start = 12.dp, bottom = 14.dp),
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    modifier = Modifier.graphicsLayer { clip = false }
                ) {
                    items(row.items.take(13), key = { it.id }) { card ->
                        val isFirst = card.id == firstCardId
                        PosterCard(
                            item = card,
                            modifier = if (isFirst) Modifier.width(160.dp).focusRequester(focusRequester) else Modifier.width(160.dp),
                            onClick = { onOpen(card) },
                            onFavoriteChanged = onFavoriteChanged,
                        )
                    }
                    if (row.items.size >= 14) {
                        item(key = "more_${row.id}") {
                            MoreCard(onClick = { onMore(row) })
                        }
                    }
                }
            }
        }
    }
}

val NeonCyan = Color(0xFF80F5FF)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun MoreCard(onClick: () -> Unit) {
    Column(modifier = Modifier.width(160.dp).padding(4.dp)) {
        Card(onClick = onClick, modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f)) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color(0xFF20232D)),
                contentAlignment = Alignment.Center
            ) {
                Text("Більше", color = Color(0xFFD0BCFF), fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("Усі тайтли", color = Color(0xFF94A3B8))
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PosterCard(
    item: AnimeCard,
    modifier: Modifier = Modifier,
    showRelatedBadge: Boolean = false,
    onClick: () -> Unit,
    onFavoriteChanged: () -> Unit = {},
) {
    val context = LocalContext.current
    val favoritesManager = remember(context) { FavoritesManager(context) }
    val favoriteIds by favoritesManager.favoriteIds.collectAsState()
    val isFavorite = item.id in favoriteIds
    var showFavoriteMenu by remember { mutableStateOf(false) }
    var isFocused by remember { mutableStateOf(false) }
    val lightCyanGrey = Color(0xFFB8C2D1)
    val textColor = if (isFocused) NeonCyan else lightCyanGrey
    val scale = if (isFocused) 1.025f else 1.0f

    Column(
        modifier = modifier
            .padding(4.dp)
            .onFocusChanged { isFocused = it.isFocused }
            .scale(scale)
    ) {
        Card(
            onClick = onClick,
            onLongClick = { showFavoriteMenu = true },
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f),
        ) {
            Box(Modifier.fillMaxSize()) {
                AsyncImage(
                    model = item.cover,
                    contentDescription = item.title.display,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                ReleaseBadge(
                    score = item.score,
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
                )
                if (showRelatedBadge) {
                    Box(
                        modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
                            .clip(RoundedCornerShape(8.dp)).background(Color(0xDD15171E))
                            .padding(7.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        FontAwesomeIcon(FaIcons.LINK, color = Color(0xFFD0BCFF), size = 13.sp)
                    }
                }
                if (isFavorite) {
                    FavoriteBadge(Modifier.align(Alignment.BottomEnd).padding(6.dp))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Column(modifier = Modifier.padding(horizontal = 4.dp)) {
            Text(
                text = item.title.display,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelMedium,
                color = textColor,
                fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Normal,
            )
            item.seasonYear?.let { year ->
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "$year",
                    style = MaterialTheme.typography.labelMedium,
                    fontSize = 10.sp,
                    color = textColor,
                    fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
    }
    if (showFavoriteMenu) {
        FavoriteContextMenu(
            isFavorite = isFavorite,
            onToggle = {
                showFavoriteMenu = false
                favoritesManager.toggle(item)
                onFavoriteChanged()
            },
            onDismiss = { showFavoriteMenu = false },
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ErrorBlock(message: String, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(message, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            Button(onClick = onRetry) { Text("Повторити") }
        }
    }
}
