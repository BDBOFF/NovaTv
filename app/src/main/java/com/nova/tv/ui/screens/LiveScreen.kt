package com.nova.tv.ui.screens

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nova.tv.AppViewModel
import com.nova.tv.data.Channel
import com.nova.tv.data.ContentType
import com.nova.tv.data.Kind
import com.nova.tv.ui.Nova
import com.nova.tv.ui.Special
import com.nova.tv.ui.Type
import com.nova.tv.ui.components.Badge
import com.nova.tv.ui.components.FocusBox
import com.nova.tv.ui.components.NetImage
import com.nova.tv.ui.components.PivotScroll
import com.nova.tv.ui.components.ProgressLine
import com.nova.tv.ui.components.formatClock
import kotlinx.coroutines.delay

@Composable
fun rememberNow(periodMs: Long = 30_000): Long {
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(periodMs); value = System.currentTimeMillis()
        }
    }
    return now
}

fun channelsFor(vm: AppViewModel, selected: String): List<Channel> = when (selected) {
    Special.ALL -> vm.visibleChannels()
    Special.FAV -> vm.favorites(Kind.LIVE).mapNotNull { vm.channelsById[it.id] }
    Special.HISTORY -> vm.history(Kind.LIVE).mapNotNull { vm.channelsById[it.id] }
    else -> vm.channels.filter { it.categoryId == selected }
}

fun liveCategoryEntries(vm: AppViewModel): List<CatEntry> = buildList {
    add(CatEntry(Special.FAV, "Favoris", vm.favorites(Kind.LIVE).size))
    add(CatEntry(Special.HISTORY, "Récemment regardées", vm.history(Kind.LIVE).size))
    add(CatEntry(Special.ALL, "Toutes les chaînes", vm.visibleChannels().size))
    vm.categoriesOf(ContentType.LIVE).forEach { add(CatEntry(it.id, it.name, vm.count(ContentType.LIVE, it.id))) }
}

fun defaultLiveCategory(vm: AppViewModel): String =
    vm.selection[ContentType.LIVE] ?: when {
        vm.favorites(Kind.LIVE).isNotEmpty() -> Special.FAV
        else -> vm.categoriesOf(ContentType.LIVE).firstOrNull()?.id ?: Special.ALL
    }

@Composable
fun LiveScreen(vm: AppViewModel) {
    val selected = defaultLiveCategory(vm)
    val cats = remember(vm.channels, vm.storage.favorites, vm.storage.history, vm.storage.showAdult) { liveCategoryEntries(vm) }
    val list = remember(selected, vm.channels, vm.storage.favorites, vm.storage.history) { channelsFor(vm, selected) }
    val now = rememberNow()
    val context = androidx.compose.ui.platform.LocalContext.current

    Row(Modifier.fillMaxSize().padding(top = 8.dp)) {
        CategoryColumn(cats, selected, { vm.selection[ContentType.LIVE] = it }, 270.dp)
        Column(Modifier.weight(1f).fillMaxHeight()) {
            Row(Modifier.padding(start = 24.dp, end = 48.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(cats.firstOrNull { it.id == selected }?.name ?: "TV Direct", style = Type.H1, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${list.size} chaînes", style = Type.Small, color = Nova.Muted)
                }
                Text(formatClock(now), style = Type.H2, color = Nova.Muted)
            }
            if (list.isEmpty()) {
                EmptyState(
                    "Aucune chaîne",
                    if (selected == Special.FAV) "Appui long sur OK sur une chaîne pour l'ajouter, ou bouton cœur dans le lecteur." else "Cette catégorie est vide.",
                )
            } else {
                val gridState = rememberLazyGridState()
                LaunchedEffect(selected) { gridState.scrollToItem(0) }
                PivotScroll(pivot = 16.dp) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        state = gridState,
                        contentPadding = PaddingValues(start = 24.dp, end = 48.dp, top = 12.dp, bottom = 80.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(list, key = { it.id }) { ch ->
                            ChannelCard(vm, ch, now, onLongClick = { longPressFav(vm, context, vm.channelTile(ch)) }) { vm.playChannel(ch, list) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ChannelCard(vm: AppViewModel, ch: Channel, now: Long, onLongClick: (() -> Unit)? = null, onClick: () -> Unit) {
    LaunchedEffect(ch.id) { vm.ensureEpg(ch) }
    val programs = vm.epg[ch.id]
    val current = programs?.firstOrNull { it.isNow(now) }
    val next = programs?.firstOrNull { it.start >= (current?.end ?: now) }
    val fav = vm.isFav(Kind.LIVE, ch.id)

    FocusBox(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = Modifier.fillMaxWidth().height(112.dp),
        focusedScale = 1.05f,
        memoryKey = "ch_${ch.id}",
        shape = RoundedCornerShape(12.dp),
    ) { focused ->
        Row(
            Modifier
                .fillMaxSize()
                .background(if (focused) Nova.Surface3 else Nova.Surface)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(64.dp).clip(RoundedCornerShape(10.dp)).background(Nova.Surface2),
                contentAlignment = Alignment.Center,
            ) {
                NetImage(
                    ch.logo,
                    Modifier.size(54.dp).background(Color.Transparent),
                    fallbackText = if (ch.logo == null) ch.displayName.take(3) else null,
                    contentScale = ContentScale.Fit,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(ch.displayName, style = Type.H3, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    ch.quality?.let { Spacer(Modifier.width(6.dp)); Badge(it, filled = false) }
                    if (fav) {
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.Filled.Favorite, null, tint = Nova.Accent, modifier = Modifier.size(12.dp))
                    }
                }
                Spacer(Modifier.height(4.dp))
                if (current != null) {
                    Text(
                        "${formatClock(current.start)} – ${formatClock(current.end)}",
                        style = Type.Small, color = Nova.Muted,
                    )
                    Text(current.title, style = Type.Small, color = Nova.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(5.dp))
                    ProgressLine(current.progress(now), Modifier.fillMaxWidth())
                    if (focused && next != null) {
                        Spacer(Modifier.height(3.dp))
                        Text("Ensuite : ${next.title}", style = Type.Small, color = Nova.Dim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                } else {
                    Text(
                        if (programs == null) "Chargement du programme…" else "Programme non disponible",
                        style = Type.Small, color = Nova.Dim,
                    )
                }
            }
        }
    }
}
