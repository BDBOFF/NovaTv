package com.nova.tv.data

import kotlinx.serialization.Serializable

@Serializable
data class Account(
    val id: String,
    val name: String,
    val server: String,
    val username: String,
    val password: String,
)

data class AccountInfo(
    val status: String,
    val expiresAt: Long?,
    val maxConnections: Int?,
    val activeConnections: Int?,
    val isTrial: Boolean,
    val createdAt: Long?,
    val timezone: String?,
)

enum class ContentType { LIVE, MOVIE, SERIES }

object Kind {
    const val MOVIE = "movie"
    const val SERIES = "series"
    const val EPISODE = "episode"
    const val LIVE = "live"
    /** Titres connus de TMDB mais absents (pour l'instant) du catalogue. */
    const val TMDB_MOVIE = "tmdb_movie"
    const val TMDB_TV = "tmdb_tv"
}

data class Category(val id: String, val name: String)

data class Channel(
    val id: Int,
    val num: Int,
    val name: String,
    val displayName: String,
    val quality: String?,
    val logo: String?,
    val epgId: String?,
    val categoryId: String?,
    val archive: Boolean,
    val archiveDays: Int,
)

data class Movie(
    val id: Int,
    val name: String,
    val title: String,
    val year: String?,
    val poster: String?,
    val rating: Double?,
    val added: Long,
    val categoryId: String?,
    val ext: String,
    val tmdbId: String?,
)

data class Series(
    val id: Int,
    val name: String,
    val title: String,
    val year: String?,
    val cover: String?,
    val plot: String?,
    val genre: String?,
    val rating: Double?,
    val lastModified: Long,
    val categoryId: String?,
    val backdrop: String?,
    val tmdbId: String?,
)

data class CastMember(val name: String, val role: String?, val photo: String?)

/** Fiche unifiée (film ou série) enrichie par Xtream + TMDB. */
data class Details(
    val title: String,
    val logo: String?,
    val plot: String?,
    val backdrop: String?,
    val poster: String?,
    val year: String?,
    val genres: List<String>,
    val rating: Double?,
    val runtimeMin: Int?,
    val director: String?,
    val cast: List<CastMember>,
    val trailer: String?,
)

data class Episode(
    val id: Int,
    val season: Int,
    val number: Int,
    val title: String,
    val ext: String,
    val image: String?,
    val plot: String?,
    val durationSec: Int?,
)

data class SeriesContent(val seasons: List<Int>, val episodes: Map<Int, List<Episode>>) {
    val all: List<Episode> get() = seasons.flatMap { episodes[it].orEmpty() }
}

data class EpgProgram(val title: String, val description: String?, val start: Long, val end: Long) {
    fun isNow(now: Long) = now in start until end
    fun progress(now: Long): Float =
        if (end <= start) 0f else ((now - start).toFloat() / (end - start)).coerceIn(0f, 1f)
}

@Serializable
data class Fav(
    val accountId: String,
    val kind: String,
    val id: Int,
    val title: String,
    val image: String? = null,
    val addedAt: Long = 0,
    val year: String? = null,
)

@Serializable
data class History(
    val accountId: String,
    val kind: String,
    val id: Int,
    val title: String,
    val subtitle: String = "",
    val image: String? = null,
    val seriesId: Int = 0,
    val ext: String = "",
    val position: Long = 0,
    val duration: Long = 0,
    val updatedAt: Long = 0,
) {
    val progress: Float
        get() = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
}

/** Un élément lisible par le lecteur. */
data class PlayItem(
    val kind: String,
    val id: Int,
    val url: String,
    val title: String,
    val subtitle: String = "",
    val image: String? = null,
    val seriesId: Int = 0,
    val ext: String = "",
    val channel: Channel? = null,
    val catchup: EpgProgram? = null,
)

data class PlaySession(
    val items: List<PlayItem>,
    val index: Int,
    val startPosition: Long = 0,
) {
    val isLive: Boolean get() = items.getOrNull(index)?.kind == Kind.LIVE
}

class XtreamException(message: String) : Exception(message)
