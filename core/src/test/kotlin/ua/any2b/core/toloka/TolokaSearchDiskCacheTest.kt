package ua.any2b.core.toloka

import java.nio.file.Files
import kotlin.test.*

class TolokaSearchDiskCacheTest {
    private fun result(query: String, title: String = "Фільм / Movie (2026) WEB-DL Ukr") = TolokaSearch(query,
        listOf(TolokaHit("123", title, "https://toloka.to/t123", "2 GB", 4, null, "author")), false, true)

    @Test fun survivesRestartNormalizesQueriesAndSeparatesAccounts() {
        val root = Files.createTempDirectory("toloka-search-cache").toFile()
        try {
            val first = TolokaSearchDiskCache(root)
            first.put("user", result("  Film   Title "))
            val reopened = TolokaSearchDiskCache(root)
            val loaded = assertNotNull(reopened.get("user", "film title"))
            assertEquals(result("unused").results, loaded.results)
            assertTrue(loaded.cached)
            assertNull(loaded.sessionReused)
            assertNull(reopened.get("other-account", "film title"))
            assertFalse(root.listFiles()!!.single().readText().contains("sessionReused"))
        } finally { root.deleteRecursively() }
    }

    @Test fun expiresAtTenDaysEvenWhenRepeatedlyRead() {
        val root = Files.createTempDirectory("toloka-search-cache").toFile()
        var time = 1_800_000_000_000L
        try {
            val cache = TolokaSearchDiskCache(root, now = { time })
            cache.put("u", result("query"))
            time += 9L * 24 * 60 * 60 * 1000
            assertNotNull(cache.get("u", "query"))
            time += 24L * 60 * 60 * 1000
            assertNull(cache.get("u", "query"))
            assertTrue(root.listFiles()!!.isEmpty())
        } finally { root.deleteRecursively() }
    }

    @Test fun pressureEvictsOldestDespiteReadsAndLeavesHeadroom() {
        val root = Files.createTempDirectory("toloka-search-cache").toFile()
        var time = 1_800_000_000_000L
        try {
            val cache = TolokaSearchDiskCache(root, maxBytes = 4096, now = { time })
            cache.put("u", result("query0", "x".repeat(200)))
            var evicted = false
            for (index in 1..30) {
                assertNotNull(cache.get("u", "query0")) // Reading does not make an old result fresh.
                time++
                cache.put("u", result("query$index", "x".repeat(200)))
                assertNotNull(cache.get("u", "query$index"))
                if (cache.get("u", "query0") == null) {
                    evicted = true
                    assertTrue(root.listFiles()!!.sumOf { it.length() } <= 4096 * 3 / 4)
                    break
                }
            }
            assertTrue(evicted)
        } finally { root.deleteRecursively() }
    }

    @Test fun emptyResultsAreCachedAndCorruptionIsAMiss() {
        val root = Files.createTempDirectory("toloka-search-cache").toFile()
        try {
            val cache = TolokaSearchDiskCache(root)
            cache.put("u", TolokaSearch("empty", emptyList(), false, false))
            assertEquals(emptyList(), assertNotNull(cache.get("u", "empty")).results)
            root.listFiles()!!.single().writeText("{broken")
            assertNull(cache.get("u", "empty"))
            assertTrue(root.listFiles()!!.isEmpty())
        } finally { root.deleteRecursively() }
    }

    @Test fun oversizedResultDoesNotReplaceUsableCacheAndPruneRemovesExpiredEntries() {
        val root = Files.createTempDirectory("toloka-search-cache").toFile()
        var time = 1_800_000_000_000L
        try {
            val cache = TolokaSearchDiskCache(root, maxBytes = 4096, ttlMs = 100, now = { time })
            cache.put("u", result("query"))
            cache.put("u", result("query", "x".repeat(4096)))
            assertEquals(result("query").results, assertNotNull(cache.get("u", "query")).results)
            time += 100
            cache.prune()
            assertTrue(root.listFiles()!!.isEmpty())
        } finally { root.deleteRecursively() }
    }
}
