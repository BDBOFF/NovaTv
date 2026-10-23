package com.nova.tv.ui.screens

import com.nova.tv.ui.Gutter
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.nova.tv.AppViewModel
import com.nova.tv.data.Details
import com.nova.tv.data.ContentType
import com.nova.tv.data.Kind
import com.nova.tv.ui.Nova
import com.nova.tv.ui.Route
import com.nova.tv.ui.Scope
import com.nova.tv.ui.Tile
import com.nova.tv.ui.Type
import com.nova.tv.ui.components.NovaButton
import com.nova.tv.ui.components.PlayGlyph
import com.nova.tv.ui.components.PivotScroll
import com.nova.tv.ui.components.RailRow
import com.nova.tv.ui.components.formatRuntime
import kotlinx.coroutines.delay

/** Action commune au clic sur une tuile, partout dans l'app. */
fun openTile(vm: AppViewModel, t: Tile) {
    when (t.kind) {
        Kind.MOVIE -> vm.navigate(Route.MovieDetail(t.id))
        Kind.SERIES -> vm.navigate(Route.SeriesDetail(t.id))
        Kind.EPISODE -> t.history?.let { vm.resume(it) }
        Kind.LIVE -> t.channel?.let { ch ->
            vm.playChannel(ch, vm.channels.filter { it.categoryId == ch.categoryId })
        }
        Kind.TMDB_MOVIE, Kind.TMDB_TV -> vm.navigate(Route.TmdbDetail(t.kind, t.id))
        Kind.SAGA -> vm.navigate(Route.Saga(t.id))
        Kind.MATCH -> t.channel?.let { ch ->
            val p = t.program
            if (p == null || p.start <= System.currentTimeMillis()) {
                vm.playChannel(ch, vm.channels.filter { it.categoryId == ch.categoryId })
            } else {
                android.widget.Toast.makeText(
                    vm.getApplication<android.app.Application>(),
                    "Début à ${com.nova.tv.ui.components.formatClock(p.start)} sur ${ch.displayName}",
                    android.widget.Toast.LENGTH_LONG,
                ).show()
            }
        }
        // Genres et années : ouverts par openExplore (le périmètre dépend de la page)
        else -> {}
    }
}

/** Ouvre une tuile d'exploration (genre / année) selon la page courante. */
fun openExplore(vm: AppViewModel, t: Tile, scope: Scope): Boolean {
    when (t.kind) {
        Kind.GENRE -> vm.navigate(Route.Grid("genre", t.key.removePrefix("genre_"), scope, t.title))
        Kind.YEAR -> vm.navigate(Route.Grid("year", t.id.toString(), scope, t.title))
        else -> return false
    }
    return true
}

/** Appui long sur une tuile : ajout / retrait des favoris avec confirmation. */
fun longPressFav(vm: AppViewModel, context: android.content.Context, t: Tile) {
    vm.toggleFavTile(t)?.let { android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_SHORT).show() }
}

@Composable
fun BrowseScreen(vm: AppViewModel, scope: Scope) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val rails = remember(
        scope, vm.movies, vm.series, vm.channels, vm.storage.favorites, vm.storage.history,
        vm.trendMovies, vm.trendSeries, vm.nowPlaying, vm.storage.showAdult, vm.exploreVersion,
    ) { vm.buildRails(scope) }

    if (com.nova.tv.ui.LocalDevice.current.compact) {
        MobileBrowse(vm, scope, rails)
        return
    }

    val featured: Tile? = remember(rails) {
        rails.firstOrNull()?.tiles?.firstOrNull { it.kind != Kind.LIVE }
    }
    var focusedTile by remember { mutableStateOf<Tile?>(null) }
    var heroFocused by remember { mutableStateOf(false) }
    val current = if (heroFocused) featured else (focusedTile ?: featured)
    val playRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()

    val details by produceState<Details?>(current?.let { vm.cachedPreview(it) }, current?.key) {
        val t = current ?: return@produceState
        value = vm.cachedPreview(t)
        if (value == null) {
            delay(320)
            value = runCatching { vm.previewDetails(t) }.getOrNull()
        }
    }

    LaunchedEffect(featured != null) {
        if (scope == Scope.ALL && featured != null && !vm.initialFocusDone) {
            vm.initialFocusDone = true
            delay(120)
            runCatching { playRequester.requestFocus() }
        }
    }

    if (rails.isEmpty()) {
        EmptyState("Aucun contenu", "Votre abonnement ne contient ni films ni séries, ou le catalogue est vide.")
        return
    }

    Box(Modifier.fillMaxSize()) {
        // ------------------------------------------------------------ Fond immersif
        val backdrop = details?.backdrop ?: current?.image
        Crossfade(targetState = backdrop, animationSpec = tween(450), label = "backdrop") { url ->
            Box(Modifier.fillMaxSize()) {
                if (url != null) {
                    AsyncImage(
                        model = url,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        alignment = Alignment.TopCenter,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .fillMaxWidth(0.70f)
                            .fillMaxHeight(0.78f),
                    )
                }
            }
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    0.0f to Nova.Bg, 0.30f to Nova.Bg.copy(alpha = 0.92f), 0.55f to Nova.Bg.copy(alpha = 0.25f), 1f to Color.Transparent,
                )
            )
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0.0f to Color.Transparent, 0.45f to Nova.Bg.copy(alpha = 0.35f), 0.68f to Nova.Bg, 1f to Nova.Bg,
                )
            )
        )

        Column(Modifier.fillMaxSize()) {
            Spacer(Modifier.height(78.dp))
            // ------------------------------------------------------------ Informations
            Column(
                Modifier
                    .padding(horizontal = Gutter)
                    .widthIn(max = 470.dp)
                    .height(240.dp)
            ) {
                HeroInfo(current, details)
                val showButtons = heroFocused || focusedTile == null
                Row(
                    Modifier
                        .padding(top = 16.dp)
                        .alpha(if (showButtons) 1f else 0f)
                        .onFocusChanged { heroFocused = it.hasFocus }
                        .focusGroup(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    NovaButton(
                        "Lecture",
                        onClick = {
                            featured?.let { f ->
                                when (f.kind) {
                                    Kind.MOVIE -> vm.moviesById[f.id]?.let { vm.playMovie(it) }
                                    else -> openTile(vm, f)
                                }
                            }
                        },
                        glyph = { c -> PlayGlyph(c, 13.dp) },
                        primary = true,
                        focusRequester = playRequester,
                        memoryKey = "hero_play",
                    )
                    NovaButton(
                        "Plus d'infos",
                        onClick = { featured?.let { openTile(vm, it) } },
                        icon = Icons.Filled.Info,
                    )
                    if (scope != Scope.ALL) {
                        NovaButton(
                            "Catégories",
                            onClick = {
                                vm.navigate(Route.Catalog(if (scope == Scope.MOVIES) ContentType.MOVIE else ContentType.SERIES))
                            },
                            icon = Icons.Filled.List,
                        )
                    }
                }
            }
            // ------------------------------------------------------------ Rangées
            Box(Modifier.fillMaxWidth().weight(1f)) { PivotScroll(pivot = 44.dp) {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(top = 8.dp, bottom = 260.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(rails, key = { it.key }) { rail ->
                        RailRow(
                            rail = rail,
                            onTileClick = { if (!openExplore(vm, it, scope)) openTile(vm, it) },
                            onTileFocused = { focusedTile = it },
                            onTileLongClick = { longPressFav(vm, context, it) },
                            onSeeAll = rail.seeAllTarget?.let { target -> { vm.openSeeAll(target) } },
                            channelInfo = { t -> channelNow(vm, t) },
                        )
                    }
                }
            } }
        }
    }
}

@Composable
fun channelNow(vm: AppViewModel, t: Tile): Pair<String?, Float?> {
    val ch = t.channel ?: return null to null
    LaunchedEffect(ch.id) { vm.ensureEpg(ch) }
    val now = System.currentTimeMillis()
    val p = vm.epg[ch.id]?.firstOrNull { it.isNow(now) }
    return (p?.title ?: ch.displayName) to p?.progress(now)
}

@Composable
private fun HeroInfo(tile: Tile?, d: Details?) {
    if (tile == null) return
    Column {
        val logo = d?.logo
        if (logo != null) {
            AsyncImage(
                model = logo,
                contentDescription = tile.title,
                contentScale = ContentScale.Fit,
                alignment = Alignment.BottomStart,
                modifier = Modifier.heightIn(max = 84.dp).widthIn(max = 320.dp).height(84.dp),
            )
        } else {
            Text(
                d?.title ?: tile.title, style = Type.Display, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(10.dp))
        MetaLine(d, tile)
        Spacer(Modifier.height(8.dp))
        val plot = d?.plot ?: tile.history?.subtitle
        if (!plot.isNullOrBlank()) {
            Text(plot, style = Type.Body, color = Nova.Text.copy(alpha = 0.82f), maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun MetaLine(d: Details?, tile: Tile?) {
    val parts = buildList {
        (d?.year ?: tile?.subtitle?.takeIf { it.length == 4 })?.let { add(it) }
        formatRuntime(d?.runtimeMin)?.let { add(it) }
        d?.genres?.take(3)?.joinToString(", ")?.takeIf { it.isNotBlank() }?.let { add(it) }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        d?.rating?.let {
            Text("★ ${"%.1f".format(it)}", style = Type.H3, color = Nova.Gold)
            if (parts.isNotEmpty()) Text("   ", style = Type.H3)
        }
        Text(parts.joinToString("  ·  "), style = Type.H3, color = Nova.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun EmptyState(title: String, message: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = Type.H1)
            Spacer(Modifier.height(8.dp))
            Text(message, style = Type.Body, color = Nova.Muted)
        }
    }
}
