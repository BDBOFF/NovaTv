package com.nova.tv

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nova.tv.data.Account
import com.nova.tv.data.AccountInfo
import com.nova.tv.data.Category
import com.nova.tv.data.Channel
import com.nova.tv.data.ContentType
import com.nova.tv.data.Details
import com.nova.tv.data.EpgProgram
import com.nova.tv.data.Episode
import com.nova.tv.data.Fav
import com.nova.tv.data.History
import com.nova.tv.data.Http
import com.nova.tv.data.Kind
import com.nova.tv.data.Movie
import com.nova.tv.data.PlayItem
import com.nova.tv.data.PlaySession
import com.nova.tv.data.Series
import com.nova.tv.data.SeriesContent
import com.nova.tv.data.Storage
import com.nova.tv.data.Titles
import com.nova.tv.data.Tmdb
import com.nova.tv.data.UpdateInfo
import com.nova.tv.data.Updater
import com.nova.tv.data.TmdbItem
import com.nova.tv.data.XtreamClient
import com.nova.tv.data.normalizeServer
import com.nova.tv.ui.FocusMemory
import com.nova.tv.ui.MainTab
import com.nova.tv.ui.Rail
import com.nova.tv.ui.RailStyle
import com.nova.tv.ui.Route
import com.nova.tv.ui.Scope
import com.nova.tv.ui.SeeAll
import com.nova.tv.ui.Special
import com.nova.tv.ui.Tile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.UUID

class AppViewModel(app: Application) : AndroidViewModel(app) {

    val storage = Storage(app)
    val focus = FocusMemory()

    // ------------------------------------------------------------- Navigation
    val backStack = mutableStateListOf<Route>(Route.Splash)
    var tab by mutableStateOf(MainTab.HOME)
    var initialFocusDone = false
    var focusTabsRequest by mutableIntStateOf(0)
    var tabBarFocused = false
    val selection = mutableStateMapOf<ContentType, String>()

    // ------------------------------------------------------------- Session
    var client by mutableStateOf<XtreamClient?>(null)
        private set
    var info by mutableStateOf<AccountInfo?>(null)
        private set
    var loading by mutableStateOf(false)
        private set
    var loadingText by mutableStateOf("")
        private set
    var error by mutableStateOf<String?>(null)

    // ------------------------------------------------------------- Catalogue
    var liveCategories by mutableStateOf<List<Category>>(emptyList())
        private set
    var channels by mutableStateOf<List<Channel>>(emptyList())
        private set
    var movieCategories by mutableStateOf<List<Category>>(emptyList())
        private set
    var movies by mutableStateOf<List<Movie>>(emptyList())
        private set
    var seriesCategories by mutableStateOf<List<Category>>(emptyList())
        private set
    var series by mutableStateOf<List<Series>>(emptyList())
        private set

    var moviesById: Map<Int, Movie> = emptyMap()
        private set
    var seriesById: Map<Int, Series> = emptyMap()
        private set
    var channelsById: Map<Int, Channel> = emptyMap()
        private set
    var counts: Map<String, Int> = emptyMap()
        private set

    var trendMovies by mutableStateOf<List<TmdbItem>>(emptyList())
        private set
    var trendSeries by mutableStateOf<List<TmdbItem>>(emptyList())
        private set
    var nowPlaying by mutableStateOf<List<TmdbItem>>(emptyList())
        private set

    // ------------------------------------------------------------- Caches
    val epg = mutableStateMapOf<Int, List<EpgProgram>>()
    private val epgTime = HashMap<Int, Long>()
    private val epgFull = HashSet<Int>()
    private val epgLoading = HashSet<Int>()
    private val epgSem = Semaphore(4)
    private val details = HashMap<String, Details>()
    private val contents = HashMap<Int, SeriesContent>()
    private var tmdbInstance: Tmdb? = null

    val account: Account? get() = storage.accounts.find { it.id == storage.activeId }
    private val accId: String get() = storage.activeId ?: ""

    init {
        Http.userAgent = storage.userAgent
        viewModelScope.launch {
            delay(500)
            val acc = account
            if (acc == null) replaceAll(Route.Login)
            else if (connect(acc) != null) replaceAll(Route.Login)
        }
    }

    // ============================================================= Mise à jour

    var update by mutableStateOf<UpdateInfo?>(null)
        private set
    var updateProgress by mutableStateOf<Float?>(null)
        private set
    var updateMessage by mutableStateOf<String?>(null)
        private set
    private var downloaded: java.io.File? = null

    fun checkUpdate() {
        viewModelScope.launch { update = Updater.check() }
    }

    fun installUpdate() {
        val info = update ?: return
        val ctx = getApplication<Application>()
        downloaded?.takeIf { it.exists() }?.let { f ->
            if (!Updater.install(ctx, f)) updateMessage = "Autorisez SamothTV à installer des applications, puis réessayez."
            return
        }
        if (updateProgress != null) return
        viewModelScope.launch {
            updateMessage = null
            updateProgress = 0f
            try {
                val f = Updater.download(ctx, info) { p -> updateProgress = p }
                downloaded = f
                if (!Updater.install(ctx, f)) updateMessage = "Autorisez SamothTV à installer des applications, puis réessayez."
            } catch (e: Exception) {
                updateMessage = "Échec du téléchargement : ${e.message}"
            } finally {
                updateProgress = null
            }
        }
    }

    // ============================================================= Navigation

    val route: Route get() = backStack.last()

    fun navigate(r: Route) {
        backStack.add(r)
    }

    fun back(): Boolean {
        if (backStack.size <= 1) return false
        backStack.removeAt(backStack.lastIndex)
        focus.pending.addAll(focus.last.keys)
        return true
    }

    fun replaceAll(r: Route) {
        backStack.clear()
        backStack.add(r)
    }

    fun openSeeAll(target: SeeAll) {
        when (target.tab) {
            MainTab.MOVIES -> {
                selection[ContentType.MOVIE] = target.category
                navigate(Route.Catalog(ContentType.MOVIE))
            }
            MainTab.SERIES -> {
                selection[ContentType.SERIES] = target.category
                navigate(Route.Catalog(ContentType.SERIES))
            }
            else -> tab = target.tab
        }
    }

    // ============================================================= Connexion

    suspend fun addAccount(name: String, server: String, user: String, pass: String): String? {
        val acc = Account(
            id = UUID.randomUUID().toString(),
            name = name.trim().ifBlank { hostOf(server) },
            server = normalizeServer(server),
            username = user.trim(),
            password = pass.trim(),
        )
        return connect(acc)
    }

    private fun hostOf(server: String): String =
        normalizeServer(server).substringAfter("://").substringBefore(":").substringBefore("/")

    /** Se connecte, charge tout le catalogue et ouvre l'accueil. Renvoie un message d'erreur ou null. */
    suspend fun connect(acc: Account, force: Boolean = false): String? {
        loading = true
        error = null
        loadingText = "Connexion au serveur…"
        try {
            val c = XtreamClient(acc, getApplication<Application>().cacheDir)
            if (force) c.clearCache()
            val inf = c.login()
            storage.upsertAccount(acc)
            storage.setActive(acc.id)
            client = c
            info = inf
            loadingText = "Chargement des chaînes, films et séries…"
            coroutineScope {
                val lc = async { runCatching { c.liveCategories(force) }.getOrDefault(emptyList()) }
                val ls = async { runCatching { c.liveStreams(force) }.getOrDefault(emptyList()) }
                val mc = async { runCatching { c.vodCategories(force) }.getOrDefault(emptyList()) }
                val ms = async { runCatching { c.vodStreams(force) }.getOrDefault(emptyList()) }
                val sc = async { runCatching { c.seriesCategories(force) }.getOrDefault(emptyList()) }
                val ss = async { runCatching { c.series(force) }.getOrDefault(emptyList()) }
                liveCategories = lc.await()
                channels = ls.await()
                movieCategories = mc.await()
                movies = ms.await()
                seriesCategories = sc.await()
                series = ss.await()
            }
            loadingText = "Préparation de l'interface…"
            buildIndexes()
            epg.clear(); epgTime.clear(); epgFull.clear()
            details.clear(); contents.clear()
            trendMovies = emptyList(); trendSeries = emptyList(); nowPlaying = emptyList()
            focus.last.clear(); focus.pending.clear()
            initialFocusDone = false
            selection.clear()
            tab = MainTab.HOME
            replaceAll(Route.Main)
            viewModelScope.launch { loadTrending() }
            checkUpdate()
            return null
        } catch (e: Exception) {
            val msg = when {
                e is java.net.UnknownHostException -> "Serveur introuvable. Vérifiez l'adresse."
                e is java.net.SocketTimeoutException -> "Le serveur ne répond pas (délai dépassé)."
                e is java.net.ConnectException -> "Connexion au serveur impossible."
                else -> e.message ?: "Erreur de connexion"
            }
            error = msg
            return msg
        } finally {
            loading = false
        }
    }

    private suspend fun buildIndexes() = withContext(Dispatchers.Default) {
        moviesById = movies.associateBy { it.id }
        seriesById = series.associateBy { it.id }
        channelsById = channels.associateBy { it.id }
        val map = HashMap<String, Int>()
        channels.forEach { map.merge("L:${it.categoryId}", 1, Int::plus) }
        movies.forEach { map.merge("M:${it.categoryId}", 1, Int::plus) }
        series.forEach { map.merge("S:${it.categoryId}", 1, Int::plus) }
        counts = map
        moviesByTmdb = movies.mapNotNull { m -> m.tmdbId?.takeIf { it != "0" }?.let { it to m } }.toMap()
        moviesByNorm = movies.groupBy { Titles.normalize(it.title) }
        seriesByTmdb = series.mapNotNull { x -> x.tmdbId?.takeIf { it != "0" }?.let { it to x } }.toMap()
        seriesByNorm = series.groupBy { Titles.normalize(it.title) }
    }

    fun refresh() {
        val acc = account ?: return
        viewModelScope.launch {
            replaceAll(Route.Splash)
            if (connect(acc, force = true) != null) replaceAll(Route.Login)
        }
    }

    fun switchAccount(acc: Account) {
        viewModelScope.launch {
            replaceAll(Route.Splash)
            if (connect(acc) != null) replaceAll(Route.Login)
        }
    }

    fun logout() {
        storage.activeId?.let { storage.removeAccount(it) }
        client = null
        info = null
        movies = emptyList(); series = emptyList(); channels = emptyList()
        replaceAll(Route.Login)
    }

    fun clearImageAndDataCache() {
        client?.clearCache()
        getApplication<Application>().cacheDir.resolve("img").deleteRecursively()
        details.clear()
        contents.clear()
    }

    // ============================================================= Catégories

    private val adultRx = Regex("(?i)(adult|xxx|\\+18|18\\+|porn|hot)")

    fun isAdult(c: Category) = adultRx.containsMatchIn(c.name)

    fun categoriesOf(type: ContentType): List<Category> {
        val list = when (type) {
            ContentType.LIVE -> liveCategories
            ContentType.MOVIE -> movieCategories
            ContentType.SERIES -> seriesCategories
        }
        val prefix = prefixOf(type)
        return list.filter { (counts["$prefix:${it.id}"] ?: 0) > 0 && (storage.showAdult || !isAdult(it)) }
    }

    private fun hiddenCategoryIds(type: ContentType): Set<String> {
        if (storage.showAdult) return emptySet()
        val list = when (type) {
            ContentType.LIVE -> liveCategories
            ContentType.MOVIE -> movieCategories
            ContentType.SERIES -> seriesCategories
        }
        return list.filter { isAdult(it) }.map { it.id }.toSet()
    }

    fun visibleMovies(): List<Movie> {
        val hidden = hiddenCategoryIds(ContentType.MOVIE)
        return if (hidden.isEmpty()) movies else movies.filter { it.categoryId !in hidden }
    }

    fun visibleSeries(): List<Series> {
        val hidden = hiddenCategoryIds(ContentType.SERIES)
        return if (hidden.isEmpty()) series else series.filter { it.categoryId !in hidden }
    }

    fun visibleChannels(): List<Channel> {
        val hidden = hiddenCategoryIds(ContentType.LIVE)
        return if (hidden.isEmpty()) channels else channels.filter { it.categoryId !in hidden }
    }

    fun count(type: ContentType, catId: String) = counts["${prefixOf(type)}:$catId"] ?: 0

    private fun prefixOf(type: ContentType) = when (type) {
        ContentType.LIVE -> "L"
        ContentType.MOVIE -> "M"
        ContentType.SERIES -> "S"
    }

    // ============================================================= Favoris / historique

    fun isFav(kind: String, id: Int) = storage.isFav(accId, kind, id)

    fun toggleFav(kind: String, id: Int, title: String, image: String?, year: String? = null) {
        storage.toggleFav(Fav(accId, kind, id, title, image, System.currentTimeMillis(), year))
    }

    fun favorites(kind: String): List<Fav> = storage.favorites.filter { it.accountId == accId && it.kind == kind }

    fun history(kind: String? = null): List<History> =
        storage.history.filter { it.accountId == accId && (kind == null || it.kind == kind) }

    fun resumeOf(kind: String, id: Int): History? =
        storage.history.firstOrNull { it.accountId == accId && it.kind == kind && it.id == id }

    fun lastEpisodeOf(seriesId: Int): History? =
        storage.history.firstOrNull { it.accountId == accId && it.kind == Kind.EPISODE && it.seriesId == seriesId }

    fun continueWatching(): List<History> {
        val seen = HashSet<Int>()
        return history().filter { h ->
            when (h.kind) {
                Kind.MOVIE -> h.progress in 0.02f..0.94f
                Kind.EPISODE -> seen.add(h.seriesId) && (h.progress in 0.02f..0.94f || h.progress > 0.94f)
                else -> false
            }
        }.take(20)
    }

    // ============================================================= TMDB / fiches

    fun tmdb(): Tmdb? {
        val k = storage.tmdbKey.trim()
        if (k.isBlank()) return null
        if (tmdbInstance?.key != k) tmdbInstance = Tmdb(k)
        return tmdbInstance
    }

    fun cachedDetails(kind: String, id: Int): Details? = details["$kind:$id"]

    suspend fun movieDetails(m: Movie): Details {
        details["${Kind.MOVIE}:${m.id}"]?.let { return it }
        val c = client
        val x = c?.let { runCatching { it.vodInfo(m.id) }.getOrNull() }
        val t = tmdb()?.let { runCatching { it.movie(x?.tmdbId ?: m.tmdbId, m.title, m.year ?: x?.year) }.getOrNull() }
        val d = Details(
            title = m.title,
            logo = t?.logo,
            plot = t?.plot?.takeIf { it.isNotBlank() } ?: x?.plot,
            backdrop = t?.backdrop ?: x?.backdrop,
            poster = m.poster ?: t?.poster ?: x?.poster,
            year = m.year ?: x?.year ?: t?.year,
            genres = t?.genres?.takeIf { it.isNotEmpty() } ?: splitGenres(x?.genre),
            rating = t?.rating ?: x?.rating ?: m.rating,
            runtimeMin = t?.runtimeMin ?: x?.durationSec?.let { sec -> (sec / 60).takeIf { m -> m > 0 } },
            director = t?.director ?: x?.director,
            cast = t?.cast?.takeIf { it.isNotEmpty() } ?: splitCast(x?.cast),
            trailer = t?.trailer ?: x?.trailer,
        )
        details["${Kind.MOVIE}:${m.id}"] = d
        return d
    }

    suspend fun seriesContent(s: Series): SeriesContent {
        contents[s.id]?.let { return it }
        val c = client ?: return SeriesContent(emptyList(), emptyMap())
        val info = c.seriesInfo(s.id)
        contents[s.id] = info.content
        return info.content
    }

    suspend fun seriesDetails(s: Series): Details {
        details["${Kind.SERIES}:${s.id}"]?.let { return it }
        val x = client?.let { runCatching { it.seriesInfo(s.id) }.getOrNull() }
        x?.let { contents[s.id] = it.content }
        val meta = x?.meta
        val t = tmdb()?.let { runCatching { it.tv(meta?.tmdbId ?: s.tmdbId, s.title, s.year) }.getOrNull() }
        val d = Details(
            title = s.title,
            logo = t?.logo,
            plot = t?.plot?.takeIf { it.isNotBlank() } ?: s.plot ?: meta?.plot,
            backdrop = t?.backdrop ?: s.backdrop ?: meta?.backdrop,
            poster = s.cover ?: t?.poster,
            year = s.year ?: t?.year,
            genres = t?.genres?.takeIf { it.isNotEmpty() } ?: splitGenres(s.genre ?: meta?.genre),
            rating = t?.rating ?: s.rating,
            runtimeMin = t?.runtimeMin,
            director = t?.director ?: meta?.director,
            cast = t?.cast?.takeIf { it.isNotEmpty() } ?: splitCast(meta?.cast),
            trailer = t?.trailer ?: meta?.trailer,
        )
        details["${Kind.SERIES}:${s.id}"] = d
        return d
    }

    /** Fiche légère pour l'aperçu de l'accueil. */
    suspend fun previewDetails(tile: Tile): Details? = when (tile.kind) {
        Kind.MOVIE -> moviesById[tile.id]?.let { movieDetails(it) }
        Kind.SERIES -> seriesById[tile.id]?.let { seriesDetails(it) }
        Kind.EPISODE -> tile.history?.seriesId?.let { seriesById[it] }?.let { seriesDetails(it) }
        Kind.TMDB_MOVIE, Kind.TMDB_TV -> tmdbDetails(tile.kind, tile.id)
        else -> null
    }

    fun cachedPreview(tile: Tile): Details? = when (tile.kind) {
        Kind.MOVIE -> cachedDetails(Kind.MOVIE, tile.id)
        Kind.SERIES -> cachedDetails(Kind.SERIES, tile.id)
        Kind.EPISODE -> tile.history?.seriesId?.let { cachedDetails(Kind.SERIES, it) }
        Kind.TMDB_MOVIE, Kind.TMDB_TV -> details["${tile.kind}:${tile.id}"]
        else -> null
    }

    private fun splitGenres(s: String?): List<String> =
        s?.split(',', '/', '|')?.map { it.trim() }?.filter { it.isNotEmpty() }?.take(3) ?: emptyList()

    private fun splitCast(s: String?) =
        s?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }?.take(12)
            ?.map { com.nova.tv.data.CastMember(it, null, null) } ?: emptyList()

    // ============================================================= TMDB ↔ catalogue

    private var moviesByTmdb: Map<String, Movie> = emptyMap()
    private var moviesByNorm: Map<String, List<Movie>> = emptyMap()
    private var seriesByTmdb: Map<String, Series> = emptyMap()
    private var seriesByNorm: Map<String, List<Series>> = emptyMap()
    private val tmdbItems = HashMap<String, TmdbItem>()

    fun tmdbItem(kind: String, id: Int): TmdbItem? = tmdbItems["$kind:$id"]

    fun matchMovie(tmdbId: Int?, title: String, original: String?, year: String?): Movie? {
        val hidden = hiddenCategoryIds(ContentType.MOVIE)
        if (tmdbId != null) moviesByTmdb[tmdbId.toString()]?.takeIf { it.categoryId !in hidden }?.let { return it }
        val cands = (moviesByNorm[Titles.normalize(title)].orEmpty() +
            (original?.let { moviesByNorm[Titles.normalize(it)] }.orEmpty()))
            .filter { it.categoryId !in hidden }
        if (cands.isEmpty()) return null
        if (year == null) return cands.first()
        return cands.firstOrNull { it.year == year } ?: cands.firstOrNull { it.year == null }
    }

    fun matchSeries(tmdbId: Int?, title: String, original: String?): Series? {
        val hidden = hiddenCategoryIds(ContentType.SERIES)
        if (tmdbId != null) seriesByTmdb[tmdbId.toString()]?.takeIf { it.categoryId !in hidden }?.let { return it }
        val cands = (seriesByNorm[Titles.normalize(title)].orEmpty() +
            (original?.let { seriesByNorm[Titles.normalize(it)] }.orEmpty()))
            .filter { it.categoryId !in hidden }
        return cands.maxByOrNull { it.lastModified }
    }

    /** Si un titre TMDB est arrivé dans le catalogue, renvoie la tuile du catalogue. */
    fun resolveTmdb(kind: String, id: Int, title: String, year: String?): Tile? {
        val original = tmdbItem(kind, id)?.originalTitle
        return if (kind == Kind.TMDB_MOVIE) matchMovie(id, title, original, year)?.let { movieTile(it) }
        else matchSeries(id, title, original)?.let { seriesTile(it) }
    }

    fun tmdbTile(item: TmdbItem): Tile {
        val kind = if (item.type == "movie") Kind.TMDB_MOVIE else Kind.TMDB_TV
        tmdbItems["$kind:${item.id}"] = item
        val inCatalog = if (item.type == "movie") matchMovie(item.id, item.title, item.originalTitle, item.year)?.let { movieTile(it) }
        else matchSeries(item.id, item.title, item.originalTitle)?.let { seriesTile(it) }
        return inCatalog ?: Tile(
            key = "t$kind${item.id}", kind = kind, id = item.id, title = item.title,
            image = item.poster, subtitle = item.year, badge = "BIENTÔT",
        )
    }

    suspend fun tmdbDetails(kind: String, id: Int): Details? {
        details["$kind:$id"]?.let { return it }
        val t = tmdb() ?: return null
        val d = runCatching { t.detailsById(if (kind == Kind.TMDB_MOVIE) "movie" else "tv", id) }.getOrNull() ?: return null
        details["$kind:$id"] = d
        return d
    }

    private suspend fun loadTrending() {
        val t = tmdb() ?: return
        coroutineScope {
            val m = async { runCatching { t.trending("movie") }.getOrDefault(emptyList()) }
            val s = async { runCatching { t.trending("tv") }.getOrDefault(emptyList()) }
            val c = async { runCatching { t.nowPlaying() }.getOrDefault(emptyList()) }
            trendMovies = m.await()
            trendSeries = s.await()
            nowPlaying = c.await()
        }
    }

    private fun currentYear() = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)

    private fun topRatedMovies(n: Int): List<Movie> {
        val pool = visibleMovies().filter { (it.rating ?: 0.0) in 5.0..9.6 }
        val recent = pool.filter { (it.year?.toIntOrNull() ?: 0) >= currentYear() - 4 }
        val src = if (recent.size >= n) recent else pool
        return src.sortedWith(compareByDescending<Movie> { it.rating ?: 0.0 }.thenByDescending { it.added })
            .distinctBy { Titles.normalize(it.title) }.take(n)
    }

    private fun topRatedSeries(n: Int): List<Series> {
        val pool = visibleSeries().filter { (it.rating ?: 0.0) in 5.0..9.6 }
        val recent = pool.filter { (it.year?.toIntOrNull() ?: 0) >= currentYear() - 4 }
        val src = if (recent.size >= n) recent else pool
        return src.sortedWith(compareByDescending<Series> { it.rating ?: 0.0 }.thenByDescending { it.lastModified })
            .distinctBy { Titles.normalize(it.title) }.take(n)
    }

    private fun <T> interleave(a: List<T>, b: List<T>): List<T> {
        val out = ArrayList<T>(a.size + b.size)
        for (i in 0 until maxOf(a.size, b.size)) {
            if (i < a.size) out += a[i]
            if (i < b.size) out += b[i]
        }
        return out
    }

    // ============================================================= Tuiles & rangées

    private val week = 7L * 24 * 3600

    fun movieTile(m: Movie, rank: Int? = null): Tile {
        val now = System.currentTimeMillis() / 1000
        val isNew = m.added > 0 && now - m.added < week
        return Tile(
            key = "m${m.id}", kind = Kind.MOVIE, id = m.id, title = m.title, image = m.poster,
            subtitle = m.year, badge = if (isNew) "NOUVEAU" else null, rank = rank,
        )
    }

    fun seriesTile(s: Series, rank: Int? = null): Tile {
        val now = System.currentTimeMillis() / 1000
        val isNew = s.lastModified > 0 && now - s.lastModified < week
        return Tile(
            key = "s${s.id}", kind = Kind.SERIES, id = s.id, title = s.title, image = s.cover,
            subtitle = s.year, badge = if (isNew) "NOUVEAUX ÉPISODES" else null, rank = rank,
        )
    }

    fun channelTile(c: Channel) = Tile(
        key = "c${c.id}", kind = Kind.LIVE, id = c.id, title = c.displayName, image = c.logo,
        badge = c.quality, channel = c,
    )

    private fun historyTile(h: History): Tile {
        val finished = h.kind == Kind.EPISODE && h.progress > 0.94f
        return Tile(
            key = "h${h.kind}${h.id}", kind = if (h.kind == Kind.EPISODE) Kind.EPISODE else h.kind, id = h.id,
            title = h.title, image = h.image,
            subtitle = if (finished) "Épisode suivant" else h.subtitle,
            progress = if (finished) null else h.progress, history = h,
        )
    }

    private fun pendingFavTile(f: Fav): Tile {
        val resolved = resolveTmdb(f.kind, f.id, f.title, f.year)
        return resolved?.copy(badge = "DISPONIBLE") ?: Tile(
            key = "t${f.kind}${f.id}", kind = f.kind, id = f.id, title = f.title,
            image = f.image, subtitle = f.year, badge = "BIENTÔT",
        )
    }

    fun favoriteTiles(scope: Scope): List<Tile> =
        storage.favorites.filter { it.accountId == accId }.mapNotNull { f ->
            when (f.kind) {
                Kind.MOVIE -> if (scope == Scope.SERIES) null else moviesById[f.id]?.let { movieTile(it) }
                Kind.SERIES -> if (scope == Scope.MOVIES) null else seriesById[f.id]?.let { seriesTile(it) }
                Kind.TMDB_MOVIE -> if (scope == Scope.SERIES) null else pendingFavTile(f)
                Kind.TMDB_TV -> if (scope == Scope.MOVIES) null else pendingFavTile(f)
                else -> null
            }
        }.distinctBy { it.key }

    /** Rangées d'une page (Accueil, Films ou Séries), dans l'ordre voulu. */
    fun buildRails(scope: Scope): List<Rail> {
        val rails = ArrayList<Rail>()
        val vm = visibleMovies()
        val vs = visibleSeries()
        val wantM = scope != Scope.SERIES
        val wantS = scope != Scope.MOVIES

        // 1. Tendances de la semaine (TMDB) — Top 10, y compris les titres pas encore disponibles
        val trend = when (scope) {
            Scope.ALL -> interleave(trendMovies.map { tmdbTile(it) }, trendSeries.map { tmdbTile(it) })
            Scope.MOVIES -> trendMovies.map { tmdbTile(it) }
            Scope.SERIES -> trendSeries.map { tmdbTile(it) }
        }.distinctBy { it.key }.take(10).mapIndexed { i, t -> t.copy(rank = i + 1, key = "trend_${t.key}") }
        if (trend.isNotEmpty()) rails += Rail("trend", "Tendances de la semaine", RailStyle.RANK, trend)

        // 2. Les mieux notés (films et séries mélangés sur l'accueil)
        val top = when (scope) {
            Scope.ALL -> interleave(topRatedMovies(10).map { movieTile(it) }, topRatedSeries(10).map { seriesTile(it) }).take(10)
            Scope.MOVIES -> topRatedMovies(10).map { movieTile(it) }
            Scope.SERIES -> topRatedSeries(10).map { seriesTile(it) }
        }.mapIndexed { i, t -> t.copy(rank = i + 1, key = "top_${t.key}") }
        if (top.isNotEmpty()) rails += Rail("top", "Les mieux notés", RailStyle.RANK, top)

        if (scope == Scope.MOVIES && nowPlaying.isNotEmpty()) {
            rails += Rail(
                "cinema", "Actuellement au cinéma", RailStyle.POSTER,
                nowPlaying.map { tmdbTile(it) }.distinctBy { it.key }.map { it.copy(key = "cine_${it.key}") },
            )
        }

        // 3. Reprendre la lecture
        val resume = continueWatching().filter {
            when (scope) {
                Scope.ALL -> true
                Scope.MOVIES -> it.kind == Kind.MOVIE
                Scope.SERIES -> it.kind == Kind.EPISODE
            }
        }
        if (resume.isNotEmpty()) {
            rails += Rail("resume", "Reprendre la lecture", RailStyle.LANDSCAPE, resume.map { historyTile(it) })
        }

        // 4. Favoris
        val favs = favoriteTiles(scope)
        if (favs.isNotEmpty()) rails += Rail("favs", "Favoris", RailStyle.POSTER, favs)

        // 5. Nouveautés
        if (wantM && vm.isNotEmpty()) {
            rails += Rail(
                "recent_m", "Films récemment ajoutés", RailStyle.POSTER,
                vm.sortedByDescending { it.added }.take(24).map { movieTile(it) },
                SeeAll(MainTab.MOVIES, Special.RECENT),
            )
        }
        if (wantS && vs.isNotEmpty()) {
            rails += Rail(
                "recent_s", "Séries mises à jour", RailStyle.POSTER,
                vs.sortedByDescending { it.lastModified }.take(24).map { seriesTile(it) },
                SeeAll(MainTab.SERIES, Special.RECENT),
            )
        }
        if (scope == Scope.ALL) {
            val liveFav = favorites(Kind.LIVE).mapNotNull { channelsById[it.id] }
            val liveRecent = history(Kind.LIVE).mapNotNull { channelsById[it.id] }
            val liveRow = (liveFav + liveRecent).distinctBy { it.id }.take(20)
            if (liveRow.isNotEmpty()) rails += Rail("live", "Vos chaînes", RailStyle.CHANNEL, liveRow.map { channelTile(it) })
        }

        // 6. Catégories
        val catLimit = if (scope == Scope.ALL) 4 else 40
        if (wantM) {
            val byCat = vm.groupBy { it.categoryId }
            categoriesOf(ContentType.MOVIE).filter { (byCat[it.id]?.size ?: 0) >= 4 }.take(catLimit).forEach { cat ->
                rails += Rail(
                    "mc_${cat.id}", cat.name, RailStyle.POSTER,
                    byCat[cat.id].orEmpty().sortedByDescending { it.added }.take(20).map { movieTile(it) },
                    SeeAll(MainTab.MOVIES, cat.id),
                )
            }
        }
        if (wantS) {
            val byCat = vs.groupBy { it.categoryId }
            categoriesOf(ContentType.SERIES).filter { (byCat[it.id]?.size ?: 0) >= 4 }.take(if (scope == Scope.ALL) 3 else 40).forEach { cat ->
                rails += Rail(
                    "sc_${cat.id}", cat.name, RailStyle.POSTER,
                    byCat[cat.id].orEmpty().sortedByDescending { it.lastModified }.take(20).map { seriesTile(it) },
                    SeeAll(MainTab.SERIES, cat.id),
                )
            }
        }
        return rails
    }

    // ============================================================= Guide TV

    fun ensureEpg(ch: Channel, full: Boolean = false) {
        val c = client ?: return
        val now = System.currentTimeMillis()
        if (ch.id in epgLoading) return
        val t = epgTime[ch.id]
        val fresh = t != null && now - t < 20 * 60 * 1000 && (epg[ch.id]?.lastOrNull()?.end ?: now) > now + 30 * 60 * 1000
        val fullOk = !full || ch.id in epgFull
        if (fresh && fullOk) return
        if (t != null && now - t < 2 * 60 * 1000 && epg[ch.id].isNullOrEmpty() && fullOk) return
        epgLoading.add(ch.id)
        viewModelScope.launch {
            try {
                epgSem.withPermit {
                    val list = if (full) c.fullEpg(ch.id).ifEmpty { c.shortEpg(ch.id, 20) } else c.shortEpg(ch.id, 12)
                    epg[ch.id] = list
                    if (full) epgFull.add(ch.id)
                }
            } catch (e: Exception) {
                if (epg[ch.id] == null) epg[ch.id] = emptyList()
            } finally {
                epgTime[ch.id] = System.currentTimeMillis()
                epgLoading.remove(ch.id)
            }
        }
    }

    fun nowPlaying(chId: Int, now: Long): EpgProgram? = epg[chId]?.firstOrNull { it.isNow(now) }
    fun nextProgram(chId: Int, now: Long): EpgProgram? = epg[chId]?.firstOrNull { it.start >= now }

    // ============================================================= Lecture

    private fun liveItem(ch: Channel): PlayItem? {
        val c = client ?: return null
        return PlayItem(
            kind = Kind.LIVE, id = ch.id, url = c.liveUrl(ch, storage.liveFormat),
            title = ch.displayName, image = ch.logo, channel = ch,
        )
    }

    fun playChannel(ch: Channel, list: List<Channel>) {
        val items = list.ifEmpty { listOf(ch) }.mapNotNull { liveItem(it) }
        val idx = items.indexOfFirst { it.id == ch.id }.coerceAtLeast(0)
        if (items.isEmpty()) return
        navigate(Route.Player(PlaySession(items, idx)))
    }

    fun playCatchup(ch: Channel, p: EpgProgram) {
        val c = client ?: return
        val item = PlayItem(
            kind = Kind.MOVIE, id = -ch.id, url = c.catchupUrl(ch, p), title = p.title,
            subtitle = "Replay · ${ch.displayName}", image = ch.logo, catchup = p,
        )
        navigate(Route.Player(PlaySession(listOf(item), 0)))
    }

    fun playMovie(m: Movie, fromStart: Boolean = false) {
        val c = client ?: return
        val d = cachedDetails(Kind.MOVIE, m.id)
        val item = PlayItem(
            kind = Kind.MOVIE, id = m.id, url = c.movieUrl(m), title = m.title,
            subtitle = listOfNotNull(m.year, d?.genres?.firstOrNull()).joinToString(" · "),
            image = d?.backdrop ?: m.poster, ext = m.ext,
        )
        val resume = if (fromStart) 0L else resumeOf(Kind.MOVIE, m.id)?.takeIf { it.progress < 0.95f }?.position ?: 0L
        navigate(Route.Player(PlaySession(listOf(item), 0, resume)))
    }

    fun playEpisode(s: Series, content: SeriesContent, ep: Episode, fromStart: Boolean = false) {
        val c = client ?: return
        val backdrop = cachedDetails(Kind.SERIES, s.id)?.backdrop
        val all = content.all
        val items = all.map { e ->
            PlayItem(
                kind = Kind.EPISODE, id = e.id, url = c.episodeUrl(e.id, e.ext), title = s.title,
                subtitle = "S${e.season} · É${e.number} · ${e.title}",
                image = e.image ?: backdrop ?: s.cover, seriesId = s.id, ext = e.ext,
            )
        }
        val idx = items.indexOfFirst { it.id == ep.id }.coerceAtLeast(0)
        val resume = if (fromStart) 0L else resumeOf(Kind.EPISODE, ep.id)?.takeIf { it.progress < 0.95f }?.position ?: 0L
        if (items.isEmpty()) return
        navigate(Route.Player(PlaySession(items, idx, resume)))
    }

    /** Lance la reprise depuis une tuile "Reprendre la lecture". */
    fun resume(h: History) {
        when (h.kind) {
            Kind.MOVIE -> moviesById[h.id]?.let { playMovie(it) }
            Kind.LIVE -> channelsById[h.id]?.let { ch -> playChannel(ch, channels.filter { it.categoryId == ch.categoryId }) }
            Kind.EPISODE -> {
                val s = seriesById[h.seriesId] ?: return
                viewModelScope.launch {
                    val content = runCatching { seriesContent(s) }.getOrNull() ?: return@launch
                    val all = content.all
                    val idx = all.indexOfFirst { it.id == h.id }
                    if (idx < 0) {
                        navigate(Route.SeriesDetail(s.id)); return@launch
                    }
                    val target = if (h.progress > 0.94f) all.getOrNull(idx + 1) else all[idx]
                    if (target == null) navigate(Route.SeriesDetail(s.id))
                    else playEpisode(s, content, target, fromStart = target.id != h.id)
                }
            }
        }
    }

    fun saveProgress(item: PlayItem, position: Long, duration: Long) {
        if (item.catchup != null) return
        if (item.kind == Kind.LIVE) {
            storage.saveHistory(
                History(accId, Kind.LIVE, item.id, item.title, image = item.image, updatedAt = System.currentTimeMillis())
            )
            return
        }
        if (duration <= 0 || position < 5000) return
        storage.saveHistory(
            History(
                accountId = accId, kind = item.kind, id = item.id, title = item.title, subtitle = item.subtitle,
                image = item.image, seriesId = item.seriesId, ext = item.ext,
                position = position, duration = duration, updatedAt = System.currentTimeMillis(),
            )
        )
    }

    // ============================================================= Recherche

    data class SearchResult(val movies: List<Movie>, val series: List<Series>, val channels: List<Channel>)

    suspend fun search(query: String): SearchResult = withContext(Dispatchers.Default) {
        val q = Titles.normalize(query)
        if (q.length < 2) return@withContext SearchResult(emptyList(), emptyList(), emptyList())
        val m = visibleMovies().asSequence().filter { Titles.normalize(it.title).contains(q) }
            .sortedWith(compareBy<Movie> { !Titles.normalize(it.title).startsWith(q) }.thenByDescending { it.added })
            .take(40).toList()
        val s = visibleSeries().asSequence().filter { Titles.normalize(it.title).contains(q) }
            .sortedWith(compareBy<Series> { !Titles.normalize(it.title).startsWith(q) }.thenByDescending { it.lastModified })
            .take(40).toList()
        val c = visibleChannels().asSequence().filter { Titles.normalize(it.displayName).contains(q) }.take(40).toList()
        SearchResult(m, s, c)
    }
}
