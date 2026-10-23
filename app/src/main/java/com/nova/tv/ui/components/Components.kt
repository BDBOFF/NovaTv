package com.nova.tv.ui.components

import com.nova.tv.ui.Gutter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
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

/** Après un appui long, on ignore OK jusqu'à ce que la touche soit relâchée. */
object LongPressGuard {
    @Volatile
    var active = false
}
val LocalScreenKey = staticCompositionLocalOf { "" }

/**
 * Brique de base de toute l'interface TV : élément focalisable à la télécommande,
 * avec zoom, contour lumineux et mémorisation du focus.
 */
@OptIn(ExperimentalFoundationApi::class)
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
    onLongClick: (() -> Unit)? = null,
    content: @Composable BoxScope.(focused: Boolean) -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        when {
            pressed -> 0.96f
            focused -> focusedScale
            else -> 1f
        },
        tween(120), label = "focusScale",
    )
    val longState = remember { booleanArrayOf(false) }
    val onClickState by rememberUpdatedState(onClick)
    val onLongState by rememberUpdatedState(onLongClick)
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
            .clip(shape)
            .then(if (focused) Modifier.border(borderWidth, borderColor, shape) else Modifier)
            .focusRequester(requester)
            .then(
                if (onLongClick == null) Modifier
                else Modifier.onPreviewKeyEvent { ev ->
                    val k = ev.key
                    if (k != Key.DirectionCenter && k != Key.Enter && k != Key.NumPadEnter) return@onPreviewKeyEvent false
                    when (ev.type) {
                        KeyEventType.KeyDown -> {
                            if (ev.nativeKeyEvent.repeatCount == 0) longState[0] = false
                            if (ev.nativeKeyEvent.repeatCount >= 1 && !longState[0]) {
                                longState[0] = true
                                // le relâchement de OK ne doit pas valider ce qui s'ouvre ensuite
                                LongPressGuard.active = true
                                onLongState?.invoke()
                            }
                            true
                        }
                        KeyEventType.KeyUp -> {
                            if (!longState[0]) onClickState()
                            longState[0] = false
                            true
                        }
                        else -> false
                    }
                }
            )
            .then(
                if (onLongClick != null) Modifier.combinedClickable(
                    interactionSource = interaction,
                    indication = null,
                    onLongClick = onLongClick,
                    onClick = onClick,
                )
                else Modifier.clickable(interactionSource = interaction, indication = null, onClick = onClick)
            )
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
    val WideW = 200.dp
    val WideH = 112.dp
}

/** Tuile d'exploration (saga, genre, année) : image + grand titre. */
@Composable
fun ExploreCard(tile: Tile, onClick: () -> Unit, modifier: Modifier = Modifier, onFocused: () -> Unit = {}) {
    FocusBox(
        onClick = onClick,
        modifier = modifier.width(CardSize.WideW).height(CardSize.WideH),
        memoryKey = tile.key,
        onFocused = onFocused,
    ) { focused ->
        NetImage(tile.image, Modifier.fillMaxSize())
        Box(
            Modifier.fillMaxSize().background(
                Brush.linearGradient(
                    listOf(Color.Black.copy(alpha = if (focused) 0.35f else 0.6f), Nova.Accent.copy(alpha = if (focused) 0.35f else 0.18f))
                )
            )
        )
        Column(Modifier.align(Alignment.Center).padding(horizontal = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                tile.title, style = Type.H1, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            tile.subtitle?.let {
                Text(it, style = Type.Small, color = Color.White.copy(alpha = 0.85f), maxLines = 1, textAlign = TextAlign.Center)
            }
        }
    }
}

/** Carte "Match du jour" : chaîne, horaire, affiche du match. */
@Composable
fun MatchCard(tile: Tile, onClick: () -> Unit, modifier: Modifier = Modifier, onFocused: () -> Unit = {}) {
    val p = tile.program
    val now = System.currentTimeMillis()
    val live = p != null && p.isNow(now)
    FocusBox(
        onClick = onClick,
        modifier = modifier.width(CardSize.LandW).height(CardSize.LandH),
        memoryKey = tile.key,
        onFocused = onFocused,
    ) { focused ->
        Box(
            Modifier.fillMaxSize().background(
                Brush.linearGradient(listOf(if (focused) Nova.Surface3 else Nova.Surface2, Color(0xFF2A0306)))
            )
        )
        Column(Modifier.fillMaxSize().padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                NetImage(tile.image, Modifier.size(34.dp, 24.dp).background(Color.Transparent), contentScale = ContentScale.Fit)
                Spacer(Modifier.width(8.dp))
                Text(tile.subtitle ?: "", style = Type.Small, color = Nova.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                if (live) Badge("EN DIRECT") else if (p != null) Badge(formatClock(p.start), filled = false)
            }
            Spacer(Modifier.weight(1f))
            Text(tile.title, style = Type.H3, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (live && p != null) {
                Spacer(Modifier.height(6.dp))
                ProgressLine(p.progress(now), Modifier.fillMaxWidth())
            } else if (p != null) {
                Text("${formatClock(p.start)} – ${formatClock(p.end)}", style = Type.Small, color = Nova.Muted)
            }
        }
    }
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
    onLongClick: (() -> Unit)? = null,
) {
    Column(if (fill) modifier.fillMaxWidth() else modifier.width(width)) {
        FocusBox(
            onClick = onClick,
            onLongClick = onLongClick,
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
fun RankCard(tile: Tile, onClick: () -> Unit, modifier: Modifier = Modifier, onFocused: () -> Unit = {}, onLongClick: (() -> Unit)? = null) {
    val rank = tile.rank ?: 0
    // L'affiche chevauche toujours un peu le chiffre, même à 2 chiffres
    val numberZone = if (rank >= 10) 118.dp else 62.dp
    Box(modifier.width(CardSize.PosterW + numberZone).height(CardSize.PosterH)) {
        RankNumber(
            "$rank",
            Modifier.align(Alignment.BottomStart).offset(x = (-6).dp, y = 22.dp),
        )
        FocusBox(
            onClick = onClick,
            modifier = Modifier.align(Alignment.CenterEnd).width(CardSize.PosterW).height(CardSize.PosterH),
            memoryKey = tile.key,
            onLongClick = onLongClick,
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
    onLongClick: (() -> Unit)? = null,
) {
    FocusBox(
        onClick = onClick,
        modifier = modifier.width(CardSize.ChanW).height(CardSize.ChanH),
        memoryKey = tile.key,
        onLongClick = onLongClick,
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

/**
 * Gros chiffre du classement façon Netflix : contour clair et intérieur sombre,
 * lisible même quand l'affiche le chevauche légèrement.
 */
/** Style des gros chiffres du classement (réglable dans Réglages → Lecture & affichage). */
enum class RankStyle(val label: String) { NEON("Néon rouge"), CLASSIC("Classique"), OUTLINE("Contour") }

val LocalRankStyle = staticCompositionLocalOf { RankStyle.NEON }

@Composable
private fun RankNumber(text: String, modifier: Modifier) {
    val two = text.length >= 2
    val size = if (two) 140.sp else 150.sp
    val spacing = if (two) (-14).sp else (-12).sp
    when (LocalRankStyle.current) {
        RankStyle.CLASSIC -> Text(
            text, fontFamily = Inter, fontWeight = FontWeight.Black, fontSize = size, lineHeight = size,
            letterSpacing = spacing, color = Color.White.copy(alpha = 0.18f), maxLines = 1, modifier = modifier,
        )
        RankStyle.OUTLINE, RankStyle.NEON -> {
            val neon = LocalRankStyle.current == RankStyle.NEON
            val outline = if (neon) Nova.Accent else Color(0xFF9A9AA8)
            val fill = if (neon) Color(0xFF1A0003) else Nova.Bg
            val w = if (neon) 2.5.dp else 2.dp
            Box(modifier) {
                if (neon) {
                    // halo diffus
                    for ((dx, dy) in listOf(-3 to 0, 3 to 0, 0 to -3, 0 to 3)) {
                        Text(
                            text, fontFamily = Inter, fontWeight = FontWeight.Black, fontSize = size, lineHeight = size,
                            letterSpacing = spacing, color = Nova.Accent.copy(alpha = 0.25f), maxLines = 1,
                            modifier = Modifier.offset(x = w * dx, y = w * dy),
                        )
                    }
                }
                for ((dx, dy) in listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1, -1 to -1, 1 to 1, -1 to 1, 1 to -1)) {
                    Text(
                        text, fontFamily = Inter, fontWeight = FontWeight.Black, fontSize = size, lineHeight = size,
                        letterSpacing = spacing, color = outline, maxLines = 1,
                        modifier = Modifier.offset(x = w * dx, y = w * dy),
                    )
                }
                Text(
                    text, fontFamily = Inter, fontWeight = FontWeight.Black, fontSize = size, lineHeight = size,
                    letterSpacing = spacing, color = fill, maxLines = 1,
                )
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
    onTileLongClick: ((Tile) -> Unit)? = null,
    onSeeAll: (() -> Unit)? = null,
    channelInfo: @Composable (Tile) -> Pair<String?, Float?> = { null to null },
) {
    Column(Modifier.fillMaxWidth()) {
        Text(rail.title, style = Type.H2, modifier = Modifier.padding(start = Gutter, bottom = 10.dp))
        val h = when (rail.style) {
            RailStyle.POSTER, RailStyle.RANK -> CardSize.PosterH
            RailStyle.LANDSCAPE -> CardSize.LandH
            RailStyle.CHANNEL -> CardSize.ChanH
            RailStyle.WIDE -> CardSize.WideH
            RailStyle.MATCH -> CardSize.LandH
        }
        EdgeScroll { LazyRow(
            contentPadding = PaddingValues(horizontal = Gutter, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(if (rail.style == RailStyle.RANK) 6.dp else 14.dp),
            modifier = Modifier.fillMaxWidth().height(h + 20.dp),
        ) {
            items(rail.tiles, key = { it.key }) { t ->
                when (rail.style) {
                    RailStyle.POSTER -> PosterCard(t, { onTileClick(t) }, onFocused = { onTileFocused(t) }, onLongClick = onTileLongClick?.let { f -> { f(t) } })
                    RailStyle.RANK -> RankCard(t, { onTileClick(t) }, onFocused = { onTileFocused(t) }, onLongClick = onTileLongClick?.let { f -> { f(t) } })
                    RailStyle.LANDSCAPE -> LandscapeCard(t, { onTileClick(t) }, onFocused = { onTileFocused(t) })
                    RailStyle.CHANNEL -> {
                        val (now, p) = channelInfo(t)
                        ChannelTileCard(t, { onTileClick(t) }, nowTitle = now, progress = p, onFocused = { onTileFocused(t) }, onLongClick = onTileLongClick?.let { f -> { f(t) } })
                    }
                    RailStyle.WIDE -> ExploreCard(t, { onTileClick(t) }, onFocused = { onTileFocused(t) })
                    RailStyle.MATCH -> MatchCard(t, { onTileClick(t) }, onFocused = { onTileFocused(t) })
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
fun EdgeScroll(edge: Dp = Gutter, content: @Composable () -> Unit) {
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
