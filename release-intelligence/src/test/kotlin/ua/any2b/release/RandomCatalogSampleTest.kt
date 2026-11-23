package ua.any2b.release

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RandomCatalogSampleTest {
    @Test fun parsesTwentyFrozenRandomAnitubeTitles() {
        val rows = javaClass.getResourceAsStream("/anitube-random-20.tsv")!!.bufferedReader().use { reader ->
            reader.readLines().filter { it.isNotBlank() && !it.startsWith('#') }.map { it.split('\t') }
        }
        assertEquals(20, rows.size)
        assertEquals(20, rows.map { it[0] }.distinct().size)
        val failures = mutableListOf<String>()
        val report = StringBuilder("# Випадкова вибірка AniTube: 20 назв\n\n")
            .append("Джерело: `parseSearchResults.json`; seed `20261123`; вибірка без повторів із каталогу, відсортованого за newsId. Очікування перевірені за вихідними рядками. Це тест назв каталогу, без синтетичних технічних суфіксів; рік та серії з інших полів JSON не додаються.\n\n")
            .append("| ID | Вхід | Отримані назви | Сезон | Рік | Результат |\n| --- | --- | --- | --- | --- | --- |\n")
        rows.forEach { row ->
            val (id, raw, expectedTitle, expectedSeason, expectedYear) = row
            val parsed = KotlinReleaseParser.parse(ReleaseInput(raw, ReleaseSource.ANITUBE))
            val expectedCoverage = expectedSeason.toIntOrNull()?.let { EpisodeCoverage.SeasonPack(it) } ?: EpisodeCoverage.Unknown
            val expectedYears = expectedYear.toIntOrNull()?.let { listOf(ReleaseYears(it)) } ?: emptyList()
            val titles = parsed.titleCandidates.map { it.text }
            val ok = titles == listOf(expectedTitle) && parsed.episodeCoverage == expectedCoverage && parsed.years == expectedYears &&
                parsed.video == VideoMetadata() && parsed.audio == AudioMetadata() && parsed.subtitleLanguages.isEmpty()
            if (!ok) failures += "$id: expected title=$expectedTitle coverage=$expectedCoverage years=$expectedYears; actual=$parsed"
            val season = (parsed.episodeCoverage as? EpisodeCoverage.SeasonPack)?.start?.toString() ?: "—"
            val years = parsed.years.joinToString { if (it.start == it.end) "${it.start}" else "${it.start}–${it.end}" }.ifEmpty { "—" }
            report.append("| $id | ${raw.replace("|", "\\|")} | ${titles.joinToString(" / ").replace("|", "\\|")} | $season | $years | ${if (ok) "PASS" else "FAIL"} |\n")
        }
        report.append("\nРезультат: ${20 - failures.size}/20.\n")
        val output = File("build/reports/random-catalog-sample.md")
        output.parentFile.mkdirs()
        output.writeText(report.toString())
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }
}
