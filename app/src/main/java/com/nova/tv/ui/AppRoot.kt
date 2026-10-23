package com.nova.tv.ui

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.layout.safeDrawingPadding
import com.nova.tv.AppViewModel
import com.nova.tv.player.PlayerScreen
import com.nova.tv.ui.components.LocalFocusMemory
import com.nova.tv.ui.components.LocalRankStyle
import com.nova.tv.ui.components.RankStyle
import com.nova.tv.ui.components.LocalScreenKey
import com.nova.tv.ui.screens.CatalogScreen
import com.nova.tv.ui.screens.GridScreen
import com.nova.tv.ui.screens.SagaScreen
import com.nova.tv.ui.screens.LiveScreen
import com.nova.tv.ui.screens.TmdbDetailScreen
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import com.nova.tv.ui.screens.LoginScreen
import com.nova.tv.ui.screens.MainScreen
import com.nova.tv.ui.screens.MovieDetailScreen
import com.nova.tv.ui.screens.SeriesDetailScreen
import com.nova.tv.ui.screens.SplashScreen

@Composable
fun AppRoot(vm: AppViewModel) {
    val route = vm.backStack.lastOrNull() ?: Route.Splash
    val holder = rememberSaveableStateHolder()
    val context = LocalContext.current
    var lastBack by remember { mutableLongStateOf(0L) }
    val isTv = remember { isTvDevice(context) }
    val config = LocalConfiguration.current
    val compact = !isTv && (config.screenWidthDp < 600 || config.screenHeightDp < 480)
    val device = DeviceInfo(isTv = isTv, compact = compact)

    BackHandler(enabled = route !is Route.Player) {
        if (vm.back()) return@BackHandler
        if (route == Route.Main && vm.tab != MainTab.HOME) {
            vm.tab = MainTab.HOME
            vm.focusTabsRequest++
            return@BackHandler
        }
        if (isTv && route == Route.Main && !vm.tabBarFocused) {
            vm.focusTabsRequest++
            return@BackHandler
        }
        val now = System.currentTimeMillis()
        if (now - lastBack < 2200) {
            (context as? Activity)?.finish()
        } else {
            lastBack = now
            Toast.makeText(context, "Appuyez à nouveau sur Retour pour quitter", Toast.LENGTH_SHORT).show()
        }
    }

    CompositionLocalProvider(
        LocalFocusMemory provides vm.focus,
        LocalDevice provides device,
        LocalGutter provides (if (isTv) 48.dp else if (compact) 16.dp else 32.dp),
        LocalRankStyle provides (runCatching { RankStyle.valueOf(vm.storage.rankStyle) }.getOrNull() ?: RankStyle.NEON),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Nova.Bg)
                .then(if (!isTv && route !is Route.Player) Modifier.safeDrawingPadding() else Modifier)
        ) {
            val key = when (route) {
                Route.Splash -> "splash"
                Route.Login -> "login"
                Route.Main -> "main"
                is Route.MovieDetail -> "movie_${route.id}"
                is Route.SeriesDetail -> "series_${route.id}"
                is Route.Player -> "player_${vm.backStack.size}"
                is Route.Catalog -> "catalog_${route.type}"
                Route.LiveGrid -> "livegrid"
                is Route.Saga -> "saga_${route.id}"
                is Route.Grid -> "grid_${route.mode}_${route.key}_${route.scope}"
                is Route.TmdbDetail -> "tmdb_${route.kind}_${route.id}"
            }
            holder.SaveableStateProvider(key) {
                when (route) {
                    Route.Splash -> SplashScreen(vm)
                    Route.Login -> LoginScreen(vm)
                    Route.Main -> MainScreen(vm)
                    is Route.MovieDetail -> MovieDetailScreen(vm, route.id)
                    is Route.SeriesDetail -> SeriesDetailScreen(vm, route.id)
                    is Route.Player -> PlayerScreen(vm, route.session)
                    is Route.Catalog -> CompositionLocalProvider(LocalScreenKey provides "catalog_${route.type}") {
                        Box(Modifier.fillMaxSize().padding(top = 28.dp)) { CatalogScreen(vm, route.type) }
                    }
                    is Route.TmdbDetail -> TmdbDetailScreen(vm, route.kind, route.id)
                    is Route.Saga -> SagaScreen(vm, route.id)
                    is Route.Grid -> GridScreen(vm, route.mode, route.key, route.scope, route.title)
                    Route.LiveGrid -> CompositionLocalProvider(LocalScreenKey provides "livegrid") {
                        Box(Modifier.fillMaxSize().padding(top = 28.dp)) { LiveScreen(vm) }
                    }
                }
            }
        }
    }
}
