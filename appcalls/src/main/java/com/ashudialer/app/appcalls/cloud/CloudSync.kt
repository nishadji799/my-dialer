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

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.BatteryManager
import androidx.core.app.NotificationCompat
import androidx.documentfile.provider.DocumentFile
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.ashudialer.app.appcalls.AppCallsLogger
import com.ashudialer.app.appcalls.R
import com.ashudialer.app.appcalls.recording.RecMeta
import com.ashudialer.app.appcalls.recording.RecordingMetaStore
import java.io.File
import java.util.concurrent.TimeUnit

/** What kind of place the chosen backup folder is - so the screen can say it plainly. */
enum class FolderKind { NONE, GOOGLE_DRIVE, PHONE, OTHER_CLOUD }

/**
 * Entry point of the optional cloud backup. Nothing here runs unless the person turned it on AND chose
 * where files go (see [CloudPrefs]); with the feature off every function is a quick no-op.
 *
 * Two ways to back up:
 *  - MODE_FOLDER: a folder picked in Android's file picker. A folder inside the Google Drive app is uploaded
 *    by the Drive app; a folder on the phone is just a copy (File manager).
 *  - MODE_DRIVE: sign in with Google once; recordings are uploaded straight to a Drive folder.
 *
 * A new recording is copied IMMEDIATELY when the conditions allow (phone folders do not need a network),
 * and WorkManager is the safety net that waits for Wi-Fi / charging and retries. A periodic sweep queues
 * anything that was missed.
 */
object CloudSync {

    private const val TAG = "AppCalls:Cloud"
    private const val WORK_TAG = "ashu_cloud_copy"
    private const val SWEEP_NAME = "ashu_cloud_sweep"
    const val CHANNEL_ID = "ashu_cloud_sync"
    private const val NOTIFICATION_ID = 7420

    internal const val KEY_PATH = "path"
    internal const val KEY_URI = "uri"
    internal const val KEY_NAME = "name"
    internal const val KEY_DELETE_LOCAL = "delete_local"

    /** Called by the recorder the moment a recording has been saved. */
    fun onRecordingSaved(context: Context, file: File, uri: Uri?) {
        val app = context.applicationContext
        val prefs = CloudPrefs(app)
        if (!prefs.isActive || !prefs.autoBackup) return
        enqueue(app, file, uri)          // the safety net
        if (canCopyNow(app, prefs)) {
            Thread {
                runCatching { CloudCopyCore.copyOne(app, file.absolutePath, uri?.toString(), file.name, prefs.cloudOnly) }
            }.start()
        }
    }

    /** Queues one recording for upload (skipped when it is already in the cloud). */
    fun enqueue(context: Context, file: File, uri: Uri?) {
        val app = context.applicationContext
        val prefs = CloudPrefs(app)
        if (!prefs.isActive) return
        val meta = RecordingMetaStore(app)
        if (meta.get(file.name).cloud == RecMeta.CLOUD_DONE) return
        meta.update(file.name) { it.copy(cloud = RecMeta.CLOUD_QUEUED) }

        // A folder on the phone needs no network at all; anything else needs one (Wi-Fi if the person asked).
        val network = when {
            prefs.mode == CloudPrefs.MODE_FOLDER && folderKind(app) == FolderKind.PHONE -> NetworkType.NOT_REQUIRED
            prefs.wifiOnly -> NetworkType.UNMETERED
            else -> NetworkType.CONNECTED
        }
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(network)
            .setRequiresCharging(prefs.chargingOnly)
            .build()
        val data = Data.Builder()
            .putString(KEY_PATH, file.absolutePath)
            .putString(KEY_URI, uri?.toString())
            .putString(KEY_NAME, file.name)
            .putBoolean(KEY_DELETE_LOCAL, prefs.cloudOnly)
            .build()
        val request = OneTimeWorkRequestBuilder<CloudCopyWorker>()
            .setConstraints(constraints)
            .setInputData(data)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .addTag(WORK_TAG)
            .build()
        // KEEP: tapping "Back up now" twice (or a retry sweep) never uploads the same file twice.
        WorkManager.getInstance(app).enqueueUniqueWork("cloud_${file.name}", ExistingWorkPolicy.KEEP, request)
        AppCallsLogger.i(TAG, "Queued for cloud backup: ${file.name}")
    }

    /** Queues every recording that is not in the cloud yet. Returns how many were queued. */
    fun backUpAll(context: Context, files: List<File>, uriFor: (File) -> Uri? = { null }): Int {
        val app = context.applicationContext
        if (!CloudPrefs(app).isActive) return 0
        val meta = RecordingMetaStore(app)
        var queued = 0
        for (f in files) {
            if (meta.get(f.name).cloud != RecMeta.CLOUD_DONE) {
                enqueue(app, f, uriFor(f))
                queued++
            }
        }
        return queued
    }

    /** Schedules (once) the periodic check that catches recordings that never got backed up. */
    fun ensureSweepScheduled(context: Context) {
        val app = context.applicationContext
        // v5: every hour instead of every six. The sweep is cheap (it only queues files that are
        // not marked as backed up yet), and a one-hour cadence means a recording made while the
        // phone was offline goes up soon after the network comes back instead of "tomorrow".
        val request = PeriodicWorkRequestBuilder<CloudSweepWorker>(1, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        // UPDATE replaces the old 6-hour schedule instead of KEEP silently ignoring the new one.
        WorkManager.getInstance(app).enqueueUniquePeriodicWork(SWEEP_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    /**
     * v5: re-queues everything that is not backed up yet, with FRESH constraints.
     *
     * WorkManager keeps the constraints a work item was created with. So when someone turned
     * "Wi-Fi only" OFF (or ON), work that was already queued kept waiting for the old network
     * type - which is exactly why uploads felt like they never happened on mobile data. This
     * cancels the queued copies and enqueues them again with the current rule.
     */
    fun refreshQueue(context: Context) {
        val app = context.applicationContext
        val prefs = CloudPrefs(app)
        if (!prefs.isActive || !prefs.autoBackup) return
        WorkManager.getInstance(app).cancelAllWorkByTag(WORK_TAG)
        Thread {
            runCatching {
                val files = com.ashudialer.app.appcalls.recording.RecordingLibrary.listAll(app)
                backUpAll(app, files)
            }
        }.start()
    }

    /** Stops everything still waiting to be uploaded (files stay safe on the phone). */
    fun cancelAll(context: Context) {
        WorkManager.getInstance(context.applicationContext).cancelAllWorkByTag(WORK_TAG)
    }

    /** Remembers the folder the person picked in the system file picker. */
    fun savePickedFolder(context: Context, tree: Uri): Boolean {
        return try {
            context.contentResolver.takePersistableUriPermission(
                tree,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            val prefs = CloudPrefs(context)
            prefs.folderUri = tree.toString()
            prefs.folderName = DocumentFile.fromTreeUri(context, tree)?.name.orEmpty()
            prefs.mode = CloudPrefs.MODE_FOLDER
            prefs.lastError = ""
            true
        } catch (e: Exception) {
            AppCallsLogger.w(TAG, "Could not keep access to the picked folder: ${e.message}")
            false
        }
    }

    fun folderKind(context: Context): FolderKind {
        val text = CloudPrefs(context).folderUri ?: return FolderKind.NONE
        val authority = runCatching { Uri.parse(text).authority }.getOrNull().orEmpty()
        return when {
            authority.startsWith("com.google.android.apps.docs") -> FolderKind.GOOGLE_DRIVE
            authority == "com.android.externalstorage.documents" ||
                authority.startsWith("com.android.providers") ||
                authority.contains("fileexplorer") ||
                authority.contains("documentsui") ||
                authority.contains("filemanager") -> FolderKind.PHONE
            else -> FolderKind.OTHER_CLOUD
        }
    }

    /** True when the picked folder still exists and can be written to. */
    fun isFolderUsable(context: Context): Boolean {
        val text = CloudPrefs(context).folderUri ?: return false
        return runCatching {
            val doc = DocumentFile.fromTreeUri(context, Uri.parse(text))
            doc != null && doc.exists() && doc.canWrite()
        }.getOrDefault(false)
    }

    private var usableCheckedAt = 0L
    private var usableCached = false

    /** [isFolderUsable] remembered for a few seconds, so a screen can call it while drawing. */
    fun folderIsUsableCached(context: Context): Boolean {
        val now = System.currentTimeMillis()
        if (now - usableCheckedAt > 5_000L) {
            usableCached = isFolderUsable(context)
            usableCheckedAt = now
        }
        return usableCached
    }

    /** Opens the backup folder in the app that owns it (Google Drive), for "Open folder". */
    fun folderViewIntent(context: Context): Intent? {
        val prefs = CloudPrefs(context)
        if (prefs.mode == CloudPrefs.MODE_DRIVE) {
            return Intent(Intent.ACTION_VIEW, Uri.parse("https://drive.google.com/drive/my-drive")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val text = prefs.folderUri ?: return null
        val doc = DocumentFile.fromTreeUri(context, Uri.parse(text)) ?: return null
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(doc.uri, "vnd.android.document/directory")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /** The system sign-in request for MODE_DRIVE (used by the settings screen). */
    fun driveAuthorizationRequest() = DriveRestClient.authorizationRequest()

    /** Why queued recordings are not uploading yet, in plain words (null = nothing is blocking). */
    fun waitingReason(context: Context): String? {
        val prefs = CloudPrefs(context)
        if (!prefs.isActive) return null
        val phoneFolder = prefs.mode == CloudPrefs.MODE_FOLDER && folderKind(context) == FolderKind.PHONE
        if (!phoneFolder) {
            if (!hasInternet(context)) return "Waiting for an internet connection"
            if (prefs.wifiOnly && isMetered(context)) return "Waiting for Wi-Fi (Wi-Fi only is on)"
        }
        if (prefs.chargingOnly && !isCharging(context)) return "Waiting for the phone to be plugged in (Only while charging is on)"
        return null
    }

    /**
     * Writes a tiny test file into the chosen place and says what happened, in plain words.
     * Blocking - call it from a background thread.
     */
    fun testBackup(context: Context): String {
        val app = context.applicationContext
        val prefs = CloudPrefs(app)
        if (!prefs.isActive) return "Backup is not set up yet."
        val stamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).format(java.util.Date())
        val name = "Ashu-Phone-backup-test_$stamp.txt"
        val bytes = "This file was created by Ashu Phone to test your recording backup. You can delete it.".toByteArray()
        return if (prefs.mode == CloudPrefs.MODE_DRIVE) {
            val result = DriveRestClient.upload(app, name, "text/plain", bytes.size.toLong()) { bytes.inputStream() }
            if (result.ok) "Success. A test file was uploaded to the \"${DriveRestClient.FOLDER_NAME}\" folder in your Google Drive."
            else "Test failed: ${result.message}"
        } else {
            val kind = folderKind(app)
            val folder = prefs.folderUri?.let { runCatching { DocumentFile.fromTreeUri(app, Uri.parse(it)) }.getOrNull() }
            if (folder == null || !folder.exists() || !folder.canWrite()) {
                "Test failed: the folder is not reachable. Choose it again."
            } else {
                val doc = runCatching { folder.createFile("text/plain", name) }.getOrNull()
                val ok = doc != null && runCatching {
                    app.contentResolver.openOutputStream(doc.uri, "w")?.use { it.write(bytes); true } ?: false
                }.getOrDefault(false)
                if (!ok) "Test failed: could not write into the folder."
                else when (kind) {
                    FolderKind.GOOGLE_DRIVE -> "Success. A test file was written to your Google Drive folder. It can take a minute to show up in the Drive app."
                    FolderKind.PHONE -> "Test file written, but this folder is ON YOUR PHONE, not in Google Drive. To use Drive, choose a folder inside Drive, or switch to the Google account method."
                    else -> "Success. A test file was written to the folder."
                }
            }
        }
    }

    internal fun mimeFor(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
        "m4a", "mp4" -> "audio/mp4"
        "aac" -> "audio/aac"
        "ogg", "opus", "oga" -> "audio/ogg"
        "flac" -> "audio/flac"
        "wav" -> "audio/wav"
        "mp3" -> "audio/mpeg"
        "amr" -> "audio/amr"
        "txt" -> "text/plain"
        else -> "application/octet-stream"
    }

    private fun canCopyNow(context: Context, prefs: CloudPrefs): Boolean {
        if (prefs.chargingOnly && !isCharging(context)) return false
        val phoneFolder = prefs.mode == CloudPrefs.MODE_FOLDER && folderKind(context) == FolderKind.PHONE
        if (phoneFolder) return true
        if (!hasInternet(context)) return false
        return !(prefs.wifiOnly && isMetered(context))
    }

    private fun hasInternet(context: Context): Boolean = runCatching {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        cm.getNetworkCapabilities(cm.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }.getOrDefault(false)

    private fun isMetered(context: Context): Boolean = runCatching {
        (context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager).isActiveNetworkMetered
    }.getOrDefault(true)

    private fun isCharging(context: Context): Boolean = runCatching {
        (context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager).isCharging
    }.getOrDefault(false)

    /** One quiet notification when a backup gave up. Never fires for the normal waiting-for-Wi-Fi case. */
    internal fun notifyProblem(context: Context, text: String) {
        runCatching {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Recording backup", NotificationManager.IMPORTANCE_LOW)
            )
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_rec_mic)
                .setContentTitle("Recording backup needs attention")
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()
            nm.notify(NOTIFICATION_ID, notification)
        }
    }
}
