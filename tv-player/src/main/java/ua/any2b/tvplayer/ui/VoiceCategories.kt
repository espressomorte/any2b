package ua.any2b.tvplayer.ui

import ua.any2b.core.anitube.AnitubeVoice

internal data class VoiceCategoryGroup(
    val key: String,
    val name: String,
    val variants: List<IndexedValue<AnitubeVoice>>,
) {
    val preferredIndex: Int get() = variants.first().index
}

internal fun voiceCategoryGroups(voices: List<AnitubeVoice>): List<VoiceCategoryGroup> {
    val indexed = voices.withIndex()
    val byStudio = compareBy<IndexedValue<AnitubeVoice>> { it.value.name.trim().lowercase() }
        .thenBy { it.index }
    return listOf(
        "dub" to "Озвучка",
        "sub" to "Субтитри",
    ).mapNotNull { (key, name) ->
        val options = indexed.filter {
            it.value.category.equals("СУБТИТРИ", ignoreCase = true) == (key == "sub")
        }.sortedWith(byStudio)
        if (options.isEmpty()) null else VoiceCategoryGroup(key, name, options)
    }
}

internal fun voiceButtonLabel(group: VoiceCategoryGroup, voice: AnitubeVoice): String {
    val studio = voice.name.trim().ifBlank { "Невідома студія" }
    val player = voice.playerName.trim()
        .replace(Regex("^(?:плеєр|player)\\s*[:·-]?\\s*", RegexOption.IGNORE_CASE), "")
        .firstOrNull { it.isLetterOrDigit() }
        ?.uppercaseChar()
    return listOfNotNull(group.name, studio, player?.toString()).joinToString(" ")
}

internal fun defaultVoiceIndex(voices: List<AnitubeVoice>): Int {
    return voiceCategoryGroups(voices).firstOrNull()?.preferredIndex ?: 0
}
