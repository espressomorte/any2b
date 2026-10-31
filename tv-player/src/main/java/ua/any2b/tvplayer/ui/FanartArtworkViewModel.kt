package ua.any2b.tvplayer.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import kotlin.math.max
import kotlin.math.min
import ua.any2b.core.fanart.FanartArtwork
import ua.any2b.core.fanart.FanartClient
import ua.any2b.core.tmdb.TmdbMediaType
import ua.any2b.tvplayer.data.FanartKeyStore

data class FanartArtworkKey(val type: TmdbMediaType, val id: Int)
data class FanartPalette(val background: Color, val light: Boolean)

sealed interface FanartArtworkState {
    data object Idle : FanartArtworkState
    data class Loading(val key: FanartArtworkKey) : FanartArtworkState
    data class Ready(
        val key: FanartArtworkKey,
        val artwork: FanartArtwork?,
        val palette: FanartPalette = FanartPalette(AppColors.BackgroundDark, false),
        val reason: String? = null,
    ) : FanartArtworkState
}

class FanartArtworkViewModel(application: Application) : AndroidViewModel(application) {
    private val client = FanartClient()
    private val keyStore = FanartKeyStore(application)
    private val _state = MutableStateFlow<FanartArtworkState>(FanartArtworkState.Idle)
    val state: StateFlow<FanartArtworkState> = _state
    private var job: Job? = null

    fun load(type: TmdbMediaType, id: Int) {
        val lookup = FanartArtworkKey(type, id)
        if (id <= 0 || (_state.value as? FanartArtworkState.Ready)?.key == lookup ||
            (_state.value as? FanartArtworkState.Loading)?.key == lookup) return
        job?.cancel()
        val key = keyStore.read()
        if (key.isNullOrBlank()) {
            _state.value = FanartArtworkState.Ready(lookup, null,
                reason = "Не задано ключ fanart.tv")
            return
        }
        _state.value = FanartArtworkState.Loading(lookup)
        job = viewModelScope.launch {
            val result = try {
                withTimeoutOrNull(20_000) {
                    val found = client.inspect(type, id, key)
                    val reason = when {
                        !found.entryFound -> "Для цього TMDB ID немає запису на fanart.tv"
                        !found.posterFound && !found.englishLogoFound ->
                            "Немає постера без написів або англійською та англійського логотипа"
                        !found.posterFound -> "Немає постера без написів або англійською"
                        !found.englishLogoFound -> "Немає англійського логотипа"
                        else -> null
                    }
                    found.artwork?.let { candidate ->
                        val app = getApplication<Application>()
                        val loaded = coroutineScope {
                            val poster = async {
                                app.imageLoader.execute(ImageRequest.Builder(app)
                                    .data(candidate.posterUrl).size(700, 1050)
                                    .allowHardware(false).build()) as? SuccessResult
                            }
                            val logo = async {
                                app.imageLoader.execute(ImageRequest.Builder(app)
                                    .data(candidate.logoUrl).size(900, 450)
                                    .allowHardware(false).build()) as? SuccessResult
                            }
                            poster.await() to logo.await()
                        }
                        val posterResult = loaded.first
                        val logoResult = loaded.second
                        if (posterResult != null && logoResult != null)
                            FanartArtworkState.Ready(lookup, candidate,
                                palette = withContext(Dispatchers.Default) {
                                    paletteFromArtwork(posterResult, logoResult)
                                })
                        else FanartArtworkState.Ready(lookup, null,
                            reason = "Не вдалося завантажити зображення")
                    } ?: FanartArtworkState.Ready(lookup, null, reason = reason)
                } ?: FanartArtworkState.Ready(lookup, null,
                    reason = "Час очікування fanart.tv вичерпано")
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                val reason = when {
                    error.message?.contains("HTTP 401") == true -> "Ключ fanart.tv не прийнято"
                    error.message?.contains("HTTP 429") == true -> "fanart.tv тимчасово обмежив запити"
                    error.message?.startsWith("Fanart HTTP ") == true ->
                        "fanart.tv повернув ${error.message}"
                    error is UnknownHostException -> "Не вдалося знайти адресу fanart.tv (DNS)"
                    error is SSLException -> "Не вдалося встановити захищене з’єднання (TLS)"
                    error is SocketTimeoutException -> "fanart.tv не відповів вчасно"
                    else -> "Не вдалося зв’язатися з fanart.tv"
                }
                FanartArtworkState.Ready(lookup, null, reason = reason)
            }
            if ((_state.value as? FanartArtworkState.Loading)?.key == lookup) {
                _state.value = result
            }
        }
    }
}

private fun paletteFromPoster(result: SuccessResult?): FanartPalette {
    val bitmap = (result?.drawable as? BitmapDrawable)?.bitmap
        ?: return FanartPalette(AppColors.BackgroundDark, false)
    var red = 0L
    var green = 0L
    var blue = 0L
    var samples = 0
    for (y in 1..12) for (x in 1..8) {
        val pixel = bitmap.getPixel(x * (bitmap.width - 1) / 9,
            y * (bitmap.height - 1) / 13)
        if (android.graphics.Color.alpha(pixel) < 200) continue
        red += android.graphics.Color.red(pixel)
        green += android.graphics.Color.green(pixel)
        blue += android.graphics.Color.blue(pixel)
        samples++
    }
    if (samples == 0) return FanartPalette(AppColors.BackgroundDark, false)
    val r = (red / samples).toInt()
    val g = (green / samples).toInt()
    val b = (blue / samples).toInt()
    val luminance = (0.2126 * r + 0.7152 * g + 0.0722 * b) / 255.0
    if (luminance < 0.58) return FanartPalette(AppColors.BackgroundDark, false)
    val background = Color(
        android.graphics.Color.rgb((r * 0.55 + 255 * 0.45).toInt(),
            (g * 0.55 + 255 * 0.45).toInt(), (b * 0.55 + 255 * 0.45).toInt()))
    return FanartPalette(background, true)
}

private fun paletteFromArtwork(poster: SuccessResult, logo: SuccessResult): FanartPalette {
    val palette = paletteFromPoster(poster)
    val logoBitmap = (logo.drawable as? BitmapDrawable)?.bitmap ?: return palette
    val logoColor = sampledColorBins(logoBitmap, 96, 48).maxByOrNull { it.count }?.color
        ?: return palette
    if (contrastRatio(logoColor, palette.background) >= 4.5) return palette

    val posterBitmap = (poster.drawable as? BitmapDrawable)?.bitmap ?: return palette
    val posterColors = sampledColorBins(posterBitmap, 32, 48)
        .filterNot { isDarkNeutral(it.color) }
    val darkText = Color(0xFF182330)
    val posterColor = posterColors.filter {
        contrastRatio(it.color, logoColor) >= 4.5 &&
            max(contrastRatio(it.color, Color.White), contrastRatio(it.color, darkText)) >= 4.5
    }
        .maxByOrNull { it.count }?.color
    val dominantPosterColor = posterColors.maxByOrNull { it.count }?.color ?: palette.background
    val fallback = if (contrastRatio(logoColor, Color.White) >=
        contrastRatio(logoColor, Color.Black)) Color.White else Color.Black
    val background = posterColor ?: (0..20).asSequence()
        .map { step -> lerp(dominantPosterColor, fallback, step / 20f) }
        .firstOrNull {
            !isDarkNeutral(it) && contrastRatio(it, logoColor) >= 4.5 &&
                max(contrastRatio(it, Color.White), contrastRatio(it, darkText)) >= 4.5
        } ?: fallback
    val light = contrastRatio(background, darkText) >= contrastRatio(background, Color.White)
    return palette.copy(background = background, light = light)
}

private data class ColorBin(val color: Color, val count: Int)

/** Excludes black and desaturated shades from dark through medium gray. */
private fun isDarkNeutral(color: Color): Boolean {
    val value = maxOf(color.red, color.green, color.blue)
    val lowest = minOf(color.red, color.green, color.blue)
    val saturation = if (value == 0f) 0f else (value - lowest) / value
    return value <= 0.12f || (value <= 0.68f && saturation <= 0.28f)
}

/** Samples a small fixed grid; transparent logo pixels are ignored. */
private fun sampledColorBins(bitmap: Bitmap, columns: Int, rows: Int): List<ColorBin> {
    data class Bucket(var count: Int = 0, var red: Long = 0, var green: Long = 0,
        var blue: Long = 0)
    val buckets = HashMap<Int, Bucket>()
    try {
        for (y in 0 until rows) for (x in 0 until columns) {
            val pixel = bitmap.getPixel((x * 2 + 1) * bitmap.width / (columns * 2),
                (y * 2 + 1) * bitmap.height / (rows * 2))
            if (android.graphics.Color.alpha(pixel) < 128) continue
            val red = android.graphics.Color.red(pixel)
            val green = android.graphics.Color.green(pixel)
            val blue = android.graphics.Color.blue(pixel)
            val key = ((red shr 4) shl 8) or ((green shr 4) shl 4) or (blue shr 4)
            val bucket = buckets.getOrPut(key) { Bucket() }
            bucket.count++
            bucket.red += red
            bucket.green += green
            bucket.blue += blue
        }
    } catch (_: IllegalStateException) {
        return emptyList()
    }
    return buckets.values.map { bucket ->
        ColorBin(Color(android.graphics.Color.rgb((bucket.red / bucket.count).toInt(),
            (bucket.green / bucket.count).toInt(), (bucket.blue / bucket.count).toInt())),
            bucket.count)
    }
}

private fun contrastRatio(first: Color, second: Color): Double {
    val a = first.luminance().toDouble()
    val b = second.luminance().toDouble()
    return (max(a, b) + 0.05) / (min(a, b) + 0.05)
}
