package com.nova.tv.player

import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.nova.tv.data.Http
import com.nova.tv.ui.Nova
import com.nova.tv.ui.components.Badge
import com.nova.tv.ui.components.NetImage
import kotlinx.coroutines.delay

/**
 * Mini-lecture muette de la chaîne sélectionnée (TV Direct).
 * Démarre après une courte pause pour ne pas charger chaque chaîne survolée.
 */
@Composable
fun LivePreview(url: String?, logo: String?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val player = remember {
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(OkHttpDataSource.Factory(Http.client)))
            .build()
            .apply { volume = 0f; playWhenReady = true }
    }
    var ready by remember { mutableStateOf(false) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() {
                ready = true
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    LaunchedEffect(url) {
        ready = false
        player.stop()
        player.clearMediaItems()
        if (url != null) {
            delay(1200)
            val b = MediaItem.Builder().setUri(url)
            if (url.contains(".m3u8")) b.setMimeType(MimeTypes.APPLICATION_M3U8)
            player.setMediaItem(b.build())
            player.prepare()
            player.play()
        }
    }

    Box(modifier.clip(RoundedCornerShape(14.dp)).background(Color.Black)) {
        val exo = player
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    isFocusable = false
                    isFocusableInTouchMode = false
                    descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                    this.player = exo
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
        if (!ready) {
            Box(Modifier.fillMaxSize().background(Nova.Surface2), contentAlignment = Alignment.Center) {
                NetImage(
                    logo,
                    Modifier.padding(40.dp).fillMaxSize().background(Color.Transparent),
                    contentScale = ContentScale.Fit,
                )
            }
        }
        Badge("APERÇU", Modifier.align(Alignment.TopStart).padding(10.dp))
    }
}
