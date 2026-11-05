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

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NoteAlt
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhonelinkSetup
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.Voicemail
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ashudialer.app.ui.components.bounceClick
import com.ashudialer.app.ui.components.liquidGlass
import com.ashudialer.app.ui.components.rememberButtonHaptic
import com.ashudialer.app.ui.components.staggerIn
import com.ashudialer.app.ui.theme.DialerPalette
import com.ashudialer.app.ui.theme.LocalAshuStyle
import com.ashudialer.app.ui.theme.LocalDialerPalette

/**
 * v4 "More" - redesigned. The old flat 18-row list is now:
 *  - a search box that finds any tool or setting by name,
 *  - four big featured tiles (Recordings, Private Space, Notes, Call Insights),
 *  - grouped sections (Look & feel, Calls & safety, Data & backup, Ashu Phone) with a one-line
 *    explanation under every item so nothing needs guessing.
 *
 * The labels passed to [onItemClick] are exactly the same strings as before (plus the new
 * "Personalize"), so MainActivity's existing routing keeps working.
 */
private data class MoreItem(val label: String, val hint: String, val icon: ImageVector)

private data class MoreSection(val title: String, val items: List<MoreItem>)

@Composable
fun MoreScreen(
    onItemClick: (String) -> Unit,
    showSimRouting: Boolean = false,
    vibrateOnButtonPress: Boolean = true,
    modifier: Modifier = Modifier
) {
    val palette = LocalDialerPalette.current
    val vibrate = rememberButtonHaptic(vibrateOnButtonPress)
    val recordingEnabled = com.ashudialer.app.BuildConfig.CALL_RECORDING_ENABLED
    var query by remember { mutableStateOf("") }

    val featured = remember(recordingEnabled) {
        buildList {
            if (recordingEnabled) add(MoreItem("Recordings", "Listen, tag & back up", Icons.Filled.FiberManualRecord))
            add(MoreItem("Private Space", "Hidden numbers & calls", Icons.Filled.Lock))
            add(MoreItem("Notes", "Notes about your calls", Icons.Filled.NoteAlt))
            add(MoreItem("Call Insights", "Your calling habits", Icons.Filled.BarChart))
            if (!recordingEnabled) add(MoreItem("Personalize", "Themes, glass & motion", Icons.Filled.Tune))
        }
    }
    val sections = remember(showSimRouting) {
        buildList {
            add(
                MoreSection(
                    "Look & feel",
                    listOf(
                        MoreItem("Personalize", "Themes, glass, accent colour, motion", Icons.Filled.Tune),
                        MoreItem("Appearance", "Quickly switch theme", Icons.Filled.Palette),
                        MoreItem("Vibration Patterns", "Ring and call vibration", Icons.Filled.Vibration)
                    )
                )
            )
            add(
                MoreSection(
                    "Calls & safety",
                    buildList {
                        add(MoreItem("Quiet Hours", "Silence calls on a schedule", Icons.Filled.Bedtime))
                        add(MoreItem("Blocked numbers", "Numbers that can't reach you", Icons.Filled.Block))
                        add(MoreItem("Reported Numbers", "Spam you reported", Icons.Filled.WarningAmber))
                        add(MoreItem("Voicemail", "Open your voicemail", Icons.Filled.Voicemail))
                        if (showSimRouting) add(MoreItem("SIM Routing", "Pick a SIM per contact", Icons.Filled.SimCard))
                    }
                )
            )
            add(
                MoreSection(
                    "Data & backup",
                    listOf(
                        MoreItem("Account", "Sign in to sync", Icons.Filled.AccountCircle),
                        MoreItem("Backup", "Cloud backup of your data", Icons.Filled.CloudUpload),
                        MoreItem("Local Backup", "Save a backup file on this phone", Icons.Filled.SdStorage)
                    )
                )
            )
            add(
                MoreSection(
                    "Ashu Phone",
                    listOf(
                        MoreItem("Settings", "Everything else", Icons.Filled.Settings),
                        MoreItem("Set as default dialer", "Needed for calls & recording", Icons.Filled.PhonelinkSetup),
                        MoreItem("Check for updates", "See what's new", Icons.Filled.SystemUpdate),
                        MoreItem("Help & feedback", "Guides and contact", Icons.Filled.Help),
                        MoreItem("Privacy Policy", "How your data is handled", Icons.Filled.PrivacyTip),
                        MoreItem("About", "Version, credits & open-source licences", Icons.Filled.Info)
                    )
                )
            )
        }
    }
    val everything = remember(featured, sections) {
        (featured + sections.flatMap { it.items }).distinctBy { it.label }
    }
    val q = query.trim().lowercase()
    val results = remember(q, everything) {
        if (q.isEmpty()) emptyList() else everything.filter { it.label.lowercase().contains(q) || it.hint.lowercase().contains(q) }
    }

    fun open(label: String) {
        vibrate()
        onItemClick(label)
    }

    Column(modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text(
            "More",
            fontSize = 30.sp,
            fontWeight = FontWeight.ExtraBold,
            color = palette.textPrimary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        // search
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .liquidGlass(palette, RoundedCornerShape(18.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Search, contentDescription = null, tint = palette.textSecondary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) Text("Search tools and settings", fontSize = 15.sp, color = palette.textSecondary)
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = TextStyle(color = palette.textPrimary, fontSize = 15.sp),
                    cursorBrush = SolidColor(palette.accent),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (query.isNotEmpty()) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Clear",
                    tint = palette.textSecondary,
                    modifier = Modifier.size(18.dp).clickable { query = "" }
                )
            }
        }
        Spacer(Modifier.height(14.dp))

        if (q.isNotEmpty()) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth().liquidGlass(palette, RoundedCornerShape(20.dp))
            ) {
                if (results.isEmpty()) {
                    Text("Nothing matches \u201C$query\u201D", fontSize = 14.sp, color = palette.textSecondary, modifier = Modifier.padding(18.dp))
                }
                results.forEach { item -> MoreRow(item, palette) { open(item.label) } }
            }
        } else {
            // featured tiles, two per row
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                featured.chunked(2).forEachIndexed { rowIndex, pair ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        pair.forEachIndexed { i, item ->
                            FeaturedTile(item, palette, rowIndex * 2 + i, Modifier.weight(1f)) { open(item.label) }
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }

            sections.forEachIndexed { sIndex, section ->
                Text(
                    section.title.uppercase(),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                    color = palette.textSecondary,
                    modifier = Modifier.padding(start = 22.dp, end = 16.dp, top = 20.dp, bottom = 8.dp)
                )
                Column(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth()
                        .staggerIn(sIndex + 2)
                        .liquidGlass(palette, RoundedCornerShape(20.dp))
                ) {
                    section.items.forEachIndexed { i, item ->
                        MoreRow(item, palette) { open(item.label) }
                    }
                }
            }

            Text(
                "Ashu Phone \u2022 made with care by Ashutosh Nishad",
                fontSize = 11.5.sp,
                color = palette.textSecondary,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 22.dp)
            )
        }
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun FeaturedTile(item: MoreItem, palette: DialerPalette, index: Int, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier = modifier
            .staggerIn(index)
            .liquidGlass(palette, RoundedCornerShape(22.dp))
            .bounceClick(pressedScale = 0.96f, onClick = onClick)
            .padding(16.dp)
    ) {
        Box(
            modifier = Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(palette.accentSoft),
            contentAlignment = Alignment.Center
        ) {
            Icon(item.icon, contentDescription = null, tint = palette.accent, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.height(14.dp))
        Text(item.label, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary, maxLines = 1)
        Text(item.hint, fontSize = 12.sp, color = palette.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun MoreRow(item: MoreItem, palette: DialerPalette, onClick: () -> Unit) {
    val style = LocalAshuStyle.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = style.rowPadding + 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(36.dp).clip(RoundedCornerShape(11.dp)).background(palette.accentSoft),
            contentAlignment = Alignment.Center
        ) {
            Icon(item.icon, contentDescription = null, tint = palette.accent, modifier = Modifier.size(19.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(item.label, fontSize = 15.5.sp, fontWeight = FontWeight.SemiBold, color = palette.textPrimary)
            Text(item.hint, fontSize = 12.sp, color = palette.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = palette.textSecondary, modifier = Modifier.size(18.dp))
    }
}
