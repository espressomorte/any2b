package ua.any2b.cli

import org.json.JSONObject
import java.nio.file.Files
import java.nio.file.Path
import java.nio.charset.StandardCharsets
import kotlin.random.Random

/** File oriented entry points for mapping generation, review rework, and fuzzy enrichment. */
internal object AniTubeMappingCommands {
    suspend fun generate(args: List<String>) {
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

    suspend fun reanalyze(args: List<String>) {
        val catalogFile = Path.of(args.getOrNull(1)?.takeIf(String::isNotBlank)
            ?: "parseSearchResults.json").toAbsolutePath()
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
            val confirmedLinks = confirmedSnapshots(review)
                .mapNotNull { it.optInt("anilistId").takeIf { id -> id > 0 } }
                .distinct().joinToString(" ") { "https://anilist.co/anime/$it/" }
            row.optInt("newsId") to (comment.takeIf(String::isNotBlank) ?: confirmedLinks)
        }
        val token = System.getenv("TMDB_TOKEN")?.trim()?.takeIf(String::isNotBlank)
            ?: localTmdbToken()
            ?: error("Вкажіть tmdb.token у local.properties або TMDB_TOKEN у середовищі")
        val originalById = originalItems.associateBy { it.optInt("newsId") }
        val reanalyzedIds = sourcesToReanalyze.mapTo(mutableSetOf()) { it.newsId }
        println("Повторно аналізую ${sourcesToReanalyze.size} записів із коментарями або ручним підтвердженням")
        val json = AniTubeMappingGenerator(token).generate(
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

    fun fuzzy(args: List<String>) {
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
}
