package ua.any2b.core.toloka

import kotlin.test.Test
import kotlin.test.assertEquals

class TolokaSearchPlanTest {
    @Test
    fun animeRomajiPrecedesEnglishAndJapaneseTmdbTitle() {
        val primary = TolokaSearchPlan.primary("Yami no Shoukanshi",
            listOf("Темний заклинач"), "Українська назва",
            "I'm Dating a Dark Summoner", "闇の召喚士")
        assertEquals("Yami no Shoukanshi", primary)
        assertEquals(listOf("Yami no Shoukanshi", "Темний заклинач", "Українська назва",
            "I'm Dating a Dark Summoner"),
            listOf(primary) + TolokaSearchPlan.fallbacks(primary, "Yami no Shoukanshi",
                listOf("Темний заклинач"), "Українська назва", "I'm Dating a Dark Summoner"))
    }
}
