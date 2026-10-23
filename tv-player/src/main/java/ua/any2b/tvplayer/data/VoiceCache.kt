package ua.any2b.tvplayer.data

import ua.any2b.core.anitube.AnitubeVoice
import java.util.concurrent.ConcurrentHashMap

object VoiceCache {
    private val cache = ConcurrentHashMap<Int, List<AnitubeVoice>>()

    fun put(animeId: Int, voices: List<AnitubeVoice>) {
        cache[animeId] = voices
    }

    fun get(animeId: Int): List<AnitubeVoice>? {
        return cache[animeId]
    }
}
