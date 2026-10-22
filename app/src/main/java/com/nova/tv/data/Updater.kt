package com.nova.tv.data

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.nova.tv.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.Request
import java.io.File
import java.io.IOException

data class UpdateInfo(val version: Int, val url: String)

/** Mise à jour intégrée : vérifie la dernière version publiée sur GitHub et l'installe. */
object Updater {
    private const val REPO = "BDBOFF/NovaTv"
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun check(): UpdateInfo? = withContext(Dispatchers.IO) {
        runCatching {
            val body = Http.get(
                "https://api.github.com/repos/$REPO/releases/latest",
                mapOf("Accept" to "application/vnd.github+json"),
            )
            val o = json.parseToJsonElement(body).obj() ?: return@runCatching null
            val version = o.s("tag_name")?.substringAfter("build-")?.toIntOrNull() ?: return@runCatching null
            val url = o["assets"].items().mapNotNull { it.obj() }
                .firstOrNull { it.s("name")?.endsWith(".apk") == true }
                ?.s("browser_download_url") ?: return@runCatching null
            if (version > BuildConfig.VERSION_CODE) UpdateInfo(version, url) else null
        }.getOrNull()
    }

    suspend fun download(context: Context, info: UpdateInfo, onProgress: (Float) -> Unit): File =
        withContext(Dispatchers.IO) {
            val dir = if (Build.VERSION.SDK_INT >= 24) File(context.cacheDir, "apk")
            else File(context.externalCacheDir ?: context.cacheDir, "apk")
            dir.mkdirs()
            dir.listFiles()?.forEach { it.delete() }
            val file = File(dir, "SamothTV-${info.version}.apk")
            Http.client.newCall(Request.Builder().url(info.url).build()).execute().use { r ->
                if (!r.isSuccessful) throw IOException("Téléchargement impossible (HTTP ${r.code})")
                val body = r.body ?: throw IOException("Réponse vide")
                val total = body.contentLength()
                body.byteStream().use { input ->
                    file.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        var done = 0L
                        var lastReport = 0L
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            done += n
                            if (total > 0 && done - lastReport > 256 * 1024) {
                                lastReport = done
                                onProgress(done.toFloat() / total)
                            }
                        }
                    }
                }
            }
            onProgress(1f)
            file
        }

    /** Renvoie false si l'utilisateur doit d'abord autoriser l'installation (écran ouvert). */
    fun install(context: Context, file: File): Boolean {
        if (Build.VERSION.SDK_INT >= 26 && !context.packageManager.canRequestPackageInstalls()) {
            try {
                context.startActivity(
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (e: ActivityNotFoundException) {
                // ignoré : certaines box n'ont pas cet écran
            }
            return false
        }
        val uri = if (Build.VERSION.SDK_INT >= 24) {
            FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        } else {
            file.setReadable(true, false)
            Uri.fromFile(file)
        }
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        return true
    }
}
