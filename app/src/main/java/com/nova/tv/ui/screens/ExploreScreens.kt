package com.nova.tv.ui.screens

import androidx.compose.foundation.layout.widthIn
import com.nova.tv.ui.Gutter
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.nova.tv.AppViewModel
import com.nova.tv.ui.Nova
import com.nova.tv.ui.Scope
import com.nova.tv.ui.Tile
import com.nova.tv.ui.Type
import com.nova.tv.ui.components.LocalScreenKey
import com.nova.tv.ui.components.PivotScroll
import com.nova.tv.ui.components.PosterCard

/** Grille d'affiches commune (genres, années, sagas). */
@Composable
private fun PosterGrid(vm: AppViewModel, tiles: List<Tile>, first: FocusRequester, columns: Int = 7) {
    val context = LocalContext.current
    PivotScroll(pivot = 16.dp) {
        LazyVerticalGrid(
            columns = if (com.nova.tv.ui.LocalDevice.current.isTv) GridCells.Fixed(columns) else GridCells.Adaptive(104.dp),
            contentPadding = PaddingValues(start = Gutter, end = Gutter, top = 12.dp, bottom = 80.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            itemsIndexed(tiles, key = { _, t -> t.key }) { i, t ->
                PosterCard(
                    t,
                    onClick = { openTile(vm, t) },
                    showTitle = true,
                    fill = true,
                    focusRequester = if (i == 0) first else null,
                    onLongClick = { longPressFav(vm, context, t) },
                )
            }
        }
    }
}

@Composable
fun GridScreen(vm: AppViewModel, mode: String, key: String, scope: Scope, title: String) {
    val screen = "grid_${mode}_${key}_$scope"
    val first = remember { FocusRequester() }
    val tiles by produceState<List<Tile>?>(null, mode, key, scope, vm.storage.favorites) {
        value = when (mode) {
            "year" -> vm.yearTiles(scope, key.toIntOrNull() ?: 0)
            else -> vm.genreTiles(scope, key)
        }
    }
    CompositionLocalProvider(LocalScreenKey provides screen) {
        Column(Modifier.fillMaxSize().background(Nova.Bg).padding(top = 36.dp)) {
            Column(Modifier.padding(horizontal = Gutter)) {
                Text(title, style = Type.Display)
                val scopeLabel = when (scope) {
                    Scope.MOVIES -> "Films"
                    Scope.SERIES -> "Séries"
                    Scope.ALL -> "Films et séries"
                }
                Text(
                    tiles?.let { "$scopeLabel · ${it.size} titres dans votre catalogue" } ?: "Recherche dans votre catalogue…",
                    style = Type.H3, color = Nova.Muted,
                )
            }
            val list = tiles
            when {
                list == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Nova.Accent, strokeWidth = 3.dp, modifier = Modifier.size(36.dp))
                }
                list.isEmpty() -> EmptyState("Rien pour l'instant", "Aucun titre de ce genre n'a été trouvé dans votre catalogue.")
                else -> {
                    RequestInitialFocus(screen, first)
                    PosterGrid(vm, list, first)
                }
            }
        }
    }
}

@Composable
fun SagaScreen(vm: AppViewModel, id: Int) {
    val saga = vm.sagaById(id)
    if (saga == null) {
        EmptyState("Saga introuvable", "Revenez en arrière et réessayez."); return
    }
    val screen = "saga_$id"
    val first = remember { FocusRequester() }
    val tiles = remember(saga, vm.movies, vm.storage.favorites) { vm.sagaTiles(saga) }
    val available = remember(saga, vm.movies) { vm.sagaAvailable(saga) }

    CompositionLocalProvider(LocalScreenKey provides screen) {
        Box(Modifier.fillMaxSize().background(Nova.Bg)) {
            saga.backdrop?.let {
                AsyncImage(
                    model = it, contentDescription = null, contentScale = ContentScale.Crop,
                    alignment = Alignment.TopCenter,
                    modifier = Modifier.align(Alignment.TopEnd).fillMaxWidth(0.75f).fillMaxHeight(0.6f),
                )
            }
            Box(
                Modifier.fillMaxSize().background(
                    Brush.horizontalGradient(0f to Nova.Bg, 0.3f to Nova.Bg.copy(alpha = 0.92f), 0.65f to Nova.Bg.copy(alpha = 0.3f), 1f to Color.Transparent)
                )
            )
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Transparent, 0.38f to Nova.Bg.copy(alpha = 0.7f), 0.55f to Nova.Bg)))
            Column(Modifier.fillMaxSize().padding(top = 48.dp)) {
                Column(Modifier.padding(horizontal = Gutter).widthIn(max = 560.dp)) {
                    Text("SAGA", style = Type.Label, color = Nova.Accent)
                    Spacer(Modifier.height(4.dp))
                    Text(saga.name, style = Type.Display, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "$available film${if (available > 1) "s" else ""} sur ${saga.parts.size} dans votre catalogue · " +
                            "${saga.parts.first().year} – ${saga.parts.last().year}",
                        style = Type.H3, color = Nova.Muted,
                    )
                    saga.overview?.takeIf { it.isNotBlank() }?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, style = Type.Body, color = Nova.Text.copy(alpha = 0.85f), maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                }
                Spacer(Modifier.height(12.dp))
                RequestInitialFocus(screen, first)
                PosterGrid(vm, tiles, first)
            }
        }
    }
}
