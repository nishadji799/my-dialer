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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.SettingsBrightness
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ashudialer.app.ui.theme.AUTO_THEME_ID
import com.ashudialer.app.ui.theme.AllPalettes
import com.ashudialer.app.ui.theme.DefaultPalettes
import com.ashudialer.app.ui.theme.DialerPalette
import com.ashudialer.app.ui.theme.LocalDialerPalette


@Composable
fun ThemePickerButton(
    onClick: () -> Unit,
    currentThemeId: String = "",
    modifier: Modifier = Modifier
) {
    val palette = LocalDialerPalette.current
    val label = if (currentThemeId == AUTO_THEME_ID) "System" else palette.displayName

    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, palette.cardBorder),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = palette.cardBackground),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.Palette,
            contentDescription = "Change theme",
            tint = palette.accent,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(text = label, color = palette.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

/**
 * v5: a classic picker instead of the old 3-column row of plain circles.
 *
 * Each look now shows a small real preview (its own background, a card and the accent) so the
 * choice is obvious before tapping, the selected tile gently springs up and gets an accent ring,
 * and the list is split into "Simple" (White / Black) and "More looks" - which is also the order
 * a normal person should meet them in. No cartoon faces, no oversized emoji, just type and colour.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemePickerSheet(
    currentThemeId: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val palette = LocalDialerPalette.current
    val simple = DefaultPalettes
    val more = AllPalettes.filter { it.id !in simple.map { p -> p.id } }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = palette.cardBackground
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(palette.accentSoft),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Palette, contentDescription = null, tint = palette.accent, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        "Themes",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = palette.textPrimary
                    )
                    Text(
                        "Pick a look. Everything updates instantly.",
                        fontSize = 12.5.sp,
                        color = palette.textSecondary
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.heightIn(max = 470.dp)
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    SystemThemeCard(
                        selected = currentThemeId == AUTO_THEME_ID,
                        palette = palette,
                        onClick = { onSelect(AUTO_THEME_ID) }
                    )
                }
                item(span = { GridItemSpan(maxLineSpan) }) { SectionLabel("SIMPLE", palette) }
                items(simple) { p ->
                    ThemeTile(p = p, selected = p.id == currentThemeId, current = palette) { onSelect(p.id) }
                }
                item(span = { GridItemSpan(maxLineSpan) }) { SectionLabel("MORE LOOKS", palette) }
                items(more) { p ->
                    ThemeTile(p = p, selected = p.id == currentThemeId, current = palette) { onSelect(p.id) }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String, palette: DialerPalette) {
    Text(
        text = text,
        fontSize = 10.5.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.4.sp,
        color = palette.textSecondary,
        modifier = Modifier.padding(top = 4.dp)
    )
}

@Composable
private fun SystemThemeCard(
    selected: Boolean,
    palette: DialerPalette,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (selected) 1f else 0.985f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 520f),
        label = "system-theme-scale"
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(RoundedCornerShape(16.dp))
            .background(palette.accentSoft.copy(alpha = if (selected) 1f else 0.55f))
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) palette.accent else palette.cardBorder,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(
                    Brush.linearGradient(
                        listOf(palette.textSecondary.copy(alpha = 0.28f), palette.textPrimary.copy(alpha = 0.55f))
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Row {
                Icon(Icons.Filled.LightMode, contentDescription = null, tint = Color.White.copy(alpha = 0.95f), modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(3.dp))
                Icon(Icons.Filled.DarkMode, contentDescription = null, tint = Color.White.copy(alpha = 0.75f), modifier = Modifier.size(17.dp))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("Follow system", fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, color = palette.textPrimary)
            Text("White in light mode, Black in dark mode", fontSize = 11.5.sp, color = palette.textSecondary)
        }
        if (selected) {
            Icon(Icons.Filled.Check, contentDescription = "Selected", tint = palette.accent, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun ThemeTile(
    p: DialerPalette,
    selected: Boolean,
    current: DialerPalette,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (selected) 1f else 0.97f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 480f),
        label = "theme-tile-scale"
    )
    Column(
        modifier = Modifier
            .scale(scale)
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(92.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(p.background)
                .border(
                    width = if (selected) 2.5.dp else 1.dp,
                    color = if (selected) p.accent else current.cardBorder,
                    shape = RoundedCornerShape(18.dp)
                )
        ) {
            // A tiny "screen": one card + accent dots, drawn with the theme's own colours.
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(0.78f)
                        .height(11.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(p.cardBackground.copy(alpha = 0.95f))
                )
                Box(
                    Modifier
                        .fillMaxWidth(0.52f)
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(p.textSecondary.copy(alpha = 0.45f))
                )
                Spacer(Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    repeat(3) {
                        Box(Modifier.size(13.dp).clip(CircleShape).background(p.accent.copy(alpha = 0.85f)))
                    }
                }
            }
            if (selected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(p.accent),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Check, contentDescription = "Selected", tint = Color.White, modifier = Modifier.size(14.dp))
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = p.displayName,
            fontSize = 12.5.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = current.textPrimary,
            maxLines = 1
        )
    }
}
