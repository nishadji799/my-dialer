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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallMade
import androidx.compose.material.icons.filled.CallMissed
import androidx.compose.material.icons.filled.CallReceived
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ashudialer.app.data.RecentCall
import com.ashudialer.app.data.db.CallDirection
import com.ashudialer.app.ui.theme.DialerPalette
import com.ashudialer.app.ui.theme.LocalAshuStyle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The round shortcut buttons above the bottom bar, and the glass windows they open.
 *
 * What the person sees (the animation from the reference video, in Ashu Phone's own glass style):
 *  - a round button sits at the bottom-right, just above the bar;
 *  - tapping it grows a floating glass window out of the button (a springy scale from the button's own
 *    corner, with a fade), over a dimmed EMPTY backdrop - nothing from the page behind shows through;
 *  - the button turns into a close (X) while the window is open; tapping it, the backdrop or Back closes it.
 *
 * Which button shows where is decided by the caller (Dialer on Recents; Recents on the other tabs; both
 * when the Dialer tab was removed from the bar). Every duration follows Personalize > Animation speed.
 */
@Composable
fun FloatingQuickAccess(
    palette: DialerPalette,
    showDialerFab: Boolean,
    showRecentsFab: Boolean,
    dialerOpen: Boolean,
    recentsOpen: Boolean,
    onToggleDialer: () -> Unit,
    onToggleRecents: () -> Unit,
    onDismiss: () -> Unit,
    dialerContent: @Composable () -> Unit,
    recentsContent: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    val style = LocalAshuStyle.current
    val anyOpen = dialerOpen || recentsOpen
    // v5: grow from the bottom centre with a critically-damped spring. The old bottom-right origin
    // plus a bouncy spring made the numbers inside travel diagonally and overshoot ("side side").
    val cornerOrigin = TransformOrigin(0.5f, 1f)

    Box(modifier.fillMaxSize()) {
        // dimmed, EMPTY backdrop
        AnimatedVisibility(
            visible = anyOpen,
            enter = fadeIn(tween(style.ms(220))),
            exit = fadeOut(tween(style.ms(180)))
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(palette.solidBackground.copy(alpha = 0.84f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss
                    )
            )
        }

        // the dialer window
        AnimatedVisibility(
            visible = dialerOpen,
            enter = scaleIn(spring(dampingRatio = 1f, stiffness = 420f), initialScale = 0.12f, transformOrigin = cornerOrigin) +
                fadeIn(tween(style.ms(160))),
            exit = scaleOut(tween(style.ms(200)), targetScale = 0.12f, transformOrigin = cornerOrigin) +
                fadeOut(tween(style.ms(140))),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            FloatingWindowFrame(palette, heightFraction = 0.75f, bottomGap = if (showRecentsFab && showDialerFab) 132.dp else 78.dp) {
                dialerContent()
            }
        }

        // the recents window
        AnimatedVisibility(
            visible = recentsOpen,
            enter = scaleIn(spring(dampingRatio = 1f, stiffness = 420f), initialScale = 0.12f, transformOrigin = cornerOrigin) +
                fadeIn(tween(style.ms(160))),
            exit = scaleOut(tween(style.ms(200)), targetScale = 0.12f, transformOrigin = cornerOrigin) +
                fadeOut(tween(style.ms(140))),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            FloatingWindowFrame(palette, heightFraction = 0.62f, bottomGap = if (showRecentsFab && showDialerFab) 132.dp else 78.dp) {
                recentsContent()
            }
        }

        // the buttons
        Column(
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 18.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (showRecentsFab) {
                QuickButton(palette, open = recentsOpen, sizeDp = if (showDialerFab) 48 else 60, onClick = onToggleRecents) {
                    Icon(Icons.Filled.History, contentDescription = "Recents", tint = Color.White, modifier = Modifier.size(if (showDialerFab) 22.dp else 26.dp))
                }
            }
            if (showDialerFab) {
                QuickButton(palette, open = dialerOpen, sizeDp = 60, onClick = onToggleDialer) {
                    Icon(Icons.Filled.Dialpad, contentDescription = "Dialer", tint = Color.White, modifier = Modifier.size(28.dp))
                }
            }
        }
    }
}

@Composable
private fun FloatingWindowFrame(
    palette: DialerPalette,
    heightFraction: Float,
    bottomGap: androidx.compose.ui.unit.Dp,
    content: @Composable () -> Unit
) {
    Box(
        Modifier
            .fillMaxWidth()
            .fillMaxHeight(heightFraction)
            .padding(start = 12.dp, end = 12.dp, bottom = bottomGap)
            .liquidGlass(palette, RoundedCornerShape(30.dp), tintAlpha = 0.84f)
            // touches inside the window must never fall through to the backdrop (which would close it)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
    ) {
        content()
    }
}

/** A glass-rimmed round button; it spins into an X while its window is open. */
@Composable
private fun QuickButton(
    palette: DialerPalette,
    open: Boolean,
    sizeDp: Int,
    onClick: () -> Unit,
    icon: @Composable () -> Unit
) {
    val style = LocalAshuStyle.current
    val spin by animateFloatAsState(
        targetValue = if (open) 90f else 0f,
        animationSpec = tween(style.ms(260)),
        label = "quick-spin"
    )
    Box(
        modifier = Modifier
            .size(sizeDp.dp)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(palette.accent, palette.accent.copy(alpha = 0.82f))))
            .border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape)
            .bounceClick(pressedScale = 0.92f, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Crossfade(targetState = open, animationSpec = tween(style.ms(180)), label = "quick-icon") { isOpen ->
            Box(Modifier.graphicsLayer { rotationZ = if (isOpen) spin - 90f else 0f }) {
                if (isOpen) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(26.dp))
                } else {
                    icon()
                }
            }
        }
    }
}

/** The compact list inside the floating Recents window. */
@Composable
fun FloatingRecentsList(
    palette: DialerPalette,
    recents: List<RecentCall>,
    onCall: (RecentCall) -> Unit,
    onOpenAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    val time = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    Column(modifier.fillMaxSize().padding(top = 16.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Recent calls", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary, modifier = Modifier.weight(1f))
            Text(
                "Open all",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = palette.accent,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(palette.accentSoft)
                    .bounceClick(onClick = onOpenAll)
                    .padding(horizontal = 14.dp, vertical = 7.dp)
            )
        }
        Spacer(Modifier.height(8.dp))
        if (recents.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No recent calls yet", fontSize = 14.sp, color = palette.textSecondary)
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(recents.take(25), key = { it.id }) { call ->
                    val missed = call.direction == CallDirection.MISSED || call.direction == CallDirection.REJECTED
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onCall(call) }
                            .padding(horizontal = 18.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Avatar(name = call.displayName.ifBlank { call.phoneNumber }, photoUri = call.photoUri, size = 42.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                call.displayName.ifBlank { call.phoneNumber },
                                fontSize = 15.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (missed) palette.danger else palette.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    when (call.direction) {
                                        CallDirection.INCOMING -> Icons.Filled.CallReceived
                                        CallDirection.OUTGOING -> Icons.Filled.CallMade
                                        else -> Icons.Filled.CallMissed
                                    },
                                    contentDescription = null,
                                    tint = if (missed) palette.danger else palette.textSecondary,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    time.format(Date(call.timestampMillis)) + if (call.callCount > 1) "  \u2022  ${call.callCount} calls" else "",
                                    fontSize = 12.5.sp,
                                    color = palette.textSecondary,
                                    maxLines = 1
                                )
                            }
                        }
                        Box(
                            Modifier.size(40.dp).clip(CircleShape).background(palette.callGreen.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.Call, contentDescription = "Call", tint = palette.callGreen, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }
    }
}
