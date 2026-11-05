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

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.ashudialer.app.ui.theme.DialerPalette
import com.ashudialer.app.ui.theme.LocalAshuStyle
import com.ashudialer.app.ui.theme.LocalButtonDepth

/**
 * The one glass modifier for the whole app. Every card, sheet, chip and key goes through here, so a
 * change in this file (or a slider in Personalize) restyles every screen at once.
 *
 * PERFORMANCE (rewritten for 2.0.1): the glass used to be four stacked modifiers (clip, background,
 * background, border), each with its own outline work, repeated for every card on screen. It is now ONE
 * cached draw pass: the outline is built once per size, the brushes are built once per look (remembered),
 * and drawing a card is a handful of draw calls. Same look, much less work per frame.
 *
 * Driven by [com.ashudialer.app.ui.theme.AshuStyle]: glass frosting, corner scale and the shiny edge.
 */
@Composable
fun Modifier.liquidGlass(
    palette: DialerPalette,
    shape: Shape = RoundedCornerShape(20.dp),
    tintAlpha: Float = 0.55f,
    borderAlpha: Float = 1f
): Modifier {
    val style = LocalAshuStyle.current
    val shp = rememberScaledShape(shape, style.cornerScale)
    val isRaised = LocalButtonDepth.current == "raised"

    // Pure Black skips the whole glass look - see DialerPalette.flatSurfaces' doc comment.
    if (palette.flatSurfaces) {
        val flatBorder = palette.cardBorder.copy(alpha = (if (isRaised) 1f else 0.85f) * borderAlpha)
        return this
            .let { m ->
                if (isRaised) {
                    m.shadow(
                        elevation = 4.dp,
                        shape = shp,
                        ambientColor = Color.Black.copy(alpha = 0.6f),
                        spotColor = Color.Black.copy(alpha = 0.6f)
                    )
                } else {
                    m
                }
            }
            .clip(shp)
            .background(palette.cardBackground, shp)
            .border(1.dp, flatBorder, shp)
    }

    val look = remember(palette, style.glassIntensity, style.specular, tintAlpha, borderAlpha, isRaised) {
        GlassLook.build(palette, style.glassIntensity, style.specular, tintAlpha, borderAlpha, isRaised)
    }

    return this
        .let { m ->
            if (isRaised && !palette.glass) {
                m.shadow(
                    elevation = 4.dp,
                    shape = shp,
                    ambientColor = Color.Black.copy(alpha = if (palette.isDark) 0.5f else 0.22f),
                    spotColor = Color.Black.copy(alpha = if (palette.isDark) 0.5f else 0.22f)
                )
            } else {
                m
            }
        }
        .clip(shp)
        .drawWithCache {
            val outline = shp.createOutline(size, layoutDirection, this)
            val outlinePath = Path().apply {
                when (outline) {
                    is Outline.Rectangle -> addRect(outline.rect)
                    is Outline.Rounded -> addRoundRect(outline.roundRect)
                    is Outline.Generic -> addPath(outline.path)
                }
            }
            // A stroke is centred on the edge and the clip removes the outer half, so the visible
            // width is half of what is passed here.
            val rimWidth = 2f * look.rimDp * density
            val hairWidth = 1f * density
            onDrawBehind {
                look.fillColor?.let { drawPath(outlinePath, color = it) }
                look.fillBrush?.let { drawPath(outlinePath, brush = it) }
                look.sheen?.let { drawPath(outlinePath, brush = it) }
                drawPath(outlinePath, brush = look.rim, style = Stroke(width = rimWidth))
                look.hairline?.let { drawPath(outlinePath, color = it, style = Stroke(width = hairWidth)) }
            }
        }
}

/** Everything needed to paint one glass look, built once and remembered. */
private class GlassLook(
    val fillColor: Color?,
    val fillBrush: Brush?,
    val sheen: Brush?,
    val rim: Brush,
    val rimDp: Float,
    val hairline: Color?
) {
    companion object {
        fun build(
            palette: DialerPalette,
            glassIntensity: Float,
            specular: Boolean,
            tintAlpha: Float,
            borderAlpha: Float,
            isRaised: Boolean
        ): GlassLook {
            val k = glassIntensity.coerceIn(0f, 1f)
            val dark = palette.isDark

            if (palette.glass) {
                // Callers that ask for a heavier pane (BottomNav passes 0.9) get a proportionally milkier one.
                val boost = (tintAlpha / 0.55f).coerceIn(0.7f, 1.7f)
                // v5: a touch more pane + a noticeably brighter rim. The "glass" reads clearly again
                // without adding any blur, so it stays cheap to draw (no extra per-frame work).
                val topA = (if (dark) 0.085f + 0.16f * k else 0.50f + 0.34f * k) * boost
                val bottomA = (if (dark) 0.042f + 0.088f * k else 0.36f + 0.28f * k) * boost
                val rimBright = if (specular) (if (dark) 0.74f else 1f) else (if (dark) 0.26f else 0.65f)
                return GlassLook(
                    fillColor = null,
                    fillBrush = Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = topA.coerceIn(0f, 0.96f)),
                            Color.White.copy(alpha = bottomA.coerceIn(0f, 0.92f))
                        )
                    ),
                    sheen = Brush.linearGradient(
                        listOf(
                            Color.White.copy(alpha = if (dark) 0.22f else 0.44f),
                            Color.Transparent,
                            Color.White.copy(alpha = if (dark) 0.05f else 0.08f)
                        )
                    ),
                    rim = Brush.linearGradient(
                        listOf(
                            Color.White.copy(alpha = (rimBright * borderAlpha).coerceIn(0f, 1f)),
                            Color.White.copy(alpha = (rimBright * 0.16f * borderAlpha).coerceIn(0f, 1f)),
                            Color.White.copy(alpha = (rimBright * 0.42f * borderAlpha).coerceIn(0f, 1f))
                        )
                    ),
                    rimDp = 1f,
                    hairline = if (dark) Color.Black.copy(alpha = 0.22f) else Color(0xFF0F1B3A).copy(alpha = 0.10f)
                )
            }

            // Classic glass (every non-"glass" palette)
            val tintFactor = 0.45f + 0.9f * k
            val shine = if (specular) 0.5f + 0.8f * k else 0f
            val cardIsNearWhite = !dark &&
                palette.cardBackground.red > 0.92f &&
                palette.cardBackground.green > 0.92f &&
                palette.cardBackground.blue > 0.92f
            val tint = palette.cardBackground.copy(alpha = (tintAlpha * tintFactor).coerceIn(0.08f, 0.98f))
            val highlight = Brush.linearGradient(
                listOf(
                    Color.White.copy(alpha = ((if (dark) 0.22f else 0.45f) * shine).coerceIn(0f, 1f)),
                    Color.White.copy(alpha = ((if (dark) 0.07f else 0.08f) * shine).coerceIn(0f, 1f)),
                    Color.White.copy(alpha = 0f)
                )
            )
            val baseStroke = if (dark) 0.26f else 0.65f
            val edge = if (specular) 1f else 0.55f
            val rim: Brush = if (cardIsNearWhite) {
                val c = Color.Black.copy(alpha = ((if (isRaised) 0.16f else 0.10f) * borderAlpha).coerceIn(0f, 1f))
                Brush.linearGradient(listOf(c, c))
            } else {
                val a = ((baseStroke + if (isRaised) 0.12f else 0f) * borderAlpha * edge).coerceIn(0f, 1f)
                if (specular) {
                    Brush.linearGradient(
                        listOf(
                            Color.White.copy(alpha = (a * 1.35f).coerceAtMost(1f)),
                            Color.White.copy(alpha = a * 0.45f),
                            Color.White.copy(alpha = a * 0.8f)
                        )
                    )
                } else {
                    Brush.linearGradient(listOf(Color.White.copy(alpha = a), Color.White.copy(alpha = a)))
                }
            }
            return GlassLook(
                fillColor = tint,
                fillBrush = null,
                sheen = highlight,
                rim = rim,
                rimDp = 1f,
                hairline = null
            )
        }
    }
}

/** Scales every rounded corner of [base] by [k]; each corner is capped at half the shortest side. */
private class ScaledCornerShape(
    private val base: CornerBasedShape,
    private val k: Float
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val maxRadius = minOf(size.width, size.height) / 2f
        fun radius(c: CornerSize): Float = (c.toPx(size, density) * k).coerceIn(0f, maxRadius)
        val topStart = radius(base.topStart)
        val topEnd = radius(base.topEnd)
        val bottomEnd = radius(base.bottomEnd)
        val bottomStart = radius(base.bottomStart)
        val ltr = layoutDirection == LayoutDirection.Ltr
        return Outline.Rounded(
            RoundRect(
                left = 0f,
                top = 0f,
                right = size.width,
                bottom = size.height,
                topLeftCornerRadius = CornerRadius(if (ltr) topStart else topEnd),
                topRightCornerRadius = CornerRadius(if (ltr) topEnd else topStart),
                bottomRightCornerRadius = CornerRadius(if (ltr) bottomEnd else bottomStart),
                bottomLeftCornerRadius = CornerRadius(if (ltr) bottomStart else bottomEnd)
            )
        )
    }
}

@Composable
private fun rememberScaledShape(shape: Shape, k: Float): Shape =
    remember(shape, k) {
        // Circles (avatars, round buttons) and anything that is not a rounded-corner shape stay as is.
        if (k == 1f || shape !is CornerBasedShape || shape == CircleShape) shape
        else ScaledCornerShape(shape, k)
    }

/** Rounded-rect glass panel - the default for cards, rows, sheets, and settings items. */
@Composable
fun Modifier.glassCard(
    palette: DialerPalette,
    corner: Dp = 18.dp,
    tintAlpha: Float = 0.55f
): Modifier = this.liquidGlass(palette, RoundedCornerShape(corner), tintAlpha)

/** Circular glass chip - for avatar rings, icon badges, and pill buttons. */
@Composable
fun Modifier.glassCircle(
    palette: DialerPalette,
    tintAlpha: Float = 0.55f
): Modifier = this.liquidGlass(palette, CircleShape, tintAlpha)
