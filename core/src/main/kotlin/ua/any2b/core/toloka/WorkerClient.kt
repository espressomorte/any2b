package ua.any2b.core.toloka

import java.io.IOException
import java.net.SocketTimeoutException
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject

data class WorkerStatus(val kvConfigured: Boolean, val sessionStored: Boolean, val cooldownSeconds: Long)

data class TolokaHit(
    val id: String,
    val title: String,
    val pageUrl: String,
    val sizeText: String,
    val seeders: Int?,
    val leechers: Int?,
)

data class TolokaSearch(
    val query: String,
    val results: List<TolokaHit>,
    val cached: Boolean,
    val sessionReused: Boolean?,
)

sealed class WorkerFailure(message: String) : IOException(message) {
    class Unauthorized : WorkerFailure("Неправильний або відсутній ключ Worker")
    class RateLimited(val waitSeconds: Long) : WorkerFailure("Worker тимчасово обмежив запити")
    class Timeout : WorkerFailure("Час очікування Worker вичерпано")
    class Http(val status: Int) : WorkerFailure("Worker відповів HTTP $status")
    class LinkExpired : WorkerFailure("Посилання на torrent протухло. Повторіть пошук Toloka")
    class InvalidResponse : WorkerFailure("Worker повернув неочікувані дані")
    class Network : WorkerFailure("Не вдалося з’єднатися з Worker")
}

/** All requests use a user supplied HTTPS URL and bearer key; neither is logged. */
class WorkerClient(
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .followRedirects(false)
        .build(),
) {
    @Volatile private var cooldownUntilNanos = 0L

    fun remainingCooldownSeconds(): Long {
        val deadline = cooldownUntilNanos
        if (deadline == 0L) return 0L
        return ((deadline - System.nanoTime()).coerceAtLeast(0L) + 999_999_999L) / 1_000_000_000L
    }

    suspend fun status(baseUrl: String, key: String): WorkerStatus {
        val json = request(baseUrl, key, "status").use { JSONObject(it.body?.string().orEmpty()) }
        return WorkerStatus(
            kvConfigured = json.optBoolean("kvConfigured"),
            sessionStored = json.optBoolean("sessionStored"),
            cooldownSeconds = json.optLong("cooldownSeconds").coerceAtLeast(0),
        )
    }

    suspend fun search(baseUrl: String, key: String, query: String): TolokaSearch {
        require(query.isNotBlank()) { "Введіть пошуковий запит" }
        val json = request(baseUrl, key, "search", "q" to query).use {
            JSONObject(it.body?.string().orEmpty())
        }
        val array = json.optJSONArray("results") ?: throw WorkerFailure.InvalidResponse()
        val hits = (0 until array.length()).map { index ->
            val item = array.optJSONObject(index) ?: throw WorkerFailure.InvalidResponse()
            TolokaHit(
                id = item.optString("id"),
                title = item.optString("title"),
                pageUrl = item.optString("pageUrl"),
                sizeText = item.optString("sizeText"),
                seeders = item.optIntOrNull("seeders"),
                leechers = item.optIntOrNull("leechers"),
            ).also { if (it.id.isBlank() || it.title.isBlank()) throw WorkerFailure.InvalidResponse() }
        }
        return TolokaSearch(
            query = json.optString("query", query),
            results = hits,
            cached = json.optBoolean("cached"),
            sessionReused = if (json.has("sessionReused") && !json.isNull("sessionReused"))
                json.optBoolean("sessionReused") else null,
        )
    }

    /** Downloads binary torrent bytes; never substitutes a Toloka page URL. */
    suspend fun torrent(baseUrl: String, key: String, topicId: String): ByteArray {
        require(topicId.matches(Regex("[0-9]+"))) { "Некоректний ID роздачі" }
        val response = request(baseUrl, key, "torrent", "id" to topicId)
        response.use {
            if (it.header("Content-Type")?.substringBefore(';')?.trim()?.lowercase() != "application/x-bittorrent") {
                throw WorkerFailure.InvalidResponse()
            }
            val bytes = it.body?.byteStream()?.use { stream ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (output.size() <= MAX_TORRENT_BYTES) {
                    val count = stream.read(buffer)
                    if (count < 0) break
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
                ?: throw WorkerFailure.InvalidResponse()
            if (bytes.size > MAX_TORRENT_BYTES || bytes.firstOrNull()?.toInt() != 'd'.code) {
                throw WorkerFailure.InvalidResponse()
            }
            return bytes
        }
    }

    private suspend fun request(baseUrl: String, key: String, path: String, parameter: Pair<String, String>? = null): Response {
        val wait = remainingCooldownSeconds()
        if (wait > 0) throw WorkerFailure.RateLimited(wait)
        require(key.isNotBlank()) { "Вкажіть ключ Worker" }
        val base = baseUrl.trim().trimEnd('/').toHttpUrlOrNull()
            ?: throw IllegalArgumentException("Некоректний URL Worker")
        require(base.isHttps && base.username.isEmpty() && base.password.isEmpty() &&
            base.query == null && base.fragment == null) { "Worker має використовувати HTTPS URL без параметрів" }
        val url = base.newBuilder().addPathSegment(path).apply {
            parameter?.let { addQueryParameter(it.first, it.second) }
        }.build()
        val call = http.newCall(Request.Builder().url(url).header("Authorization", "Bearer $key").build())
        val response = try {
            suspendCancellableCoroutine<Response> { continuation ->
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
        } catch (error: SocketTimeoutException) {
            throw WorkerFailure.Timeout()
        } catch (error: IOException) {
            throw WorkerFailure.Network()
        }
        if (response.isSuccessful) return response
        response.use {
            when (it.code) {
                401 -> throw WorkerFailure.Unauthorized()
                404 -> {
                    val error = try { JSONObject(it.body?.string().orEmpty()).optString("error") }
                    catch (_: Exception) { "" }
                    if (path == "torrent" && error == "run_search_first_or_link_expired") {
                        throw WorkerFailure.LinkExpired()
                    }
                    throw WorkerFailure.Http(404)
                }
                429 -> {
                    val seconds = retrySeconds(it).coerceIn(1L, 86_400L)
                    cooldownUntilNanos = System.nanoTime() + seconds * 1_000_000_000L
                    throw WorkerFailure.RateLimited(seconds)
                }
                else -> throw WorkerFailure.Http(it.code)
            }
        }
    }

    private fun retrySeconds(response: Response): Long {
        val header = response.header("Retry-After")
        header?.toLongOrNull()?.let { return it }
        if (header != null) try {
            return (ZonedDateTime.parse(header, DateTimeFormatter.RFC_1123_DATE_TIME)
                .toInstant().epochSecond - Instant.now().epochSecond).coerceAtLeast(1)
        } catch (_: Exception) { }
        return try { JSONObject(response.body?.string().orEmpty()).optLong("retryAfterSeconds", 60) }
        catch (_: Exception) { 60 }
    }

    private fun JSONObject.optIntOrNull(name: String): Int? =
        if (has(name) && !isNull(name)) optInt(name) else null

    private companion object { const val MAX_TORRENT_BYTES = 20 * 1024 * 1024 }
}
