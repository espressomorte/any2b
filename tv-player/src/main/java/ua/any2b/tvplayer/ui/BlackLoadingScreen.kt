package ua.any2b.tvplayer.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import ua.any2b.tvplayer.R

internal const val STATUS_FONT_HEIGHT_FRACTION = 0.014f
internal const val STATUS_BOTTOM_OFFSET_FRACTION = 0.15f

private val GeistMono = FontFamily(Font(R.font.geist_mono_regular, FontWeight.Normal))

/** Shared black loading surface: optional title logo at center and one adaptive status line. */
@Composable
internal fun BlackLoadingScreen(message: String, logoUrl: String? = null) {
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        val density = LocalDensity.current
        val statusFontSize = with(density) {
            (maxHeight.toPx() * STATUS_FONT_HEIGHT_FRACTION /
                (this.density * this.fontScale)).sp
        }
        if (!logoUrl.isNullOrBlank()) {
            AsyncImage(model = logoUrl,
                contentDescription = "Логотип тайтлу",
                contentScale = ContentScale.Fit,
                modifier = Modifier.align(Alignment.Center)
                    .widthIn(max = 480.dp).width(480.dp).height(132.dp))
        }
        Text(message,
            modifier = Modifier.align(Alignment.BottomCenter)
                .padding(bottom = maxHeight * STATUS_BOTTOM_OFFSET_FRACTION)
                .widthIn(max = 900.dp).fillMaxWidth().padding(horizontal = 24.dp),
            color = MaterialTheme.colorScheme.onSurface,
            fontFamily = GeistMono,
            fontSize = statusFontSize,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center)
    }
}
