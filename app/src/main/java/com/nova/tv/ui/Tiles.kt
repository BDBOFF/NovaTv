package com.nova.tv.ui

import com.nova.tv.data.Channel
import com.nova.tv.data.History

data class Tile(
    val key: String,
    val kind: String,
    val id: Int,
    val title: String,
    val image: String?,
    val subtitle: String? = null,
    val progress: Float? = null,
    val badge: String? = null,
    val rank: Int? = null,
    val history: History? = null,
    val channel: Channel? = null,
    val seeAll: Boolean = false,
)

enum class RailStyle { POSTER, LANDSCAPE, RANK, CHANNEL }

data class Rail(
    val key: String,
    val title: String,
    val style: RailStyle,
    val tiles: List<Tile>,
    val seeAllTarget: SeeAll? = null,
)

/** Destination du bouton "Tout voir" d'une rangée. */
data class SeeAll(val tab: MainTab, val category: String)

enum class MainTab(val label: String) {
    HOME("Accueil"),
    MOVIES("Films"),
    SERIES("Séries"),
    LIVE("TV Direct"),
    GUIDE("Guide TV"),
    SEARCH("Recherche"),
    SETTINGS("Réglages"),
}

sealed interface Route {
    data object Splash : Route
    data object Login : Route
    data object Main : Route
    data class MovieDetail(val id: Int) : Route
    data class SeriesDetail(val id: Int) : Route
    data class Player(val session: com.nova.tv.data.PlaySession) : Route
    data class Catalog(val type: com.nova.tv.data.ContentType) : Route
    data object LiveGrid : Route
    data class TmdbDetail(val kind: String, val id: Int) : Route
}

/** Périmètre d'une page d'accueil : tout, films seulement, séries seulement. */
enum class Scope { ALL, MOVIES, SERIES }

/** Mémorise le dernier élément focalisé par écran pour le restaurer au retour. */
class FocusMemory {
    val last = HashMap<String, String>()
    val pending = HashSet<String>()
}

object Special {
    const val ALL = "__all"
    const val FAV = "__fav"
    const val RECENT = "__recent"
    const val RESUME = "__resume"
    const val HISTORY = "__history"
}
