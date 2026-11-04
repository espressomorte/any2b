package ua.any2b.core.matcher

import kotlin.test.Test
import kotlin.test.assertEquals

class TitleNormalizationTest {
    @Test
    fun `latin cyrillic mode matches historical ranking normalization`() {
        assertEquals("spy x family 2", TitleNormalization.latinCyrillic(
            "SPY × FAMILY (2023) [1080p] 2".replace("×", "x"),
            removeBracketedContent = true,
        ))
        assertEquals("тест 2024", TitleNormalization.latinCyrillic("Тест: 2024"))
    }

    @Test
    fun `unicode mode retains scripts while replacing punctuation`() {
        assertEquals("攻殻 機動隊", TitleNormalization.unicodeWords("攻殻・機動隊"))
        assertEquals("alpha beta", TitleNormalization.unicodeWords("Alpha  beta"))
    }

    @Test
    fun `metadata distance treats missing values as unknown`() {
        assertEquals(1, MetadataDistance.between(2024, 2025))
        kotlin.test.assertTrue(MetadataDistance.withinOrUnknown(null, 12, tolerance = 0))
        kotlin.test.assertTrue(MetadataDistance.withinOrUnknown(12, 13, tolerance = 1))
        kotlin.test.assertFalse(MetadataDistance.withinOrUnknown(12, 14, tolerance = 1))
    }
}
