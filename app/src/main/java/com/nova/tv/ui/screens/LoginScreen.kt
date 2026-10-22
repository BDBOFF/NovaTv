package com.nova.tv.ui.screens

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
        Text("SAMOTH", style = textStyle.copy(fontWeight = FontWeight.Black), color = Color.White)
        Text(" TV", style = textStyle.copy(fontWeight = FontWeight.Black), color = Nova.Accent)
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
                Wordmark(68, Type.Display.copy(fontSize = Type.Display.fontSize * 1.2f))
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
    Column(modifier) {
        Text(label, style = Type.Small, color = if (focused) Nova.Text else Nova.Muted)
        Spacer(Modifier.height(6.dp))
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = Type.H3.copy(color = Nova.Text),
            cursorBrush = SolidColor(Nova.Accent),
            interactionSource = interaction,
            visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
            keyboardActions = KeyboardActions(
                onNext = { focusManager.moveFocus(FocusDirection.Down) },
                onDone = { onDone() },
                onSearch = { onDone() },
            ),
            modifier = Modifier
                .fillMaxWidth()
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
    }
}

@Composable
fun LoginScreen(vm: AppViewModel) {
    var name by rememberSaveable { mutableStateOf("") }
    var server by rememberSaveable { mutableStateOf("") }
    var user by rememberSaveable { mutableStateOf("") }
    var pass by rememberSaveable { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val firstField = remember { FocusRequester() }
    val accounts = vm.storage.accounts

    LaunchedEffect(Unit) { runCatching { firstField.requestFocus() } }

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
        if (server.isBlank() || user.isBlank() || pass.isBlank()) {
            localError = "Renseignez l'adresse du serveur, l'utilisateur et le mot de passe."
            return
        }
        localError = null
        scope.launch { vm.addAccount(name, server, user, pass) }
    }

    Row(Modifier.fillMaxSize().background(Nova.Bg)) {
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
                    "Connectez votre abonnement Xtream Codes : films, séries, chaînes en direct et guide TV, " +
                        "avec affiches et fiches détaillées.",
                    style = Type.Body, color = Nova.Muted, modifier = Modifier.width(380.dp),
                )
            }
            Text(
                "Lecteur uniquement · Aucun contenu n'est fourni avec l'application",
                style = Type.Small, color = Nova.Dim, modifier = Modifier.align(Alignment.BottomStart),
            )
        }

        // ------- Formulaire
        Column(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 56.dp, vertical = 40.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Ajouter une playlist", style = Type.H1)
            TvTextField(
                "Adresse du serveur", server, ::onServerChange,
                placeholder = "http://exemple.com:8080", keyboardType = KeyboardType.Uri,
                focusRequester = firstField,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                TvTextField("Nom d'utilisateur", user, { user = it }, Modifier.weight(1f))
                TvTextField("Mot de passe", pass, { pass = it }, Modifier.weight(1f), password = true)
            }
            TvTextField(
                "Nom de la playlist (facultatif)", name, { name = it },
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
