package ua.any2b.core.toloka

import kotlin.test.Test
import kotlin.test.assertEquals

class TolokaTitleAliasesTest {
    @Test
    fun separatesLanguageVariantsAndRemovesReleaseMetadata() {
        val aliases = TolokaTitleAliases.extract(
            "Темний заклинач / Yami no Shoukanshi | I'm Dating a Dark Summoner (2026) [1080p]"
        )
        assertEquals(listOf("Темний заклинач"), aliases["ukrainian"])
        assertEquals(listOf("Yami no Shoukanshi", "I'm Dating a Dark Summoner"), aliases["latin"])
    }

    @Test
    fun separatesCyrillicAndLatinEvenWhenTorrentUsesParentheses() {
        val aliases = TolokaTitleAliases.extract("Темний заклинач (Yami no Shoukanshi) [720p]")
        assertEquals(listOf("Темний заклинач"), aliases["ukrainian"])
        assertEquals(listOf("Yami no Shoukanshi"), aliases["latin"])
    }
}
