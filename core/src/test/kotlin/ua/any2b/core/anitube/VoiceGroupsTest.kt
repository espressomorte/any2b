package ua.any2b.core.anitube

import kotlin.test.Test
import kotlin.test.assertEquals

class VoiceGroupsTest {
    private fun voice(name: String, player: String, vararg numbers: Int) = AnitubeVoice(
        category = "ОЗВУЧЕННЯ", name = name, playerName = player,
        episodes = numbers.map { AnitubeEpisode("$player-$it", it, "$it серія", "https://example.com/$player/$it.m3u8", player) },
    )

    @Test
    fun `groups variants by studio and keeps other studios separate`() {
        val voices = listOf(voice("AniUa", "A", 1), voice("AniUa", "F", 1),
            voice("AniUa", "B", 1), voice("FanVoxUA", "A", 1))
        val groups = groupedVoices(voices)
        assertEquals(2, groups.size)
        assertEquals(listOf(0, 1, 2), groups.first { it.name == "AniUa" }.variants.map { it.index })
    }

    @Test
    fun `backup choices keep selected source first and match episode number`() {
        val voices = listOf(voice("AniUa", "A", 1, 2), voice("AniUa", "F", 2),
            voice("AniUa", "B", 1), voice("FanVoxUA", "A", 2))
        assertEquals(listOf(0 to 1, 1 to 0), sameStudioEpisodeSources(voices, 0, 1))
        assertEquals(listOf(1 to 0, 0 to 1), sameStudioEpisodeSources(voices, 1, 0))
    }
}
