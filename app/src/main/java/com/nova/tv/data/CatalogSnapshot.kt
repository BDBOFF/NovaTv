package com.nova.tv.data

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.security.MessageDigest

/**
 * Copie compacte (binaire) du catalogue déjà chargé.
 * Au lancement, on la relit en une fraction de seconde au lieu de re-télécharger
 * et re-décoder des dizaines de Mo de JSON : l'accueil s'affiche tout de suite,
 * et la mise à jour du catalogue se fait ensuite en arrière-plan.
 */
class CatalogSnapshot(cacheRoot: File, account: Account) {

    class Data(
        val savedAt: Long,
        val liveCategories: List<Category>,
        val vodCategories: List<Category>,
        val seriesCategories: List<Category>,
        val channels: List<Channel>,
        val movies: List<Movie>,
        val series: List<Series>,
    )

    private val file: File = run {
        val key = account.type + "|" + normalizeServer(account.server) + "|" + account.username + "|" + account.password
        val h = MessageDigest.getInstance("SHA-1").digest(key.toByteArray()).joinToString("") { "%02x".format(it) }
        File(File(cacheRoot, "snapshot").apply { mkdirs() }, "$h.bin")
    }

    fun exists() = file.exists()

    fun delete() {
        runCatching { file.delete() }
    }

    fun read(): Data? = runCatching {
        DataInputStream(BufferedInputStream(file.inputStream(), 1 shl 16)).use { r ->
            if (r.readInt() != VERSION) return null
            val saved = r.readLong()
            fun cats(): List<Category> {
                val n = r.readInt()
                val out = ArrayList<Category>(n)
                repeat(n) { out.add(Category(r.readUTF(), r.readUTF())) }
                return out
            }
            fun str(): String? = if (r.readBoolean()) r.readUTF() else null
            fun dbl(): Double? = if (r.readBoolean()) r.readDouble() else null
            val lc = cats()
            val vc = cats()
            val sc = cats()
            val nc = r.readInt()
            val channels = ArrayList<Channel>(nc)
            repeat(nc) {
                channels.add(
                    Channel(
                        id = r.readInt(), num = r.readInt(), name = r.readUTF(), displayName = r.readUTF(),
                        quality = str(), logo = str(), epgId = str(), categoryId = str(),
                        archive = r.readBoolean(), archiveDays = r.readInt(),
                    )
                )
            }
            val nm = r.readInt()
            val movies = ArrayList<Movie>(nm)
            repeat(nm) {
                movies.add(
                    Movie(
                        id = r.readInt(), name = r.readUTF(), title = r.readUTF(), year = str(), poster = str(),
                        rating = dbl(), added = r.readLong(), categoryId = str(), ext = r.readUTF(), tmdbId = str(),
                    )
                )
            }
            val ns = r.readInt()
            val series = ArrayList<Series>(ns)
            repeat(ns) {
                series.add(
                    Series(
                        id = r.readInt(), name = r.readUTF(), title = r.readUTF(), year = str(), cover = str(),
                        plot = str(), genre = str(), rating = dbl(), lastModified = r.readLong(),
                        categoryId = str(), backdrop = str(), tmdbId = str(),
                    )
                )
            }
            Data(saved, lc, vc, sc, channels, movies, series)
        }
    }.getOrNull()

    fun write(d: Data) {
        runCatching {
            val tmp = File(file.parentFile, file.name + ".tmp")
            DataOutputStream(BufferedOutputStream(tmp.outputStream(), 1 shl 16)).use { w ->
                // writeUTF est limité à 64 Ko : on coupe les textes très longs
                fun utf(s: String) = w.writeUTF(if (s.length > 4000) s.take(4000) else s)
                fun str(s: String?) {
                    w.writeBoolean(s != null)
                    if (s != null) utf(s)
                }
                fun dbl(v: Double?) {
                    w.writeBoolean(v != null)
                    if (v != null) w.writeDouble(v)
                }
                fun cats(l: List<Category>) {
                    w.writeInt(l.size)
                    l.forEach { utf(it.id); utf(it.name) }
                }
                w.writeInt(VERSION)
                w.writeLong(d.savedAt)
                cats(d.liveCategories); cats(d.vodCategories); cats(d.seriesCategories)
                w.writeInt(d.channels.size)
                d.channels.forEach { c ->
                    w.writeInt(c.id); w.writeInt(c.num); utf(c.name); utf(c.displayName)
                    str(c.quality); str(c.logo); str(c.epgId); str(c.categoryId)
                    w.writeBoolean(c.archive); w.writeInt(c.archiveDays)
                }
                w.writeInt(d.movies.size)
                d.movies.forEach { m ->
                    w.writeInt(m.id); utf(m.name); utf(m.title); str(m.year); str(m.poster)
                    dbl(m.rating); w.writeLong(m.added); str(m.categoryId); utf(m.ext); str(m.tmdbId)
                }
                w.writeInt(d.series.size)
                d.series.forEach { s ->
                    w.writeInt(s.id); utf(s.name); utf(s.title); str(s.year); str(s.cover)
                    str(s.plot?.let { if (it.length > 600) it.take(600) else it }); str(s.genre); dbl(s.rating)
                    w.writeLong(s.lastModified); str(s.categoryId); str(s.backdrop); str(s.tmdbId)
                }
            }
            if (!tmp.renameTo(file)) {
                file.delete()
                tmp.renameTo(file)
            }
        }
    }

    companion object {
        private const val VERSION = 1
    }
}
