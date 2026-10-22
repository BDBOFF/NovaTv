package com.nova.tv.ui.screens

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.nova.tv.AppViewModel
import com.nova.tv.data.CastMember
import com.nova.tv.data.Details
import com.nova.tv.data.Episode
import com.nova.tv.data.Kind
import com.nova.tv.data.SeriesContent
import com.nova.tv.ui.Nova
import com.nova.tv.ui.Rail
import com.nova.tv.ui.RailStyle
import com.nova.tv.ui.Type
import com.nova.tv.ui.components.CardSize
import com.nova.tv.ui.components.EdgeScroll
import com.nova.tv.ui.components.FocusBox
import com.nova.tv.ui.components.LocalFocusMemory
import com.nova.tv.ui.components.LocalScreenKey
import com.nova.tv.ui.components.NetImage
import com.nova.tv.ui.components.NovaButton
import com.nova.tv.ui.components.PlayGlyph
import com.nova.tv.ui.components.ProgressLine
import com.nova.tv.ui.components.RailRow
import com.nova.tv.ui.components.formatRuntime
import kotlinx.coroutines.delay

// ============================================================================ Commun

@Composable
private fun DetailBackground(url: String?) {
    Box(Modifier.fillMaxSize()) {
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter,
                modifier = Modifier.align(Alignment.TopEnd).fillMaxWidth(0.78f).fillMaxHeight(0.85f),
            )
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    0f to Nova.Bg, 0.28f to Nova.Bg.copy(alpha = 0.95f), 0.6f to Nova.Bg.copy(alpha = 0.3f), 1f to Color.Transparent,
                )
            )
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(0f to Color.Transparent, 0.55f to Nova.Bg.copy(alpha = 0.6f), 0.85f to Nova.Bg)
            )
        )
    }
}

@Composable
private fun TitleBlock(title: String, d: Details?) {
    val logo = d?.logo
    if (logo != null) {
        AsyncImage(
            model = logo,
            contentDescription = title,
            contentScale = ContentScale.Fit,
            alignment = Alignment.BottomStart,
            modifier = Modifier.height(96.dp).heightIn(max = 96.dp).widthIn(max = 380.dp),
        )
    } else {
        Text(title, style = Type.Display, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun InfoBlock(d: Details?, loading: Boolean) {
    Spacer(Modifier.height(12.dp))
    MetaLine(d, null)
    Spacer(Modifier.height(12.dp))
    when {
        d?.plot != null -> Text(
            d.plot, style = Type.Body, color = Nova.Text.copy(alpha = 0.85f),
            maxLines = 4, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(560.dp),
        )
        loading -> CircularProgressIndicator(color = Nova.Accent, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
    }
    val credits = buildList {
        d?.director?.let { add("Réalisation : $it") }
        d?.cast?.take(4)?.joinToString(", ") { it.name }?.takeIf { it.isNotBlank() }?.let { add("Avec : $it") }
    }
    if (credits.isNotEmpty()) {
        Spacer(Modifier.height(10.dp))
        credits.forEach {
            Text(it, style = Type.Small, color = Nova.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(560.dp))
        }
    }
}

@Composable
private fun CastRow(cast: List<CastMember>) {
    if (cast.isEmpty()) return
    Column {
        Text("Distribution", style = Type.H2, modifier = Modifier.padding(start = 48.dp, bottom = 10.dp))
        EdgeScroll {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 48.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(cast, key = { it.name + (it.role ?: "") }) { c ->
                    Column(Modifier.width(92.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        FocusBox(onClick = {}, shape = CircleShape, modifier = Modifier.size(76.dp), borderWidth = 2.dp) { _ ->
                            NetImage(c.photo, Modifier.fillMaxSize(), fallbackText = c.name.split(" ").mapNotNull { it.firstOrNull()?.toString() }.take(2).joinToString(""))
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(c.name, style = Type.Small, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                        c.role?.let { Text(it, style = Type.Small, color = Nova.Dim, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center) }
                    }
                }
            }
        }
    }
}

fun openTrailer(context: Context, key: String) {
    val id = if (key.contains("v=")) key.substringAfter("v=").substringBefore("&") else key.substringAfterLast("/")
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube:$id")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: ActivityNotFoundException) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=$id")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e2: ActivityNotFoundException) {
            Toast.makeText(context, "Aucune application YouTube installée", Toast.LENGTH_SHORT).show()
        }
    }
}

@Composable
fun RequestInitialFocus(screen: String, requester: FocusRequester) {
    val memory = LocalFocusMemory.current
    LaunchedEffect(Unit) {
        val restoring = screen in memory.pending
        delay(120)
        if (!restoring) {
            runCatching { requester.requestFocus() }
            return@LaunchedEffect
        }
        delay(400)
        if (screen in memory.pending) {
            memory.pending.remove(screen)
            runCatching { requester.requestFocus() }
        }
    }
}

// ============================================================================ Film

@Composable
fun MovieDetailScreen(vm: AppViewModel, id: Int) {
    val movie = vm.moviesById[id]
    if (movie == null) {
        EmptyState("Film introuvable", "Il a peut-être été retiré du catalogue."); return
    }
    val screen = "movie_$id"
    val context = LocalContext.current
    val d by produceState(vm.cachedDetails(Kind.MOVIE, id), id) {
        if (value == null) value = runCatching { vm.movieDetails(movie) }.getOrNull()
    }
    val loading = d == null
    val resume = vm.resumeOf(Kind.MOVIE, id)?.takeIf { it.progress in 0.02f..0.95f }
    val fav = vm.isFav(Kind.MOVIE, id)
    val playRequester = remember { FocusRequester() }
    val similar = remember(id, vm.movies) {
        vm.visibleMovies().filter { it.categoryId == movie.categoryId && it.id != id }
            .sortedByDescending { it.added }.take(20).map { vm.movieTile(it) }
    }

    CompositionLocalProvider(LocalScreenKey provides screen) {
        RequestInitialFocus(screen, playRequester)
        Box(Modifier.fillMaxSize()) {
            DetailBackground(d?.backdrop ?: movie.poster)
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                Column(Modifier.padding(start = 48.dp, top = 56.dp, end = 48.dp)) {
                    TitleBlock(movie.title, d)
                    InfoBlock(d, loading)
                    Spacer(Modifier.height(22.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        NovaButton(
                            if (resume != null) "Reprendre" else "Lecture",
                            onClick = { vm.playMovie(movie) },
                            glyph = { c -> PlayGlyph(c, 13.dp) },
                            primary = true,
                            focusRequester = playRequester,
                            memoryKey = "play",
                        )
                        if (resume != null) {
                            NovaButton("Depuis le début", onClick = { vm.playMovie(movie, fromStart = true) }, icon = Icons.Filled.Refresh, memoryKey = "restart")
                        }
                        NovaButton(
                            if (fav) "Dans ma liste" else "Ma liste",
                            onClick = { vm.toggleFav(Kind.MOVIE, id, movie.title, movie.poster) },
                            icon = if (fav) Icons.Filled.Check else Icons.Filled.Add,
                            memoryKey = "fav",
                        )
                        d?.trailer?.let { t ->
                            NovaButton("Bande-annonce", onClick = { openTrailer(context, t) }, icon = Icons.Filled.Star, memoryKey = "trailer")
                        }
                    }
                    if (resume != null) {
                        Spacer(Modifier.height(14.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ProgressLine(resume.progress, Modifier.width(220.dp))
                            Spacer(Modifier.width(10.dp))
                            val left = ((resume.duration - resume.position) / 60000).coerceAtLeast(1)
                            Text("Il reste $left min", style = Type.Small, color = Nova.Muted)
                        }
                    }
                }
                Spacer(Modifier.height(40.dp))
                CastRow(d?.cast.orEmpty())
                if (similar.isNotEmpty()) {
                    Spacer(Modifier.height(20.dp))
                    RailRow(
                        Rail("similar", "Dans la même catégorie", RailStyle.POSTER, similar),
                        onTileClick = { openTile(vm, it) },
                    )
                }
                Spacer(Modifier.height(60.dp))
            }
        }
    }
}

// ============================================================================ Série

@Composable
fun SeriesDetailScreen(vm: AppViewModel, id: Int) {
    val series = vm.seriesById[id]
    if (series == null) {
        EmptyState("Série introuvable", "Elle a peut-être été retirée du catalogue."); return
    }
    val screen = "series_$id"
    val context = LocalContext.current
    val d by produceState(vm.cachedDetails(Kind.SERIES, id), id) {
        if (value == null) value = runCatching { vm.seriesDetails(series) }.getOrNull()
    }
    val content by produceState<SeriesContent?>(null, id) {
        value = runCatching { vm.seriesContent(series) }.getOrNull() ?: SeriesContent(emptyList(), emptyMap())
    }
    val last = vm.lastEpisodeOf(id)
    val fav = vm.isFav(Kind.SERIES, id)
    val playRequester = remember { FocusRequester() }
    var season by rememberSaveable(id) { mutableIntStateOf(-1) }
    var focusedEp by remember { mutableStateOf<Episode?>(null) }

    val c = content
    val all = c?.all.orEmpty()
    // Épisode à lancer avec le bouton principal
    val target: Episode? = remember(c, last) {
        if (c == null) null else {
            val idx = last?.let { h -> all.indexOfFirst { it.id == h.id } } ?: -1
            when {
                idx < 0 -> all.firstOrNull()
                last != null && last.progress > 0.94f -> all.getOrNull(idx + 1) ?: all[idx]
                else -> all[idx]
            }
        }
    }
    LaunchedEffect(c) {
        if (c != null && season == -1) season = target?.season ?: c.seasons.firstOrNull() ?: 1
    }
    val episodes = c?.episodes?.get(season).orEmpty()

    CompositionLocalProvider(LocalScreenKey provides screen) {
        RequestInitialFocus(screen, playRequester)
        Box(Modifier.fillMaxSize()) {
            DetailBackground(d?.backdrop ?: series.backdrop ?: series.cover)
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                Column(Modifier.padding(start = 48.dp, top = 56.dp, end = 48.dp)) {
                    TitleBlock(series.title, d)
                    InfoBlock(d, d == null)
                    if (c != null && c.seasons.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "${c.seasons.size} saison${if (c.seasons.size > 1) "s" else ""} · ${all.size} épisodes",
                            style = Type.Small, color = Nova.Muted,
                        )
                    }
                    Spacer(Modifier.height(22.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        val resumeSame = last != null && target != null && last.id == target.id && last.progress < 0.94f
                        val label = when {
                            target == null -> "Lecture"
                            resumeSame -> "Reprendre S${target.season} É${target.number}"
                            else -> "Lecture S${target.season} É${target.number}"
                        }
                        NovaButton(
                            label,
                            onClick = { if (c != null && target != null) vm.playEpisode(series, c, target) },
                            glyph = { col -> PlayGlyph(col, 13.dp) },
                            primary = true,
                            focusRequester = playRequester,
                            memoryKey = "play",
                        )
                        NovaButton(
                            if (fav) "Dans ma liste" else "Ma liste",
                            onClick = { vm.toggleFav(Kind.SERIES, id, series.title, series.cover) },
                            icon = if (fav) Icons.Filled.Check else Icons.Filled.Add,
                            memoryKey = "fav",
                        )
                        d?.trailer?.let { t ->
                            NovaButton("Bande-annonce", onClick = { openTrailer(context, t) }, icon = Icons.Filled.Star, memoryKey = "trailer")
                        }
                    }
                }
                Spacer(Modifier.height(30.dp))

                if (c == null) {
                    Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Nova.Accent, strokeWidth = 2.dp, modifier = Modifier.size(26.dp))
                    }
                } else if (c.seasons.isNotEmpty()) {
                    // ---------------------------------------------- Saisons
                    EdgeScroll {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 48.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(c.seasons, key = { it }) { s ->
                                Chip("Saison $s", s == season) { season = s }
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    // ---------------------------------------------- Épisodes
                    EdgeScroll {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 48.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            items(episodes, key = { it.id }) { ep ->
                                val h = vm.resumeOf(Kind.EPISODE, ep.id)
                                EpisodeCard(
                                    ep, h?.progress, series.cover,
                                    onClick = { vm.playEpisode(series, c, ep) },
                                    onFocused = { focusedEp = ep },
                                )
                            }
                        }
                    }
                    val fe = focusedEp?.takeIf { it.season == season }
                    Column(Modifier.padding(horizontal = 48.dp).height(58.dp)) {
                        if (fe != null) {
                            Text("É${fe.number} · ${fe.title}", style = Type.H3, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            fe.plot?.let {
                                Text(it, style = Type.Small, color = Nova.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(640.dp))
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                CastRow(d?.cast.orEmpty())
                Spacer(Modifier.height(60.dp))
            }
        }
    }
}

@Composable
private fun EpisodeCard(ep: Episode, progress: Float?, fallback: String?, onClick: () -> Unit, onFocused: () -> Unit) {
    Column(Modifier.width(CardSize.LandW)) {
        FocusBox(
            onClick = onClick,
            onFocused = onFocused,
            memoryKey = "ep_${ep.id}",
            modifier = Modifier.width(CardSize.LandW).height(CardSize.LandH),
        ) { focused ->
            NetImage(ep.image ?: fallback, Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.5f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.8f))))
            Box(
                Modifier.align(Alignment.TopStart).padding(8.dp).clip(RoundedCornerShape(6.dp))
                    .background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 8.dp, vertical = 4.dp)
            ) { Text("É${ep.number}", style = Type.Label, color = Color.White) }
            if (focused) {
                Box(
                    Modifier.align(Alignment.Center).size(38.dp).clip(CircleShape).background(Nova.Accent),
                    contentAlignment = Alignment.Center,
                ) { Box(Modifier.padding(start = 3.dp)) { PlayGlyph(Color.White, 13.dp) } }
            }
            Column(Modifier.align(Alignment.BottomStart).padding(10.dp)) {
                Text(ep.title, style = Type.H3, maxLines = 1, overflow = TextOverflow.Ellipsis)
                formatRuntime(ep.durationSec?.div(60))?.let { Text(it, style = Type.Small, color = Nova.Muted) }
                if (progress != null && progress > 0.01f) {
                    Spacer(Modifier.height(5.dp))
                    ProgressLine(progress, Modifier.fillMaxWidth())
                }
            }
        }
    }
}

// ============================================================================ Titre TMDB (tendances, au cinéma)

@Composable
fun TmdbDetailScreen(vm: AppViewModel, kind: String, id: Int) {
    val screen = "tmdb_${kind}_$id"
    val context = LocalContext.current
    val item = vm.tmdbItem(kind, id)
    val fav = vm.storage.favorites.firstOrNull { it.kind == kind && it.id == id && it.accountId == vm.storage.activeId }
    val d by produceState(vm.cachedDetails(kind, id), kind, id) {
        if (value == null) value = vm.tmdbDetails(kind, id)
    }
    val details = d
    val shown: Details? = details ?: item?.let {
        Details(
            title = it.title, logo = null, plot = it.overview, backdrop = it.backdrop, poster = it.poster,
            year = it.year, genres = emptyList(), rating = it.rating, runtimeMin = null, director = null,
            cast = emptyList(), trailer = null,
        )
    }
    val title = shown?.title?.takeIf { it.isNotBlank() } ?: fav?.title ?: ""
    val year = shown?.year ?: fav?.year
    val available = remember(vm.movies, vm.series, title, year) { vm.resolveTmdb(kind, id, title, year) }
    val isFav = vm.isFav(kind, id)
    val primary = remember { FocusRequester() }
    val poster = item?.poster ?: shown?.poster ?: fav?.image

    CompositionLocalProvider(LocalScreenKey provides screen) {
        RequestInitialFocus(screen, primary)
        Box(Modifier.fillMaxSize()) {
            DetailBackground(shown?.backdrop ?: poster)
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                Column(Modifier.padding(start = 48.dp, top = 56.dp, end = 48.dp)) {
                    TitleBlock(title, shown)
                    InfoBlock(shown, details == null && vm.tmdb() != null)
                    Spacer(Modifier.height(18.dp))
                    if (available != null) {
                        com.nova.tv.ui.components.Badge("DISPONIBLE DANS VOTRE CATALOGUE")
                    } else {
                        com.nova.tv.ui.components.Badge("PAS ENCORE DANS VOTRE CATALOGUE", filled = false)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Ajoutez-le à vos favoris : il apparaîtra comme disponible dès que votre fournisseur l'ajoutera.",
                            style = Type.Small, color = Nova.Muted,
                        )
                    }
                    Spacer(Modifier.height(18.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (available != null) {
                            NovaButton(
                                "Regarder",
                                onClick = { openTile(vm, available) },
                                glyph = { c -> PlayGlyph(c, 13.dp) },
                                primary = true,
                                focusRequester = primary,
                                memoryKey = "watch",
                            )
                        }
                        NovaButton(
                            if (isFav) "Dans mes favoris" else "Ajouter aux favoris",
                            onClick = { vm.toggleFav(kind, id, title, poster, year) },
                            icon = if (isFav) Icons.Filled.Check else Icons.Filled.Add,
                            primary = available == null,
                            focusRequester = if (available == null) primary else null,
                            memoryKey = "fav",
                        )
                        shown?.trailer?.let { t ->
                            NovaButton("Bande-annonce", onClick = { openTrailer(context, t) }, icon = Icons.Filled.Star, memoryKey = "trailer")
                        }
                    }
                }
                Spacer(Modifier.height(40.dp))
                CastRow(shown?.cast.orEmpty())
                Spacer(Modifier.height(60.dp))
            }
        }
    }
}
