package com.nova.tv.ui

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Type d'appareil : la même app s'adapte à la TV (télécommande), à la tablette et au téléphone (tactile).
 * - isTv : navigation à la télécommande
 * - compact : petit écran (téléphone), mise en page mobile
 */
data class DeviceInfo(val isTv: Boolean, val compact: Boolean) {
    val touch: Boolean get() = !isTv
}

val LocalDevice = staticCompositionLocalOf { DeviceInfo(isTv = true, compact = false) }

/** Marge latérale de l'écran : large sur TV, réduite sur téléphone. */
val LocalGutter = staticCompositionLocalOf { 48.dp }

val Gutter: Dp
    @Composable @ReadOnlyComposable
    get() = LocalGutter.current

fun isTvDevice(context: Context): Boolean {
    val ui = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
    if (ui?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION) return true
    val pm = context.packageManager
    return pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
        pm.hasSystemFeature("amazon.hardware.fire_tv") ||
        !pm.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)
}
