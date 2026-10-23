package com.nova.tv.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.security.MessageDigest

/**
 * Synchronisation entre appareils (reprise de lecture + favoris) via Firebase Realtime Database.
 *
 * Aucun compte à créer : tous les appareils connectés au même abonnement IPTV partagent
 * automatiquement les mêmes données. L'identifiant est une empreinte des identifiants,
 * qui ne permet pas de les retrouver.
 */
class SyncManager(private val storage: Storage, private val scope: CoroutineScope) {

    companion object {
        /** Adresse de la base Firebase (vide = synchronisation désactivée). */
        const val DATABASE = ""
        private const val PULL_EVERY = 5 * 60 * 1000L
        private const val PUSH_DELAY = 20 * 1000L
    }

    val enabled: Boolean get() = DATABASE.isNotBlank()

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; isLenient = true }
    private val lock = Mutex()
    private val pending = LinkedHashMap<String, JsonElement>()
    private var syncId: String? = null
    private var accountId: String? = null
    private var pushJob: Job? = null
    private var loop: Job? = null

    var lastSync by androidx.compose.runtime.mutableLongStateOf(0L)
        private set

    init {
        storage.onChange = { change -> onLocalChange(change) }
    }

    private fun sha256(s: String) =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    private fun key(kind: String, id: Int) = "${kind}_$id"

    /** À appeler quand une playlist est ouverte. */
    fun attach(account: Account) {
        accountId = account.id
        syncId = if (account.type == "m3u") sha256("samoth|m3u|" + account.server.trim())
        else sha256("samoth|xtream|" + account.username.trim() + "|" + account.password.trim())
        pending.clear()
        startLoop()
    }

    private fun startLoop() {
        if (!enabled) return
        loop?.cancel()
        loop = scope.launch {
            while (isActive) {
                pull()
                delay(PULL_EVERY)
            }
        }
    }

    /** L'appli revient au premier plan : on récupère ce qui a été regardé ailleurs. */
    fun onForeground() {
        if (enabled && syncId != null) scope.launch { pull() }
    }

    /** L'appli passe en arrière-plan : on envoie tout de suite. */
    fun onBackground() {
        if (enabled) scope.launch { flush() }
    }

    private fun onLocalChange(change: SyncChange) {
        if (!enabled || syncId == null) return
        val now = System.currentTimeMillis()
        synchronized(pending) {
            when (change) {
                is SyncChange.HistorySaved -> {
                    if (change.h.kind == Kind.LIVE) return
                    val k = key(change.h.kind, change.h.id)
                    pending["h/$k"] = json.encodeToJsonElement(History.serializer(), change.h.copy(accountId = ""))
                    pending["hx/$k"] = JsonNull
                }
                is SyncChange.HistoryRemoved -> {
                    val k = key(change.kind, change.id)
                    pending["h/$k"] = JsonNull
                    pending["hx/$k"] = JsonPrimitive(now)
                }
                SyncChange.HistoryCleared -> {
                    pending.keys.removeAll { it.startsWith("h/") || it.startsWith("hx/") }
                    pending["h"] = JsonNull
                    pending["hclear"] = JsonPrimitive(now)
                }
                is SyncChange.FavAdded -> {
                    val k = key(change.f.kind, change.f.id)
                    pending["f/$k"] = json.encodeToJsonElement(Fav.serializer(), change.f.copy(accountId = ""))
                    pending["fx/$k"] = JsonNull
                }
                is SyncChange.FavRemoved -> {
                    val k = key(change.kind, change.id)
                    pending["f/$k"] = JsonNull
                    pending["fx/$k"] = JsonPrimitive(now)
                }
            }
        }
        // les favoris partent vite, la progression de lecture est regroupée
        val delayMs = if (change is SyncChange.FavAdded || change is SyncChange.FavRemoved) 2000L else PUSH_DELAY
        if (pushJob?.isActive != true) {
            pushJob = scope.launch {
                delay(delayMs)
                flush()
            }
        }
    }

    private fun url(path: String = "") = DATABASE.trimEnd('/') + "/sync/$syncId$path.json"

    suspend fun flush() = lock.withLock {
        val id = syncId ?: return@withLock
        if (!enabled) return@withLock
        val batch = synchronized(pending) {
            if (pending.isEmpty()) return@withLock
            val copy = JsonObject(LinkedHashMap(pending))
            pending.clear()
            copy
        }
        try {
            withContext(Dispatchers.IO) {
                val body = batch.toString().toRequestBody("application/json".toMediaType())
                Http.client.newCall(Request.Builder().url(url()).patch(body).build()).execute().use { r ->
                    if (!r.isSuccessful) throw java.io.IOException("HTTP ${r.code}")
                }
            }
            lastSync = System.currentTimeMillis()
        } catch (e: Exception) {
            // hors ligne : on remet en attente pour le prochain essai
            synchronized(pending) { batch.forEach { (k, v) -> pending.putIfAbsent(k, v) } }
        }
        Unit
    }

    suspend fun pull() {
        val id = syncId ?: return
        val acc = accountId ?: return
        if (!enabled) return
        flush()
        val root = try {
            withContext(Dispatchers.IO) {
                Http.client.newCall(Request.Builder().url(url()).build()).execute().use { r ->
                    if (!r.isSuccessful) return@withContext null
                    val text = r.body?.string().orEmpty()
                    runCatching { json.parseToJsonElement(text) }.getOrNull() as? JsonObject
                }
            }
        } catch (e: Exception) {
            null
        } ?: return
        if (syncId != id) return

        val hist = root.objOf("h")?.mapNotNull { (_, v) ->
            runCatching { json.decodeFromJsonElement(History.serializer(), v) }.getOrNull()
        }.orEmpty()
        val removedH = HashMap<String, Long>()
        root.objOf("hx")?.forEach { (k, v) -> (v as? JsonPrimitive)?.longOrNull?.let { removedH[k] = it } }
        root.l("hclear")?.let { cleared ->
            storage.history.filter { it.accountId == acc && it.updatedAt <= cleared }
                .forEach { removedH[key(it.kind, it.id)] = cleared }
        }
        val favs = root.objOf("f")?.mapNotNull { (_, v) ->
            runCatching { json.decodeFromJsonElement(Fav.serializer(), v) }.getOrNull()
        }.orEmpty()
        val removedF = HashMap<String, Long>()
        root.objOf("fx")?.forEach { (k, v) -> (v as? JsonPrimitive)?.longOrNull?.let { removedF[k] = it } }

        withContext(Dispatchers.Main) {
            storage.mergeRemote(acc, hist, removedH, favs, removedF)
        }
        // Ce que cet appareil a de plus récent (ou que le cloud n'a pas encore) part à son tour
        val remoteH = hist.associateBy { key(it.kind, it.id) }
        val remoteF = favs.associateBy { key(it.kind, it.id) }
        var queued = false
        synchronized(pending) {
            storage.history.filter { it.accountId == acc && it.kind != Kind.LIVE }.forEach { h ->
                val k = key(h.kind, h.id)
                val r = remoteH[k]
                if (r == null || h.updatedAt > r.updatedAt) {
                    pending["h/$k"] = json.encodeToJsonElement(History.serializer(), h.copy(accountId = ""))
                    queued = true
                }
            }
            storage.favorites.filter { it.accountId == acc }.forEach { f ->
                val k = key(f.kind, f.id)
                if (!remoteF.containsKey(k)) {
                    pending["f/$k"] = json.encodeToJsonElement(Fav.serializer(), f.copy(accountId = ""))
                    queued = true
                }
            }
        }
        if (queued) flush()
        lastSync = System.currentTimeMillis()
    }
}
