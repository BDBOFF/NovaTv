@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.nova.tv.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nova.tv.AppViewModel
import com.nova.tv.data.Account
import com.nova.tv.ui.LocalDevice
import com.nova.tv.ui.Nova
import com.nova.tv.ui.Route
import com.nova.tv.ui.Type
import com.nova.tv.ui.components.FocusBox
import com.nova.tv.ui.components.NovaButton
import kotlinx.coroutines.delay

private val avatarColors = listOf(
    Color(0xFFD10A20), Color(0xFF3D7BFF), Color(0xFFFFB020),
    Color(0xFF2FBF71), Color(0xFFA64DFF), Color(0xFFFF6A1A),
)

fun avatarColor(acc: Account, index: Int): Color =
    avatarColors[(if (acc.avatar >= 0) acc.avatar else index).mod(avatarColors.size)]

/** Pastille ronde avec l'initiale du profil. */
@Composable
fun ProfileAvatar(acc: Account, index: Int, size: Dp) {
    val c = avatarColor(acc, index)
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(c, c.copy(alpha = 0.55f), Color(0xFF14141B)))),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            acc.name.trim().firstOrNull()?.uppercase() ?: "?",
            fontFamily = com.nova.tv.ui.Inter, fontWeight = FontWeight.Black,
            fontSize = (size.value * 0.46f).sp, color = Color.White,
        )
    }
}

/** Écran "Qui regarde ?" : un profil = un abonnement, avec sa propre reprise et ses favoris. */
@Composable
fun ProfilesScreen(vm: AppViewModel) {
    val accounts = vm.storage.accounts
    val compact = LocalDevice.current.compact
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(150); runCatching { first.requestFocus() }
    }
    if (vm.backStack.size > 1) BackHandler { vm.back() }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.radialGradient(listOf(Color(0xFF2A0306), Nova.Bg), radius = 1500f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Wordmark(if (compact) 28 else 34, Type.H1)
            Spacer(Modifier.height(if (compact) 24.dp else 34.dp))
            Text("Qui regarde ?", style = Type.Display)
            Spacer(Modifier.height(if (compact) 24.dp else 34.dp))
            val tile = if (compact) 92.dp else 120.dp
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(if (compact) 18.dp else 30.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(22.dp),
                modifier = Modifier.widthIn(max = 900.dp),
            ) {
                val activeIndex = accounts.indexOfFirst { it.id == vm.storage.activeId }.coerceAtLeast(0)
                accounts.forEachIndexed { i, acc ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(tile + 30.dp)) {
                        FocusBox(
                            onClick = { vm.switchAccount(acc) },
                            shape = CircleShape,
                            focusedScale = 1.1f,
                            borderColor = Color.White,
                            borderWidth = 3.dp,
                            focusRequester = if (i == activeIndex) first else null,
                            modifier = Modifier.size(tile),
                        ) { _ -> ProfileAvatar(acc, i, tile) }
                        Spacer(Modifier.height(10.dp))
                        Text(acc.name, style = Type.H3, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                        if (acc.id == vm.storage.activeId) Text("Dernier utilisé", style = Type.Small, color = Nova.Dim)
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(tile + 30.dp)) {
                    FocusBox(
                        onClick = { vm.navigate(Route.Login) },
                        shape = CircleShape,
                        focusedScale = 1.1f,
                        borderWidth = 3.dp,
                        modifier = Modifier.size(tile),
                    ) { focused ->
                        Box(
                            Modifier
                                .size(tile)
                                .clip(CircleShape)
                                .background(if (focused) Nova.Surface3 else Nova.Surface)
                                .border(2.dp, Nova.Line, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) { Text("+", fontSize = (tile.value * 0.45f).sp, color = Nova.Muted) }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text("Ajouter", style = Type.H3, color = Nova.Muted)
                }
            }
            Spacer(Modifier.height(30.dp))
            Text(
                "Un profil = un abonnement IPTV. Chacun garde sa reprise de lecture et ses favoris, synchronisés sur tous vos appareils.",
                style = Type.Small, color = Nova.Dim, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 520.dp),
            )
        }
    }
}

/** Avertissement : l'abonnement est déjà en lecture sur un autre appareil. */
@Composable
fun BusyDialog(vm: AppViewModel) {
    val acc = vm.account
    val play = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(100); runCatching { play.requestFocus() }
    }
    BackHandler { vm.busyWarning = null }
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.75f)), contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .padding(20.dp)
                .widthIn(max = 560.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Nova.Surface)
                .border(1.dp, Nova.Line, RoundedCornerShape(18.dp))
                .padding(26.dp),
        ) {
            Text("Abonnement déjà en lecture", style = Type.H1)
            Spacer(Modifier.height(10.dp))
            Text(
                "Le profil « ${acc?.name ?: ""} » est déjà utilisé sur un autre appareil. " +
                    "Votre fournisseur n'autorise qu'une lecture à la fois : lancer ici peut couper l'autre écran, " +
                    "voire suspendre l'abonnement.",
                style = Type.Body, color = Nova.Muted,
            )
            Spacer(Modifier.height(20.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                NovaButton("Changer de profil", onClick = { vm.busyWarning = null; vm.navigate(Route.Profiles) }, primary = true, focusRequester = play)
                NovaButton("Lancer quand même", onClick = { vm.confirmBusyPlay() })
                NovaButton("Annuler", onClick = { vm.busyWarning = null })
            }
        }
    }
}
