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
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ashudialer.app.appcalls.cloud.CloudPrefs
import com.ashudialer.app.appcalls.cloud.CloudSync
import com.ashudialer.app.appcalls.recording.RecMeta
import com.ashudialer.app.appcalls.recording.RecordingForegroundService
import com.ashudialer.app.appcalls.recording.RecordingMetaStore
import com.ashudialer.app.appcalls.recording.RecordingPrefs
import com.ashudialer.app.telecom.RecordingSetupChecker
import com.ashudialer.app.ui.components.bounceClick
import com.ashudialer.app.ui.components.liquidGlass
import com.ashudialer.app.ui.theme.DialerPalette
import com.ashudialer.app.ui.theme.LocalAshuStyle
import com.ashudialer.app.ui.theme.LocalDialerPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private sealed interface HubEntry {
    val key: String
}

private data class HubHeader(override val key: String, val title: String, val trailing: String) : HubEntry
private data class HubRow(val item: RecItem) : HubEntry {
    override val key: String get() = "r_" + item.key
}

/**
 * v4 Recordings hub - the whole screen was redesigned. Everything the old screen did is still here
 * (source tabs, selection, move to Private Space, delete, share, in-app playback, setup shortcut) and
 * these are new, all optional:
 *  - a status card (is the recorder ready / recording right now, storage used, cloud backup state)
 *  - a real waveform player with speed control, +-10 s and BOOKMARKS (mark a moment during the call
 *    from the call screen, or while listening)
 *  - star, tags and a note on every recording
 *  - search (name, number, tags, notes), quick filters, sorting and "group by person"
 *  - optional backup to a Google Drive folder, one by one or all at once
 *  - storage tools (clean old / very short / already-backed-up recordings, never touching starred ones)
 *
 * The new optional parameters all have defaults, so existing callers keep working unchanged.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RecordingsScreen(
    recordings: List<File>,
    onBack: () -> Unit,
    onPlay: (File) -> Unit,
    onShare: (File) -> Unit,
    onDelete: (File) -> Unit,
    onMoveToPrivateSpace: (List<File>) -> Unit = {},
    onOpenRecordingGuide: () -> Unit = {},
    onOpenRecordingSettings: () -> Unit = {},
    onOpenRecordingSetup: () -> Unit = {},
    groupByContact: Boolean = false,
    onGroupByContactChange: (Boolean) -> Unit = {},
    sortModeId: String = SortMode.NEWEST.id,
    onSortModeChange: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val palette = LocalDialerPalette.current
    val style = LocalAshuStyle.current
    val context = LocalContext.current
    val store = remember { RecordingMetaStore(context) }
    val cloudPrefs = remember { CloudPrefs(context) }
    var metaVersion by remember { mutableIntStateOf(0) }
    val metas = remember(recordings, metaVersion) { store.all() }

    // Read the length of recordings we haven't measured yet - once, in the background.
    LaunchedEffect(recordings) {
        withContext(Dispatchers.IO) {
            var sinceRefresh = 0
            for (f in recordings) {
                // 0 = never measured. -1 = could not be read: remembered so a broken file is not retried
                // every time this screen opens.
                if (store.get(f.name).durationMs == 0L) {
                    val d = RecordingProbe.durationMs(context, f)
                    store.update(f.name) { it.copy(durationMs = if (d > 0L) d else -1L) }
                    sinceRefresh++
                    if (sinceRefresh >= 8) {
                        sinceRefresh = 0
                        metaVersion++
                    }
                }
            }
            if (sinceRefresh > 0) metaVersion++
        }
    }

    val recorderReady by produceState(initialValue = true) {
        value = withContext(Dispatchers.IO) { RecordingSetupChecker.isReady(context) }
    }
    val featureOn = remember(metaVersion) {
        RecordingPrefs(context).let { it.callRecordingEnabled || it.anyAppCallRecordingEnabled }
    }
    val cloudActive = remember(metaVersion) { cloudPrefs.isActive }

    // ---- state -------------------------------------------------------------------------------
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var kindFilter by remember { mutableStateOf(RecordingKind.ALL) }
    var quickFilter by remember { mutableStateOf<QuickFilter?>(null) }
    var tagFilter by remember { mutableStateOf<String?>(null) }
    var expandedKey by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var menuOpen by remember { mutableStateOf(false) }
    var sortMenuOpen by remember { mutableStateOf(false) }
    var toolsOpen by remember { mutableStateOf(false) }
    var tagsFor by remember { mutableStateOf<RecItem?>(null) }
    var detailsFor by remember { mutableStateOf<RecItem?>(null) }
    var pendingDelete by remember { mutableStateOf<List<File>?>(null) }
    var pendingDeleteMessage by remember { mutableStateOf("") }
    val selecting = selected.isNotEmpty()
    val sort = SortMode.from(sortModeId)

    BackHandler(enabled = selecting) { selected = emptySet() }
    BackHandler(enabled = !selecting && searching) {
        searching = false
        query = ""
    }

    // ---- data --------------------------------------------------------------------------------
    // File sizes / dates are read once, on a background thread - never while the list is drawing.
    val fileStats by produceState(initialValue = emptyMap<String, Pair<Long, Long>>(), recordings) {
        value = withContext(Dispatchers.IO) {
            recordings.associate { f ->
                f.absolutePath to (runCatching { f.length() }.getOrDefault(0L) to runCatching { f.lastModified() }.getOrDefault(0L))
            }
        }
    }
    val allItems = remember(recordings, metas, fileStats) {
        recordings.distinctBy { it.absolutePath }.map { f ->
            val stat = fileStats[f.absolutePath]
            buildRecItem(f, metas[f.name] ?: RecMeta(), stat?.first ?: 0L, stat?.second ?: 0L)
        }
    }
    val q = query.trim().lowercase()
    val qDigits = remember(q) { q.filter { it.isDigit() } }
    val filtered = remember(allItems, q, kindFilter, quickFilter, tagFilter) {
        allItems.filter { item ->
            (kindFilter == RecordingKind.ALL || item.kind == kindFilter) &&
                (tagFilter == null || tagFilter in item.meta.tags) &&
                when (quickFilter) {
                    null -> true
                    QuickFilter.STARRED -> item.meta.starred
                    QuickFilter.UNPLAYED -> !item.meta.played
                    QuickFilter.BOOKMARKED -> item.meta.marks.isNotEmpty()
                    QuickFilter.IN_CLOUD -> item.meta.cloud == RecMeta.CLOUD_DONE
                    QuickFilter.NOT_IN_CLOUD -> item.meta.cloud != RecMeta.CLOUD_DONE
                    QuickFilter.INCOMING -> item.incoming == true
                    QuickFilter.OUTGOING -> item.incoming == false
                } &&
                (q.isEmpty() ||
                    item.title.lowercase().contains(q) ||
                    item.file.name.lowercase().contains(q) ||
                    item.meta.tags.any { it.lowercase().contains(q) } ||
                    item.meta.note.lowercase().contains(q) ||
                    (qDigits.length >= 2 && item.title.filter { it.isDigit() }.contains(qDigits)))
        }
    }
    val sorted = remember(filtered, sort) {
        when (sort) {
            SortMode.NEWEST -> filtered.sortedByDescending { it.whenMs }
            SortMode.OLDEST -> filtered.sortedBy { it.whenMs }
            SortMode.LONGEST -> filtered.sortedByDescending { it.meta.durationMs }
            SortMode.LARGEST -> filtered.sortedByDescending { it.sizeBytes }
            SortMode.NAME -> filtered.sortedBy { it.title.lowercase() }
        }
    }
    val entries: List<HubEntry> = remember(sorted, groupByContact, sort) {
        val out = ArrayList<HubEntry>()
        when {
            groupByContact -> sorted.groupBy { it.title.lowercase() }.forEach { (k, list) ->
                val total = list.sumOf { it.meta.durationMs }
                out.add(
                    HubHeader(
                        "p_$k",
                        list.first().title,
                        "${list.size} ${if (list.size == 1) "call" else "calls"}" + if (total > 0) " \u2022 ${fmtDuration(total)}" else ""
                    )
                )
                list.forEach { out.add(HubRow(it)) }
            }
            sort == SortMode.NEWEST || sort == SortMode.OLDEST -> sorted.groupBy { dayHeader(it.whenMs) }.forEach { (day, list) ->
                out.add(HubHeader("d_$day", day, "${list.size}"))
                list.forEach { out.add(HubRow(it)) }
            }
            else -> sorted.forEach { out.add(HubRow(it)) }
        }
        out
    }

    val totalBytes = remember(allItems) { allItems.sumOf { it.sizeBytes } }
    val cloudCount = remember(metas) { metas.values.count { it.cloud == RecMeta.CLOUD_DONE } }
    val cloudPending = remember(allItems, cloudActive) { if (cloudActive) allItems.count { it.meta.cloud != RecMeta.CLOUD_DONE } else 0 }
    val kindCounts = remember(allItems) { allItems.groupingBy { it.kind }.eachCount() }
    val knownTags = remember(allItems) { allItems.flatMap { it.meta.tags }.distinct().sorted() }
    val listState = rememberLazyListState()

    // ---- actions -----------------------------------------------------------------------------
    fun touch() {
        metaVersion++
    }

    fun toggleStar(items: List<RecItem>) {
        val makeStarred = items.any { !it.meta.starred }
        items.forEach { i -> store.update(i.file.name) { it.copy(starred = makeStarred) } }
        touch()
    }

    fun backUp(items: List<RecItem>) {
        if (!cloudPrefs.isActive) {
            Toast.makeText(context, "Choose a Drive folder in recording settings first", Toast.LENGTH_SHORT).show()
            onOpenRecordingSettings()
            return
        }
        var queued = 0
        items.forEach { i ->
            if (i.meta.cloud != RecMeta.CLOUD_DONE) {
                val priv = i.file.absolutePath.startsWith(context.filesDir.absolutePath)
                CloudSync.enqueue(context, i.file, if (priv) null else resolveRecordingUri(context, i.file))
                queued++
            }
        }
        Toast.makeText(
            context,
            if (queued == 0) "Already backed up" else "Queued $queued for backup",
            Toast.LENGTH_SHORT
        ).show()
        touch()
    }

    fun askDelete(files: List<File>, message: String) {
        pendingDelete = files
        pendingDeleteMessage = message
    }

    val selectedItems = allItems.filter { it.key in selected }

    Column(modifier = modifier.fillMaxSize()) {
        // ===== top bar ===========================================================================
        if (selecting) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { selected = emptySet() }) {
                    Icon(Icons.Filled.Close, contentDescription = "Cancel selection", tint = palette.textPrimary)
                }
                Text(
                    "${selected.size} selected",
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    color = palette.textPrimary,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { selected = sorted.map { it.key }.toSet() }) {
                    Icon(Icons.Filled.SelectAll, contentDescription = "Select all", tint = palette.textPrimary)
                }
                IconButton(onClick = { toggleStar(selectedItems) }) {
                    Icon(Icons.Filled.Star, contentDescription = "Star", tint = palette.textPrimary)
                }
                IconButton(onClick = {
                    backUp(selectedItems)
                    selected = emptySet()
                }) {
                    Icon(Icons.Filled.CloudUpload, contentDescription = "Back up", tint = palette.textPrimary)
                }
                IconButton(onClick = {
                    onMoveToPrivateSpace(selectedItems.map { it.file })
                    selected = emptySet()
                }) {
                    Icon(Icons.Filled.Lock, contentDescription = "Move to Private Space", tint = palette.textPrimary)
                }
                IconButton(onClick = {
                    askDelete(
                        selectedItems.map { it.file },
                        "Delete ${selectedItems.size} ${if (selectedItems.size == 1) "recording" else "recordings"}? This can't be undone."
                    )
                }) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = palette.danger)
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 6.dp, end = 6.dp, top = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = palette.textPrimary)
                }
                Column(Modifier.weight(1f)) {
                    Text("Recordings", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = palette.textPrimary)
                    Text(
                        "${allItems.size} saved \u2022 ${formatSize(totalBytes)}",
                        fontSize = 12.5.sp,
                        color = palette.textSecondary
                    )
                }
                IconButton(onClick = {
                    searching = !searching
                    if (!searching) query = ""
                }) {
                    Icon(Icons.Filled.Search, contentDescription = "Search", tint = if (searching) palette.accent else palette.textPrimary)
                }
                IconButton(onClick = onOpenRecordingSettings) {
                    Icon(Icons.Filled.Settings, contentDescription = "Recording settings", tint = palette.textPrimary)
                }
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "More", tint = palette.textPrimary)
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Storage tools") },
                            leadingIcon = { Icon(Icons.Filled.Storage, null) },
                            onClick = { menuOpen = false; toolsOpen = true }
                        )
                        DropdownMenuItem(
                            text = { Text("Setup checklist") },
                            leadingIcon = { Icon(Icons.Filled.Settings, null) },
                            onClick = { menuOpen = false; onOpenRecordingSetup() }
                        )
                        DropdownMenuItem(
                            text = { Text("Beginner guide") },
                            leadingIcon = { Icon(Icons.Filled.HelpOutline, null) },
                            onClick = { menuOpen = false; onOpenRecordingGuide() }
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = searching && !selecting,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(palette.searchBackground)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Search, contentDescription = null, tint = palette.textSecondary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Text("Search name, number, tag or note", fontSize = 15.sp, color = palette.textSecondary)
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
        }

        // ===== list ==============================================================================
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 28.dp)
        ) {
            if (!selecting) {
                item(key = "status") {
                    RecorderStatusCard(
                        palette = palette,
                        featureOn = featureOn,
                        ready = recorderReady,
                        recordingNow = RecordingForegroundService.isRecordingNow,
                        count = allItems.size,
                        totalBytes = totalBytes,
                        cloudCount = cloudCount,
                        cloudActive = cloudActive,
                        cloudFolder = if (cloudPrefs.mode == com.ashudialer.app.appcalls.cloud.CloudPrefs.MODE_DRIVE) "Google Drive" else cloudPrefs.folderName,
                        cloudWifiOnly = cloudPrefs.wifiOnly,
                        cloudPending = cloudPending,
                        onOpenSettings = onOpenRecordingSettings,
                        onOpenSetup = onOpenRecordingSetup,
                        onOpenCloud = onOpenRecordingSettings,
                        onBackUpNow = { backUp(allItems.filter { it.meta.cloud != RecMeta.CLOUD_DONE }) }
                    )
                }
            }
            if (allItems.isNotEmpty()) {
                item(key = "filters") {
                    Column(Modifier.padding(top = 12.dp)) {
                        // source tabs (only the ones that have recordings)
                        val kinds = RecordingKind.entries.filter { it == RecordingKind.ALL || (kindCounts[it] ?: 0) > 0 }
                        if (kinds.size > 2) {
                            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                kinds.forEach { k ->
                                    val n = if (k == RecordingKind.ALL) allItems.size else (kindCounts[k] ?: 0)
                                    HubChip("${k.title} $n", kindFilter == k, palette) { kindFilter = k }
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            QuickFilter.entries
                                .filter { cloudActive || cloudCount > 0 || (it != QuickFilter.IN_CLOUD && it != QuickFilter.NOT_IN_CLOUD) }
                                .forEach { f ->
                                    HubChip(f.label, quickFilter == f, palette) { quickFilter = if (quickFilter == f) null else f }
                                }
                            knownTags.forEach { t ->
                                HubChip("# $t", tagFilter == t, palette) { tagFilter = if (tagFilter == t) null else t }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box {
                                Text(
                                    "\u2195  ${sort.label}",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = palette.textPrimary,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .background(palette.accentSoft)
                                        .bounceClick { sortMenuOpen = true }
                                        .padding(horizontal = 14.dp, vertical = 8.dp)
                                )
                                DropdownMenu(expanded = sortMenuOpen, onDismissRequest = { sortMenuOpen = false }) {
                                    SortMode.entries.forEach { m ->
                                        DropdownMenuItem(
                                            text = { Text(m.label, fontWeight = if (m == sort) FontWeight.Bold else FontWeight.Normal) },
                                            onClick = { sortMenuOpen = false; onSortModeChange(m.id) }
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.width(8.dp))
                            HubChip("Group by person", groupByContact, palette) { onGroupByContactChange(!groupByContact) }
                            Spacer(Modifier.weight(1f))
                            Text(
                                "${sorted.size} shown",
                                fontSize = 12.sp,
                                color = palette.textSecondary
                            )
                        }
                    }
                }
            }

            if (entries.isEmpty()) {
                item(key = "empty") {
                    EmptyRecordings(
                        palette = palette,
                        searching = allItems.isNotEmpty(),
                        featureOn = featureOn,
                        onOpenSetup = { if (featureOn) onOpenRecordingSetup() else onOpenRecordingSettings() }
                    )
                }
            }

            itemsIndexed(entries, key = { _, e -> e.key }) { entryIndex, entry ->
                when (entry) {
                    is HubHeader -> SectionHeader(entry.title, entry.trailing, palette)
                    is HubRow -> {
                        val item = entry.item
                        RecordingCard(
                            item = item,
                            palette = palette,
                            index = entryIndex,
                            expanded = expandedKey == item.key,
                            selecting = selecting,
                            selected = item.key in selected,
                            cloudActive = cloudActive,
                            onClick = {
                                if (selecting) {
                                    selected = if (item.key in selected) selected - item.key else selected + item.key
                                } else {
                                    expandedKey = if (expandedKey == item.key) null else item.key
                                }
                            },
                            onLongClick = {
                                expandedKey = null
                                selected = if (item.key in selected) selected - item.key else selected + item.key
                            },
                            onToggleStar = { toggleStar(listOf(item)) },
                            onEditTags = { tagsFor = item },
                            onSaveNote = { text ->
                                store.update(item.file.name) { it.copy(note = text) }
                                touch()
                            },
                            onBackUp = { backUp(listOf(item)) },
                            onShare = { onShare(item.file) },
                            onOpenWith = { onPlay(item.file) },
                            onDetails = { detailsFor = item },
                            onMoveToPrivate = { onMoveToPrivateSpace(listOf(item.file)) },
                            onDelete = { askDelete(listOf(item.file), "Delete the recording with ${item.title}? This can't be undone.") },
                            onMarksChange = { marks ->
                                store.update(item.file.name) { it.copy(marks = marks) }
                                touch()
                            },
                            onFirstPlay = {
                                if (!item.meta.played) {
                                    store.update(item.file.name) { it.copy(played = true) }
                                    touch()
                                }
                            },
                            modifier = Modifier
                        )
                    }
                }
            }
        }
    }

    // ===== dialogs ===============================================================================
    tagsFor?.let { target ->
        TagEditorDialog(
            palette = palette,
            current = target.meta.tags,
            known = knownTags,
            onDismiss = { tagsFor = null },
            onSave = { tags ->
                store.update(target.file.name) { it.copy(tags = tags) }
                tagsFor = null
                touch()
            }
        )
    }
    detailsFor?.let { target -> RecordingDetailsDialog(palette, target) { detailsFor = null } }

    pendingDelete?.let { files ->
        ConfirmDialog(
            palette = palette,
            title = "Delete recordings?",
            message = pendingDeleteMessage,
            confirmLabel = "Delete",
            destructive = true,
            onDismiss = { pendingDelete = null },
            onConfirm = {
                files.forEach { onDelete(it) }
                pendingDelete = null
                selected = emptySet()
                expandedKey = null
                touch()
            }
        )
    }

    if (toolsOpen) {
        StorageToolsDialog(
            palette = palette,
            items = allItems,
            onDismiss = { toolsOpen = false },
            onDeleteGroup = { files, why ->
                toolsOpen = false
                askDelete(files, why)
            }
        )
    }
}

@Composable
private fun HubChip(label: String, selected: Boolean, palette: DialerPalette, onClick: () -> Unit) {
    val bg by animateColorAsState(if (selected) palette.accent else palette.accentSoft, label = "chip-bg")
    Text(
        label,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = if (selected) palette.solidBackground else palette.textPrimary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .bounceClick(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    )
}

/** Clean-up shortcuts. Starred recordings are never touched by any of them. */
@Composable
private fun StorageToolsDialog(
    palette: DialerPalette,
    items: List<RecItem>,
    onDismiss: () -> Unit,
    onDeleteGroup: (List<File>, String) -> Unit
) {
    val now = remember { System.currentTimeMillis() }
    val old = remember(items) { items.filter { !it.meta.starred && it.whenMs > 0 && now - it.whenMs > 30L * 86_400_000L } }
    val short = remember(items) { items.filter { !it.meta.starred && it.meta.durationMs in 1..4_999 } }
    val inCloud = remember(items) { items.filter { !it.meta.starred && it.meta.cloud == RecMeta.CLOUD_DONE } }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.cardBackground,
        title = { Text("Storage tools", color = palette.textPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "${items.size} recordings \u2022 ${formatSize(items.sumOf { it.sizeBytes })} on this phone. Starred recordings are never deleted by these tools.",
                    fontSize = 13.sp,
                    color = palette.textSecondary
                )
                ToolRow(palette, "Older than 30 days", old.size, formatSize(old.sumOf { it.sizeBytes })) {
                    onDeleteGroup(old.map { it.file }, "Delete ${old.size} recordings older than 30 days? This can't be undone.")
                }
                ToolRow(palette, "Shorter than 5 seconds", short.size, formatSize(short.sumOf { it.sizeBytes })) {
                    onDeleteGroup(short.map { it.file }, "Delete ${short.size} very short recordings (under 5 seconds)? This can't be undone.")
                }
                ToolRow(palette, "Already backed up to the cloud", inCloud.size, formatSize(inCloud.sumOf { it.sizeBytes })) {
                    onDeleteGroup(inCloud.map { it.file }, "Remove ${inCloud.size} recordings from this phone? Their cloud copies stay in your Drive folder.")
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close", color = palette.accent, fontWeight = FontWeight.Bold) } }
    )
}

@Composable
private fun ToolRow(palette: DialerPalette, title: String, count: Int, size: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(palette.accentSoft)
            .then(if (count > 0) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = if (count > 0) palette.textPrimary else palette.textSecondary)
            Text(if (count > 0) "$count recordings \u2022 $size" else "Nothing to clean", fontSize = 12.sp, color = palette.textSecondary)
        }
        if (count > 0) Icon(Icons.Filled.Delete, contentDescription = null, tint = palette.danger, modifier = Modifier.size(20.dp))
    }
}
