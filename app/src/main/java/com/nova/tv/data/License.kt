package com.nova.tv.data

import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.FormBody
import okhttp3.Request
import java.security.MessageDigest

/** Offre affichée dans l'écran d'abonnement. */
data class Offer(val id: String, val label: String, val price: String, val detail: String, val highlight: Boolean = false)

/**
 * Licences SamothTV : essai gratuit de 3 jours, puis clé achetée sur le site (Lemon Squeezy).
 * Les clés sont vérifiées directement auprès de Lemon Squeezy, sans serveur à nous.
 */
class LicenseManager(private val context: Context) {

    companion object {
        const val TRIAL_DAYS = 3
        /** Page d'achat (à remplacer par l'adresse définitive du site). */
        const val BUY_URL = "https://bdboff.github.io/samothtv/#tarifs"
        /** Identifiant de la boutique Lemon Squeezy (0 = pas de contrôle tant qu'elle n'existe pas). */
        const val STORE_ID = 0
        private const val API = "https://api.lemonsqueezy.com/v1/licenses"
        /** Re-vérification en ligne tous les 3 jours, tolérance hors ligne de 14 jours. */
        private const val CHECK_EVERY = 3L * 24 * 3600 * 1000
        private const val OFFLINE_GRACE = 14L * 24 * 3600 * 1000
        /** Empreinte du code propriétaire (le code lui-même n'est pas dans le code source). */
        private const val OWNER_HASH = "cbe6f742feff6099e9c2debabfb95ffdea09930e3d623e91280f7ac8bbe8ac0f"

        val offers = listOf(
            Offer("1m", "1 mois", "2,99 €", "Sans engagement"),
            Offer("3m", "3 mois", "6,99 €", "soit 2,33 € / mois"),
            Offer("6m", "6 mois", "11,99 €", "soit 2,00 € / mois"),
            Offer("1y", "1 an", "19,99 €", "soit 1,67 € / mois", highlight = true),
            Offer("life", "À vie", "34,99 €", "Paiement unique"),
        )
    }

    enum class State { TRIAL, ACTIVE, EXPIRED }

    private val prefs = context.getSharedPreferences("samoth_license", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    var state by mutableStateOf(State.TRIAL)
        private set
    var trialDaysLeft by mutableStateOf(TRIAL_DAYS)
        private set
    var key by mutableStateOf(prefs.getString("key", null))
        private set
    var plan by mutableStateOf(prefs.getString("plan", null))
        private set
    var expiresAt by mutableStateOf(prefs.getLong("expires", 0L).takeIf { it > 0 })
        private set
    val isOwner: Boolean get() = prefs.getBoolean("owner", false)

    init {
        if (!prefs.contains("trialStart")) prefs.edit().putLong("trialStart", System.currentTimeMillis()).apply()
        refresh()
    }

    /** Recalcule l'état à partir de ce qui est enregistré (sans réseau). */
    fun refresh() {
        val now = System.currentTimeMillis()
        if (isOwner) {
            state = State.ACTIVE; return
        }
        if (key != null) {
            val lastOk = prefs.getLong("lastOk", 0L)
            val exp = expiresAt
            val revoked = prefs.getBoolean("revoked", false)
            state = when {
                revoked -> State.EXPIRED
                exp != null && now > exp -> State.EXPIRED
                now - lastOk > OFFLINE_GRACE -> State.EXPIRED
                else -> State.ACTIVE
            }
            if (state == State.ACTIVE) return
        }
        val start = prefs.getLong("trialStart", now)
        val end = start + TRIAL_DAYS * 24L * 3600 * 1000
        val leftMs = end - now
        trialDaysLeft = if (leftMs <= 0) 0 else ((leftMs + 24L * 3600 * 1000 - 1) / (24L * 3600 * 1000)).toInt()
        if (key == null || state != State.EXPIRED) state = if (leftMs > 0) State.TRIAL else State.EXPIRED
    }

    private fun sha256(s: String): String =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    private fun deviceName(): String {
        val id = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "inconnu"
        return "${Build.MANUFACTURER} ${Build.MODEL} ($id)".take(190)
    }

    private suspend fun post(path: String, form: Map<String, String>): JsonObject? = withContext(Dispatchers.IO) {
        val body = FormBody.Builder().apply { form.forEach { (k, v) -> add(k, v) } }.build()
        val req = Request.Builder().url("$API/$path").post(body).header("Accept", "application/json").build()
        Http.client.newCall(req).execute().use { r ->
            val text = r.body?.string().orEmpty()
            runCatching { json.parseToJsonElement(text).obj() }.getOrNull()
        }
    }

    private fun readExpiry(o: JsonObject): Long? {
        val s = o.objOf("license_key")?.s("expires_at") ?: return null
        return runCatching {
            val f = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
            f.timeZone = java.util.TimeZone.getTimeZone("UTC")
            f.parse(s.take(19))?.time
        }.getOrNull()
    }

    private fun readPlan(o: JsonObject): String? = o.objOf("meta")?.let { m ->
        listOfNotNull(m.s("product_name"), m.s("variant_name")?.takeIf { it != "Default" }).joinToString(" · ").ifBlank { null }
    }

    /** Active une clé sur cet appareil. Renvoie un message d'erreur, ou null si c'est bon. */
    suspend fun activate(input: String): String? {
        val code = input.trim()
        if (code.isEmpty()) return "Saisissez votre clé."
        if (sha256(code.uppercase()) == OWNER_HASH) {
            prefs.edit().putBoolean("owner", true).apply()
            plan = "Propriétaire"
            refresh()
            return null
        }
        return try {
            val o = post("activate", mapOf("license_key" to code, "instance_name" to deviceName()))
                ?: return "Réponse illisible du serveur de licences."
            val ok = o["activated"]?.toString() == "true"
            if (!ok) {
                val err = o.s("error") ?: "Clé refusée."
                return when {
                    err.contains("activation limit", true) -> "Cette clé est déjà utilisée sur le nombre maximal d'appareils. Désactivez-la sur un autre appareil."
                    err.contains("expired", true) -> "Cette clé a expiré. Renouvelez votre offre sur le site."
                    err.contains("not found", true) || err.contains("invalid", true) -> "Clé inconnue. Vérifiez qu'elle est bien recopiée."
                    else -> err
                }
            }
            if (STORE_ID != 0 && o.objOf("meta")?.i("store_id") != STORE_ID) return "Cette clé ne correspond pas à SamothTV."
            val instance = o.objOf("instance")?.s("id")
            val exp = readExpiry(o)
            prefs.edit()
                .putString("key", code).putString("instance", instance)
                .putLong("expires", exp ?: 0L).putLong("lastOk", System.currentTimeMillis())
                .putBoolean("revoked", false).putString("plan", readPlan(o))
                .apply()
            key = code; expiresAt = exp; plan = readPlan(o)
            refresh()
            null
        } catch (e: Exception) {
            "Connexion impossible au serveur de licences. Vérifiez Internet et réessayez."
        }
    }

    /** Re-vérification périodique en arrière-plan. */
    suspend fun revalidateIfNeeded(force: Boolean = false) {
        val k = key ?: return
        if (isOwner) return
        val last = prefs.getLong("lastOk", 0L)
        if (!force && System.currentTimeMillis() - last < CHECK_EVERY) return
        try {
            val form = buildMap {
                put("license_key", k)
                prefs.getString("instance", null)?.let { put("instance_id", it) }
            }
            val o = post("validate", form) ?: return
            val valid = o["valid"]?.toString() == "true"
            val status = o.objOf("license_key")?.s("status")
            val exp = readExpiry(o)
            if (valid && status != "expired" && status != "disabled") {
                prefs.edit().putLong("lastOk", System.currentTimeMillis()).putLong("expires", exp ?: 0L).putBoolean("revoked", false).apply()
                expiresAt = exp
            } else if (o.containsKey("valid")) {
                prefs.edit().putBoolean("revoked", true).apply()
            }
        } catch (_: Exception) {
            // hors ligne : on garde l'état, la tolérance de 14 jours s'applique
        }
        refresh()
    }

    /** Libère la place de cet appareil (pour utiliser la clé ailleurs). */
    suspend fun deactivate(): String? {
        val k = key ?: return null
        val inst = prefs.getString("instance", null)
        if (inst != null) {
            try {
                post("deactivate", mapOf("license_key" to k, "instance_id" to inst))
            } catch (e: Exception) {
                return "Connexion impossible. Réessayez avec Internet."
            }
        }
        prefs.edit().remove("key").remove("instance").remove("expires").remove("lastOk").remove("revoked").remove("plan").apply()
        key = null; expiresAt = null; plan = null
        refresh()
        return null
    }
}
