package ua.any2b.core.fenix

/** Доріжка плеєра Fenix (аудіо або субтитри). */
data class FenixTrack(
    val label: String,
    val language: String,
    val trackType: String,      // "dub", "original", "sub", etc.
    val hlsUrl: String,         // підписаний URL дочірнього плейлиста
)

/** Повний результат розбору плеєра FenixPlay. */
data class FenixStream(
    val finalMasterUrl: String,
    val tracks: List<FenixTrack>,
    val edgeOrigin: String,
    val edgeToken: String,
)
