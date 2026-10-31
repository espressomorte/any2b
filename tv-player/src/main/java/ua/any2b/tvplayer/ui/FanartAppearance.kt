package ua.any2b.tvplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.lightColorScheme
import ua.any2b.core.fanart.FanartArtwork

/** Tonal surfaces follow the artwork while retaining readable text. */
internal data class DetailsSurfaceColors(
    val panel: Color,
    val card: Color,
    val text: Color,
    val mutedText: Color,
)

internal fun detailsSurfaceColors(palette: FanartPalette?, experimental: Boolean): DetailsSurfaceColors {
    if (!experimental || palette == null) return DetailsSurfaceColors(
        AppColors.SurfaceDark, AppColors.SurfaceCard, Color.White, AppColors.TextSecondary)
    val tint = if (palette.light) Color.Black else Color.White
    return DetailsSurfaceColors(
        panel = lerp(palette.background, tint, 0.16f),
        card = lerp(palette.background, tint, 0.20f),
        text = if (palette.light) Color(0xFF182330) else Color.White,
        mutedText = if (palette.light) Color(0xFF415164) else AppColors.TextSecondary,
    )
}

@Composable
fun FanartDayTheme(enabled: Boolean, content: @Composable () -> Unit) {
    if (enabled) MaterialTheme(colorScheme = lightColorScheme(),
        typography = MaterialTheme.typography, content = content)
    else content()
}

/** Bleeds 20% beyond its measured slot and fades evenly into the page on all four sides. */
@Composable
fun FanartPoster(artwork: FanartArtwork, contentDescription: String,
    backgroundColor: Color, modifier: Modifier = Modifier) {
    Box(modifier) {
        Box(Modifier.fillMaxSize().posterBleed()) {
            AsyncImage(model = artwork.posterUrl, contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize())
            val fade = 56.dp
            Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().height(fade)
                .background(Brush.verticalGradient(listOf(backgroundColor, Color.Transparent))))
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(fade)
                .background(Brush.verticalGradient(listOf(Color.Transparent, backgroundColor))))
            Box(Modifier.align(Alignment.CenterStart).width(fade).fillMaxHeight()
                .background(Brush.horizontalGradient(listOf(backgroundColor, Color.Transparent))))
            Box(Modifier.align(Alignment.CenterEnd).width(fade).fillMaxHeight()
                .background(Brush.horizontalGradient(listOf(Color.Transparent, backgroundColor))))
        }
    }
}

@Composable
fun FanartLogo(artwork: FanartArtwork, contentDescription: String, modifier: Modifier = Modifier) {
    AsyncImage(model = artwork.logoUrl, contentDescription = contentDescription,
        contentScale = ContentScale.Fit, modifier = modifier)
}

private fun Modifier.posterBleed(): Modifier = layout { measurable, constraints ->
    val parentWidth = constraints.maxWidth
    val parentHeight = constraints.maxHeight
    val artWidth = (parentWidth * 1.2f).toInt()
    val artHeight = (parentHeight * 1.2f).toInt()
    val placeable = measurable.measure(
        androidx.compose.ui.unit.Constraints.fixed(artWidth, artHeight))
    layout(parentWidth, parentHeight) {
        placeable.place((parentWidth - artWidth) / 2, (parentHeight - artHeight) / 2)
    }
}
