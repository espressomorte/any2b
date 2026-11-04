package ua.any2b.core.anitube

/** Variants from one studio and one voice category share a single choice in the UI. */
data class AnitubeVoiceGroup(val name: String, val category: String, val variants: List<IndexedValue<AnitubeVoice>>) {
    val primaryIndex: Int get() = variants.first().index
}

fun groupedVoices(voices: List<AnitubeVoice>): List<AnitubeVoiceGroup> =
    voices.withIndex().groupBy { voice ->
        voice.value.category.trim().lowercase() to voice.value.name.trim().lowercase()
    }.values.map { variants ->
        AnitubeVoiceGroup(variants.first().value.name.trim().ifBlank { "Невідома студія" },
            variants.first().value.category, variants)
    }.sortedWith(compareBy<AnitubeVoiceGroup> {
        it.category.equals("СУБТИТРИ", ignoreCase = true)
    }.thenBy { it.name.lowercase() })

/** The selected source goes first, followed by backups that contain the same episode number. */
fun sameStudioEpisodeSources(
    voices: List<AnitubeVoice>, voiceIndex: Int, episodeIndex: Int,
): List<Pair<Int, Int>> {
    val episodeNumber = voices.getOrNull(voiceIndex)?.episodes?.getOrNull(episodeIndex)?.episodeNumber
        ?: return emptyList()
    val group = groupedVoices(voices).firstOrNull { entry ->
        entry.variants.any { it.index == voiceIndex }
    } ?: return emptyList()
    return (listOf(voiceIndex) + group.variants.map { it.index }.filterNot { it == voiceIndex })
        .mapNotNull { index ->
            val match = voices[index].episodes.indexOfFirst { it.episodeNumber == episodeNumber }
            match.takeIf { it >= 0 }?.let { index to it }
        }
}
