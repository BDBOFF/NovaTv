@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.nova.tv.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.nova.tv.AppViewModel
import com.nova.tv.data.LicenseManager
import com.nova.tv.ui.LocalDevice
import com.nova.tv.ui.Nova
import com.nova.tv.ui.Type
import com.nova.tv.ui.components.Badge
import com.nova.tv.ui.components.NovaButton
import kotlinx.coroutines.launch

/** QR code dessiné directement (pour acheter depuis son téléphone en regardant la TV). */
@Composable
fun QrCode(text: String, size: Dp, modifier: Modifier = Modifier) {
    val matrix = remember(text) {
        runCatching {
            QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, mapOf(EncodeHintType.MARGIN to 1))
        }.getOrNull()
    }
    Box(modifier.size(size).clip(RoundedCornerShape(10.dp)).background(Color.White).padding(8.dp)) {
        if (matrix != null) Canvas(Modifier.fillMaxSize()) {
            val n = matrix.width
            val cell = this.size.width / n
            for (y in 0 until n) for (x in 0 until n) {
                if (matrix.get(x, y)) drawRect(Color.Black, Offset(x * cell, y * cell), Size(cell + 0.5f, cell + 0.5f))
            }
        }
    }
}

/**
 * Écran d'abonnement : affiché en plein écran quand l'essai est terminé,
 * ou ouvert depuis le badge "Essai" / les réglages.
 */
@Composable
fun LicenseScreen(vm: AppViewModel, blocking: Boolean, onClose: () -> Unit = {}) {
    val lic = vm.license
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val compact = LocalDevice.current.compact
    var code by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }

    fun submit() {
        if (busy) return
        busy = true
        message = null
        scope.launch {
            val err = lic.activate(code)
            busy = false
            if (err == null) {
                android.widget.Toast.makeText(context, "SamothTV est activé. Bon visionnage !", android.widget.Toast.LENGTH_LONG).show()
                onClose()
            } else message = err
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.radialGradient(listOf(Color(0xFF3A0508), Nova.Bg), radius = 1600f))
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = if (compact) 20.dp else 56.dp, vertical = if (compact) 20.dp else 36.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Wordmark(if (compact) 30 else 36, Type.H1)
            Text(
                when {
                    blocking -> "Votre essai gratuit est terminé"
                    lic.state == LicenseManager.State.TRIAL -> "Essai gratuit : encore ${lic.trialDaysLeft} jour${if (lic.trialDaysLeft > 1) "s" else ""}"
                    else -> "Votre licence"
                },
                style = Type.Display,
            )
            Text(
                "Choisissez une offre sur le site, puis saisissez ici la clé reçue par e-mail. " +
                    "Une licence fonctionne sur 2 appareils.",
                style = Type.Body, color = Nova.Muted, modifier = Modifier.widthIn(max = 640.dp),
            )

            // ---------------------------------------------------------------- Offres
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                LicenseManager.offers.forEach { o ->
                    Column(
                        Modifier
                            .width(if (compact) 150.dp else 150.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (o.highlight) Nova.Accent.copy(alpha = 0.18f) else Nova.Surface)
                            .border(1.dp, if (o.highlight) Nova.Accent else Nova.Line, RoundedCornerShape(12.dp))
                            .padding(14.dp),
                    ) {
                        if (o.highlight) {
                            Badge("LE PLUS CHOISI")
                            Spacer(Modifier.height(6.dp))
                        }
                        Text(o.label.uppercase(), style = Type.Label, color = Nova.Muted)
                        Text(o.price, style = Type.H1)
                        Text(o.detail, style = Type.Small, color = Nova.Dim)
                    }
                }
            }

            // ---------------------------------------------------------------- Acheter + clé
            FlowRow(horizontalArrangement = Arrangement.spacedBy(28.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    QrCode(LicenseManager.BUY_URL, 128.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.widthIn(max = 260.dp)) {
                        Text("Acheter avec votre téléphone", style = Type.H3)
                        Text("Scannez le code, ou rendez-vous sur :", style = Type.Small, color = Nova.Muted)
                        Text(LicenseManager.BUY_URL.removePrefix("https://"), style = Type.Small, color = Nova.Text)
                        if (LocalDevice.current.touch) {
                            Spacer(Modifier.height(8.dp))
                            NovaButton("Ouvrir le site", onClick = {
                                runCatching {
                                    context.startActivity(
                                        android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(LicenseManager.BUY_URL))
                                            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                }
                            })
                        }
                    }
                }
                Column(Modifier.widthIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    TvTextField(
                        "Votre clé de licence", code, { code = it },
                        placeholder = "XXXXXXXX-XXXX-XXXX-XXXX-XXXXXXXXXXXX",
                        imeAction = ImeAction.Done, onDone = { submit() }, focusRequester = first,
                    )
                    message?.let { Text(it, style = Type.Small, color = Color(0xFFFFB4A6)) }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        NovaButton(if (busy) "Vérification…" else "Activer", onClick = { submit() }, icon = Icons.Filled.Check, primary = true)
                        if (busy) CircularProgressIndicator(color = Nova.Accent, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                        if (!blocking) NovaButton("Continuer l'essai", onClick = onClose, icon = Icons.Filled.PlayArrow)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "SamothTV est un lecteur : aucune chaîne ni aucun contenu n'est fourni. Abonnement IPTV non inclus.",
                style = Type.Small, color = Nova.Dim,
            )
        }
    }
}
