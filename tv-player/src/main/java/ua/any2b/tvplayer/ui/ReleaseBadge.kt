package ua.any2b.tvplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text

@Composable
fun ReleaseBadge(
    format: String?,
    score: Int?,
    modifier: Modifier = Modifier
) {
    val isMovie = format?.equals("MOVIE", ignoreCase = true) == true
    val typeText = if (isMovie) "Movie" else "Series"

    // T-90 for movies (light green), T-80 for series (dim/medium green)
    val leftBgColor = if (isMovie) Color(0xFFC2E8CE) else Color(0xFF8CD8A3)
    val leftTextColor = Color(0xFF0F172A)

    val lightCyanGrey = Color(0xFFB8C2D1)

    val hasScore = score != null
    val ratingColor = if (score != null) scoreToSpectrumColor(score) else lightCyanGrey
    val scoreText = if (score != null) "$score%" else "null"
    val scoreTextColor = ratingColor

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(leftBgColor),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left half: Type ("Movie" / "Series")
        Box(
            modifier = Modifier
                .background(leftBgColor)
                .padding(horizontal = 7.dp, vertical = 3.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = typeText,
                color = leftTextColor,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // Vertical divider between left and right halves
        Box(
            modifier = Modifier
                .width(1.dp)
                .height(14.dp)
                .background(leftTextColor.copy(alpha = 0.25f))
        )

        // Right half: Rating percentage / "null" with spectrum outline and text matching outline color
        Box(
            modifier = Modifier
                .background(Color(0xDD15171E))
                .border(
                    width = 1.5.dp,
                    color = ratingColor,
                    shape = RoundedCornerShape(topEnd = 50.dp, bottomEnd = 50.dp)
                )
                .padding(horizontal = 7.dp, vertical = 3.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = scoreText,
                color = scoreTextColor,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * Maps score (0..100) to the visible light spectrum wavelength (740nm Red -> 380nm Violet)
 * and converts to Jetpack Compose Color.
 */
fun scoreToSpectrumColor(score: Int): Color {
    val clamped = score.coerceIn(0, 100)
    val wavelength = 740.0 - (clamped / 100.0) * (740.0 - 380.0)
    var r = 0.0
    var g = 0.0
    var b = 0.0
    var factor = 1.0

    when {
        wavelength in 380.0..440.0 -> {
            r = -(wavelength - 440.0) / (440.0 - 380.0)
            g = 0.0
            b = 1.0
        }
        wavelength in 440.0..490.0 -> {
            r = 0.0
            g = (wavelength - 440.0) / (490.0 - 440.0)
            b = 1.0
        }
        wavelength in 490.0..510.0 -> {
            r = 0.0
            g = 1.0
            b = -(wavelength - 510.0) / (510.0 - 490.0)
        }
        wavelength in 510.0..580.0 -> {
            r = (wavelength - 510.0) / (580.0 - 510.0)
            g = 1.0
            b = 0.0
        }
        wavelength in 580.0..645.0 -> {
            r = 1.0
            g = -(wavelength - 645.0) / (645.0 - 580.0)
            b = 0.0
        }
        wavelength in 645.0..740.0 -> {
            r = 1.0
            g = 0.0
            b = 0.0
        }
    }

    if (wavelength in 380.0..420.0) {
        factor = 0.3 + 0.7 * (wavelength - 380.0) / (420.0 - 380.0)
    } else if (wavelength in 700.0..740.0) {
        factor = 0.3 + 0.7 * (740.0 - wavelength) / (740.0 - 700.0)
    }

    return Color(
        red = (r * factor).toFloat().coerceIn(0f, 1f),
        green = (g * factor).toFloat().coerceIn(0f, 1f),
        blue = (b * factor).toFloat().coerceIn(0f, 1f)
    )
}
