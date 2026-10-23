package ua.any2b.core.extractor

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

/**
 * Екстрактор для ashdi.vip
 */
class AshdiExtractor(
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build(),
) : VideoExtractor {

    override val name: String = "Ashdi"

    override fun canHandle(url: String): Boolean =
        url.contains("ashdi.vip", ignoreCase = true)

    override suspend fun extract(embedUrl: String): ExtractedStream = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(embedUrl)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .header("Referer", "https://anitube.in.ua/")
            .build()

        val html = http.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw Exception("Ashdi відповів HTTP ${resp.code}")
            resp.body?.string().orEmpty()
        }

        // Шукаємо посилання на m3u8 у виклику Playerjs: file: 'https://ashdi.vip/.../index.m3u8'
        val pattern = Pattern.compile("""file:\s*['"](https?://[^'"]+\.m3u8[^'"]*)['"]""")
        val matcher = pattern.matcher(html)
        if (!matcher.find()) {
            // Запасний варіант: шукаємо будь-який m3u8 у тексті
            val fallbackPattern = Pattern.compile("""['"](https?://[^'"]+ashdi\.vip[^'"]+\.m3u8[^'"]*)['"]""")
            val fallbackMatcher = fallbackPattern.matcher(html)
            if (!fallbackMatcher.find()) {
                throw Exception("Не вдалося знайти посилання на m3u8 у плеєрі Ashdi ($embedUrl)")
            }
            return@withContext ExtractedStream(
                streamUrl = fallbackMatcher.group(1),
                provider = name,
                headers = mapOf("Referer" to "https://ashdi.vip/")
            )
        }

        val streamUrl = matcher.group(1)
        ExtractedStream(
            streamUrl = streamUrl,
            provider = name,
            headers = mapOf("Referer" to "https://ashdi.vip/")
        )
    }
}
