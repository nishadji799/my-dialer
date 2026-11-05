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
package com.ashudialer.app.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ashudialer.app.R
import com.ashudialer.app.ui.components.bounceClick
import com.ashudialer.app.ui.components.liquidGlass
import com.ashudialer.app.ui.theme.AUTO_THEME_ID
import com.ashudialer.app.ui.theme.DefaultPalettes
import com.ashudialer.app.ui.theme.DialerPalette
import com.ashudialer.app.ui.theme.LocalAshuStyle
import com.ashudialer.app.ui.theme.LocalDialerPalette
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * v4 onboarding - completely redesigned.
 *
 * Five full-screen pages in a swipeable pager, each with its own live, drawn animation (no images or
 * video, so it stays crisp on every screen, costs nothing in APK size, and follows the active theme):
 *   1. Welcome            - rings of light spreading from a glass call button
 *   2. Calling            - a dial pad that types a number by itself
 *   3. Recording          - a live waveform with a bookmark and a cloud backup
 *   4. Privacy            - a shield being scanned, with the protections that fill in
 *   5. Make it yours      - the two default looks (White / Black / follow the phone), live
 *
 * Illustrations and text move at different speeds while you swipe (parallax), the page indicator
 * stretches, and the glow behind everything drifts with your finger. All animation honours the
 * "Animation speed" setting - when it is Off nothing moves and nothing is ever left half-drawn.
 *
 * Same public signature as before, so MainActivity needs no change.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    currentThemeId: String,
    onThemeSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalDialerPalette.current
    val scope = rememberCoroutineScope()
    val pageCount = 5
    val pagerState = rememberPagerState(pageCount = { pageCount })

    val pageColors = remember(palette) {
        listOf(
            palette.accent,
            Color(0xFF4F8BFF),
            Color(0xFFFF5A5F),
            Color(0xFF22B573),
            palette.accent
        )
    }
    val titles = remember {
        listOf(
            "Welcome to Ashu Phone",
            "Calling, made beautiful",
            "Record calls your way",
            "Private by design",
            "Make it yours"
        )
    }
    val bodies = remember {
        listOf(
            "A fast, private dialer with the tools power users actually want.",
            "Smart search finds people as you type. Swipe a recent call to call back or message.",
            "Optional. Auto-record, mark key moments, add notes and back up to Google Drive when you choose.",
            "Block spam, hide numbers in Private Space and silence calls with Quiet Hours.",
            "White and Black are the defaults. Dozens of optional looks, including Liquid Glass, live in Personalize."
        )
    }

    Box(modifier = modifier.fillMaxSize().background(palette.opaqueBackground)) {
        // ambient glow that drifts with the swipe
        Canvas(Modifier.fillMaxSize()) {
            val progress = pagerState.currentPage + pagerState.currentPageOffsetFraction
            val w = size.width
            val h = size.height
            fun glow(cx: Float, cy: Float, r: Float, color: Color, a: Float) {
                drawCircle(
                    brush = Brush.radialGradient(listOf(color.copy(alpha = a), Color.Transparent), center = Offset(cx, cy), radius = r),
                    radius = r,
                    center = Offset(cx, cy)
                )
            }
            val idx = progress.toInt().coerceIn(0, pageCount - 1)
            val next = (idx + 1).coerceAtMost(pageCount - 1)
            val frac = (progress - idx).coerceIn(0f, 1f)
            val c = androidx.compose.ui.graphics.lerp(pageColors[idx], pageColors[next], frac)
            val dark = palette.isDark
            glow(w * (0.82f - 0.12f * (progress / (pageCount - 1))), h * 0.16f, w * 0.95f, c, if (dark) 0.30f else 0.22f)
            glow(w * (0.10f + 0.16f * (progress / (pageCount - 1))), h * 0.78f, w * 0.85f, c, if (dark) 0.16f else 0.13f)
        }

        Column(Modifier.fillMaxSize()) {
            // top row: skip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.End
            ) {
                val last = pagerState.currentPage == pageCount - 1
                Text(
                    "Skip",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (last) Color.Transparent else palette.textSecondary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .then(if (last) Modifier else Modifier.bounceClick { onFinished() })
                        .padding(horizontal = 18.dp, vertical = 12.dp)
                )
            }

            HorizontalPager(state = pagerState, modifier = Modifier.weight(1f).fillMaxWidth()) { page ->
                val offset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                val dist = abs(offset).coerceIn(0f, 1f)
                Column(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1.15f)
                            .fillMaxWidth()
                            .graphicsLayer {
                                translationX = -offset * size.width * 0.28f
                                alpha = 1f - dist
                                scaleX = 1f - 0.18f * dist
                                scaleY = 1f - 0.18f * dist
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        when (page) {
                            0 -> WelcomeArt(pageColors[0], palette)
                            1 -> SmartSearchArt(pageColors[1], palette)
                            2 -> RecordArt(pageColors[2], palette)
                            3 -> ShieldArt(pageColors[3], palette)
                            else -> ThemeArt(currentThemeId, onThemeSelected, palette)
                        }
                    }
                    Column(
                        modifier = Modifier
                            .weight(0.85f)
                            .fillMaxWidth()
                            .graphicsLayer {
                                translationX = -offset * size.width * 0.6f
                                alpha = (1f - dist * 1.3f).coerceIn(0f, 1f)
                            },
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            titles[page],
                            fontSize = 28.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = palette.textPrimary,
                            textAlign = TextAlign.Center,
                            lineHeight = 34.sp
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            bodies[page],
                            fontSize = 15.5.sp,
                            color = palette.textSecondary,
                            textAlign = TextAlign.Center,
                            lineHeight = 22.sp
                        )
                    }
                }
            }

            // indicator + primary button
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    repeat(pageCount) { i ->
                        val selected = pagerState.currentPage == i
                        val w by animateDpAsState(if (selected) 28.dp else 8.dp, spring(dampingRatio = 0.65f), label = "dot-w")
                        val c by animateColorAsState(if (selected) pageColors[i] else palette.textSecondary.copy(alpha = 0.35f), label = "dot-c")
                        Box(Modifier.height(8.dp).width(w).clip(CircleShape).background(c))
                    }
                }
                Spacer(Modifier.height(18.dp))
                val isLast = pagerState.currentPage == pageCount - 1
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(RoundedCornerShape(50))
                        .background(palette.accent)
                        .bounceClick(pressedScale = 0.97f) {
                            if (isLast) onFinished()
                            else scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                        },
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (isLast) "Get started" else "Next",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = palette.solidBackground
                    )
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        if (isLast) Icons.Filled.Check else Icons.Filled.ArrowForward,
                        contentDescription = null,
                        tint = palette.solidBackground,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

// ------------------------------------------------------------------------------------------------
//  Page 1 - Welcome: a glass app tile with a slow light sweep and soft echoes
// ------------------------------------------------------------------------------------------------

@Composable
private fun WelcomeArt(color: Color, palette: DialerPalette) {
    val style = LocalAshuStyle.current
    val on = style.animationsOn
    val transition = rememberInfiniteTransition(label = "welcome")
    val ripple by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(style.ms(4200).coerceAtLeast(900), easing = LinearEasing)),
        label = "welcome-ripple"
    )
    val sweep by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(style.ms(3800).coerceAtLeast(900), easing = LinearEasing)),
        label = "welcome-sweep"
    )
    val breathe by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(style.ms(5200).coerceAtLeast(900), easing = LinearEasing), RepeatMode.Reverse),
        label = "welcome-breathe"
    )
    Box(Modifier.size(300.dp), contentAlignment = Alignment.Center) {
        // slow breathing light under the tile
        Box(
            Modifier
                .size(260.dp)
                .graphicsLayer {
                    val s = if (on) 0.92f + 0.16f * breathe else 1f
                    scaleX = s
                    scaleY = s
                    alpha = if (on) 0.55f + 0.35f * breathe else 0.7f
                }
                .background(Brush.radialGradient(listOf(color.copy(alpha = 0.42f), Color.Transparent)))
        )
        // two soft echoes of the tile spreading outward
        repeat(2) { i ->
            Box(
                Modifier
                    .size(132.dp)
                    .graphicsLayer {
                        val p = if (on) (ripple + i * 0.5f) % 1f else 0.25f + 0.4f * i
                        val s = 1f + 0.7f * p
                        scaleX = s
                        scaleY = s
                        alpha = (1f - p) * 0.30f
                    }
                    .border(1.5.dp, color, RoundedCornerShape(40.dp))
            )
        }
        // the glass tile
        Box(
            modifier = Modifier.size(132.dp).liquidGlass(palette, RoundedCornerShape(40.dp), tintAlpha = 0.85f),
            contentAlignment = Alignment.Center
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.45f)
                    .align(Alignment.CenterStart)
                    .graphicsLayer {
                        translationX = (if (on) sweep * 2.6f - 0.6f else 0.4f) * size.width
                        rotationZ = 14f
                    }
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color.Transparent, Color.White.copy(alpha = if (palette.isDark) 0.16f else 0.40f), Color.Transparent)
                        )
                    )
            )
            // v5: the first onboarding page shows the person's own app icon (slightly inset with a
            // soft inner tile) instead of a generic Material phone glyph, so the very first screen
            // already looks like *this* app.
            Image(
                painter = painterResource(R.mipmap.ic_launcher_png),
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier
                    .size(76.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .border(1.dp, Color.White.copy(alpha = if (palette.isDark) 0.20f else 0.55f), RoundedCornerShape(22.dp))
            )
        }
    }
}

// ------------------------------------------------------------------------------------------------
//  Page 2 - smart search that types by itself, and a recent call that swipes
// ------------------------------------------------------------------------------------------------

@Composable
private fun SmartSearchArt(color: Color, palette: DialerPalette) {
    val style = LocalAshuStyle.current
    val on = style.animationsOn
    val people = remember {
        listOf(
            Triple("Aarav Sharma", "Mobile \u2022 98765 43210", "AS"),
            Triple("Aarti Verma", "Mobile \u2022 98123 45670", "AV"),
            Triple("Aaryan Patel", "Mobile \u2022 99887 76655", "AP")
        )
    }
    var typed by remember { mutableStateOf(if (on) "" else "Aar") }
    LaunchedEffect(on) {
        if (on) {
            while (true) {
                typed = ""
                delay(600)
                for (i in 1..5) {
                    typed = "Aarav".take(i)
                    delay(style.ms(260).toLong().coerceAtLeast(60L))
                }
                delay(2800)
            }
        }
    }
    val swipe = remember { Animatable(0f) }
    LaunchedEffect(on) {
        if (on) {
            while (true) {
                delay(3600)
                swipe.animateTo(1f, tween(650, easing = FastOutSlowInEasing))
                delay(500)
                swipe.animateTo(0f, spring(dampingRatio = 0.72f))
                delay(2600)
            }
        }
    }

    Column(Modifier.fillMaxWidth(0.92f), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .liquidGlass(palette, RoundedCornerShape(20.dp))
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Search, contentDescription = null, tint = palette.textSecondary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                if (typed.isEmpty()) "Search contacts or numbers" else typed,
                fontSize = 16.sp,
                color = if (typed.isEmpty()) palette.textSecondary else palette.textPrimary,
                maxLines = 1
            )
        }
        Spacer(Modifier.height(12.dp))
        Column(Modifier.fillMaxWidth().liquidGlass(palette, RoundedCornerShape(24.dp))) {
            people.forEachIndexed { i, (name, sub, initials) ->
                val match = typed.isEmpty() || name.startsWith(typed, ignoreCase = true)
                val rowAlpha by androidx.compose.animation.core.animateFloatAsState(
                    if (match) 1f else 0.32f, tween(220), label = "row-alpha"
                )
                Box(Modifier.fillMaxWidth().graphicsLayer { alpha = rowAlpha }) {
                    if (i == 0) {
                        Row(
                            Modifier
                                .matchParentSize()
                                .background(palette.callGreen.copy(alpha = (0.85f * swipe.value).coerceIn(0f, 1f)))
                                .padding(start = 22.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Call, contentDescription = null, tint = Color.White.copy(alpha = swipe.value.coerceIn(0f, 1f)), modifier = Modifier.size(24.dp))
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer { if (i == 0) translationX = swipe.value * size.width * 0.34f }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier.size(42.dp).clip(CircleShape).background(palette.accentSoft),
                            contentAlignment = Alignment.Center
                        ) { Text(initials, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = palette.accent) }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                buildAnnotatedString {
                                    if (match && typed.isNotEmpty()) {
                                        withStyle(SpanStyle(color = color, fontWeight = FontWeight.Bold)) { append(name.take(typed.length)) }
                                        append(name.drop(typed.length))
                                    } else {
                                        append(name)
                                    }
                                },
                                fontSize = 15.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = palette.textPrimary,
                                maxLines = 1
                            )
                            Text(sub, fontSize = 12.5.sp, color = palette.textSecondary, maxLines = 1)
                        }
                        Box(
                            Modifier.size(38.dp).clip(CircleShape).background(palette.callGreen.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center
                        ) { Icon(Icons.Filled.Call, contentDescription = null, tint = palette.callGreen, modifier = Modifier.size(19.dp)) }
                    }
                }
                if (i < people.lastIndex) {
                    Box(Modifier.fillMaxWidth().padding(start = 68.dp).height(1.dp).background(palette.cardBorder.copy(alpha = 0.5f)))
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "Swipe a recent call: right to call, left to message",
            fontSize = 12.sp,
            color = palette.textSecondary,
            textAlign = TextAlign.Center
        )
    }
}

// ------------------------------------------------------------------------------------------------
//  Page 3 - recording: waveform, bookmark, cloud
// ------------------------------------------------------------------------------------------------

@Composable
private fun RecordArt(color: Color, palette: DialerPalette) {
    val style = LocalAshuStyle.current
    val transition = rememberInfiniteTransition(label = "record")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(style.ms(2600).coerceAtLeast(500), easing = LinearEasing)),
        label = "record-phase"
    )
    val bob by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(style.ms(1800).coerceAtLeast(500), easing = LinearEasing)),
        label = "bob"
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .liquidGlass(palette, RoundedCornerShape(26.dp))
                .padding(18.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val pulse = if (style.animationsOn) 0.55f + 0.45f * sin(phase * 2f * PI.toFloat() * 2f) else 1f
                Box(Modifier.size(10.dp).clip(CircleShape).background(color.copy(alpha = pulse.coerceIn(0.2f, 1f))))
                Spacer(Modifier.width(8.dp))
                Text("REC", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = color, letterSpacing = 1.sp)
                Spacer(Modifier.weight(1f))
                Text("Call with Aarav", fontSize = 12.sp, color = palette.textSecondary)
            }
            Spacer(Modifier.height(12.dp))
            Box {
                Canvas(Modifier.fillMaxWidth().height(84.dp)) {
                    val bars = 27
                    val gap = 4.dp.toPx()
                    val w = (size.width - gap * (bars - 1)) / bars
                    val playhead = if (style.animationsOn) phase else 0.55f
                    for (i in 0 until bars) {
                        val envelope = 0.35f + 0.65f * sin((i / (bars - 1f)) * PI.toFloat())
                        val wobble = 0.5f + 0.5f * sin(phase * 2f * PI.toFloat() + i * 0.55f)
                        val h = (size.height * (0.16f + 0.84f * wobble) * envelope).coerceAtLeast(5.dp.toPx())
                        val played = (i + 0.5f) / bars <= playhead
                        drawRoundRect(
                            color = if (played) color else palette.textSecondary.copy(alpha = 0.3f),
                            topLeft = Offset(i * (w + gap), (size.height - h) / 2f),
                            size = Size(w, h),
                            cornerRadius = CornerRadius(w / 2f, w / 2f)
                        )
                    }
                }
                // a bookmark that pops onto the waveform
                val pop = if (style.animationsOn) ((phase - 0.35f) * 6f).coerceIn(0f, 1f) else 1f
                Icon(
                    Icons.Filled.Bookmark,
                    contentDescription = null,
                    tint = Color(0xFFFFB300),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 108.dp)
                        .size(22.dp)
                        .graphicsLayer {
                            scaleX = pop
                            scaleY = pop
                            alpha = pop
                        }
                )
            }
        }
        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(46.dp).liquidGlass(palette, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.CloudUpload,
                    contentDescription = null,
                    tint = palette.accent,
                    modifier = Modifier
                        .size(24.dp)
                        .graphicsLayer { translationY = if (style.animationsOn) -4.dp.toPx() * sin(bob * 2f * PI.toFloat()) else 0f }
                )
            }
            Spacer(Modifier.width(12.dp))
            Text("Backed up to your Drive folder", fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = palette.textPrimary)
        }
    }
}

// ------------------------------------------------------------------------------------------------
//  Page 4 - a shield that gets scanned
// ------------------------------------------------------------------------------------------------

@Composable
private fun ShieldArt(color: Color, palette: DialerPalette) {
    val style = LocalAshuStyle.current
    val transition = rememberInfiniteTransition(label = "shield")
    val sweep by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(style.ms(3200).coerceAtLeast(600), easing = LinearEasing)),
        label = "sweep"
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(width = 190.dp, height = 220.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val shield = Path().apply {
                    moveTo(w * 0.5f, 0f)
                    cubicTo(w * 0.68f, h * 0.10f, w * 0.86f, h * 0.12f, w, h * 0.14f)
                    lineTo(w, h * 0.50f)
                    cubicTo(w, h * 0.76f, w * 0.74f, h * 0.92f, w * 0.5f, h)
                    cubicTo(w * 0.26f, h * 0.92f, 0f, h * 0.76f, 0f, h * 0.50f)
                    lineTo(0f, h * 0.14f)
                    cubicTo(w * 0.14f, h * 0.12f, w * 0.32f, h * 0.10f, w * 0.5f, 0f)
                    close()
                }
                drawPath(
                    path = shield,
                    brush = Brush.verticalGradient(listOf(color.copy(alpha = 0.28f), color.copy(alpha = 0.06f)))
                )
                clipPath(shield) {
                    val y = (if (style.animationsOn) sweep else 0.5f) * h
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color.Transparent, color.copy(alpha = 0.55f), Color.Transparent),
                            startY = y - 40.dp.toPx(),
                            endY = y + 40.dp.toPx()
                        )
                    )
                }
                drawPath(path = shield, color = color, style = Stroke(width = 2.5.dp.toPx()))
            }
            Icon(Icons.Filled.Lock, contentDescription = null, tint = color, modifier = Modifier.size(58.dp))
        }
        Spacer(Modifier.height(16.dp))
        val items = listOf("Spam blocked", "Private Space", "Quiet Hours")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items.forEachIndexed { i, label ->
                val reveal = if (style.animationsOn) ((sweep * 3.2f) - i).coerceIn(0f, 1f) else 1f
                Row(
                    modifier = Modifier
                        .graphicsLayer {
                            alpha = 0.25f + 0.75f * reveal
                            translationY = (1f - reveal) * 8.dp.toPx()
                        }
                        .liquidGlass(palette, RoundedCornerShape(50))
                        .padding(horizontal = 9.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Check, contentDescription = null, tint = color, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(3.dp))
                    Text(label, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = palette.textPrimary, maxLines = 1, softWrap = false)
                }
            }
        }
    }
}

// ------------------------------------------------------------------------------------------------
//  Page 5 - choose a look (live)
// ------------------------------------------------------------------------------------------------

@Composable
private fun ThemeArt(currentThemeId: String, onThemeSelected: (String) -> Unit, palette: DialerPalette) {
    val options = remember {
        listOf(
            Triple(AUTO_THEME_ID, "Auto", null as DialerPalette?),
            Triple(DefaultPalettes[0].id, "White", DefaultPalettes[0]),
            Triple(DefaultPalettes[1].id, "Black", DefaultPalettes[1])
        )
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            options.forEach { (id, name, p) ->
                val selected = currentThemeId == id
                val scale by androidx.compose.animation.core.animateFloatAsState(
                    if (selected) 1.06f else 0.96f,
                    spring(dampingRatio = 0.6f),
                    label = "theme-scale"
                )
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        }
                        .bounceClick { onThemeSelected(id) }
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 92.dp, height = 156.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .border(if (selected) 3.dp else 1.dp, if (selected) palette.accent else palette.cardBorder, RoundedCornerShape(22.dp))
                    ) {
                        if (p != null) MiniPhone(p) else {
                            // "Auto" = half white, half black
                            Row(Modifier.fillMaxSize()) {
                                Box(Modifier.weight(1f).fillMaxSize()) { MiniPhone(DefaultPalettes[0]) }
                                Box(Modifier.weight(1f).fillMaxSize()) { MiniPhone(DefaultPalettes[1]) }
                            }
                        }
                        if (selected) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(6.dp)
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(palette.accent),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Filled.Check, contentDescription = "Selected", tint = palette.solidBackground, modifier = Modifier.size(15.dp))
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(name, fontSize = 14.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, color = palette.textPrimary)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            if (currentThemeId == AUTO_THEME_ID) "Follows your phone's light / dark setting" else "You can change this any time",
            fontSize = 12.5.sp,
            color = palette.textSecondary
        )
    }
}

/** A tiny drawn phone screen in a theme's own colours. */
@Composable
private fun MiniPhone(p: DialerPalette) {
    Box(Modifier.fillMaxSize().background(p.solidBackground)) {
        Column(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.fillMaxWidth(0.55f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(p.textPrimary.copy(alpha = 0.85f)))
            repeat(3) {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(p.cardBackground).padding(5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(12.dp).clip(CircleShape).background(p.avatarBackground))
                    Spacer(Modifier.width(4.dp))
                    Column {
                        Box(Modifier.width(26.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(p.textPrimary.copy(alpha = 0.7f)))
                        Spacer(Modifier.height(3.dp))
                        Box(Modifier.width(18.dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(p.textSecondary.copy(alpha = 0.6f)))
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            Box(Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(7.dp)).background(p.accent))
        }
    }
}
