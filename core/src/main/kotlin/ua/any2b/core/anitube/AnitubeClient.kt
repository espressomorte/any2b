package ua.any2b.core.anitube

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

/**
 * Клієнт для взаємодії з anitube.in.ua: пошук та отримання дерева серій/озвучень.
 */
class AnitubeClient(
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build(),
) {
    private val userAgent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    /**
     * Пошук тайтлів на AniTube за ключовим словом (підтримує українські назви та ромаджі).
     */
    suspend fun search(query: String): List<AnitubeSearchResult> = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(query.trim(), "UTF-8")
        val url = "$BASE_URL/anime/?do=search&subaction=search&story=$encoded"

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent)
            .header("Referer", "$BASE_URL/")
            .build()

        val html = http.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) return@withContext emptyList()
            resp.body?.string().orEmpty()
        }

        parseSearchResults(html)
    }

    /**
     * Завантажує сторінку аніме за URL та витягує всі варіанти озвучень і серій.
     */
    suspend fun getVoices(pageUrl: String): List<AnitubeVoice> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(pageUrl)
            .header("User-Agent", userAgent)
            .build()

        val pageHtml = http.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw Exception("Не вдалося завантажити сторінку $pageUrl: HTTP ${resp.code}")
            resp.body?.string().orEmpty()
        }

        // 1. Витягуємо dle_login_hash
        val hashMatcher = Pattern.compile("""dle_login_hash\s*=\s*['"]([^'"]+)['"]""").matcher(pageHtml)
        if (!hashMatcher.find()) throw Exception("dle_login_hash не знайдено на сторінці")
        val userHash = hashMatcher.group(1)

        // 2. Витягуємо news_id (напр. з URL: 6085-monolog... -> 6085)
        val newsIdMatcher = Pattern.compile("""/(\d+)-""").matcher(pageUrl)
        val newsId = if (newsIdMatcher.find()) newsIdMatcher.group(1) else {
            val altMatcher = Pattern.compile("""news_id\s*=\s*['"]?(\d+)""").matcher(pageHtml)
            if (altMatcher.find()) altMatcher.group(1) else throw Exception("news_id не знайдено для $pageUrl")
        }

        // 3. Запит до playlists.php
        val ajaxUrl = "$BASE_URL/engine/ajax/playlists.php?news_id=$newsId&xfield=playlist&user_hash=$userHash"
        val ajaxReq = Request.Builder()
            .url(ajaxUrl)
            .header("User-Agent", userAgent)
            .header("Referer", pageUrl)
            .header("X-Requested-With", "XMLHttpRequest")
            .build()

        val ajaxJson = http.newCall(ajaxReq).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            JSONObject(body)
        }

        if (!ajaxJson.optBoolean("success", false)) {
            return@withContext emptyList()
        }

        val htmlResponse = ajaxJson.optString("response", "")
        parsePlaylistHtml(htmlResponse)
    }

    private fun parseSearchResults(html: String): List<AnitubeSearchResult> {
        val results = mutableListOf<AnitubeSearchResult>()
        // Шукаємо посилання на новини виду <a href="https://anitube.in.ua/6085-...">Назва</a>
        val pattern = Pattern.compile(
            """itemprop="name"[^>]*>\s*<a\s+href="($BASE_URL/(\d+)-[^"]+\.html)"[^>]*>(.*?)</a>""",
            Pattern.DOTALL or Pattern.CASE_INSENSITIVE
        )
        val matcher = pattern.matcher(html)

        val seen = mutableSetOf<Int>()
        while (matcher.find()) {
            val pageUrl = matcher.group(1)
            val newsId = matcher.group(2).toIntOrNull() ?: continue
            val rawTitle = matcher.group(3)
            val cleanTitle = rawTitle.replace(Regex("<[^>]+>"), "").trim()

            if (seen.add(newsId)) {
                results.add(AnitubeSearchResult(newsId = newsId, title = cleanTitle, pageUrl = pageUrl))
            }
        }
        return results
    }

    private fun parsePlaylistHtml(html: String): List<AnitubeVoice> {
        val liPattern = Pattern.compile("""<li([^>]*)>(.*?)</li>""", Pattern.DOTALL)
        val matcher = liPattern.matcher(html)

        val names = mutableMapOf<String, String>()
        val rawItems = mutableListOf<Triple<String, String, String>>() // id, cleanText, fileUrl

        while (matcher.find()) {
            val attrs = matcher.group(1)
            val text = matcher.group(2)
            val cleanText = text.replace(Regex("<[^>]+>"), "").trim()

            val idMatch = Pattern.compile("""data-id="([^"]*)"""").matcher(attrs)
            val fileMatch = Pattern.compile("""data-file="([^"]*)"""").matcher(attrs)

            if (idMatch.find()) {
                val id = idMatch.group(1)
                if (fileMatch.find()) {
                    rawItems.add(Triple(id, cleanText, fileMatch.group(1)))
                } else {
                    names[id] = cleanText
                }
            }
        }

        val voicesMap = mutableMapOf<String, MutableList<AnitubeEpisode>>()
        val voiceCategories = mutableMapOf<String, String>()

        for ((id, title, fileUrl) in rawItems) {
            val parts = id.split("_")
            val catKey = if (parts.size >= 2) "${parts[0]}_${parts[1]}" else parts.firstOrNull().orEmpty()
            val category = names[catKey].orEmpty()

            val studioKey = if (parts.size >= 3) "${parts[0]}_${parts[1]}_${parts[2]}" else catKey
            val studioName = names[studioKey] ?: category.ifBlank { "Оригінал" }

            val playerName = names[id].orEmpty()
            val epNum = Regex("""(\d+)""").find(title)?.groupValues?.get(1)?.toIntOrNull() ?: 1

            voicesMap.getOrPut(studioName) { mutableListOf() }.add(
                AnitubeEpisode(
                    id = id,
                    episodeNumber = epNum,
                    title = title,
                    fileUrl = fileUrl,
                    playerName = playerName
                )
            )
            voiceCategories[studioName] = category
        }

        return voicesMap.map { (name, episodes) ->
            AnitubeVoice(
                category = voiceCategories[name].orEmpty(),
                name = name,
                episodes = episodes
            )
        }
    }

    companion object {
        const val BASE_URL = "https://anitube.in.ua"
    }
}
