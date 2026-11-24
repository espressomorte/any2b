package ua.any2b.core.toloka

import java.io.IOException
import java.net.SocketTimeoutException
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.Credentials
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup

/** Local Toloka backend. Cookies and personal download links remain in process memory. */
class DirectTolokaClient(
    private val origin: HttpUrl = "https://toloka.to/".toHttpUrl(),
    http: OkHttpClient? = null,
) {
    private val lock = Mutex()
    private val cookies = mutableListOf<Cookie>()
    private val http = http ?: OkHttpClient.Builder()
        .cookieJar(object : CookieJar {
            override fun saveFromResponse(url: HttpUrl, received: List<Cookie>) {
                synchronized(cookies) {
                    received.forEach { cookie ->
                        cookies.removeAll { it.name == cookie.name && it.domain == cookie.domain }
                        if (cookie.expiresAt > System.currentTimeMillis()) cookies.add(cookie)
                    }
                }
            }
            override fun loadForRequest(url: HttpUrl): List<Cookie> = synchronized(cookies) {
                cookies.removeAll { it.expiresAt <= System.currentTimeMillis() }
                cookies.filter { it.matches(url) }
            }
        })
        .connectTimeout(10, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS).followRedirects(false).build()
    private var sessionUntil = 0L
    private var cooldownUntil = 0L
    private var lastTrackerSearchAt = 0L
    private val downloads = mutableMapOf<String, Pair<HttpUrl, Long>>()
    private val searches = mutableMapOf<String, Pair<TolokaSearch, Long>>()

    fun remainingCooldownSeconds(): Long =
        ((cooldownUntil - System.currentTimeMillis()).coerceAtLeast(0) + 999) / 1000

    suspend fun check(username: String, password: String): Boolean = lock.withLock {
        ensureSession(username, password)
        true
    }

    /** RSS catalog request, using the existing session when local credentials are configured. */
    suspend fun rss(username: String? = null, password: String? = null): String = lock.withLock {
        if (!username.isNullOrBlank() && !password.isNullOrBlank())
            ensureSession(username, password)
        val url = origin.newBuilder().addPathSegment("rss.php")
            .addQueryParameter("t", "1").addQueryParameter("c", "50")
            .addQueryParameter("lite", "1")
            .addQueryParameter("cat", "8").addQueryParameter("toronly", "1")
            .addQueryParameter("thumbs", "1").build()
        val authorization = if (!username.isNullOrBlank() && !password.isNullOrBlank())
            Credentials.basic(username, password) else null
        request(url, authorization = authorization).use { response ->
            if (!response.isSuccessful) throw TolokaFailure.Http(response.code)
            val xml = response.body?.string().orEmpty()
            if (!xml.contains("<rss", ignoreCase = true) &&
                !xml.contains("<feed", ignoreCase = true))
                throw TolokaFailure.InvalidResponse()
            xml
        }
    }

    suspend fun search(username: String, password: String, query: String, forceRefresh: Boolean = false): TolokaSearch = lock.withLock {
        searches.entries.removeAll { it.value.second <= System.currentTimeMillis() }
        val trimmed = query.trim()
        require(trimmed.isNotEmpty() && trimmed.length <= 150) { "Запит має містити 1–150 символів" }
        val cacheKey = username + "\u0000" + trimmed.lowercase().replace(Regex("\\s+"), " ")
        searches[cacheKey]?.takeIf { !forceRefresh && it.second > System.currentTimeMillis() }?.let {
            return@withLock it.first.copy(query = trimmed, cached = true, sessionReused = null)
        }
        val reused = ensureSession(username, password)
        val waitMs = (lastTrackerSearchAt + MIN_TRACKER_SEARCH_INTERVAL_MS -
            System.currentTimeMillis()).coerceAtLeast(0L)
        if (waitMs > 0L) delay(waitMs)
        val url = origin.newBuilder().addPathSegment("tracker.php")
            .addQueryParameter("nm", trimmed).addQueryParameter("o", "1")
            .addQueryParameter("s", "2").build()
        lastTrackerSearchAt = System.currentTimeMillis()
        val html = page(request(url))
        if (!isAuthenticated(html)) {
            sessionUntil = 0
            throw TolokaFailure.InvalidResponse()
        }
        val parsed = TolokaHtmlSearchParser.parse(html, origin)
        val now = System.currentTimeMillis()
        parsed.downloads.forEach { (id, path) -> downloads[id] = path to (now + 3_600_000) }
        val result = TolokaSearch(trimmed, parsed.hits, cached = false, sessionReused = reused)
        searches[cacheKey] = result to (now + 300_000)
        while (searches.size > 128) searches.remove(searches.keys.first())
        result
    }

    suspend fun torrent(username: String, password: String, topicId: String): ByteArray = lock.withLock {
        require(topicId.matches(Regex("[0-9]{1,12}"))) { "Некоректний ID роздачі" }
        val link = downloads[topicId]?.takeIf { it.second > System.currentTimeMillis() }
            ?.first ?: throw TolokaFailure.LinkExpired()
        ensureSession(username, password)
        request(link).use { response ->
            if (!response.isSuccessful) throw TolokaFailure.Http(response.code)
            val contentType = response.header("Content-Type").orEmpty().lowercase()
            if (contentType.contains("text/html") || contentType.contains("json"))
                throw TolokaFailure.InvalidResponse()
            val input = response.body?.byteStream() ?: throw TolokaFailure.InvalidResponse()
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            input.use {
                while (true) {
                    val count = it.read(buffer)
                    if (count < 0) break
                    output.write(buffer, 0, count)
                    if (output.size() > 2 * 1024 * 1024) throw TolokaFailure.InvalidResponse()
                }
            }
            output.toByteArray().also { bytes ->
                if (!TorrentStructure.valid(bytes))
                    throw TolokaFailure.InvalidResponse()
            }
        }
    }

    private suspend fun ensureSession(username: String, password: String): Boolean {
        if (remainingCooldownSeconds() > 0) throw TolokaFailure.RateLimited(remainingCooldownSeconds())
        require(username.isNotBlank() && password.isNotBlank()) { "Вкажіть дані Toloka" }
        if (sessionUntil > System.currentTimeMillis()) return true
        synchronized(cookies) { cookies.clear() }
        page(request(origin.newBuilder().addPathSegment("login.php").build()))
        val form = FormBody.Builder().add("username", username).add("password", password)
            .add("autologin", "on").add("ssl", "on").add("redirect", "")
            .add("login", "Вхід").build()
        val login = page(request(origin.newBuilder().addPathSegment("login.php").build(), form))
        val confirmed = isAuthenticated(login) ||
            isAuthenticated(page(request(origin.newBuilder().addPathSegment("tracker.php").build())))
        if (!confirmed) throw TolokaFailure.Unauthorized()
        sessionUntil = System.currentTimeMillis() + 1_800_000
        return false
    }

    private fun isAuthenticated(html: String) = html.contains("logout=true", ignoreCase = true)

    private fun page(response: Response): String = response.use {
        val html = it.body?.string().orEmpty()
        if (!it.isSuccessful || html.contains("cf-chl-") ||
            html.contains("challenge-platform") || html.contains("just a moment", ignoreCase = true))
            throw TolokaFailure.InvalidResponse()
        html
    }

    private suspend fun request(initial: HttpUrl, form: FormBody? = null,
        authorization: String? = null): Response {
        if (remainingCooldownSeconds() > 0) throw TolokaFailure.RateLimited(remainingCooldownSeconds())
        var url = initial
        var body = form
        repeat(6) { redirect ->
            if (url.host != origin.host || url.scheme != origin.scheme)
                throw TolokaFailure.InvalidResponse()
            val builder = Request.Builder().url(url).header("User-Agent", "Animarium-Toloka/0.2")
                .header("Referer", origin.newBuilder().addPathSegment("login.php").build().toString())
            if (authorization != null) builder.header("Authorization", authorization)
            if (body != null) builder.post(body!!) else builder.get()
            val call = http.newCall(builder.build())
            val response = try { suspendCancellableCoroutine<Response> { continuation ->
                continuation.invokeOnCancellation { call.cancel() }
                call.enqueue(object : Callback {
                    override fun onFailure(call: Call, error: IOException) {
                        if (continuation.isActive) continuation.resumeWithException(error)
                    }
                    override fun onResponse(call: Call, response: Response) {
                        if (continuation.isActive) continuation.resume(response) else response.close()
                    }
                })
            } } catch (_: SocketTimeoutException) { throw TolokaFailure.Timeout() }
            catch (_: IOException) { throw TolokaFailure.Network() }
            if (response.code == 429) {
                val retryAfter = response.header("Retry-After")
                val suggested = retryAfter?.toLongOrNull() ?: retryAfter?.let { value ->
                    try {
                        val deadline = ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME)
                        (deadline.toInstant().toEpochMilli() - System.currentTimeMillis() + 999) / 1000
                    } catch (_: Exception) { null }
                }
                val seconds = (suggested ?: 900L).coerceIn(60L, 86_400L)
                cooldownUntil = System.currentTimeMillis() + seconds * 1000
                response.close()
                throw TolokaFailure.RateLimited(seconds)
            }
            val location = response.header("Location")
            if (response.code !in listOf(301, 302, 303, 307, 308) || location == null)
                return response
            response.close()
            if (redirect == 5) throw TolokaFailure.InvalidResponse()
            url = url.resolve(location) ?: throw TolokaFailure.InvalidResponse()
            if (body != null && response.code in listOf(301, 302, 303)) body = null
        }
        throw TolokaFailure.InvalidResponse()
    }

    private companion object {
        /** Shared across manual and background searches for this process-wide client. */
        const val MIN_TRACKER_SEARCH_INTERVAL_MS = 2_000L
    }
}

data class ParsedTolokaPage(val hits: List<TolokaHit>, val downloads: Map<String, HttpUrl>)

object TolokaHtmlSearchParser {
    fun parse(html: String, origin: HttpUrl = "https://toloka.to/".toHttpUrl()): ParsedTolokaPage {
        val hits = mutableListOf<TolokaHit>()
        val downloads = mutableMapOf<String, HttpUrl>()
        Jsoup.parse(html, origin.toString()).select("table.forumline tr[class*=prow]").forEach { row ->
            val link = row.selectFirst("td:nth-child(3) > a[href]") ?: return@forEach
            val pageUrl = origin.resolve(link.attr("href")) ?: return@forEach
            if (pageUrl.host != origin.host || pageUrl.scheme != origin.scheme) return@forEach
            val id = Regex("^/t([0-9]+)(?:-|$)").find(pageUrl.encodedPath)?.groupValues?.get(1)
                ?: pageUrl.queryParameter("t")?.takeIf { it.matches(Regex("[0-9]+")) }
                ?: return@forEach
            val title = link.text().trim().takeIf(String::isNotEmpty) ?: return@forEach
            if (hits.any { it.id == id }) return@forEach
            val download = row.selectFirst("td:nth-child(6) > a[href]")
                ?.let { origin.resolve(it.attr("href")) }
                ?.takeIf { it.host == origin.host && it.scheme == origin.scheme }
            if (download != null) downloads[id] = download
            fun count(cell: Int): Int? = row.selectFirst("td:nth-child($cell) > b")
                ?.text()?.replace(Regex("[\\s\\u00a0]"), "")?.toIntOrNull()
            val author = row.select("a[href]").firstOrNull { element ->
                val url = origin.resolve(element.attr("href"))
                url?.host == origin.host && url.encodedPath == "/tracker.php" &&
                    url.queryParameter("pid")?.matches(Regex("[0-9]+")) == true
            }?.text()?.trim()?.takeIf(String::isNotBlank)
            hits.add(TolokaHit(id, title, pageUrl.toString(),
                row.selectFirst("td:nth-child(7)")?.text().orEmpty().trim(),
                count(10), count(11), author))
        }
        return ParsedTolokaPage(hits, downloads)
    }
}
