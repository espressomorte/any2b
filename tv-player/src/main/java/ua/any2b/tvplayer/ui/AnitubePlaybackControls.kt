package ua.any2b.tvplayer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.zIndex
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Glow
import androidx.tv.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ua.any2b.core.anitube.AnitubeEpisode
import ua.any2b.core.anitube.AnitubeVoice
import ua.any2b.core.anitube.groupedVoices

internal data class AnitubeSeasonOption(val id: Int, val label: String)

@Composable
internal fun AnitubePlaybackControls(
    voices: List<AnitubeVoice>,
    selectedVoiceIndex: Int,
    onSelectVoice: (Int) -> Unit,
    episodes: List<AnitubeEpisode>,
    selectedEpisodeIndex: Int,
    onSelectEpisode: (Int) -> Unit,
    seasons: List<AnitubeSeasonOption> = emptyList(),
    selectedSeasonId: Int? = null,
    onSelectSeason: (Int) -> Unit = {},
    isMovie: Boolean,
    resolving: Boolean,
    watchLabel: String,
    canWatch: Boolean,
    watchFocusRequester: FocusRequester? = null,
    onWatch: () -> Unit,
    surfaceColors: DetailsSurfaceColors,
    showSelectors: Boolean = true,
    showWatch: Boolean = true,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (showSelectors) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showWatch) AnitubeWatchButton(resolving, watchLabel, canWatch,
                watchFocusRequester, onWatch, surfaceColors)
            if (!isMovie && seasons.size > 1) {
                val current = seasons.firstOrNull { it.id == selectedSeasonId } ?: seasons.first()
                AnitubeOptionButton(current.label, null, true, seasons.mapIndexed { index, season ->
                    index to season.label
                }, surfaceColors) { index -> seasons.getOrNull(index)?.let { onSelectSeason(it.id) } }
            }
            if (voices.isNotEmpty()) {
                val selectedVoice = voices.getOrNull(selectedVoiceIndex) ?: voices.first()
                val subtitle = selectedVoice.category.equals("СУБТИТРИ", ignoreCase = true)
                val groups = groupedVoices(voices)
                val selectedGroup = groups.firstOrNull { group ->
                    group.variants.any { it.index == selectedVoiceIndex }
                }
                val options = groups.map { it.primaryIndex to it.name }
                AnitubeOptionButton(
                    label = selectedGroup?.name ?: voiceButtonLabel(selectedVoice),
                    icon = if (subtitle) FaIcons.COMMENTING_O else FaIcons.MICROPHONE,
                    selected = true,
                    options = options,
                    optionIcon = { index -> if (voices[index].category.equals("СУБТИТРИ", ignoreCase = true))
                        FaIcons.COMMENTING_O else FaIcons.MICROPHONE },
                    surfaceColors = surfaceColors,
                    modifier = Modifier.widthIn(max = 210.dp),
                    centerLabel = true,
                    onSelect = onSelectVoice,
                )
            }
            if (!isMovie && episodes.isNotEmpty()) {
                EpisodeWindowPicker(
                    episodes = episodes,
                    selectedEpisodeIndex = selectedEpisodeIndex,
                    onEpisode = onSelectEpisode,
                    surfaceColors = surfaceColors,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        }
        if (showWatch && !showSelectors) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            AnitubeWatchButton(resolving, watchLabel, canWatch,
                watchFocusRequester, onWatch, surfaceColors)
        }
        }
    }
}

@Composable
private fun AnitubeWatchButton(resolving: Boolean, watchLabel: String, canWatch: Boolean,
    watchFocusRequester: FocusRequester?, onWatch: () -> Unit,
    surfaceColors: DetailsSurfaceColors) {
    var focused by remember { mutableStateOf(false) }
    val buttonBg = if (focused) AppColors.NeonCyanLight else surfaceColors.card
    val buttonText = if (focused) AppColors.ButtonDarkText else surfaceColors.text
    Button(
        onClick = onWatch,
        enabled = canWatch && !resolving,
        scale = ButtonDefaults.scale(scale = 1f, focusedScale = 1f),
        shape = ButtonDefaults.shape(RoundedCornerShape(10.dp)),
        glow = ButtonDefaults.glow(glow = Glow.None,
            focusedGlow = Glow(elevationColor = AppColors.NeonCyan, elevation = 8.dp)),
        colors = ButtonDefaults.colors(containerColor = buttonBg,
            contentColor = buttonText, focusedContainerColor = buttonBg,
            focusedContentColor = buttonText),
        modifier = Modifier.heightIn(min = 38.dp)
            .then(watchFocusRequester?.let(Modifier::focusRequester) ?: Modifier)
            .zIndex(if (focused) 1f else 0f)
            .onFocusChanged { focused = it.isFocused }
            .neonGlow(focused = focused, borderRadius = 10.dp),
    ) { Text(if (resolving) "Підготовка…" else watchLabel,
        fontSize = 12.sp, fontWeight = FontWeight.Bold) }
}

@Composable
private fun AnitubeOptionButton(
    label: String,
    icon: String?,
    selected: Boolean,
    options: List<Pair<Int, String>>,
    surfaceColors: DetailsSurfaceColors,
    modifier: Modifier = Modifier,
    centerLabel: Boolean = false,
    optionIcon: (Int) -> String? = { null },
    onSelect: (Int) -> Unit,
) {
    var expanded by remember(label, options) { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }
    val background = when {
        focused -> AppColors.NeonCyanLight
        selected -> AppColors.NeonCyan
        else -> surfaceColors.card
    }
    val foreground = if (focused || selected) AppColors.ButtonDarkText else surfaceColors.mutedText
    Card(
        onClick = { expanded = true },
        scale = CardDefaults.scale(scale = 1f, focusedScale = 1f),
        shape = CardDefaults.shape(RoundedCornerShape(10.dp)),
        border = CardDefaults.border(border = Border.None, focusedBorder = Border.None),
        glow = CardDefaults.glow(glow = Glow.None,
            focusedGlow = Glow(elevationColor = AppColors.NeonCyan, elevation = 8.dp)),
        colors = CardDefaults.colors(containerColor = background, focusedContainerColor = background),
        modifier = modifier.heightIn(min = 36.dp).onFocusChanged { focused = it.isFocused }
            .neonGlow(focused = focused, borderRadius = 10.dp),
    ) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically) {
            if (centerLabel) {
                Box(Modifier.width(13.dp).height(22.dp), contentAlignment = Alignment.CenterStart) {
                    if (icon != null) FontAwesomeIcon(icon, color = foreground, size = 13.sp)
                }
                Box(Modifier.height(22.dp), contentAlignment = Alignment.Center) {
                    Text(label, color = foreground, fontSize = 11.sp, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                }
                Box(Modifier.width(13.dp).height(22.dp), contentAlignment = Alignment.CenterEnd) {
                    FontAwesomeIcon(FaIcons.ANGLE_DOWN, color = foreground, size = 10.sp)
                }
            } else {
                if (icon != null) FontAwesomeIcon(icon, color = foreground, size = 13.sp)
                Text(label, color = foreground, fontSize = 11.sp, maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
                FontAwesomeIcon(FaIcons.ANGLE_DOWN, color = foreground, size = 10.sp)
            }
        }
    }
    if (expanded) Dialog(onDismissRequest = { expanded = false }) {
        Column(Modifier.widthIn(min = 280.dp, max = 560.dp)
            .background(surfaceColors.panel, RoundedCornerShape(12.dp))
            .padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            LazyColumn(contentPadding = PaddingValues(vertical = 4.dp)) {
                itemsIndexed(options, key = { _, option -> option.first }) { _, option ->
                    Button(onClick = { expanded = false; onSelect(option.first) },
                        modifier = Modifier.fillMaxWidth()) {
                        optionIcon(option.first)?.let {
                            FontAwesomeIcon(it, size = 13.sp)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(option.second, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}
