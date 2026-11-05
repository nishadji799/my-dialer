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

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ashudialer.app.appcalls.cloud.CloudPrefs
import com.ashudialer.app.appcalls.cloud.CloudSync
import com.ashudialer.app.appcalls.cloud.FolderKind
import com.ashudialer.app.appcalls.recording.RecMeta
import com.ashudialer.app.appcalls.recording.RecordingMarks
import com.ashudialer.app.appcalls.recording.RecordingMetaStore
import com.ashudialer.app.appcalls.recording.RecordingPrefs
import com.ashudialer.app.telecom.CallRecorder
import com.ashudialer.app.ui.components.bounceClick
import com.ashudialer.app.ui.components.liquidGlass
import com.ashudialer.app.ui.theme.DialerPalette
import com.google.android.gms.auth.api.identity.Identity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

/**
 * Cloud backup card for the recording settings - fully optional, OFF by default.
 *
 * Two ways, chosen right on the card:
 *  - "Google account": sign in once, recordings go straight to a "Ashu Phone Recordings" folder in your
 *    Google Drive, named with the contact and date. The easy way.
 *  - "Folder / File manager": pick a folder in Android's file picker. A folder inside the Drive app is
 *    uploaded by Drive; a folder on the phone is a plain copy you can see in any file manager. The card
 *    says clearly which of the two the chosen folder is - choosing a phone folder and expecting Drive was
 *    the most common mix-up.
 *
 * "Keep recordings only in the cloud" is the optional mode where nothing stays on the phone: a new
 * recording waits in the app's private storage (never the public Music folder) just until its upload
 * is complete, then it is removed from the phone. If an upload can't finish, the recording is kept.
 */
@Composable
fun CloudBackupCard(palette: DialerPalette, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { CloudPrefs(context) }
    val meta = remember { RecordingMetaStore(context) }
    var tick by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var showGuide by remember { mutableStateOf(false) }

    // These are re-read whenever `tick` changes (after any switch, picker result or backup action).
    val enabled = remember(tick) { prefs.enabled }
    val mode = remember(tick) { prefs.mode }
    val folderPicked = remember(tick) { !prefs.folderUri.isNullOrBlank() }
    val kind = remember(tick) { CloudSync.folderKind(context) }
    val driveConnected = remember(tick) { prefs.driveConnected }
    val ready = if (mode == CloudPrefs.MODE_DRIVE) driveConnected else folderPicked
    val pending = remember(tick) { meta.all().values.count { it.cloud == RecMeta.CLOUD_QUEUED } }
    val waiting = remember(tick, pending) { if (pending > 0) CloudSync.waitingReason(context) else null }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            if (CloudSync.savePickedFolder(context, uri)) {
                prefs.enabled = true
                Toast.makeText(context, "Backup folder set", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Couldn't keep access to that folder. Try another one.", Toast.LENGTH_LONG).show()
            }
            tick++
        }
    }
    val consent = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { res ->
        try {
            val r = Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(res.data)
            if (!r.accessToken.isNullOrBlank()) {
                prefs.driveConnected = true
                prefs.mode = CloudPrefs.MODE_DRIVE
                prefs.enabled = true
                CloudSync.ensureSweepScheduled(context)
                CloudSync.refreshQueue(context)
                Toast.makeText(context, "Google Drive connected", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Couldn't connect Google Drive: ${e.message}", Toast.LENGTH_LONG).show()
        }
        tick++
    }
    fun connectDrive() {
        Identity.getAuthorizationClient(context).authorize(CloudSync.driveAuthorizationRequest())
            .addOnSuccessListener { r ->
                val pi = r.pendingIntent
                if (r.hasResolution() && pi != null) {
                    consent.launch(IntentSenderRequest.Builder(pi.intentSender).build())
                } else {
                    prefs.driveConnected = true
                    prefs.mode = CloudPrefs.MODE_DRIVE
                    prefs.enabled = true
                    tick++
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(context, "Couldn't connect Google Drive: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .liquidGlass(palette, RoundedCornerShape(22.dp))
            .animateContentSize()
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(44.dp).clip(CircleShape).background(palette.accentSoft),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (enabled && ready) Icons.Filled.CloudDone else Icons.Filled.Cloud,
                    contentDescription = null,
                    tint = palette.accent,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Back up recordings", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary)
                Text(
                    "Optional. Google Drive or a folder of your choice.",
                    fontSize = 12.sp,
                    color = palette.textSecondary
                )
            }
            androidx.compose.material3.Switch(
                checked = enabled,
                onCheckedChange = { on ->
                    prefs.enabled = on
                    if (on) {
                        CloudSync.ensureSweepScheduled(context)
                        CloudSync.refreshQueue(context)
                    } else {
                        CloudSync.cancelAll(context)
                    }
                    tick++
                },
                colors = androidx.compose.material3.SwitchDefaults.colors(
                    checkedThumbColor = palette.solidBackground,
                    checkedTrackColor = palette.accent,
                    uncheckedTrackColor = palette.accentSoft,
                    uncheckedThumbColor = palette.textSecondary,
                    uncheckedBorderColor = palette.cardBorder
                )
            )
        }

        AnimatedVisibility(visible = enabled) {
            Column {
                Spacer(Modifier.height(14.dp))
                Text("How should recordings be saved?", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = palette.textPrimary)
                Spacer(Modifier.height(6.dp))
                Segmented(
                    listOf(CloudPrefs.MODE_DRIVE to "Google account", CloudPrefs.MODE_FOLDER to "Folder / File manager"),
                    mode, palette
                ) { prefs.mode = it; tick++ }
                Spacer(Modifier.height(12.dp))

                if (mode == CloudPrefs.MODE_DRIVE) {
                    // ----------------------------------------------------------- Google account
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(palette.accentSoft)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (driveConnected) Icons.Filled.CloudDone else Icons.Filled.Cloud,
                            contentDescription = null,
                            tint = if (driveConnected) palette.callGreen else palette.accent,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (driveConnected) "Google Drive connected" else "Not connected",
                                fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = palette.textPrimary
                            )
                            Text(
                                "Saved in a folder called \u201CAshu Phone Recordings\u201D, named with the contact and date.",
                                fontSize = 11.5.sp, color = palette.textSecondary
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    if (!driveConnected) {
                        PrimaryPill("Sign in with Google", palette) { connectDrive() }
                    } else {
                        Text(
                            "Disconnect",
                            fontSize = 13.sp, fontWeight = FontWeight.Bold, color = palette.danger,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .clickable {
                                    prefs.driveConnected = false
                                    prefs.driveFolderId = null
                                    CloudSync.cancelAll(context)
                                    tick++
                                }
                                .padding(horizontal = 6.dp, vertical = 6.dp)
                        )
                    }
                } else {
                    // ----------------------------------------------------------- folder
                    if (!folderPicked) {
                        PrimaryPill("Choose a folder", palette) { picker.launch(null) }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "For Google Drive: open the \u2630 menu \u2192 Drive \u2192 open a folder \u2192 \u201CUse this folder\u201D.\n" +
                                "For your phone: pick Internal storage, open (or create) any folder such as \u201CAshuBackup\u201D, then tap \u201CUse this folder\u201D at the bottom. Android does not allow choosing the very top \u201CInternal storage\u201D itself \u2014 that is why the button looks missing there.",
                            fontSize = 12.sp,
                            color = palette.textSecondary
                        )
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(palette.accentSoft)
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.FolderOpen, contentDescription = null, tint = palette.accent, modifier = Modifier.size(22.dp))
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    prefs.folderName.ifBlank { "Selected folder" },
                                    fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = palette.textPrimary, maxLines = 1
                                )
                                val usable = CloudSync.folderIsUsableCached(context)
                                Text(
                                    when {
                                        !usable -> "Can't reach this folder \u2014 choose it again"
                                        kind == FolderKind.GOOGLE_DRIVE -> "\u2713 Google Drive folder"
                                        kind == FolderKind.PHONE -> "\u26A0 This folder is on your PHONE, not in Google Drive"
                                        else -> "\u2713 Cloud app folder"
                                    },
                                    fontSize = 11.5.sp,
                                    color = when {
                                        !usable -> palette.danger
                                        kind == FolderKind.PHONE -> androidx.compose.ui.graphics.Color(0xFFE08A00)
                                        else -> palette.callGreen
                                    }
                                )
                            }
                            Text(
                                "Change",
                                fontSize = 13.sp, fontWeight = FontWeight.Bold, color = palette.accent,
                                modifier = Modifier.bounceClick { picker.launch(null) }.padding(6.dp)
                            )
                        }
                        if (kind == FolderKind.PHONE) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Files go to this folder in your File manager. They will NOT appear in Google Drive. To get them in Drive, choose a folder inside Drive, or switch to \u201CGoogle account\u201D above.",
                                fontSize = 12.sp,
                                color = androidx.compose.ui.graphics.Color(0xFFE08A00)
                            )
                        } else {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Recordings are saved inside a sub-folder called \u201C${com.ashudialer.app.appcalls.cloud.DriveRestClient.FOLDER_NAME}\u201D, so they never mix with your other files.",
                                fontSize = 12.sp,
                                color = palette.textSecondary
                            )
                        }
                    }
                }

                if (ready) {
                    Spacer(Modifier.height(6.dp))
                    PrefSwitchRow("Back up new recordings automatically", "Right after each call recording is saved", prefs.autoBackup, palette) {
                        prefs.autoBackup = it; tick++
                        if (it) CloudSync.refreshQueue(context)
                    }
                    PrefSwitchRow("Wi\u2011Fi only", "Don't use mobile data for uploads (not needed for phone folders)", prefs.wifiOnly, palette) {
                        prefs.wifiOnly = it; tick++
                        // v5: re-queue with the new network rule immediately, so turning this OFF
                        // really starts uploading over mobile data instead of waiting for the old
                        // Wi-Fi-only work item to time out.
                        CloudSync.refreshQueue(context)
                    }
                    PrefSwitchRow("Only while charging", "Upload when the phone is plugged in", prefs.chargingOnly, palette) {
                        prefs.chargingOnly = it; tick++
                        CloudSync.refreshQueue(context)
                    }
                    PrefSwitchRow(
                        "Keep recordings only in the cloud",
                        "After a successful upload the copy on this phone is deleted. Until then it waits in private app storage, never in Music.",
                        prefs.cloudOnly,
                        palette
                    ) { prefs.cloudOnly = it; tick++ }

                    Spacer(Modifier.height(8.dp))
                    val status = buildString {
                        if (pending > 0) append("$pending waiting to upload. ")
                        if (waiting != null) append("$waiting. ")
                        if (prefs.lastSuccessAt > 0L) {
                            append("Last backup: ")
                            append(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(prefs.lastSuccessAt)))
                            append(". ")
                        }
                        if (prefs.lastError.isNotBlank()) append("Last problem: ${prefs.lastError}.")
                    }.trim()
                    if (status.isNotEmpty()) {
                        Text(status, fontSize = 12.sp, color = palette.textSecondary)
                        Spacer(Modifier.height(8.dp))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinePill("Back up all now", palette, Modifier.weight(1f)) {
                            val files = CallRecorder.listRecordings(context)
                            val queued = CloudSync.backUpAll(context, files) { f ->
                                if (f.absolutePath.startsWith(context.filesDir.absolutePath)) null
                                else resolveRecordingUri(context, f)
                            }
                            Toast.makeText(
                                context,
                                if (queued == 0) "Everything is already backed up" else "Queued $queued recordings",
                                Toast.LENGTH_SHORT
                            ).show()
                            tick++
                        }
                        OutlinePill(if (busy) "Testing\u2026" else "Test backup", palette, Modifier.weight(1f)) {
                            if (!busy) {
                                busy = true
                                testResult = null
                                scope.launch {
                                    val message = withContext(Dispatchers.IO) { CloudSync.testBackup(context) }
                                    testResult = message
                                    busy = false
                                    tick++
                                }
                            }
                        }
                        OutlinePill("Open", palette, Modifier.weight(1f)) {
                            val intent = CloudSync.folderViewIntent(context)
                            try {
                                if (intent != null) context.startActivity(intent)
                                else Toast.makeText(context, "No folder chosen yet", Toast.LENGTH_SHORT).show()
                            } catch (_: Exception) {
                                Toast.makeText(context, "Open your file manager or the Google Drive app to see the folder", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    testResult?.let { message ->
                        Spacer(Modifier.height(8.dp))
                        Text(
                            message,
                            fontSize = 12.5.sp,
                            color = palette.textPrimary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(palette.accentSoft)
                                .padding(12.dp)
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))
                Text(
                    if (showGuide) "Hide the guide" else "How does backup work?",
                    fontSize = 13.sp, fontWeight = FontWeight.Bold, color = palette.accent,
                    modifier = Modifier.clip(RoundedCornerShape(50)).clickable { showGuide = !showGuide }.padding(vertical = 6.dp)
                )
                AnimatedVisibility(visible = showGuide) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(palette.accentSoft)
                            .padding(12.dp)
                    ) {
                        listOf(
                            "1. Pick how: \u201CGoogle account\u201D sends recordings straight to your Google Drive (easiest). \u201CFolder\u201D copies them to a folder you choose.",
                            "2. For a Drive folder, choose it INSIDE the Drive app (\u2630 menu \u2192 Drive). A folder in Internal storage stays on your phone and will not show in Drive.",
                            "3. Leave \u201CBack up new recordings automatically\u201D on. Each recording is copied right after the call. If there is no internet yet, it waits and tries again.",
                            "4. Tap \u201CTest backup\u201D. It saves a small test file and tells you exactly where it went.",
                            "5. Drive can take a minute to show new files in the app. Pull down to refresh."
                        ).forEach { Text(it, fontSize = 12.5.sp, color = palette.textPrimary, modifier = Modifier.padding(vertical = 3.dp)) }
                    }
                }
            }
        }
    }
}

/** Small optional extras for the recorder itself. */
@Composable
fun RecordingExtrasCard(palette: DialerPalette, prefs: RecordingPrefs, modifier: Modifier = Modifier) {
    var showMark by remember { mutableStateOf(prefs.showMarkButton) }
    var minSec by remember { mutableIntStateOf(prefs.discardShorterThanSec) }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .liquidGlass(palette, RoundedCornerShape(22.dp))
            .padding(16.dp)
    ) {
        Text("Smart extras", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary)
        Text("All optional", fontSize = 12.sp, color = palette.textSecondary)
        Spacer(Modifier.height(8.dp))
        PrefSwitchRow(
            "\u201CMark moment\u201D button on the call screen",
            "Tap the flag during a call to bookmark an important moment. Jump to it later while listening.",
            showMark,
            palette
        ) { showMark = it; prefs.showMarkButton = it }
        Spacer(Modifier.height(6.dp))
        Text("Discard very short recordings", fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, color = palette.textPrimary)
        Text("Missed taps and instantly-ended calls aren't worth keeping.", fontSize = 12.sp, color = palette.textSecondary)
        Spacer(Modifier.height(8.dp))
        Segmented(
            options = listOf("0" to "Keep all", "3" to "< 3 s", "5" to "< 5 s", "10" to "< 10 s"),
            selected = minSec.toString(),
            palette = palette
        ) { minSec = it.toInt(); prefs.discardShorterThanSec = minSec }
    }
}

@Composable
private fun PrimaryPill(label: String, palette: DialerPalette, onClick: () -> Unit) {
    Text(
        label,
        fontSize = 14.5.sp,
        fontWeight = FontWeight.Bold,
        color = palette.solidBackground,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(50))
            .background(palette.accent)
            .bounceClick(onClick = onClick)
            .padding(vertical = 12.dp),
        textAlign = androidx.compose.ui.text.style.TextAlign.Center
    )
}

@Composable
private fun OutlinePill(label: String, palette: DialerPalette, modifier: Modifier, onClick: () -> Unit) {
    Text(
        label,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = palette.accent,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(palette.accentSoft)
            .bounceClick(onClick = onClick)
            .padding(vertical = 10.dp),
        textAlign = androidx.compose.ui.text.style.TextAlign.Center
    )
}

/**
 * The flag button on the call screen's recording bar. Each tap bookmarks the current moment of the
 * recording in progress (it pops, and the count goes up). Hidden when turned off in recording settings.
 */
@Composable
internal fun MarkMomentButton(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val enabled = remember { RecordingPrefs(context).showMarkButton }
    if (!enabled) return
    var count by remember { mutableIntStateOf(RecordingMarks.count) }
    var popped by remember { mutableStateOf(false) }
    LaunchedEffect(popped) {
        if (popped) {
            delay(170)
            popped = false
        }
    }
    val scale by animateFloatAsState(if (popped) 1.3f else 1f, spring(dampingRatio = 0.35f), label = "mark-pop")
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(Color(0xFFFFB300).copy(alpha = 0.28f))
            .clickable {
                if (RecordingMarks.markNow()) {
                    count = RecordingMarks.count
                    popped = true
                }
            }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Filled.Bookmark,
            contentDescription = "Mark this moment",
            tint = Color(0xFF8A5A00),
            modifier = Modifier
                .size(18.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
        )
        if (count > 0) {
            Spacer(Modifier.width(4.dp))
            Text("$count", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF5C3B00))
        }
    }
}
