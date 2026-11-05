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

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ashudialer.app.ui.theme.LocalAshuStyle
import kotlinx.coroutines.delay

/**
 * v4 motion toolkit. Every helper reads [LocalAshuStyle] so the "Animation speed" slider in
 * Personalize (including "Off") controls them all at once. When animations are off they render
 * their final state immediately - nothing is ever left half-faded.
 */

/** Springy "press in" feel: the element shrinks a little while a finger is on it and bounces back. */
@Composable
fun Modifier.pressScale(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = 0.95f
): Modifier {
    val style = LocalAshuStyle.current
    val pressed by interactionSource.collectIsPressedAsState()
    val target = if (pressed && style.animationsOn) pressedScale else 1f
    val scale by animateFloatAsState(
        targetValue = target,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium * style.motionScale.coerceIn(0.4f, 2f)
        ),
        label = "press-scale"
    )
    return this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/** Clickable with the springy press feel and no grey ripple box (the glass look has its own feedback). */
@Composable
fun Modifier.bounceClick(
    enabled: Boolean = true,
    pressedScale: Float = 0.95f,
    onClick: () -> Unit
): Modifier {
    val source = remember { MutableInteractionSource() }
    return this
        .pressScale(source, pressedScale)
        .clickable(
            interactionSource = source,
            indication = null,
            enabled = enabled,
            onClick = onClick
        )
}

/**
 * Slide-up + fade-in for list rows, one after another. Only the first [maxStaggered] rows are
 * delayed, so a long list never feels slow; the rest simply appear.
 */
@Composable
fun Modifier.staggerIn(
    index: Int,
    stepMs: Int = 32,
    maxStaggered: Int = 6,
    distance: Dp = 14.dp
): Modifier {
    val style = LocalAshuStyle.current
    if (!style.animationsOn || !style.staggerLists) return this
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay((index.coerceAtMost(maxStaggered) * stepMs / style.motionScale).toLong())
        shown = true
    }
    val progress by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(style.ms(280)),
        label = "stagger"
    )
    return this.graphicsLayer {
        alpha = progress
        translationY = (1f - progress) * distance.toPx()
    }
}

/** A soft pulsing halo behind an element - used for the REC dot, the call button, live states. */
@Composable
fun Modifier.pulseHalo(
    color: Color,
    active: Boolean = true,
    maxRadius: Dp = 28.dp
): Modifier {
    val style = LocalAshuStyle.current
    if (!active || !style.animationsOn) return this
    val transition = rememberInfiniteTransition(label = "halo")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(style.ms(1700), easing = LinearEasing)),
        label = "halo-t"
    )
    return this.drawBehind {
        // Read inside the draw block: only the draw phase re-runs each frame, not recomposition.
        val p = t
        val r = maxRadius.toPx() * (0.55f + 0.45f * p)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(color.copy(alpha = 0.38f * (1f - p)), Color.Transparent),
                center = center,
                radius = r
            ),
            radius = r,
            center = center
        )
    }
}

/** A diagonal light sweep over a placeholder while something loads. */
@Composable
fun Modifier.shimmerSweep(base: Color, highlight: Color): Modifier {
    val style = LocalAshuStyle.current
    val transition = rememberInfiniteTransition(label = "shimmer")
    val t by transition.animateFloat(
        initialValue = -0.4f,
        targetValue = 1.4f,
        animationSpec = infiniteRepeatable(
            tween(if (style.animationsOn) style.ms(1300) else 1300, easing = LinearEasing),
            RepeatMode.Restart
        ),
        label = "shimmer-t"
    )
    return this.drawBehind {
        val w = size.width
        val x = t * w
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(base, highlight, base),
                start = Offset(x - w * 0.3f, 0f),
                end = Offset(x + w * 0.3f, size.height)
            )
        )
    }
}
