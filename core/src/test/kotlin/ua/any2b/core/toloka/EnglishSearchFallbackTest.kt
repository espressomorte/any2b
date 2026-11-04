package ua.any2b.core.toloka

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class EnglishSearchFallbackTest {
    @Test
    fun retriesOnceInEnglishOnlyAfterEmptyUkrainianResults() = runBlocking {
        val queries = mutableListOf<String>()
        val result = searchWithEnglishFallback("Українська назва", "English title",
            search = { query -> queries += query; emptyList<String>() },
            hasMatches = { it.isNotEmpty() })
        assertEquals(listOf("Українська назва", "English title"), queries)
        assertEquals("English title", result.first)
    }

    @Test
    fun keepsFirstResultsAndDoesNotSearchAgain() = runBlocking {
        val queries = mutableListOf<String>()
        val result = searchWithEnglishFallback("Українська назва", "English title",
            search = { query -> queries += query; listOf("match") },
            hasMatches = { it.isNotEmpty() })
        assertEquals(listOf("Українська назва"), queries)
        assertEquals("Українська назва", result.first)
    }
}
