package ua.any2b.core.fanart

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import ua.any2b.core.tmdb.TmdbMediaType

data class FanartArtwork(val posterUrl: String, val logoUrl: String)
data class FanartLookup(
    val artwork: FanartArtwork?,
    val entryFound: Boolean,
    val posterFound: Boolean,
    val textlessPosterFound: Boolean,
    val englishLogoFound: Boolean,
)

/** A personal key is supplied by the caller and sent only in the client-key header. */
class FanartClient(
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .callTimeout(16, TimeUnit.SECONDS)
        .build(),
    private val baseUrl: HttpUrl = "https://webservice.fanart.tv/v3.2/".toHttpUrl(),
) {
    /** Uses a stable, well-known movie so 404 does not masquerade as a bad key. */
    suspend fun checkKey(personalKey: String): Int {
        require(personalKey.isNotBlank())
        val request = Request.Builder()
            .url(baseUrl.newBuilder().addPathSegment("movies").addPathSegment("550").build())
            .header("client-key", personalKey)
            .header("Accept", "application/json")
            .build()
        return execute(request).use { it.code }
    }

    suspend fun artwork(type: TmdbMediaType, tmdbId: Int, personalKey: String): FanartArtwork? {
        return inspect(type, tmdbId, personalKey).artwork
    }

    suspend fun inspect(type: TmdbMediaType, tmdbId: Int, personalKey: String): FanartLookup {
        require(tmdbId > 0)
        require(personalKey.isNotBlank())
        val url = baseUrl.newBuilder()
            .addPathSegment(if (type == TmdbMediaType.MOVIE) "movies" else "tv")
            .addPathSegment(tmdbId.toString())
            .apply { if (type == TmdbMediaType.TV) addQueryParameter("source", "tmdb") }
            .build()
        val request = Request.Builder().url(url)
            .header("client-key", personalKey)
            .header("Accept", "application/json")
            .build()
        val response = execute(request)
        return response.use {
            if (it.code == 404) return@use FanartLookup(null, false, false, false, false)
            if (!it.isSuccessful) throw IOException("Fanart HTTP ${it.code}")
            val json = try { JSONObject(it.body?.string().orEmpty()) }
                catch (_: Exception) { throw IOException("Invalid Fanart response") }
            val posterKeys = if (type == TmdbMediaType.MOVIE) listOf("movieposter")
                else listOf("tvposter")
            val logoKeys = if (type == TmdbMediaType.MOVIE) listOf("hdmovielogo", "movielogo")
                else listOf("hdtvlogo", "clearlogo")
            val posters = posterKeys.flatMap { json.images(it) }
            val textlessPoster = posters.filter { image ->
                image.language in setOf("", "0", "00", "null")
            }.maxByOrNull { image -> image.likes }
            val poster = (textlessPoster ?: posters.filter { it.language == "en" }
                .maxByOrNull { it.likes })?.url
            val logo = logoKeys.asSequence().flatMap { json.images(it).asSequence() }
                .filter { image -> image.language == "en" }
                .maxByOrNull { image -> image.likes }?.url
            FanartLookup(
                if (poster != null && logo != null) FanartArtwork(poster, logo) else null,
                true, poster != null, textlessPoster != null, logo != null,
            )
        }
    }

    private suspend fun execute(request: Request): Response {
        val call = http.newCall(request)
        val response = suspendCancellableCoroutine<Response> { continuation ->
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, error: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(error)
                }

                override fun onResponse(call: Call, response: Response) {
                    if (continuation.isActive) continuation.resume(response) else response.close()
                }
            })
        }
        return response
    }
}

private data class FanartImage(val url: String, val language: String, val likes: Int)

private fun JSONObject.images(key: String): List<FanartImage> {
    val array: JSONArray = optJSONArray(key) ?: return emptyList()
    return (0 until array.length()).mapNotNull { index ->
        val image = array.optJSONObject(index) ?: return@mapNotNull null
        val url = image.optString("url").toHttpUrlOrNull()
            ?.takeIf { it.isHttps && it.host == "assets.fanart.tv" }
            ?.toString() ?: return@mapNotNull null
        FanartImage(url, image.optString("lang", "").trim().lowercase(),
            image.optString("likes").toIntOrNull() ?: 0)
    }
}
