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
package com.ashudialer.app.appcalls.recording

import android.content.Context
import android.os.SystemClock
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Everything the person adds ON TOP of a recording file: star, tags, a note, bookmarks ("marks"),
 * whether it was played, its length, and whether it has been backed up to the cloud folder.
 *
 * It is keyed by the recording's FILE NAME (names carry the date + time, so they are unique) and kept
 * in plain SharedPreferences as one small JSON string per recording. That means:
 *  - the recorder service (which has no UI) can write bookmarks the moment a recording is saved,
 *  - starring / tagging never touches or rewrites the audio file itself,
 *  - the data survives the recording moving between public Music and private storage.
 *
 * v4 feature. Inspired by the "mark a moment", "tags and stars" and "note per call" ideas in
 * CallVault (https://github.com/madkongo/CallVault, GPL-3.0+) - ideas only, this code is written for
 * Ashu Dialer.
 */
data class RecMeta(
    val starred: Boolean = false,
    val tags: List<String> = emptyList(),
    val note: String = "",
    /** Bookmark positions in milliseconds from the start of the recording. */
    val marks: List<Long> = emptyList(),
    val played: Boolean = false,
    val durationMs: Long = 0L,
    /** One of the CLOUD_* constants below. */
    val cloud: Int = CLOUD_NONE,
    val cloudAt: Long = 0L,
    /** Document URI of the copy in the cloud folder (empty when there is none). */
    val cloudUri: String = ""
) {
    companion object {
        const val CLOUD_NONE = 0
        const val CLOUD_QUEUED = 1
        const val CLOUD_DONE = 2
        const val CLOUD_FAILED = 3
    }
}

class RecordingMetaStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun get(name: String): RecMeta = synchronized(LOCK) { decode(prefs.getString(name, null)) }

    fun all(): Map<String, RecMeta> = synchronized(LOCK) {
        val out = HashMap<String, RecMeta>()
        for ((key, value) in prefs.all) {
            if (value is String) out[key] = decode(value)
        }
        out
    }

    /** Reads, changes and saves one recording's extras atomically. Returns the saved value. */
    fun update(name: String, change: (RecMeta) -> RecMeta): RecMeta = synchronized(LOCK) {
        val next = change(decode(prefs.getString(name, null)))
        // A recording with nothing set needs no entry at all - keeps the store small.
        if (next == RecMeta()) prefs.edit().remove(name).apply()
        else prefs.edit().putString(name, encode(next)).apply()
        next
    }

    fun remove(name: String) = synchronized(LOCK) { prefs.edit().remove(name).apply() }

    private fun encode(m: RecMeta): String = JSONObject().apply {
        put("s", m.starred)
        put("t", JSONArray(m.tags))
        put("n", m.note)
        put("m", JSONArray(m.marks))
        put("p", m.played)
        put("d", m.durationMs)
        put("c", m.cloud)
        put("ca", m.cloudAt)
        put("cu", m.cloudUri)
    }.toString()

    private fun decode(json: String?): RecMeta {
        if (json.isNullOrBlank()) return RecMeta()
        return try {
            val o = JSONObject(json)
            val tags = mutableListOf<String>()
            o.optJSONArray("t")?.let { a -> for (i in 0 until a.length()) tags.add(a.optString(i)) }
            val marks = mutableListOf<Long>()
            o.optJSONArray("m")?.let { a -> for (i in 0 until a.length()) marks.add(a.optLong(i)) }
            RecMeta(
                starred = o.optBoolean("s", false),
                tags = tags.filter { it.isNotBlank() },
                note = o.optString("n", ""),
                marks = marks.sorted(),
                played = o.optBoolean("p", false),
                durationMs = o.optLong("d", 0L),
                cloud = o.optInt("c", RecMeta.CLOUD_NONE),
                cloudAt = o.optLong("ca", 0L),
                cloudUri = o.optString("cu", "")
            )
        } catch (_: Exception) {
            RecMeta()
        }
    }

    companion object {
        private const val PREFS = "ashu_recording_meta"
        private val LOCK = Any()
    }
}

/**
 * "Mark this moment": the in-call screen calls [markNow] while a recording is running; the marks wait
 * here and are attached to the saved file by RecordingForegroundService the moment it is finalized.
 */
object RecordingMarks {
    private val pending = CopyOnWriteArrayList<Long>()

    /** Adds a bookmark at the current point of the recording in progress. False if nothing is recording. */
    fun markNow(): Boolean {
        if (!RecordingForegroundService.isRecordingNow) return false
        val started = RecordingForegroundService.startedAtElapsedMs
        if (started <= 0L) return false
        pending.add((SystemClock.elapsedRealtime() - started).coerceAtLeast(0L))
        return true
    }

    val count: Int get() = pending.size

    fun drain(): List<Long> {
        val copy = pending.toList()
        pending.clear()
        return copy
    }

    fun clear() = pending.clear()
}
