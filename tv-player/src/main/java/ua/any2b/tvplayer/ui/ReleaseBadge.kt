package ua.any2b.tvplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text

@Composable
fun ReleaseBadge(
    score: Int?,
    modifier: Modifier = Modifier
) {
    if (score == null) return
    val ratingColor = scoreToSpectrumColor(score)

    // Визначаємо, який колір тексту буде контрастним до кольорового фону
    val contentColor = if (ratingColor.luminance() > 0.5f) Color.Black else Color.White

    // Кольорова заливка бейджа (можна регулювати alpha для соковитості)
    val backgroundTint = ratingColor.copy(alpha = 0.85f)

    val floatScore = score / 10f
    val scoreText = String.format(java.util.Locale.US, "%.1f", floatScore)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(backgroundTint)
            .border(1.5.dp, contentColor.copy(alpha = 0.5f), RoundedCornerShape(percent = 50))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = scoreText,
            color = contentColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

fun adjustBrightness(color: Color, factor: Float): Color {
    val argb = android.graphics.Color.argb(
        (color.alpha * 255).toInt(),
        (color.red * 255).toInt(),
        (color.green * 255).toInt(),
        (color.blue * 255).toInt()
    )
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(argb, hsv)
    hsv[2] = (hsv[2] * factor).coerceIn(0f, 1f)
    return Color(android.graphics.Color.HSVToColor(hsv))
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

@Preview
@Composable
private fun ReleaseBadgePreview() {
    AppTheme {
        ReleaseBadge(
            score = 85
        )
    }
}
