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
        val headings = mutableListOf<SearchHeading>()
        while (matcher.find()) {
            val newsId = matcher.group(2).toIntOrNull() ?: continue
            headings.add(SearchHeading(
                start = matcher.start(),
                end = matcher.end(),
                newsId = newsId,
                pageUrl = matcher.group(1),
                title = matcher.group(3).replace(Regex("<[^>]+>"), "").trim(),
            ))
        }

        val seen = mutableSetOf<Int>()
        headings.forEachIndexed { index, heading ->
            if (seen.add(heading.newsId)) {
                val nextStart = headings.getOrNull(index + 1)?.start ?: html.length
                val chunk = html.substring(heading.end, nextStart)
                val episodes = EPS_RE.find(chunk)?.groupValues
                results.add(AnitubeSearchResult(
                    newsId = heading.newsId,
                    title = heading.title,
                    pageUrl = heading.pageUrl,
                    year = YEAR_RE.find(chunk)?.groupValues?.get(1)?.toIntOrNull(),
                    airedEpisodes = episodes?.get(1)?.toIntOrNull(),
                    totalEpisodes = episodes?.get(2)?.toIntOrNull(),
                    durationMinutes = episodes?.get(3)?.toIntOrNull(),
                ))
            }
        }
        return results
    }

    private data class SearchHeading(
        val start: Int,
        val end: Int,
        val newsId: Int,
        val pageUrl: String,
        val title: String,
    )

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

        val voicesMap = linkedMapOf<String, MutableList<AnitubeEpisode>>()
        val voiceCategories = mutableMapOf<String, String>()
        val voiceNames = mutableMapOf<String, String>()

        for ((id, title, fileUrl) in rawItems) {
            val parts = id.split("_")
            val catKey = if (parts.size >= 2) "${parts[0]}_${parts[1]}" else parts.firstOrNull().orEmpty()
            val category = names[catKey].orEmpty()

            val studioKey = if (parts.size >= 3) parts.take(3).joinToString("_") else catKey
            val studioName = names[studioKey] ?: category.ifBlank { "Оригінал" }

            // The fourth data-id segment identifies the player within a studio.
            val playerKey = if (parts.size >= 4) parts.take(4).joinToString("_") else studioKey
            val playerName = names[playerKey].orEmpty()
            val epNum = episodeNumberFromTitle(title) ?: 1

            voicesMap.getOrPut(playerKey) { mutableListOf() }.add(
                AnitubeEpisode(
                    id = id,
                    episodeNumber = epNum,
                    title = title,
                    fileUrl = fileUrl,
                    playerName = playerName
                )
            )
            voiceCategories[playerKey] = category
            voiceNames[playerKey] = studioName
        }

        return voicesMap.map { (key, episodes) ->
            AnitubeVoice(
                category = voiceCategories[key].orEmpty(),
                name = voiceNames[key].orEmpty(),
                playerName = names[key].orEmpty(),
                episodes = episodes
            )
        }
    }

    private fun episodeNumberFromTitle(title: String): Int? {
        val match = Regex("""^\s*(\d{1,3})(?=\b|[.\-])|(?:серія|епізод|episode|ep\.?)\s*#?\s*(\d+)""", RegexOption.IGNORE_CASE)
            .find(title) ?: return null
        return (match.groupValues[1].ifEmpty { match.groupValues[2] }).toIntOrNull()
    }

    companion object {
        const val BASE_URL = "https://anitube.in.ua"
        private val YEAR_RE = Regex("""Рік виходу аніме:\s*</dt>\s*(?:<a[^>]*>)?\s*(\d{4})""", RegexOption.IGNORE_CASE)
        private val EPS_RE = Regex("""Серій:\s*</dt>\s*(\d+)(?:\s*з\s*(\d+|\?))?(?:\s*\((\d+)\s*хв\))?""", RegexOption.IGNORE_CASE)
    }
}
