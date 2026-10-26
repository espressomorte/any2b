package ua.any2b.tvplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import coil.compose.AsyncImage
import ua.any2b.tvplayer.data.WatchHistoryEntry
import ua.any2b.tvplayer.data.FavoritesManager
import ua.any2b.core.anilist.AnimeCard
import ua.any2b.core.anilist.Title

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun HistoryScreen(
    history: List<WatchHistoryEntry>,
    onOpen: (Int) -> Unit,
    onBack: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0B0E))
            .padding(32.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Історія переглядів",
                    style = MaterialTheme.typography.headlineLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }

            if (history.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Історія переглядів порожня",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color(0xFF64748B),
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    items(history, key = { it.animeId }) { item ->
                        HistoryCard(
                            item = item,
                            onClick = { onOpen(item.animeId) }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun HistoryCard(item: WatchHistoryEntry, onClick: () -> Unit) {
    val context = LocalContext.current
    val favoritesManager = remember(context) { FavoritesManager(context) }
    val favoriteIds by favoritesManager.favoriteIds.collectAsState()
    val isFavorite = item.animeId in favoriteIds
    var showFavoriteMenu by remember { mutableStateOf(false) }
    var isFocused by remember { mutableStateOf(false) }
    val lightCyanGrey = Color(0xFFB8C2D1)
    val textColor = if (isFocused) NeonCyan else lightCyanGrey
    val scale = if (isFocused) 1.025f else 1.0f

    Column(
        modifier = Modifier
            .fillMaxWidth()
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
                    contentDescription = item.titleDisplay,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color(0xCC0F1115))
                        .padding(8.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Серія ${item.episodeNumber}",
                            color = Color(0xFFD0BCFF),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Color(0xFF20232D))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(item.progress.coerceIn(0f, 1f))
                                    .fillMaxHeight()
                                    .background(Color(0xFFD0BCFF))
                            )
                        }
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
                text = item.titleDisplay,
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
                favoritesManager.toggle(AnimeCard(
                    id = item.animeId,
                    title = Title(item.romaji, item.english, item.native),
                    cover = item.cover,
                    score = item.score,
                    episodes = item.episodes,
                    format = item.format,
                    status = item.status,
                    seasonYear = item.seasonYear,
                    nextEpisode = null,
                ))
            },
            onDismiss = { showFavoriteMenu = false },
        )
    }
}
