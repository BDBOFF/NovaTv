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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.fillMaxWidth
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

val avatarEmojis = listOf(
    "", "🦊", "🐼", "🐯", "🦁", "🐺", "🐻", "🐨", "🐶", "🐱", "🦄", "🐸",
    "🐙", "🦖", "👽", "🤖", "👑", "⚽", "🎬", "🎮", "⭐", "🔥", "🌙", "🚀",
)

/** Pastille ronde : avatar illustré, ou initiale du profil. */
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
        if (acc.emoji.isNotEmpty()) {
            Text(acc.emoji, fontSize = (size.value * 0.52f).sp)
        } else {
            Text(
                acc.name.trim().firstOrNull()?.uppercase() ?: "?",
                fontFamily = com.nova.tv.ui.Inter, fontWeight = FontWeight.Black,
                fontSize = (size.value * 0.46f).sp, color = Color.White,
            )
        }
    }
}

private enum class Sheet { MENU, AVATAR, PIN_SET, PIN_ASK, DELETE }

/** Écran "Qui regarde ?" : un profil = un abonnement, avec sa propre reprise et ses favoris. */
@Composable
fun ProfilesScreen(vm: AppViewModel) {
    val accounts = vm.storage.accounts
    val compact = LocalDevice.current.compact
    val first = remember { FocusRequester() }
    var manage by remember { mutableStateOf(false) }
    var target by remember { mutableStateOf<Account?>(null) }
    var sheet by remember { mutableStateOf<Sheet?>(null) }
    LaunchedEffect(Unit) {
        delay(150); runCatching { first.requestFocus() }
    }
    if (sheet != null) BackHandler { sheet = null }
    else if (manage) BackHandler { manage = false }
    else if (vm.backStack.size > 1) BackHandler { vm.back() }

    fun open(acc: Account) {
        if (manage) {
            target = acc; sheet = Sheet.MENU
        } else if (acc.pin.isNotEmpty()) {
            target = acc; sheet = Sheet.PIN_ASK
        } else vm.switchAccount(acc)
    }

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
            Text(if (manage) "Gérer les profils" else "Qui regarde ?", style = Type.Display)
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
                            onClick = { open(acc) },
                            onLongClick = { target = acc; sheet = Sheet.MENU },
                            shape = CircleShape,
                            focusedScale = 1.1f,
                            borderColor = Color.White,
                            borderWidth = 3.dp,
                            focusRequester = if (i == activeIndex) first else null,
                            modifier = Modifier.size(tile),
                        ) { _ ->
                            ProfileAvatar(acc, i, tile)
                            if (manage) Box(
                                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)),
                                contentAlignment = Alignment.Center,
                            ) { Text("✎", fontSize = (tile.value * 0.32f).sp, color = Color.White) }
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(acc.name, style = Type.H3, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                            if (acc.pin.isNotEmpty()) Text("  🔒", style = Type.Small)
                        }
                        if (acc.id == vm.storage.activeId) Text("Dernier utilisé", style = Type.Small, color = Nova.Dim)
                    }
                }
                if (!manage) Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(tile + 30.dp)) {
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
            Spacer(Modifier.height(28.dp))
            NovaButton(if (manage) "Terminé" else "Gérer les profils", onClick = { manage = !manage }, primary = manage)
            Spacer(Modifier.height(18.dp))
            Text(
                "Un profil = un abonnement IPTV. Chacun garde sa reprise de lecture et ses favoris, synchronisés sur tous vos appareils. " +
                    "Appui long sur un profil pour le modifier.",
                style = Type.Small, color = Nova.Dim, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 560.dp),
            )
        }

        val acc = target
        if (acc != null && sheet != null) {
            val index = accounts.indexOfFirst { it.id == acc.id }.coerceAtLeast(0)
            val fresh = accounts.firstOrNull { it.id == acc.id } ?: acc
            when (sheet) {
                Sheet.MENU -> ProfileMenu(vm, fresh, index, onDismiss = { sheet = null }, onSheet = { sheet = it })
                Sheet.AVATAR -> AvatarPicker(fresh, index, onDismiss = { sheet = Sheet.MENU }) { color, emoji ->
                    vm.updateAccount(fresh.copy(avatar = color, emoji = emoji)); sheet = Sheet.MENU
                }
                Sheet.PIN_SET -> PinDialog(
                    title = if (fresh.pin.isEmpty()) "Choisir un code PIN" else "Nouveau code PIN",
                    subtitle = "4 chiffres, demandés à l'ouverture de « ${fresh.name} ».",
                    onDismiss = { sheet = Sheet.MENU },
                    extra = if (fresh.pin.isNotEmpty()) ("Retirer le code" to { vm.updateAccount(fresh.copy(pin = "")); sheet = Sheet.MENU }) else null,
                ) { pin -> vm.updateAccount(fresh.copy(pin = pin)); sheet = Sheet.MENU; null }
                Sheet.PIN_ASK -> PinDialog(
                    title = "Code PIN de « ${fresh.name} »",
                    subtitle = "Saisissez le code pour ouvrir ce profil.",
                    onDismiss = { sheet = null },
                ) { pin -> if (pin == fresh.pin) { sheet = null; vm.switchAccount(fresh); null } else "Code incorrect" }
                Sheet.DELETE -> ConfirmDialog(
                    "Supprimer « ${fresh.name} » ?",
                    "Le profil et ses favoris sont retirés de cet appareil. Votre abonnement IPTV n'est pas touché.",
                    "Supprimer", onDismiss = { sheet = Sheet.MENU },
                ) { sheet = null; target = null; vm.deleteAccount(fresh) }
                null -> {}
            }
        }
    }
}

@Composable
private fun DialogFrame(onDismiss: () -> Unit, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.72f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .padding(20.dp)
                .widthIn(max = 520.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Nova.Surface)
                .border(1.dp, Nova.Line, RoundedCornerShape(18.dp))
                .verticalScroll(rememberScrollState())
                .padding(22.dp),
        ) { content() }
    }
}

@Composable
private fun MenuRow(label: String, danger: Boolean = false, requester: FocusRequester? = null, onClick: () -> Unit) {
    FocusBox(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        focusedScale = 1.02f,
        borderWidth = 0.dp,
        glow = false,
        focusRequester = requester,
        modifier = Modifier.fillMaxWidth().height(48.dp),
    ) { focused ->
        Box(
            Modifier.fillMaxSize().background(if (focused) Color.White else Color.Transparent).padding(horizontal = 14.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                label, style = Type.H3,
                color = when {
                    focused -> Color.Black
                    danger -> Color(0xFFFF6B6B)
                    else -> Nova.Text
                },
            )
        }
    }
}

@Composable
private fun ProfileMenu(vm: AppViewModel, acc: Account, index: Int, onDismiss: () -> Unit, onSheet: (Sheet) -> Unit) {
    val first = remember { FocusRequester() }
    LaunchedEffect(acc.id) { delay(80); runCatching { first.requestFocus() } }
    DialogFrame(onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProfileAvatar(acc, index, 52.dp)
            Spacer(Modifier.width(14.dp))
            Column {
                Text(acc.name, style = Type.H1, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    if (acc.type == "m3u") "Playlist M3U" else "${acc.username} · ${acc.server.substringAfter("://")}",
                    style = Type.Small, color = Nova.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        MenuRow("Ouvrir ce profil", requester = first) { onDismiss(); vm.switchAccount(acc) }
        MenuRow("Choisir l'avatar") { onSheet(Sheet.AVATAR) }
        MenuRow("Modifier le nom et les codes") { onDismiss(); vm.navigate(Route.ProfileEdit(acc.id)) }
        MenuRow(if (acc.pin.isEmpty()) "Protéger par un code PIN" else "Changer ou retirer le code PIN") { onSheet(Sheet.PIN_SET) }
        MenuRow("Recharger les chaînes, films et guide TV") { onDismiss(); vm.switchAccount(acc, force = true) }
        MenuRow("Supprimer le profil", danger = true) { onSheet(Sheet.DELETE) }
        MenuRow("Fermer") { onDismiss() }
    }
}

@Composable
private fun AvatarPicker(acc: Account, index: Int, onDismiss: () -> Unit, onPick: (Int, String) -> Unit) {
    var color by remember { mutableStateOf(if (acc.avatar >= 0) acc.avatar else index) }
    var emoji by remember { mutableStateOf(acc.emoji) }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(80); runCatching { first.requestFocus() } }
    DialogFrame(onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProfileAvatar(acc.copy(avatar = color, emoji = emoji), index, 64.dp)
            Spacer(Modifier.width(14.dp))
            Text("Choisir l'avatar", style = Type.H1)
        }
        Spacer(Modifier.height(14.dp))
        Text("Couleur", style = Type.Small, color = Nova.Muted)
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            avatarColors.forEachIndexed { i, c ->
                FocusBox(
                    onClick = { color = i },
                    shape = CircleShape,
                    borderWidth = 3.dp,
                    glow = false,
                    focusRequester = if (i == 0) first else null,
                    modifier = Modifier.size(40.dp),
                ) { _ ->
                    Box(Modifier.fillMaxSize().background(c), contentAlignment = Alignment.Center) {
                        if (i == color) Text("✓", color = Color.White, style = Type.H2)
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Text("Illustration", style = Type.Small, color = Nova.Muted)
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            avatarEmojis.forEach { e ->
                FocusBox(
                    onClick = { emoji = e },
                    shape = RoundedCornerShape(12.dp),
                    borderWidth = 3.dp,
                    glow = false,
                    modifier = Modifier.size(48.dp),
                ) { _ ->
                    Box(
                        Modifier.fillMaxSize().background(if (e == emoji) Nova.Accent.copy(alpha = 0.35f) else Nova.Surface2),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (e.isEmpty()) Text(acc.name.trim().firstOrNull()?.uppercase() ?: "A", style = Type.H2)
                        else Text(e, fontSize = 24.sp)
                    }
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            NovaButton("Enregistrer", onClick = { onPick(color, emoji) }, primary = true)
            NovaButton("Annuler", onClick = onDismiss)
        }
    }
}

/** Saisie d'un code PIN à 4 chiffres avec un pavé numérique (télécommande ou doigt). */
@Composable
private fun PinDialog(
    title: String,
    subtitle: String,
    onDismiss: () -> Unit,
    extra: Pair<String, () -> Unit>? = null,
    onDone: (String) -> String?,
) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(80); runCatching { first.requestFocus() } }
    fun press(d: String) {
        error = null
        if (pin.length < 4) pin += d
        if (pin.length == 4) {
            val err = onDone(pin)
            if (err != null) { error = err; pin = "" }
        }
    }
    DialogFrame(onDismiss) {
        Text(title, style = Type.H1)
        Text(subtitle, style = Type.Small, color = Nova.Muted)
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.align(Alignment.CenterHorizontally)) {
            repeat(4) { i ->
                Box(
                    Modifier.size(18.dp).clip(CircleShape)
                        .background(if (i < pin.length) Nova.Accent else Nova.Surface3)
                )
            }
        }
        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, style = Type.Small, color = Color(0xFFFF6B6B), modifier = Modifier.align(Alignment.CenterHorizontally))
        }
        Spacer(Modifier.height(16.dp))
        val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "⌫", "0", "✕")
        Column(Modifier.align(Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            keys.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { k ->
                        FocusBox(
                            onClick = {
                                when (k) {
                                    "⌫" -> { pin = pin.dropLast(1); error = null }
                                    "✕" -> onDismiss()
                                    else -> press(k)
                                }
                            },
                            shape = RoundedCornerShape(14.dp),
                            borderWidth = 0.dp,
                            glow = false,
                            focusRequester = if (k == "1") first else null,
                            modifier = Modifier.size(64.dp, 52.dp),
                        ) { focused ->
                            Box(
                                Modifier.fillMaxSize().background(if (focused) Color.White else Nova.Surface2),
                                contentAlignment = Alignment.Center,
                            ) { Text(k, style = Type.H1, color = if (focused) Color.Black else Nova.Text) }
                        }
                    }
                }
            }
        }
        if (extra != null) {
            Spacer(Modifier.height(16.dp))
            NovaButton(extra.first, onClick = extra.second)
        }
    }
}

@Composable
private fun ConfirmDialog(title: String, text: String, action: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val cancel = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(80); runCatching { cancel.requestFocus() } }
    DialogFrame(onDismiss) {
        Text(title, style = Type.H1)
        Spacer(Modifier.height(8.dp))
        Text(text, style = Type.Body, color = Nova.Muted)
        Spacer(Modifier.height(18.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            NovaButton("Annuler", onClick = onDismiss, focusRequester = cancel)
            NovaButton(action, onClick = onConfirm, primary = true)
        }
    }
}

/** Modifier le nom et les codes d'un profil. */
@Composable
fun ProfileEditScreen(vm: AppViewModel, id: String) {
    val acc = vm.storage.accounts.firstOrNull { it.id == id }
    if (acc == null) {
        EmptyState("Profil introuvable", "Il a peut-être été supprimé."); return
    }
    val m3u = acc.type == "m3u"
    var name by remember { mutableStateOf(acc.name) }
    var server by remember { mutableStateOf(acc.server) }
    var user by remember { mutableStateOf(acc.username) }
    var pass by remember { mutableStateOf(acc.password) }
    var epg by remember { mutableStateOf(acc.epgUrl) }
    var error by remember { mutableStateOf<String?>(null) }
    val compact = LocalDevice.current.compact

    fun save() {
        if (name.isBlank()) { error = "Donnez un nom au profil."; return }
        if (!server.trim().startsWith("http")) { error = if (m3u) "Le lien doit commencer par http." else "L'adresse du serveur doit commencer par http."; return }
        if (!m3u && (user.isBlank() || pass.isBlank())) { error = "Renseignez l'utilisateur et le mot de passe."; return }
        val updated = acc.copy(
            name = name.trim(),
            server = if (m3u) server.trim() else com.nova.tv.data.normalizeServer(server),
            username = user.trim(), password = pass.trim(), epgUrl = epg.trim(),
        )
        val changed = updated.server != acc.server || updated.username != acc.username ||
            updated.password != acc.password || updated.epgUrl != acc.epgUrl
        vm.saveEditedAccount(updated, changed)
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Nova.Bg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = if (compact) 20.dp else 56.dp, vertical = if (compact) 20.dp else 40.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProfileAvatar(acc, vm.storage.accounts.indexOf(acc), 56.dp)
            Spacer(Modifier.width(14.dp))
            Text("Modifier le profil", style = Type.H1)
        }
        Column(Modifier.widthIn(max = 640.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            TvTextField("Nom du profil", name, { name = it })
            if (m3u) {
                TvTextField("Lien de la playlist (M3U, M3U8)", server, { server = it }, keyboardType = androidx.compose.ui.text.input.KeyboardType.Uri)
                TvTextField("Lien du guide TV XMLTV (facultatif)", epg, { epg = it }, keyboardType = androidx.compose.ui.text.input.KeyboardType.Uri)
            } else {
                TvTextField("Adresse du serveur", server, { server = it }, keyboardType = androidx.compose.ui.text.input.KeyboardType.Uri)
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    TvTextField("Nom d'utilisateur", user, { user = it }, Modifier.weight(1f))
                    TvTextField("Mot de passe", pass, { pass = it }, Modifier.weight(1f), password = true)
                }
                Text(
                    "Votre fournisseur a changé d'adresse ? Remplacez simplement l'adresse du serveur : favoris et reprise sont conservés.",
                    style = Type.Small, color = Nova.Muted,
                )
            }
            error?.let { Text(it, style = Type.Small, color = Color(0xFFFF6B6B)) }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                NovaButton("Enregistrer", onClick = { save() }, primary = true)
                NovaButton("Annuler", onClick = { vm.back() })
            }
        }
        if (LocalDevice.current.isTv) {
            PhoneConnectCard(
                title = "Modifier depuis votre téléphone",
                page = { com.nova.tv.data.PhoneForm.loginPage(it) },
            ) { v ->
                v["name"]?.takeIf { it.isNotBlank() }?.let { name = it }
                if (m3u) {
                    v["m3u"]?.takeIf { it.isNotBlank() }?.let { server = it }
                    v["epg"]?.takeIf { it.isNotBlank() }?.let { epg = it }
                } else {
                    v["server"]?.takeIf { it.isNotBlank() }?.let { server = it }
                    v["username"]?.takeIf { it.isNotBlank() }?.let { user = it }
                    v["password"]?.takeIf { it.isNotBlank() }?.let { pass = it }
                }
            }
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
