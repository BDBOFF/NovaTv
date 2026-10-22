package com.nova.tv.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.nova.tv.AppViewModel
import com.nova.tv.ui.Nova
import com.nova.tv.ui.Rail
import com.nova.tv.ui.RailStyle
import com.nova.tv.ui.Type
import com.nova.tv.ui.components.PivotScroll
import com.nova.tv.ui.components.RailRow
import kotlinx.coroutines.delay

@Composable
fun SearchScreen(vm: AppViewModel) {
    var query by rememberSaveable { mutableStateOf("") }
    val result by produceState<AppViewModel.SearchResult?>(null, query, vm.movies) {
        if (query.isBlank()) {
            value = null; return@produceState
        }
        delay(280)
        value = vm.search(query)
    }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 48.dp, vertical = 8.dp)) {
            TvTextField(
                label = "Rechercher",
                value = query,
                onChange = { query = it },
                placeholder = "Un film, une série, une chaîne…",
                imeAction = ImeAction.Search,
                modifier = Modifier.width(520.dp),
            )
        }
        val r = result
        when {
            query.isBlank() -> SearchHint()
            r == null -> Box(Modifier.fillMaxSize())
            r.movies.isEmpty() && r.series.isEmpty() && r.channels.isEmpty() ->
                EmptyState("Aucun résultat", "Essayez avec un autre mot-clé.")
            else -> {
                val rails = buildList {
                    if (r.movies.isNotEmpty()) add(Rail("q_m", "Films · ${r.movies.size}", RailStyle.POSTER, r.movies.map { vm.movieTile(it) }))
                    if (r.series.isNotEmpty()) add(Rail("q_s", "Séries · ${r.series.size}", RailStyle.POSTER, r.series.map { vm.seriesTile(it) }))
                    if (r.channels.isNotEmpty()) add(Rail("q_c", "Chaînes · ${r.channels.size}", RailStyle.CHANNEL, r.channels.map { vm.channelTile(it) }))
                }
                PivotScroll(pivot = 44.dp) {
                    LazyColumn(
                        contentPadding = PaddingValues(top = 16.dp, bottom = 200.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        rails.forEach { rail ->
                            item(key = rail.key) {
                                RailRow(
                                    rail,
                                    onTileClick = { t ->
                                        val ch = t.channel
                                        if (ch != null) vm.playChannel(ch, r.channels) else openTile(vm, t)
                                    },
                                    channelInfo = { t -> channelNow(vm, t) },
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
private fun SearchHint() {
    Column(Modifier.fillMaxWidth().padding(horizontal = 48.dp, vertical = 30.dp)) {
        Text("Que voulez-vous regarder ?", style = Type.H1)
        Spacer(Modifier.height(8.dp))
        Text(
            "Appuyez sur OK pour ouvrir le clavier, ou utilisez la recherche vocale de votre télécommande.",
            style = Type.Body, color = Nova.Muted,
        )
    }
}
