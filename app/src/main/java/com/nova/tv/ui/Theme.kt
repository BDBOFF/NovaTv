package com.nova.tv.ui

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.nova.tv.R

object Nova {
    val Bg = Color(0xFF07070A)
    val Surface = Color(0xFF14141B)
    val Surface2 = Color(0xFF1D1D26)
    val Surface3 = Color(0xFF282833)
    val Accent = Color(0xFFD10A20)
    val Accent2 = Color(0xFF7A000F)
    val Text = Color(0xFFF5F5F7)
    val Muted = Color(0xFFA3A3B2)
    val Dim = Color(0xFF6B6B7A)
    val Line = Color(0x26FFFFFF)
    val Gold = Color(0xFFFFC24B)
    val Green = Color(0xFF3DDC84)

    val AccentBrush = Brush.horizontalGradient(listOf(Accent, Accent2))
}

val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
    Font(R.font.inter_extrabold, FontWeight.ExtraBold),
    Font(R.font.inter_black, FontWeight.Black),
)

object Type {
    val Display = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Black, fontSize = 40.sp, lineHeight = 44.sp, letterSpacing = (-0.8).sp)
    val H1 = TextStyle(fontFamily = Inter, fontWeight = FontWeight.ExtraBold, fontSize = 28.sp, lineHeight = 32.sp, letterSpacing = (-0.4).sp)
    val H2 = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 18.sp, lineHeight = 22.sp, letterSpacing = (-0.2).sp)
    val H3 = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 18.sp)
    val Body = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 19.sp)
    val Small = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp)
    val Label = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 9.sp, lineHeight = 11.sp, letterSpacing = 0.8.sp)
}

@Composable
fun NovaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Nova.Accent,
            secondary = Nova.Accent2,
            background = Nova.Bg,
            surface = Nova.Surface,
            onPrimary = Color.White,
            onBackground = Nova.Text,
            onSurface = Nova.Text,
        ),
        typography = Typography(bodyMedium = Type.Body, bodyLarge = Type.Body),
    ) {
        CompositionLocalProvider(LocalContentColor provides Nova.Text, content = content)
    }
}
