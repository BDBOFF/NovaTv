package com.nova.tv.ui.screens

import androidx.compose.foundation.layout.widthIn
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nova.tv.AppViewModel
import com.nova.tv.data.normalizeServer
import com.nova.tv.ui.Nova
import com.nova.tv.ui.Type
import com.nova.tv.ui.components.FocusBox
import com.nova.tv.ui.components.NovaButton
import kotlinx.coroutines.launch
import java.net.URI

@Composable
fun BrandMark(size: Int = 44) {
    Box(
        Modifier
            .size(size.dp)
            .clip(RoundedCornerShape((size * 0.22f).dp))
            .background(Brush.linearGradient(listOf(Nova.Accent, Nova.Accent2))),
    ) {
        Text(
            "S",
            style = Type.H1.copy(fontWeight = FontWeight.Black, fontSize = (size * 0.72f).sp, lineHeight = (size * 0.8f).sp),
            color = Color(0xFF08080A),
            modifier = Modifier.align(Alignment.Center).padding(end = (size * 0.1f).dp),
        )
        Text(
            "TV",
            style = Type.Label.copy(fontWeight = FontWeight.Black, fontSize = (size * 0.2f).sp, lineHeight = (size * 0.22f).sp),
            color = Color.White,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = (size * 0.08f).dp, bottom = (size * 0.06f).dp),
        )
    }
}

/** Logo complet : pictogramme S-TV + "SAMOTH TV". */
@Composable
fun Wordmark(markSize: Int = 30, textStyle: TextStyle = Type.H2) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        BrandMark(markSize)
        Spacer(Modifier.width((markSize * 0.35f).dp))
        // Un seul texte sur une ligne : "SAMOTH TV" ne se coupe jamais
        Text(
            androidx.compose.ui.text.buildAnnotatedString {
                append("SAMOTH ")
                pushStyle(androidx.compose.ui.text.SpanStyle(color = Nova.Accent))
                append("TV")
                pop()
            },
            style = textStyle.copy(fontWeight = FontWeight.Black),
            color = Color.White,
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
fun SplashScreen(vm: AppViewModel) {
    val pulse = rememberInfiniteTransition(label = "pulse")
    val a by pulse.animateFloat(0.55f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "a")
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.radialGradient(listOf(Color(0xFF3A0508), Nova.Bg), radius = 1400f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.alpha(a)) {
                if (com.nova.tv.ui.LocalDevice.current.compact) Wordmark(44, Type.H1.copy(fontSize = Type.H1.fontSize * 1.15f))
                else Wordmark(68, Type.Display.copy(fontSize = Type.Display.fontSize * 1.2f))
            }
            Spacer(Modifier.height(36.dp))
            Text(vm.loadingText.ifBlank { "Démarrage…" }, style = Type.Body, color = Nova.Muted)
            Spacer(Modifier.height(16.dp))
            CircularProgressIndicator(color = Nova.Accent, strokeWidth = 3.dp, modifier = Modifier.size(28.dp))
        }
    }
}

/** Champ de saisie adapté à la télécommande. */
@Composable
fun TvTextField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    password: Boolean = false,
    placeholder: String = "",
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    onDone: () -> Unit = {},
    focusRequester: FocusRequester? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val focusManager = LocalFocusManager.current
    var visible by remember { mutableStateOf(false) }
    Column(modifier) {
        Text(label, style = Type.Small, color = if (focused) Nova.Text else Nova.Muted)
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = Type.H3.copy(color = Nova.Text),
            cursorBrush = SolidColor(Nova.Accent),
            interactionSource = interaction,
            visualTransformation = if (password && !visible) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
            keyboardActions = KeyboardActions(
                onNext = { focusManager.moveFocus(FocusDirection.Down) },
                onDone = { onDone() },
                onSearch = { onDone() },
            ),
            modifier = Modifier
                .weight(1f)
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
            decorationBox = { inner ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (focused) Nova.Surface3 else Nova.Surface2)
                        .border(
                            if (focused) 2.dp else 1.dp,
                            if (focused) Nova.Accent else Nova.Line,
                            RoundedCornerShape(10.dp),
                        )
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (value.isEmpty()) Text(placeholder, style = Type.H3, color = Nova.Dim)
                    inner()
                }
            },
        )
        if (password) {
            Spacer(Modifier.width(8.dp))
            // Le petit œil : afficher / masquer le mot de passe
            com.nova.tv.ui.components.FocusBox(
                onClick = { visible = !visible },
                shape = RoundedCornerShape(10.dp),
                focusedScale = 1.06f,
                borderWidth = 0.dp,
                glow = false,
                modifier = Modifier.size(46.dp),
            ) { f ->
                Box(Modifier.fillMaxSize().background(if (f) Color.White else Nova.Surface2), contentAlignment = Alignment.Center) {
                    EyeIcon(open = visible, color = if (f) Color.Black else Nova.Muted)
                }
            }
        }
        }
    }
}

/** Icône œil (barré quand le mot de passe est masqué). */
@Composable
private fun EyeIcon(open: Boolean, color: Color) {
    androidx.compose.foundation.Canvas(Modifier.size(22.dp)) {
        val w = size.width
        val h = size.height
        val stroke = androidx.compose.ui.graphics.drawscope.Stroke(width = w * 0.09f)
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(w * 0.04f, h * 0.5f)
            quadraticBezierTo(w * 0.5f, h * 0.02f, w * 0.96f, h * 0.5f)
            quadraticBezierTo(w * 0.5f, h * 0.98f, w * 0.04f, h * 0.5f)
            close()
        }
        drawPath(path, color, style = stroke)
        drawCircle(color, radius = w * 0.14f, center = androidx.compose.ui.geometry.Offset(w / 2, h / 2))
        if (!open) drawLine(color, androidx.compose.ui.geometry.Offset(w * 0.12f, h * 0.88f), androidx.compose.ui.geometry.Offset(w * 0.88f, h * 0.12f), strokeWidth = w * 0.09f)
    }
}

/** Carte "QR code" : le téléphone (iPhone ou Android) remplit le formulaire à la place de la télécommande. */
@Composable
fun PhoneConnectCard(title: String, page: (String) -> String, onSubmit: (Map<String, String>) -> Unit) {
    val submitState = androidx.compose.runtime.rememberUpdatedState(onSubmit)
    val form = remember { com.nova.tv.data.PhoneForm(page) { submitState.value(it) } }
    var url by remember { mutableStateOf<String?>(null) }
    androidx.compose.runtime.DisposableEffect(form) {
        url = form.start()
        onDispose { form.stop() }
    }
    val u = url ?: return
    Row(
        Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .border(1.dp, Nova.Line, RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        QrCode(u, 120.dp)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.widthIn(max = 260.dp)) {
            Text(title, style = Type.H3)
            Spacer(Modifier.height(4.dp))
            Text(
                "Scannez avec l'appareil photo de votre iPhone ou Android, connecté au même Wi-Fi que la TV.",
                style = Type.Small, color = Nova.Muted,
            )
            Spacer(Modifier.height(4.dp))
            Text(u.substringBefore("/?"), style = Type.Small, color = Nova.Dim)
        }
    }
}

@Composable
fun LoginScreen(vm: AppViewModel) {
    var name by rememberSaveable { mutableStateOf("") }
    var server by rememberSaveable { mutableStateOf("") }
    var user by rememberSaveable { mutableStateOf("") }
    var pass by rememberSaveable { mutableStateOf("") }
    var m3uMode by rememberSaveable { mutableStateOf(false) }
    var m3uUrl by rememberSaveable { mutableStateOf("") }
    var epgUrl by rememberSaveable { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val firstField = remember { FocusRequester() }
    val accounts = vm.storage.accounts


    // Collage d'une URL M3U complète : on en extrait serveur + identifiants.
    fun onServerChange(v: String) {
        server = v
        if (v.contains("username=") && v.contains("password=")) {
            runCatching {
                val uri = URI(v.trim())
                val params = uri.rawQuery.split("&").associate {
                    val kv = it.split("=", limit = 2); kv[0] to (kv.getOrNull(1) ?: "")
                }
                params["username"]?.let { user = java.net.URLDecoder.decode(it, "UTF-8") }
                params["password"]?.let { pass = java.net.URLDecoder.decode(it, "UTF-8") }
                server = normalizeServer(v)
            }
        }
    }

    fun submit() {
        if (vm.loading) return
        if (m3uMode) {
            if (!m3uUrl.trim().startsWith("http")) {
                localError = "Collez le lien complet de votre playlist (commençant par http)."
                return
            }
            localError = null
            scope.launch { vm.addM3u(name, m3uUrl, epgUrl) }
            return
        }
        if (server.isBlank() || user.isBlank() || pass.isBlank()) {
            localError = "Renseignez l'adresse du serveur, l'utilisateur et le mot de passe."
            return
        }
        localError = null
        scope.launch { vm.addAccount(name, server, user, pass) }
    }

    val compact = com.nova.tv.ui.LocalDevice.current.compact
    Row(Modifier.fillMaxSize().background(Nova.Bg)) {
        if (!compact) {
        // ------- Panneau de marque
        Box(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFF2A0306), Color(0xFF120204), Nova.Bg),
                    )
                )
                .padding(56.dp)
        ) {
            Wordmark(40, Type.H1)
            Column(Modifier.align(Alignment.CenterStart)) {
                Text("Votre TV,", style = Type.Display.copy(fontSize = Type.Display.fontSize * 1.25f))
                Text(
                    "sublimée.",
                    style = Type.Display.copy(fontSize = Type.Display.fontSize * 1.25f), color = Nova.Accent,
                )
                Spacer(Modifier.height(18.dp))
                Text(
                    "Connectez votre abonnement Xtream Codes ou votre lien M3U : films, séries, chaînes en direct et guide TV, " +
                        "avec affiches et fiches détaillées.",
                    style = Type.Body, color = Nova.Muted, modifier = Modifier.widthIn(max = 380.dp),
                )
                Spacer(Modifier.height(22.dp))
                PhoneConnectCard(
                    title = "Plus simple : remplissez depuis votre téléphone",
                    page = { com.nova.tv.data.PhoneForm.loginPage(it) },
                ) { v ->
                    name = v["name"].orEmpty()
                    if (v["type"] == "m3u") {
                        m3uMode = true
                        m3uUrl = v["m3u"].orEmpty()
                        epgUrl = v["epg"].orEmpty()
                    } else {
                        m3uMode = false
                        user = v["username"].orEmpty()
                        pass = v["password"].orEmpty()
                        onServerChange(v["server"].orEmpty())
                    }
                    submit()
                }
            }
            Text(
                "Lecteur uniquement · Aucun contenu n'est fourni avec l'application",
                style = Type.Small, color = Nova.Dim, modifier = Modifier.align(Alignment.BottomStart),
            )
        }

        }
        // ------- Formulaire
        Column(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = if (compact) 20.dp else 56.dp, vertical = if (compact) 24.dp else 40.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (compact) {
                Wordmark(36, Type.H1)
                Spacer(Modifier.height(10.dp))
            }
            Text("Ajouter une playlist", style = Type.H1)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("Xtream Codes", !m3uMode) { m3uMode = false; localError = null }
                Chip("Lien M3U", m3uMode) { m3uMode = true; localError = null }
            }
            if (!m3uMode) {
                TvTextField(
                    "Adresse du serveur", server, ::onServerChange,
                    placeholder = "http://exemple.com:8080", keyboardType = KeyboardType.Uri,
                    focusRequester = firstField,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    TvTextField("Nom d'utilisateur", user, { user = it }, Modifier.weight(1f))
                    TvTextField("Mot de passe", pass, { pass = it }, Modifier.weight(1f), password = true)
                }
            } else {
                TvTextField(
                    "Lien de la playlist (M3U, M3U8)", m3uUrl, { m3uUrl = it },
                    placeholder = "http://exemple.com/playlist.m3u", keyboardType = KeyboardType.Uri,
                )
                TvTextField(
                    "Lien du guide TV XMLTV (facultatif)", epgUrl, { epgUrl = it },
                    placeholder = "http://exemple.com/epg.xml", keyboardType = KeyboardType.Uri,
                )
                Text(
                    "Le guide est souvent déjà indiqué dans la playlist : laissez vide si vous n'en avez pas.",
                    style = Type.Small, color = Nova.Muted,
                )
            }
            TvTextField(
                "Nom du profil (facultatif)", name, { name = it },
                placeholder = "Ma TV", imeAction = ImeAction.Done, onDone = { submit() },
            )
            val err = localError ?: vm.error
            if (err != null) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Nova.Accent.copy(alpha = 0.14f))
                        .border(1.dp, Nova.Accent.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                        .padding(12.dp)
                ) { Text(err, style = Type.Body, color = Color(0xFFFFB4A6)) }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                NovaButton(
                    if (vm.loading) "Connexion…" else "Se connecter",
                    onClick = { submit() },
                    icon = Icons.Filled.Check,
                    primary = true,
                )
                if (vm.loading) {
                    Spacer(Modifier.width(16.dp))
                    CircularProgressIndicator(color = Nova.Accent, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(vm.loadingText, style = Type.Small, color = Nova.Muted)
                }
            }
            if (accounts.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text("Playlists enregistrées", style = Type.H2)
                accounts.forEach { acc ->
                    FocusBox(
                        onClick = { scope.launch { vm.connect(acc) } },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        focusedScale = 1.02f,
                    ) { focused ->
                        Row(
                            Modifier
                                .fillMaxSize()
                                .background(if (focused) Nova.Surface3 else Nova.Surface)
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            androidx.compose.material3.Icon(Icons.Filled.Person, null, tint = Nova.Accent)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(acc.name, style = Type.H3)
                                Text(acc.server.substringAfter("://"), style = Type.Small, color = Nova.Muted)
                            }
                        }
                    }
                }
            }
        }
    }
}
