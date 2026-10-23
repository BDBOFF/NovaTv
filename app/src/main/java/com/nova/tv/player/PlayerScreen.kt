package com.nova.tv.player

import android.view.ViewGroup
import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.view.WindowManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.input.pointer.pointerInput
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.nova.tv.ui.LocalDevice
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.nova.tv.AppViewModel
import com.nova.tv.data.Http
import com.nova.tv.data.Kind
import com.nova.tv.data.PlayItem
import com.nova.tv.data.PlaySession
import com.nova.tv.ui.Nova
import com.nova.tv.ui.Type
import com.nova.tv.ui.components.Badge
import com.nova.tv.ui.components.FocusBox
import com.nova.tv.ui.components.NetImage
import com.nova.tv.ui.components.PauseGlyph
import com.nova.tv.ui.components.PlayGlyph
import com.nova.tv.ui.components.ProgressLine
import com.nova.tv.ui.components.formatClock
import com.nova.tv.ui.components.formatPosition
import com.nova.tv.ui.screens.rememberNow
import kotlinx.coroutines.delay
import java.util.Locale

/** Action "quitter le lecteur" accessible aux bandeaux (bouton retour tactile). */
private val LocalPlayerExit = staticCompositionLocalOf<() -> Unit> { {} }

private enum class Panel { AUDIO, SUBS, CHANNELS, EPISODES }

private data class TrackOption(
    val label: String,
    val group: Tracks.Group?,
    val index: Int,
    val selected: Boolean,
)

private val resizeModes = listOf(
    AspectRatioFrameLayout.RESIZE_MODE_FIT to "Adapté",
    AspectRatioFrameLayout.RESIZE_MODE_ZOOM to "Zoom",
    AspectRatioFrameLayout.RESIZE_MODE_FILL to "Étiré",
)

@Composable
fun PlayerScreen(vm: AppViewModel, session: PlaySession) {
    val context = LocalContext.current
    var index by remember { mutableIntStateOf(session.index) }
    val item: PlayItem = session.items[index.coerceIn(0, session.items.lastIndex)]
    val isLive = item.kind == Kind.LIVE

    val player = remember {
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(OkHttpDataSource.Factory(Http.client)))
            .build()
            .apply { playWhenReady = true }
    }

    var isPlaying by remember { mutableStateOf(false) }
    var buffering by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var autoRetries by remember { mutableIntStateOf(0) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var overlay by remember { mutableStateOf(true) }
    var interaction by remember { mutableLongStateOf(0L) }
    var panel by remember { mutableStateOf<Panel?>(null) }
    var tracks by remember { mutableStateOf(Tracks.EMPTY) }
    var resizeIndex by remember { mutableIntStateOf(0) }
    var seekPreview by remember { mutableLongStateOf(-1L) }
    var nextDismissedFor by remember { mutableIntStateOf(-1) }
    val remaining = if (duration > 0) duration - position else Long.MAX_VALUE
    val nextItem = session.items.getOrNull(index + 1)
    val nextCardVisible = item.kind == Kind.EPISODE && nextItem != null && vm.storage.autoNext &&
        nextDismissedFor != index && error == null && panel == null && remaining in 1..20_000


    val rootFocus = remember { FocusRequester() }
    val device = LocalDevice.current
    val activity = context as? Activity
    var gestureHint by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(gestureHint) {
        if (gestureHint != null) {
            delay(900); gestureHint = null
        }
    }
    // Téléphone / tablette : plein écran en paysage, barres système masquées
    if (device.touch && activity != null) {
        DisposableEffect(Unit) {
            val previous = activity.requestedOrientation
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            val ctrl = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
            ctrl.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            ctrl.hide(WindowInsetsCompat.Type.systemBars())
            onDispose {
                ctrl.show(WindowInsetsCompat.Type.navigationBars())
                activity.requestedOrientation = previous
                val lp = activity.window.attributes
                lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                activity.window.attributes = lp
            }
        }
    }
    val mainControl = remember { FocusRequester() }
    val now = rememberNow(20_000)

    fun touch() {
        interaction = System.currentTimeMillis()
    }

    fun showOverlay() {
        overlay = true; touch()
    }

    fun exit() {
        vm.back()
    }

    fun goTo(i: Int) {
        if (i in session.items.indices && i != index) {
            index = i; showOverlay()
        }
    }

    fun seekBy(deltaMs: Long) {
        if (isLive) return
        val dur = player.duration.takeIf { it > 0 } ?: return
        val base = if (seekPreview >= 0) seekPreview else player.currentPosition
        val target = (base + deltaMs).coerceIn(0, dur - 1000)
        seekPreview = target
        player.seekTo(target)
        showOverlay()
    }

    fun togglePlay() {
        if (player.isPlaying) player.pause() else player.play()
        showOverlay()
    }

    // ------------------------------------------------------------------ Lecteur : écouteurs + libération
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY) {
                    autoRetries = 0
                    seekPreview = -1
                }
                if (state == Player.STATE_ENDED) {
                    val cur = session.items[index]
                    if (cur.kind != Kind.LIVE) vm.saveProgress(cur, player.duration, player.duration)
                    val next = index + 1
                    if (cur.kind == Kind.EPISODE && vm.storage.autoNext && nextDismissedFor != index && next <= session.items.lastIndex) {
                        index = next
                    } else if (cur.kind != Kind.LIVE) {
                        exit()
                    }
                }
            }

            override fun onPlayerError(e: PlaybackException) {
                error = when (e.errorCode) {
                    PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ->
                        "Le serveur a refusé le flux. Le nombre maximal de connexions est peut-être atteint, ou le contenu est indisponible."
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ->
                        "Connexion au flux impossible. Vérifiez votre connexion Internet."
                    PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
                    PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
                    PlaybackException.ERROR_CODE_DECODER_INIT_FAILED ->
                        "Ce format vidéo n'est pas pris en charge par votre appareil."
                    PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW -> "Flux désynchronisé, reconnexion…"
                    else -> "Lecture impossible (${e.errorCodeName})."
                }
            }

            override fun onTracksChanged(t: Tracks) {
                tracks = t
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    // ------------------------------------------------------------------ Chargement de l'élément courant
    DisposableEffect(index) {
        val current = session.items[index]
        val start = if (index == session.index) session.startPosition else 0L
        error = null
        autoRetries = 0
        seekPreview = -1
        val builder = MediaItem.Builder().setUri(current.url)
        if (current.url.contains(".m3u8")) builder.setMimeType(MimeTypes.APPLICATION_M3U8)
        player.setMediaItem(builder.build(), start)
        player.prepare()
        player.play()
        if (current.kind == Kind.LIVE) {
            vm.saveProgress(current, 0, 0)
            current.channel?.let { vm.ensureEpg(it) }
        }
        onDispose {
            if (current.kind != Kind.LIVE) {
                vm.saveProgress(current, player.currentPosition, player.duration)
            }
        }
    }

    // Position / durée
    LaunchedEffect(Unit) {
        while (true) {
            position = player.currentPosition
            duration = player.duration.takeIf { it > 0 } ?: 0L
            delay(500)
        }
    }
    // Sauvegarde régulière de la progression
    LaunchedEffect(index) {
        while (true) {
            delay(15_000)
            val cur = session.items[index]
            if (cur.kind != Kind.LIVE) vm.saveProgress(cur, player.currentPosition, player.duration)
        }
    }
    // Reconnexion automatique du direct
    LaunchedEffect(error) {
        if (error != null && isLive && autoRetries < 3) {
            delay(1500)
            autoRetries++
            error = null
            player.seekToDefaultPosition()
            player.prepare()
            player.play()
        }
    }
    // Masquage automatique de l'interface
    LaunchedEffect(overlay, interaction, panel, isPlaying, error) {
        if (overlay && panel == null && isPlaying && error == null) {
            delay(5000)
            overlay = false
        }
    }
    // Gestion du focus quand l'interface apparaît / disparaît
    LaunchedEffect(overlay, panel, nextCardVisible) {
        delay(60)
        if (panel != null || nextCardVisible) return@LaunchedEffect
        if (overlay) runCatching { mainControl.requestFocus() } else runCatching { rootFocus.requestFocus() }
    }

    BackHandler {
        when {
            panel != null -> {
                panel = null; showOverlay()
            }
            else -> exit()
        }
    }

    fun onKey(ev: KeyEvent): Boolean {
        if (ev.type != KeyEventType.KeyDown) return false
        touch()
        when (ev.key) {
            Key.MediaPlayPause, Key.MediaPlay, Key.MediaPause -> {
                togglePlay(); return true
            }
            Key.MediaFastForward -> {
                seekBy(30_000); return true
            }
            Key.MediaRewind -> {
                seekBy(-30_000); return true
            }
            Key.ChannelUp, Key.PageUp -> {
                if (session.items.size > 1) goTo((index - 1 + session.items.size) % session.items.size); return true
            }
            Key.ChannelDown, Key.PageDown -> {
                if (session.items.size > 1) goTo((index + 1) % session.items.size); return true
            }
            Key.MediaNext -> {
                goTo(index + 1); return true
            }
            Key.MediaPrevious -> {
                goTo(index - 1); return true
            }
        }
        if (panel != null || overlay || nextCardVisible) return false
        // Interface masquée : raccourcis directs
        return when (ev.key) {
            Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                if (!isLive) togglePlay() else showOverlay(); true
            }
            Key.DirectionLeft -> {
                if (isLive) showOverlay() else seekBy(-10_000); true
            }
            Key.DirectionRight -> {
                if (isLive) showOverlay() else seekBy(10_000); true
            }
            Key.DirectionUp -> {
                if (isLive && session.items.size > 1) goTo((index - 1 + session.items.size) % session.items.size) else showOverlay(); true
            }
            Key.DirectionDown -> {
                if (isLive && session.items.size > 1) goTo((index + 1) % session.items.size) else showOverlay(); true
            }
            Key.Menu -> {
                showOverlay(); true
            }
            else -> false
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootFocus)
            .onPreviewKeyEvent { onKey(it) }
            .focusable()
    ) {
        val exo = player
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                    keepScreenOn = true
                    isFocusable = false
                    isFocusableInTouchMode = false
                    descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                    this.player = exo
                }
            },
            update = { view -> view.resizeMode = resizeModes[resizeIndex].first },
            modifier = Modifier.fillMaxSize(),
        )

        // ------------------------------------------------------------------ Gestes tactiles
        if (device.touch) {
            val audio = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(isLive) {
                        detectTapGestures(
                            onTap = { if (overlay) overlay = false else showOverlay() },
                            onDoubleTap = { o ->
                                if (!isLive) {
                                    val forward = o.x > size.width / 2
                                    seekBy(if (forward) 10_000 else -10_000)
                                    gestureHint = if (forward) "+10 s  ▶▶" else "◀◀  -10 s"
                                } else showOverlay()
                            },
                        )
                    }
                    .pointerInput(Unit) {
                        var left = true
                        var acc = 0f
                        detectVerticalDragGestures(
                            onDragStart = { o -> left = o.x < size.width / 2; acc = 0f },
                        ) { change, dy ->
                            change.consume()
                            val frac = -dy / size.height
                            if (left && activity != null) {
                                val lp = activity.window.attributes
                                val cur = if (lp.screenBrightness < 0f) 0.5f else lp.screenBrightness
                                val v = (cur + frac).coerceIn(0.02f, 1f)
                                lp.screenBrightness = v
                                activity.window.attributes = lp
                                gestureHint = "Luminosité ${(v * 100).toInt()} %"
                            } else {
                                val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                                acc += frac * max * 1.6f
                                val steps = acc.toInt()
                                if (steps != 0) {
                                    acc -= steps
                                    val v = (audio.getStreamVolume(AudioManager.STREAM_MUSIC) + steps).coerceIn(0, max)
                                    audio.setStreamVolume(AudioManager.STREAM_MUSIC, v, 0)
                                }
                                gestureHint = "Volume ${audio.getStreamVolume(AudioManager.STREAM_MUSIC) * 100 / max} %"
                            }
                        }
                    }
                    .pointerInput(isLive) {
                        if (!isLive) return@pointerInput
                        var total = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { total = 0f },
                            onDragEnd = {
                                val n = session.items.size
                                if (n > 1) {
                                    if (total < -140f) goTo((index + 1) % n)
                                    else if (total > 140f) goTo((index - 1 + n) % n)
                                }
                            },
                        ) { change, dx ->
                            change.consume()
                            total += dx
                        }
                    }
            )
        }

        if (buffering && error == null) {
            CircularProgressIndicator(
                color = Nova.Accent, strokeWidth = 3.dp,
                modifier = Modifier.align(Alignment.Center).size(46.dp),
            )
        }

        error?.let { msg ->
            ErrorCard(
                msg,
                onRetry = {
                    error = null
                    player.prepare(); player.play()
                },
                onExit = { exit() },
            )
        }

        gestureHint?.let { hint ->
            Box(
                Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.Black.copy(alpha = 0.65f))
                    .padding(horizontal = 22.dp, vertical = 12.dp)
            ) { Text(hint, style = Type.H2, color = Color.White) }
        }

        AnimatedVisibility(
            visible = overlay && error == null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) { CompositionLocalProvider(LocalPlayerExit provides { exit() }) {
            if (isLive) {
                LiveOverlay(
                    vm = vm, item = item, now = now, index = index, total = session.items.size,
                    mainControl = mainControl,
                    resizeLabel = resizeModes[resizeIndex].second,
                    onChannels = { panel = Panel.CHANNELS },
                    onAudio = { panel = Panel.AUDIO },
                    onSubs = { panel = Panel.SUBS },
                    onResize = { resizeIndex = (resizeIndex + 1) % resizeModes.size; touch() },
                    onFocusAny = { touch() },
                )
            } else {
                VodOverlay(
                    item = item,
                    position = if (seekPreview >= 0) seekPreview else position,
                    duration = duration,
                    isPlaying = isPlaying,
                    now = now,
                    hasNext = item.kind == Kind.EPISODE && index < session.items.lastIndex,
                    isEpisode = item.kind == Kind.EPISODE,
                    mainControl = mainControl,
                    resizeLabel = resizeModes[resizeIndex].second,
                    onSeek = { seekBy(it) },
                    onToggle = { togglePlay() },
                    onAudio = { panel = Panel.AUDIO },
                    onSubs = { panel = Panel.SUBS },
                    onEpisodes = { panel = Panel.EPISODES },
                    onNext = { goTo(index + 1) },
                    onResize = { resizeIndex = (resizeIndex + 1) % resizeModes.size; touch() },
                    onFocusAny = { touch() },
                    onSkipIntro = { seekBy(85_000) },
                    onSeekTo = { f ->
                        if (duration > 0) {
                            val t = (f.coerceIn(0f, 1f) * duration).toLong()
                            seekPreview = t
                            player.seekTo(t)
                            showOverlay()
                        }
                    },
                )
            }
        } }

        // ------------------------------------------------------------------ Épisode suivant (fin d'épisode)
        if (nextCardVisible && nextItem != null) {
            NextEpisodeCard(
                next = nextItem,
                secondsLeft = (remaining / 1000).toInt().coerceAtLeast(0),
                onPlay = { goTo(index + 1) },
                onCancel = { nextDismissedFor = index; showOverlay() },
                modifier = Modifier.align(Alignment.BottomEnd),
            )
        }

        // ------------------------------------------------------------------ Panneaux latéraux
        AnimatedVisibility(
            visible = panel != null,
            enter = slideInHorizontally { it } + fadeIn(),
            exit = slideOutHorizontally { it } + fadeOut(),
            modifier = Modifier.align(Alignment.CenterEnd),
        ) {
            val p = panel
            when (p) {
                Panel.AUDIO -> OptionPanel(
                    "Langue audio",
                    trackOptions(tracks, C.TRACK_TYPE_AUDIO, withOff = false),
                ) { opt ->
                    selectTrack(player, C.TRACK_TYPE_AUDIO, opt); panel = null
                }
                Panel.SUBS -> OptionPanel(
                    "Sous-titres",
                    trackOptions(tracks, C.TRACK_TYPE_TEXT, withOff = true),
                ) { opt ->
                    selectTrack(player, C.TRACK_TYPE_TEXT, opt); panel = null
                }
                Panel.CHANNELS -> ListPanel(
                    title = "Chaînes",
                    items = session.items,
                    selected = index,
                    label = { it.title },
                    sub = { it.channel?.let { ch -> vm.nowPlaying(ch.id, System.currentTimeMillis())?.title } },
                    image = { it.image },
                ) { i -> goTo(i); panel = null }
                Panel.EPISODES -> ListPanel(
                    title = "Épisodes",
                    items = session.items,
                    selected = index,
                    label = { it.subtitle },
                    sub = { null },
                    image = { it.image },
                ) { i -> goTo(i); panel = null }
                null -> Box(Modifier)
            }
        }
    }

    LaunchedEffect(Unit) {
        delay(80)
        runCatching { mainControl.requestFocus() }
    }
}

// ============================================================================== Pistes

private fun trackLabel(f: Format, i: Int): String {
    val lang = f.language?.takeIf { it.isNotBlank() && it != "und" }?.let { code ->
        val name = Locale(code).getDisplayLanguage(Locale.FRENCH)
        if (name.isBlank() || name == code) code.uppercase() else name.replaceFirstChar { it.uppercase() }
    }
    val codec = when (f.sampleMimeType) {
        MimeTypes.AUDIO_AC3 -> "Dolby Digital"
        MimeTypes.AUDIO_E_AC3, MimeTypes.AUDIO_E_AC3_JOC -> "Dolby Digital+"
        MimeTypes.AUDIO_DTS, MimeTypes.AUDIO_DTS_HD -> "DTS"
        MimeTypes.AUDIO_AAC -> "AAC"
        else -> null
    }
    val channels = when (f.channelCount) {
        1 -> "Mono"; 2 -> "Stéréo"; 6 -> "5.1"; 8 -> "7.1"; else -> null
    }
    val main = listOfNotNull(lang, f.label?.takeIf { it != lang }).joinToString(" · ").ifBlank { "Piste ${i + 1}" }
    val extra = listOfNotNull(codec, channels).joinToString(" ")
    return if (extra.isBlank()) main else "$main  ($extra)"
}

private fun trackOptions(tracks: Tracks, type: Int, withOff: Boolean): List<TrackOption> {
    val list = ArrayList<TrackOption>()
    var anySelected = false
    var n = 0
    for (g in tracks.groups) {
        if (g.type != type) continue
        for (i in 0 until g.length) {
            if (!g.isTrackSupported(i)) continue
            val sel = g.isTrackSelected(i)
            if (sel) anySelected = true
            list += TrackOption(trackLabel(g.getTrackFormat(i), n++), g, i, sel)
        }
    }
    if (withOff) list.add(0, TrackOption("Désactivés", null, -1, !anySelected))
    if (list.isEmpty()) list += TrackOption("Aucune piste disponible", null, -2, true)
    return list
}

private fun selectTrack(player: Player, type: Int, opt: TrackOption) {
    val g = opt.group
    val params = player.trackSelectionParameters.buildUpon()
    if (g == null) {
        if (opt.index == -1) params.setTrackTypeDisabled(type, true) else return
    } else {
        params.setTrackTypeDisabled(type, false)
        params.setOverrideForType(TrackSelectionOverride(g.mediaTrackGroup, opt.index))
    }
    player.trackSelectionParameters = params.build()
}

// ============================================================================== Interfaces

@Composable
private fun TopInfo(title: String, subtitle: String?, now: Long, badge: String? = null) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.85f), Color.Transparent)))
            .padding(start = 48.dp, end = 48.dp, top = 30.dp, bottom = 50.dp)
    ) {
        Column(Modifier.align(Alignment.TopStart).padding(end = 70.dp).widthIn(max = 640.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (LocalDevice.current.touch) {
                    val exitPlayer = LocalPlayerExit.current
                    Box(
                        Modifier.size(40.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.14f)).clickable { exitPlayer() },
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Filled.ArrowBack, "Retour", tint = Color.White, modifier = Modifier.size(22.dp)) }
                    Spacer(Modifier.width(12.dp))
                }
                Text(title, style = Type.H1, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                badge?.let { Spacer(Modifier.width(10.dp)); Badge(it) }
            }
            subtitle?.takeIf { it.isNotBlank() }?.let { Text(it, style = Type.H3, color = Nova.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        Text(formatClock(now), style = Type.H2, modifier = Modifier.align(Alignment.TopEnd))
    }
}

@Composable
private fun ControlButton(
    label: String,
    onClick: () -> Unit,
    onFocus: () -> Unit,
    requester: FocusRequester? = null,
    content: @Composable (Color) -> Unit,
) {
    FocusBox(
        onClick = onClick,
        onFocused = onFocus,
        focusRequester = requester,
        shape = RoundedCornerShape(50),
        focusedScale = 1.08f,
        borderWidth = 0.dp,
        glow = false,
        modifier = Modifier.height(40.dp),
    ) { focused ->
        Row(
            Modifier
                .fillMaxHeight()
                .background(if (focused) Color.White else Color.White.copy(alpha = 0.14f))
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val c = if (focused) Color.Black else Color.White
            content(c)
            if (label.isNotEmpty()) Text(label, style = Type.H3, color = c)
        }
    }
}

@Composable
private fun SeekBar(
    position: Long,
    duration: Long,
    onSeek: (Long) -> Unit,
    onFocus: () -> Unit,
    requester: FocusRequester,
    onSeekTo: (Float) -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    LaunchedEffect(focused) { if (focused) onFocus() }
    val progress = if (duration > 0) position.toFloat() / duration else 0f
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(formatPosition(position), style = Type.H3, modifier = Modifier.width(76.dp))
        Box(
            Modifier
                .weight(1f)
                .height(24.dp)
                .focusRequester(requester)
                .onKeyEvent { ev ->
                    if (ev.type != KeyEventType.KeyDown) return@onKeyEvent false
                    val repeat = ev.nativeKeyEvent.repeatCount
                    val step = when {
                        repeat > 30 -> 120_000L
                        repeat > 12 -> 60_000L
                        repeat > 4 -> 30_000L
                        else -> 10_000L
                    }
                    when (ev.key) {
                        Key.DirectionLeft -> {
                            onSeek(-step); true
                        }
                        Key.DirectionRight -> {
                            onSeek(step); true
                        }
                        else -> false
                    }
                }
                .pointerInput(duration) {
                    detectTapGestures { o -> onSeekTo(o.x / size.width) }
                }
                .pointerInput(duration) {
                    detectHorizontalDragGestures { change, _ ->
                        change.consume()
                        onSeekTo(change.position.x / size.width)
                    }
                }
                .focusable(interactionSource = interaction),
            contentAlignment = Alignment.CenterStart,
        ) {
            ProgressLine(progress, Modifier.fillMaxWidth(), if (focused) 6.dp else 4.dp)
            Box(
                Modifier
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .height(20.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Box(
                    Modifier
                        .size(if (focused) 18.dp else 12.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .border(if (focused) 3.dp else 0.dp, Nova.Accent, CircleShape)
                )
            }
        }
        Text(
            "-" + formatPosition((duration - position).coerceAtLeast(0)),
            style = Type.H3, color = Nova.Muted,
            modifier = Modifier.width(86.dp).padding(start = 10.dp),
        )
    }
}

@Composable
private fun VodOverlay(
    item: PlayItem,
    position: Long,
    duration: Long,
    isPlaying: Boolean,
    now: Long,
    hasNext: Boolean,
    isEpisode: Boolean,
    mainControl: FocusRequester,
    resizeLabel: String,
    onSeek: (Long) -> Unit,
    onToggle: () -> Unit,
    onAudio: () -> Unit,
    onSubs: () -> Unit,
    onEpisodes: () -> Unit,
    onNext: () -> Unit,
    onResize: () -> Unit,
    onFocusAny: () -> Unit,
    onSkipIntro: () -> Unit,
    onSeekTo: (Float) -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        TopInfo(item.title, item.subtitle, now)
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f))))
                .padding(start = 48.dp, end = 48.dp, top = 60.dp, bottom = 34.dp)
        ) {
            if (duration > 0) {
                val endAt = now + (duration - position)
                Text("Fin à ${formatClock(endAt)}", style = Type.Small, color = Nova.Muted, modifier = Modifier.padding(start = 76.dp, bottom = 4.dp))
            }
            SeekBar(position, duration, onSeek, onFocusAny, mainControl, onSeekTo)
            Spacer(Modifier.height(18.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                ControlButton("", onToggle, onFocusAny) { c -> if (isPlaying) PauseGlyph(c, 14.dp) else PlayGlyph(c, 14.dp) }
                if (isEpisode && position in 0..300_000) {
                    ControlButton("Passer l'intro", onSkipIntro, onFocusAny) { c -> PlayGlyph(c, 12.dp) }
                } else {
                    ControlButton("-10 s", { onSeek(-10_000) }, onFocusAny) { }
                    ControlButton("+10 s", { onSeek(10_000) }, onFocusAny) { }
                }
                ControlButton("Audio", onAudio, onFocusAny) { }
                ControlButton("Sous-titres", onSubs, onFocusAny) { }
                ControlButton(resizeLabel, onResize, onFocusAny) { }
                if (isEpisode) ControlButton("Épisodes", onEpisodes, onFocusAny) { c -> Icon(Icons.Filled.List, null, tint = c, modifier = Modifier.size(18.dp)) }
                if (hasNext) ControlButton("Épisode suivant", onNext, onFocusAny) { c -> PlayGlyph(c, 12.dp) }
            }
        }
    }
}

@Composable
private fun LiveOverlay(
    vm: AppViewModel,
    item: PlayItem,
    now: Long,
    index: Int,
    total: Int,
    mainControl: FocusRequester,
    resizeLabel: String,
    onChannels: () -> Unit,
    onAudio: () -> Unit,
    onSubs: () -> Unit,
    onResize: () -> Unit,
    onFocusAny: () -> Unit,
) {
    val ch = item.channel
    val programs = ch?.let { vm.epg[it.id] }
    val current = programs?.firstOrNull { it.isNow(now) }
    val next = programs?.firstOrNull { it.start >= (current?.end ?: now) }
    val fav = ch != null && vm.isFav(Kind.LIVE, ch.id)

    Box(Modifier.fillMaxSize()) {
        TopInfo(
            item.title,
            "Chaîne ${index + 1} / $total  ·  " + if (LocalDevice.current.isTv) "▲▼ pour zapper" else "glissez ◀ ▶ pour zapper",
            now, badge = "EN DIRECT",
        )
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.92f))))
                .padding(start = 48.dp, end = 48.dp, top = 60.dp, bottom = 34.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(92.dp, 62.dp).clip(RoundedCornerShape(12.dp)).background(Nova.Surface2),
                    contentAlignment = Alignment.Center,
                ) {
                    NetImage(item.image, Modifier.size(78.dp, 50.dp).background(Color.Transparent), contentScale = ContentScale.Fit)
                }
                Spacer(Modifier.width(18.dp))
                Column(Modifier.weight(1f)) {
                    if (current != null) {
                        Text(current.title, style = Type.H2, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${formatClock(current.start)} – ${formatClock(current.end)}", style = Type.Small, color = Nova.Muted)
                            Spacer(Modifier.width(12.dp))
                            ProgressLine(current.progress(now), Modifier.width(260.dp))
                        }
                        current.description?.let {
                            Text(it, style = Type.Small, color = Nova.Dim, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(620.dp))
                        }
                    } else {
                        Text(item.title, style = Type.H2)
                        Text(if (programs == null) "Chargement du programme…" else "Programme non disponible", style = Type.Small, color = Nova.Muted)
                    }
                    next?.let {
                        Spacer(Modifier.height(4.dp))
                        Text("Ensuite · ${formatClock(it.start)}  ${it.title}", style = Type.Small, color = Nova.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ControlButton("Chaînes", onChannels, onFocusAny, mainControl) { c -> Icon(Icons.Filled.List, null, tint = c, modifier = Modifier.size(18.dp)) }
                if (ch != null) {
                    ControlButton(if (fav) "Favori" else "Ajouter aux favoris", {
                        vm.toggleFav(Kind.LIVE, ch.id, ch.displayName, ch.logo); onFocusAny()
                    }, onFocusAny) { c ->
                        Icon(if (fav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, null, tint = if (fav) Nova.Accent else c, modifier = Modifier.size(18.dp))
                    }
                }
                ControlButton("Audio", onAudio, onFocusAny) { }
                ControlButton("Sous-titres", onSubs, onFocusAny) { }
                ControlButton(resizeLabel, onResize, onFocusAny) { }
            }
        }
    }
}

@Composable
private fun NextEpisodeCard(
    next: PlayItem,
    secondsLeft: Int,
    onPlay: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier,
) {
    val playFocus = remember { FocusRequester() }
    LaunchedEffect(next.id) {
        delay(80); runCatching { playFocus.requestFocus() }
    }
    Row(
        modifier
            .padding(end = 48.dp, bottom = 48.dp)
            .width(560.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Nova.Surface.copy(alpha = 0.96f))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NetImage(next.image, Modifier.size(176.dp, 99.dp).clip(RoundedCornerShape(10.dp)))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text("Épisode suivant dans $secondsLeft s", style = Type.Small, color = Nova.Accent)
            Text(next.subtitle, style = Type.H3, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(10.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ControlButton("Regarder", onPlay, {}, playFocus) { c -> PlayGlyph(c, 12.dp) }
                ControlButton("Annuler", onCancel, {}) { }
            }
        }
    }
}

@Composable
private fun ErrorCard(message: String, onRetry: () -> Unit, onExit: () -> Unit) {
    val retry = remember { FocusRequester() }
    LaunchedEffect(message) {
        delay(80); runCatching { retry.requestFocus() }
    }
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f)), contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .width(520.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Nova.Surface)
                .padding(28.dp)
        ) {
            Text("Oups, la lecture a échoué", style = Type.H1)
            Spacer(Modifier.height(10.dp))
            Text(message, style = Type.Body, color = Nova.Muted)
            Spacer(Modifier.height(22.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ControlButton("Réessayer", onRetry, {}, retry) { c -> Icon(Icons.Filled.Refresh, null, tint = c, modifier = Modifier.size(18.dp)) }
                ControlButton("Quitter", onExit, {}) { }
            }
        }
    }
}

@Composable
private fun PanelFrame(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier
            .width(360.dp)
            .fillMaxHeight()
            .background(Nova.Surface.copy(alpha = 0.97f))
            .padding(top = 30.dp)
    ) {
        Text(title, style = Type.H1, modifier = Modifier.padding(horizontal = 24.dp))
        Spacer(Modifier.height(14.dp))
        content()
    }
}

@Composable
private fun OptionPanel(title: String, options: List<TrackOption>, onSelect: (TrackOption) -> Unit) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(120); runCatching { first.requestFocus() }
    }
    val selectedIdx = options.indexOfFirst { it.selected }.coerceAtLeast(0)
    PanelFrame(title) {
        LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            itemsIndexed(options) { i, opt ->
                FocusBox(
                    onClick = { onSelect(opt) },
                    focusRequester = if (i == selectedIdx) first else null,
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    focusedScale = 1.02f,
                    borderWidth = 0.dp,
                    glow = false,
                ) { focused ->
                    Row(
                        Modifier.fillMaxSize().background(if (focused) Color.White else Color.Transparent).padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            opt.label, style = Type.H3, color = if (focused) Color.Black else Nova.Text,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                        )
                        if (opt.selected) Icon(Icons.Filled.Check, null, tint = if (focused) Color.Black else Nova.Accent, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ListPanel(
    title: String,
    items: List<PlayItem>,
    selected: Int,
    label: (PlayItem) -> String,
    sub: (PlayItem) -> String?,
    image: (PlayItem) -> String?,
    onSelect: (Int) -> Unit,
) {
    val state = rememberLazyListState()
    val sel = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        state.scrollToItem((selected - 3).coerceAtLeast(0))
        delay(150)
        runCatching { sel.requestFocus() }
    }
    PanelFrame(title) {
        LazyColumn(
            state = state,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            itemsIndexed(items) { i, it ->
                FocusBox(
                    onClick = { onSelect(i) },
                    focusRequester = if (i == selected) sel else null,
                    modifier = Modifier.fillMaxWidth().height(58.dp),
                    focusedScale = 1.02f,
                    borderWidth = 0.dp,
                    glow = false,
                ) { focused ->
                    Row(
                        Modifier
                            .fillMaxSize()
                            .background(
                                when {
                                    focused -> Color.White
                                    i == selected -> Nova.Surface3
                                    else -> Color.Transparent
                                }
                            )
                            .padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        NetImage(
                            image(it), Modifier.size(54.dp, 38.dp).clip(RoundedCornerShape(6.dp)),
                            contentScale = if (it.kind == Kind.LIVE) ContentScale.Fit else ContentScale.Crop,
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                label(it), style = Type.H3, color = if (focused) Color.Black else Nova.Text,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                            sub(it)?.let { s ->
                                Text(s, style = Type.Small, color = if (focused) Color.DarkGray else Nova.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        if (i == selected) {
                            Spacer(Modifier.width(6.dp))
                            Box(Modifier.size(8.dp).clip(CircleShape).background(Nova.Accent))
                        }
                    }
                }
            }
        }
    }
}
