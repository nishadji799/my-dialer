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

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ashudialer.app.appcalls.recording.RecMeta
import com.ashudialer.app.ui.components.bounceClick
import com.ashudialer.app.ui.theme.DialerPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/** "m:ss" or "h:mm:ss". */
internal fun fmtDuration(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/** Reads a recording's length without playing it. Returns 0 when it can't be read. */
internal object RecordingProbe {
    fun durationMs(context: Context, file: File): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            val isPrivate = file.absolutePath.startsWith(context.filesDir.absolutePath)
            val uri = if (isPrivate) null else resolveRecordingUri(context, file)
            if (uri != null) retriever.setDataSource(context, uri) else retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } catch (_: Exception) {
            0L
        } finally {
            runCatching { retriever.release() }
        }
    }
}

/**
 * Real waveform of a recording: the audio is decoded once in the background and reduced to a few dozen
 * peak values, then cached. If the file can't be decoded (or is very long) it returns null and the UI
 * draws a decorative waveform instead - playback never depends on this.
 */
internal object WaveformExtractor {
    private val cache = ConcurrentHashMap<String, FloatArray>()
    private const val MAX_DURATION_US = 45L * 60L * 1_000_000L

    fun cached(key: String): FloatArray? = cache[key]

    fun extract(context: Context, file: File, uri: Uri?, buckets: Int): FloatArray? {
        cache[file.name]?.let { return it }
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            if (uri != null) extractor.setDataSource(context, uri, null) else extractor.setDataSource(file.absolutePath)
            var track = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                if (f.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                    track = i
                    format = f
                    break
                }
            }
            if (track < 0 || format == null) return null
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return null
            val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) else 0L
            if (durationUs > MAX_DURATION_US) return null
            extractor.selectTrack(track)

            val decoder = MediaCodec.createDecoderByType(mime)
            codec = decoder
            decoder.configure(format, null, null, 0)
            decoder.start()

            val times = ArrayList<Long>()
            val levels = ArrayList<Float>()
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            var idleSpins = 0
            while (!outputDone && idleSpins < 400) {
                if (!inputDone) {
                    val inIndex = decoder.dequeueInputBuffer(5_000)
                    if (inIndex >= 0) {
                        val buf = decoder.getInputBuffer(inIndex)
                        val size = if (buf != null) extractor.readSampleData(buf, 0) else -1
                        if (size < 0) {
                            decoder.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            decoder.queueInputBuffer(inIndex, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val outIndex = decoder.dequeueOutputBuffer(info, 5_000)
                if (outIndex >= 0) {
                    idleSpins = 0
                    val out = decoder.getOutputBuffer(outIndex)
                    if (out != null && info.size > 0) {
                        out.position(info.offset)
                        out.limit(info.offset + info.size)
                        val shorts = out.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                        var peak = 0
                        while (shorts.hasRemaining()) {
                            val v = abs(shorts.get().toInt())
                            if (v > peak) peak = v
                        }
                        times.add(info.presentationTimeUs)
                        levels.add(peak / 32768f)
                    }
                    decoder.releaseOutputBuffer(outIndex, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                } else {
                    idleSpins++
                }
            }
            if (levels.isEmpty()) return null

            val total = if (durationUs > 0) durationUs else (times.lastOrNull() ?: 0L)
            val peaks = FloatArray(buckets)
            for (i in levels.indices) {
                val bucket = if (total > 0) ((times[i].toDouble() / total) * buckets).toInt()
                else (i.toDouble() / levels.size * buckets).toInt()
                val b = bucket.coerceIn(0, buckets - 1)
                if (levels[i] > peaks[b]) peaks[b] = levels[i]
            }
            val top = peaks.maxOrNull() ?: 0f
            if (top <= 0.0001f) return null
            for (i in peaks.indices) peaks[i] = sqrt((peaks[i] / top).coerceIn(0f, 1f)).coerceAtLeast(0.04f)
            cache[file.name] = peaks
            return peaks
        } catch (_: Throwable) {
            return null
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor.release() }
        }
    }
}

/** A calm, repeatable fake waveform (from the file name) used while the real one is still loading. */
private fun decorativePeaks(seed: String, count: Int): FloatArray {
    var h = seed.hashCode().toLong() and 0xFFFFFFFFL
    return FloatArray(count) { i ->
        h = (h * 1103515245L + 12345L) and 0x7FFFFFFFL
        val base = 0.25f + (h % 1000) / 1000f * 0.6f
        val envelope = 0.55f + 0.45f * kotlin.math.sin(i / count.toFloat() * Math.PI.toFloat())
        (base * envelope).coerceIn(0.08f, 1f)
    }
}

/**
 * Bars that show the recording's loudness over time. The played part is tinted with the accent colour;
 * bookmarks are small flags above the bars; tap or drag anywhere to jump there.
 */
@Composable
internal fun WaveformScrubber(
    peaks: FloatArray,
    progress: Float,
    marks: List<Float>,
    palette: DialerPalette,
    onScrub: (Float) -> Unit,
    onSeekCommit: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val played = palette.accent
    val rest = palette.textSecondary.copy(alpha = 0.32f)
    Canvas(
        modifier = modifier
            .pointerInput(Unit) {
                detectTapGestures { offset -> onSeekCommit((offset.x / size.width).coerceIn(0f, 1f)) }
            }
            .pointerInput(Unit) {
                var last = 0f
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        last = (offset.x / size.width).coerceIn(0f, 1f)
                        onScrub(last)
                    },
                    onDragEnd = { onSeekCommit(last) },
                    onDragCancel = { onSeekCommit(last) },
                    onHorizontalDrag = { change, _ ->
                        last = (change.position.x / size.width).coerceIn(0f, 1f)
                        onScrub(last)
                    }
                )
            }
    ) {
        val n = peaks.size
        if (n == 0) return@Canvas
        val gap = 2.5f
        val barW = ((size.width - gap * (n - 1)) / n).coerceAtLeast(1.5f)
        val flagZone = 8.dp.toPx()
        val usable = size.height - flagZone
        val mid = flagZone + usable / 2f
        for (i in 0 until n) {
            val x = i * (barW + gap)
            val h = max(3.dp.toPx(), peaks[i] * usable)
            val isPlayed = (i + 0.5f) / n <= progress
            drawRoundRect(
                color = if (isPlayed) played else rest,
                topLeft = Offset(x, mid - h / 2f),
                size = Size(barW, h),
                cornerRadius = CornerRadius(barW / 2f, barW / 2f)
            )
        }
        // bookmark flags
        marks.forEach { f ->
            val x = (f * size.width).coerceIn(2f, size.width - 2f)
            drawLine(
                color = Color(0xFFFFB300),
                start = Offset(x, flagZone - 2.dp.toPx()),
                end = Offset(x, size.height),
                strokeWidth = 1.5f.dp.toPx()
            )
            drawCircle(color = Color(0xFFFFB300), radius = 3.5f.dp.toPx(), center = Offset(x, 3.5f.dp.toPx()))
        }
        // playhead
        val px = (progress * size.width).coerceIn(0f, size.width)
        drawLine(
            color = played,
            start = Offset(px, flagZone),
            end = Offset(px, size.height),
            strokeWidth = 2.dp.toPx()
        )
    }
}

/**
 * The in-app player shown when a recording is opened: real waveform, play/pause, -10s / +10s, speed
 * (0.75x - 2x) and bookmarks. The MediaPlayer set-up below is the SAME hard-won set-up the old inline
 * player used (explicit USAGE_MEDIA attributes, AudioManager back to MODE_NORMAL, explicit full
 * volume, private files played from their path) - those exist because recordings made right after a
 * call otherwise played back silently on some phones.
 */
@Composable
internal fun RecordingPlayerPanel(
    file: File,
    meta: RecMeta,
    palette: DialerPalette,
    onMarksChange: (List<Long>) -> Unit,
    onFirstPlay: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var player by remember(file) { mutableStateOf<MediaPlayer?>(null) }
    var positionMs by remember(file) { mutableIntStateOf(0) }
    var durationMs by remember(file) { mutableIntStateOf(0) }
    var isPlaying by remember(file) { mutableStateOf(false) }
    var scrub by remember(file) { mutableStateOf<Float?>(null) }
    var speed by remember(file) { mutableFloatStateOf(1f) }
    var loadError by remember(file) { mutableStateOf(false) }

    val isPrivate = remember(file) { file.absolutePath.startsWith(context.filesDir.absolutePath) }
    val uri = remember(file) { if (isPrivate) null else resolveRecordingUri(context, file) }
    val decorative = remember(file.name) { decorativePeaks(file.name, 64) }
    val peaks by produceState<FloatArray?>(initialValue = WaveformExtractor.cached(file.name), file) {
        if (value == null) {
            value = withContext(Dispatchers.Default) { WaveformExtractor.extract(context, file, uri, 80) }
        }
    }

    DisposableEffect(file) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val previousMode = audioManager?.mode
        try {
            audioManager?.mode = AudioManager.MODE_NORMAL
        } catch (_: Exception) {
        }
        val mp = MediaPlayer().apply {
            try {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                if (uri != null) setDataSource(context, uri) else setDataSource(file.absolutePath)
                setOnPreparedListener {
                    durationMs = duration
                    try {
                        @Suppress("DEPRECATION")
                        setVolume(1f, 1f)
                    } catch (_: Exception) {
                    }
                    start()
                    isPlaying = true
                    onFirstPlay()
                }
                setOnCompletionListener {
                    isPlaying = false
                    positionMs = 0
                }
                setOnErrorListener { _, _, _ ->
                    loadError = true
                    isPlaying = false
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
        while (isPlaying && scrub == null) {
            val current = try { player?.currentPosition } catch (_: Exception) { null }
            if (current != null) positionMs = current
            delay(110)
        }
    }

    fun applySpeed(newSpeed: Float) {
        val mp = player ?: return
        speed = newSpeed
        try {
            val wasPlaying = mp.isPlaying
            mp.playbackParams = (mp.playbackParams ?: PlaybackParams()).setSpeed(newSpeed)
            // Some phones start playing when the speed is changed on a paused player.
            if (!wasPlaying) mp.pause()
        } catch (_: Exception) {
        }
    }

    fun seekTo(ms: Int) {
        val target = ms.coerceIn(0, max(durationMs, 0))
        positionMs = target
        try {
            player?.seekTo(target)
        } catch (_: Exception) {
        }
    }

    fun togglePlay() {
        val mp = player ?: return
        try {
            if (isPlaying) {
                mp.pause()
                isPlaying = false
            } else {
                mp.start()
                isPlaying = true
            }
        } catch (_: Exception) {
        }
    }

    Column(modifier = modifier.fillMaxWidth().padding(top = 6.dp)) {
        if (loadError) {
            Text(
                "Couldn't play this recording \u2014 the file may be incomplete.",
                fontSize = 12.sp,
                color = palette.danger,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            return@Column
        }

        val shownProgress = scrub ?: if (durationMs > 0) positionMs.toFloat() / durationMs else 0f
        val shownPeaks = peaks ?: decorative
        val markFractions = if (durationMs > 0) meta.marks.map { (it.toFloat() / durationMs).coerceIn(0f, 1f) } else emptyList()

        WaveformScrubber(
            peaks = shownPeaks,
            progress = shownProgress,
            marks = markFractions,
            palette = palette,
            onScrub = { scrub = it },
            onSeekCommit = { f ->
                scrub = null
                seekTo((f * durationMs).toInt())
            },
            modifier = Modifier.fillMaxWidth().height(64.dp)
        )
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(fmtDuration((shownProgress * durationMs).toLong()), fontSize = 11.5.sp, color = palette.textSecondary)
            Text(fmtDuration(durationMs.toLong()), fontSize = 11.5.sp, color = palette.textSecondary)
        }
        Spacer(Modifier.height(6.dp))

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // speed
            val speeds = listOf(0.75f, 1f, 1.25f, 1.5f, 2f)
            Text(
                text = (if (speed == speed.toInt().toFloat()) "${speed.toInt()}x" else "${speed}x"),
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold,
                color = palette.accent,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(palette.accentSoft)
                    .bounceClick {
                        val next = speeds[(speeds.indexOf(speed).let { if (it < 0) 1 else it } + 1) % speeds.size]
                        applySpeed(next)
                    }
                    .padding(horizontal = 12.dp, vertical = 7.dp)
            )
            IconButton(onClick = { seekTo(positionMs - 10_000) }) {
                Icon(Icons.Filled.Replay10, contentDescription = "Back 10 seconds", tint = palette.textPrimary)
            }
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(palette.accent)
                    .bounceClick { togglePlay() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = palette.solidBackground,
                    modifier = Modifier.size(28.dp)
                )
            }
            IconButton(onClick = { seekTo(positionMs + 10_000) }) {
                Icon(Icons.Filled.Forward10, contentDescription = "Forward 10 seconds", tint = palette.textPrimary)
            }
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(palette.accentSoft)
                    .bounceClick { onMarksChange((meta.marks + positionMs.toLong()).distinct().sorted()) }
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Bookmark, contentDescription = "Add bookmark here", tint = Color(0xFFFFB300), modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Mark", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary)
            }
        }

        if (meta.marks.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                meta.marks.forEachIndexed { index, ms ->
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color(0xFFFFB300).copy(alpha = 0.18f))
                            .clickable { seekTo(ms.toInt()) }
                            .padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("#${index + 1}  ${fmtDuration(ms)}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = palette.textPrimary)
                        IconButton(
                            onClick = { onMarksChange(meta.marks.filterIndexed { i, _ -> i != index }) },
                            modifier = Modifier.size(22.dp)
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = "Remove bookmark", tint = palette.textSecondary, modifier = Modifier.size(13.dp))
                        }
                    }
                }
            }
        }
    }
}
