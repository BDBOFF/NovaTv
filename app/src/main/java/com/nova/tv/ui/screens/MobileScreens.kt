package com.nova.tv.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.nova.tv.AppViewModel
import com.nova.tv.data.Channel
import com.nova.tv.data.ContentType
import com.nova.tv.data.Details
import com.nova.tv.data.Kind
import com.nova.tv.player.LivePreview
import com.nova.tv.ui.MainTab
import com.nova.tv.ui.Nova
import com.nova.tv.ui.Rail
import com.nova.tv.ui.Route
import com.nova.tv.ui.Scope
import com.nova.tv.ui.Special
import com.nova.tv.ui.Tile
import com.nova.tv.ui.Type
import com.nova.tv.ui.components.Badge
import com.nova.tv.ui.components.NetImage
import com.nova.tv.ui.components.NovaButton
import com.nova.tv.ui.components.PlayGlyph
import com.nova.tv.ui.components.ProgressLine
import com.nova.tv.ui.components.RailRow
import com.nova.tv.ui.components.formatClock
import kotlinx.coroutines.delay

// =============================================================================== Accueil / Films / Séries

@Composable
fun MobileBrowse(vm: AppViewModel, scope: Scope, rails: List<Rail>) {
    val context = LocalContext.current
    val heroTiles = remember(rails) {
        rails.firstOrNull()?.tiles?.filter { it.kind != Kind.LIVE }?.take(6).orEmpty()
    }
    if (rails.isEmpty()) {
        EmptyState("Aucun contenu", "Le catalogue est vide.")
        return
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (heroTiles.isNotEmpty()) item(key = "hero") { MobileHero(vm, scope, heroTiles) }
        if (scope != Scope.ALL) item(key = "cats") {
            Row(Modifier.padding(horizontal = 16.dp)) {
                NovaButton(
                    "Toutes les catégories",
                    onClick = { vm.navigate(Route.Catalog(if (scope == Scope.MOVIES) ContentType.MOVIE else ContentType.SERIES)) },
                    icon = Icons.Filled.List,
                )
            }
        }
        items(rails, key = { it.key }) { rail ->
            RailRow(
                rail = rail,
                onTileClick = { if (!openExplore(vm, it, scope)) openTile(vm, it) },
                onTileLongClick = { longPressFav(vm, context, it) },
                onSeeAll = rail.seeAllTarget?.let { target -> { vm.openSeeAll(target) } },
                channelInfo = { t -> channelNow(vm, t) },
            )
        }
    }
}

/** Grande affiche qui défile toute seule (et au doigt) en haut des pages mobiles. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MobileHero(vm: AppViewModel, scope: Scope, tiles: List<Tile>) {
    val pager = rememberPagerState { tiles.size }
    val config = LocalConfiguration.current
    val portrait = config.screenHeightDp > config.screenWidthDp
    LaunchedEffect(tiles.size) {
        while (tiles.size > 1) {
            delay(7000)
            if (!pager.isScrollInProgress) pager.animateScrollToPage((pager.currentPage + 1) % tiles.size)
        }
    }
    Box(Modifier.fillMaxWidth()) {
        HorizontalPager(
            state = pager,
            modifier = Modifier
                .fillMaxWidth()
                .then(if (portrait) Modifier.aspectRatio(0.78f) else Modifier.height(300.dp)),
        ) { page -> HeroPage(vm, tiles[page], portrait) }
        if (tiles.size > 1) {
            Row(
                Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                repeat(tiles.size) { i ->
                    Box(
                        Modifier
                            .size(if (i == pager.currentPage) 18.dp else 6.dp, 6.dp)
                            .clip(RoundedCornerShape(50))
                            .background(if (i == pager.currentPage) Nova.Accent else Color.White.copy(alpha = 0.35f))
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroPage(vm: AppViewModel, tile: Tile, portrait: Boolean) {
    val d by produceState<Details?>(vm.cachedPreview(tile), tile.key) {
        if (value == null) value = runCatching { vm.previewDetails(tile) }.getOrNull()
    }
    val pending = tile.kind == Kind.TMDB_MOVIE || tile.kind == Kind.TMDB_TV
    val image = if (portrait) d?.poster ?: tile.image else d?.backdrop ?: tile.image
    Box(Modifier.fillMaxSize().clickable { openTile(vm, tile) }) {
        AsyncImage(
            model = image, contentDescription = null, contentScale = ContentScale.Crop,
            alignment = Alignment.TopCenter, modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.15f), 0.45f to Color.Transparent, 0.78f to Nova.Bg.copy(alpha = 0.85f), 1f to Nova.Bg)
            )
        )
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            tile.rank?.let {
                Text(if (tile.key.startsWith("trend_")) "N°$it · TENDANCES DE LA SEMAINE" else "N°$it", style = Type.Label, color = Nova.Accent)
                Spacer(Modifier.height(6.dp))
            }
            if (d?.logo != null) {
                AsyncImage(
                    model = d?.logo, contentDescription = tile.title, contentScale = ContentScale.Fit,
                    modifier = Modifier.heightIn(max = 70.dp).widthIn(max = 260.dp).height(70.dp),
                )
            } else {
                Text(
                    d?.title?.takeIf { it.isNotBlank() } ?: tile.title, style = Type.H1, textAlign = TextAlign.Center,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(6.dp))
            val meta = listOfNotNull(
                d?.rating?.let { "★ ${"%.1f".format(it)}" },
                d?.year ?: tile.subtitle?.takeIf { it.length == 4 },
                d?.genres?.take(2)?.joinToString(" · ")?.takeIf { it.isNotBlank() },
            ).joinToString("   ")
            if (meta.isNotBlank()) Text(meta, style = Type.Small, color = Nova.Text.copy(alpha = 0.85f), maxLines = 1)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (pending) {
                    val fav = vm.isFav(tile.kind, tile.id)
                    NovaButton(
                        if (fav) "Dans mes favoris" else "Favoris",
                        onClick = { vm.toggleFavTile(tile) },
                        icon = if (fav) Icons.Filled.Check else Icons.Filled.Add,
                        primary = true,
                    )
                } else {
                    NovaButton(
                        "Lecture",
                        onClick = {
                            if (tile.kind == Kind.MOVIE) vm.moviesById[tile.id]?.let { vm.playMovie(it) } else openTile(vm, tile)
                        },
                        glyph = { c -> PlayGlyph(c, 13.dp) },
                        primary = true,
                    )
                }
                NovaButton("Infos", onClick = { openTile(vm, tile) }, icon = Icons.Filled.Info)
            }
        }
    }
}

// =============================================================================== TV Direct

@Composable
fun MobileLiveHome(vm: AppViewModel, rails: List<Rail>, now: Long) {
    val context = LocalContext.current
    val featured: Channel? = remember(rails) { rails.firstOrNull { it.key != "l_matches" }?.tiles?.firstOrNull()?.channel }
    LaunchedEffect(featured?.id) { featured?.let { vm.ensureEpg(it) } }
    val programs = featured?.let { vm.epg[it.id] }
    val current = programs?.firstOrNull { it.isNow(now) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (featured != null) item(key = "preview") {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { vm.playChannel(featured, rails.first { it.key != "l_matches" }.tiles.mapNotNull { it.channel }) },
                ) {
                    if (vm.storage.livePreview) {
                        LivePreview(vm.liveUrl(featured), featured.logo, Modifier.fillMaxSize())
                    } else {
                        Box(Modifier.fillMaxSize().background(Nova.Surface2), contentAlignment = Alignment.Center) {
                            NetImage(featured.logo, Modifier.size(160.dp, 90.dp).background(Color.Transparent), contentScale = ContentScale.Fit)
                        }
                    }
                    Box(
                        Modifier.align(Alignment.Center).size(54.dp).clip(CircleShape).background(Nova.Accent.copy(alpha = 0.9f)),
                        contentAlignment = Alignment.Center,
                    ) { Box(Modifier.padding(start = 4.dp)) { PlayGlyph(Color.White, 18.dp) } }
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Badge("EN DIRECT")
                    Spacer(Modifier.width(8.dp))
                    Text(featured.displayName, style = Type.H3, color = Nova.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(4.dp))
                Text(current?.title ?: featured.displayName, style = Type.H1, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (current != null) {
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${formatClock(current.start)} – ${formatClock(current.end)}", style = Type.Small, color = Nova.Muted)
                        Spacer(Modifier.width(10.dp))
                        ProgressLine(current.progress(now), Modifier.weight(1f))
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    NovaButton(
                        "Toutes les chaînes",
                        onClick = {
                            if (vm.selection[ContentType.LIVE] == null) vm.selection[ContentType.LIVE] = Special.ALL
                            vm.navigate(Route.LiveGrid)
                        },
                        icon = Icons.Filled.List,
                    )
                    NovaButton("Guide TV", onClick = { vm.tab = MainTab.GUIDE }, icon = Icons.Filled.DateRange)
                }
            }
        }
        items(rails, key = { it.key }) { rail ->
            val list = rail.tiles.mapNotNull { it.channel }
            RailRow(
                rail = rail,
                onTileClick = { t -> if (t.kind == Kind.MATCH) openTile(vm, t) else t.channel?.let { c -> vm.playChannel(c, list) } },
                onTileLongClick = { longPressFav(vm, context, it) },
                onSeeAll = rail.seeAllTarget?.let { target -> { vm.openSeeAll(target) } },
                channelInfo = { t -> channelNow(vm, t) },
            )
        }
    }
}
