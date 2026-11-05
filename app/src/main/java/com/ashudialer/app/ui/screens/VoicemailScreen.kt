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

import android.content.Intent
import android.telephony.TelephonyManager
import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material.icons.filled.Voicemail
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ashudialer.app.telecom.VoicemailHelper
import com.ashudialer.app.telecom.VoicemailSim
import com.ashudialer.app.ui.components.bounceClick
import com.ashudialer.app.ui.components.liquidGlass
import com.ashudialer.app.ui.theme.DialerPalette
import com.ashudialer.app.ui.theme.LocalDialerPalette

/**
 * Voicemail, done like other dialers: pick the SIM, see the number it will call, type your own if your
 * carrier has not set one (or set a wrong one), and call. Long-press 1 on the dial pad goes straight here.
 */
@Composable
fun VoicemailScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val palette = LocalDialerPalette.current
    val context = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    val sims = remember(tick) { VoicemailHelper.sims(context) }

    Column(modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 6.dp, end = 16.dp, top = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = palette.textPrimary)
            }
            Column(Modifier.weight(1f)) {
                Text("Voicemail", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = palette.textPrimary)
                Text("Check messages left for you by callers", fontSize = 12.5.sp, color = palette.textSecondary)
            }
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            sims.forEach { sim ->
                SimVoicemailCard(sim, palette) { tick++ }
            }

            Column(
                Modifier.fillMaxWidth().liquidGlass(palette, RoundedCornerShape(22.dp)).padding(16.dp)
            ) {
                Text("Not working?", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary)
                Spacer(Modifier.height(6.dp))
                listOf(
                    "Voicemail has to be switched on with your carrier first. Ask them, or look in their app.",
                    "If the number above is empty or wrong, type the voicemail number your carrier gave you and tap Save.",
                    "Some carriers only play voicemail when your phone cannot be reached or you decline a call."
                ).forEach { line ->
                    Text("\u2022  $line", fontSize = 13.sp, color = palette.textSecondary, modifier = Modifier.padding(vertical = 2.dp))
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "Open phone voicemail settings",
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = palette.accent,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(palette.accentSoft)
                        .bounceClick {
                            try {
                                context.startActivity(Intent(TelephonyManager.ACTION_CONFIGURE_VOICEMAIL).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            } catch (_: Exception) {
                                Toast.makeText(context, "This phone has no voicemail settings screen", Toast.LENGTH_SHORT).show()
                            }
                        }
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SimVoicemailCard(sim: VoicemailSim, palette: DialerPalette, onChanged: () -> Unit) {
    val context = LocalContext.current
    var typed by remember(sim.key, sim.custom) { mutableStateOf(sim.custom.orEmpty()) }
    Column(Modifier.fillMaxWidth().liquidGlass(palette, RoundedCornerShape(22.dp)).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(palette.accentSoft),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Filled.SimCard, contentDescription = null, tint = palette.accent, modifier = Modifier.size(22.dp)) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(sim.label, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary)
                Text(
                    when {
                        sim.custom != null -> "Using the number you entered"
                        sim.detected != null -> "Number from your carrier"
                        else -> "Your carrier has not set a voicemail number"
                    },
                    fontSize = 12.sp,
                    color = palette.textSecondary
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(sim.effective ?: "\u2014", fontSize = 24.sp, fontWeight = FontWeight.Medium, color = palette.textPrimary, letterSpacing = 1.sp)
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(palette.searchBackground)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.weight(1f)) {
                if (typed.isEmpty()) Text("Use a different number", fontSize = 15.sp, color = palette.textSecondary)
                BasicTextField(
                    value = typed,
                    onValueChange = { typed = it.filter { c -> c.isDigit() || c == '+' || c == '*' || c == '#' }.take(20) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    textStyle = TextStyle(color = palette.textPrimary, fontSize = 15.sp),
                    cursorBrush = SolidColor(palette.accent),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (typed != sim.custom.orEmpty()) {
                Text(
                    "Save",
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = palette.accent,
                    modifier = Modifier
                        .bounceClick {
                            VoicemailHelper.saveCustom(context, sim, typed)
                            onChanged()
                        }
                        .padding(start = 12.dp)
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(50))
                .background(palette.callGreen)
                .bounceClick {
                    if (!VoicemailHelper.call(context, sim)) {
                        Toast.makeText(
                            context,
                            "No voicemail number yet. Type the number your carrier gave you, then tap Save.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                },
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Voicemail, contentDescription = null, tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(8.dp))
            Text("Call voicemail", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = androidx.compose.ui.graphics.Color.White)
        }
    }
}
