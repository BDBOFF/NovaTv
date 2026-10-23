package com.nova.tv.data

/** Une source de contenu : serveur Xtream Codes ou playlist M3U. */
interface Source {
    val account: Account
    var timezone: String?

    suspend fun login(): AccountInfo
    fun clearCache()

    suspend fun liveCategories(force: Boolean): List<Category>
    suspend fun vodCategories(force: Boolean): List<Category>
    suspend fun seriesCategories(force: Boolean): List<Category>
    suspend fun liveStreams(force: Boolean): List<Channel>
    suspend fun vodStreams(force: Boolean): List<Movie>
    suspend fun series(force: Boolean): List<Series>

    suspend fun vodInfo(id: Int): XtVodInfo
    suspend fun seriesInfo(id: Int): XtSeriesInfo

    suspend fun shortEpg(streamId: Int, limit: Int = 12): List<EpgProgram>
    suspend fun fullEpg(streamId: Int): List<EpgProgram>

    fun liveUrl(ch: Channel, format: String): String
    fun movieUrl(m: Movie): String
    fun episodeUrl(id: Int, ext: String): String
    fun catchupUrl(ch: Channel, p: EpgProgram): String
}
