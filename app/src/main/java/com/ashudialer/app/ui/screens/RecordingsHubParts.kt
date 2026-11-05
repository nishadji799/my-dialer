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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CallMade
import androidx.compose.material.icons.filled.CallReceived
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ashudialer.app.appcalls.recording.RecMeta
import com.ashudialer.app.ui.components.Avatar
import com.ashudialer.app.ui.components.bounceClick
import com.ashudialer.app.ui.components.liquidGlass
import com.ashudialer.app.ui.components.pulseHalo
import com.ashudialer.app.ui.components.staggerIn
import com.ashudialer.app.ui.theme.DialerPalette
import com.ashudialer.app.ui.theme.LocalAshuStyle
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// ------------------------------------------------------------------------------------------------
//  Data shown by the Recordings hub
// ------------------------------------------------------------------------------------------------

internal data class RecItem(
    val file: File,
    val kind: RecordingKind,
    val title: String,
    val incoming: Boolean?,
    val whenMs: Long,
    val sizeBytes: Long,
    val meta: RecMeta
) {
    val key: String get() = file.absolutePath
}

internal enum class QuickFilter(val label: String) {
    STARRED("Starred"),
    UNPLAYED("New"),
    BOOKMARKED("Bookmarked"),
    IN_CLOUD("In cloud"),
    NOT_IN_CLOUD("Not backed up"),
    INCOMING("Incoming"),
    OUTGOING("Outgoing")
}

internal enum class SortMode(val id: String, val label: String) {
    NEWEST("newest", "Newest first"),
    OLDEST("oldest", "Oldest first"),
    LONGEST("longest", "Longest first"),
    LARGEST("largest", "Largest file"),
    NAME("name", "Name A\u2013Z");

    companion object {
        fun from(id: String): SortMode = entries.firstOrNull { it.id == id } ?: NEWEST
    }
}

private val FILE_STAMP = Regex("""(\d{8})_(\d{6})""")

/** The recording time: from the "yyyyMMdd_HHmmss" stamp in the file name, else the file's own date. */
internal fun parseWhenMs(file: File, fallbackMs: Long = 0L): Long {
    val m = FILE_STAMP.find(file.name)
    if (m != null) {
        try {
            val parsed = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).parse("${m.groupValues[1]}_${m.groupValues[2]}")
            if (parsed != null) return parsed.time
        } catch (_: Exception) {
        }
    }
    return fallbackMs
}

/** [sizeBytes] / [modifiedMs] come from a background read - nothing here touches the disk. */
internal fun buildRecItem(file: File, meta: RecMeta, sizeBytes: Long = 0L, modifiedMs: Long = 0L): RecItem {
    val label = parseRecordingLabel(file.name)
    val incoming = when {
        label.second.startsWith("Incoming") -> true
        label.second.startsWith("Outgoing") -> false
        else -> null
    }
    return RecItem(
        file = file,
        kind = kindOf(file),
        title = label.first.replace('_', ' ').trim().ifBlank { "Unknown" },
        incoming = incoming,
        whenMs = parseWhenMs(file, modifiedMs),
        sizeBytes = sizeBytes,
        meta = meta
    )
}

internal fun formatSize(bytes: Long): String = when {
    bytes < 1024L -> "$bytes B"
    bytes < 1024L * 1024L -> "${bytes / 1024} KB"
    bytes < 1024L * 1024L * 1024L -> "%.1f MB".format(bytes / 1048576.0)
    else -> "%.2f GB".format(bytes / 1073741824.0)
}

internal fun timeOfDay(ms: Long): String =
    if (ms <= 0L) "" else SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(ms))

internal fun dayHeader(ms: Long): String {
    if (ms <= 0L) return "Earlier"
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = ms }
    fun startOfDay(c: Calendar) = (c.clone() as Calendar).apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    val days = ((startOfDay(now) - startOfDay(then)) / 86_400_000L).toInt()
    return when {
        days <= 0 -> "Today"
        days == 1 -> "Yesterday"
        days < 7 -> SimpleDateFormat("EEEE", Locale.getDefault()).format(Date(ms))
        then.get(Calendar.YEAR) == now.get(Calendar.YEAR) -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(ms))
        else -> SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(ms))
    }
}

private val Amber = Color(0xFFFFB300)

// ------------------------------------------------------------------------------------------------
//  Status card
// ------------------------------------------------------------------------------------------------

@Composable
internal fun RecorderStatusCard(
    palette: DialerPalette,
    featureOn: Boolean,
    ready: Boolean,
    recordingNow: Boolean,
    count: Int,
    totalBytes: Long,
    cloudCount: Int,
    cloudActive: Boolean,
    cloudFolder: String,
    cloudWifiOnly: Boolean,
    cloudPending: Int,
    onOpenSettings: () -> Unit,
    onOpenSetup: () -> Unit,
    onOpenCloud: () -> Unit,
    onBackUpNow: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (headline, detail, dot) = when {
        recordingNow -> Triple("Recording in progress", "Mark moments with the flag button on the call screen", palette.danger)
        !featureOn -> Triple("Call recording is off", "Turn it on in recording settings", palette.textSecondary)
        !ready -> Triple("Setup needed", "A few steps are left before calls can be recorded", Amber)
        else -> Triple("Recorder is ready", "Your calls are recorded automatically", palette.callGreen)
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .liquidGlass(palette, RoundedCornerShape(24.dp))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .pulseHalo(palette.danger, active = recordingNow, maxRadius = 34.dp)
                    .clip(CircleShape)
                    .background(dot.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Mic, contentDescription = null, tint = dot, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(headline, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary)
                Text(detail, fontSize = 12.5.sp, color = palette.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (!recordingNow && (!featureOn || !ready)) {
                Text(
                    text = if (!featureOn) "Settings" else "Fix now",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = palette.solidBackground,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(palette.accent)
                        .bounceClick { if (!featureOn) onOpenSettings() else onOpenSetup() }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
        }

        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(palette, "$count", if (count == 1) "Recording" else "Recordings", Modifier.weight(1f))
            StatTile(palette, formatSize(totalBytes), "On this phone", Modifier.weight(1f))
            StatTile(palette, "$cloudCount", "In cloud", Modifier.weight(1f))
        }

        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(palette.accentSoft)
                .clickable { onOpenCloud() }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (cloudActive) Icons.Filled.CloudDone else Icons.Filled.Cloud,
                contentDescription = null,
                tint = palette.accent,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (cloudActive) "Backing up to ${cloudFolder.ifBlank { "your cloud folder" }}" else "Back up to Google Drive",
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    if (cloudActive) (if (cloudWifiOnly) "Wi\u2011Fi only \u2022 automatic" else "Any network \u2022 automatic")
                    else "Optional \u2014 no login needed, pick a Drive folder",
                    fontSize = 11.5.sp,
                    color = palette.textSecondary
                )
            }
            if (cloudActive && cloudPending > 0) {
                Text(
                    "Back up $cloudPending",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = palette.accent,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .border(1.dp, palette.accent.copy(alpha = 0.6f), RoundedCornerShape(50))
                        .bounceClick { onBackUpNow() }
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                )
            } else {
                Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = palette.textSecondary)
            }
        }
    }
}

@Composable
private fun StatTile(palette: DialerPalette, value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(palette.accentSoft.copy(alpha = if (palette.isDark) 0.55f else 0.7f))
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary, maxLines = 1)
        Text(label, fontSize = 11.sp, color = palette.textSecondary, maxLines = 1)
    }
}

// ------------------------------------------------------------------------------------------------
//  One recording
// ------------------------------------------------------------------------------------------------

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
internal fun RecordingCard(
    item: RecItem,
    palette: DialerPalette,
    index: Int,
    expanded: Boolean,
    selecting: Boolean,
    selected: Boolean,
    cloudActive: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggleStar: () -> Unit,
    onEditTags: () -> Unit,
    onSaveNote: (String) -> Unit,
    onBackUp: () -> Unit,
    onShare: () -> Unit,
    onOpenWith: () -> Unit,
    onDetails: () -> Unit,
    onMoveToPrivate: () -> Unit,
    onDelete: () -> Unit,
    onMarksChange: (List<Long>) -> Unit,
    onFirstPlay: () -> Unit,
    modifier: Modifier = Modifier
) {
    val style = LocalAshuStyle.current
    val meta = item.meta
    var menuOpen by remember { mutableStateOf(false) }
    var editingNote by remember(item.key) { mutableStateOf(false) }
    var noteDraft by remember(item.key, meta.note) { mutableStateOf(meta.note) }

    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .staggerIn(index)
            .liquidGlass(palette, shape)
            .then(if (selected) Modifier.border(2.dp, palette.accent, shape) else Modifier)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 14.dp, vertical = style.rowPadding + 2.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // avatar with a small in/out badge
            Box(contentAlignment = Alignment.BottomEnd) {
                Avatar(name = item.title, photoUri = null, size = 46.dp)
                if (item.incoming != null) {
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(palette.solidBackground),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (item.incoming) Icons.Filled.CallReceived else Icons.Filled.CallMade,
                            contentDescription = if (item.incoming) "Incoming" else "Outgoing",
                            tint = if (item.incoming) palette.callGreen else palette.accent,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        item.title,
                        fontSize = 15.5.sp,
                        fontWeight = if (!meta.played) FontWeight.Bold else FontWeight.SemiBold,
                        color = palette.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (!meta.played) {
                        Spacer(Modifier.width(6.dp))
                        Box(Modifier.size(7.dp).clip(CircleShape).background(palette.accent))
                    }
                }
                val bits = buildList {
                    add(timeOfDay(item.whenMs))
                    if (meta.durationMs > 0) add(fmtDuration(meta.durationMs))
                    add(formatSize(item.sizeBytes))
                    if (item.kind != RecordingKind.PHONE && item.kind != RecordingKind.ALL) add(item.kind.title)
                }.filter { it.isNotBlank() }
                Text(
                    bits.joinToString("  \u2022  "),
                    fontSize = 12.5.sp,
                    color = palette.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (selecting) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(if (selected) palette.accent else Color.Transparent)
                        .border(1.5.dp, if (selected) palette.accent else palette.textSecondary, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (selected) Text("\u2713", color = palette.solidBackground, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            } else {
                if (meta.starred) {
                    Icon(Icons.Filled.Star, contentDescription = "Starred", tint = Amber, modifier = Modifier.size(19.dp))
                    Spacer(Modifier.width(6.dp))
                }
                CloudBadge(meta.cloud, palette)
            }
        }

        if (meta.tags.isNotEmpty() || meta.marks.isNotEmpty() || meta.note.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                meta.tags.forEach { tag -> SmallChip(tag, palette.accentSoft, palette.accent) }
                if (meta.marks.isNotEmpty()) {
                    SmallChip("\uD83D\uDD16 ${meta.marks.size}", Amber.copy(alpha = 0.18f), palette.textPrimary)
                }
                if (meta.note.isNotBlank() && !expanded) {
                    SmallChip("\uD83D\uDCDD Note", palette.accentSoft, palette.textSecondary)
                }
            }
        }

        AnimatedVisibility(
            visible = expanded && !selecting,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column {
                RecordingPlayerPanel(
                    file = item.file,
                    meta = meta,
                    palette = palette,
                    onMarksChange = onMarksChange,
                    onFirstPlay = onFirstPlay
                )
                Spacer(Modifier.height(10.dp))

                // note
                if (editingNote || meta.note.isNotBlank()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(palette.accentSoft.copy(alpha = if (palette.isDark) 0.5f else 0.7f))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.weight(1f)) {
                            if (noteDraft.isEmpty()) {
                                Text("Add a note about this call\u2026", fontSize = 13.5.sp, color = palette.textSecondary)
                            }
                            BasicTextField(
                                value = noteDraft,
                                onValueChange = { noteDraft = it.take(400) },
                                textStyle = TextStyle(color = palette.textPrimary, fontSize = 13.5.sp),
                                cursorBrush = SolidColor(palette.accent),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        if (noteDraft != meta.note) {
                            Text(
                                "Save",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = palette.accent,
                                modifier = Modifier
                                    .bounceClick {
                                        onSaveNote(noteDraft.trim())
                                        editingNote = false
                                    }
                                    .padding(start = 10.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ActionButton(
                        icon = if (meta.starred) Icons.Filled.Star else Icons.Filled.StarBorder,
                        label = if (meta.starred) "Starred" else "Star",
                        tint = if (meta.starred) Amber else palette.textPrimary,
                        palette = palette,
                        onClick = onToggleStar
                    )
                    ActionButton(Icons.Filled.Label, "Tags", palette.textPrimary, palette, onEditTags)
                    ActionButton(Icons.Filled.Edit, "Note", palette.textPrimary, palette) { editingNote = true }
                    ActionButton(
                        icon = when (meta.cloud) {
                            RecMeta.CLOUD_DONE -> Icons.Filled.CloudDone
                            RecMeta.CLOUD_QUEUED -> Icons.Filled.CloudUpload
                            else -> Icons.Filled.Cloud
                        },
                        label = when (meta.cloud) {
                            RecMeta.CLOUD_DONE -> "Backed up"
                            RecMeta.CLOUD_QUEUED -> "Queued"
                            RecMeta.CLOUD_FAILED -> "Retry"
                            else -> if (cloudActive) "Back up" else "Cloud"
                        },
                        tint = when (meta.cloud) {
                            RecMeta.CLOUD_DONE -> palette.callGreen
                            RecMeta.CLOUD_FAILED -> palette.danger
                            else -> palette.textPrimary
                        },
                        palette = palette,
                        onClick = onBackUp
                    )
                    ActionButton(Icons.Filled.Share, "Share", palette.textPrimary, palette, onShare)
                    Box {
                        ActionButton(Icons.Filled.MoreVert, "More", palette.textPrimary, palette) { menuOpen = true }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Details") },
                                leadingIcon = { Icon(Icons.Filled.Info, null) },
                                onClick = { menuOpen = false; onDetails() }
                            )
                            DropdownMenuItem(
                                text = { Text("Open with\u2026") },
                                leadingIcon = { Icon(Icons.Filled.OpenInNew, null) },
                                onClick = { menuOpen = false; onOpenWith() }
                            )
                            DropdownMenuItem(
                                text = { Text("Move to Private Space") },
                                leadingIcon = { Icon(Icons.Filled.Lock, null) },
                                onClick = { menuOpen = false; onMoveToPrivate() }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete", color = palette.danger) },
                                leadingIcon = { Icon(Icons.Filled.Delete, null, tint = palette.danger) },
                                onClick = { menuOpen = false; onDelete() }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun CloudBadge(state: Int, palette: DialerPalette) {
    when (state) {
        RecMeta.CLOUD_DONE -> Icon(Icons.Filled.CloudDone, "Backed up", tint = palette.callGreen, modifier = Modifier.size(19.dp))
        RecMeta.CLOUD_QUEUED -> {
            val t = rememberInfiniteTransition(label = "cloud-wait")
            val a by t.animateFloat(
                initialValue = 0.35f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
                label = "cloud-a"
            )
            Icon(Icons.Filled.CloudUpload, "Waiting to back up", tint = palette.accent.copy(alpha = a), modifier = Modifier.size(19.dp))
        }
        RecMeta.CLOUD_FAILED -> Icon(Icons.Filled.CloudOff, "Backup failed", tint = palette.danger, modifier = Modifier.size(19.dp))
        else -> Unit
    }
}

@Composable
private fun SmallChip(text: String, bg: Color, fg: Color) {
    Text(
        text,
        fontSize = 11.5.sp,
        fontWeight = FontWeight.SemiBold,
        color = fg,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .padding(horizontal = 9.dp, vertical = 3.dp)
    )
}

@Composable
private fun ActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color,
    palette: DialerPalette,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .bounceClick(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(2.dp))
        Text(label, fontSize = 10.5.sp, color = palette.textSecondary, maxLines = 1)
    }
}

// ------------------------------------------------------------------------------------------------
//  Empty state (a gently breathing waveform)
// ------------------------------------------------------------------------------------------------

@Composable
internal fun EmptyRecordings(
    palette: DialerPalette,
    searching: Boolean,
    featureOn: Boolean,
    onOpenSetup: () -> Unit,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "empty-wave")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2400), RepeatMode.Restart),
        label = "empty-phase"
    )
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Canvas(Modifier.width(150.dp).height(64.dp)) {
            val bars = 15
            val gap = 5.dp.toPx()
            val w = (size.width - gap * (bars - 1)) / bars
            for (i in 0 until bars) {
                val wave = kotlin.math.sin((phase * 2f * Math.PI.toFloat()) + i * 0.55f)
                val envelope = 1f - kotlin.math.abs(i - bars / 2f) / (bars / 2f) * 0.65f
                val h = (size.height * (0.22f + 0.78f * ((wave + 1f) / 2f)) * envelope).coerceAtLeast(6.dp.toPx())
                drawRoundRect(
                    color = palette.accent.copy(alpha = 0.35f + 0.5f * envelope),
                    topLeft = Offset(i * (w + gap), (size.height - h) / 2f),
                    size = Size(w, h),
                    cornerRadius = CornerRadius(w / 2f, w / 2f)
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(
            if (searching) "No recordings match" else "No recordings yet",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = palette.textPrimary
        )
        Spacer(Modifier.height(6.dp))
        Text(
            when {
                searching -> "Try another name, number, tag or filter."
                featureOn -> "Your next recorded call will show up here, with a waveform you can scrub and bookmarks you can add."
                else -> "Turn on call recording and your calls will be saved here automatically."
            },
            fontSize = 13.5.sp,
            color = palette.textSecondary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        if (!searching) {
            Spacer(Modifier.height(18.dp))
            Text(
                if (featureOn) "Check setup" else "Set up recording",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = palette.solidBackground,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(palette.accent)
                    .bounceClick(onClick = onOpenSetup)
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            )
        }
    }
}

// ------------------------------------------------------------------------------------------------
//  Dialogs
// ------------------------------------------------------------------------------------------------

internal val SuggestedTags = listOf("Work", "Personal", "Family", "Important", "Money", "Follow-up")

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TagEditorDialog(
    palette: DialerPalette,
    current: List<String>,
    known: List<String>,
    onDismiss: () -> Unit,
    onSave: (List<String>) -> Unit
) {
    var chosen by remember { mutableStateOf(current.toSet()) }
    var custom by remember { mutableStateOf("") }
    val options = remember(known) { (SuggestedTags + known + current).distinct() }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.cardBackground,
        title = { Text("Tags", color = palette.textPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    options.forEach { tag ->
                        val on = tag in chosen
                        Text(
                            tag,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (on) palette.solidBackground else palette.textPrimary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(if (on) palette.accent else palette.accentSoft)
                                .clickable { chosen = if (on) chosen - tag else chosen + tag }
                                .padding(horizontal = 12.dp, vertical = 7.dp)
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(palette.accentSoft)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.weight(1f)) {
                        if (custom.isEmpty()) Text("New tag", fontSize = 14.sp, color = palette.textSecondary)
                        BasicTextField(
                            value = custom,
                            onValueChange = { custom = it.take(20) },
                            singleLine = true,
                            textStyle = TextStyle(color = palette.textPrimary, fontSize = 14.sp),
                            cursorBrush = SolidColor(palette.accent),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (custom.isNotBlank()) {
                        Text(
                            "Add",
                            fontWeight = FontWeight.Bold,
                            color = palette.accent,
                            modifier = Modifier
                                .bounceClick {
                                    chosen = chosen + custom.trim()
                                    custom = ""
                                }
                                .padding(start = 10.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave((current.filter { it in chosen } + chosen.filter { it !in current }).distinct()) }) {
                Text("Save", color = palette.accent, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = palette.textSecondary) } }
    )
}

@Composable
internal fun RecordingDetailsDialog(palette: DialerPalette, item: RecItem, onDismiss: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val where = if (item.file.absolutePath.startsWith(context.filesDir.absolutePath)) "Private (inside the app)" else "Music / ${item.file.parentFile?.name ?: ""}"
    val rows = listOf(
        "File" to item.file.name,
        "Stored in" to where,
        "Recorded" to (if (item.whenMs > 0) SimpleDateFormat("EEE, MMM d yyyy \u2022 h:mm a", Locale.getDefault()).format(Date(item.whenMs)) else "Unknown"),
        "Length" to (if (item.meta.durationMs > 0) fmtDuration(item.meta.durationMs) else "Not read yet"),
        "Size" to formatSize(item.sizeBytes),
        "Format" to item.file.extension.uppercase().ifBlank { "Unknown" },
        "Direction" to when (item.incoming) { true -> "Incoming"; false -> "Outgoing"; null -> "Unknown" },
        "Source" to (if (item.kind == RecordingKind.PHONE) "Phone call" else item.kind.title),
        "Cloud" to when (item.meta.cloud) {
            RecMeta.CLOUD_DONE -> "Backed up"
            RecMeta.CLOUD_QUEUED -> "Waiting to upload"
            RecMeta.CLOUD_FAILED -> "Backup failed"
            else -> "Not backed up"
        },
        "Bookmarks" to "${item.meta.marks.size}"
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.cardBackground,
        title = { Text("Recording details", color = palette.textPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                rows.forEach { (k, v) ->
                    Column {
                        Text(k, fontSize = 11.sp, color = palette.textSecondary)
                        Text(v, fontSize = 14.sp, color = palette.textPrimary)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close", color = palette.accent, fontWeight = FontWeight.Bold) } }
    )
}

@Composable
internal fun ConfirmDialog(
    palette: DialerPalette,
    title: String,
    message: String,
    confirmLabel: String,
    destructive: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.cardBackground,
        title = { Text(title, color = palette.textPrimary, fontWeight = FontWeight.Bold) },
        text = { Text(message, color = palette.textSecondary, fontSize = 14.sp) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel, color = if (destructive) palette.danger else palette.accent, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = palette.textSecondary) } }
    )
}

/** Wraps a section title used by the list ("Today", a contact name...). */
@Composable
internal fun SectionHeader(title: String, trailing: String, palette: DialerPalette, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 14.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = palette.textSecondary,
            letterSpacing = 0.6.sp,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (trailing.isNotBlank()) Text(trailing, fontSize = 12.sp, color = palette.textSecondary)
    }
}
