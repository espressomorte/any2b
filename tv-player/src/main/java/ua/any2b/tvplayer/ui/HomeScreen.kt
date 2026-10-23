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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
) {
    val context = LocalContext.current

    Row(modifier = Modifier.fillMaxSize()) {
        // Left Navigation Island (Острівець з меню)
        NavigationIsland(
            onSearchClick = {
                val intent = Intent(context, ua.any2b.tvplayer.SearchActivity::class.java)
                context.startActivity(intent)
            },
            onHistoryClick = {
                val intent = Intent(context, ua.any2b.tvplayer.HistoryActivity::class.java)
                context.startActivity(intent)
            },
            onFavoritesClick = {
                android.widget.Toast.makeText(context, "Обране", android.widget.Toast.LENGTH_SHORT).show()
            },
            onSettingsClick = {
                val intent = Intent(context, ua.any2b.tvplayer.SettingsActivity::class.java)
                context.startActivity(intent)
            }
        )

        // Main Catalog Content (shifted right)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            when (state) {
                HomeState.Loading -> AnimariumLoadingScreen()
                is HomeState.Error -> ErrorBlock(state.message, onRetry)
                is HomeState.Ready -> Rows(state.rows, onOpen)
            }
        }
    }
}

@Composable
private fun NavigationIsland(
    onSearchClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onFavoritesClick: () -> Unit,
    onSettingsClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .width(88.dp)
            .fillMaxHeight()
            .padding(vertical = 24.dp, horizontal = 12.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xFF15171E).copy(alpha = 0.85f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically)
        ) {
            NavIconItem(icon = "🔍", onClick = onSearchClick)
            NavIconItem(icon = "🕒", onClick = onHistoryClick)
            NavIconItem(icon = "❤️", onClick = onFavoritesClick)
            NavIconItem(icon = "⚙️", onClick = onSettingsClick)
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun NavIconItem(icon: String, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }

    Surface(
        onClick = onClick,
        modifier = Modifier
            .size(56.dp)
            .onFocusChanged { isFocused = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(16.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (isFocused) Color(0xFF4F378B).copy(alpha = 0.6f) else Color.Transparent,
            focusedContainerColor = Color(0xFF4F378B).copy(alpha = 0.8f)
        )
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text = icon, fontSize = 22.sp, fontFamily = IbmPlexMono)
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
            model = R.drawable.standby,
            contentDescription = "Standby",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun Rows(rows: List<HomeRow>, onOpen: (AnimeCard) -> Unit) {
    val firstCardId = rows.firstOrNull()?.items?.firstOrNull()?.id
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(rows) {
        try {
            focusRequester.requestFocus()
        } catch (_: Exception) {}
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 32.dp, bottom = 48.dp, start = 24.dp, end = 48.dp),
        verticalArrangement = Arrangement.spacedBy(32.dp),
    ) {
        items(rows, key = { it.id }) { row ->
            Column {
                Text(
                    text = row.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontFamily = IbmPlexMono,
                    modifier = Modifier.padding(start = 12.dp, bottom = 14.dp),
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    items(row.items, key = { it.id }) { card ->
                        val isFirst = card.id == firstCardId
                        PosterCard(
                            item = card,
                            modifier = if (isFirst) Modifier.width(160.dp).focusRequester(focusRequester) else Modifier.width(160.dp),
                            onClick = { onOpen(card) }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PosterCard(item: AnimeCard, modifier: Modifier = Modifier, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    val textColor = if (isFocused) Color(0xFFD0BCFF) else Color(0xFF94A3B8)
    val scale = if (isFocused) 1.05f else 1.0f

    Column(
        modifier = modifier
            .onFocusChanged { isFocused = it.isFocused }
            .scale(scale)
    ) {
        Card(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f),
        ) {
            AsyncImage(
                model = item.cover,
                contentDescription = item.title.display,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = item.title.display,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelMedium,
            fontFamily = IbmPlexMono,
            color = textColor,
            fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ErrorBlock(message: String, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(message, style = MaterialTheme.typography.titleMedium, fontFamily = IbmPlexMono, textAlign = TextAlign.Center)
            Button(onClick = onRetry) { Text("Повторити", fontFamily = IbmPlexMono) }
        }
    }
}
