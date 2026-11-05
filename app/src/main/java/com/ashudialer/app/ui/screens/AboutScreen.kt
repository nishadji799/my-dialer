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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ashudialer.app.ui.components.glassCard
import com.ashudialer.app.ui.components.liquidGlass
import com.ashudialer.app.ui.components.staggerIn
import com.ashudialer.app.ui.theme.DialerPalette
import com.ashudialer.app.ui.theme.LocalDialerPalette

private data class FeatureHighlight(val icon: ImageVector, val title: String, val description: String)

private val whatsNew = listOf(
    "Floating Dialer button: a round button above the bar opens a glass dialer window (Recents gets the same, from the other tabs)",
    "Conference calls: Merge and Swap buttons, a proper \u201CConference call\u201D title, a Manage sheet with Hold, Private and Remove",
    "Call notifications redesigned: labelled buttons, live timer, clearer missed-call alert, optional system call chip",
    "Voicemail screen: per-SIM number you can edit, plus hold 1 on the dial pad",
    "Google Drive backup: sign in with Google, or pick a folder; Test backup, plain-language guide and clear status",
    "Personalize is now live: every slider changes the screen as you drag; rainbow colour slider; bottom bar order and visibility",
    "Smoother everywhere: lighter glass drawing, cached aurora, faster Recordings list",
    "Recordings of Private Space numbers hide themselves too",
    "Dial pad layouts (Classic, Compact, Glass card) and wide contact suggestions",
    "Troubleshooting only shows what your phone really needs (Xiaomi/HyperOS extras only on Xiaomi)",
    "Fixed: Recents search crash, call timer before answer, stale Incoming-call notification, wrong name in multi-call"
)

private val featureHighlights = listOf(
    FeatureHighlight(
        Icons.Filled.Contacts,
        "Tap any contact photo",
        "Opens their full profile \u2014 call, message, notes, call history, and more \u2014 from anywhere a name or photo appears."
    ),
    FeatureHighlight(
        Icons.Filled.Shield,
        "Spam protection",
        "Suspicious and unknown callers are flagged automatically, with an option to block."
    ),
    FeatureHighlight(
        Icons.Filled.FiberManualRecord,
        "Call recording",
        "Record calls on demand or automatically, with playback, bookmarks and optional cloud backup."
    ),
    FeatureHighlight(
        Icons.Filled.Palette,
        "Themes",
        "Simple White and Black by default, plus optional glass and colour themes."
    )
)

/** One open-source project the app is built on or learned from. */
private data class Credit(
    val name: String,
    val author: String,
    val license: String,
    val usedFor: String,
    val url: String
)

private val credits = listOf(
    Credit(
        "ShizuCallRecorder",
        "kitsumed and contributors",
        "GPL-3.0-or-later",
        "The call-recording engine (Shizuku shell service, recording coordinator, session logic) is adapted from this project. Ashu Phone's recorder is a modified fork derived from it.",
        "https://github.com/kitsumed/ShizuCallRecorder"
    ),
    Credit(
        "Ever Dialer",
        "hari161008 and contributors",
        "GPL-3.0",
        "Ideas only: recorder settings, haptic strength, bottom-bar customisation and glass looks. No Ever Dialer code is included.",
        "https://github.com/hari161008/Ever-Dialer"
    ),
    Credit(
        "CallVault and JemRec",
        "madkongo; Yevhen Yemchynskyi",
        "GPL-3.0 / Apache-2.0",
        "Ideas only: marks and notes on recordings, discarding very short recordings, and backing up through the system file picker. No code from either project is included in Ashu Phone.",
        "https://github.com/madkongo/CallVault"
    ),
    Credit(
        "scrcpy",
        "Genymobile",
        "Apache-2.0",
        "The bundled scrcpy-server (unmodified) used by the app-call recorder.",
        "https://github.com/Genymobile/scrcpy"
    ),
    Credit(
        "Shizuku and Shizuku-API",
        "RikkaApps",
        "Apache-2.0",
        "The privileged helper that lets recording work without root.",
        "https://github.com/RikkaApps/Shizuku"
    )
)

@Composable
fun AboutScreen(
    onBack: () -> Unit,
    versionName: String,
    modifier: Modifier = Modifier
) {
    val palette = LocalDialerPalette.current
    val uriHandler = LocalUriHandler.current

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = palette.textPrimary)
            }
            Spacer(Modifier.width(4.dp))
            Text("About", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary)
        }

        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(palette.accentSoft),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Phone, contentDescription = null, tint = palette.accent, modifier = Modifier.size(34.dp))
                }
                Spacer(Modifier.height(14.dp))
                Text("Ashu Phone", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = palette.textPrimary)
                Spacer(Modifier.height(4.dp))
                Text("Version $versionName", fontSize = 13.sp, color = palette.textSecondary)
                Spacer(Modifier.height(6.dp))
                Text("Created by Ashutosh Nishad", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = palette.accent)
                Spacer(Modifier.height(10.dp))
                LinkPill("Source code (GPL-3.0-or-later)", palette) {
                    uriHandler.openUri("https://github.com/ashutoshnishad799-svg/AshuDialer-v2")
                }
            }

            AboutSectionLabel("WHAT'S NEW", palette)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .staggerIn(0)
                    .liquidGlass(palette, RoundedCornerShape(20.dp))
                    .padding(vertical = 8.dp)
            ) {
                whatsNew.forEach { line ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 7.dp), verticalAlignment = Alignment.Top) {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = palette.accent, modifier = Modifier.size(16.dp).padding(top = 2.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(line, fontSize = 13.5.sp, color = palette.textPrimary, lineHeight = 19.sp)
                    }
                }
            }

            AboutSectionLabel("WHAT'S INSIDE", palette)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .glassCard(palette, 18.dp)
            ) {
                featureHighlights.forEachIndexed { index, feature ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(feature.icon, contentDescription = null, tint = palette.accent, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text(feature.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = palette.textPrimary)
                            Spacer(Modifier.height(2.dp))
                            Text(feature.description, fontSize = 13.sp, color = palette.textSecondary, lineHeight = 18.sp)
                        }
                    }
                    if (index != featureHighlights.lastIndex) {
                        HorizontalDivider(color = palette.cardBorder, thickness = 1.dp)
                    }
                }
            }

            AboutSectionLabel("OPEN-SOURCE CREDITS", palette)
            Text(
                "Ashu Phone is free software under the GNU GPL v3 or later. It stands on the work of these projects \u2014 thank you.",
                fontSize = 12.5.sp,
                color = palette.textSecondary,
                modifier = Modifier.padding(horizontal = 22.dp).padding(bottom = 8.dp)
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .glassCard(palette, 18.dp)
            ) {
                credits.forEachIndexed { index, c ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { uriHandler.openUri(c.url) }
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(c.name, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary, modifier = Modifier.weight(1f))
                            Icon(Icons.Filled.OpenInNew, contentDescription = "Open", tint = palette.textSecondary, modifier = Modifier.size(16.dp))
                        }
                        Text("${c.author} \u2022 ${c.license}", fontSize = 12.sp, color = palette.accent)
                        Spacer(Modifier.height(3.dp))
                        Text(c.usedFor, fontSize = 12.5.sp, color = palette.textSecondary, lineHeight = 17.sp)
                    }
                    if (index != credits.lastIndex) HorizontalDivider(color = palette.cardBorder, thickness = 1.dp)
                }
            }

            Text(
                "Ashu Phone is a modified edition. The original projects above are not affiliated with it, and the changes made here are described in CHANGELOG.md.",
                fontSize = 11.5.sp,
                color = palette.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 20.dp)
            )
        }
    }
}

@Composable
private fun AboutSectionLabel(text: String, palette: DialerPalette) {
    Text(
        text,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        color = palette.textSecondary,
        letterSpacing = 0.8.sp,
        modifier = Modifier.fillMaxWidth().padding(start = 22.dp, end = 16.dp, top = 18.dp, bottom = 8.dp)
    )
}

@Composable
private fun LinkPill(label: String, palette: DialerPalette, onClick: () -> Unit) {
    Text(
        label,
        fontSize = 12.5.sp,
        fontWeight = FontWeight.SemiBold,
        color = palette.accent,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(palette.accentSoft)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp)
    )
}
