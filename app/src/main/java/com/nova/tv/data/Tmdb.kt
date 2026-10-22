package com.nova.tv.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.net.URLEncoder

/** Un titre TMDB (tendance, au cinéma…), présent ou non dans le catalogue IPTV. */
data class TmdbItem(
    val id: Int,
    val type: String, // "movie" ou "tv"
    val title: String,
    val originalTitle: String?,
    val year: String?,
    val poster: String?,
    val backdrop: String?,
    val overview: String?,
    val rating: Double?,
)

/**
 * Enrichissement des fiches via The Movie Database (gratuit, clé personnelle).
 * Accepte une clé API v3 ou un jeton d'accès v4 (Bearer).
 */
class Tmdb(val key: String) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val cache = HashMap<String, Details?>()
    private val lock = Any()
    private val bearer = key.length > 40
    private val lang = "fr-FR"

    private suspend fun get(path: String, query: String = ""): JsonObject? {
        val sep = if (query.isEmpty()) "" else "&$query"
        val url = if (bearer) "$API$path?language=$lang$sep" else "$API$path?api_key=$key&language=$lang$sep"
        val headers = if (bearer) mapOf("Authorization" to "Bearer $key", "accept" to "application/json") else emptyMap()
        val body = Http.get(url, headers)
        return withContext(Dispatchers.Default) { json.parseToJsonElement(body).obj() }
    }

    private suspend fun search(type: String, title: String, year: String?): Int? {
        val q = URLEncoder.encode(title, "UTF-8")
        val yearParam = if (type == "movie") "year" else "first_air_date_year"
        if (year != null) {
            get("/search/$type", "query=$q&$yearParam=$year")?.get("results").items()
                .firstOrNull()?.obj()?.i("id")?.let { return it }
        }
        return get("/search/$type", "query=$q")?.get("results").items().firstOrNull()?.obj()?.i("id")
    }

    suspend fun movie(tmdbId: String?, title: String, year: String?): Details? =
        details("movie", tmdbId, title, year)

    suspend fun tv(tmdbId: String?, title: String, year: String?): Details? =
        details("tv", tmdbId, title, year)

    private suspend fun details(type: String, tmdbId: String?, title: String, year: String?): Details? {
        val cacheKey = "$type|${tmdbId ?: ""}|$title|$year"
        synchronized(lock) { if (cache.containsKey(cacheKey)) return cache[cacheKey] }
        val id = tmdbId?.toIntOrNull()?.takeIf { it > 0 } ?: search(type, title, year)
        val result = if (id == null) null else {
            val o = get(
                "/$type/$id",
                "append_to_response=credits,images,videos&include_image_language=fr,en,null"
            )
            o?.let { parse(type, it) }
        }
        synchronized(lock) { cache[cacheKey] = result }
        return result
    }

    private fun parse(type: String, o: JsonObject): Details {
        val logos = o.objOf("images")?.get("logos").items().mapNotNull { it.obj() }
        val logo = (logos.firstOrNull { it.s("iso_639_1") == "fr" } ?: logos.firstOrNull { it.s("iso_639_1") == "en" }
            ?: logos.firstOrNull())?.s("file_path")?.let { "${IMG}w500$it" }
        val credits = o.objOf("credits")
        val cast = credits?.get("cast").items().take(16).mapNotNull { c ->
            val co = c.obj() ?: return@mapNotNull null
            CastMember(
                name = co.s("name") ?: return@mapNotNull null,
                role = co.s("character"),
                photo = co.s("profile_path")?.let { "${IMG}w185$it" },
            )
        }
        val director = if (type == "movie") {
            credits?.get("crew").items().mapNotNull { it.obj() }.firstOrNull { it.s("job") == "Director" }?.s("name")
        } else {
            o["created_by"].items().mapNotNull { it.obj()?.s("name") }.take(2).joinToString(", ").ifBlank { null }
        }
        val videos = o.objOf("videos")?.get("results").items().mapNotNull { it.obj() }
        val trailer = videos.firstOrNull { it.s("site") == "YouTube" && it.s("type") == "Trailer" }?.s("key")
            ?: videos.firstOrNull { it.s("site") == "YouTube" }?.s("key")
        val runtime = if (type == "movie") o.i("runtime") else o["episode_run_time"].items().firstOrNull()
            ?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content?.toIntOrNull() }
        return Details(
            title = o.firstOf("title", "name") ?: "",
            logo = logo,
            plot = o.s("overview"),
            backdrop = o.s("backdrop_path")?.let { "${IMG}w1280$it" },
            poster = o.s("poster_path")?.let { "${IMG}w500$it" },
            year = Titles.year(o.firstOf("release_date", "first_air_date")),
            genres = o["genres"].items().mapNotNull { it.obj()?.s("name") },
            rating = o.d("vote_average")?.takeIf { it > 0 },
            runtimeMin = runtime?.takeIf { it > 0 },
            director = director,
            cast = cast,
            trailer = trailer,
        )
    }

    private fun item(type: String, it: JsonObject): TmdbItem? {
        val id = it.i("id") ?: return null
        return TmdbItem(
            id = id,
            type = type,
            title = it.firstOf("title", "name") ?: return null,
            originalTitle = it.firstOf("original_title", "original_name"),
            year = Titles.year(it.firstOf("release_date", "first_air_date")),
            poster = it.s("poster_path")?.let { p -> "${IMG}w500$p" },
            backdrop = it.s("backdrop_path")?.let { p -> "${IMG}w1280$p" },
            overview = it.s("overview"),
            rating = it.d("vote_average")?.takeIf { r -> r > 0 },
        )
    }

    private suspend fun list(type: String, path: String, query: String = ""): List<TmdbItem> {
        val o = get(path, query) ?: return emptyList()
        return o["results"].items().mapNotNull { it.obj() }.mapNotNull { item(type, it) }
    }

    /** Tendances de la semaine (films ou séries), 2 pages. */
    suspend fun trending(type: String): List<TmdbItem> =
        (list(type, "/trending/$type/week") + list(type, "/trending/$type/week", "page=2")).distinctBy { it.id }

    /** Films actuellement au cinéma en France. */
    suspend fun nowPlaying(): List<TmdbItem> = list("movie", "/movie/now_playing", "region=FR")

    suspend fun detailsById(type: String, id: Int): Details? = details(type, id.toString(), "", null)

    companion object {
        private const val API = "https://api.themoviedb.org/3"
        private const val IMG = "https://image.tmdb.org/t/p/"
    }
}
