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

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.outlined.Dialpad
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import kotlin.math.roundToInt
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.animation.core.Animatable
import kotlin.math.abs
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.border
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ashudialer.app.ui.theme.AshuStyle
import com.ashudialer.app.ui.theme.DialerPalette
import com.ashudialer.app.ui.theme.LocalAshuStyle
import com.ashudialer.app.ui.theme.LocalDialerPalette

enum class DialerTab(val label: String) {
    CONTACTS("Contacts"),
    RECENT("Recent"),
    DIALER("Dialer"),
    PROTECT("Protect"),
    MORE("More")
}


/** Height of one tab cell. The sliding pill uses the same height, so it always lines up with the tabs. */
private val NavCellHeight = 56.dp

/**
 * The bottom bar. A frosted "glass" pill sits under the selected tab. Tap a tab and the pill glides there. Put a
 * finger on the bar and slide it left or right and the pill follows the finger, the tab under it is selected as it
 * passes (so the screen above switches with its slide animation), and on release it settles on the nearest tab.
 * Only THIS bar reacts to sliding; the pages themselves do not swipe.
 */
@Composable
fun DialerBottomNav(
    selected: DialerTab,
    onSelect: (DialerTab) -> Unit,
    // v2.0.1: the tabs to show, already in the person's chosen order (see visibleNavTabs)
    tabs: List<DialerTab> = DialerTab.values().toList()
) {
    val palette = LocalDialerPalette.current
    val style = LocalAshuStyle.current
    val view = androidx.compose.ui.platform.LocalView.current
    val hapticState = rememberUpdatedState(style.hapticLevel)
    val selIndex = tabs.indexOf(selected).coerceAtLeast(0)
    val currentOnSelect by rememberUpdatedState(onSelect)
    // v4: a light tick every time the selected tab changes (strength follows Personalize > Haptics).
    val fireSelect: (DialerTab) -> Unit = { tab ->
        val level = hapticState.value
        if (level > 0) {
            view.performHapticFeedback(
                if (level >= 3) android.view.HapticFeedbackConstants.LONG_PRESS
                else android.view.HapticFeedbackConstants.CLOCK_TICK
            )
        }
        currentOnSelect(tab)
    }

    // Pill position in tab units (0.0 = first tab ... 4.0 = last). NaN while no finger is dragging.
    var dragPosition by remember { mutableFloatStateOf(Float.NaN) }
    var lastDragPosition by remember { mutableFloatStateOf(selIndex.toFloat()) }
    var pressedIndex by remember { mutableIntStateOf(-1) }
    val glide = remember { Animatable(selIndex.toFloat()) }
    val dragging = !dragPosition.isNaN()

    LaunchedEffect(selected, tabs) {
        if (!dragging) glide.animateTo(selIndex.toFloat(), spring(dampingRatio = 0.78f, stiffness = 420f))
    }
    LaunchedEffect(dragging) {
        if (!dragging) {
            // The finger lifted: carry on from where the pill was left instead of jumping back to the old tab.
            glide.snapTo(lastDragPosition)
            glide.animateTo(selIndex.toFloat(), spring(dampingRatio = 0.78f, stiffness = 420f))
        }
    }
    val position: () -> Float = { if (dragPosition.isNaN()) glide.value else dragPosition }

    // v4 Personalize > Bottom bar: a floating glass pill (default) or a full-width docked bar.
    val navDocked = style.navStyle == AshuStyle.NAV_DOCKED
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
            .then(if (navDocked) Modifier else Modifier.padding(horizontal = 12.dp, vertical = 10.dp))
            .liquidGlass(
                palette = palette,
                shape = if (navDocked) RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp) else RoundedCornerShape(28.dp),
                tintAlpha = 0.9f
            )
            .padding(vertical = 8.dp, horizontal = 4.dp)
    ) {
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .height(NavCellHeight)
                .pointerInput(tabs) {
                    val slop = viewConfiguration.touchSlop
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val cellW = size.width / tabs.size.toFloat()
                        fun indexAt(x: Float) = (x / cellW).toInt().coerceIn(0, tabs.size - 1)
                        fun pillAt(x: Float) = (x / cellW - 0.5f).coerceIn(0f, (tabs.size - 1).toFloat())
                        pressedIndex = indexAt(down.position.x)
                        var dragged = false
                        var lastIndex = -1
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) {
                                // A plain tap (no slide): select the tab that was touched.
                                if (!dragged) fireSelect(tabs[indexAt(change.position.x)])
                                break
                            }
                            if (!dragged && abs(change.position.x - down.position.x) > slop) {
                                dragged = true
                                pressedIndex = -1
                            }
                            if (dragged) {
                                change.consume()
                                val p = pillAt(change.position.x)
                                dragPosition = p
                                lastDragPosition = p
                                val idx = p.roundToInt().coerceIn(0, tabs.size - 1)
                                if (idx != lastIndex) {
                                    lastIndex = idx
                                    fireSelect(tabs[idx])
                                }
                            }
                        }
                        pressedIndex = -1
                        dragPosition = Float.NaN
                    }
                }
        ) {
            val cellW = maxWidth / tabs.size
            val cellWpx = with(LocalDensity.current) { cellW.toPx() }

            // The glass pill: translucent fill, a soft top-to-bottom sheen and a bright hairline edge.
            Box(
                Modifier
                    .width(cellW)
                    .height(NavCellHeight)
                    .graphicsLayer {
                        val pos = position()
                        translationX = pos * cellWpx
                        // v4 "liquid" stretch: the glass bubble elongates while it travels to a new tab
                        // and settles back into shape as it arrives.
                        val stretch = if (style.animationsOn) abs(pos - selIndex).coerceIn(0f, 1f) else 0f
                        scaleX = 1f + 0.24f * stretch
                        scaleY = 1f - 0.08f * stretch
                    }
                    .padding(horizontal = 4.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(palette.accentSoft, palette.accentSoft.copy(alpha = 0.62f))
                        )
                    )
                    .border(1.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(18.dp))
            )

            Row(Modifier.fillMaxSize()) {
                tabs.forEachIndexed { index, tab ->
                    NavTabItem(
                        tab = tab,
                        index = index,
                        pressed = pressedIndex == index,
                        position = position,
                        palette = palette,
                        labels = style.navLabels,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun NavTabItem(
    tab: DialerTab,
    index: Int,
    pressed: Boolean,
    position: () -> Float,
    palette: DialerPalette,
    labels: String,
    modifier: Modifier
) {
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = tween(if (pressed) 60 else 140),
        label = "nav-press"
    )
    // 1.0 when the pill sits exactly on this tab, 0.0 when it is a whole tab or more away.
    val near = (1f - abs(position() - index)).coerceIn(0f, 1f)
    val contentColor = lerp(palette.textSecondary, palette.accent, near)
    val isSelected = near > 0.5f

    Column(
        modifier = modifier
            .fillMaxHeight()
            .graphicsLayer { scaleX = pressScale; scaleY = pressScale },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = iconFor(tab, isSelected),
            contentDescription = tab.label,
            tint = contentColor,
            modifier = Modifier
                .size(if (labels == AshuStyle.LABELS_NEVER) 25.dp else 22.dp)
                .graphicsLayer {
                    val grow = 1f + 0.12f * near
                    scaleX = grow
                    scaleY = grow
                }
        )
        val showLabel = labels == AshuStyle.LABELS_ALWAYS || (labels == AshuStyle.LABELS_SELECTED && isSelected)
        androidx.compose.animation.AnimatedVisibility(
            visible = showLabel,
            enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.expandVertically(),
            exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.shrinkVertically()
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(3.dp))
                Text(
                    text = tab.label,
                    fontSize = 10.5.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                    color = contentColor
                )
            }
        }
    }
}

private fun iconFor(tab: DialerTab, selected: Boolean): ImageVector = when (tab) {
    DialerTab.CONTACTS -> if (selected) Icons.Filled.Person else Icons.Outlined.Person
    DialerTab.RECENT -> if (selected) Icons.Filled.Phone else Icons.Outlined.Phone
    DialerTab.DIALER -> if (selected) Icons.Filled.Dialpad else Icons.Outlined.Dialpad
    DialerTab.PROTECT -> if (selected) Icons.Filled.Shield else Icons.Outlined.Shield
    DialerTab.MORE -> if (selected) Icons.Filled.MoreHoriz else Icons.Outlined.MoreHoriz
}

/** v5: the tab's outline icon, for the drag-to-reorder rows in Personalize. */
fun tabIcon(tab: DialerTab): ImageVector = iconFor(tab, false)

/** The saved order as tabs. Unknown names are ignored and any tab missing from the list is appended. */
fun parseNavOrder(csv: String): List<DialerTab> {
    val parsed = csv.split(',').mapNotNull { n -> DialerTab.values().firstOrNull { it.name == n.trim() } }.distinct()
    return parsed + DialerTab.values().filter { it !in parsed }
}

fun serializeNavOrder(tabs: List<DialerTab>): String = tabs.joinToString(",") { it.name }

/** Tabs for the bottom bar: the chosen order, minus the hidden ones. Contacts, Protect and More always stay. */
fun visibleNavTabs(order: String, showDialer: Boolean, showRecent: Boolean): List<DialerTab> =
    parseNavOrder(order).filter { (it != DialerTab.DIALER || showDialer) && (it != DialerTab.RECENT || showRecent) }
