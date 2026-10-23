package com.nova.tv.ui.screens

import androidx.compose.foundation.layout.widthIn
import com.nova.tv.ui.Gutter
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nova.tv.AppViewModel
import com.nova.tv.data.Channel
import com.nova.tv.data.ContentType
import com.nova.tv.player.LivePreview
import com.nova.tv.ui.MainTab
import com.nova.tv.ui.Nova
import com.nova.tv.ui.Route
import com.nova.tv.ui.Special
import com.nova.tv.ui.Tile
import com.nova.tv.ui.Type
import com.nova.tv.ui.components.Badge
import com.nova.tv.ui.components.NetImage
import com.nova.tv.ui.components.NovaButton
import com.nova.tv.ui.components.PivotScroll
import com.nova.tv.ui.components.PlayGlyph
import com.nova.tv.ui.components.ProgressLine
import com.nova.tv.ui.components.RailRow
import com.nova.tv.ui.components.formatClock

/** Page d'accueil de la TV Direct : grande zone d'aperçu + rangées de chaînes. */
@Composable
fun LiveHomeScreen(vm: AppViewModel) {
    LaunchedEffect(Unit) { vm.loadMatches() }
    val rails = remember(vm.channels, vm.storage.favorites, vm.storage.history, vm.storage.showAdult, vm.matches) { vm.buildLiveRails() }
    var focused by remember { mutableStateOf<Tile?>(null) }
    var heroFocused by remember { mutableStateOf(false) }
    val now = rememberNow(30_000)
    val context = androidx.compose.ui.platform.LocalContext.current
    val listState = rememberLazyListState()

    if (rails.isEmpty()) {
        EmptyState("Aucune chaîne", "Votre abonnement ne contient pas de chaînes en direct.")
        return
    }

    if (com.nova.tv.ui.LocalDevice.current.compact) {
        MobileLiveHome(vm, rails, now)
        return
    }

    val featured: Channel? = remember(rails) { rails.firstOrNull()?.tiles?.firstOrNull()?.channel }
    val ch: Channel? = if (heroFocused) featured else focused?.channel ?: featured
    LaunchedEffect(ch?.id) { ch?.let { vm.ensureEpg(it) } }
    val programs = ch?.let { vm.epg[it.id] }
    val current = programs?.firstOrNull { it.isNow(now) }
    val next = programs?.firstOrNull { it.start >= (current?.end ?: now) }

    Box(Modifier.fillMaxSize()) {
        // Fond : halo rouge + logo de la chaîne en grand, discret
        Box(
            Modifier.fillMaxSize().background(
                Brush.radialGradient(
                    listOf(Nova.Accent.copy(alpha = 0.28f), Color.Transparent),
                    center = androidx.compose.ui.geometry.Offset(1500f, 120f),
                    radius = 1300f,
                )
            )
        )
        if (vm.storage.livePreview) {
            LivePreview(
                url = ch?.let { vm.liveUrl(it) },
                logo = ch?.logo,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 80.dp, end = Gutter)
                    .size(384.dp, 216.dp),
            )
        } else if (ch?.logo != null) {
            NetImage(
                ch.logo,
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 90.dp, end = 70.dp)
                    .size(300.dp, 170.dp)
                    .background(Color.Transparent),
                contentScale = ContentScale.Fit,
            )
        }

        Column(Modifier.fillMaxSize()) {
            Spacer(Modifier.height(78.dp))
            // ------------------------------------------------ Aperçu de la chaîne
            Column(Modifier.padding(horizontal = Gutter).widthIn(max = 470.dp).height(240.dp)) {
                if (ch != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Badge("EN DIRECT")
                        Spacer(Modifier.width(10.dp))
                        Text(ch.displayName, style = Type.H3, color = Nova.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        ch.quality?.let { Spacer(Modifier.width(8.dp)); Badge(it, filled = false) }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        current?.title ?: ch.displayName,
                        style = Type.Display, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(8.dp))
                    if (current != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${formatClock(current.start)} – ${formatClock(current.end)}", style = Type.H3, color = Nova.Muted)
                            Spacer(Modifier.width(12.dp))
                            ProgressLine(current.progress(now), Modifier.width(200.dp))
                            Spacer(Modifier.width(10.dp))
                            val left = ((current.end - now) / 60000).coerceAtLeast(0)
                            Text("encore $left min", style = Type.Small, color = Nova.Muted)
                        }
                        current.description?.let {
                            Spacer(Modifier.height(6.dp))
                            Text(it, style = Type.Body, color = Nova.Text.copy(alpha = 0.8f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    } else {
                        Text(
                            if (programs == null) "Chargement du programme…" else "Programme non disponible",
                            style = Type.H3, color = Nova.Muted,
                        )
                    }
                    next?.let {
                        Spacer(Modifier.height(4.dp))
                        Text("Ensuite · ${formatClock(it.start)}  ${it.title}", style = Type.Small, color = Nova.Dim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Spacer(Modifier.weight(1f))
                Row(
                    Modifier
                        .onFocusChanged { heroFocused = it.hasFocus }
                        .focusGroup(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    NovaButton(
                        "Regarder",
                        onClick = {
                            featured?.let { f -> vm.playChannel(f, rails.first().tiles.mapNotNull { it.channel }) }
                        },
                        glyph = { c -> PlayGlyph(c, 13.dp) },
                        primary = true,
                        memoryKey = "live_play",
                    )
                    NovaButton(
                        "Toutes les chaînes",
                        onClick = {
                            if (vm.selection[ContentType.LIVE] == null) vm.selection[ContentType.LIVE] = Special.ALL
                            vm.navigate(Route.LiveGrid)
                        },
                        icon = Icons.Filled.List,
                        memoryKey = "live_all",
                    )
                    NovaButton(
                        "Guide TV",
                        onClick = { vm.tab = MainTab.GUIDE },
                        icon = Icons.Filled.DateRange,
                        memoryKey = "live_guide",
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            // ------------------------------------------------ Rangées
            Box(Modifier.fillMaxWidth().weight(1f)) {
                PivotScroll(pivot = 44.dp) {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(top = 8.dp, bottom = 260.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(rails, key = { it.key }) { rail ->
                            val list = rail.tiles.mapNotNull { it.channel }
                            RailRow(
                                rail = rail,
                                onTileClick = { t -> if (t.kind == com.nova.tv.data.Kind.MATCH) openTile(vm, t) else t.channel?.let { c -> vm.playChannel(c, list) } },
                                onTileFocused = { focused = it },
                                onTileLongClick = { longPressFav(vm, context, it) },
                                onSeeAll = rail.seeAllTarget?.let { target -> { vm.openSeeAll(target) } },
                                channelInfo = { t -> channelNow(vm, t) },
                            )
                        }
                    }
                }
            }
        }
    }
}
