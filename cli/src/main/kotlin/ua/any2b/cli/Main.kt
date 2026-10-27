package ua.any2b.cli

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import ua.any2b.core.anilist.AniListClient
import ua.any2b.core.anilist.Title
import ua.any2b.core.anitube.AnitubeClient
import ua.any2b.core.extractor.StreamResolver
import ua.any2b.core.matcher.TitleMatcher
import java.net.URI
import java.util.concurrent.TimeUnit

fun main(args: Array<String>) = runBlocking {
    val command = args.firstOrNull()?.lowercase() ?: "menu"

    when (command) {
        "anilist", "home" -> {
            println("═══ AniList: Головна сторінка ═══")
            val client = AniListClient()
            val rows = client.homeRows(perRow = 5)
            for (row in rows) {
                println("\n▶ ${row.title} (${row.items.size} тайтлів):")
                for (item in row.items) {
                    println("  • [#${item.id}] ${item.title.display} | оцінка: ${item.score ?: "—"} | формат: ${item.format ?: "—"}")
                }
            }
        }

        "search" -> {
            val query = args.drop(1).joinToString(" ").takeIf { it.isNotBlank() } ?: "Kusuriya no Hitorigoto"
            println("═══ Пошук на AniTube: '$query' ═══")
            val client = AnitubeClient()
            val results = client.search(query)
            println("Знайдено ${results.size} результатів:")
            results.forEachIndexed { i, res ->
                println("  ${i + 1}. [${res.newsId}] ${res.title} -> ${res.pageUrl}")
            }
        }

        "match" -> {
            val romaji = args.drop(1).joinToString(" ").takeIf { it.isNotBlank() } ?: "Kusuriya no Hitorigoto"
            println("═══ Пошук та співставлення для: '$romaji' ═══")
            val anitube = AnitubeClient()
            val candidates = anitube.search(romaji)
            val matched = TitleMatcher.match(
                title = Title(romaji = romaji, english = null, native = null),
                candidates = candidates
            )
            println("Знайдено кандидатів: ${matched.size}")
            matched.forEach { m ->
                println("  • (${m.score} бала) ${m.result.title} -> ${m.result.pageUrl}")
            }
        }

        "flow" -> {
            val query = args.drop(1).joinToString(" ").takeIf { it.isNotBlank() } ?: "Kusuriya no Hitorigoto"
            println("═══ Повний ланцюжок: AniList -> AniTube -> StreamResolver ═══")

            val anitube = AnitubeClient()
            val resolver = StreamResolver()

            println("1. Шукаємо '$query' на AniTube...")
            val results = anitube.search(query)
            val best = results.firstOrNull() ?: run {
                println("✗ Нічого не знайдено."); return@runBlocking
            }
            println("✓ Обрано: ${best.title} (${best.pageUrl})")

            println("\n2. Отримуємо плейлист серій...")
            val voices = anitube.getVoices(best.pageUrl)
            println("✓ Знайдено озвучень: ${voices.size}")
            voices.forEach { v ->
                val hosts = v.episodes.map { URI(it.fileUrl).host }.distinct()
                println("  • [${v.displayName}] серій: ${v.episodes.size}, хости: $hosts")
            }

            // Перевіряємо резолвінг для різних провайдерів
            println("\n3. Тестування універсального StreamResolver для різних студій:")
            val testedHosts = mutableSetOf<String>()

            for (voice in voices) {
                val ep = voice.episodes.firstOrNull() ?: continue
                val host = URI(ep.fileUrl).host
                if (!resolver.canHandle(ep.fileUrl)) continue
                if (testedHosts.contains(host)) continue

                println("\n── Студія: '${voice.displayName}' | ${ep.title} | $host ──")
                try {
                    val stream = resolver.resolve(ep.fileUrl)
                    println("✓ Провайдер: ${stream.provider}")
                    println("✓ Stream URL: ${stream.streamUrl.take(85)}...")
                    probeHlsStream(stream.streamUrl, stream.headers)
                    testedHosts.add(host)
                } catch (e: Exception) {
                    println("✗ Помилка резолвінгу: ${e.message}")
                }
            }
        }

        "resolve" -> {
            val url = args.getOrNull(1)?.takeIf { it.isNotBlank() }
                ?: error("Вкажіть URL плеєра: ./gradlew :cli:run --args=\"resolve <URL>\"")
            println("═══ Резолвінг плеєра: $url ═══")
            val resolver = StreamResolver()
            val stream = resolver.resolve(url)
            println("✓ Провайдер: ${stream.provider}")
            println("✓ HLS URL: ${stream.streamUrl}")
            println("✓ Заголовки: ${stream.headers}")
            probeHlsStream(stream.streamUrl, stream.headers)
        }

        "probe" -> {
            val url = args.getOrNull(1)?.takeIf { it.isNotBlank() }
                ?: error("Вкажіть stream URL: ./gradlew :cli:run --args=\"probe <URL>\"")
            probeHlsStream(url)
        }

        else -> {
            println("""
                ═════════════════════════════════════════════════════════
                 Any2B CLI — тестування Core бібліотеки (StreamResolver)
                ═════════════════════════════════════════════════════════
                Команди:
                  anilist              - перевірити запит головної AniList
                  search <query>       - пошук тайтлів на AniTube
                  match <romaji>       - тест зіставлення назв (TitleMatcher)
                  flow [query]         - перевірка ланцюжка для ВСІХ студій через StreamResolver
                  resolve <embedUrl>   - витягнути HLS з конкретного URL плеєра (Ashdi/Fenix)
                  probe <streamUrl>    - HLS probe перевірка стріму
                ═════════════════════════════════════════════════════════
            """.trimIndent())
        }
    }
}

private fun probeHlsStream(streamUrl: String, headers: Map<String, String> = emptyMap()) {
    val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    fun fetch(url: String): Pair<Int, String> {
        val reqBuilder = Request.Builder().url(url)
        headers.forEach { (k, v) -> reqBuilder.header(k, v) }
        return try {
            http.newCall(reqBuilder.build()).execute().use { resp ->
                resp.code to (resp.body?.string().orEmpty())
            }
        } catch (e: Exception) {
            -1 to (e.message ?: "error")
        }
    }

    print("   ↳ Master M3U8: ")
    val (code, body) = fetch(streamUrl)
    print("HTTP $code")
    if (code != 200) { println(" ✗"); return }

    val childLine = body.lines().firstOrNull { it.isNotBlank() && !it.startsWith("#") }
    if (childLine != null) {
        val childUrl = if (childLine.startsWith("http")) childLine else URI(streamUrl).resolve(childLine).toString()
        print(" -> Child: ")
        val (cCode, cBody) = fetch(childUrl)
        print("HTTP $cCode")

        val segLine = cBody.lines().firstOrNull { it.isNotBlank() && !it.startsWith("#") }
        if (segLine != null) {
            val segUrl = if (segLine.startsWith("http")) segLine else URI(childUrl).resolve(segLine).toString()
            print(" -> Segment: ")
            val (sCode, _) = fetch(segUrl)
            println("HTTP $sCode ${if (sCode == 200) "✓ [OK]" else "✗ [FAIL]"}")
        } else {
            println(" (сегментів не знайдено)")
        }
    } else {
        println(" ✓")
    }
}
