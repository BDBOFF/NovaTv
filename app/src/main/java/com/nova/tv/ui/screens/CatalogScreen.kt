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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import com.nova.tv.ui.components.LocalScreenKey
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nova.tv.AppViewModel
import com.nova.tv.data.ContentType
import com.nova.tv.data.Kind
import com.nova.tv.ui.Nova
import com.nova.tv.ui.Special
import com.nova.tv.ui.Tile
import com.nova.tv.ui.Type
import com.nova.tv.ui.components.FocusBox
import com.nova.tv.ui.components.PivotScroll
import com.nova.tv.ui.components.PosterCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class CatEntry(val id: String, val name: String, val count: Int)

enum class SortMode(val label: String) { RECENT("Récents"), ALPHA("A → Z"), RATING("Mieux notés") }

@Composable
fun CatalogScreen(vm: AppViewModel, type: ContentType) {
    val isMovie = type == ContentType.MOVIE
    val selected = vm.selection[type] ?: Special.ALL
    var sort by rememberSaveable(type) { mutableStateOf(SortMode.RECENT) }
    val favKind = if (isMovie) Kind.MOVIE else Kind.SERIES

    val cats: List<CatEntry> = buildList {
        val total = if (isMovie) vm.visibleMovies().size else vm.visibleSeries().size
        add(CatEntry(Special.ALL, "Tout", total))
        add(CatEntry(Special.RECENT, "Ajouts récents", minOf(total, 100)))
        add(CatEntry(Special.FAV, "Ma liste", vm.favorites(favKind).size))
        if (isMovie) add(CatEntry(Special.RESUME, "En cours", vm.history(Kind.MOVIE).count { it.progress in 0.02f..0.94f }))
        vm.categoriesOf(type).forEach { add(CatEntry(it.id, it.name, vm.count(type, it.id))) }
    }

    val tiles by produceState<List<Tile>?>(null, selected, sort, type, vm.movies, vm.series, vm.storage.favorites, vm.storage.showAdult) {
        value = withContext(Dispatchers.Default) {
            if (isMovie) {
                val all = vm.visibleMovies()
                val base = when (selected) {
                    Special.ALL -> all
                    Special.RECENT -> all.sortedByDescending { it.added }.take(100)
                    Special.FAV -> vm.favorites(Kind.MOVIE).mapNotNull { vm.moviesById[it.id] }
                    Special.RESUME -> vm.history(Kind.MOVIE).filter { it.progress in 0.02f..0.94f }.mapNotNull { vm.moviesById[it.id] }
                    else -> all.filter { it.categoryId == selected }
                }
                val sorted = when {
                    selected == Special.FAV || selected == Special.RESUME -> base
                    sort == SortMode.ALPHA -> base.sortedBy { it.title.lowercase() }
                    sort == SortMode.RATING -> base.sortedByDescending { it.rating ?: 0.0 }
                    else -> base.sortedByDescending { it.added }
                }
                sorted.map { vm.movieTile(it) }
            } else {
                val all = vm.visibleSeries()
                val base = when (selected) {
                    Special.ALL -> all
                    Special.RECENT -> all.sortedByDescending { it.lastModified }.take(100)
                    Special.FAV -> vm.favorites(Kind.SERIES).mapNotNull { vm.seriesById[it.id] }
                    else -> all.filter { it.categoryId == selected }
                }
                val sorted = when {
                    selected == Special.FAV -> base
                    sort == SortMode.ALPHA -> base.sortedBy { it.title.lowercase() }
                    sort == SortMode.RATING -> base.sortedByDescending { it.rating ?: 0.0 }
                    else -> base.sortedByDescending { it.lastModified }
                }
                sorted.map { vm.seriesTile(it) }
            }
        }
    }

    val firstItem = remember { FocusRequester() }
    Row(Modifier.fillMaxSize().padding(top = 8.dp)) {
        CategoryColumn(
            entries = cats,
            selected = selected,
            onSelect = { vm.selection[type] = it },
            width = 250.dp,
        )
        Column(Modifier.weight(1f).fillMaxHeight()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 24.dp, end = 48.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val title = cats.firstOrNull { it.id == selected }?.name ?: if (isMovie) "Films" else "Séries"
                Column(Modifier.weight(1f)) {
                    Text(title, style = Type.H1, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${tiles?.size ?: 0} titres", style = Type.Small, color = Nova.Muted)
                }
                SortMode.entries.forEach { m ->
                    Chip(m.label, sort == m) { sort = m }
                    Spacer(Modifier.width(8.dp))
                }
            }
            val list = tiles
            when {
                list == null -> Box(Modifier.fillMaxSize())
                list.isEmpty() -> EmptyState(
                    "Rien ici pour l'instant",
                    if (selected == Special.FAV) "Ajoutez des titres à votre liste depuis leur fiche." else "Cette catégorie est vide.",
                )
                else -> {
                    val gridState = rememberLazyGridState()
                    LaunchedEffect(selected, sort) { gridState.scrollToItem(0) }
                    val screen = LocalScreenKey.current
                    if (screen.startsWith("catalog_")) RequestInitialFocus(screen, firstItem)
                    PivotScroll(pivot = 16.dp) {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(6),
                            state = gridState,
                            contentPadding = PaddingValues(start = 24.dp, end = 48.dp, top = 12.dp, bottom = 80.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalArrangement = Arrangement.spacedBy(18.dp),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            itemsIndexed(list, key = { _, it -> it.key }) { i, t ->
                                PosterCard(
                                    t,
                                    focusRequester = if (i == 0) firstItem else null,
                                    onClick = { openTile(vm, t) },
                                    showTitle = true,
                                    fill = true,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CategoryColumn(entries: List<CatEntry>, selected: String, onSelect: (String) -> Unit, width: Dp) {
    val state = rememberLazyListState()
    LaunchedEffect(Unit) {
        val idx = entries.indexOfFirst { it.id == selected }
        if (idx > 3) state.scrollToItem(idx - 2)
    }
    PivotScroll(pivot = 120.dp) {
        LazyColumn(
            state = state,
            modifier = Modifier.width(width).fillMaxHeight(),
            contentPadding = PaddingValues(start = 36.dp, end = 8.dp, top = 8.dp, bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            items(entries, key = { it.id }) { e ->
                CategoryItem(e, e.id == selected) { onSelect(e.id) }
            }
        }
    }
}

@Composable
fun CategoryItem(e: CatEntry, selected: Boolean, onClick: () -> Unit) {
    FocusBox(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(38.dp),
        shape = RoundedCornerShape(8.dp),
        focusedScale = 1.03f,
        borderWidth = 0.dp,
        glow = false,
        memoryKey = "cat_${e.id}",
    ) { focused ->
        Row(
            Modifier
                .fillMaxSize()
                .background(
                    when {
                        focused -> Color.White
                        selected -> Nova.Surface2
                        else -> Color.Transparent
                    }
                )
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selected) {
                Box(
                    Modifier.width(3.dp).height(16.dp).background(Nova.Accent, RoundedCornerShape(2.dp))
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                e.name,
                style = Type.H3,
                color = when {
                    focused -> Color.Black
                    selected -> Color.White
                    else -> Nova.Muted
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (e.count >= 0) Text("${e.count}", style = Type.Small, color = if (focused) Color.DarkGray else Nova.Dim)
        }
    }
}

@Composable
fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    FocusBox(
        onClick = onClick,
        modifier = Modifier.height(30.dp),
        shape = RoundedCornerShape(50),
        focusedScale = 1.05f,
        borderWidth = 0.dp,
        glow = false,
    ) { focused ->
        Box(
            Modifier
                .fillMaxHeight()
                .background(
                    when {
                        focused -> Color.White
                        selected -> Nova.Accent
                        else -> Nova.Surface2
                    }
                )
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(label, style = Type.Small, color = if (focused) Color.Black else Color.White)
        }
    }
}
