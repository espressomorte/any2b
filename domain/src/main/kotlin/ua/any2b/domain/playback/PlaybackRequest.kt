package ua.any2b.domain.playback

/** Typed handoff from a source to the shared player. */
sealed interface PlaybackRequest {
    val streamUrl: String

    /** A video file prepared by a torrent source. */
    data class TorrentFile(
        override val streamUrl: String,
        val topicId: String,
        val fileId: Int,
        val fileName: String?,
    ) : PlaybackRequest

    /** A resolved online stream with provider track and fallback context. */
    data class OnlineStream(
        override val streamUrl: String,
        val voiceIndex: Int,
        val episodeIndex: Int,
        val episodeNumber: Int,
        val resumeProgress: Float,
        val backupStreams: List<BackupStream> = emptyList(),
    ) : PlaybackRequest

    data class BackupStream(
        val embedUrl: String,
        val voiceIndex: Int,
        val episodeIndex: Int,
    )
}
