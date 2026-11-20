// SPDX-License-Identifier: GPL-3.0-only
package com.sour.lumaduo.ui.luma

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * LumaDuo dark palette. This is the single source of truth for the bottom-screen UI;
 * the prototype CSS mirrors these values.
 */
object LumaColors {
    val Bg = Color(0xFF0F0F0F)
    val Surface1 = Color(0xFF17171A)
    val Surface2 = Color(0xFF1F1F24)
    val Surface3 = Color(0xFF26262E)
    val Surface4 = Color(0xFF32323C)
    val Outline = Color(0xFF2E2E38)
    val OutlineStrong = Color(0xFF42424E)
    val Text = Color(0xFFF5F5F7)
    val Secondary = Color(0xFFA9A9B4)
    val Tertiary = Color(0xFF73737F)
    val Disabled = Color(0xFF4A4A55)
    val Accent = Color(0xFFF2C14E)
    val OnAccent = Color(0xFF1A1205)
    val AccentSoft = Color(0x1FF2C14E)
    val Signal = Color(0xFF5ED0FF)
    val Success = Color(0xFF4CC38A)
    val Warning = Color(0xFFFF7A45)
    val Error = Color(0xFFF2555A)
    val FocusRing = Color(0xFFF5F5F7)
    val FocusHalo = Color(0xFF000000)
    val Scrim = Color(0xA8000000)
    val StageBg = Color(0xFF000000)

    internal val scheme = darkColorScheme(
        primary = Accent,
        onPrimary = OnAccent,
        primaryContainer = AccentSoft,
        onPrimaryContainer = Accent,
        secondary = Signal,
        onSecondary = Color(0xFF06141B),
        background = Bg,
        onBackground = Text,
        surface = Surface1,
        onSurface = Text,
        surfaceVariant = Surface3,
        onSurfaceVariant = Secondary,
        outline = OutlineStrong,
        error = Error,
        onError = Color(0xFF1B0507),
    )
}

object LumaType {
    val display = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
    )
    val titleL = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
    )
    val titleM = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    )
    val bodyL = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    )
    val bodyM = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    )
    val label = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    )
    val caption = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 11.sp,
        lineHeight = 14.sp,
    )
    val micro = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 9.sp,
        lineHeight = 12.sp,
    )
    val numeric = TextStyle(
        fontFamily = FontFamily.Default,
        fontFeatureSettings = "tnum",
        fontSize = 13.sp,
        lineHeight = 18.sp,
    )

    internal val material = Typography(
        displayLarge = display,
        titleLarge = titleL,
        titleMedium = titleM,
        bodyLarge = bodyL,
        bodyMedium = bodyM,
        labelLarge = label,
        labelMedium = caption,
        labelSmall = micro,
        bodySmall = caption,
    )
}

object LumaMetrics {
    val RailWidth = 64.dp
    val AppBarHeight = 44.dp
    val ModeBarHeight = 28.dp
    val ContentHeight = 408.dp
    val PagePadding = 12.dp
    val SectionGap = 16.dp
    val CardGap = 8.dp
    val TouchTarget = 44.dp
    val FocusStroke = 2.dp
}

object LumaShapes {
    val chip = RoundedCornerShape(6.dp)
    val control = RoundedCornerShape(8.dp)
    val card = RoundedCornerShape(10.dp)
    val panel = RoundedCornerShape(12.dp)
    val sheet = RoundedCornerShape(16.dp)

    internal val material = Shapes(
        extraSmall = chip,
        small = control,
        medium = card,
        large = panel,
        extraLarge = sheet,
    )
}

@Composable
fun LumaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LumaColors.scheme,
        typography = LumaType.material,
        shapes = LumaShapes.material,
        content = content,
    )
}
