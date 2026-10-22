package com.nova.tv.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.text.Normalizer
import java.util.concurrent.TimeUnit

object Http {
    @Volatile
    var userAgent: String = DEFAULT_UA

    const val DEFAULT_UA = "SamothTV/1.0 (Linux; Android TV)"

    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .addInterceptor { chain ->
                val req = chain.request()
                val builder = req.newBuilder()
                if (req.header("User-Agent") == null || req.header("User-Agent")!!.startsWith("okhttp")) {
                    builder.header("User-Agent", userAgent)
                }
                chain.proceed(builder.build())
            }
            .build()
    }

    suspend fun get(url: String, headers: Map<String, String> = emptyMap()): String =
        withContext(Dispatchers.IO) {
            val rb = Request.Builder().url(url)
            headers.forEach { (k, v) -> rb.header(k, v) }
            client.newCall(rb.build()).execute().use { r ->
                if (!r.isSuccessful) throw IOException("Erreur serveur (HTTP ${r.code})")
                r.body?.string() ?: ""
            }
        }
}

// ---------- JSON tolérant (les panels Xtream renvoient des types très variables) ----------

fun JsonElement?.obj(): JsonObject? = this as? JsonObject

fun JsonElement?.items(): List<JsonElement> = when (this) {
    is JsonArray -> this
    is JsonObject -> this.values.toList()
    else -> emptyList()
}

fun JsonObject.objOf(key: String): JsonObject? = this[key] as? JsonObject

fun JsonObject.s(key: String): String? {
    val e = this[key] ?: return null
    if (e is JsonNull) return null
    val p = e as? JsonPrimitive ?: return null
    val c = p.contentOrNull?.trim() ?: return null
    return c.takeIf { it.isNotEmpty() && it != "null" }
}

fun JsonObject.i(key: String): Int? = s(key)?.toDoubleOrNull()?.toInt()
fun JsonObject.l(key: String): Long? = s(key)?.toDoubleOrNull()?.toLong()
fun JsonObject.d(key: String): Double? = s(key)?.replace(',', '.')?.toDoubleOrNull()

fun JsonObject.firstOf(vararg keys: String): String? {
    for (k in keys) s(k)?.let { return it }
    return null
}

/** Première image valide pour une clé qui peut être une chaîne ou un tableau. */
fun JsonObject.image(key: String): String? {
    val v = when (val e = this[key]) {
        is JsonArray -> e.firstOrNull()?.let { (it as? JsonPrimitive)?.contentOrNull }
        is JsonPrimitive -> e.contentOrNull
        else -> null
    }
    return v?.trim()?.takeIf { it.startsWith("http") }
}

fun String?.validUrl(): String? = this?.trim()?.takeIf { it.startsWith("http") }

// ---------- Nettoyage des noms IPTV ----------

object Titles {
    private val prefix = Regex("^\\s*(\\|?\\s*[A-Z0-9]{2,4}\\s*\\|\\s*|\\[[^\\]]{1,12}\\]\\s*|[A-Z]{2,3}\\s*[:\\-–]\\s+|[A-Z]{2,3}\\s*»\\s*)+")
    private val tags = Regex(
        "(?i)(?<![\\p{L}\\d])(4K|UHD|FHD|HDR|HEVC|H\\.?265|x265|MULTI|VOSTFR|VOST|VFF|VFQ|VFI|TRUEFRENCH|1080p|720p|2160p)(?![\\p{L}\\d])"
    )
    private val yearParen = Regex("[\\(\\[]\\s*(19|20)\\d{2}\\s*[\\)\\]]")
    private val yearEnd = Regex("\\s*[\\-–]\\s*(19|20)\\d{2}\\s*$")
    private val trailingYear = Regex("\\s((?:19|20)\\d{2})$")
    private val yearAny = Regex("(?<!\\d)(19[2-9]\\d|20[0-3]\\d)(?!\\d)")
    private val quality = Regex("(?i)(?<![\\p{L}\\d])(4K|UHD|FHD|HD|SD|HEVC|LOW|720P|1080P)(?![\\p{L}\\d])")

    fun clean(raw: String): String {
        var s = raw.replace(prefix, "")
        s = s.replace(yearParen, "")
        s = s.replace(yearEnd, "")
        s = s.replace(tags, "")
        s = s.replace(Regex("[\\(\\[]\\s*[\\)\\]]"), "")
        s = s.replace(Regex("\\s{2,}"), " ").trim().trim('-', '|', '–', ':', ' ')
        // Année collée en fin de titre ("Vaiana 2 2026") : on la retire si elle est récente
        val m = trailingYear.find(s)
        if (m != null) {
            val y = m.groupValues[1].toInt()
            val rest = s.substring(0, m.range.first).trim()
            if (rest.length >= 2 && y in 1920..(java.util.Calendar.getInstance().get(java.util.Calendar.YEAR) + 1)) s = rest
        }
        return s.ifBlank { raw.trim() }
    }

    fun year(raw: String?): String? = raw?.let { yearAny.findAll(it).lastOrNull()?.value }

    fun normalize(s: String): String {
        val n = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
        return n.replace(Regex("[^a-z0-9]"), "")
    }

    /** Sépare un nom de chaîne "FR| CANAL+ SPORT FHD" en ("CANAL+ SPORT", "FHD"). */
    fun channel(raw: String): Pair<String, String?> {
        val q = quality.findAll(raw).lastOrNull()?.value?.uppercase()?.let {
            when (it) {
                "1080P" -> "FHD"
                "720P" -> "HD"
                "UHD" -> "4K"
                else -> it
            }
        }
        var s = raw.replace(prefix, "")
        s = s.replace(quality, "").replace(Regex("[\\(\\[]\\s*[\\)\\]]"), "")
        s = s.replace(Regex("\\s{2,}"), " ").trim().trim('-', '|', '–', ':', ' ')
        return s.ifBlank { raw.trim() } to q
    }
}
