package ua.any2b.tvplayer.source

/** Provider-specific data after a source has prepared a playable stream. */
sealed interface PlaybackHandoff {
    val streamUrl: String

    data class Toloka(
        override val streamUrl: String,
        val topicId: String,
        val fileId: Int,
        val fileName: String?,
    ) : PlaybackHandoff

    data class AniTube(
        override val streamUrl: String,
        val voiceIndex: Int,
        val episodeIndex: Int,
        val episodeNumber: Int,
        val resumeProgress: Float,
        val backupStreams: List<BackupStream> = emptyList(),
    ) : PlaybackHandoff

    data class BackupStream(
        val embedUrl: String,
        val voiceIndex: Int,
        val episodeIndex: Int,
    )
}
