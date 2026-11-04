package ua.any2b.tvplayer.data

import ua.any2b.core.tmdb.TmdbMediaType

/** Stable typed identity. Plain numeric keys remain the legacy AniList ID representation. */
sealed interface TitleKey {
    val storageKey: String

    data class AniList(val id: Int) : TitleKey {
        init { require(id > 0) }
        override val storageKey: String get() = id.toString()
    }

    data class Tmdb(val type: TmdbMediaType, val id: Int) : TitleKey {
        init { require(id > 0) }
        override val storageKey: String get() = "tmdb:${type.path}:$id"
    }

    companion object {
        /** Reads both the current typed form and historical AniList integer IDs. */
        fun parse(value: String): TitleKey? {
            value.toIntOrNull()?.takeIf { it > 0 }?.let { return AniList(it) }
            val parts = value.split(':')
            if (parts.size != 3 || parts[0] != "tmdb") return null
            val type = TmdbMediaType.entries.firstOrNull { it.path == parts[1] } ?: return null
            val id = parts[2].toIntOrNull()?.takeIf { it > 0 } ?: return null
            return Tmdb(type, id)
        }
    }
}
