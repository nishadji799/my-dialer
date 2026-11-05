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
package com.ashudialer.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "pixel_dialer_settings")

data class AppSettings(
    // Off by default: this app cannot reliably capture the other side of a
    // call on a normal, non-rooted phone (see RecordingGuideScreen). Showing
    // the record button out of the box, before the person has read that
    // explanation, sets an expectation the app usually can't meet. The
    // person now has to deliberately turn this on from Settings, which is
    // exactly where the guide/explanation lives.
    val callRecordingEnabled: Boolean = false,
    val autoRecordAll: Boolean = false,
    val announceRecording: Boolean = false,
    val cloudBackupEnabled: Boolean = false,

    // Sub-toggles under callRecordingEnabled, for WhatsApp/Telegram VoIP call recording
    // specifically (see AppCallNotificationListenerService in the :appcalls module). Off by
    // default even once callRecordingEnabled is on: this path needs its own Shizuku grant and a
    // separate Notification Access permission, so it shouldn't silently activate the first time
    // someone turns on native call recording.
    val recordWhatsAppCallsEnabled: Boolean = false,
    val recordTelegramCallsEnabled: Boolean = false,

    val myPhoneNumber: String = "",


    val ledFlashForAlerts: Boolean = false,
    val spamProtectionEnabled: Boolean = true,
    val silenceUnknownCallers: Boolean = false,
    val flagInternationalNumbers: Boolean = true,

    val vibrateOnButtonPress: Boolean = true,
    // v5: a short buzz the moment the other side answers. Many people hold the phone away from
    // their ear while it rings; this is the classic "it connected" signal.
    val vibrateOnConnect: Boolean = true,
    // v5: auto-redial. Disabled by default.
    val autoRedialEnabled: Boolean = false,
    val autoRedialAttempts: Int = 3,
    val autoRedialDelaySeconds: Int = 5,


    val alwaysFullScreenIncoming: Boolean = false,

    val keepCallsInNotifications: Boolean = false,

    val backEndsCall: Boolean = false,

    val disableProximitySensor: Boolean = false,


    val showContactThumbnails: Boolean = true,
    val showPhoneNumbers: Boolean = false,
    val useRelativeDate: Boolean = true,
    // Recents: fold every call to the same number on the same calendar day into one
    // row (with a count), even when other numbers were called in between. Off by
    // default so existing behaviour is unchanged; opt in from Settings.
    val groupRecentsByDay: Boolean = false,


    val showSearchBar: Boolean = true,

    val fontSizeIndex: Int = 1,

    // Which look the full-screen incoming-call UI uses: "clean" (default), "center" or "swipe"
    // (see IncomingCallScreen.kt). A value saved by an older build ("aurora", "orbit", "pulse",
    // "classic", "hyper", "ios") is mapped to the nearest current look by
    // IncomingCallStyles.normalize(), so nobody ends up with a broken screen after updating.
    // Stored as a string (not an enum) for the same DataStore-friendliness reason fontSizeIndex is
    // an Int rather than a sealed type.
    val incomingCallStyle: String = "swipe",

    // On a dark theme, draw the incoming-call screen as dark frosted glass. Off = it stays white /
    // light exactly like the reference designs, on every theme.
    val incomingCallGlass: Boolean = true,

    // The Swipe incoming-call style's slow pulsing glow behind the photo. Off = the photo
    // stays still. Clean and Center ignore this, they have no such animation.
    val incomingCallAvatarPulse: Boolean = true,

    // Frosted-glass look for the ACTIVE call screen (CallScreen, once a call connects) -
    // separate from incomingCallGlass above, which only affects the ringing screen. When true,
    // InCallActivity turns on a real background blur (Window.setBackgroundBlurRadius, Android
    // 12+) behind the window and CallScreen draws its own background at reduced opacity so that
    // blur shows through. Defaults to false (opt-in) rather than true like incomingCallGlass:
    // this drives an actual window blur rather than just picking a color scheme, is unavailable
    // below Android 12, and changes a screen that was deliberately made fully opaque earlier to
    // avoid an OEM-transition glitch - so it should be something the person turns on
    // deliberately and can turn back off, not a new default everyone gets silently.
    val inCallFrostedGlass: Boolean = false,

    // Dual-SIM outgoing calls. Empty = always show the SIM picker before
    // dialing when there's no per-number routing rule (SimRoutingScreen)
    // for that number and more than one SIM is available - previously
    // there was no picker at all on this path (see placeCallDirect's
    // fallback to `handle = null`, i.e. whatever SIM the OS silently
    // picks), which is the actual bug this setting's picker fixes. A
    // non-empty value is a PhoneAccountHandle.id: skip the picker and
    // always use that SIM for any number with no specific rule, for
    // someone who'd rather set-and-forget than be asked every call.
    val defaultSimAccountId: String = "",

    // Ask for confirmation before every outgoing call placed with more
    // than one SIM available (distinct from defaultSimAccountId's picker,
    // which only appears when there's a genuine choice to make - this can
    // stay on even once a default SIM is set, for a "confirm which SIM,
    // every time" workflow rather than "pick once, never ask again").
    val confirmSimBeforeCall: Boolean = true,

    // Visual weight of buttons/pills across the dialpad, call screen and
    // action buttons - "flat" keeps today's minimal look (no shadow), "raised"
    // adds a subtle drop shadow + slightly stronger border for a more
    // tactile, pressable look for anyone who wants buttons to look more
    // like physical buttons and less like flat labels.
    val buttonDepth: String = "flat",

    // ---------------- v4 Personalize (see ui/theme/AshuStyle.kt and PersonalizeScreen.kt) ----------
    val glassIntensityPct: Int = 72,
    val cornerScalePct: Int = 100,
    val motionScalePct: Int = 100,
    val hapticLevel: Int = 2,
    /** ARGB of the custom accent colour, 0 = use the theme's own accent. */
    val accentOverrideArgb: Long = 0L,
    val navStyle: String = "floating",
    val navLabels: String = "always",
    val avatarShape: String = "circle",
    val listDensity: String = "comfortable",
    val auroraEnabled: Boolean = true,
    val specularEnabled: Boolean = true,
    val keypadStyle: String = "glass",
    val swipeActionsEnabled: Boolean = true,
    val staggerLists: Boolean = true,

    // ---------------- v4 Recordings screen preferences (UI only - the recorder keeps its own prefs) ---
    val recordingsGroupByContact: Boolean = false,
    val recordingsSort: String = "newest",

    // ---------------- v2.0.1 bottom bar, floating dialer and dialer layout ----------------------------
    /** Bottom bar order, comma separated DialerTab names. */
    val navOrder: String = "CONTACTS,RECENT,DIALER,PROTECT,MORE",
    /** Keep the Dialer tab in the bottom bar (default). When off, the floating button is the way in. */
    val navShowDialer: Boolean = true,
    val navShowRecent: Boolean = true,
    /** The round floating button above the bar: Dialer on Recents, Recents on the other tabs. */
    val quickFab: Boolean = true,
    /** Tapping Dialer opens a floating glass window instead of switching to the dialer page. */
    val dialerFloating: Boolean = false,
    /** Contact suggestions under the number go edge to edge and bigger. */
    val dialerBoxWide: Boolean = false,
    /** classic | compact | glass */
    val dialerLayout: String = "classic",
    /** auto | system | glass - see CallNotificationHelper.callStyleMode */
    val callNotifStyle: String = "auto"
)

/** Keys of the generic Personalize / Recordings-UI preferences (stored as "p_<name>"). */
object PKey {
    const val GLASS = "glass"
    const val CORNER = "corner"
    const val MOTION = "motion"
    const val HAPTIC = "haptic"
    const val ACCENT = "accent"
    const val NAV_STYLE = "nav_style"
    const val NAV_LABELS = "nav_labels"
    const val AVATAR = "avatar"
    const val DENSITY = "density"
    const val AURORA = "aurora"
    const val SPECULAR = "specular"
    const val KEYPAD = "keypad"
    const val SWIPE = "swipe"
    const val STAGGER = "stagger"
    const val REC_GROUP = "rec_group_by_contact"
    const val REC_SORT = "rec_sort"
    const val NAV_ORDER = "nav_order"
    const val NAV_SHOW_DIALER = "nav_show_dialer"
    const val NAV_SHOW_RECENT = "nav_show_recent"
    const val QUICK_FAB = "quick_fab"
    const val DIALER_FLOATING = "dialer_floating"
    const val DIALER_WIDE = "dialer_wide"
    const val DIALER_LAYOUT = "dialer_layout"
    const val CALL_NOTIF = "call_notif"
    const val VIBRATE_CONNECT = "vibrate_on_connect"
    // v5: auto-redial - automatically retries an outgoing call that never connected (busy / no
    // answer / failed), up to N attempts with a delay between each. Off by default so it never
    // surprises the person; they opt in from Personalize > Calls.
    const val AUTO_REDIAL = "auto_redial"
    const val AUTO_REDIAL_ATTEMPTS = "auto_redial_attempts"
    const val AUTO_REDIAL_DELAY = "auto_redial_delay"
}

/** Everything the person tuned in Personalize, as one object every screen can read via LocalAshuStyle. */
fun AppSettings.toAshuStyle(): com.ashudialer.app.ui.theme.AshuStyle =
    com.ashudialer.app.ui.theme.AshuStyle(
        glassIntensity = glassIntensityPct.coerceIn(0, 100) / 100f,
        cornerScale = cornerScalePct.coerceIn(40, 180) / 100f,
        motionScale = motionScalePct.coerceIn(0, 200) / 100f,
        hapticLevel = hapticLevel.coerceIn(0, 3),
        accentOverride = if (accentOverrideArgb != 0L) androidx.compose.ui.graphics.Color(accentOverrideArgb.toInt()) else null,
        navStyle = navStyle,
        navLabels = navLabels,
        avatarShape = avatarShape,
        density = listDensity,
        aurora = auroraEnabled,
        specular = specularEnabled,
        keypadStyle = keypadStyle,
        swipeActions = swipeActionsEnabled,
        staggerLists = staggerLists
    )

class AppSettingsRepository(private val context: Context) {

    private val keyCallRecording = booleanPreferencesKey("call_recording_enabled")
    private val keyAutoRecordAll = booleanPreferencesKey("auto_record_all")
    private val keyAnnounceRecording = booleanPreferencesKey("announce_recording")
    private val keyCloudBackup = booleanPreferencesKey("cloud_backup_enabled")
    private val keyRecordWhatsAppCalls = booleanPreferencesKey("record_whatsapp_calls_enabled")
    private val keyRecordTelegramCalls = booleanPreferencesKey("record_telegram_calls_enabled")
    private val keyMyPhoneNumber = stringPreferencesKey("my_phone_number")
    private val keyLedFlash = booleanPreferencesKey("led_flash_for_alerts")
    private val keySpamProtection = booleanPreferencesKey("spam_protection_enabled")
    private val keySilenceUnknown = booleanPreferencesKey("silence_unknown_callers")
    private val keyFlagInternational = booleanPreferencesKey("flag_international_numbers")
    private val keyVibrateOnButton = booleanPreferencesKey("vibrate_on_button_press")
    private val keyFullScreenIncoming = booleanPreferencesKey("always_full_screen_incoming")
    private val keyKeepCallsInNotifications = booleanPreferencesKey("keep_calls_in_notifications")
    private val keyBackEndsCall = booleanPreferencesKey("back_ends_call")
    private val keyDisableProximity = booleanPreferencesKey("disable_proximity_sensor")
    private val keyShowThumbnails = booleanPreferencesKey("show_contact_thumbnails")
    private val keyShowPhoneNumbers = booleanPreferencesKey("show_phone_numbers")
    private val keyRelativeDate = booleanPreferencesKey("use_relative_date")
    private val keyGroupRecentsByDay = booleanPreferencesKey("group_recents_by_day")
    private val keyShowSearchBar = booleanPreferencesKey("show_search_bar")
    private val keyFontSizeIndex = androidx.datastore.preferences.core.intPreferencesKey("font_size_index")
    private val keyIncomingCallStyle = stringPreferencesKey("incoming_call_style")
    private val keyIncomingCallGlass = booleanPreferencesKey("incoming_call_glass")
    private val keyInCallFrostedGlass = booleanPreferencesKey("in_call_frosted_glass")
    private val keyIncomingCallAvatarPulse = booleanPreferencesKey("incoming_call_avatar_pulse")
    private val keyDefaultSimAccountId = stringPreferencesKey("default_sim_account_id")
    private val keyConfirmSimBeforeCall = booleanPreferencesKey("confirm_sim_before_call")
    private val keyButtonDepth = stringPreferencesKey("button_depth")
    // "Haven't talked in N days" card on Recents (see ReconnectRepository/
    // ReconnectSuggestionCard). Stores "phoneNumber|epochDay" of the last
    // dismissed suggestion - epochDay (not just the number) so dismissing
    // today doesn't permanently suppress that same contact if they come up
    // again as a fresh suggestion weeks later; see
    // MainViewModel.dismissReconnectSuggestion for how this is read back.
    private val keyDismissedReconnectSuggestion = stringPreferencesKey("dismissed_reconnect_suggestion")

    /**
     * Ticks whenever any call-recording preference changes. SharedPreferences writes made directly
     * by the recording screens don't touch DataStore, so without this the Settings / in-call screens
     * would keep showing a stale master switch until the next unrelated DataStore write.
     */
    private val recordingPrefsTicks: Flow<Long> = kotlinx.coroutines.flow.callbackFlow {
        val sp = context.getSharedPreferences("ashu_call_recording_prefs", Context.MODE_PRIVATE)
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            trySend(System.nanoTime())
        }
        sp.registerOnSharedPreferenceChangeListener(listener)
        trySend(0L)
        awaitClose { sp.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    val settingsFlow: Flow<AppSettings> = kotlinx.coroutines.flow.combine(
        context.settingsDataStore.data,
        recordingPrefsTicks
    ) { prefs, _ -> prefs }.map { prefs ->
        AppSettings(
            callRecordingEnabled = recordingPrefs.callRecordingEnabled,
            autoRecordAll = recordingPrefs.autoRecordIncoming || recordingPrefs.autoRecordOutgoing,
            announceRecording = prefs[keyAnnounceRecording] ?: false,
            cloudBackupEnabled = prefs[keyCloudBackup] ?: false,
            recordWhatsAppCallsEnabled = recordingPrefs.recordWhatsApp,
            recordTelegramCallsEnabled = recordingPrefs.recordTelegram,
            myPhoneNumber = prefs[keyMyPhoneNumber] ?: "",
            ledFlashForAlerts = prefs[keyLedFlash] ?: false,
            spamProtectionEnabled = prefs[keySpamProtection] ?: true,
            silenceUnknownCallers = prefs[keySilenceUnknown] ?: false,
            flagInternationalNumbers = prefs[keyFlagInternational] ?: true,
            vibrateOnButtonPress = prefs[keyVibrateOnButton] ?: true,
            alwaysFullScreenIncoming = prefs[keyFullScreenIncoming] ?: false,
            keepCallsInNotifications = prefs[keyKeepCallsInNotifications] ?: false,
            backEndsCall = prefs[keyBackEndsCall] ?: false,
            disableProximitySensor = prefs[keyDisableProximity] ?: false,
            showContactThumbnails = prefs[keyShowThumbnails] ?: true,
            showPhoneNumbers = prefs[keyShowPhoneNumbers] ?: false,
            useRelativeDate = prefs[keyRelativeDate] ?: true,
            groupRecentsByDay = prefs[keyGroupRecentsByDay] ?: false,
            showSearchBar = prefs[keyShowSearchBar] ?: true,
            fontSizeIndex = prefs[keyFontSizeIndex] ?: 1,
            incomingCallStyle = com.ashudialer.app.ui.screens.IncomingCallStyles.normalize(prefs[keyIncomingCallStyle]),
            incomingCallGlass = prefs[keyIncomingCallGlass] ?: true,
            incomingCallAvatarPulse = prefs[keyIncomingCallAvatarPulse] ?: true,
            inCallFrostedGlass = prefs[keyInCallFrostedGlass] ?: false,
            defaultSimAccountId = prefs[keyDefaultSimAccountId] ?: "",
            confirmSimBeforeCall = prefs[keyConfirmSimBeforeCall] ?: true,
            buttonDepth = prefs[keyButtonDepth] ?: "flat",
            glassIntensityPct = prefs[pInt(PKey.GLASS)] ?: 72,
            cornerScalePct = prefs[pInt(PKey.CORNER)] ?: 100,
            motionScalePct = prefs[pInt(PKey.MOTION)] ?: 100,
            hapticLevel = prefs[pInt(PKey.HAPTIC)] ?: 2,
            accentOverrideArgb = prefs[pLong(PKey.ACCENT)] ?: 0L,
            navStyle = prefs[pStr(PKey.NAV_STYLE)] ?: "floating",
            navLabels = prefs[pStr(PKey.NAV_LABELS)] ?: "always",
            avatarShape = prefs[pStr(PKey.AVATAR)] ?: "circle",
            listDensity = prefs[pStr(PKey.DENSITY)] ?: "comfortable",
            auroraEnabled = prefs[pBool(PKey.AURORA)] ?: true,
            specularEnabled = prefs[pBool(PKey.SPECULAR)] ?: true,
            keypadStyle = prefs[pStr(PKey.KEYPAD)] ?: "glass",
            swipeActionsEnabled = prefs[pBool(PKey.SWIPE)] ?: true,
            staggerLists = prefs[pBool(PKey.STAGGER)] ?: true,
            recordingsGroupByContact = prefs[pBool(PKey.REC_GROUP)] ?: false,
            recordingsSort = prefs[pStr(PKey.REC_SORT)] ?: "newest",
            navOrder = prefs[pStr(PKey.NAV_ORDER)] ?: "CONTACTS,RECENT,DIALER,PROTECT,MORE",
            navShowDialer = prefs[pBool(PKey.NAV_SHOW_DIALER)] ?: true,
            navShowRecent = prefs[pBool(PKey.NAV_SHOW_RECENT)] ?: true,
            quickFab = prefs[pBool(PKey.QUICK_FAB)] ?: true,
            dialerFloating = prefs[pBool(PKey.DIALER_FLOATING)] ?: false,
            dialerBoxWide = prefs[pBool(PKey.DIALER_WIDE)] ?: false,
            dialerLayout = prefs[pStr(PKey.DIALER_LAYOUT)] ?: "classic",
            callNotifStyle = prefs[pStr(PKey.CALL_NOTIF)] ?: "auto",
            vibrateOnConnect = prefs[pBool(PKey.VIBRATE_CONNECT)] ?: true,
            autoRedialEnabled = prefs[pBool(PKey.AUTO_REDIAL)] ?: false,
            autoRedialAttempts = prefs[pInt(PKey.AUTO_REDIAL_ATTEMPTS)] ?: 3,
            autoRedialDelaySeconds = prefs[pInt(PKey.AUTO_REDIAL_DELAY)] ?: 5,
        )
    }

    private fun pInt(name: String) = androidx.datastore.preferences.core.intPreferencesKey("p_$name")
    private fun pLong(name: String) = androidx.datastore.preferences.core.longPreferencesKey("p_$name")
    private fun pStr(name: String) = stringPreferencesKey("p_$name")
    private fun pBool(name: String) = booleanPreferencesKey("p_$name")

    suspend fun setPersonalizeInt(name: String, value: Int) {
        context.settingsDataStore.edit { it[pInt(name)] = value }
    }

    suspend fun setPersonalizeLong(name: String, value: Long) {
        context.settingsDataStore.edit { it[pLong(name)] = value }
    }

    suspend fun setPersonalizeString(name: String, value: String) {
        context.settingsDataStore.edit { it[pStr(name)] = value }
    }

    suspend fun setPersonalizeBool(name: String, value: Boolean) {
        context.settingsDataStore.edit { it[pBool(name)] = value }
    }

    /** Puts every Personalize control back to its default (the theme itself is not touched). */
    suspend fun resetPersonalization() {
        context.settingsDataStore.edit { prefs ->
            val mine = prefs.asMap().keys.filter { it.name.startsWith("p_") && !it.name.startsWith("p_rec_") }
            mine.forEach { prefs.remove(it) }
        }
    }

    // The recorder itself (phone-state receiver + foreground service in :appcalls) reads its
    // settings synchronously from RecordingPrefs, because it can't await a DataStore read at the
    // instant a call changes state. These four are still exposed here so the Settings screen and
    // the in-call screen (which observe settingsFlow) keep working - every write is mirrored into
    // RecordingPrefs so the two can never disagree.
    private val recordingPrefs get() = com.ashudialer.app.appcalls.recording.RecordingPrefs(context)

    suspend fun setCallRecordingEnabled(enabled: Boolean) {
        recordingPrefs.callRecordingEnabled = enabled
        context.settingsDataStore.edit { it[keyCallRecording] = enabled }
    }

    suspend fun setRecordWhatsAppCallsEnabled(enabled: Boolean) {
        recordingPrefs.recordWhatsApp = enabled
        context.settingsDataStore.edit { it[keyRecordWhatsAppCalls] = enabled }
    }

    suspend fun setRecordTelegramCallsEnabled(enabled: Boolean) {
        recordingPrefs.recordTelegram = enabled
        context.settingsDataStore.edit { it[keyRecordTelegramCalls] = enabled }
    }

    suspend fun setAutoRecordAll(enabled: Boolean) {
        recordingPrefs.autoRecordIncoming = enabled
        recordingPrefs.autoRecordOutgoing = enabled
        context.settingsDataStore.edit { it[keyAutoRecordAll] = enabled }
    }

    suspend fun setAnnounceRecording(enabled: Boolean) {
        context.settingsDataStore.edit { it[keyAnnounceRecording] = enabled }
    }

    suspend fun setCloudBackupEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[keyCloudBackup] = enabled }
    }

    suspend fun setMyPhoneNumber(number: String) {
        context.settingsDataStore.edit { it[keyMyPhoneNumber] = number }
    }

    suspend fun setLedFlashForAlerts(enabled: Boolean) {
        context.settingsDataStore.edit { it[keyLedFlash] = enabled }
    }

    suspend fun setSpamProtectionEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[keySpamProtection] = enabled }
    }

    suspend fun setSilenceUnknownCallers(enabled: Boolean) {
        context.settingsDataStore.edit { it[keySilenceUnknown] = enabled }
    }

    suspend fun setFlagInternationalNumbers(enabled: Boolean) {
        context.settingsDataStore.edit { it[keyFlagInternational] = enabled }
    }

    suspend fun setVibrateOnButtonPress(enabled: Boolean) {
        context.settingsDataStore.edit { it[keyVibrateOnButton] = enabled }
    }

    suspend fun setAlwaysFullScreenIncoming(enabled: Boolean) {
        context.settingsDataStore.edit { it[keyFullScreenIncoming] = enabled }
    }

    suspend fun setKeepCallsInNotifications(enabled: Boolean) {
        context.settingsDataStore.edit { it[keyKeepCallsInNotifications] = enabled }
    }

    suspend fun setBackEndsCall(enabled: Boolean) {
        context.settingsDataStore.edit { it[keyBackEndsCall] = enabled }
    }

    suspend fun setDisableProximitySensor(enabled: Boolean) {
        context.settingsDataStore.edit { it[keyDisableProximity] = enabled }
    }

    // v5: auto-redial persistence.
    suspend fun setAutoRedialEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[pBool(PKey.AUTO_REDIAL)] = enabled }
    }

    suspend fun setAutoRedialAttempts(attempts: Int) {
        context.settingsDataStore.edit { it[pInt(PKey.AUTO_REDIAL_ATTEMPTS)] = attempts.coerceIn(1, 10) }
    }

    suspend fun setAutoRedialDelaySeconds(seconds: Int) {
        context.settingsDataStore.edit { it[pInt(PKey.AUTO_REDIAL_DELAY)] = seconds.coerceIn(1, 60) }
    }

    suspend fun setShowContactThumbnails(enabled: Boolean) {
        context.settingsDataStore.edit { it[keyShowThumbnails] = enabled }
    }

    suspend fun setShowPhoneNumbers(enabled: Boolean) {
        context.settingsDataStore.edit { it[keyShowPhoneNumbers] = enabled }
    }

    suspend fun setUseRelativeDate(enabled: Boolean) {
        context.settingsDataStore.edit { it[keyRelativeDate] = enabled }
    }

    suspend fun setGroupRecentsByDay(enabled: Boolean) {
        context.settingsDataStore.edit { it[keyGroupRecentsByDay] = enabled }
    }

    suspend fun setShowSearchBar(enabled: Boolean) {
        context.settingsDataStore.edit { it[keyShowSearchBar] = enabled }
    }

    suspend fun setFontSizeIndex(index: Int) {
        context.settingsDataStore.edit { it[keyFontSizeIndex] = index }
    }

    suspend fun setIncomingCallStyle(style: String) {
        context.settingsDataStore.edit { it[keyIncomingCallStyle] = style }
    }

    suspend fun setIncomingCallGlass(enabled: Boolean) {
        context.settingsDataStore.edit { it[keyIncomingCallGlass] = enabled }
    }

    suspend fun setIncomingCallAvatarPulse(enabled: Boolean) {
        context.settingsDataStore.edit { it[keyIncomingCallAvatarPulse] = enabled }
    }

    suspend fun setInCallFrostedGlass(enabled: Boolean) {
        context.settingsDataStore.edit { it[keyInCallFrostedGlass] = enabled }
    }

    suspend fun setDefaultSimAccountId(id: String) {
        context.settingsDataStore.edit { it[keyDefaultSimAccountId] = id }
    }

    suspend fun setConfirmSimBeforeCall(enabled: Boolean) {
        context.settingsDataStore.edit { it[keyConfirmSimBeforeCall] = enabled }
    }

    suspend fun setButtonDepth(depth: String) {
        context.settingsDataStore.edit { it[keyButtonDepth] = depth }
    }

    /** "phoneNumber|epochDay" of the last dismissed reconnect suggestion, or null if none yet. */
    suspend fun dismissedReconnectSuggestion(): String? =
        context.settingsDataStore.data.first()[keyDismissedReconnectSuggestion]

    suspend fun setDismissedReconnectSuggestion(phoneNumber: String, epochDay: Long) {
        context.settingsDataStore.edit { it[keyDismissedReconnectSuggestion] = "$phoneNumber|$epochDay" }
    }
}
