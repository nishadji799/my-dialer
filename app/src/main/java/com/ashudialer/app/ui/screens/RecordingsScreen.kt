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
@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.ashudialer.app.ui.screens

import android.media.MediaPlayer
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ashudialer.app.ui.theme.LocalDialerPalette
import kotlinx.coroutines.delay
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.ashudialer.app.ui.components.glassCard


// v4: the Recordings screen itself now lives in RecordingsHub.kt (same name, same parameters plus a few
// new optional ones). What stays here is the reusable inline player used by Private Space, and the
// helpers (label parsing, source detection, MediaStore lookup) shared by both screens.

/**
 * A real, self-contained in-app player for a single recording - play/pause,
 * a scrubbable progress bar, and elapsed/total time - instead of handing
 * the file off to whatever external audio app the person happens to have
 * installed. Backed by a plain MediaPlayer scoped to this composable's
 * lifetime, released whenever the row collapses or the screen leaves.
 *
 * isPlaying is owned by the parent RecordingRow (not local state here
 * anymore) so the row's leading play/pause icon and this player's own
 * play/pause button always agree - see the comment on RecordingRow's
 * isPlaying for why that split used to let the two icons disagree.
 */
@Composable
internal fun InlineAudioPlayer(
    file: File,
    palette: com.ashudialer.app.ui.theme.DialerPalette,
    isPlaying: Boolean,
    onIsPlayingChange: (Boolean) -> Unit
) {
    val context = LocalContext.current
    var player by remember(file) { mutableStateOf<MediaPlayer?>(null) }
    var positionMs by remember(file) { mutableStateOf(0) }
    var durationMs by remember(file) { mutableStateOf(0) }
    var isSeeking by remember(file) { mutableStateOf(false) }
    var loadError by remember(file) { mutableStateOf(false) }

    DisposableEffect(file) {
        // Belt-and-braces alongside the AudioAttributes fix above: even
        // with USAGE_MEDIA set on the player itself, the shared
        // AudioManager can still be left in AudioManager.MODE_IN_COMMUNICATION
        // (Telecom sets this while a call is active and it isn't always
        // guaranteed to have been reset back to MODE_NORMAL by the time this
        // screen is opened, e.g. opening Recordings right after ending a
        // call). In that mode, audio can get routed toward the earpiece at
        // call-volume levels rather than the normal media/speaker path -
        // which sounds exactly like "no audio" if the phone isn't held to
        // an ear. Forcing MODE_NORMAL before playback starts, and restoring
        // whatever mode was active before once this player is torn down,
        // guarantees this screen's playback always uses the same audio path
        // a music or voice-message app would, regardless of what the rest
        // of the app was doing right before.
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
        val previousMode = audioManager?.mode
        try {
            audioManager?.mode = android.media.AudioManager.MODE_NORMAL
        } catch (_: Exception) {
        }

        val mp = MediaPlayer().apply {
            try {
                // Explicitly setting AudioAttributes with USAGE_MEDIA here is
                // the actual fix for recordings playing back completely
                // silent - MediaPlayer's *default* audio attributes without
                // this call are supposed to already be USAGE_MEDIA, but this
                // app spends a lot of its lifetime routing call audio via
                // Telecom (which manipulates the shared AudioManager's mode
                // and active audio stream while a call is up). Once that
                // happens, a MediaPlayer created without an explicit
                // AudioAttributes call can inherit stale routing from
                // whatever stream was last active - it "plays" (no error,
                // progress bar moves, duration is correct) but the actual
                // audio never reaches the speaker/earpiece at an audible
                // level. Setting this explicitly on every playback, every
                // time, forces it onto the normal media stream regardless of
                // whatever audio state the rest of the app left behind.
                setAudioAttributes(
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                // A recording in the app's own private storage (Private Space) is played straight from its
                // path. It must NOT go through the MediaStore lookup: that matches by file NAME, so if a
                // same-named public copy still existed (for example while a move is in progress) it would
                // open the PUBLIC copy instead of the private one.
                val isPrivateFile = file.absolutePath.startsWith(context.filesDir.absolutePath)
                val recordingUri = if (isPrivateFile) null else resolveRecordingUri(context, file)
                if (recordingUri != null) {
                    setDataSource(context, recordingUri)
                } else {
                    setDataSource(file.absolutePath)
                }
                setOnPreparedListener {
                    durationMs = duration
                    // Belt-and-braces alongside the AudioAttributes fix
                    // above: on some hardware, a MediaPlayer playing a
                    // mono, low-sample-rate stream (this app's recordings
                    // are 16kHz or 8kHz mono - see CallRecorder.kt) has
                    // been observed to leave its own internal playback
                    // volume at effectively zero until setVolume is called
                    // explicitly, independent of the device's system media
                    // volume slider. Setting it to full here costs nothing
                    // when it was already fine, and fixes it on the
                    // hardware where it wasn't.
                    try {
                        @Suppress("DEPRECATION")
                        setVolume(1f, 1f)
                    } catch (_: Exception) {
                    }
                    start()
                    onIsPlayingChange(true)
                }
                setOnCompletionListener {
                    onIsPlayingChange(false)
                    positionMs = 0
                }
                setOnErrorListener { _, _, _ ->
                    // A recording that fell back to MIC-only capture with
                    // near-silent audio can still fail to *decode* if the
                    // file itself is malformed (e.g. the app was killed
                    // mid-recording before stop() finished writing the MPEG-4
                    // container's trailer). Surfacing this explicitly as
                    // "Couldn't play this recording" is more honest than
                    // leaving the row looking like it's silently doing
                    // nothing when tapped.
                    loadError = true
                    onIsPlayingChange(false)
                    true
                }
                prepareAsync()
            } catch (_: Exception) {
                loadError = true
            }
        }
        player = mp
        onDispose {
            try {
                mp.stop()
            } catch (_: Exception) {
            }
            mp.release()
            player = null
            try {
                if (previousMode != null) audioManager?.mode = previousMode
            } catch (_: Exception) {
            }
        }
    }

    LaunchedEffect(player, isPlaying) {
        while (isPlaying && !isSeeking) {
            val current = try { player?.currentPosition } catch (_: Exception) { null }
            if (current != null) positionMs = current
            delay(200)
        }
    }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        if (loadError) {
            Text(
                "Couldn't play this recording — the file may be incomplete.",
                fontSize = 12.sp,
                color = palette.danger,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            return@Column
        }
        Slider(
            value = if (durationMs > 0) positionMs.toFloat() / durationMs else 0f,
            onValueChange = { fraction ->
                isSeeking = true
                positionMs = (fraction * durationMs).toInt()
            },
            onValueChangeFinished = {
                try {
                    player?.seekTo(positionMs)
                } catch (_: Exception) {
                }
                isSeeking = false
            },
            colors = SliderDefaults.colors(
                thumbColor = palette.accent,
                activeTrackColor = palette.accent,
                inactiveTrackColor = palette.cardBorder
            )
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(formatMs(positionMs), fontSize = 11.5.sp, color = palette.textSecondary)
            IconButton(
                onClick = {
                    val mp = player ?: return@IconButton
                    try {
                        if (isPlaying) {
                            mp.pause()
                            onIsPlayingChange(false)
                        } else {
                            mp.start()
                            onIsPlayingChange(true)
                        }
                    } catch (_: Exception) {
                    }
                },
                modifier = Modifier.size(34.dp).clip(CircleShape).background(palette.accentSoft)
            ) {
                Icon(
                    if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = palette.accent,
                    modifier = Modifier.size(16.dp)
                )
            }
            Text(formatMs(durationMs), fontSize = 11.5.sp, color = palette.textSecondary)
        }
    }
}

internal fun resolveRecordingUri(context: Context, file: File): Uri? {
    return try {
        val collection = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }
        val projection = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.DISPLAY_NAME, MediaStore.Audio.Media.DATA)
        // The recording may sit directly in "Music/Ashu Dialer/" (saved by older versions) or in a
        // per-source sub-folder such as "Music/Ashu Dialer/WhatsApp/" (current versions), so the folder
        // is matched by PREFIX. An exact match on "Music/Ashu Dialer/" would find none of the new
        // recordings, and tapping play would silently do nothing.
        val selection = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            "${MediaStore.Audio.Media.DISPLAY_NAME} = ? AND ${MediaStore.Audio.Media.RELATIVE_PATH} LIKE ?"
        } else {
            "${MediaStore.Audio.Media.DISPLAY_NAME} = ?"
        }
        val args = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            arrayOf(file.name, "Music/Ashu Dialer/%")
        } else arrayOf(file.name)
        context.contentResolver.query(collection, projection, selection, args, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID))
                return MediaStore.Audio.Media.getContentUri(
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) MediaStore.VOLUME_EXTERNAL_PRIMARY else "external"
                ).buildUpon().appendPath(id.toString()).build()
            }
        }
        null
    } catch (_: Exception) {
        null
    }
}

private fun formatMs(ms: Int): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}


internal fun parseRecordingLabel(fileName: String): Pair<String, String> {
    val withoutExt = fileName.substringBeforeLast(".")
    val parts = withoutExt.split("_").toMutableList()

    // Optional trailing direction token added by the new file-name template.
    var direction: String? = null
    if (parts.size >= 4 && parts.last().lowercase() in setOf("in", "out")) {
        direction = if (parts.removeAt(parts.lastIndex).lowercase() == "in") "Incoming" else "Outgoing"
    }
    if (parts.size < 3) return withoutExt to (direction ?: "")

    val caller = parts.dropLast(2).joinToString("_").ifBlank { "Unknown" }
    val datePart = parts[parts.size - 2]
    val timePart = parts[parts.size - 1]
    return try {
        val parsed = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).parse("${datePart}_$timePart")
        val formatted = if (parsed != null) SimpleDateFormat("MMM d, yyyy \u2022 h:mm a", Locale.getDefault()).format(parsed) else ""
        caller to listOfNotNull(direction, formatted.ifBlank { null }).joinToString(" \u2022 ")
    } catch (_: Exception) {
        caller to (direction ?: "")
    }
}

/** The pages of the Recordings list: everything, phone calls, and one page per supported calling app. */
internal enum class RecordingKind(val title: String) {
    ALL("All"), PHONE("Phone"), WHATSAPP("WhatsApp"), TELEGRAM("Telegram"), INSTAGRAM("Instagram"), SNAPCHAT("Snapchat")
}

/**
 * Which tab a recording belongs in.
 *
 * The FOLDER is checked first (Music/Ashu Dialer/WhatsApp/... - see RecordingStorage.subFolderFor),
 * because that is always right, including when the person edited the file-name template and the
 * app's name no longer appears in the file name. The file name is the fallback, which is what
 * recordings made by older versions (saved directly in "Ashu Dialer/") rely on.
 */
internal fun kindOf(file: File): RecordingKind {
    val folder = file.parentFile?.name?.lowercase().orEmpty()
    val n = file.name.lowercase()
    return when {
        folder == "whatsapp" -> RecordingKind.WHATSAPP
        folder == "telegram" -> RecordingKind.TELEGRAM
        folder == "instagram" -> RecordingKind.INSTAGRAM
        folder == "snapchat" -> RecordingKind.SNAPCHAT
        folder == "phone" -> RecordingKind.PHONE
        n.startsWith("whatsapp") -> RecordingKind.WHATSAPP
        n.startsWith("telegram") -> RecordingKind.TELEGRAM
        n.startsWith("instagram") -> RecordingKind.INSTAGRAM
        n.startsWith("snapchat") -> RecordingKind.SNAPCHAT
        else -> RecordingKind.PHONE
    }
}
