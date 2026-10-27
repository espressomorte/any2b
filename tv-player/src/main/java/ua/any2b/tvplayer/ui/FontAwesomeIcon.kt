package ua.any2b.tvplayer.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import ua.any2b.tvplayer.R

private val FontAwesomeSolid = FontFamily(Font(R.font.fa_solid_900))

object FaIcons {
    const val PENCIL = "\uf303"
    const val COG = "\uf013"
    const val SEARCH = "\uf002"
    const val LINK = "\uf0c1"
    const val BOOKMARK = "\uf02e"
    const val ANGLE_DOWN = "\uf107"
    const val ARROW_UP = "\uf062"
    const val ARROW_DOWN = "\uf063"
    const val CHECK = "\uf00c"
}

@Composable
fun FontAwesomeIcon(
    icon: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    size: TextUnit = 20.sp,
) {
    Text(icon, modifier = modifier, fontFamily = FontAwesomeSolid, fontSize = size, color = color)
}
