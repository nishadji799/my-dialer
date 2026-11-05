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

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * v4 "Personalize" model. Everything the person can tune about how the app LOOKS and FEELS lives
 * here, is built once from AppSettings (see AppSettings.toAshuStyle) and is handed to every screen
 * through [LocalAshuStyle] - exactly the way LocalDialerPalette already hands out colours. So a
 * single slider in the Personalize screen restyles the whole app with no per-screen plumbing.
 *
 * Defaults reproduce the v3 look, so nobody's phone changes shape unless they move a control.
 */
@Immutable
data class AshuStyle(
    /** 0..1 - how frosted (opaque) glass surfaces are. 0.72 = the v5 default (a little richer than the old 0.6). */
    val glassIntensity: Float = 0.72f,
    /** Multiplier on every rounded corner of glass surfaces (0.5 sharp .. 1.6 very round). */
    val cornerScale: Float = 1f,
    /** 0 = no animation at all, 1 = normal, 1.75 = lively. Scales every duration in the app. */
    val motionScale: Float = 1f,
    /** 0 off, 1 light tick, 2 normal, 3 strong. */
    val hapticLevel: Int = 2,
    /** Custom accent colour chosen by the person, or null to use the theme's own accent. */
    val accentOverride: Color? = null,
    /** "floating" glass pill, "docked" full-width bar. */
    val navStyle: String = NAV_FLOATING,
    /** "always", "selected" (only the active tab shows its name) or "never". */
    val navLabels: String = LABELS_ALWAYS,
    /** "circle", "squircle" or "rounded". */
    val avatarShape: String = AVATAR_CIRCLE,
    /** "compact", "comfortable" or "roomy" - vertical breathing room of list rows. */
    val density: String = DENSITY_COMFORTABLE,
    /** Slowly moving aurora light behind glass themes. */
    val aurora: Boolean = true,
    /** Bright hairline edge on glass surfaces. */
    val specular: Boolean = true,
    /** "glass", "flat" or "outline" dial-pad keys. */
    val keypadStyle: String = KEYPAD_GLASS,
    /** Swipe a recent call: right = call, left = message. */
    val swipeActions: Boolean = true,
    /** Rows slide + fade in one after another when a list opens. */
    val staggerLists: Boolean = true
) {
    val animationsOn: Boolean get() = motionScale > 0.01f

    /** A duration in ms, scaled by [motionScale]. 0 when animations are off. */
    fun ms(base: Int): Int = if (!animationsOn) 0 else (base / motionScale).toInt().coerceAtLeast(1)

    /** Vertical padding of one list row. */
    val rowPadding: Dp
        get() = when (density) {
            DENSITY_COMPACT -> 6.dp
            DENSITY_ROOMY -> 14.dp
            else -> 10.dp
        }

    companion object {
        const val NAV_FLOATING = "floating"
        const val NAV_DOCKED = "docked"

        const val LABELS_ALWAYS = "always"
        const val LABELS_SELECTED = "selected"
        const val LABELS_NEVER = "never"

        const val AVATAR_CIRCLE = "circle"
        const val AVATAR_SQUIRCLE = "squircle"
        const val AVATAR_ROUNDED = "rounded"

        const val DENSITY_COMPACT = "compact"
        const val DENSITY_COMFORTABLE = "comfortable"
        const val DENSITY_ROOMY = "roomy"

        const val KEYPAD_GLASS = "glass"
        const val KEYPAD_FLAT = "flat"
        const val KEYPAD_OUTLINE = "outline"
    }
}

val LocalAshuStyle = compositionLocalOf { AshuStyle() }
