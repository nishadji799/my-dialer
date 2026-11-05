/*
 * Ashu Phone
 * Copyright (C) 2026 Ashutosh Nishad
 *
 * This file is part of Ashu Phone, licensed under the GNU General Public
 * License, version 3 or (at your option) any later version.
 * See the LICENSE file in the project root. This program comes with ABSOLUTELY NO WARRANTY.
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.ashudialer.app.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import com.ashudialer.app.ui.components.AuroraBackground
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp

val LocalDialerPalette = compositionLocalOf { GradientPalette }

/**
 * "flat" (default, today's existing look) or "raised" - see
 * AppSettings.buttonDepth's doc comment. Read by liquidGlass/glassCard/
 * glassCircle in LiquidGlass.kt so every button, key, and chip across the
 * whole app picks this up automatically with no per-call-site changes,
 * the same reasoning LocalDialerPalette itself already relies on for
 * theme colors.
 */
val LocalButtonDepth = compositionLocalOf { "flat" }


const val AUTO_THEME_ID = "auto"

/**
 * The one place "auto" is turned into a real theme id. Used here, by InCallActivity's
 * pre-Compose window-background placeholder, and by CallNotificationHelper's notification
 * styling, so all three always agree on what "System" actually looks like - previously only
 * this Composable and CallNotificationHelper each had their own hand-copied version of this
 * mapping (identical, but two places to get out of sync) and InCallActivity had none at all,
 * which meant an "auto" user in dark mode got a plain Gradient-colored flash before every call.
 *
 * System light -> "professional" (the Slate look - see ProfessionalPalette's displayName;
 * the id string itself is unchanged so nobody's already-saved explicit "professional" choice
 * or DataStore value needs a migration). System dark -> "darkmode", which - unlike Pure
 * Black - keeps flatSurfaces = false and so already renders every card through the app's
 * normal frosted/translucent "glass" look (see DialerPalette.flatSurfaces's doc comment).
 */
fun resolveThemeId(themeId: String, systemIsDark: Boolean): String =
    if (themeId == AUTO_THEME_ID) {
        // v4: the two simple defaults - plain White in light mode, plain Black in dark mode.
        if (systemIsDark) "darkmode" else "white"
    } else {
        themeId
    }

private val DialerTypography = Typography(
    headlineLarge = TextStyle(fontWeight = FontWeight.ExtraBold, fontSize = 30.sp, letterSpacing = (-0.5).sp),
    titleLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.5.sp),
    bodyLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 16.sp),
    bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 0.5.sp)
)

/**
 * fontSizeIndex -> a fontScale multiplier, the same idea as Android's own
 * Settings > Display > Font size slider (which also works by scaling a
 * density's fontScale, not by changing individual text sizes). 1.0 (index 1,
 * "Medium") is unscaled - today's default look is preserved exactly for
 * anyone who never touches this setting.
 */
private fun fontScaleFor(index: Int): Float = when (index) {
    0 -> 0.88f   // Small
    2 -> 1.15f   // Large
    3 -> 1.3f    // Extra Large
    else -> 1f   // Medium (default)
}

/**
 * v4: a custom accent colour (Personalize > Accent). Only the accent-derived colours change; every
 * theme keeps its own backgrounds, cards and text so contrast stays what the theme was tuned for.
 */
internal fun DialerPalette.withAccent(accent: Color?): DialerPalette {
    if (accent == null) return this
    return copy(
        accent = accent,
        accentSoft = accent.copy(alpha = if (isDark) 0.24f else 0.16f),
        swatchEnd = accent
    )
}

@Composable
fun AshuDialerTheme(
    themeId: String,
    fontSizeIndex: Int = 1,
    buttonDepth: String = "flat",
    style: AshuStyle = AshuStyle(),
    content: @Composable () -> Unit
) {
    val systemIsDark = isSystemInDarkTheme()
    val resolvedId = resolveThemeId(themeId, systemIsDark)
    val basePalette = paletteById(resolvedId)
    val palette = remember(basePalette, style.accentOverride) { basePalette.withAccent(style.accentOverride) }
    // Liquid Glass themes with the aurora switched on: the aurora is drawn once, behind EVERYTHING, and
    // every screen gets a transparent `background` so it shows through. Off / any other theme: untouched.
    SyncSystemBars(palette)
    val glassAurora = palette.glass && style.aurora
    val shownPalette = if (glassAurora) palette.copy(background = SolidColor(Color.Transparent)) else palette

    val scheme = if (palette.isDark) {
        darkColorScheme(
            primary = palette.accent,
            background = palette.solidBackground,
            surface = palette.cardBackground,
            onBackground = palette.textPrimary,
            onSurface = palette.textPrimary
        )
    } else {
        lightColorScheme(
            primary = palette.accent,
            background = palette.solidBackground,
            surface = palette.cardBackground,
            onBackground = palette.textPrimary,
            onSurface = palette.textPrimary
        )
    }

    val baseDensity = LocalDensity.current
    val scaledDensity = remember(baseDensity, fontSizeIndex) {
        Density(density = baseDensity.density, fontScale = fontScaleFor(fontSizeIndex))
    }

    CompositionLocalProvider(
        LocalDialerPalette provides shownPalette,
        LocalDensity provides scaledDensity,
        LocalButtonDepth provides buttonDepth,
        LocalAshuStyle provides style
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = DialerTypography
        ) {
            if (glassAurora) {
                Box(Modifier.fillMaxSize()) {
                    AuroraBackground(palette = palette)
                    content()
                }
            } else {
                // v2.0.1: the theme's own background also fills the area BEHIND the status bar and gesture bar,
                // so those strips always match the app (before, they showed a fixed window colour).
                Box(Modifier.fillMaxSize().background(palette.opaqueBackground)) { content() }
            }
        }
    }
}

/**
 * Keeps the status bar and gesture bar icons in step with the app's own theme on every screen of every
 * activity (not with the phone's light/dark setting), and tells MIUI / HyperOS the same thing - those
 * ROMs keep their own "dark status bar" flag and otherwise ignore the standard one.
 */
@androidx.compose.runtime.Composable
private fun SyncSystemBars(palette: DialerPalette) {
    val view = androidx.compose.ui.platform.LocalView.current
    if (view.isInEditMode) return
    androidx.compose.runtime.SideEffect {
        var ctx: android.content.Context? = view.context
        var window: android.view.Window? = null
        while (ctx is android.content.ContextWrapper) {
            if (ctx is android.app.Activity) {
                window = ctx.window
                break
            }
            ctx = ctx.baseContext
        }
        val w = window ?: return@SideEffect
        val controller = androidx.core.view.WindowCompat.getInsetsController(w, view)
        controller.isAppearanceLightStatusBars = !palette.isDark
        controller.isAppearanceLightNavigationBars = !palette.isDark
        setMiuiDarkStatusIcons(w, darkIcons = !palette.isDark)
    }
}

private fun setMiuiDarkStatusIcons(window: android.view.Window, darkIcons: Boolean) {
    try {
        val flag = Class.forName("android.view.MiuiWindowManager\$LayoutParams")
            .getField("EXTRA_FLAG_STATUS_BAR_DARK_MODE")
            .getInt(null)
        window.javaClass
            .getMethod("setExtraFlags", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
            .invoke(window, if (darkIcons) flag else 0, flag)
    } catch (_: Throwable) {
        // Not a MIUI / HyperOS phone (or the ROM hides it): the standard flags above are enough.
    }
}
