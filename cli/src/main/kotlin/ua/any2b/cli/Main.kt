package ua.any2b.cli

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.json.JSONArray
import ua.any2b.core.anilist.AniListClient
import ua.any2b.core.anilist.Title
import ua.any2b.core.anitube.AnitubeClient
import ua.any2b.core.anitube.AnitubeSearchResult
import ua.any2b.core.extractor.StreamResolver
import ua.any2b.core.matcher.TitleMatcher
import ua.any2b.core.matcher.SeriesTitleSearch
import ua.any2b.core.tmdb.FribbMappings
import ua.any2b.core.tmdb.TmdbClient
import ua.any2b.core.tmdb.TmdbTitle
import ua.any2b.core.tmdb.TmdbMediaType
import java.net.URI
import java.time.Instant
import java.nio.file.Files
import java.nio.file.Path
import java.nio.charset.StandardCharsets
import java.nio.file.StandardCopyOption
import java.util.Properties
import kotlin.random.Random
import java.util.concurrent.TimeUnit

fun main(args: Array<String>) = runBlocking {
    val command = args.firstOrNull()?.lowercase() ?: "menu"

    when (command) {
        "tmdb-movie" -> {
            val movieId = args.getOrNull(1)?.let {
                it.toIntOrNull() ?: error("TMDB ID фільму має бути числом")
            } ?: 157336 // Interstellar
            require(movieId > 0) { "TMDB ID фільму має бути додатним числом" }
            val language = args.getOrNull(2) ?: "uk-UA"
            require(language.matches(Regex("[a-z]{2}-[A-Z]{2}"))) { "Мова має бути у форматі uk-UA" }
            printTmdbMovie(movieId, language)
        }

        "tmdb-tv" -> {
            val seriesId = args.getOrNull(1)?.let {
                it.toIntOrNull() ?: error("TMDB ID серіалу має бути числом")
            } ?: 1429 // Attack on Titan
            require(seriesId > 0) { "TMDB ID серіалу має бути додатним числом" }
            val language = args.getOrNull(2) ?: "uk-UA"
            require(language.matches(Regex("[a-z]{2}-[A-Z]{2}"))) { "Мова має бути у форматі uk-UA" }
            printTmdbTv(seriesId, language)
        }

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

        "crawl-anitube" -> {
            val output = args.getOrNull(1)?.takeIf(String::isNotBlank)
                ?: "../parseSearchResults.json"
            val delayMs = args.getOrNull(2)?.toLongOrNull() ?: 1500L
            require(delayMs >= 0) { "Затримка має бути невід'ємною" }
            val file = Path.of(output).toAbsolutePath()
            val results = try {
                val crawl = crawlCatalog(delayMs)
                verifyCatalogSample(crawl, delayMs)
                crawl.results
            } catch (error: Exception) {
                throw IllegalStateException("Обхід AniTube не завершено; файл не оновлено: ${error.message}", error)
            }
            writeCatalog(file, results)
            println("Збережено ${results.size} тайтлів у $file")
        }

        "update-anitube" -> {
            val output = args.getOrNull(1)?.takeIf(String::isNotBlank)
                ?: "../parseSearchResults.json"
            val delayMs = args.getOrNull(2)?.toLongOrNull() ?: 1500L
            require(delayMs >= 0) { "Затримка має бути невід'ємною" }
            val file = Path.of(output).toAbsolutePath()
            val existing = readCatalog(file)
            val results = try {
                updateCatalog(existing, delayMs)
            } catch (error: Exception) {
                throw IllegalStateException("Оновлення AniTube не завершено; файл не оновлено: ${error.message}", error)
            }
            writeCatalog(file, results)
            println("Оновлено каталог: ${results.size} тайтлів у $file")
        }

        "audit-tmdb-anilist" -> {
            val input = Path.of(args.getOrNull(1)?.takeIf(String::isNotBlank) ?: "../parseSearchResults.json")
            val sampleSize = args.getOrNull(2)?.toIntOrNull() ?: 10
            val seed = args.getOrNull(3)?.toLongOrNull() ?: 20261103L
            require(sampleSize in 1..100) { "Розмір вибірки має бути від 1 до 100" }
            auditTmdbAniList(input, sampleSize, seed)
        }

        "map-anitube" -> {
            val input = Path.of(args.getOrNull(1)?.takeIf(String::isNotBlank) ?: "../parseSearchResults.json")
            val output = Path.of(args.getOrNull(2)?.takeIf(String::isNotBlank) ?: "../anilistTmbdbAnitube.json")
            val limit = args.getOrNull(3)?.toIntOrNull() ?: 0
            val seed = args.getOrNull(4)?.toLongOrNull() ?: 20261103L
            require(limit >= 0) { "Ліміт має бути нульовим (весь каталог) або додатним" }
            val catalog = readCatalog(input)
            require(catalog.isNotEmpty()) { "Каталог не знайдений або порожній: ${input.toAbsolutePath()}" }
            val sample = if (limit == 0 || limit >= catalog.size) catalog else
                catalog.shuffled(Random(seed)).take(limit)
            val token = System.getenv("TMDB_TOKEN")?.trim()?.takeIf(String::isNotBlank)
                ?: localTmdbToken()
                ?: error("Вкажіть tmdb.token у local.properties або TMDB_TOKEN у середовищі")
            val outputFile = output.toAbsolutePath()
            val priorItems = if (limit == 0) readMappingItems(outputFile) else emptyList()
            val json = AniTubeMappingGenerator(token).generate(
                sample, sourceCount = catalog.size, sampleSeed = seed.takeIf { sample.size < catalog.size },
                existingItems = priorItems,
                onCheckpoint = { checkpoint -> writeJsonAtomically(outputFile, checkpoint.toString(2) + "\n") },
            )
            writeJsonAtomically(outputFile, json.toString(2) + "\n")
            println("Збережено ${json.getJSONArray("items").length()} записів у $outputFile")
        }

        "reanalyze-anitube" -> {
            val catalogFile = Path.of(args.getOrNull(1)?.takeIf(String::isNotBlank) ?: "parseSearchResults.json").toAbsolutePath()
            val reviewedFile = Path.of(args.getOrNull(2)?.takeIf(String::isNotBlank)
                ?: "anilistTmbdbAnitube-reviewed.json").toAbsolutePath()
            val outputFile = Path.of(args.getOrNull(3)?.takeIf(String::isNotBlank)
                ?: "anilistTmbdbAnitube-reworked.json").toAbsolutePath()
            require(outputFile != reviewedFile && outputFile != catalogFile) {
                "Файл результату має відрізнятися від вхідного каталогу та експорту перевірки"
            }
            val catalog = readCatalog(catalogFile).associateBy { it.newsId }
            require(catalog.isNotEmpty()) { "Каталог не знайдений або порожній: $catalogFile" }
            val reviewedJson = JSONObject(Files.readString(reviewedFile, StandardCharsets.UTF_8))
            val reviewedRows = reviewedJson.optJSONArray("items")
                ?: error("У файлі перевірки немає масиву items: $reviewedFile")
            val originalItems = (0 until reviewedRows.length()).mapNotNull { reviewedRows.optJSONObject(it) }
            val rowsToReanalyze = originalItems.filter { row ->
                val review = row.optJSONObject("userReview")
                review?.optString("comment")?.isNotBlank() == true ||
                    confirmedSnapshots(review).isNotEmpty()
            }
            require(rowsToReanalyze.isNotEmpty()) {
                "Не знайдено записів із коментарями або ручним підтвердженням у $reviewedFile"
            }
            val sourcesToReanalyze = rowsToReanalyze.mapNotNull { row -> catalog[row.optInt("newsId")] }
            require(sourcesToReanalyze.isNotEmpty()) {
                "У вихідному каталозі немає записів для коментарів із $reviewedFile"
            }
            val reviewHints = rowsToReanalyze.associate { row ->
                val review = row.getJSONObject("userReview")
                val comment = review.optString("comment")
                val confirmedLinks = confirmedSnapshots(review).mapNotNull { it.optInt("anilistId").takeIf { id -> id > 0 } }
                    .distinct().joinToString(" ") { "https://anilist.co/anime/$it/" }
                val hint = comment.takeIf(String::isNotBlank)
                    ?: confirmedLinks
                row.optInt("newsId") to hint
            }
            val token = System.getenv("TMDB_TOKEN")?.trim()?.takeIf(String::isNotBlank)
                ?: localTmdbToken()
                ?: error("Вкажіть tmdb.token у local.properties або TMDB_TOKEN у середовищі")
            val originalById = originalItems.associateBy { it.optInt("newsId") }
            val reanalyzedIds = sourcesToReanalyze.mapTo(mutableSetOf()) { it.newsId }
            println("Повторно аналізую ${sourcesToReanalyze.size} записів із коментарями або ручним підтвердженням")
            val generator = AniTubeMappingGenerator(token)
            val json = generator.generate(
                sourcesToReanalyze,
                sourceCount = sourcesToReanalyze.size,
                sampleSeed = null,
                reviewHints = reviewHints,
                onCheckpoint = { checkpoint ->
                    writeReanalysisOutput(outputFile, reviewedJson, originalItems, originalById,
                        reanalyzedIds, checkpoint)
                },
            )
            writeReanalysisOutput(outputFile, reviewedJson, originalItems, originalById,
                reanalyzedIds, json)
            println("Збережено результат у $outputFile")
        }

        "fuzzy-anitube" -> {
            val script = listOf(Path.of("scripts/fuzzy_tmdb.py"), Path.of("cli/scripts/fuzzy_tmdb.py"))
                .firstOrNull(Files::isRegularFile)?.toAbsolutePath()
                ?: error("Не знайдений Python скрипт cli/scripts/fuzzy_tmdb.py")
            val repoRoot = script.parent.parent.parent
            val input = Path.of(args.getOrNull(1)?.takeIf(String::isNotBlank)
                ?: repoRoot.resolve("anilistTmbdbAnitube-reworked.json").toString()).toAbsolutePath()
            val output = Path.of(args.getOrNull(2)?.takeIf(String::isNotBlank)
                ?: repoRoot.resolve("anilistTmbdbAnitube-fuzzy.json").toString()).toAbsolutePath()
            val statuses = args.getOrNull(3)?.takeIf(String::isNotBlank) ?: "not_found,needs_review"
            val limit = args.getOrNull(4)?.toIntOrNull() ?: 100
            val minScore = args.getOrNull(5)?.takeIf(String::isNotBlank) ?: "42"
            val python = args.getOrNull(6)?.takeIf(String::isNotBlank) ?: "python3"
            require(limit >= 0) { "Ліміт має бути 0 (усі записи) або додатним" }
            val token = System.getenv("TMDB_TOKEN")?.trim()?.takeIf(String::isNotBlank)
                ?: localTmdbToken()
                ?: error("Вкажіть tmdb.token у local.properties або TMDB_TOKEN у середовищі")
            val openAiKey = System.getenv("OPENAI_API_KEY")?.trim()?.takeIf(String::isNotBlank)
                ?: localOpenAiKey()
            val cache = repoRoot.resolve("total-matcher/data/fuzzy-tmdb-cache.json")
            val translationCache = repoRoot.resolve("total-matcher/data/fuzzy-title-cache.json")
            val process = ProcessBuilder(
                python, script.toString(),
                "--input", input.toString(), "--output", output.toString(),
                "--statuses", statuses, "--limit", limit.toString(),
                "--min-score", minScore, "--cache", cache.toString(),
                "--translation-cache", translationCache.toString(),
            ).inheritIO().apply {
                environment()["TMDB_TOKEN"] = token
                openAiKey?.let { environment()["OPENAI_API_KEY"] = it }
            }.start()
            val exitCode = process.waitFor()
            check(exitCode == 0) { "Fuzzy TMDB скрипт завершився з кодом $exitCode" }
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
                  crawl-anitube [json] [delayMs] - обійти каталог AniTube і зберегти JSON
                  update-anitube [json] [delayMs] - додати новини з початку каталогу
                  audit-tmdb-anilist [json] [n] [seed] - перевірити назви AniTube через TMDB і Fribb/AniList
                  map-anitube [catalog.json] [output.json] [limit] [seed] - зіставити каталог через TMDB, AniBridge і AniList
                  match <romaji>       - тест зіставлення назв (TitleMatcher)
                  flow [query]         - перевірка ланцюжка для ВСІХ студій через StreamResolver
                  resolve <embedUrl>   - витягнути HLS з конкретного URL плеєра (Ashdi/Fenix)
                  probe <streamUrl>    - HLS probe перевірка стріму
                  tmdb-movie [id] [uk-UA] - дані фільму TMDB (за замовчуванням Interstellar)
                  tmdb-tv [id] [uk-UA] - дані серіалу TMDB (за замовчуванням Attack on Titan)
                ═════════════════════════════════════════════════════════
            """.trimIndent())
        }
    }
}

private data class CatalogCrawl(val results: List<AnitubeSearchResult>, val lastPage: Int)

private suspend fun crawlCatalog(delayMs: Long = 1500): CatalogCrawl {
    val all = LinkedHashMap<Int, AnitubeSearchResult>()
    val client = AnitubeClient()
    var page = 1
    while (true) {
        val items = client.listingPage(page)
        val fresh = items.filter { it.newsId !in all }
        if (items.isEmpty() || fresh.isEmpty()) {
            println("Кінець каталогу на сторінці $page: ${if (items.isEmpty()) "404 або порожня сторінка" else "усі ID вже зібрані"}.")
            break
        }
        fresh.forEach { all[it.newsId] = it }
        println("Сторінка $page: +${fresh.size}, разом ${all.size}")
        page++
        delay(delayMs)
    }
    return CatalogCrawl(all.values.toList(), page - 1)
}

private suspend fun updateCatalog(
    known: List<AnitubeSearchResult>,
    delayMs: Long,
): List<AnitubeSearchResult> {
    val all = LinkedHashMap<Int, AnitubeSearchResult>().apply { known.forEach { put(it.newsId, it) } }
    val client = AnitubeClient()
    var page = 1
    while (true) {
        val items = client.listingPage(page)
        if (items.isEmpty()) {
            println("Кінець каталогу на сторінці $page: 404 або порожня сторінка.")
            break
        }
        val fresh = items.filter { it.newsId !in all }
        if (fresh.isEmpty()) {
            println("Оновлення завершено на сторінці $page: усі ID вже відомі.")
            break
        }
        fresh.forEach { all[it.newsId] = it }
        println("Сторінка $page: +${fresh.size}, разом ${all.size}")
        page++
        delay(delayMs)
    }
    return all.values.toList()
}

private suspend fun verifyCatalogSample(
    crawl: CatalogCrawl,
    delayMs: Long,
) {
    val results = crawl.results
    if (results.isEmpty()) error("каталог порожній")
    if (crawl.lastPage <= 1) return
    val pages = (2..crawl.lastPage).shuffled().take(3)
    val knownIds = results.mapTo(mutableSetOf()) { it.newsId }
    val client = AnitubeClient()
    for ((index, page) in pages.withIndex()) {
        val missed = client.listingPage(page).filter { it.newsId !in knownIds }
        if (missed.isNotEmpty()) {
            error("вибіркова перевірка сторінки $page знайшла ${missed.size} нових ID; повтори повний обхід")
        }
        println("Перевірка сторінки $page: усі ID зібрані")
        if (index < pages.lastIndex) delay(delayMs)
    }
}

private fun writeCatalog(file: Path, results: List<AnitubeSearchResult>) {
    val json = JSONArray().apply {
        results.forEach { result ->
            put(JSONObject().apply {
                put("newsId", result.newsId)
                put("title", result.title)
                put("pageUrl", result.pageUrl)
                put("posterUrl", result.posterUrl ?: JSONObject.NULL)
                put("year", result.year ?: JSONObject.NULL)
                put("airedEpisodes", result.airedEpisodes ?: JSONObject.NULL)
                put("totalEpisodes", result.totalEpisodes ?: JSONObject.NULL)
                put("durationMinutes", result.durationMinutes ?: JSONObject.NULL)
            })
        }
    }
    file.parent?.let(Files::createDirectories)
    val temp = Files.createTempFile(file.parent, "anitube-catalog-", ".json.tmp")
    try {
        Files.writeString(temp, json.toString(2) + "\n", StandardCharsets.UTF_8)
        try {
            Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING)
        }
    } finally {
        Files.deleteIfExists(temp)
    }
}

private fun writeJsonAtomically(file: Path, json: String) {
    file.parent?.let(Files::createDirectories)
    val temp = Files.createTempFile(file.parent, "anitube-mapping-", ".json.tmp")
    try {
        Files.writeString(temp, json, StandardCharsets.UTF_8)
        try {
            Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING)
        }
    } finally {
        Files.deleteIfExists(temp)
    }
}

private fun readCatalog(file: Path): List<AnitubeSearchResult> {
    if (!Files.exists(file)) return emptyList()
    val json = JSONArray(Files.readString(file, StandardCharsets.UTF_8))
    return (0 until json.length()).mapNotNull { index ->
        val item = json.optJSONObject(index) ?: return@mapNotNull null
        val newsId = item.optInt("newsId").takeIf { it > 0 } ?: return@mapNotNull null
        AnitubeSearchResult(
            newsId = newsId,
            title = item.optString("title"),
            pageUrl = item.optString("pageUrl"),
            posterUrl = item.optString("posterUrl").takeIf { it.isNotBlank() && it != "null" },
            year = item.optInt("year").takeIf { it > 0 },
            airedEpisodes = item.optInt("airedEpisodes").takeIf { it > 0 },
            totalEpisodes = item.optInt("totalEpisodes").takeIf { it > 0 },
            durationMinutes = item.optInt("durationMinutes").takeIf { it > 0 },
        )
    }
}

private fun readMappingItems(file: Path): List<JSONObject> {
    if (!Files.isRegularFile(file)) return emptyList()
    return try {
        val rows = JSONObject(Files.readString(file, StandardCharsets.UTF_8)).optJSONArray("items")
            ?: return emptyList()
        (0 until rows.length()).mapNotNull { rows.optJSONObject(it) }
    } catch (_: Exception) {
        emptyList()
    }
}

private fun confirmedSnapshots(review: JSONObject?): List<JSONObject> {
    if (review == null) return emptyList()
    val multiple = review.optJSONArray("confirmedCandidates")
    val values = if (multiple != null) {
        (0 until multiple.length()).mapNotNull { multiple.optJSONObject(it) }
    } else emptyList()
    val single = review.optJSONObject("confirmedCandidate")
    return (values + listOfNotNull(single)).distinctBy { candidate ->
        "${candidate.optInt("anilistId")}:${candidate.optInt("tmdbId")}:${candidate.optInt("index")}"
    }
}

private fun writeReanalysisOutput(
    outputFile: Path,
    reviewedJson: JSONObject,
    originalItems: List<JSONObject>,
    originalById: Map<Int, JSONObject>,
    reanalyzedIds: Set<Int>,
    generated: JSONObject,
) {
    val generatedRows = generated.optJSONArray("items") ?: JSONArray()
    val generatedById = (0 until generatedRows.length()).mapNotNull { index ->
        generatedRows.optJSONObject(index)?.let { it.optInt("newsId") to it }
    }.toMap()
    val merged = originalItems.map { original ->
        val id = original.optInt("newsId")
        val replacement = if (id in reanalyzedIds) generatedById[id] else null
        if (replacement == null) original else JSONObject(replacement.toString()).apply {
            val review = original.optJSONObject("userReview")
            val reviewCopy = review?.let { JSONObject(it.toString()) }
            val confirmed = confirmedSnapshots(review)
            if (confirmed.isNotEmpty()) {
                val oldCandidates = original.optJSONArray("candidates") ?: JSONArray()
                val sourceCandidates = (0 until oldCandidates.length()).mapNotNull { oldCandidates.optJSONObject(it) }
                val refreshedCandidates = optJSONArray("candidates") ?: JSONArray()
                val chosenCandidates = confirmed.mapNotNull { snapshot ->
                    val confirmedId = snapshot.optInt("anilistId").takeIf { it > 0 }
                    val confirmedTmdb = snapshot.optInt("tmdbId").takeIf { it > 0 }
                    val previous = sourceCandidates.firstOrNull { candidate ->
                        sameCandidate(candidate, confirmedId, confirmedTmdb)
                    } ?: oldCandidates.optJSONObject(snapshot.optInt("index"))
                    if (confirmedId != null && (previous == null || previous.optInt("tmdbId") <= 0)) {
                        (0 until refreshedCandidates.length()).mapNotNull { refreshedCandidates.optJSONObject(it) }
                            .firstOrNull { it.optInt("anilistId") == confirmedId && it.optInt("tmdbId") > 0 }
                            ?: previous
                    } else previous
                }.distinctBy { candidate ->
                    "${candidate.optInt("anilistId")}:${candidate.optInt("tmdbId")}:${candidate.optInt("tmdbSeason")}"
                }
                if (chosenCandidates.isNotEmpty()) {
                    val chosen = chosenCandidates.map { JSONObject(it.toString()).put("manualConfirmation", true) }
                    val generatedCandidates = optJSONArray("candidates") ?: JSONArray()
                    val ordered = JSONArray()
                    chosen.forEach(ordered::put)
                    for (index in 0 until generatedCandidates.length()) {
                        val candidate = generatedCandidates.optJSONObject(index) ?: continue
                        val selected = chosenCandidates.any { confirmedCandidate ->
                            sameCandidate(candidate,
                                confirmedCandidate.optInt("anilistId").takeIf { it > 0 },
                                confirmedCandidate.optInt("tmdbId").takeIf { it > 0 })
                        }
                        if (!selected) ordered.put(candidate)
                    }
                    put("candidates", ordered)
                    put("status", "matched")
                    put("reason", "Підтверджено користувачем; ручне підтвердження")
                    put("manualConfirmation", true)
                    val manualRefs = JSONArray()
                    chosen.forEachIndexed { index, candidate ->
                        manualRefs.put(JSONObject().apply {
                            put("index", index)
                            put("anilistId", candidate.optInt("anilistId").takeIf { it > 0 } ?: JSONObject.NULL)
                            put("tmdbId", candidate.optInt("tmdbId").takeIf { it > 0 } ?: JSONObject.NULL)
                            put("tmdbType", candidate.optString("tmdbType").takeIf(String::isNotBlank) ?: JSONObject.NULL)
                            put("tmdbSeason", candidate.optInt("tmdbSeason").takeIf { !candidate.isNull("tmdbSeason") } ?: JSONObject.NULL)
                            put("anilistTitle", candidate.optString("anilistTitle").takeIf(String::isNotBlank) ?: JSONObject.NULL)
                            put("tmdbTitle", candidate.optString("tmdbTitle").takeIf(String::isNotBlank) ?: JSONObject.NULL)
                        })
                    }
                    put("manualConfirmationCandidates", manualRefs)
                    if (manualRefs.length() == 1) put("manualConfirmationCandidate", manualRefs.getJSONObject(0))
                    else remove("manualConfirmationCandidate")
                    reviewCopy?.put("confirmedCandidates", manualRefs)
                    if (manualRefs.length() == 1) reviewCopy?.put("confirmedCandidate", manualRefs.getJSONObject(0))
                    else reviewCopy?.remove("confirmedCandidate")
                }
            }
            val sourceCandidates = original.optJSONArray("candidates") ?: JSONArray()
            val targetCandidates = optJSONArray("candidates") ?: JSONArray()
            val oldRejected = review?.optJSONArray("rejectedCandidateIndexes") ?: JSONArray()
            val remappedRejected = JSONArray()
            for (targetIndex in 0 until targetCandidates.length()) {
                val candidate = targetCandidates.optJSONObject(targetIndex) ?: continue
                val wasRejected = (0 until oldRejected.length()).any { rejectedIndex ->
                    val oldCandidate = sourceCandidates.optJSONObject(oldRejected.optInt(rejectedIndex)) ?: return@any false
                    sameCandidate(candidate,
                        oldCandidate.optInt("anilistId").takeIf { it > 0 },
                        oldCandidate.optInt("tmdbId").takeIf { it > 0 })
                }
                if (wasRejected) remappedRejected.put(targetIndex)
            }
            reviewCopy?.put("rejectedCandidateIndexes", remappedRejected)
            reviewCopy?.let { put("userReview", it) }
        }
    }
    val statusCounts = merged.groupingBy { it.optString("status", "unknown") }.eachCount()
    val metadata = JSONObject(reviewedJson.optJSONObject("\$meta")?.toString() ?: "{}").apply {
        put("processedCount", merged.size)
        put("statusCounts", JSONObject(statusCounts))
        put("reanalysisCount", generatedById.size)
        put("reanalysisGeneratedAt", Instant.now().toString())
        put("reanalysisSource", "userReview.comment+confirmedCandidate")
    }
    val output = JSONObject().put("\$meta", metadata).put("items", JSONArray(merged))
    writeJsonAtomically(outputFile, output.toString(2) + "\n")
}

private fun sameCandidate(candidate: JSONObject, anilistId: Int?, tmdbId: Int?): Boolean {
    if (anilistId == null && tmdbId == null) return false
    if (anilistId != null && candidate.optInt("anilistId").takeIf { it > 0 } != anilistId) return false
    if (tmdbId != null && candidate.optInt("tmdbId").takeIf { it > 0 } != tmdbId) return false
    return true
}

private data class RankedAniListCandidate(
    val anime: ua.any2b.core.anilist.AniListAnimeLookup,
    val titleSimilarity: Double,
    val score: Double,
)

private suspend fun auditTmdbAniList(input: Path, sampleSize: Int, seed: Long) {
    val catalog = readCatalog(input)
    require(catalog.isNotEmpty()) { "Каталог не знайдений або порожній: ${input.toAbsolutePath()}" }
    val token = System.getenv("TMDB_TOKEN")?.trim()?.takeIf(String::isNotBlank)
        ?: localTmdbToken()
        ?: error("Вкажіть tmdb.token у local.properties або TMDB_TOKEN у середовищі")
    val fribbUrl = "https://raw.githubusercontent.com/Fribb/anime-lists/master/anime-list-mini.json"
    val fribbJson = OkHttpClient.Builder().callTimeout(30, TimeUnit.SECONDS).build()
        .newCall(Request.Builder().url(fribbUrl).build()).execute().use { response ->
            if (!response.isSuccessful) error("Fribb відповів HTTP ${response.code}")
            response.body?.string() ?: error("Fribb повернув порожню відповідь")
        }
    val index = FribbMappings.parseIndex(fribbJson)
    val sample = catalog.shuffled(kotlin.random.Random(seed)).take(sampleSize)
    val aniList = AniListClient()
    val tmdb = TmdbClient()
    var anilistClear = 0
    var complete = 0
    sample.forEachIndexed { i, item ->
        val query = cleanAniTubeTitle(item.title)
        val candidates = aniList.searchAnimeLookups(query, 20)
            .map { anime -> rankAniList(item, anime) }
            .filter { it.titleSimilarity >= 0.30 }
            .sortedByDescending { it.score }
        val best = candidates.firstOrNull()
        val second = candidates.getOrNull(1)
        val uniqueAniList = best != null && best.titleSimilarity >= 0.45 &&
            (second == null || best.score - second.score >= 1.5) &&
            withinOrUnknown(item.year, best.anime.seasonYear, 1) &&
            withinOrUnknown(item.totalEpisodes, best.anime.episodes, 1)
        println("${i + 1}. AniTube #${item.newsId}: ${item.title} (${item.year ?: "рік?"}, ${item.totalEpisodes ?: "серії?"} еп.)")
        if (candidates.isEmpty()) {
            println("   AniList: кандидатів за назвою немає")
            return@forEachIndexed
        }
        candidates.take(3).forEach { candidate ->
            val a = candidate.anime
            println("   AniList #${a.id}: ${a.title.display} / ${a.title.romaji ?: "—"}; " +
                "${a.seasonYear ?: "рік?"}, ${a.episodes ?: "серії?"} еп.; " +
                "назва=${"%.2f".format(candidate.titleSimilarity)}, score=${"%.1f".format(candidate.score)}")
        }
        if (!uniqueAniList) {
            println("   Крок 1: неоднозначно")
            return@forEachIndexed
        }
        anilistClear++
        val anime = best!!.anime
        val directMap = FribbMappings.preferred(index.byAniList[anime.id].orEmpty(), anime.format)
        val directExternal = anime.externalLinks.firstNotNullOfOrNull(::tmdbExternalLink)
        println("   AniList TMDB externalLink: ${directExternal?.let { "${it.first.path} #${it.second}" } ?: "немає"}")
        val mapped = directMap ?: directExternal?.let { ua.any2b.core.tmdb.FribbTmdbId(it.first, it.second, null) }
        if (mapped != null) {
            val season = mapped.season?.takeIf { mapped.type == TmdbMediaType.TV }
                ?: if (mapped.type == TmdbMediaType.MOVIE) 0 else null
            println("   TMDB: ${mapped.type.path} #${mapped.id}${season?.let { ", сезон $it" } ?: ""} " +
                "(${if (directMap != null) "Fribb" else "AniList externalLink"})")
            if (season != null || mapped.type == TmdbMediaType.MOVIE) complete++
        } else {
            val fallback = resolveTmdbByAniListName(anime, item, tmdb, token)
            if (fallback == null) {
                println("   Крок 2/3: TMDB не підтверджено")
            } else {
                println("   TMDB: ${fallback.first.type.path} #${fallback.first.id}" +
                    "${fallback.second?.let { ", сезон $it" } ?: ""} (пошук назви AniList)")
                if (fallback.second != null || fallback.first.type == TmdbMediaType.MOVIE) complete++
            }
        }
    }
    println("\nAniList однозначно: $anilistClear/$sampleSize; повний TMDB+Season: $complete/$sampleSize; seed=$seed")
}

private fun cleanAniTubeTitle(value: String): String = value
    .replace(Regex("(?i)\\s*[\\[(][^\\])]*(?:сезон|season|сез|ова|ova|фільм|movie|film)[^\\])]*[\\])]"), " ")
    .let(SeriesTitleSearch::clean).trim()

private fun rankAniList(item: AnitubeSearchResult,
    anime: ua.any2b.core.anilist.AniListAnimeLookup): RankedAniListCandidate {
    val names = listOf(anime.title.romaji, anime.title.english, anime.title.native) + anime.synonyms
    val similarity = names.filterNotNull().maxOfOrNull { stringSimilarity(item.title, it) } ?: 0.0
    var score = similarity * 4.0
    item.year?.let { sourceYear -> anime.seasonYear?.let { actualYear ->
        score += when (kotlin.math.abs(sourceYear - actualYear)) { 0 -> 3.0; 1 -> 1.0; else -> -3.0 }
    } }
    item.totalEpisodes?.let { sourceEpisodes -> anime.episodes?.let { actualEpisodes ->
        score += when (kotlin.math.abs(sourceEpisodes - actualEpisodes)) { 0 -> 2.0; 1 -> 1.0; in 2..3 -> 0.0; else -> -2.0 }
    } }
    val expectsMovie = Regex("(?i)\\b(?:фільм|movie|film)\\b").containsMatchIn(item.title)
    if (expectsMovie && anime.format == "MOVIE") score += 2.0
    if (!expectsMovie && anime.format in setOf("TV", "TV_SHORT")) score += 0.5
    return RankedAniListCandidate(anime, similarity, score)
}

private fun stringSimilarity(first: String, second: String): Double {
    fun normalize(value: String) = value.lowercase()
        .replace(Regex("\\([^)]*\\)|\\[[^]]*]"), " ")
        .replace(Regex("[^a-z0-9а-яіїєґ ]"), " ")
        .replace(Regex("\\s+"), " ").trim()
    val a = normalize(first)
    val b = normalize(second)
    if (a.isBlank() || b.isBlank()) return 0.0
    if (a == b) return 1.0
    if (a in b || b in a) return 0.85
    val at = a.split(' ').filter(String::isNotBlank).toSet()
    val bt = b.split(' ').filter(String::isNotBlank).toSet()
    return at.intersect(bt).size.toDouble() / at.union(bt).size
}

private fun withinOrUnknown(first: Int?, second: Int?, tolerance: Int): Boolean =
    first == null || second == null || kotlin.math.abs(first - second) <= tolerance

private fun tmdbExternalLink(link: ua.any2b.core.anilist.AniListExternalLink): Pair<TmdbMediaType, Int>? {
    val match = Regex("(?i)themoviedb\\.org/(tv|movie)/(\\d+)").find(link.url) ?: return null
    val type = if (match.groupValues[1].equals("tv", true)) TmdbMediaType.TV else TmdbMediaType.MOVIE
    return type to match.groupValues[2].toInt()
}

private suspend fun resolveTmdbByAniListName(
    anime: ua.any2b.core.anilist.AniListAnimeLookup,
    source: AnitubeSearchResult,
    tmdb: TmdbClient,
    token: String,
): Pair<TmdbTitle, Int?>? {
    val names = listOfNotNull(anime.title.english, anime.title.romaji).distinct()
    if (names.isEmpty()) return null
    val expectedType = if (anime.format == "MOVIE") TmdbMediaType.MOVIE else TmdbMediaType.TV
    val candidates = names.flatMap { tmdb.search(it, token) }
        .filter { it.type == expectedType }
        .distinctBy { it.id }
        .map { it to maxOf(stringSimilarity(anime.title.english.orEmpty(), it.title),
            stringSimilarity(anime.title.romaji.orEmpty(), it.title),
            stringSimilarity(anime.title.english.orEmpty(), it.originalTitle.orEmpty()),
            stringSimilarity(anime.title.romaji.orEmpty(), it.originalTitle.orEmpty())) }
        .sortedByDescending { it.second }
    val top = candidates.firstOrNull()?.takeIf { it.second >= 0.65 } ?: return null
    if (candidates.getOrNull(1)?.let { top.second - it.second < 0.15 } == true) return null
    if (expectedType == TmdbMediaType.MOVIE) return top.first to 0
    val details = tmdb.details(top.first.type, top.first.id, token)
    val sourceYear = source.year
    val sourceEpisodes = source.totalEpisodes
    val seasonHits = details.seasonEntries.filter { season ->
        val seasonYear = season.year
        val seasonEpisodes = season.episodeCount
        season.number > 0 && (sourceYear == null || seasonYear == null || kotlin.math.abs(sourceYear - seasonYear) <= 1) &&
            (sourceEpisodes == null || seasonEpisodes == null || kotlin.math.abs(sourceEpisodes - seasonEpisodes) <= 1)
    }
    val season = seasonHits.singleOrNull()?.number
    return top.first to season
}

private fun printTmdbMovie(movieId: Int, language: String) {
    val movie = fetchTmdbJson("movie/$movieId", language)
    val genres = movie.optJSONArray("genres")?.let { array ->
        (0 until array.length()).mapNotNull { array.optJSONObject(it)?.optString("name") }
            .filter(String::isNotBlank).joinToString(", ")
    }.orEmpty()
    println("═══ TMDB: фільм #${movie.optInt("id")} ═══")
    println("Назва: ${movie.optString("title")}")
    println("Оригінальна назва: ${movie.optString("original_title")}")
    println("Дата виходу: ${movie.optString("release_date").ifBlank { "—" }}")
    println("Тривалість: ${movie.optInt("runtime").takeIf { it > 0 }?.let { "$it хв" } ?: "—"}")
    println("Жанри: ${genres.ifBlank { "—" }}")
    println("Оцінка: ${movie.optDouble("vote_average").takeIf { !it.isNaN() } ?: "—"}")
    println("Опис: ${movie.optString("overview").ifBlank { "—" }}")
}

private fun printTmdbTv(seriesId: Int, language: String) {
    val series = fetchTmdbJson("tv/$seriesId", language)
    val genres = series.optJSONArray("genres")?.let { array ->
        (0 until array.length()).mapNotNull { array.optJSONObject(it)?.optString("name") }
            .filter(String::isNotBlank).joinToString(", ")
    }.orEmpty()
    println("═══ TMDB: серіал #${series.optInt("id")} ═══")
    println("Назва: ${series.optString("name")}")
    println("Оригінальна назва: ${series.optString("original_name")}")
    println("Перша трансляція: ${series.optString("first_air_date").ifBlank { "—" }}")
    println("Сезонів: ${series.optInt("number_of_seasons")}")
    println("Серій: ${series.optInt("number_of_episodes")}")
    println("Жанри: ${genres.ifBlank { "—" }}")
    println("Оцінка: ${series.optDouble("vote_average").takeIf { !it.isNaN() } ?: "—"}")
    println("Опис: ${series.optString("overview").ifBlank { "—" }}")
}

private fun fetchTmdbJson(path: String, language: String): JSONObject {
    val token = System.getenv("TMDB_TOKEN")?.trim()?.takeIf(String::isNotBlank)
        ?: localTmdbToken()
        ?: error("Вкажіть tmdb.token у локальному local.properties або TMDB_TOKEN у середовищі")
    val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .build()
    val request = Request.Builder()
        .url("https://api.themoviedb.org/3/$path?language=$language")
        .header("Authorization", "Bearer $token")
        .header("Accept", "application/json")
        .build()
    return http.newCall(request).execute().use { response ->
        if (!response.isSuccessful) error("TMDB відповів HTTP ${response.code}")
        JSONObject(response.body?.string() ?: error("TMDB повернув порожню відповідь"))
    }
}

private fun localTmdbToken(): String? {
    val file = listOf(Path.of("local.properties"), Path.of("..", "local.properties"))
        .firstOrNull { Files.isRegularFile(it) } ?: return null
    val properties = Properties()
    Files.newInputStream(file).use(properties::load)
    return properties.getProperty("tmdb.token")?.trim()?.takeIf(String::isNotBlank)
}

private fun localOpenAiKey(): String? {
    val file = listOf(Path.of("local.properties"), Path.of("..", "local.properties"))
        .firstOrNull { Files.isRegularFile(it) } ?: return null
    val properties = Properties()
    Files.newInputStream(file).use(properties::load)
    return listOf("openai.apiKey", "openai.api_key", "openai.token", "openai.key")
        .firstNotNullOfOrNull { key -> properties.getProperty(key)?.trim()?.takeIf(String::isNotBlank) }
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
