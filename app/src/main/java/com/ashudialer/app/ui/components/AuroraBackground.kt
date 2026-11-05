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
package com.ashudialer.app.ui.components

import android.content.Context
import android.os.PowerManager
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import com.ashudialer.app.ui.theme.DialerPalette
import com.ashudialer.app.ui.theme.LocalAshuStyle
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The light that lives behind every Liquid Glass surface.
 *
 * PERFORMANCE (rewritten for 2.0.1): the first version redrew four full-screen radial gradients on
 * every frame, which is a lot of work for a mid-range GPU once dozens of translucent glass panes sit
 * on top of it. Now the coloured blobs are drawn ONCE into a cached layer, and the "movement" is only
 * the GPU sliding / slowly rotating that finished layer (graphicsLayer properties change, nothing is
 * re-drawn or re-composed). Two layers move in opposite directions so it still looks like living light.
 *
 * It also never blurs anything, freezes when animation speed is "Off" or Battery Saver is on, and
 * does not even start its animation clock in those cases.
 */
@Composable
fun AuroraBackground(
    palette: DialerPalette,
    modifier: Modifier = Modifier
) {
    val style = LocalAshuStyle.current
    val context = LocalContext.current
    val saver = remember(context) {
        runCatching {
            (context.getSystemService(Context.POWER_SERVICE) as? PowerManager)?.isPowerSaveMode == true
        }.getOrDefault(false)
    }
    val animate = style.animationsOn && !saver
    val src = palette.auroraColors.ifEmpty { listOf(palette.accent, palette.accentSoft, palette.accent, palette.accentSoft) }
    // v5: a little calmer than before. The old 0.62 light strength washed the whole screen in colour,
    // which is what read as "cartoony". The white-glass themes now sit on a quiet, near-white light.
    val strength = if (palette.isDark) 0.46f else 0.42f
    val layerA = remember(src, strength) {
        listOf(
            Blob(0.20f, 0.14f, 0.62f, src[0 % src.size], strength),
            Blob(0.88f, 0.40f, 0.58f, src[1 % src.size], strength * 0.9f)
        )
    }
    val layerB = remember(src, strength) {
        listOf(
            Blob(0.30f, 0.84f, 0.66f, src[2 % src.size], strength * 0.85f),
            Blob(0.80f, 0.94f, 0.50f, src[3 % src.size], strength * 0.7f)
        )
    }

    Box(modifier.fillMaxSize().background(palette.opaqueBackground)) {
        if (animate) {
            val transition = rememberInfiniteTransition(label = "aurora")
            val clockA by transition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(38_000, easing = LinearEasing)),
                label = "aurora-a"
            )
            val clockB by transition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(53_000, easing = LinearEasing)),
                label = "aurora-b"
            )
            AuroraLayer(layerA, phase = { clockA }, reverse = false)
            AuroraLayer(layerB, phase = { clockB }, reverse = true)
        } else {
            AuroraLayer(layerA, phase = { 0.2f }, reverse = false)
            AuroraLayer(layerB, phase = { 0.6f }, reverse = true)
        }
    }
}

private data class Blob(val cx: Float, val cy: Float, val radius: Float, val color: Color, val alpha: Float)

@Composable
private fun AuroraLayer(blobs: List<Blob>, phase: () -> Float, reverse: Boolean) {
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                // Read inside the layer block: only the layer is updated each frame, never recomposed.
                val a = phase() * 2f * PI.toFloat() * (if (reverse) -1f else 1f)
                translationX = size.width * 0.09f * cos(a)
                translationY = size.height * 0.05f * sin(a)
                rotationZ = 4f * sin(a)
                scaleX = 1.22f
                scaleY = 1.22f
            }
            .drawWithCache {
                onDrawBehind {
                    val w = size.width
                    val h = size.height
                    blobs.forEach { b ->
                        val r = b.radius * maxOf(w, h)
                        val center = Offset(b.cx * w, b.cy * h)
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(b.color.copy(alpha = b.alpha), Color.Transparent),
                                center = center,
                                radius = r
                            ),
                            radius = r,
                            center = center
                        )
                    }
                }
            }
    )
}
