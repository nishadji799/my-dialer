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

package com.ashudialer.app.appcalls.cloud

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.ashudialer.app.appcalls.AppCallsLogger
import com.ashudialer.app.appcalls.recording.RecMeta
import com.ashudialer.app.appcalls.recording.RecordingLibrary
import com.ashudialer.app.appcalls.recording.RecordingMetaStore
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap

/** [ok] = the copy exists in the cloud; otherwise [retry] says whether trying again later can help. */
data class CopyResult(val ok: Boolean, val retry: Boolean, val message: String = "", val cloudUri: String = "")

/**
 * The one place a recording is really copied - used by the background worker AND by the immediate attempt
 * made the moment a recording is saved. A lock per recording name makes sure two attempts at the same
 * file never run at once, and a finished upload is never done twice.
 */
object CloudCopyCore {

    private const val TAG = "AppCalls:CloudCore"
    private val locks = ConcurrentHashMap<String, Any>()

    fun copyOne(ctx: Context, path: String, uriText: String?, name: String, deleteLocal: Boolean): CopyResult {
        val lock = locks.getOrPut(name) { Any() }
        synchronized(lock) {
            val prefs = CloudPrefs(ctx)
            val meta = RecordingMetaStore(ctx)

            if (!prefs.enabled) {
                meta.update(name) { if (it.cloud == RecMeta.CLOUD_QUEUED) it.copy(cloud = RecMeta.CLOUD_NONE) else it }
                return CopyResult(true, false, "backup is off")
            }
            if (meta.get(name).cloud == RecMeta.CLOUD_DONE) return CopyResult(true, false)

            val size = sourceSize(ctx, path, uriText)
            if (size <= 0L) {
                AppCallsLogger.w(TAG, "Not backing up $name: the source is empty or missing")
                meta.update(name) { it.copy(cloud = RecMeta.CLOUD_NONE) }
                return CopyResult(false, false, "the recording is empty or missing")
            }

            val result = if (prefs.mode == CloudPrefs.MODE_DRIVE) {
                DriveRestClient.upload(ctx, name, CloudSync.mimeFor(name), size) { openSource(ctx, path, uriText) }
            } else {
                copyToFolder(ctx, prefs, name, size, path, uriText)
            }

            if (result.ok) {
                val now = System.currentTimeMillis()
                meta.update(name) { it.copy(cloud = RecMeta.CLOUD_DONE, cloudAt = now, cloudUri = result.cloudUri) }
                prefs.lastSuccessAt = now
                prefs.lastError = ""
                AppCallsLogger.i(TAG, "Backed up $name (deleteLocal=$deleteLocal)")
                if (deleteLocal) RecordingLibrary.delete(ctx, File(path), keepMeta = true)
            } else {
                prefs.lastError = result.message
            }
            return result
        }
    }

    private fun copyToFolder(ctx: Context, prefs: CloudPrefs, name: String, size: Long, path: String, uriText: String?): CopyResult {
        val picked = prefs.folderUri?.let { runCatching { DocumentFile.fromTreeUri(ctx, Uri.parse(it)) }.getOrNull() }
        if (picked == null || !picked.exists() || !picked.canWrite()) {
            return CopyResult(false, true, "the backup folder is not reachable any more - choose it again")
        }
        // v5: people who pick the Drive ROOT (or any cloud folder) expect the recordings to land in
        // a named folder, not loose next to everything else. Google account mode already does this;
        // folder mode now matches it. Phone folders keep the exact folder the person chose.
        val folder = if (CloudSync.folderKind(ctx) == FolderKind.PHONE) picked else ensureSubfolder(picked)
        val existing = runCatching { folder.findFile(name) }.getOrNull()
        if (existing != null) {
            if (existing.length() == size) return CopyResult(true, false, "", existing.uri.toString())
            runCatching { existing.delete() } // an incomplete leftover from an interrupted attempt
        }
        val target = runCatching { folder.createFile(CloudSync.mimeFor(name), name) }.getOrNull()
            ?: return CopyResult(false, true, "could not create the file in the backup folder")
        var copied = 0L
        try {
            val input = openSource(ctx, path, uriText) ?: throw IOException("cannot read the recording")
            input.use { src ->
                val out = ctx.contentResolver.openOutputStream(target.uri, "w") ?: throw IOException("cannot write to the backup folder")
                out.use { dst ->
                    copied = src.copyTo(dst, 64 * 1024)
                    dst.flush()
                }
            }
        } catch (e: Exception) {
            runCatching { target.delete() }
            return CopyResult(false, true, e.message ?: "the copy was interrupted")
        }
        if (copied != size) {
            runCatching { target.delete() }
            return CopyResult(false, true, "the copy came out incomplete")
        }
        return CopyResult(true, false, "", target.uri.toString())
    }

    private fun ensureSubfolder(root: DocumentFile): DocumentFile {
        val name = DriveRestClient.FOLDER_NAME
        runCatching { root.findFile(name) }.getOrNull()?.let { if (it.isDirectory) return it }
        return runCatching { root.createDirectory(name) }.getOrNull() ?: root
    }

    internal fun openSource(ctx: Context, path: String, uriText: String?): InputStream? {
        if (!uriText.isNullOrBlank()) {
            runCatching { ctx.contentResolver.openInputStream(Uri.parse(uriText)) }.getOrNull()?.let { return it }
        }
        val f = File(path)
        return if (f.exists() && f.length() > 0L) FileInputStream(f) else null
    }

    private fun sourceSize(ctx: Context, path: String, uriText: String?): Long {
        val f = File(path)
        if (f.exists() && f.length() > 0L) return f.length()
        if (!uriText.isNullOrBlank()) {
            val size = runCatching {
                ctx.contentResolver.openAssetFileDescriptor(Uri.parse(uriText), "r")?.use { it.length }
            }.getOrNull()
            if (size != null && size > 0L) return size
        }
        return 0L
    }
}
