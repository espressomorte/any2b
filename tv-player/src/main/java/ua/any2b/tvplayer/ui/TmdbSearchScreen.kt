package ua.any2b.tvplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import android.os.SystemClock
import android.view.KeyEvent as AndroidKeyEvent
import android.view.ViewConfiguration as AndroidViewConfiguration
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Border
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import ua.any2b.core.tmdb.TmdbMediaType
import ua.any2b.core.tmdb.TmdbTitle

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TmdbSearchScreen(
    state: TmdbSearchState,
    onQueryChanged: (String) -> Unit,
    onSearch: () -> Unit,
    onSelectRecentTitle: (String) -> Unit,
    onClearHistory: () -> Unit,
    onOpen: (TmdbTitle) -> Unit,
) {
    val inputFocus = remember { FocusRequester() }
    val searchFocus = remember { FocusRequester() }
    val firstResultFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val finishInput = { keyboard?.hide(); focusManager.clearFocus(force = true) }
    LaunchedEffect(Unit) { inputFocus.requestFocus() }
    LaunchedEffect(state.showRecentTitles, state.loading, state.searched, state.results) {
        if (!state.showRecentTitles && (state.loading || state.searched)) {
            finishInput()
            if (state.searched) {
                withFrameNanos { }
                if (state.results.isNotEmpty()) firstResultFocus.requestFocus()
                else searchFocus.requestFocus()
                keyboard?.hide()
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(AppColors.BackgroundDark).padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Text("Пошук тайтла · TMDB", style = MaterialTheme.typography.headlineLarge,
            color = Color.White, fontWeight = FontWeight.Bold)
        Row(modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.fillMaxWidth(0.5f).height(56.dp)
                    .clip(RoundedCornerShape(12.dp)).background(AppColors.SurfaceDark)
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (state.query.isEmpty()) Text("Введіть назву фільму або серіалу…",
                    color = AppColors.TextSecondary, fontSize = 14.sp)
                BasicTextField(
                    value = state.query,
                    onValueChange = onQueryChanged,
                    singleLine = true,
                    textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { finishInput(); onSearch() }),
                    modifier = Modifier.fillMaxWidth().focusRequester(inputFocus),
                )
            }
            TmdbSearchActionIcon(FaIcons.SEARCH, "Шукати у TMDB",
                enabled = state.query.isNotBlank(),
                modifier = Modifier.focusRequester(searchFocus)) { finishInput(); onSearch() }
            TmdbSearchActionIcon(FaIcons.RECYCLE, "Очистити історію пошуку",
                enabled = state.recentTitles.isNotEmpty() || state.query.isNotEmpty() ||
                    state.results.isNotEmpty() || state.error != null,
                onClick = { finishInput(); onClearHistory() })
        }

        if (state.showRecentTitles && state.recentTitles.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                state.recentTitles.forEachIndexed { index, title ->
                    var focused by remember(title) { mutableStateOf(false) }
                    Text(
                        text = title,
                        color = if (focused) AppColors.NeonCyan else Color.LightGray.copy(
                            alpha = (0.9f - index * 0.1f).coerceAtLeast(0.25f)),
                        fontSize = (31 - index).sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth()
                            .onFocusChanged { focused = it.isFocused }
                            .clickable {
                                finishInput()
                                onSelectRecentTitle(title)
                            },
                    )
                }
            }
        }

        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.TopStart) {
            when {
                state.loading -> Text("Пошук у TMDB…", color = AppColors.AccentLight,
                    style = MaterialTheme.typography.titleMedium)
                state.error != null -> Text(state.error, color = AppColors.ErrorRed,
                    style = MaterialTheme.typography.titleMedium)
                !state.searched -> Unit
                state.results.isEmpty() -> Text("Нічого не знайдено", color = AppColors.TextSecondary,
                    style = MaterialTheme.typography.titleMedium)
                else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Результати пошуку (${state.results.size})", color = Color.White,
                        style = MaterialTheme.typography.titleLarge)
                    LazyRow(
                        contentPadding = PaddingValues(vertical = 12.dp, horizontal = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                        modifier = Modifier.fillMaxWidth().graphicsLayer { clip = false },
                    ) {
                        itemsIndexed(state.results, key = { _, title -> "${title.type}:${title.id}" }) { index, title ->
                            TmdbPosterCard(title, state.query, onClick = { onOpen(title) },
                                focusRequester = if (index == 0) firstResultFocus else null)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TmdbSearchActionIcon(icon: String, label: String, enabled: Boolean,
    modifier: Modifier = Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        enabled = enabled,
        scale = ClickableSurfaceDefaults.scale(scale = 1f, focusedScale = 1f),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
        border = ClickableSurfaceDefaults.border(border = Border.None, focusedBorder = Border.None),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = AppColors.SurfaceCard,
            focusedContainerColor = AppColors.NeonCyanLight,
        ),
        modifier = modifier.size(48.dp)
            .semantics { contentDescription = label }
            .onFocusChanged { focused = it.isFocused },
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            FontAwesomeIcon(icon, color = if (focused) AppColors.ButtonDarkText else AppColors.TextLight)
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun TmdbPosterCard(title: TmdbTitle, query: String = "",
    onClick: () -> Unit, onFocused: (() -> Unit)? = null,
    focusRequester: FocusRequester? = null, onLongClick: (() -> Unit)? = null,
    navigateLeftTo: FocusRequester? = null) {
    var focused by remember { mutableStateOf(false) }
    var suppressClickUntil by remember { mutableLongStateOf(0L) }
    var centerKeyDown by remember { mutableStateOf(false) }
    var keyDownAt by remember { mutableLongStateOf(0L) }
    var longPressTriggered by remember { mutableStateOf(false) }
    var longPressJob by remember { mutableStateOf<Job?>(null) }
    val keyScope = rememberCoroutineScope()
    val longPressTimeout = AndroidViewConfiguration.getLongPressTimeout().toLong()
    Column(Modifier.width(160.dp).padding(4.dp)) {
        Card(
            onClick = {
                if (onLongClick == null || SystemClock.uptimeMillis() >= suppressClickUntil)
                    onClick()
            },
            onLongClick = onLongClick?.let { longClick ->
                {
                    if (!centerKeyDown) {
                        suppressClickUntil = SystemClock.uptimeMillis() + 700L
                        longClick()
                    }
                }
            },
            scale = CardDefaults.scale(scale = 1f, focusedScale = 1f),
            shape = CardDefaults.shape(RoundedCornerShape(12.dp)),
            modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f)
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                .focusProperties { navigateLeftTo?.let { left = it } }
                .onPreviewKeyEvent { event ->
                    val native = event.nativeKeyEvent
                    val isCenter = native.keyCode == AndroidKeyEvent.KEYCODE_DPAD_CENTER ||
                        native.keyCode == AndroidKeyEvent.KEYCODE_ENTER ||
                        native.keyCode == AndroidKeyEvent.KEYCODE_NUMPAD_ENTER ||
                        native.keyCode == AndroidKeyEvent.KEYCODE_BUTTON_SELECT
                    if (!isCenter) return@onPreviewKeyEvent false
                    if (native.action == AndroidKeyEvent.ACTION_DOWN) {
                        if (onLongClick == null) return@onPreviewKeyEvent false
                        if (!centerKeyDown) {
                            centerKeyDown = true
                            keyDownAt = SystemClock.uptimeMillis()
                            longPressTriggered = false
                            longPressJob?.cancel()
                            longPressJob = keyScope.launch {
                                delay(longPressTimeout)
                                if (centerKeyDown) longPressTriggered = true
                            }
                        }
                        true
                    } else if (native.action == AndroidKeyEvent.ACTION_UP) {
                        if (onLongClick == null || !centerKeyDown)
                            return@onPreviewKeyEvent false
                        val wasLongPress = longPressTriggered ||
                            SystemClock.uptimeMillis() - keyDownAt >= longPressTimeout
                        centerKeyDown = false
                        longPressJob?.cancel()
                        longPressJob = null
                        if (wasLongPress) {
                            suppressClickUntil = SystemClock.uptimeMillis() + 700L
                            onLongClick?.invoke()
                        } else onClick()
                        true
                    } else false
                }
                .onFocusChanged {
                    focused = it.isFocused
                    if (!it.isFocused && centerKeyDown) {
                        centerKeyDown = false
                        longPressJob?.cancel()
                        longPressJob = null
                    }
                    if (it.isFocused) {
                        onFocused?.invoke()
                    }
                },
        ) { TmdbPosterArt(title, Modifier.fillMaxSize()) }
        Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 5.dp)) {
            Text((if (query.isBlank()) listOf(title.title) else title.searchNames(query)).joinToString(" · "),
                color = if (focused) AppColors.NeonCyan else AppColors.TextLight,
                maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelMedium, modifier = Modifier.heightIn(min = 36.dp))
            title.year?.let { year ->
                Spacer(Modifier.height(2.dp))
                Text(year.toString(), style = MaterialTheme.typography.labelMedium,
                    color = if (focused) AppColors.NeonCyan else AppColors.TextLight,
                    fontSize = 10.sp,
                    fontWeight = if (focused) FontWeight.Bold else FontWeight.Normal)
            }
        }
    }
}

@Composable
private fun TmdbPosterArt(title: TmdbTitle, modifier: Modifier = Modifier) {
    Box(modifier.background(AppColors.SurfaceCard)) {
        AsyncImage(model = title.posterUrl, contentDescription = title.title,
            contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        title.rating?.let { score ->
            ReleaseBadge((score * 10).toInt(), Modifier.align(Alignment.TopEnd).padding(8.dp))
        }
        if (title.type == TmdbMediaType.TV) {
            Box(Modifier.align(Alignment.TopStart).padding(8.dp)
                .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(6.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp)) {
                Text("TV", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
