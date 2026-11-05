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

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ashudialer.app.R
import com.ashudialer.app.ui.theme.DialerPalette

/**
 * v5: the Home-screen icon is now a real choice.
 *
 * Android only shows one launcher entry at a time, so each style lives in its own
 * <activity-alias> (see AndroidManifest.xml). Selecting one enables that alias and disables the
 * rest. No app restart and no data is touched.
 */
object AppIconManager {

    data class Choice(
        val id: String,
        val label: String,
        val alias: String,
        val background: Color,
        /** Vector foreground for the built-in adaptive icons, or 0 for the original PNG icon. */
        val foregroundRes: Int
    )

    private const val PREFS = "ashu_app_icon"
    private const val KEY = "choice"

    val choices: List<Choice> = listOf(
        Choice("default", "Default", ".LauncherPhone", Color(0xFF1D4ED8), 0),
        Choice("classic", "Classic", ".LauncherIconClassic", Color(0xFF0F172A), R.drawable.ic_launcher_fg_classic),
        Choice("midnight", "Midnight", ".LauncherIconMidnight", Color(0xFF0B0F14), R.drawable.ic_launcher_fg_midnight),
        Choice("glass", "White Glass", ".LauncherIconGlass", Color(0xFFEEF3FF), R.drawable.ic_launcher_fg_glass)
    )

    fun current(context: Context): String {
        val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
        return choices.firstOrNull { it.id == saved }?.id ?: "default"
    }

    fun apply(context: Context, id: String) {
        val choice = choices.firstOrNull { it.id == id } ?: return
        val pkg = context.packageName
        val pm = context.packageManager
        runCatching {
            // Enable the chosen alias FIRST so the app never has zero launcher entries.
            pm.setComponentEnabledSetting(
                ComponentName(pkg, pkg + choice.alias),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )
            choices.filter { it.id != id }.forEach {
                pm.setComponentEnabledSetting(
                    ComponentName(pkg, pkg + it.alias),
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP
                )
            }
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY, id).apply()
            Toast.makeText(context, "Icon changed. The Home screen may take a moment to refresh.", Toast.LENGTH_SHORT).show()
        }.onFailure {
            Toast.makeText(context, "This launcher does not allow changing the icon.", Toast.LENGTH_SHORT).show()
        }
    }
}

@Composable
fun AppIconCard(
    palette: DialerPalette,
    selectedId: String,
    onSelect: (String) -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        Text("App icon", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = palette.textPrimary)
        Text(
            "The Home-screen icon. The app itself does not change.",
            fontSize = 12.sp,
            color = palette.textSecondary
        )
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            AppIconManager.choices.forEach { choice ->
                val selected = choice.id == selectedId
                val scale by animateFloatAsState(
                    targetValue = if (selected) 1f else 0.94f,
                    animationSpec = spring(dampingRatio = 0.7f, stiffness = 480f),
                    label = "app-icon-scale"
                )
                val ring by animateColorAsState(
                    targetValue = if (selected) palette.accent else Color.Transparent,
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                    label = "app-icon-ring"
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .scale(scale)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onSelect(choice.id) }
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(58.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(choice.background)
                            .border(2.dp, ring, RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (choice.foregroundRes == 0) {
                            Image(
                                painter = painterResource(R.mipmap.ic_launcher_png),
                                contentDescription = choice.label,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(58.dp).clip(RoundedCornerShape(16.dp))
                            )
                        } else {
                            Image(
                                painter = painterResource(choice.foregroundRes),
                                contentDescription = choice.label,
                                modifier = Modifier.size(58.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(5.dp))
                    Text(
                        choice.label,
                        fontSize = 10.5.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        color = if (selected) palette.accent else palette.textSecondary,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
