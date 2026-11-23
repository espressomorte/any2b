package ua.any2b.release

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/** Strict regression test against manually reviewed expectations from a frozen real-world sample. */
class TolokaRandomSampleTest {
    @Test fun auditsTwentyRealTolokaReleases() {
        val rows = javaClass.getResourceAsStream("/toloka-random-20.tsv")!!.bufferedReader().use {
            it.readLines().filter { line -> line.isNotBlank() && !line.startsWith('#') }.map { line -> line.split('\t') }
        }
        assertEquals(20, rows.size)
        assertEquals(20, rows.map { it[0] }.distinct().size)
        val failures = linkedMapOf<String, Set<String>>()
        val counts = linkedMapOf<String, Int>()
        fun cell(value: Any?) = value.toString().replace("|", "\\|").replace("\n", " ")
        val report = StringBuilder("# Toloka: аудит 20 випадкових релізів\n\n")
            .append("Джерело: RSS-кеш із підключеного TV (`files/toloka-rss-catalog.json`), 50 записів; seed `20261123`, Python random.sample після сортування за рядковим toloka_id. Оригінальні заголовки та SHA-256 кешу збережені у тестовій TSV-вибірці. Очікування розмічені за заголовками до прогону. Мережевий матч TMDB/AniList не перевіряється.\n\n")
            .append("Перевіряються назви, рік, джерело, роздільність, кодек, мови аудіо/субтитрів та покриття серій. BluRay у поточній моделі охоплює BDRemux. HDR/DV, редакції, кількість аудіодоріжок та Ai Rem поки не мають повної моделі і не входять у числову оцінку. Очікування 08 з 12 → 1–8 є правилом джерела Toloka, а не доведеним списком файлів.\n\n")
            .append("| ID | Вхід | Отримані назви | Помилки полів |\n| --- | --- | --- | --- |\n")
        rows.forEach { row ->
            val parsed = KotlinReleaseParser.parse(ReleaseInput(row[1], ReleaseSource.TOLOKA))
            val differences = mutableListOf<String>()
            val failedFields = linkedSetOf<String>()
            fun check(field: String, expected: Any?, actual: Any?) {
                if (expected != actual) {
                    failedFields += field
                    differences += "$field: очікується $expected; отримано $actual"
                } else counts[field] = counts.getOrDefault(field, 0) + 1
            }
            val coverage = when (val c = parsed.episodeCoverage) {
                EpisodeCoverage.Unknown -> ""
                is EpisodeCoverage.SeasonEpisodes -> "S${c.season}:${c.start}-${c.end}"
                is EpisodeCoverage.SeasonPack -> "S${c.start}"
                is EpisodeCoverage.AbsoluteEpisodes -> "A:${c.start}-${c.end}/${c.total}"
            }
            check("назви", row[2].split(" ~ "), parsed.titleCandidates.map { it.text })
            check("рік", listOf(ReleaseYears(row[3].toInt())), parsed.years)
            check("джерело", row[4], parsed.video.source.orEmpty())
            check("роздільність", row[5], parsed.video.resolution.orEmpty())
            check("кодек", row[6], parsed.video.codec.orEmpty())
            fun codes(value: String) = value.split(',').filter(String::isNotEmpty).toSet()
            check("аудіо", codes(row[7]), parsed.audio.languages)
            check("субтитри", codes(row[8]), parsed.subtitleLanguages)
            check("серії", row[9], coverage)
            if (differences.isNotEmpty()) failures[row[0]] = failedFields
            report.append("| ${row[0]} | ${cell(row[1])} | ${cell(parsed.titleCandidates.joinToString(" / ") { it.text })} | ${cell(if (differences.isEmpty()) "PASS" else differences.joinToString("; "))} |\n")
        }
        report.append("\nПовністю правильні в межах перевірених полів: ${20 - failures.size}/20.\n\n")
        counts.forEach { (field, correct) -> report.append("- $field: $correct/20\n") }
        val output = File("build/reports/toloka-random-20.md")
        output.parentFile.mkdirs()
        output.writeText(report.toString())
        assertEquals(emptyMap(), failures, "Review build/reports/toloka-random-20.md")
    }
}
