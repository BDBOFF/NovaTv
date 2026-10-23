package com.nova.tv.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.nova.tv.BuildConfig
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Persistance locale : playlists, favoris, historique, réglages. */
class Storage(context: Context) {
    private val prefs = context.getSharedPreferences("nova", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private inline fun <reified T> read(key: String): T? =
        prefs.getString(key, null)?.let { s -> runCatching { json.decodeFromString<T>(s) }.getOrNull() }

    private inline fun <reified T> write(key: String, value: T) {
        prefs.edit().putString(key, json.encodeToString(value)).apply()
    }

    var accounts by mutableStateOf(read<List<Account>>("accounts") ?: emptyList())
        private set
    var activeId by mutableStateOf(prefs.getString("active", null))
        private set
    /** Prévenu à chaque changement local (sert à la synchronisation entre appareils). */
    var onChange: ((SyncChange) -> Unit)? = null

    var favorites by mutableStateOf(read<List<Fav>>("favs") ?: emptyList())
        private set
    var history by mutableStateOf(read<List<History>>("history") ?: emptyList())
        private set

    // Clé TMDB intégrée à l'app (aucun réglage visible)
    private val _tmdb = mutableStateOf(BuildConfig.TMDB_API_KEY)
    var tmdbKey: String
        get() = _tmdb.value
        set(v) {
            _tmdb.value = v.trim(); prefs.edit().putString("tmdb", v.trim()).apply()
        }

    private val _liveFormat = mutableStateOf(prefs.getString("liveFormat", "ts") ?: "ts")
    var liveFormat: String
        get() = _liveFormat.value
        set(v) {
            _liveFormat.value = v; prefs.edit().putString("liveFormat", v).apply()
        }

    private val _ua = mutableStateOf(prefs.getString("ua", Http.DEFAULT_UA) ?: Http.DEFAULT_UA)
    var userAgent: String
        get() = _ua.value
        set(v) {
            val value = v.ifBlank { Http.DEFAULT_UA }
            _ua.value = value; Http.userAgent = value
            prefs.edit().putString("ua", value).apply()
        }

    private val _autoNext = mutableStateOf(prefs.getBoolean("autoNext", true))
    var autoNext: Boolean
        get() = _autoNext.value
        set(v) {
            _autoNext.value = v; prefs.edit().putBoolean("autoNext", v).apply()
        }

    private val _rankStyle = mutableStateOf(prefs.getString("rankStyle", "NEON") ?: "NEON")
    var rankStyle: String
        get() = _rankStyle.value
        set(v) {
            _rankStyle.value = v; prefs.edit().putString("rankStyle", v).apply()
        }

    private val _livePreview = mutableStateOf(prefs.getBoolean("livePreview", true))
    var livePreview: Boolean
        get() = _livePreview.value
        set(v) {
            _livePreview.value = v; prefs.edit().putBoolean("livePreview", v).apply()
        }

    private val _showAdult = mutableStateOf(prefs.getBoolean("showAdult", false))
    var showAdult: Boolean
        get() = _showAdult.value
        set(v) {
            _showAdult.value = v; prefs.edit().putBoolean("showAdult", v).apply()
        }

    // ---------------------------------------------------------- Playlists

    fun upsertAccount(a: Account) {
        accounts = accounts.filterNot { it.id == a.id } + a
        write("accounts", accounts)
    }

    fun removeAccount(id: String) {
        accounts = accounts.filterNot { it.id == id }
        write("accounts", accounts)
        favorites = favorites.filterNot { it.accountId == id }
        write("favs", favorites)
        history = history.filterNot { it.accountId == id }
        write("history", history)
        if (activeId == id) setActive(accounts.firstOrNull()?.id)
    }

    fun setActive(id: String?) {
        activeId = id
        prefs.edit().putString("active", id).apply()
    }

    // ---------------------------------------------------------- Favoris

    fun isFav(accountId: String, kind: String, id: Int) =
        favorites.any { it.accountId == accountId && it.kind == kind && it.id == id }

    fun toggleFav(f: Fav) {
        val removing = isFav(f.accountId, f.kind, f.id)
        onChange?.invoke(if (removing) SyncChange.FavRemoved(f.kind, f.id) else SyncChange.FavAdded(f))
        favorites = if (removing) {
            favorites.filterNot { it.accountId == f.accountId && it.kind == f.kind && it.id == f.id }
        } else {
            listOf(f) + favorites
        }
        write("favs", favorites)
    }

    // ---------------------------------------------------------- Historique

    fun saveHistory(h: History) {
        onChange?.invoke(SyncChange.HistorySaved(h))
        history = (listOf(h) + history.filterNot { it.accountId == h.accountId && it.kind == h.kind && it.id == h.id })
            .take(400)
        write("history", history)
    }

    fun removeHistory(accountId: String, kind: String, id: Int) {
        onChange?.invoke(SyncChange.HistoryRemoved(kind, id))
        history = history.filterNot { it.accountId == accountId && it.kind == kind && it.id == id }
        write("history", history)
    }

    fun clearHistory(accountId: String) {
        onChange?.invoke(SyncChange.HistoryCleared)
        history = history.filterNot { it.accountId == accountId }
        write("history", history)
    }

    // ---------------------------------------------------------- Fusion des données d'un autre appareil

    /** Applique l'historique et les favoris reçus du cloud (sans re-déclencher la synchronisation). */
    fun mergeRemote(
        accountId: String,
        remoteHistory: List<History>,
        removedHistory: Map<String, Long>,
        remoteFavs: List<Fav>,
        removedFavs: Map<String, Long>,
    ) {
        fun k(kind: String, id: Int) = "${kind}_$id"
        // Historique : la version la plus récente gagne
        val mine = history.filter { it.accountId == accountId }.associateBy { k(it.kind, it.id) }.toMutableMap()
        for (r in remoteHistory) {
            val key = k(r.kind, r.id)
            val local = mine[key]
            if (local == null || r.updatedAt > local.updatedAt) mine[key] = r.copy(accountId = accountId)
        }
        for ((key, at) in removedHistory) {
            val local = mine[key]
            if (local != null && at >= local.updatedAt) mine.remove(key)
        }
        history = (history.filter { it.accountId != accountId } + mine.values)
            .sortedByDescending { it.updatedAt }.take(400)
        write("history", history)

        // Favoris
        val favs = favorites.filter { it.accountId == accountId }.associateBy { k(it.kind, it.id) }.toMutableMap()
        for (r in remoteFavs) {
            val key = k(r.kind, r.id)
            val removedAt = removedFavs[key] ?: 0L
            if (!favs.containsKey(key) && r.addedAt > removedAt) favs[key] = r.copy(accountId = accountId)
        }
        for ((key, at) in removedFavs) {
            val local = favs[key]
            if (local != null && at > local.addedAt) favs.remove(key)
        }
        favorites = favorites.filter { it.accountId != accountId } + favs.values.sortedByDescending { it.addedAt }
        write("favs", favorites)
    }
}

/** Changement local à envoyer aux autres appareils. */
sealed interface SyncChange {
    data class HistorySaved(val h: History) : SyncChange
    data class HistoryRemoved(val kind: String, val id: Int) : SyncChange
    data object HistoryCleared : SyncChange
    data class FavAdded(val f: Fav) : SyncChange
    data class FavRemoved(val kind: String, val id: Int) : SyncChange
}
