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
    var favorites by mutableStateOf(read<List<Fav>>("favs") ?: emptyList())
        private set
    var history by mutableStateOf(read<List<History>>("history") ?: emptyList())
        private set

    private val _tmdb = mutableStateOf(prefs.getString("tmdb", null) ?: BuildConfig.TMDB_API_KEY)
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
        favorites = if (isFav(f.accountId, f.kind, f.id)) {
            favorites.filterNot { it.accountId == f.accountId && it.kind == f.kind && it.id == f.id }
        } else {
            listOf(f) + favorites
        }
        write("favs", favorites)
    }

    // ---------------------------------------------------------- Historique

    fun saveHistory(h: History) {
        history = (listOf(h) + history.filterNot { it.accountId == h.accountId && it.kind == h.kind && it.id == h.id })
            .take(400)
        write("history", history)
    }

    fun removeHistory(accountId: String, kind: String, id: Int) {
        history = history.filterNot { it.accountId == accountId && it.kind == kind && it.id == id }
        write("history", history)
    }

    fun clearHistory(accountId: String) {
        history = history.filterNot { it.accountId == accountId }
        write("history", history)
    }
}
