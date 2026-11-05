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
import androidx.core.content.edit

/**
 * Settings of the optional cloud backup for recordings (plain SharedPreferences, like RecordingPrefs,
 * because the recorder service reads them synchronously the moment a recording is saved).
 *
 * The "cloud" is whatever folder the person picks in Android's own file picker - normally a folder in
 * the Google Drive app, but any app that shows up there (OneDrive, Dropbox, Nextcloud...) works the
 * same way. Ashu Dialer therefore never asks for a Google login and never talks to Google's servers
 * itself; the Drive app does the actual upload. (Design idea: CallVault's "Drive backup goes through
 * Android's own file picker", https://github.com/madkongo/CallVault, GPL-3.0+.)
 */
class CloudPrefs(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Master switch - OFF by default, the feature is fully optional. */
    var enabled: Boolean
        get() = prefs.getBoolean("enabled", false)
        set(v) = prefs.edit { putBoolean("enabled", v) }

    /** Persisted tree URI of the chosen folder, or null if none picked yet. */
    var folderUri: String?
        get() = prefs.getString("folder_uri", null)
        set(v) = prefs.edit { if (v == null) remove("folder_uri") else putString("folder_uri", v) }

    /** Friendly name of the chosen folder, for display only. */
    var folderName: String
        get() = prefs.getString("folder_name", "") ?: ""
        set(v) = prefs.edit { putString("folder_name", v) }

    /** Back up every new recording on its own as soon as it is saved. */
    var autoBackup: Boolean
        get() = prefs.getBoolean("auto_backup", true)
        set(v) = prefs.edit { putBoolean("auto_backup", v) }

    /** Only upload on Wi-Fi / unmetered networks (default on - recordings can be big). */
    var wifiOnly: Boolean
        get() = prefs.getBoolean("wifi_only", true)
        set(v) = prefs.edit { putBoolean("wifi_only", v) }

    var chargingOnly: Boolean
        get() = prefs.getBoolean("charging_only", false)
        set(v) = prefs.edit { putBoolean("charging_only", v) }

    /**
     * "Cloud only": a new recording is kept in the app's PRIVATE storage (never in the public Music
     * folder) just until it is uploaded, then deleted from the phone. If the upload cannot finish the
     * recording stays - nothing is ever lost.
     */
    var cloudOnly: Boolean
        get() = prefs.getBoolean("cloud_only", false)
        set(v) = prefs.edit { putBoolean("cloud_only", v) }

    var lastSuccessAt: Long
        get() = prefs.getLong("last_success_at", 0L)
        set(v) = prefs.edit { putLong("last_success_at", v) }

    var lastError: String
        get() = prefs.getString("last_error", "") ?: ""
        set(v) = prefs.edit { putString("last_error", v) }

    /** MODE_FOLDER (a folder picked in Android's file picker) or MODE_DRIVE (direct upload after Google sign-in). */
    var mode: String
        get() = prefs.getString("mode", MODE_FOLDER) ?: MODE_FOLDER
        set(v) = prefs.edit { putString("mode", v) }

    /** MODE_DRIVE: the person has allowed Ashu Phone to put files in their Google Drive. */
    var driveConnected: Boolean
        get() = prefs.getBoolean("drive_connected", false)
        set(v) = prefs.edit { putBoolean("drive_connected", v) }

    /** MODE_DRIVE: id of the "Ashu Phone Recordings" folder in the person's Drive (cached). */
    var driveFolderId: String?
        get() = prefs.getString("drive_folder_id", null)
        set(v) = prefs.edit { if (v == null) remove("drive_folder_id") else putString("drive_folder_id", v) }

    /** The feature is on AND there is somewhere to put files. */
    val isActive: Boolean
        get() = enabled && (if (mode == MODE_DRIVE) driveConnected else !folderUri.isNullOrBlank())

    companion object {
        private const val PREFS_NAME = "ashu_cloud_backup_prefs"
        const val MODE_FOLDER = "folder"
        const val MODE_DRIVE = "drive"
    }
}
