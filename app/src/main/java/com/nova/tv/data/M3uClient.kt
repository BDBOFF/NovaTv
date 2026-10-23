package com.nova.tv.data

import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import java.io.BufferedInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.zip.GZIPInputStream

/**
 * Source "playlist M3U" (M3U / M3U8 / m3u_plus), avec guide TV XMLTV facultatif.
 * Le contenu est trié automatiquement en chaînes, films et séries.
 */
class M3uClient(override val account: Account, cacheRoot: File) : Source {
    override var timezone: String? = null

    private val dir = File(cacheRoot, "m3u/" + account.server.hashCode().toUInt().toString(16)).apply { mkdirs() }
    private val playlistFile = File(dir, "playlist.m3u")
    private val epgFile = File(dir, "guide.xml")

    private var parsed: Parsed? = null
    private val parseLock = Mutex()
    private val epgLock = Mutex()
    private var epgLoaded = false
    private val programs = HashMap<String, List<EpgProgram>>()

    private class Parsed(
        val liveCats: List<Category>,
        val vodCats: List<Category>,
        val seriesCats: List<Category>,
        val channels: List<Channel>,
        val movies: List<Movie>,
        val series: List<Series>,
        val urls: Map<Int, String>,
        val episodes: Map<Int, SeriesContent>,
        val epgUrl: String?,
    )

    // ------------------------------------------------------------------ Téléchargement

    private suspend fun download(url: String, to: File, maxAgeMs: Long, force: Boolean) = withContext(Dispatchers.IO) {
        if (!force && to.exists() && to.length() > 0 && System.currentTimeMillis() - to.lastModified() < maxAgeMs) return@withContext
        val tmp = File(to.parentFile, to.name + ".tmp")
        try {
            Http.client.newCall(Request.Builder().url(url).build()).execute().use { r ->
                if (!r.isSuccessful) throw IOException("Erreur serveur (HTTP ${r.code})")
                val body = r.body ?: throw IOException("Réponse vide")
                body.byteStream().use { input -> tmp.outputStream().use { input.copyTo(it, 64 * 1024) } }
            }
            tmp.renameTo(to)
        } catch (e: Exception) {
            tmp.delete()
            if (!to.exists()) throw e
        }
    }

    private suspend fun ensureParsed(force: Boolean): Parsed = parseLock.withLock {
        parsed?.takeIf { !force }?.let { return it }
        download(account.server.trim(), playlistFile, CACHE_TTL, force)
        val p = withContext(Dispatchers.Default) { parse(playlistFile) }
        parsed = p
        p
    }

    override fun clearCache() {
        dir.listFiles()?.forEach { it.delete() }
        parsed = null
        epgLoaded = false
        programs.clear()
    }

    // ------------------------------------------------------------------ Analyse de la playlist

    private val attrRx = Regex("([A-Za-z0-9_-]+)=\"([^\"]*)\"")
    private val episodeRx = Regex("(?i)^(.*?)[\\s._-]*S(\\d{1,2})[\\s._-]*E(\\d{1,4})\\b(.*)$")
    private val vodExt = setOf("mp4", "mkv", "avi", "mov", "m4v", "wmv", "flv", "webm", "mpg", "mpeg")
    private val movieGroup = Regex("(?i)(vod|film|movie|cin[ée]ma)")
    private val seriesGroup = Regex("(?i)(s[ée]rie|series|saison|season|show)")

    private fun idOf(s: String): Int = s.hashCode() and 0x7fffffff

    private fun titleOf(line: String): String {
        // le titre suit la première virgule hors guillemets
        var inQuotes = false
        for (i in line.indices) {
            val c = line[i]
            if (c == '"') inQuotes = !inQuotes
            if (c == ',' && !inQuotes) return line.substring(i + 1).trim()
        }
        return line.substringAfterLast(',').trim()
    }

    private fun parse(file: File): Parsed {
        val liveCats = LinkedHashMap<String, Category>()
        val vodCats = LinkedHashMap<String, Category>()
        val seriesCats = LinkedHashMap<String, Category>()
        val channels = ArrayList<Channel>()
        val movies = ArrayList<Movie>()
        val urls = HashMap<Int, String>()
        val seriesMap = LinkedHashMap<String, MutableList<Triple<Episode, String, String?>>>() // nom -> (épisode, groupe, logo)
        var epgUrl: String? = null

        var attrs: Map<String, String> = emptyMap()
        var title: String? = null
        var groupLine: String? = null
        var num = 0
        val now = System.currentTimeMillis() / 1000

        file.bufferedReader().useLines { lines ->
            for (raw in lines) {
                val line = raw.trim()
                if (line.isEmpty()) continue
                when {
                    line.startsWith("#EXTM3U", true) -> {
                        val a = attrRx.findAll(line).associate { it.groupValues[1].lowercase() to it.groupValues[2] }
                        epgUrl = (a["url-tvg"] ?: a["x-tvg-url"] ?: a["tvg-url"])?.split(',')?.firstOrNull()?.trim()?.ifBlank { null }
                    }
                    line.startsWith("#EXTINF", true) -> {
                        attrs = attrRx.findAll(line).associate { it.groupValues[1].lowercase() to it.groupValues[2] }
                        title = titleOf(line)
                    }
                    line.startsWith("#EXTGRP:", true) -> groupLine = line.substringAfter(':').trim()
                    line.startsWith("#") -> {}
                    else -> {
                        val url = line
                        val name = title ?: attrs["tvg-name"] ?: url.substringAfterLast('/')
                        val group = attrs["group-title"]?.ifBlank { null } ?: groupLine ?: "Sans catégorie"
                        val logo = attrs["tvg-logo"].validUrl()
                        val path = url.substringBefore('?').lowercase()
                        val ext = path.substringAfterLast('.', "").takeIf { it.length in 2..4 } ?: ""
                        val ep = episodeRx.find(name)
                        val kind = when {
                            "/series/" in path -> 'S'
                            "/movie/" in path -> if (ep != null) 'S' else 'M'
                            "/live/" in path -> 'L'
                            ext in vodExt || movieGroup.containsMatchIn(group) || seriesGroup.containsMatchIn(group) ->
                                if (ep != null || seriesGroup.containsMatchIn(group)) 'S' else 'M'
                            else -> 'L'
                        }
                        val id = idOf(url)
                        urls[id] = url
                        when (kind) {
                            'L' -> {
                                val cat = liveCats.getOrPut(group) { Category("l" + idOf(group), group) }
                                val (display, quality) = Titles.channel(name)
                                channels += Channel(
                                    id = id, num = ++num, name = name, displayName = display, quality = quality,
                                    logo = logo, epgId = attrs["tvg-id"]?.ifBlank { null } ?: attrs["tvg-name"],
                                    categoryId = cat.id, archive = false, archiveDays = 0,
                                )
                            }
                            'M' -> {
                                val cat = vodCats.getOrPut(group) { Category("m" + idOf(group), group) }
                                movies += Movie(
                                    id = id, name = name, title = Titles.clean(name), year = Titles.year(name),
                                    poster = logo, rating = null, added = now - movies.size, categoryId = cat.id,
                                    ext = ext.ifBlank { "mp4" }, tmdbId = attrs["tmdb-id"] ?: attrs["tmdb"],
                                )
                            }
                            else -> {
                                val m = ep
                                val show = Titles.clean((m?.groupValues?.get(1) ?: name).trim().trimEnd('-', '_', '.', ' ')).ifBlank { name }
                                val season = m?.groupValues?.get(2)?.toIntOrNull() ?: 1
                                val number = m?.groupValues?.get(3)?.toIntOrNull() ?: ((seriesMap[show]?.size ?: 0) + 1)
                                val epTitle = m?.groupValues?.get(4)?.trim()?.trimStart('-', ' ', ':')?.ifBlank { null } ?: "Épisode $number"
                                seriesMap.getOrPut(show) { ArrayList() } += Triple(
                                    Episode(id, season, number, epTitle, ext.ifBlank { "mp4" }, null, null, null), group, logo,
                                )
                            }
                        }
                        attrs = emptyMap(); title = null; groupLine = null
                    }
                }
            }
        }

        val series = ArrayList<Series>()
        val episodes = HashMap<Int, SeriesContent>()
        for ((show, list) in seriesMap) {
            val sid = idOf("series:$show")
            val group = list.first().second
            val cat = seriesCats.getOrPut(group) { Category("s" + idOf(group), group) }
            val bySeason = list.map { it.first }.distinctBy { it.id }.groupBy { it.season }.toSortedMap()
                .mapValues { (_, v) -> v.sortedBy { it.number } }
            episodes[sid] = SeriesContent(bySeason.keys.toList(), bySeason)
            series += Series(
                id = sid, name = show, title = show, year = Titles.year(show), cover = list.firstNotNullOfOrNull { it.third },
                plot = null, genre = null, rating = null, lastModified = now - series.size, categoryId = cat.id,
                backdrop = null, tmdbId = null,
            )
        }
        return Parsed(
            liveCats.values.toList(), vodCats.values.toList(), seriesCats.values.toList(),
            channels, movies, series, urls, episodes, epgUrl,
        )
    }

    // ------------------------------------------------------------------ Source

    override suspend fun login(): AccountInfo {
        val p = try {
            ensureParsed(force = true)
        } catch (e: java.net.UnknownHostException) {
            throw e
        } catch (e: Exception) {
            throw XtreamException("Impossible de lire la playlist : ${e.message ?: "lien invalide"}")
        }
        if (p.channels.isEmpty() && p.movies.isEmpty() && p.series.isEmpty()) {
            throw XtreamException("La playlist est vide ou n'est pas au format M3U.")
        }
        return AccountInfo("Active", null, null, null, false, null, null)
    }

    override suspend fun liveCategories(force: Boolean) = ensureParsed(false).liveCats
    override suspend fun vodCategories(force: Boolean) = ensureParsed(false).vodCats
    override suspend fun seriesCategories(force: Boolean) = ensureParsed(false).seriesCats
    override suspend fun liveStreams(force: Boolean) = ensureParsed(false).channels
    override suspend fun vodStreams(force: Boolean) = ensureParsed(false).movies
    override suspend fun series(force: Boolean) = ensureParsed(false).series

    override suspend fun vodInfo(id: Int) = XtVodInfo(null, null, null, null, null, null, null, null, null, null, null)

    override suspend fun seriesInfo(id: Int): XtSeriesInfo {
        val content = ensureParsed(false).episodes[id] ?: SeriesContent(emptyList(), emptyMap())
        return XtSeriesInfo(vodInfo(id), content)
    }

    override fun liveUrl(ch: Channel, format: String) = parsed?.urls?.get(ch.id) ?: ""
    override fun movieUrl(m: Movie) = parsed?.urls?.get(m.id) ?: ""
    override fun episodeUrl(id: Int, ext: String) = parsed?.urls?.get(id) ?: ""
    override fun catchupUrl(ch: Channel, p: EpgProgram) = liveUrl(ch, "ts")

    // ------------------------------------------------------------------ Guide TV (XMLTV)

    private fun epgSource(): String? = account.epgUrl.trim().ifBlank { null } ?: parsed?.epgUrl

    private suspend fun ensureEpg() = epgLock.withLock {
        if (epgLoaded) return@withLock
        val url = epgSource()
        if (url == null) {
            epgLoaded = true; return@withLock
        }
        try {
            download(url, epgFile, EPG_TTL, false)
            val wanted = parsed?.channels?.mapNotNull { it.epgId?.lowercase() }?.toHashSet() ?: hashSetOf()
            val result = withContext(Dispatchers.IO) { parseXmltv(epgFile, wanted) }
            programs.clear()
            programs.putAll(result)
        } catch (_: Exception) {
        }
        epgLoaded = true
    }

    private fun open(file: File): InputStream {
        val input = BufferedInputStream(file.inputStream())
        input.mark(2)
        val b1 = input.read()
        val b2 = input.read()
        input.reset()
        return if (b1 == 0x1f && b2 == 0x8b) GZIPInputStream(input) else input
    }

    private fun parseXmltv(file: File, wanted: Set<String>): Map<String, List<EpgProgram>> {
        val fmt = SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US)
        val fmtNoZone = SimpleDateFormat("yyyyMMddHHmmss", Locale.US)
        fun time(s: String?): Long? {
            if (s.isNullOrBlank()) return null
            val t = s.trim()
            return runCatching { if (t.length > 14) fmt.parse(t)?.time else fmtNoZone.parse(t.take(14))?.time }.getOrNull()
        }
        val now = System.currentTimeMillis()
        val from = now - 24L * 3600 * 1000
        val to = now + 48L * 3600 * 1000
        val out = HashMap<String, MutableList<EpgProgram>>()
        open(file).use { input ->
            val p = Xml.newPullParser()
            p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            p.setInput(input, null)
            var channel: String? = null
            var start: Long? = null
            var stop: Long? = null
            var title: String? = null
            var desc: String? = null
            var inProg = false
            var ev = p.eventType
            while (ev != XmlPullParser.END_DOCUMENT) {
                when (ev) {
                    XmlPullParser.START_TAG -> when (p.name) {
                        "programme" -> {
                            channel = p.getAttributeValue(null, "channel")?.lowercase()
                            start = time(p.getAttributeValue(null, "start"))
                            stop = time(p.getAttributeValue(null, "stop"))
                            title = null; desc = null
                            inProg = channel != null && (wanted.isEmpty() || channel in wanted) &&
                                start != null && stop != null && stop > from && start < to
                        }
                        "title" -> if (inProg && title == null) title = p.nextText()
                        "desc" -> if (inProg && desc == null) desc = p.nextText()
                    }
                    XmlPullParser.END_TAG -> if (p.name == "programme") {
                        if (inProg) {
                            out.getOrPut(channel!!) { ArrayList() } += EpgProgram(title ?: "Programme", desc, start!!, stop!!)
                        }
                        inProg = false
                    }
                }
                ev = p.next()
            }
        }
        return out.mapValues { (_, v) -> v.sortedBy { it.start } }
    }

    private suspend fun programsFor(streamId: Int): List<EpgProgram> {
        ensureEpg()
        val ch = parsed?.channels?.firstOrNull { it.id == streamId } ?: return emptyList()
        val key = ch.epgId?.lowercase() ?: return emptyList()
        return programs[key].orEmpty()
    }

    override suspend fun shortEpg(streamId: Int, limit: Int): List<EpgProgram> {
        val now = System.currentTimeMillis()
        return programsFor(streamId).filter { it.end > now }.take(limit)
    }

    override suspend fun fullEpg(streamId: Int): List<EpgProgram> = programsFor(streamId)

    companion object {
        const val CACHE_TTL = 6L * 3600 * 1000
        const val EPG_TTL = 12L * 3600 * 1000
    }
}
