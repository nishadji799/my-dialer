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
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Tasks
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Direct upload to the person's own Google Drive (optional "Google account" backup mode).
 *
 * Uses the narrow `drive.file` permission: Ashu Phone can only see and touch files IT created, never the
 * rest of the person's Drive. Files go into one folder, "Ashu Phone Recordings", named exactly like the
 * recording (contact + date + time), so they are easy to find.
 *
 * One-time developer setup (not something the person using the app has to do): the Google Drive API must be
 * enabled for the Google Cloud project behind this app's Firebase project. See docs/DRIVE_BACKUP.md.
 */
object DriveRestClient {

    const val SCOPE = "https://www.googleapis.com/auth/drive.file"
    const val FOLDER_NAME = "Ashu Phone Recordings"
    private const val API = "https://www.googleapis.com/drive/v3"
    private const val UPLOAD = "https://www.googleapis.com/upload/drive/v3/files"

    fun authorizationRequest(): AuthorizationRequest =
        AuthorizationRequest.builder().setRequestedScopes(listOf(Scope(SCOPE))).build()

    sealed class Token {
        data class Ok(val value: String) : Token()
        object NeedsConsent : Token()
        data class Failed(val message: String) : Token()
    }

    /** Asks Google for an access token. Blocking - call it from a background thread. */
    fun accessToken(context: Context): Token = try {
        val result = Tasks.await(
            Identity.getAuthorizationClient(context.applicationContext).authorize(authorizationRequest()),
            30, TimeUnit.SECONDS
        )
        val token = result.accessToken
        when {
            result.hasResolution() -> Token.NeedsConsent
            !token.isNullOrBlank() -> Token.Ok(token)
            else -> Token.Failed("Google did not return an access token")
        }
    } catch (e: Exception) {
        Token.Failed(e.message ?: "Google sign-in problem")
    }

    private class Http(val code: Int, val text: String, val location: String?)

    private fun call(
        method: String,
        url: String,
        token: String?,
        body: ByteArray? = null,
        contentType: String? = null,
        headers: Map<String, String> = emptyMap()
    ): Http {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.connectTimeout = 20_000
            conn.readTimeout = 60_000
            if (token != null) conn.setRequestProperty("Authorization", "Bearer $token")
            headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
            if (body != null) {
                conn.doOutput = true
                if (contentType != null) conn.setRequestProperty("Content-Type", contentType)
                conn.setFixedLengthStreamingMode(body.size)
                conn.outputStream.use { it.write(body) }
            }
            val code = conn.responseCode
            val stream = if (code >= 400) conn.errorStream else conn.inputStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            return Http(code, text, conn.getHeaderField("Location"))
        } finally {
            conn.disconnect()
        }
    }

    private fun explain(h: Http): CopyResult {
        val t = h.text
        return when {
            h.code == 401 -> CopyResult(false, true, "Google sign-in expired - reconnect Drive in recording settings")
            h.code == 403 && (t.contains("accessNotConfigured") || t.contains("has not been used in project") || t.contains("SERVICE_DISABLED")) ->
                CopyResult(false, false, "The Google Drive API is not enabled for this app yet (developer setup - see docs/DRIVE_BACKUP.md)")
            h.code == 403 && t.contains("storageQuotaExceeded") -> CopyResult(false, false, "Your Google Drive is full")
            h.code in 500..599 || h.code == 429 -> CopyResult(false, true, "Google Drive is busy (error ${h.code})")
            else -> CopyResult(false, false, "Google Drive refused the upload (error ${h.code})")
        }
    }

    /** Finds or creates the "Ashu Phone Recordings" folder. Returns its id, or a failure. */
    private fun ensureFolder(token: String, prefs: CloudPrefs): Pair<String?, CopyResult?> {
        prefs.driveFolderId?.let { id ->
            val r = call("GET", "$API/files/$id?fields=id,trashed", token)
            if (r.code == 200 && !r.text.contains("\"trashed\": true") && !r.text.contains("\"trashed\":true")) return id to null
            prefs.driveFolderId = null
        }
        val q = URLEncoder.encode("name='$FOLDER_NAME' and mimeType='application/vnd.google-apps.folder' and trashed=false", "UTF-8")
        val found = call("GET", "$API/files?q=$q&fields=files(id)&spaces=drive", token)
        if (found.code != 200) return null to explain(found)
        val existing = JSONObject(found.text).optJSONArray("files")?.optJSONObject(0)?.optString("id")
        if (!existing.isNullOrBlank()) {
            prefs.driveFolderId = existing
            return existing to null
        }
        val meta = JSONObject().put("name", FOLDER_NAME).put("mimeType", "application/vnd.google-apps.folder").toString()
        val created = call("POST", "$API/files?fields=id", token, meta.toByteArray(), "application/json; charset=UTF-8")
        if (created.code !in 200..299) return null to explain(created)
        val id = JSONObject(created.text).optString("id")
        if (id.isBlank()) return null to CopyResult(false, true, "Google Drive did not create the folder")
        prefs.driveFolderId = id
        return id to null
    }

    /** Uploads one file (resumable upload: safe for large recordings). Blocking. */
    fun upload(context: Context, name: String, mime: String, size: Long, open: () -> InputStream?): CopyResult {
        val prefs = CloudPrefs(context)
        val token = when (val t = accessToken(context)) {
            is Token.Ok -> t.value
            Token.NeedsConsent -> return CopyResult(false, false, "Google Drive needs your permission again - reconnect it in recording settings")
            is Token.Failed -> return CopyResult(false, true, t.message)
        }
        return try {
            val (folderId, folderError) = ensureFolder(token, prefs)
            if (folderId == null) return folderError ?: CopyResult(false, true, "Could not prepare the Drive folder")

            val meta = JSONObject().put("name", name).put("parents", JSONArray().put(folderId)).toString()
            val start = call(
                "POST", "$UPLOAD?uploadType=resumable&fields=id", token, meta.toByteArray(), "application/json; charset=UTF-8",
                mapOf("X-Upload-Content-Type" to mime, "X-Upload-Content-Length" to size.toString())
            )
            if (start.code == 404) {
                prefs.driveFolderId = null
                return CopyResult(false, true, "The Drive folder was removed - it will be created again")
            }
            if (start.code !in 200..299) return explain(start)
            val session = start.location ?: return CopyResult(false, true, "Google Drive did not start the upload")

            val input = open() ?: return CopyResult(false, false, "Cannot read the recording")
            val conn = URL(session).openConnection() as HttpURLConnection
            try {
                conn.requestMethod = "PUT"
                conn.doOutput = true
                conn.connectTimeout = 20_000
                conn.readTimeout = 120_000
                conn.setRequestProperty("Content-Type", mime)
                conn.setFixedLengthStreamingMode(size)
                input.use { src -> conn.outputStream.use { out -> src.copyTo(out, 64 * 1024) } }
                val code = conn.responseCode
                val text = (if (code >= 400) conn.errorStream else conn.inputStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
                if (code !in 200..299) return explain(Http(code, text, null))
                val id = JSONObject(text).optString("id")
                CopyResult(true, false, "", if (id.isNotBlank()) "https://drive.google.com/file/d/$id/view" else "")
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            CopyResult(false, true, e.message ?: "The upload was interrupted")
        }
    }
}
