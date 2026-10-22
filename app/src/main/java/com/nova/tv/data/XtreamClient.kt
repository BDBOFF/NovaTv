package com.nova.tv.data

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import java.io.File
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

fun normalizeServer(input: String): String {
    var s = input.trim()
    if (!s.startsWith("http://", true) && !s.startsWith("https://", true)) s = "http://$s"
    s = s.substringBefore("/player_api.php").substringBefore("/get.php").substringBefore("/xmltv.php")
    return s.trimEnd('/')
}

/** Infos récupérées côté Xtream pour un film. */
data class XtVodInfo(
    val plot: String?,
    val genre: String?,
    val cast: String?,
    val director: String?,
    val year: String?,
    val durationSec: Int?,
    val rating: Double?,
    val backdrop: String?,
    val poster: String?,
    val trailer: String?,
    val tmdbId: String?,
)

data class XtSeriesInfo(
    val meta: XtVodInfo,
    val content: SeriesContent,
)

class XtreamClient(val account: Account, cacheRoot: File) {
    val base = normalizeServer(account.server)
    private val user = account.username
    private val pass = account.password
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val dir = File(cacheRoot, "xtream/" + (base + user).hashCode().toUInt().toString(16)).apply { mkdirs() }

    var timezone: String? = null

    private val vodInfoCache = HashMap<Int, XtVodInfo>()
    private val seriesInfoCache = HashMap<Int, XtSeriesInfo>()

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun api(action: String? = null, extra: String = ""): String = buildString {
        append(base).append("/player_api.php?username=").append(enc(user))
        append("&password=").append(enc(pass))
        if (action != null) append("&action=").append(action)
        append(extra)
    }

    private fun parse(body: String): JsonElement {
        val t = body.trim()
        if (t.isEmpty()) return JsonArray(emptyList())
        return json.parseToJsonElement(t)
    }

    private suspend fun fetch(url: String): JsonElement = withContext(Dispatchers.IO) {
        parse(Http.get(url))
    }

    /** Requête avec cache disque : rapide au démarrage, et utilisable hors ligne. */
    private suspend fun cached(name: String, url: String, maxAgeMs: Long, force: Boolean): JsonElement =
        withContext(Dispatchers.IO) {
            val f = File(dir, "$name.json")
            if (!force && f.exists() && System.currentTimeMillis() - f.lastModified() < maxAgeMs) {
                val el = runCatching { parse(f.readText()) }.getOrNull()
                if (el != null) return@withContext el
            }
            try {
                val body = Http.get(url)
                val el = parse(body)
                runCatching { f.writeText(body) }
                el
            } catch (e: Exception) {
                if (f.exists()) parse(f.readText()) else throw e
            }
        }

    fun clearCache() {
        dir.listFiles()?.forEach { it.delete() }
        vodInfoCache.clear()
        seriesInfoCache.clear()
    }

    // ------------------------------------------------------------------ Compte

    suspend fun login(): AccountInfo {
        val el = try {
            fetch(api())
        } catch (e: kotlinx.serialization.SerializationException) {
            throw XtreamException("Le serveur ne répond pas comme un serveur Xtream Codes")
        }
        val o = el.obj() ?: throw XtreamException("Identifiants refusés par le serveur")
        val ui = o.objOf("user_info") ?: throw XtreamException("Identifiants refusés par le serveur")
        if (ui.i("auth") == 0) throw XtreamException("Nom d'utilisateur ou mot de passe incorrect")
        val status = ui.s("status") ?: "Active"
        when (status.lowercase()) {
            "expired" -> throw XtreamException("Votre abonnement a expiré")
            "banned" -> throw XtreamException("Ce compte a été suspendu")
            "disabled" -> throw XtreamException("Ce compte est désactivé")
        }
        timezone = o.objOf("server_info")?.s("timezone")
        return AccountInfo(
            status = status,
            expiresAt = ui.l("exp_date")?.let { it * 1000 },
            maxConnections = ui.i("max_connections"),
            activeConnections = ui.i("active_cons"),
            isTrial = ui.i("is_trial") == 1,
            createdAt = ui.l("created_at")?.let { it * 1000 },
            timezone = timezone,
        )
    }

    // ------------------------------------------------------------------ Catalogue

    private suspend fun categories(action: String, force: Boolean): List<Category> {
        val el = cached(action, api(action), CATALOG_TTL, force)
        return withContext(Dispatchers.Default) {
            el.items().mapNotNull { e ->
                val o = e.obj() ?: return@mapNotNull null
                val id = o.s("category_id") ?: return@mapNotNull null
                Category(id, o.s("category_name") ?: "Sans nom")
            }
        }
    }

    suspend fun liveCategories(force: Boolean) = categories("get_live_categories", force)
    suspend fun vodCategories(force: Boolean) = categories("get_vod_categories", force)
    suspend fun seriesCategories(force: Boolean) = categories("get_series_categories", force)

    suspend fun liveStreams(force: Boolean): List<Channel> {
        val el = cached("get_live_streams", api("get_live_streams"), CATALOG_TTL, force)
        return withContext(Dispatchers.Default) {
            el.items().mapNotNull { e ->
                val o = e.obj() ?: return@mapNotNull null
                val id = o.i("stream_id") ?: return@mapNotNull null
                val name = o.s("name") ?: return@mapNotNull null
                val (display, quality) = Titles.channel(name)
                Channel(
                    id = id,
                    num = o.i("num") ?: 0,
                    name = name,
                    displayName = display,
                    quality = quality,
                    logo = o.s("stream_icon").validUrl(),
                    epgId = o.s("epg_channel_id"),
                    categoryId = o.s("category_id"),
                    archive = (o.i("tv_archive") ?: 0) == 1,
                    archiveDays = o.i("tv_archive_duration") ?: 0,
                )
            }
        }
    }

    suspend fun vodStreams(force: Boolean): List<Movie> {
        val el = cached("get_vod_streams", api("get_vod_streams"), CATALOG_TTL, force)
        return withContext(Dispatchers.Default) {
            el.items().mapNotNull { e ->
                val o = e.obj() ?: return@mapNotNull null
                val id = o.i("stream_id") ?: return@mapNotNull null
                val name = o.s("name") ?: return@mapNotNull null
                Movie(
                    id = id,
                    name = name,
                    title = Titles.clean(name),
                    year = o.s("year")?.takeIf { it.length == 4 } ?: Titles.year(name),
                    poster = o.s("stream_icon").validUrl(),
                    rating = o.d("rating")?.takeIf { it > 0 },
                    added = o.l("added") ?: 0L,
                    categoryId = o.s("category_id"),
                    ext = o.s("container_extension") ?: "mp4",
                    tmdbId = o.firstOf("tmdb", "tmdb_id"),
                )
            }
        }
    }

    suspend fun series(force: Boolean): List<Series> {
        val el = cached("get_series", api("get_series"), CATALOG_TTL, force)
        return withContext(Dispatchers.Default) {
            el.items().mapNotNull { e ->
                val o = e.obj() ?: return@mapNotNull null
                val id = o.i("series_id") ?: return@mapNotNull null
                val name = o.s("name") ?: return@mapNotNull null
                Series(
                    id = id,
                    name = name,
                    title = Titles.clean(name),
                    year = Titles.year(o.firstOf("releaseDate", "release_date", "year") ?: name),
                    cover = o.s("cover").validUrl(),
                    plot = o.s("plot"),
                    genre = o.s("genre"),
                    rating = o.d("rating")?.takeIf { it > 0 },
                    lastModified = o.l("last_modified") ?: 0L,
                    categoryId = o.s("category_id"),
                    backdrop = o.image("backdrop_path"),
                    tmdbId = o.firstOf("tmdb", "tmdb_id"),
                )
            }
        }
    }

    // ------------------------------------------------------------------ Fiches

    private fun parseMeta(info: JsonObject?): XtVodInfo {
        val o = info ?: JsonObject(emptyMap())
        val dur = o.i("duration_secs") ?: o.s("duration")?.let { parseDuration(it) }
        return XtVodInfo(
            plot = o.firstOf("plot", "description"),
            genre = o.s("genre"),
            cast = o.firstOf("cast", "actors"),
            director = o.s("director"),
            year = Titles.year(o.firstOf("releasedate", "releaseDate", "release_date", "year")),
            durationSec = dur,
            rating = o.d("rating")?.takeIf { it > 0 },
            backdrop = o.image("backdrop_path"),
            poster = (o.s("movie_image") ?: o.s("cover_big") ?: o.s("cover")).validUrl(),
            trailer = o.s("youtube_trailer"),
            tmdbId = o.firstOf("tmdb_id", "tmdb"),
        )
    }

    private fun parseDuration(s: String): Int? {
        val parts = s.split(":").mapNotNull { it.trim().toIntOrNull() }
        return when (parts.size) {
            3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
            2 -> parts[0] * 60 + parts[1]
            1 -> parts[0] * 60
            else -> null
        }
    }

    suspend fun vodInfo(id: Int): XtVodInfo {
        vodInfoCache[id]?.let { return it }
        val el = cached("vod_$id", api("get_vod_info", "&vod_id=$id"), DETAIL_TTL, false)
        val info = withContext(Dispatchers.Default) { parseMeta(el.obj()?.objOf("info")) }
        vodInfoCache[id] = info
        return info
    }

    suspend fun seriesInfo(id: Int): XtSeriesInfo {
        seriesInfoCache[id]?.let { return it }
        val el = cached("series_$id", api("get_series_info", "&series_id=$id"), SERIES_TTL, false)
        val result = withContext(Dispatchers.Default) {
            val root = el.obj() ?: JsonObject(emptyMap())
            val meta = parseMeta(root.objOf("info"))
            val bySeason = sortedMapOf<Int, MutableList<Episode>>()
            val epsEl = root["episodes"]
            val groups: List<Pair<String?, JsonElement>> = when (epsEl) {
                is JsonObject -> epsEl.entries.map { it.key to it.value }
                is JsonArray -> epsEl.map { null to it }
                else -> emptyList()
            }
            for ((key, group) in groups) {
                for (e in group.items()) {
                    val o = e.obj() ?: continue
                    val id2 = o.i("id") ?: continue
                    val season = o.i("season") ?: key?.toIntOrNull() ?: 1
                    val inf = o.objOf("info")
                    val ep = Episode(
                        id = id2,
                        season = season,
                        number = o.i("episode_num") ?: 0,
                        title = o.s("title")?.let { Titles.clean(it) } ?: "Épisode ${o.i("episode_num") ?: ""}",
                        ext = o.s("container_extension") ?: "mp4",
                        image = inf?.let { (it.s("movie_image") ?: it.s("cover_big")).validUrl() },
                        plot = inf?.firstOf("plot", "description"),
                        durationSec = inf?.i("duration_secs") ?: inf?.s("duration")?.let { parseDuration(it) },
                    )
                    bySeason.getOrPut(season) { mutableListOf() }.add(ep)
                }
            }
            val map = bySeason.mapValues { (_, v) -> v.sortedBy { it.number }.toList() }
            XtSeriesInfo(meta, SeriesContent(map.keys.toList(), map))
        }
        seriesInfoCache[id] = result
        return result
    }

    // ------------------------------------------------------------------ Guide TV

    private fun decode(s: String?): String? {
        if (s.isNullOrBlank()) return null
        val looksB64 = s.length % 4 == 0 && s.matches(Regex("^[A-Za-z0-9+/]+={0,2}$"))
        if (!looksB64) return s
        return try {
            val out = String(Base64.decode(s, Base64.DEFAULT), Charsets.UTF_8)
            if (out.any { it == '�' || (it.isISOControl() && it != '\n' && it != '\r' && it != '\t') }) s else out.trim()
        } catch (e: Exception) {
            s
        }
    }

    private fun parseEpg(el: JsonElement): List<EpgProgram> {
        val list = el.obj()?.get("epg_listings").items()
        return list.mapNotNull { e ->
            val o = e.obj() ?: return@mapNotNull null
            val start = o.l("start_timestamp")?.times(1000) ?: return@mapNotNull null
            val end = o.l("stop_timestamp")?.times(1000) ?: o.l("end_timestamp")?.times(1000) ?: return@mapNotNull null
            if (end <= start) return@mapNotNull null
            EpgProgram(
                title = decode(o.s("title")) ?: "Programme",
                description = decode(o.s("description")),
                start = start,
                end = end,
            )
        }.distinctBy { it.start }.sortedBy { it.start }
    }

    suspend fun shortEpg(streamId: Int, limit: Int = 12): List<EpgProgram> {
        val el = fetch(api("get_short_epg", "&stream_id=$streamId&limit=$limit"))
        return withContext(Dispatchers.Default) { parseEpg(el) }
    }

    suspend fun fullEpg(streamId: Int): List<EpgProgram> {
        val el = fetch(api("get_simple_data_table", "&stream_id=$streamId"))
        return withContext(Dispatchers.Default) { parseEpg(el) }
    }

    // ------------------------------------------------------------------ URLs de lecture

    fun liveUrl(ch: Channel, format: String) = "$base/live/$user/$pass/${ch.id}.$format"
    fun movieUrl(m: Movie) = "$base/movie/$user/$pass/${m.id}.${m.ext}"
    fun episodeUrl(id: Int, ext: String) = "$base/series/$user/$pass/$id.$ext"

    fun catchupUrl(ch: Channel, p: EpgProgram): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd:HH-mm", Locale.US)
        fmt.timeZone = timezone?.let { TimeZone.getTimeZone(it) } ?: TimeZone.getDefault()
        val minutes = ((p.end - p.start) / 60000L).coerceAtLeast(1)
        return "$base/timeshift/$user/$pass/$minutes/${fmt.format(p.start)}/${ch.id}.ts"
    }

    companion object {
        const val CATALOG_TTL = 6L * 3600 * 1000
        const val DETAIL_TTL = 7L * 24 * 3600 * 1000
        const val SERIES_TTL = 12L * 3600 * 1000
    }
}
