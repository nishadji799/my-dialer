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
package com.ashudialer.app.telecom

import android.content.Context
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.telephony.TelephonyManager
import androidx.core.content.edit

/** One SIM (phone account) and what is known about its voicemail number. */
data class VoicemailSim(
    val handle: PhoneAccountHandle?,
    val key: String,
    val label: String,
    /** The number the SIM / carrier reports, if any. */
    val detected: String?,
    /** A number the person typed in for this SIM, if any. */
    val custom: String?
) {
    /** What would be dialled. */
    val effective: String? get() = custom?.takeIf { it.isNotBlank() } ?: detected?.takeIf { it.isNotBlank() }
}

/**
 * Voicemail the way other dialers do it: per SIM, using the number the carrier stored on the SIM, and a
 * number the person can type in themselves when the carrier did not set one (or set a wrong one).
 */
object VoicemailHelper {

    private const val PREFS = "ashu_voicemail"

    fun sims(context: Context): List<VoicemailSim> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val telecom = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
        val baseTm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        val handles = try {
            telecom?.callCapablePhoneAccounts.orEmpty()
        } catch (_: SecurityException) {
            emptyList()
        }
        if (handles.isEmpty()) {
            val detected = runCatching { baseTm?.voiceMailNumber }.getOrNull()
            return listOf(VoicemailSim(null, "default", "Default SIM", detected, prefs.getString("vm_default", null)))
        }
        return handles.mapIndexed { index, handle ->
            val key = handle.id ?: "sim$index"
            val label = runCatching { telecom?.getPhoneAccount(handle)?.label?.toString() }.getOrNull()
                ?.takeIf { it.isNotBlank() } ?: "SIM ${index + 1}"
            val detected = runCatching { baseTm?.createForPhoneAccountHandle(handle)?.voiceMailNumber }.getOrNull()
                ?: runCatching { baseTm?.voiceMailNumber }.getOrNull().takeIf { handles.size == 1 }
            VoicemailSim(handle, key, label, detected?.takeIf { it.isNotBlank() }, prefs.getString("vm_$key", null))
        }
    }

    fun saveCustom(context: Context, sim: VoicemailSim, number: String?) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            val k = "vm_${sim.key}"
            if (number.isNullOrBlank()) remove(k) else putString(k, number.trim())
        }
    }

    /** Dials this SIM's voicemail. False when there is no number to dial yet. */
    fun call(context: Context, sim: VoicemailSim): Boolean {
        val number = sim.effective ?: return false
        return try {
            DialerPermissions.placeCall(context, number, sim.handle)
            true
        } catch (_: Exception) {
            false
        }
    }

    /** Long-press 1 on the dial pad: the first SIM that has a number. False means "open the Voicemail screen". */
    fun callQuick(context: Context): Boolean {
        val all = sims(context)
        val sim = all.firstOrNull { it.effective != null } ?: return false
        return call(context, sim)
    }
}
