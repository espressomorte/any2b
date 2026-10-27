package ua.any2b.tvplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.*

@Composable
fun FavoriteBadge(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xDD15171E))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        FontAwesomeIcon(FaIcons.BOOKMARK, color = Color(0xFFD0BCFF), size = 13.sp)
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun FavoriteContextMenu(isFavorite: Boolean, onToggle: () -> Unit, onDismiss: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onDismiss) {
        Button(
            onClick = onToggle,
            scale = ButtonDefaults.scale(scale = 1f, focusedScale = 1f),
            shape = ButtonDefaults.shape(RoundedCornerShape(12.dp)),
            glow = ButtonDefaults.glow(glow = Glow.None, focusedGlow = Glow(elevationColor = AppColors.NeonCyan, elevation = 10.dp)),
            modifier = Modifier
                .zIndex(if (isFocused) 1f else 0f)
                .onFocusChanged { isFocused = it.isFocused }
                .neonGlow(focused = isFocused, borderRadius = 12.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FontAwesomeIcon(FaIcons.BOOKMARK, size = 18.sp)
                Text(if (isFavorite) "Вилучити з обраного" else "Додати в обране")
            }
        }
    }
}
