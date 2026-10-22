package com.nova.tv.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nova.tv.AppViewModel
import com.nova.tv.BuildConfig
import com.nova.tv.ui.Nova
import com.nova.tv.ui.Route
import com.nova.tv.ui.Type
import com.nova.tv.ui.components.FocusBox
import com.nova.tv.ui.components.NovaButton
import com.nova.tv.ui.components.formatDate

private enum class Section(val label: String) {
    ACCOUNT("Compte"),
    PLAYLISTS("Playlists"),
    PLAYBACK("Lecture"),
    METADATA("Affiches & fiches"),
    ADVANCED("Avancé"),
    ABOUT("À propos"),
}

@Composable
fun SettingsScreen(vm: AppViewModel) {
    var section by rememberSaveable { mutableStateOf(Section.ACCOUNT) }
    Row(Modifier.fillMaxSize().padding(top = 8.dp)) {
        Column(
            Modifier.width(250.dp).fillMaxHeight().padding(start = 36.dp, end = 8.dp, top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Section.entries.forEach { s ->
                CategoryItem(CatEntry(s.name, s.label, -1), section == s) { section = s }
            }
        }
        Column(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 64.dp, top = 8.dp, bottom = 60.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(section.label, style = Type.H1)
            when (section) {
                Section.ACCOUNT -> AccountSection(vm)
                Section.PLAYLISTS -> PlaylistsSection(vm)
                Section.PLAYBACK -> PlaybackSection(vm)
                Section.METADATA -> MetadataSection(vm)
                Section.ADVANCED -> AdvancedSection(vm)
                Section.ABOUT -> AboutSection()
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String, valueColor: Color = Nova.Text) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Nova.Surface)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = Type.H3, color = Nova.Muted, modifier = Modifier.weight(1f))
        Text(value, style = Type.H3, color = valueColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ToggleRow(label: String, description: String, checked: Boolean, onToggle: () -> Unit) {
    FocusBox(onClick = onToggle, modifier = Modifier.fillMaxWidth(), focusedScale = 1.02f, borderWidth = 2.dp) { focused ->
        Row(
            Modifier
                .fillMaxWidth()
                .background(if (focused) Nova.Surface3 else Nova.Surface)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(label, style = Type.H3)
                Text(description, style = Type.Small, color = Nova.Muted)
            }
            Box(
                Modifier
                    .width(44.dp)
                    .height(24.dp)
                    .clip(RoundedCornerShape(50))
                    .background(if (checked) Nova.Accent else Nova.Surface3)
                    .padding(3.dp),
                contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
            ) {
                Box(Modifier.size(18.dp).clip(CircleShape).background(Color.White))
            }
        }
    }
}

@Composable
private fun ChoiceRow(label: String, options: List<Pair<String, String>>, current: String, onSelect: (String) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Nova.Surface)
            .padding(16.dp)
    ) {
        Text(label, style = Type.H3)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { (value, text) -> Chip(text, value == current) { onSelect(value) } }
        }
    }
}

@Composable
private fun AccountSection(vm: AppViewModel) {
    val acc = vm.account
    val info = vm.info
    if (acc == null) {
        Text("Aucun compte connecté.", style = Type.Body, color = Nova.Muted); return
    }
    InfoRow("Playlist", acc.name)
    InfoRow("Serveur", acc.server.substringAfter("://"))
    InfoRow("Utilisateur", acc.username)
    if (info != null) {
        val active = info.status.equals("Active", true)
        InfoRow("Statut", if (active) "Actif" else info.status, if (active) Nova.Green else Nova.Gold)
        InfoRow("Expiration", info.expiresAt?.let { formatDate(it) } ?: "Illimité")
        InfoRow("Connexions", "${info.activeConnections ?: 0} / ${info.maxConnections ?: "?"}")
        if (info.isTrial) InfoRow("Type", "Essai")
    }
    InfoRow("Catalogue", "${vm.channels.size} chaînes · ${vm.movies.size} films · ${vm.series.size} séries")
    Spacer(Modifier.height(4.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        NovaButton("Actualiser le catalogue", onClick = { vm.refresh() }, primary = true)
        NovaButton("Se déconnecter", onClick = { vm.logout() })
    }
}

@Composable
private fun PlaylistsSection(vm: AppViewModel) {
    Text("Basculez entre plusieurs abonnements Xtream Codes.", style = Type.Body, color = Nova.Muted)
    vm.storage.accounts.forEach { acc ->
        val active = acc.id == vm.storage.activeId
        FocusBox(
            onClick = { if (!active) vm.switchAccount(acc) },
            modifier = Modifier.fillMaxWidth().height(60.dp),
            focusedScale = 1.02f,
        ) { focused ->
            Row(
                Modifier.fillMaxSize().background(if (focused) Nova.Surface3 else Nova.Surface).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(acc.name, style = Type.H3)
                    Text("${acc.username} · ${acc.server.substringAfter("://")}", style = Type.Small, color = Nova.Muted)
                }
                if (active) com.nova.tv.ui.components.Badge("ACTIVE")
            }
        }
    }
    NovaButton("Ajouter une playlist", onClick = { vm.navigate(Route.Login) }, primary = true)
}

@Composable
private fun PlaybackSection(vm: AppViewModel) {
    ChoiceRow(
        "Format des chaînes en direct",
        listOf("ts" to "MPEG-TS (recommandé)", "m3u8" to "HLS"),
        vm.storage.liveFormat,
    ) { vm.storage.liveFormat = it }
    ToggleRow(
        "Épisode suivant automatique",
        "Enchaîne l'épisode suivant à la fin d'un épisode.",
        vm.storage.autoNext,
    ) { vm.storage.autoNext = !vm.storage.autoNext }
    ToggleRow(
        "Afficher les catégories adultes",
        "Masquées par défaut sur l'accueil, dans les catalogues et la recherche.",
        vm.storage.showAdult,
    ) { vm.storage.showAdult = !vm.storage.showAdult }
    val context = LocalContext.current
    NovaButton("Effacer l'historique de lecture", onClick = {
        vm.storage.activeId?.let { vm.storage.clearHistory(it) }
        Toast.makeText(context, "Historique effacé", Toast.LENGTH_SHORT).show()
    })
}

@Composable
private fun MetadataSection(vm: AppViewModel) {
    var key by remember { mutableStateOf(vm.storage.tmdbKey) }
    val context = LocalContext.current
    Text(
        "SamothTV utilise The Movie Database (TMDB) pour les fonds d'écran, logos de titres, résumés en français, " +
            "distribution et tendances. Créez une clé gratuite sur themoviedb.org → Paramètres → API, puis collez-la ici " +
            "(clé API v3 ou jeton d'accès v4).",
        style = Type.Body, color = Nova.Muted,
    )
    InfoRow("État", if (vm.storage.tmdbKey.isBlank()) "Non configuré" else "Activé", if (vm.storage.tmdbKey.isBlank()) Nova.Gold else Nova.Green)
    TvTextField("Clé TMDB", key, { key = it }, placeholder = "Collez votre clé ici", imeAction = ImeAction.Done)
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        NovaButton("Enregistrer", onClick = {
            vm.storage.tmdbKey = key
            Toast.makeText(context, "Clé enregistrée. Actualisation…", Toast.LENGTH_SHORT).show()
            vm.refresh()
        }, primary = true)
        NovaButton("Retirer la clé", onClick = { key = ""; vm.storage.tmdbKey = "" })
    }
}

@Composable
private fun AdvancedSection(vm: AppViewModel) {
    var ua by remember { mutableStateOf(vm.storage.userAgent) }
    val context = LocalContext.current
    Text(
        "Certains fournisseurs n'acceptent que certains lecteurs. Si vos chaînes ne se lancent pas, essayez un autre User-Agent.",
        style = Type.Body, color = Nova.Muted,
    )
    TvTextField("User-Agent", ua, { ua = it }, imeAction = ImeAction.Done)
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        NovaButton("Enregistrer", onClick = {
            vm.storage.userAgent = ua
            Toast.makeText(context, "User-Agent enregistré", Toast.LENGTH_SHORT).show()
        }, primary = true)
        NovaButton("Par défaut", onClick = {
            vm.storage.userAgent = ""
            ua = vm.storage.userAgent
        })
    }
    Spacer(Modifier.height(8.dp))
    NovaButton("Vider le cache (images et catalogue)", onClick = {
        vm.clearImageAndDataCache()
        Toast.makeText(context, "Cache vidé", Toast.LENGTH_SHORT).show()
    })
}

@Composable
private fun AboutSection() {
    InfoRow("Application", "SamothTV")
    InfoRow("Version", BuildConfig.VERSION_NAME)
    Text(
        "SamothTV est un lecteur : il ne fournit aucune chaîne ni aucun contenu. Utilisez uniquement des abonnements " +
            "légaux auxquels vous avez droit. Données de films et séries fournies par TMDB ; " +
            "ce produit utilise l'API TMDB mais n'est ni approuvé ni certifié par TMDB.",
        style = Type.Body, color = Nova.Muted,
    )
}
