package ua.any2b.core.toloka

import kotlin.test.Test
import kotlin.test.assertEquals

class SearchNameVariantsTest {
    @Test
    fun stripsSeasonAndPartSuffixesWithoutRemovingTheBaseTitle() {
        assertEquals(listOf("Атака титанів"), shortenedSearchNames("Атака титанів (сезон 2)"))
        assertEquals(listOf("Attack on Titan"), shortenedSearchNames("Attack on Titan (Season 2)"))
        assertEquals(listOf("Історія"), shortenedSearchNames("Історія (частина 3)"))
        assertEquals(listOf("Story"), shortenedSearchNames("Story (Part III)"))
    }

    @Test
    fun offersCollectionNameBeforeSubtitle() {
        assertEquals(listOf("Зоряні війни"),
            shortenedSearchNames("Зоряні війни: Імперія завдає удару у відповідь"))
        assertEquals(listOf("Star Wars"),
            shortenedSearchNames("Star Wars: The Empire Strikes Back"))
    }

    @Test
    fun leavesUnrelatedParenthesesAndPlainTitlesAlone() {
        assertEquals(emptyList(), shortenedSearchNames("Аніме (OVA)"))
        assertEquals(emptyList(), shortenedSearchNames("Futurama"))
    }
}
