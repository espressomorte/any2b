package ua.any2b.core.fenix

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URI
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

/**
 * Клієнт для розбору iframe FenixPlay:
 * витягує hls_master_url, edge токен і генерує підписані посилання для ExoPlayer.
 */
class FenixClient(
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build(),
) {
    private val userAgent = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36"

    /**
     * Отримує сторінку fenixplay.xyz та розпаковує готове посилання на HLS-потік з токеном edge.
     */
    suspend fun resolve(fenixEmbedUrl: String): FenixStream = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(fenixEmbedUrl)
            .header("User-Agent", userAgent)
            .header("Referer", "https://anitube.in.ua/")
            .build()

        val html = http.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw Exception("FenixPlay відповів HTTP ${resp.code}")
            resp.body?.string().orEmpty()
        }

        // 1. Шукаємо master URL
        val masterMatch = Pattern.compile(""""hls_master_url":"([^"]+)"""").matcher(html)
        if (!masterMatch.find()) throw Exception("hls_master_url не знайдено на сторінці FenixPlay")
        val rawMasterUrl = masterMatch.group(1).replace("\\/", "/")

        // 2. Шукаємо edge origin та token
        val edgeMatch = Pattern.compile(""""edge":\{"origin":"([^"]+)","token":"([^"]+)"""").matcher(html)
        if (!edgeMatch.find()) throw Exception("Параметри edge (origin/token) не знайдено")
        val edgeOrigin = edgeMatch.group(1).replace("\\/", "/")
        val edgeToken = edgeMatch.group(2)

        // 3. Формуємо підписаний finalMasterUrl
        val uri = URI(rawMasterUrl)
        val query = if (uri.query.isNullOrBlank()) "edge=$edgeToken" else "${uri.query}&edge=$edgeToken"
        val finalMasterUrl = "$edgeOrigin${uri.path}?$query"

        // 4. Шукаємо додаткові доріжки (tracks)
        val tracks = mutableListOf<FenixTrack>()
        val tracksPattern = Pattern.compile(""""tracks":\s*(\[\{.*?\}\])""", Pattern.DOTALL)
        val tracksMatch = tracksPattern.matcher(html)
        if (tracksMatch.find()) {
            try {
                val array = org.json.JSONArray(tracksMatch.group(1))
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val rawHls = obj.optString("hls_url").replace("\\/", "/")
                    if (rawHls.isNotBlank()) {
                        val trackUri = URI(rawHls)
                        val trackQuery = if (trackUri.query.isNullOrBlank()) "edge=$edgeToken" else "${trackUri.query}&edge=$edgeToken"
                        val signedHls = "$edgeOrigin${trackUri.path}?$trackQuery"

                        tracks.add(
                            FenixTrack(
                                label = obj.optString("label", "Track $i"),
                                language = obj.optString("language", "uk"),
                                trackType = obj.optString("track_type", "dub"),
                                hlsUrl = signedHls,
                            )
                        )
                    }
                }
            } catch (_: Exception) {
                // Ігноруємо якщо формат tracks відрізняється
            }
        }

        FenixStream(
            finalMasterUrl = finalMasterUrl,
            tracks = tracks,
            edgeOrigin = edgeOrigin,
            edgeToken = edgeToken,
        )
    }
}
