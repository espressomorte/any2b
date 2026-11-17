package ua.any2b.tvplayer.source

/** A source-specific request for the shared player. */
sealed interface PlaybackRequest {
    val streamUrl: String

    /** A file prepared by TorrServer from a selected torrent. */
    data class TorrentFile(
        override val streamUrl: String,
        val topicId: String,
        val fileId: Int,
        val fileName: String?,
    ) : PlaybackRequest

    /** A resolved online stream with the AniTube track and fallback context. */
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
