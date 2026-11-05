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
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ashudialer.app.appcalls.AppCallsLogger
import com.ashudialer.app.appcalls.recording.RecMeta
import com.ashudialer.app.appcalls.recording.RecordingMetaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The background safety net: copies ONE finished recording through [CloudCopyCore], waits for the right
 * network, and retries with back-off. (The same copy is also tried immediately when a recording is saved.)
 * After [MAX_ATTEMPTS] it gives up, says so, and keeps the local file - nothing is ever lost.
 */
class CloudCopyWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val ctx = applicationContext
        val path = inputData.getString(CloudSync.KEY_PATH) ?: return@withContext Result.failure()
        val uriText = inputData.getString(CloudSync.KEY_URI)
        val name = inputData.getString(CloudSync.KEY_NAME) ?: File(path).name
        val deleteLocal = inputData.getBoolean(CloudSync.KEY_DELETE_LOCAL, false)

        val result = CloudCopyCore.copyOne(ctx, path, uriText, name, deleteLocal)
        when {
            result.ok -> Result.success()
            result.retry && runAttemptCount < MAX_ATTEMPTS -> {
                AppCallsLogger.w(TAG, "Backup of $name failed (${result.message}) - will retry, attempt ${runAttemptCount + 1}")
                Result.retry()
            }
            else -> {
                RecordingMetaStore(ctx).update(name) { it.copy(cloud = RecMeta.CLOUD_FAILED) }
                CloudSync.notifyProblem(ctx, "Couldn't back up \"$name\": ${result.message}. The recording is still safe on your phone.")
                Result.failure()
            }
        }
    }

    companion object {
        private const val TAG = "AppCalls:CloudCopy"
        private const val MAX_ATTEMPTS = 5
    }
}

/** Runs every few hours: queues any recording that never made it to the cloud (missed while offline, etc). */
class CloudSweepWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val ctx = applicationContext
        val prefs = CloudPrefs(ctx)
        if (prefs.isActive && prefs.autoBackup) {
            val files = com.ashudialer.app.appcalls.recording.RecordingLibrary.listAll(ctx)
            CloudSync.backUpAll(ctx, files)
        }
        Result.success()
    }
}
