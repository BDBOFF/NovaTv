package com.nova.tv.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.nova.tv.ui.FocusMemory
import com.nova.tv.ui.Inter
import com.nova.tv.ui.Nova
import com.nova.tv.ui.Rail
import com.nova.tv.ui.RailStyle
import com.nova.tv.ui.Tile
import com.nova.tv.ui.Type
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

val LocalFocusMemory = staticCompositionLocalOf { FocusMemory() }
val LocalScreenKey = staticCompositionLocalOf { "" }

/**
 * Brique de base de toute l'interface TV : élément focalisable à la télécommande,
 * avec zoom, contour lumineux et mémorisation du focus.
 */
@Composable
fun FocusBox(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(10.dp),
    focusedScale: Float = 1.07f,
    borderColor: Color = Color.White,
    borderWidth: Dp = 2.5.dp,
    glow: Boolean = true,
    memoryKey: String? = null,
    focusRequester: FocusRequester? = null,
    onFocused: () -> Unit = {},
    content: @Composable BoxScope.(focused: Boolean) -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (focused) focusedScale else 1f, tween(170), label = "focusScale")
    val memory = LocalFocusMemory.current
    val screen = LocalScreenKey.current
    val requester = focusRequester ?: remember { FocusRequester() }
    val onFocusedState by rememberUpdatedState(onFocused)

    LaunchedEffect(focused) {
        if (focused) {
            if (memoryKey != null && screen.isNotEmpty()) memory.last[screen] = memoryKey
            onFocusedState()
        }
    }
    if (memoryKey != null) {
        LaunchedEffect(Unit) {
            if (screen in memory.pending && memory.last[screen] == memoryKey) {
                memory.pending.remove(screen)
                delay(60)
                runCatching { requester.requestFocus() }
            }
        }
    }

    Box(
        modifier
            .zIndex(if (focused) 1f else 0f)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .then(
                if (focused && glow) Modifier.shadow(20.dp, shape, ambientColor = Color.Black, spotColor = Color.Black)
                else Modifier
            )
            .clip(shape)
            .then(if (focused) Modifier.border(borderWidth, borderColor, shape) else Modifier)
            .focusRequester(requester)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
    ) {
        content(focused)
    }
}

/** Bouton en pilule (Lecture, Plus d'infos…). */
@Composable
fun NovaButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    glyph: (@Composable (Color) -> Unit)? = null,
    primary: Boolean = false,
    focusRequester: FocusRequester? = null,
    memoryKey: String? = null,
    onFocused: () -> Unit = {},
) {
    FocusBox(
        onClick = onClick,
        modifier = modifier.height(40.dp),
        shape = RoundedCornerShape(50),
        focusedScale = 1.06f,
        borderWidth = 0.dp,
        glow = false,
        focusRequester = focusRequester,
        memoryKey = memoryKey,
        onFocused = onFocused,
    ) { focused ->
        val bg: Brush = when {
            focused -> SolidColor(Color.White)
            primary -> Nova.AccentBrush
            else -> SolidColor(Color.White.copy(alpha = 0.12f))
        }
        val fg = if (focused) Color.Black else Color.White
        Row(
            Modifier
                .background(bg)
                .fillMaxHeight()
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (glyph != null) glyph(fg)
            else if (icon != null) Icon(icon, null, tint = fg, modifier = Modifier.size(18.dp))
            Text(text, style = Type.H3, color = fg, maxLines = 1)
        }
    }
}

/** Bouton rond à icône. */
@Composable
fun RoundButton(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    onFocused: () -> Unit = {},
) {
    FocusBox(onClick = onClick, modifier = modifier.size(40.dp), shape = CircleShape, borderWidth = 0.dp, glow = false, onFocused = onFocused) { focused ->
        Box(
            Modifier.fillMaxSize().background(if (focused) Color.White else Color.White.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = if (focused) Color.Black else if (active) Nova.Accent else Color.White, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
fun PlayGlyph(color: Color, size: Dp = 14.dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val p = Path()
        p.moveTo(w * 0.12f, 0f)
        p.lineTo(w, h / 2f)
        p.lineTo(w * 0.12f, h)
        p.close()
        drawPath(p, color)
    }
}

@Composable
fun PauseGlyph(color: Color, size: Dp = 14.dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        drawRect(color, topLeft = Offset(w * 0.12f, 0f), size = androidx.compose.ui.geometry.Size(w * 0.28f, h))
        drawRect(color, topLeft = Offset(w * 0.60f, 0f), size = androidx.compose.ui.geometry.Size(w * 0.28f, h))
    }
}

@Composable
fun Badge(text: String, modifier: Modifier = Modifier, filled: Boolean = true) {
    Box(
        modifier
            .clip(RoundedCornerShape(4.dp))
            .then(
                if (filled) Modifier.background(Nova.AccentBrush)
                else Modifier.border(1.dp, Nova.Muted, RoundedCornerShape(4.dp))
            )
            .padding(horizontal = 6.dp, vertical = 3.dp)
    ) {
        Text(text, style = Type.Label, color = if (filled) Color.White else Nova.Muted, maxLines = 1)
    }
}

@Composable
fun ProgressLine(progress: Float, modifier: Modifier = Modifier, height: Dp = 3.dp) {
    Box(
        modifier
            .height(height)
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.22f))
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .background(Nova.AccentBrush)
        )
    }
}

/** Image réseau avec fond de repli (titre affiché si l'image manque). */
@Composable
fun NetImage(
    url: String?,
    modifier: Modifier = Modifier,
    fallbackText: String? = null,
    contentScale: ContentScale = ContentScale.Crop,
) {
    Box(modifier.background(Brush.linearGradient(listOf(Nova.Surface3, Nova.Surface))), contentAlignment = Alignment.Center) {
        if (fallbackText != null) {
            Text(
                fallbackText, style = Type.H3, color = Nova.Muted, textAlign = TextAlign.Center,
                maxLines = 4, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(10.dp),
            )
        }
        if (!url.isNullOrBlank()) {
            AsyncImage(model = url, contentDescription = null, contentScale = contentScale, modifier = Modifier.fillMaxSize())
        }
    }
}

// ------------------------------------------------------------------------------ Cartes

object CardSize {
    val PosterW = 112.dp
    val PosterH = 168.dp
    val LandW = 228.dp
    val LandH = 128.dp
    val ChanW = 176.dp
    val ChanH = 99.dp
}

@Composable
fun PosterCard(
    tile: Tile,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = CardSize.PosterW,
    showTitle: Boolean = false,
    fill: Boolean = false,
    focusRequester: FocusRequester? = null,
    onFocused: () -> Unit = {},
) {
    Column(if (fill) modifier.fillMaxWidth() else modifier.width(width)) {
        FocusBox(
            onClick = onClick,
            focusRequester = focusRequester,
            modifier = if (fill) Modifier.fillMaxWidth().aspectRatio(2f / 3f) else Modifier.width(width).height(width * 1.5f),
            memoryKey = tile.key,
            onFocused = onFocused,
        ) { _ ->
            NetImage(tile.image, Modifier.fillMaxSize(), fallbackText = tile.title)
            tile.badge?.let {
                Badge(it, Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp))
            }
            tile.progress?.let {
                ProgressLine(it, Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(6.dp))
            }
        }
        if (showTitle) {
            Spacer(Modifier.height(8.dp))
            Text(tile.title, style = Type.Small, color = Nova.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun LandscapeCard(tile: Tile, onClick: () -> Unit, modifier: Modifier = Modifier, onFocused: () -> Unit = {}) {
    Column(modifier.width(CardSize.LandW)) {
        FocusBox(
            onClick = onClick,
            modifier = Modifier.width(CardSize.LandW).height(CardSize.LandH),
            memoryKey = tile.key,
            onFocused = onFocused,
        ) { focused ->
            NetImage(tile.image, Modifier.fillMaxSize(), fallbackText = tile.title)
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(0.45f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.85f))
                )
            )
            Box(
                Modifier
                    .align(Alignment.Center)
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(if (focused) Nova.Accent else Color.Black.copy(alpha = 0.45f))
                    .border(1.dp, Color.White.copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center,
            ) { Box(Modifier.offset(x = 1.5.dp)) { PlayGlyph(Color.White, 13.dp) } }
            Column(Modifier.align(Alignment.BottomStart).padding(horizontal = 10.dp, vertical = 9.dp)) {
                Text(tile.title, style = Type.H3, maxLines = 1, overflow = TextOverflow.Ellipsis)
                tile.subtitle?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = Type.Small, color = Nova.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                tile.progress?.let {
                    Spacer(Modifier.height(6.dp))
                    ProgressLine(it, Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
fun RankCard(tile: Tile, onClick: () -> Unit, modifier: Modifier = Modifier, onFocused: () -> Unit = {}) {
    Box(modifier.width(CardSize.PosterW + 62.dp).height(CardSize.PosterH)) {
        Text(
            text = "${tile.rank ?: ""}",
            fontFamily = Inter,
            fontWeight = FontWeight.Black,
            fontSize = 150.sp,
            lineHeight = 150.sp,
            letterSpacing = (-12).sp,
            color = Color.White.copy(alpha = 0.16f),
            modifier = Modifier.align(Alignment.BottomStart).offset(x = (-6).dp, y = 22.dp),
            maxLines = 1,
        )
        FocusBox(
            onClick = onClick,
            modifier = Modifier.align(Alignment.CenterEnd).width(CardSize.PosterW).height(CardSize.PosterH),
            memoryKey = tile.key,
            onFocused = onFocused,
        ) { _ ->
            NetImage(tile.image, Modifier.fillMaxSize(), fallbackText = tile.title)
            tile.badge?.let { Badge(it, Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp)) }
        }
    }
}

@Composable
fun ChannelTileCard(
    tile: Tile,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    nowTitle: String? = null,
    progress: Float? = null,
    onFocused: () -> Unit = {},
) {
    FocusBox(
        onClick = onClick,
        modifier = modifier.width(CardSize.ChanW).height(CardSize.ChanH),
        memoryKey = tile.key,
        onFocused = onFocused,
    ) { focused ->
        Box(Modifier.fillMaxSize().background(if (focused) Nova.Surface3 else Nova.Surface2))
        NetImage(
            tile.image,
            Modifier.align(Alignment.Center).padding(bottom = 18.dp).size(110.dp, 46.dp).background(Color.Transparent),
            contentScale = ContentScale.Fit,
            fallbackText = if (tile.image == null) tile.title else null,
        )
        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(horizontal = 9.dp, vertical = 7.dp)) {
            Text(nowTitle ?: tile.title, style = Type.Small, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (progress != null) {
                Spacer(Modifier.height(4.dp))
                ProgressLine(progress, Modifier.fillMaxWidth(), 2.dp)
            }
        }
    }
}

@Composable
fun SeeAllCard(onClick: () -> Unit, height: Dp, width: Dp = 112.dp) {
    FocusBox(onClick = onClick, modifier = Modifier.width(width).height(height)) { focused ->
        Box(
            Modifier.fillMaxSize().background(if (focused) Nova.Surface3 else Nova.Surface),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(if (focused) Nova.Accent else Nova.Surface3),
                    contentAlignment = Alignment.Center,
                ) { Text("→", style = Type.H2) }
                Spacer(Modifier.height(10.dp))
                Text("Tout voir", style = Type.H3)
            }
        }
    }
}

/** Rangée horizontale titrée de l'accueil / des fiches. */
@Composable
fun RailRow(
    rail: Rail,
    onTileClick: (Tile) -> Unit,
    onTileFocused: (Tile) -> Unit = {},
    onSeeAll: (() -> Unit)? = null,
    channelInfo: @Composable (Tile) -> Pair<String?, Float?> = { null to null },
) {
    Column(Modifier.fillMaxWidth()) {
        Text(rail.title, style = Type.H2, modifier = Modifier.padding(start = 48.dp, bottom = 10.dp))
        val h = when (rail.style) {
            RailStyle.POSTER, RailStyle.RANK -> CardSize.PosterH
            RailStyle.LANDSCAPE -> CardSize.LandH
            RailStyle.CHANNEL -> CardSize.ChanH
        }
        EdgeScroll { LazyRow(
            contentPadding = PaddingValues(horizontal = 48.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(if (rail.style == RailStyle.RANK) 6.dp else 14.dp),
            modifier = Modifier.fillMaxWidth().height(h + 20.dp),
        ) {
            items(rail.tiles, key = { it.key }) { t ->
                when (rail.style) {
                    RailStyle.POSTER -> PosterCard(t, { onTileClick(t) }, onFocused = { onTileFocused(t) })
                    RailStyle.RANK -> RankCard(t, { onTileClick(t) }, onFocused = { onTileFocused(t) })
                    RailStyle.LANDSCAPE -> LandscapeCard(t, { onTileClick(t) }, onFocused = { onTileFocused(t) })
                    RailStyle.CHANNEL -> {
                        val (now, p) = channelInfo(t)
                        ChannelTileCard(t, { onTileClick(t) }, nowTitle = now, progress = p, onFocused = { onTileFocused(t) })
                    }
                }
            }
            if (onSeeAll != null) {
                item(key = "seeall") {
                    SeeAllCard(onSeeAll, h, if (rail.style == RailStyle.LANDSCAPE) CardSize.LandW else CardSize.PosterW)
                }
            }
        } }
    }
}

/**
 * Défilement "pivot" façon Google TV : l'élément focalisé se place toujours au même endroit,
 * au lieu de coller au bord de l'écran.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PivotScroll(pivot: Dp = 0.dp, content: @Composable () -> Unit) {
    val px = with(LocalDensity.current) { pivot.toPx() }
    val spec = remember(px) {
        object : BringIntoViewSpec {
            override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
                return offset - px
            }
        }
    }
    CompositionLocalProvider(LocalBringIntoViewSpec provides spec, content = content)
}

/** Remet le comportement standard (pour les rangées horizontales imbriquées). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EdgeScroll(edge: Dp = 48.dp, content: @Composable () -> Unit) {
    val px = with(LocalDensity.current) { edge.toPx() }
    val spec = remember(px) {
        object : BringIntoViewSpec {
            override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
                val end = offset + size
                return when {
                    offset < px -> offset - px
                    end > containerSize - px -> end - (containerSize - px)
                    else -> 0f
                }
            }
        }
    }
    CompositionLocalProvider(LocalBringIntoViewSpec provides spec, content = content)
}

// ------------------------------------------------------------------------------ Formats

fun formatClock(ms: Long): String = SimpleDateFormat("HH:mm", Locale.FRANCE).format(Date(ms))

fun formatDate(ms: Long): String = SimpleDateFormat("d MMMM yyyy", Locale.FRANCE).format(Date(ms))

fun formatRuntime(min: Int?): String? {
    if (min == null || min <= 0) return null
    val h = min / 60
    val m = min % 60
    return if (h > 0) "${h} h ${m.toString().padStart(2, '0')}" else "$m min"
}

fun formatPosition(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}
