package ua.any2b.tvplayer.ui

import android.graphics.BlurMaskFilter
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.zIndex
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
import ua.any2b.core.anilist.hasFullList
import ua.any2b.core.anilist.previewItems
import ua.any2b.tvplayer.R
import ua.any2b.tvplayer.data.FavoritesManager

object AppColors {
    val BackgroundDark = Color(0xFF0A0B0E)
    val SurfaceApp = Color(0xFF0F1115)
    val SurfaceDark = Color(0xFF15171E)
    val SurfaceCard = Color(0xFF20232D)
    val SurfaceCardHover = Color(0xFF1E222D)
    val NeonCyan = Color(0xFF80F5FF)
    val PrimaryPurple = Color(0xFFA855F7)
    val AccentPurple = Color(0xFF4F378B)
    val AccentPurpleAlpha50 = Color(0xFF4F378B).copy(alpha = 0.5f)
    val AccentPurpleAlpha60 = Color(0xFF4F378B).copy(alpha = 0.6f)
    val AccentPurpleAlpha80 = Color(0xFF4F378B).copy(alpha = 0.8f)
    val TextWhite = Color.White
    val TextSecondary = Color(0xFF94A3B8)
    val TextMuted = Color(0xFFB0B3C1)
    val TextLight = Color(0xFFCCC2DC)
    val TextCardHover = Color(0xFFB8C2D1)
    val TextWatched = Color(0xFF475569)
    val ErrorRed = Color(0xFFFFB4AB)
    val SkeletonBg = Color(0xFF45405A)
    val BadgeOverlay = Color(0xDD15171E)
    val HistoryOverlay = Color(0xCC0F1115)
    val AccentLight = Color(0xFFD0BCFF)
    val ButtonDarkText = Color(0xFF0F172A)
    val NeonCyanLight = Color(0xFFE2E8F0)
}

fun Modifier.neonGlow(
    focused: Boolean,
    color: Color = AppColors.NeonCyan,
    borderRadius: Dp = 12.dp,
    blurRadius: Dp = 12.dp,
    alpha: Float = 0.6f,
): Modifier = if (focused) {
    this.drawBehind {
        drawIntoCanvas { canvas ->
            val paint = Paint().apply {
                this.color = color.copy(alpha = alpha)
                asFrameworkPaint().maskFilter = BlurMaskFilter(
                    blurRadius.toPx().coerceAtLeast(1f),
                    BlurMaskFilter.Blur.NORMAL
                )
            }
            canvas.drawRoundRect(
                left = 0f,
                top = 0f,
                right = size.width,
                bottom = size.height,
                radiusX = borderRadius.toPx(),
                radiusY = borderRadius.toPx(),
                paint = paint
            )
        }
    }
} else this

val NeonCyan = AppColors.NeonCyan

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
        displayLarge = TextStyle(fontFamily = IbmPlexSans, fontSize = 57.sp, lineHeight = 64.sp, fontWeight = FontWeight.Bold),
        displayMedium = TextStyle(fontFamily = IbmPlexSans, fontSize = 45.sp, lineHeight = 52.sp, fontWeight = FontWeight.Bold),
        displaySmall = TextStyle(fontFamily = IbmPlexSans, fontSize = 36.sp, lineHeight = 44.sp, fontWeight = FontWeight.Bold),
        headlineLarge = TextStyle(fontFamily = IbmPlexSans, fontSize = 32.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold),
        headlineMedium = TextStyle(fontFamily = IbmPlexSans, fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold),
        headlineSmall = TextStyle(fontFamily = IbmPlexSans, fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold),
        titleLarge = TextStyle(fontFamily = IbmPlexSans, fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold),
        titleMedium = TextStyle(fontFamily = IbmPlexSans, fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium),
        titleSmall = TextStyle(fontFamily = IbmPlexSans, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
        bodyLarge = TextStyle(fontFamily = IbmPlexSans, fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Normal),
        bodyMedium = TextStyle(fontFamily = IbmPlexSans, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Normal),
        bodySmall = TextStyle(fontFamily = IbmPlexSans, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Normal),
        labelLarge = TextStyle(fontFamily = IbmPlexSans, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
        labelMedium = TextStyle(fontFamily = IbmPlexSans, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
        labelSmall = TextStyle(fontFamily = IbmPlexSans, fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Normal),
    )

    MaterialTheme(
        colorScheme = darkColorScheme(),
        typography = tvTypography
    ) {
        CompositionLocalProvider(
            LocalTextStyle provides TextStyle(fontFamily = IbmPlexSans)
        ) {
            Surface(modifier = Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().background(AppColors.SurfaceApp)) {
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
            Column(modifier = Modifier.fillMaxSize().background(AppColors.BackgroundDark)) {
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
        scale = ClickableSurfaceDefaults.scale(scale = 1f, focusedScale = 1f),
        border = ClickableSurfaceDefaults.border(
            border = Border.None,
            focusedBorder = Border(BorderStroke(2.dp, AppColors.NeonCyan))
        ),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = AppColors.SurfaceCard,
            focusedContainerColor = AppColors.SurfaceCard
        ),
        modifier = Modifier
            .size(48.dp)
            .semantics { contentDescription = label }
            .onFocusChanged { isFocused = it.isFocused },
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            FontAwesomeIcon(icon, color = if (isFocused) AppColors.NeonCyan else AppColors.TextLight)
        }
    }
}

@Composable
private fun AnimariumLoadingScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.BackgroundDark),
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
        if (rows.isEmpty() && catalogError == null) {
            item(key = "empty_home") {
                Text(
                    "Усі списки приховані. Увімкніть їх у налаштуваннях головної.",
                    color = AppColors.TextSecondary,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
        if (catalogError != null) {
            item(key = "catalog_error") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(catalogError, color = AppColors.ErrorRed)
                    PrimaryButton(onClick = onRetry, text = "Повторити завантаження каталогу")
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
                    items(row.previewItems, key = { it.id }) { card ->
                        val isFirst = card.id == firstCardId
                        PosterCard(
                            item = card,
                            modifier = if (isFirst) Modifier.width(160.dp).focusRequester(focusRequester) else Modifier.width(160.dp),
                            onClick = { onOpen(card) },
                            onFavoriteChanged = onFavoriteChanged,
                        )
                    }
                    if (row.hasFullList) {
                        item(key = "more_${row.id}") {
                            MoreCard(onClick = { onMore(row) })
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun MoreCard(onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    val textColor = if (isFocused) AppColors.NeonCyan else AppColors.TextLight

    Column(
        modifier = Modifier
            .width(160.dp)
            .padding(4.dp)
            .onFocusChanged { isFocused = it.isFocused }
    ) {
        Card(
            onClick = onClick,
            scale = CardDefaults.scale(scale = 1f, focusedScale = 1f),
            border = CardDefaults.border(border = Border.None, focusedBorder = Border.None),
            glow = CardDefaults.glow(
                glow = Glow.None,
                focusedGlow = Glow(elevationColor = AppColors.NeonCyan, elevation = 12.dp)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f),
        ) {
            Box(
                modifier = Modifier.fillMaxSize().background(AppColors.SurfaceCard),
                contentAlignment = Alignment.Center
            ) {
                Text("Більше", color = textColor, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("Усі тайтли", color = if (isFocused) AppColors.NeonCyan else AppColors.TextSecondary)
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun BasePosterCard(
    title: String,
    cover: String?,
    seasonYear: Int?,
    score: Int?,
    isFavorite: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    contentOverlay: @Composable BoxScope.() -> Unit = {},
) {
    var isFocused by remember { mutableStateOf(false) }
    val textColor = if (isFocused) AppColors.NeonCyan else AppColors.TextCardHover

    Column(
        modifier = modifier
            .padding(4.dp)
            .onFocusChanged { isFocused = it.isFocused }
    ) {
        Card(
            onClick = onClick,
            onLongClick = onLongClick,
            scale = CardDefaults.scale(scale = 1f, focusedScale = 1f),
            border = CardDefaults.border(border = Border.None, focusedBorder = Border.None),
            glow = CardDefaults.glow(
                glow = Glow.None,
                focusedGlow = Glow(elevationColor = AppColors.NeonCyan, elevation = 12.dp)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f),
        ) {
            Box(Modifier.fillMaxSize()) {
                AsyncImage(
                    model = cover,
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                if (score != null) {
                    ReleaseBadge(
                        score = score,
                        modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
                    )
                }
                contentOverlay()
                if (isFavorite) {
                    FavoriteBadge(Modifier.align(Alignment.BottomEnd).padding(6.dp))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Column(modifier = Modifier.padding(horizontal = 4.dp)) {
            Text(
                text = title,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelMedium,
                color = textColor,
                fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Normal,
            )
            seasonYear?.let { year ->
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

    BasePosterCard(
        title = item.title.display,
        cover = item.cover,
        seasonYear = item.seasonYear,
        score = item.score,
        isFavorite = isFavorite,
        modifier = modifier,
        onClick = onClick,
        onLongClick = { showFavoriteMenu = true },
        contentOverlay = {
            if (showRelatedBadge) {
                Box(
                    modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
                        .clip(RoundedCornerShape(8.dp)).background(AppColors.BadgeOverlay)
                        .padding(7.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    FontAwesomeIcon(FaIcons.LINK, color = AppColors.TextLight, size = 13.sp)
                }
            }
        }
    )

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
            PrimaryButton(onClick = onRetry, text = "Повторити")
        }
    }
}

@Composable
private fun PrimaryButton(onClick: () -> Unit, text: String) {
    var isFocused by remember { mutableStateOf(false) }
    Button(
        onClick = onClick,
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
            .zIndex(if (isFocused) 1f else 0f)
            .onFocusChanged { isFocused = it.isFocused }
            .neonGlow(focused = isFocused, borderRadius = 12.dp)
    ) {
        Text(text)
    }
}
