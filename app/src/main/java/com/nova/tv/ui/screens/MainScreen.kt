package com.nova.tv.ui.screens

import com.nova.tv.ui.Gutter
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxHeight
import com.nova.tv.ui.LocalDevice
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.nova.tv.AppViewModel
import com.nova.tv.data.ContentType
import com.nova.tv.ui.MainTab
import com.nova.tv.ui.Scope
import com.nova.tv.ui.Nova
import com.nova.tv.ui.Type
import com.nova.tv.ui.components.FocusBox
import com.nova.tv.ui.components.LocalFocusMemory
import com.nova.tv.ui.components.LocalScreenKey
import com.nova.tv.ui.components.formatClock
import kotlinx.coroutines.delay

@Composable
fun MainScreen(vm: AppViewModel) {
    val requesters = remember { MainTab.entries.associateWith { FocusRequester() } }
    val memory = LocalFocusMemory.current

    LaunchedEffect(vm.focusTabsRequest) {
        if (vm.focusTabsRequest > 0) runCatching { requesters.getValue(vm.tab).requestFocus() }
    }
    LaunchedEffect(Unit) {
        delay(450)
        if (!vm.initialFocusDone) {
            vm.initialFocusDone = true
            runCatching { requesters.getValue(vm.tab).requestFocus() }
        }
        val key = screenKey(vm.tab)
        if (key in memory.pending) {
            // l'élément mémorisé n'a pas pu être restauré : on revient au menu
            memory.pending.clear()
            runCatching { requesters.getValue(vm.tab).requestFocus() }
        }
        memory.pending.clear()
    }

    if (LocalDevice.current.compact) {
        MobileMain(vm)
        return
    }

    Box(Modifier.fillMaxSize().background(Nova.Bg)) {
        CompositionLocalProvider(LocalScreenKey provides screenKey(vm.tab)) {
            when (vm.tab) {
                MainTab.HOME -> BrowseScreen(vm, Scope.ALL)
                MainTab.MOVIES -> BrowseScreen(vm, Scope.MOVIES)
                MainTab.SERIES -> BrowseScreen(vm, Scope.SERIES)
                MainTab.LIVE -> LiveHomeScreen(vm)
                MainTab.GUIDE -> Box(Modifier.padding(top = 64.dp)) { GuideScreen(vm) }
                MainTab.SEARCH -> Box(Modifier.padding(top = 64.dp)) { SearchScreen(vm) }
                MainTab.SETTINGS -> Box(Modifier.padding(top = 64.dp)) { SettingsScreen(vm) }
            }
        }
        TopBar(vm, requesters)
    }
}

private fun screenKey(tab: MainTab) = tab.name.lowercase()

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun TopBar(vm: AppViewModel, requesters: Map<MainTab, FocusRequester>) {
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(15_000); value = System.currentTimeMillis()
        }
    }
    Row(
        Modifier
            .fillMaxWidth()
            .height(64.dp)
            .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.85f), Color.Transparent)))
            .padding(horizontal = Gutter),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Wordmark(28, Type.H2)
        Spacer(Modifier.width(30.dp))
        Row(
            Modifier
                .weight(1f)
                .onFocusChanged { vm.tabBarFocused = it.hasFocus }
                .focusProperties { enter = { requesters[vm.tab] ?: FocusRequester.Default } }
                .focusGroup(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            listOf(MainTab.HOME, MainTab.MOVIES, MainTab.SERIES, MainTab.LIVE, MainTab.GUIDE).forEach { t ->
                TabItem(vm, t, requesters.getValue(t), t.label, null)
            }
            Spacer(Modifier.weight(1f))
            TabItem(vm, MainTab.SEARCH, requesters.getValue(MainTab.SEARCH), null, Icons.Filled.Search)
            TabItem(vm, MainTab.SETTINGS, requesters.getValue(MainTab.SETTINGS), null, Icons.Filled.Settings)
        }
        if (vm.update != null) {
            Spacer(Modifier.width(10.dp))
            UpdatePill(vm)
        }
        Spacer(Modifier.width(18.dp))
        Text(formatClock(now), style = Type.H3, color = Nova.Muted)
    }
}

@Composable
private fun TabItem(vm: AppViewModel, tab: MainTab, requester: FocusRequester, label: String?, icon: ImageVector?) {
    val selected = vm.tab == tab
    FocusBox(
        onClick = { vm.tab = tab },
        onFocused = { if (vm.tab != tab) vm.tab = tab },
        focusRequester = requester,
        shape = RoundedCornerShape(50),
        focusedScale = 1.04f,
        borderWidth = 0.dp,
        glow = false,
        modifier = Modifier.height(36.dp),
    ) { focused ->
        Column(
            Modifier
                .background(if (focused) Color.White else Color.Transparent)
                .padding(horizontal = if (label != null) 16.dp else 10.dp)
                .height(36.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val color = when {
                focused -> Color.Black
                selected -> Color.White
                else -> Nova.Muted
            }
            if (label != null) Text(label, style = Type.H3, color = color)
            if (icon != null) Icon(icon, label ?: tab.label, tint = color, modifier = Modifier.size(20.dp))
            if (selected && !focused && label != null) {
                Spacer(Modifier.height(3.dp))
                Box(
                    Modifier
                        .width(18.dp)
                        .height(2.5.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Nova.AccentBrush)
                )
            }
        }
    }
}

@Composable
private fun UpdatePill(vm: AppViewModel) {
    val p = vm.updateProgress
    FocusBox(
        onClick = { vm.installUpdate() },
        shape = RoundedCornerShape(50),
        focusedScale = 1.05f,
        borderWidth = 0.dp,
        glow = false,
        modifier = Modifier.height(32.dp),
    ) { focused ->
        Box(
            Modifier
                .background(if (focused) Color.White else Nova.Accent)
                .padding(horizontal = 14.dp)
                .height(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (p != null) "Téléchargement ${(p * 100).toInt()} %" else "Mise à jour disponible",
                style = Type.Small,
                color = if (focused) Color.Black else Color.White,
            )
        }
    }
}

// =============================================================================== Mobile

/** Disposition téléphone : en-tête compact + contenu + barre d'onglets en bas. */
@Composable
private fun MobileMain(vm: AppViewModel) {
    Column(Modifier.fillMaxSize().background(Nova.Bg)) {
        Row(
            Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Wordmark(26, Type.H2)
            Spacer(Modifier.weight(1f))
            if (vm.update != null) {
                UpdatePill(vm)
                Spacer(Modifier.width(6.dp))
            }
            MobileIcon(Icons.Filled.Search, vm.tab == MainTab.SEARCH) { vm.tab = MainTab.SEARCH }
            MobileIcon(Icons.Filled.Settings, vm.tab == MainTab.SETTINGS) { vm.tab = MainTab.SETTINGS }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            CompositionLocalProvider(LocalScreenKey provides screenKey(vm.tab)) {
                when (vm.tab) {
                    MainTab.HOME -> BrowseScreen(vm, Scope.ALL)
                    MainTab.MOVIES -> BrowseScreen(vm, Scope.MOVIES)
                    MainTab.SERIES -> BrowseScreen(vm, Scope.SERIES)
                    MainTab.LIVE -> LiveHomeScreen(vm)
                    MainTab.GUIDE -> GuideScreen(vm)
                    MainTab.SEARCH -> SearchScreen(vm)
                    MainTab.SETTINGS -> SettingsScreen(vm)
                }
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .background(Nova.Surface)
                .height(60.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            listOf(
                Triple(MainTab.HOME, Icons.Filled.Home, "Accueil"),
                Triple(MainTab.MOVIES, Icons.Filled.Star, "Films"),
                Triple(MainTab.SERIES, Icons.Filled.List, "Séries"),
                Triple(MainTab.LIVE, Icons.Filled.PlayArrow, "TV"),
                Triple(MainTab.GUIDE, Icons.Filled.DateRange, "Guide"),
            ).forEach { (tab, icon, label) ->
                val sel = vm.tab == tab
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable { vm.tab = tab },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(icon, label, tint = if (sel) Nova.Accent else Nova.Muted, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.height(3.dp))
                    Text(label, style = Type.Small, color = if (sel) Color.White else Nova.Muted)
                }
            }
        }
    }
}

@Composable
private fun MobileIcon(icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(42.dp).clip(RoundedCornerShape(50)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = if (selected) Nova.Accent else Color.White, modifier = Modifier.size(22.dp))
    }
}
