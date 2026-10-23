package com.nova.tv.ui.screens

import com.nova.tv.ui.Gutter
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nova.tv.AppViewModel
import com.nova.tv.data.Channel
import com.nova.tv.data.ContentType
import com.nova.tv.data.EpgProgram
import com.nova.tv.ui.Nova
import com.nova.tv.ui.Type
import com.nova.tv.ui.components.Badge
import com.nova.tv.ui.components.EdgeScroll
import com.nova.tv.ui.components.FocusBox
import com.nova.tv.ui.components.NetImage
import com.nova.tv.ui.components.PivotScroll
import com.nova.tv.ui.components.formatClock

private val PerMin: Dp = 2.6.dp
private const val SPAN_MIN = 240
private val ChannelCol: Dp = 170.dp
private val RowH: Dp = 54.dp
private const val HALF_HOUR = 30 * 60 * 1000L

@Composable
fun GuideScreen(vm: AppViewModel) {
    val context = LocalContext.current
    val selected = defaultLiveCategory(vm)
    val cats = remember(vm.channels, vm.storage.favorites, vm.storage.history) { liveCategoryEntries(vm) }
    val list = remember(selected, vm.channels, vm.storage.favorites) { channelsFor(vm, selected) }
    val now = rememberNow(30_000)
    var shift by rememberSaveable { mutableIntStateOf(0) }
    val windowStart = (now / HALF_HOUR) * HALF_HOUR - HALF_HOUR + shift * 2 * 3600_000L
    val windowEnd = windowStart + SPAN_MIN * 60_000L
    var focused by remember { mutableStateOf<Pair<Channel, EpgProgram?>?>(null) }

    fun onProgramClick(ch: Channel, p: EpgProgram?) {
        val t = System.currentTimeMillis()
        when {
            p == null || p.isNow(t) -> vm.playChannel(ch, list)
            p.end <= t && ch.archive && t - p.start < ch.archiveDays.coerceAtLeast(1) * 24 * 3600_000L -> vm.playCatchup(ch, p)
            p.end <= t -> Toast.makeText(context, "Replay non disponible pour cette chaîne", Toast.LENGTH_SHORT).show()
            else -> Toast.makeText(context, "Diffusion à ${formatClock(p.start)}", Toast.LENGTH_SHORT).show()
        }
    }

    val touch = com.nova.tv.ui.LocalDevice.current.touch
    val compact = com.nova.tv.ui.LocalDevice.current.compact
    val chCol = if (compact) 104.dp else ChannelCol
    val hScroll = rememberScrollState()
    val hScrollOrNull = if (touch) hScroll else null
    val density = androidx.compose.ui.platform.LocalDensity.current

    Column(Modifier.fillMaxSize().padding(top = 4.dp)) {
        // ---------------------------------------------------------------- Catégories
        EdgeScroll {
            LazyRow(
                contentPadding = PaddingValues(horizontal = Gutter, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(cats, key = { it.id }) { c ->
                    Chip(c.name, c.id == selected) { vm.selection[ContentType.LIVE] = c.id }
                }
            }
        }
        // ---------------------------------------------------------------- Programme focalisé
        if (touch) Row(
            Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Chip("◀ Plus tôt", false) { if (shift > -3) shift-- }
            Chip("Maintenant", shift == 0) { shift = 0 }
            Chip("Plus tard ▶", false) { if (shift < 6) shift++ }
        } else Row(Modifier.fillMaxWidth().height(86.dp).padding(horizontal = Gutter, vertical = 8.dp)) {
            val f = focused
            Column(Modifier.weight(1f)) {
                if (f != null) {
                    val p = f.second
                    Text(p?.title ?: f.first.displayName, style = Type.H2, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            buildString {
                                append(f.first.displayName)
                                if (p != null) append("  ·  ${formatClock(p.start)} – ${formatClock(p.end)}")
                            },
                            style = Type.Small, color = Nova.Muted,
                        )
                        if (p != null && p.end <= now && f.first.archive) {
                            Spacer(Modifier.width(8.dp)); Badge("REPLAY")
                        }
                        if (p != null && p.isNow(now)) {
                            Spacer(Modifier.width(8.dp)); Badge("EN DIRECT")
                        }
                    }
                    p?.description?.let {
                        Text(it, style = Type.Small, color = Nova.Dim, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                } else {
                    Text("Guide des programmes", style = Type.H2)
                    Text("Sélectionnez un programme pour le regarder, ou un replay sur les chaînes compatibles.", style = Type.Small, color = Nova.Muted)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Chip("◀ Plus tôt", false) { if (shift > -3) shift-- }
                Spacer(Modifier.width(8.dp))
                Chip("Maintenant", shift == 0) { shift = 0 }
                Spacer(Modifier.width(8.dp))
                Chip("Plus tard ▶", false) { if (shift < 6) shift++ }
            }
        }
        // ---------------------------------------------------------------- Échelle de temps
        Row(
            Modifier
                .padding(start = Gutter + chCol, end = 24.dp)
                .height(22.dp)
                .then(if (touch) Modifier.horizontalScroll(hScroll) else Modifier)
        ) {
            Box(Modifier.width(PerMin * SPAN_MIN).fillMaxHeight()) {
                var t = windowStart
                while (t < windowEnd) {
                    val x = PerMin * ((t - windowStart) / 60000f)
                    Text(formatClock(t), style = Type.Small, color = Nova.Muted, modifier = Modifier.offset(x = x))
                    t += HALF_HOUR
                }
            }
        }
        // ---------------------------------------------------------------- Grille
        if (list.isEmpty()) {
            EmptyState("Aucune chaîne", "Choisissez une autre catégorie.")
        } else Box(Modifier.fillMaxSize()) {
            val state = rememberLazyListState()
            LaunchedEffect(selected) { state.scrollToItem(0) }
            PivotScroll(pivot = 60.dp) {
                LazyColumn(
                    state = state,
                    contentPadding = PaddingValues(start = Gutter, end = 24.dp, bottom = 80.dp, top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(list, key = { it.id }) { ch ->
                        GuideRow(vm, ch, windowStart, windowEnd, now, chCol, hScrollOrNull,
                            onFocus = { p -> focused = ch to p },
                            onClick = { p -> onProgramClick(ch, p) })
                    }
                }
            }
            // Ligne "maintenant"
            if (now in windowStart until windowEnd) {
                val scrolled = with(density) { hScroll.value.toDp() }
                val x = Gutter + chCol + PerMin * ((now - windowStart) / 60000f) - (if (touch) scrolled else 0.dp)
                if (x >= Gutter + chCol) Box(
                    Modifier
                        .offset(x = x)
                        .width(2.dp)
                        .fillMaxHeight()
                        .background(Nova.Accent)
                )
            }
        }
    }
}

@Composable
private fun GuideRow(
    vm: AppViewModel,
    ch: Channel,
    windowStart: Long,
    windowEnd: Long,
    now: Long,
    chCol: Dp,
    hScroll: androidx.compose.foundation.ScrollState?,
    onFocus: (EpgProgram?) -> Unit,
    onClick: (EpgProgram?) -> Unit,
) {
    LaunchedEffect(ch.id) { vm.ensureEpg(ch, full = ch.archive) }
    val programs = vm.epg[ch.id].orEmpty().filter { it.end > windowStart && it.start < windowEnd }

    Row(Modifier.height(RowH), verticalAlignment = Alignment.CenterVertically) {
        // Chaîne
        FocusBox(
            onClick = { onClick(null) },
            onFocused = { onFocus(vm.nowPlaying(ch.id, now)) },
            modifier = Modifier.width(chCol - 8.dp).fillMaxHeight(),
            focusedScale = 1.03f,
            shape = RoundedCornerShape(8.dp),
            memoryKey = "g_ch_${ch.id}",
        ) { focused ->
            Row(
                Modifier.fillMaxSize().background(if (focused) Nova.Surface3 else Nova.Surface).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NetImage(
                    ch.logo, Modifier.size(38.dp, 30.dp).background(Color.Transparent),
                    contentScale = ContentScale.Fit,
                )
                Spacer(Modifier.width(8.dp))
                Text(ch.displayName, style = Type.Small, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.width(8.dp))
        // Programmes (défilement horizontal synchronisé sur écran tactile)
        Box(
            Modifier
                .fillMaxHeight()
                .then(if (hScroll != null) Modifier.weight(1f).horizontalScroll(hScroll) else Modifier)
        ) { Box(Modifier.width(PerMin * SPAN_MIN).fillMaxHeight()) {
            if (programs.isEmpty()) {
                ProgramCell(
                    title = if (vm.epg[ch.id] == null) "Chargement…" else "Programme non disponible",
                    x = 0.dp, w = PerMin * SPAN_MIN - 4.dp, live = true, past = false,
                    memoryKey = "g_${ch.id}_none",
                    onFocus = { onFocus(null) }, onClick = { onClick(null) },
                )
            } else {
                programs.forEach { p ->
                    val s = maxOf(p.start, windowStart)
                    val e = minOf(p.end, windowEnd)
                    val x = PerMin * ((s - windowStart) / 60000f)
                    val w = PerMin * ((e - s) / 60000f) - 4.dp
                    if (w > 6.dp) {
                        ProgramCell(
                            title = p.title, x = x, w = w, live = p.isNow(now), past = p.end <= now,
                            replay = p.end <= now && ch.archive,
                            memoryKey = "g_${ch.id}_${p.start}",
                            onFocus = { onFocus(p) }, onClick = { onClick(p) },
                        )
                    }
                }
            }
        } }
    }
}

@Composable
private fun ProgramCell(
    title: String,
    x: Dp,
    w: Dp,
    live: Boolean,
    past: Boolean,
    replay: Boolean = false,
    memoryKey: String,
    onFocus: () -> Unit,
    onClick: () -> Unit,
) {
    FocusBox(
        onClick = onClick,
        onFocused = onFocus,
        modifier = Modifier.offset(x = x).width(w).fillMaxHeight(),
        focusedScale = 1.0f,
        borderWidth = 2.dp,
        glow = false,
        shape = RoundedCornerShape(8.dp),
        memoryKey = memoryKey,
    ) { focused ->
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    when {
                        focused -> Color.White
                        live -> Nova.Surface3
                        else -> Nova.Surface
                    }
                )
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (live && !focused) {
                Box(
                    Modifier.align(Alignment.CenterStart).offset(x = (-10).dp).width(3.dp).fillMaxHeight()
                        .clip(RoundedCornerShape(2.dp)).background(Nova.Accent)
                )
            }
            Text(
                (if (replay) "↺ " else "") + title,
                style = Type.Small,
                color = when {
                    focused -> Color.Black
                    past && !replay -> Nova.Dim
                    else -> Nova.Text
                },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
