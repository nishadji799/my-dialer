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

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.compose.ui.graphics.toArgb
import com.ashudialer.app.AshuDialerApp
import com.ashudialer.app.R


object CallNotificationHelper {

    const val CHANNEL_ID = "incoming_call_channel"
    private const val NOTIFICATION_ID = 7001

    private var lastCallerName: String = ""
    private var lastIsIncoming: Boolean = false

    private var ongoingCallStartMillis: Long = 0L

    /**
     * "auto" = the system's own call notification (which Android can show as a call chip in the status bar)
     * on every phone except Xiaomi / Redmi / POCO, "system" = always that, "glass" = always the themed card.
     * Set by PixelInCallService from Personalize > Calls.
     */
    @Volatile
    var callStyleMode: String = "auto"

    // v4 FIX ("seconds start running before I even pick up"): the ongoing-call notification used to
    // start its chronometer the moment an OUTGOING call began dialing, so the timer ran while the
    // other phone was still ringing. The timer now starts only once the call is really connected
    // (Call.STATE_ACTIVE). These two remember the state so refreshOngoing() re-posts the same look.
    private var lastCallConnected: Boolean = false
    private var lastCallOnHold: Boolean = false

    /**
     * Whether the calling notification should use its light or dark
     * background/text - previously this notification used one hardcoded
     * dark teal-glass look (bg_notification_glass.xml) regardless of the
     * theme active inside the app, so on any light theme it looked like a
     * mismatched dark patch in an otherwise light shade/lock screen. Reads
     * the same synchronous "last known theme" cache InCallActivity's own
     * window-background fix already uses (see ThemePreference's class
     * doc) rather than DataStore directly, since building a notification
     * is a synchronous call path with no natural place to await a Flow,
     * and resolves "auto" through the same resolveThemeId() function
     * AshuDialerTheme and InCallActivity's window-background placeholder
     * both use, so this can never disagree with what the in-app UI is
     * actually showing.
     */
    private fun themeIsDark(context: Context): Boolean {
        val themeId = com.ashudialer.app.data.ThemePreference.peekLastKnownThemeId(context)
        val nightMode = context.resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK
        val systemIsDark = nightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES
        val resolvedId = com.ashudialer.app.ui.theme.resolveThemeId(themeId, systemIsDark)
        return com.ashudialer.app.ui.theme.paletteById(resolvedId).isDark
    }

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)

        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Incoming calls",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Shows the full-screen incoming call UI"
                setSound(null, null)
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            manager.createNotificationChannel(channel)
        }
    }

    fun showIncomingCallNotification(context: Context, callerName: String, callerNumber: String, ledFlashEnabled: Boolean = false) {
        try {
            lastCallerName = callerName
            lastIsIncoming = true
            // A genuinely new ring starting - reset so this new call gets
            // its own single setFullScreenIntent wake request. See the
            // long comment in postIncomingFrame for the full reasoning.
            fullScreenIntentAlreadySentForThisRing = false

            // Post the first frame immediately. The old implementation waited
            // for the pulse loop's first 500 ms tick, which is too fragile for
            // lock-screen/full-screen call presentation on some OEM builds.
            postIncomingFrame(context, callerName, bright = true)
            startAvatarPulse(context, callerName)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val manager = context.getSystemService(NotificationManager::class.java)
                if (manager != null && !manager.canUseFullScreenIntent()) {
                    android.util.Log.w(
                        "CallNotificationHelper",
                        "USE_FULL_SCREEN_INTENT is disabled; lock-screen incoming calls may only show as a heads-up notification."
                    )
                }
            }
            if (!androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()) {
                android.util.Log.w(
                    "CallNotificationHelper",
                    "Notifications are disabled; the app cannot present the lock-screen incoming-call notification."
                )
            }
            if (ledFlashEnabled) flashLed(context)
        } catch (e: Exception) {
            android.util.Log.e("CallNotificationHelper", "Failed to show incoming call notification", e)
        }
    }

    private val pulseHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var pulseRunnable: Runnable? = null
    private var pulseBright = false


    private fun startAvatarPulse(context: Context, callerName: String) {
        stopAvatarPulse()
        pulseBright = false
        pulseRunnable = object : Runnable {
            override fun run() {
                if (!lastIsIncoming) return
                // Skip re-posting while the real call screen (InCallActivity)
                // is what's actually on screen - isCallScreenVisible is kept
                // in sync by InCallActivity.onResume/onPause. Without this
                // check this loop re-notified every 500ms regardless, so the
                // heads-up notification kept re-appearing as an overlay on
                // top of the full-screen call UI instead of staying
                // dismissed once the person could already see the call.
                if (!isCallScreenVisible) {
                    postIncomingFrame(context, callerName, pulseBright)
                }
                pulseBright = !pulseBright
                pulseHandler.postDelayed(this, 500)
            }
        }
        pulseHandler.post(pulseRunnable!!)
    }

    private fun stopAvatarPulse() {
        pulseRunnable?.let { pulseHandler.removeCallbacks(it) }
        pulseRunnable = null
        // Ring is over (answered, declined, or timed out) - clear so the
        // next incoming call starts its own fresh single wake request
        // rather than inheriting this one's "already sent" state.
        fullScreenIntentAlreadySentForThisRing = false
    }

    private val ledHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var ledRunnable: Runnable? = null
    private var ledOn = false


    private fun flashLed(context: Context) {
        stopLedFlash()
        if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA)
            != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? android.hardware.camera2.CameraManager
                ?: return
            val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
                cameraManager.getCameraCharacteristics(id)
                    .get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: return

            ledOn = false
            val startedAtMillis = System.currentTimeMillis()
            ledRunnable = object : Runnable {
                override fun run() {
                    if (!lastIsIncoming || System.currentTimeMillis() - startedAtMillis > 30_000) {
                        stopLedFlash(cameraManager, cameraId)
                        return
                    }
                    try {
                        ledOn = !ledOn
                        cameraManager.setTorchMode(cameraId, ledOn)
                    } catch (e: Exception) {


                        stopLedFlash(cameraManager, cameraId)
                        return
                    }
                    ledHandler.postDelayed(this, 900)
                }
            }
            ledHandler.post(ledRunnable!!)
        } catch (e: Exception) {
            android.util.Log.w("CallNotificationHelper", "LED flash unavailable on this device", e)
        }
    }

    private fun stopLedFlash(cameraManager: android.hardware.camera2.CameraManager? = null, cameraId: String? = null) {
        ledRunnable?.let { ledHandler.removeCallbacks(it) }
        ledRunnable = null
        if (ledOn && cameraManager != null && cameraId != null) {
            try {
                cameraManager.setTorchMode(cameraId, false)
            } catch (e: Exception) {

            }
        }
        ledOn = false
    }

    private fun postIncomingFrame(context: Context, callerName: String, bright: Boolean) {
        val views = buildGlassLayout(context, callerName, statusText = "Incoming call", incoming = true)
        views.setInt(
            R.id.notif_avatar, "setBackgroundResource",
            if (bright) R.drawable.bg_notification_avatar_ring_bright else R.drawable.bg_notification_avatar_ring_dim
        )

        val fullScreenIntent = Intent(context, InCallActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_NO_USER_ACTION
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            context, 0, fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // DEEP FIX for "screen doesn't wake for a locked-screen incoming
        // call": this function is called every ~500ms for the whole
        // duration of ringing, by startAvatarPulse below, purely to flip
        // the avatar ring drawable between bright/dim for a breathing
        // effect. Every one of those calls used to also re-attach
        // setFullScreenIntent(fullScreenPendingIntent, true) to a freshly
        // notify()'d Notification object - so a screen-off incoming call
        // wasn't asking Android to wake the screen once, it was asking
        // ~2 times a second for as long as the phone rang.
        //
        // That repeated re-declaration is what actually broke the wake:
        // some OEM notification stacks (this exact pattern has been
        // observed causing missed wakes on MIUI/Xiaomi builds, which is
        // this app's primary test target) treat a fullScreenIntent that
        // gets re-declared on every update of the *same* notification as
        // "already handled/shown", and silently stop actually firing the
        // intent (or stop actually turning the screen on) after the first
        // attempt - so if that very first attempt lost a timing race
        // against the screen genuinely being off (which full-screen-intent
        // wake is not always instant about), every subsequent one from the
        // pulse loop was a no-op, and the screen just never came on.
        // Android's own NotificationManager also throttles/collapses
        // full-screen-intent redeliveries on the *same* notification more
        // aggressively than it throttles a plain content update, which
        // compounds the same problem even on stock/AOSP-ish builds.
        //
        // The actual fix: only the very first post of a given ring
        // (isFirstFrameOfThisRing) attaches setFullScreenIntent at all.
        // Every pulse update after that reuses the exact same Notification
        // fields except the avatar drawable - notify()'ing an update to an
        // already-shown notification is enough to refresh what's on
        // screen/lock-screen, and does NOT require (or benefit from)
        // re-asking the system to wake the screen and launch the
        // full-screen UI a second time. This mirrors how every stock
        // dialer actually behaves: one wake request per ring, not one
        // every pulse tick.
        val isFirstFrameOfThisRing = !fullScreenIntentAlreadySentForThisRing
        fullScreenIntentAlreadySentForThisRing = true

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_call_notification)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setContentIntent(fullScreenPendingIntent)
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .apply {
                if (isFirstFrameOfThisRing) {
                    setFullScreenIntent(fullScreenPendingIntent, true)
                }
            }

        val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val caller = androidx.core.app.Person.Builder()
                .setName(callerName)
                .setImportant(true)
                .build()
            builder
                .setStyle(
                    NotificationCompat.CallStyle.forIncomingCall(
                        caller,
                        CallActionReceiver.declineIntent(context),
                        CallActionReceiver.answerIntent(context)
                    )
                )
                .setContentTitle(callerName)
                .setContentText("Incoming call")
                .build()
        } else {
            builder
                .setStyle(NotificationCompat.DecoratedCustomViewStyle())
                .setCustomContentView(views)
                .setCustomBigContentView(views)
                .build()
        }

        notify(context, notification)
    }

    // Tracks whether setFullScreenIntent has already been attached for the
    // ring currently in progress - see the long comment in postIncomingFrame
    // for why this must only happen once per ring, not once per pulse tick.
    // Reset in showIncomingCallNotification (a genuinely new incoming call
    // starting) and in stopAvatarPulse (the ring ending, whichever way).
    private var fullScreenIntentAlreadySentForThisRing: Boolean = false


    var isCallScreenVisible: Boolean = false

    /**
     * @param connected false while an outgoing call is still dialing / ringing at the other end: the
     *  notification then says "Calling..." and shows NO running timer. true once the call is answered.
     * @param connectTimeMillis Call.Details.connectTimeMillis (0 = unknown, now is used instead).
     * @param onHold the call is on hold: the label changes and the live timer is hidden (the call's
     *  start time is kept, so the timer carries on from the right value after resuming).
     */
    fun showOngoingCallNotification(
        context: Context,
        callerName: String,
        connected: Boolean = true,
        connectTimeMillis: Long = 0L,
        onHold: Boolean = false
    ) {
        try {
            stopAvatarPulse()
            lastCallerName = callerName
            lastIsIncoming = false
            lastCallConnected = connected
            lastCallOnHold = onHold

            if (connected) {
                if (ongoingCallStartMillis == 0L) {
                    ongoingCallStartMillis =
                        if (connectTimeMillis > 0L) connectTimeMillis else System.currentTimeMillis()
                }
            } else {
                // Not answered yet: no start time, so nothing can tick.
                ongoingCallStartMillis = 0L
            }
            val statusLabel = when {
                !connected -> "Calling\u2026"
                onHold -> "On hold"
                else -> "Ongoing call"
            }

            val views = buildGlassLayout(
                context, callerName, statusText = statusLabel, incoming = false,
                timerStartMillis = if (connected && !onHold) ongoingCallStartMillis else 0L
            )
            val compactViews = buildCompactOngoingLayout(context, callerName, statusLabel)

            // Bug fix: this was missing FLAG_ACTIVITY_SINGLE_TOP and
            // FLAG_ACTIVITY_NO_USER_ACTION, unlike the matching intent for
            // the *incoming*-call notification above (which already has
            // both). Without SINGLE_TOP, tapping this ongoing-call
            // notification while InCallActivity (singleTask, already
            // showWhenLocked/turnScreenOn per the manifest) is technically
            // still alive but not literally foreground can make the system
            // re-resolve the launch instead of just resuming the existing
            // task - and on that fresh-resolve path some OEM keyguards
            // (this notification variant already special-cases Xiaomi/
            // Redmi/POCO just above) re-consult the lock screen before the
            // activity's own window flags get a chance to apply, which is
            // what produced "tapping the call notification asks to unlock"
            // even though the call itself should never require that.
            val contentIntent = Intent(context, InCallActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_NO_USER_ACTION
            }
            val pendingIntent = PendingIntent.getActivity(
                context, 1, contentIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )


            val builder = NotificationCompat.Builder(context, AshuDialerApp.CHANNEL_ONGOING_CALL)
                .setSmallIcon(R.drawable.ic_ongoing_call_notification)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                // Show the caller and the Hang up button on the lock screen, not just "Sensitive content hidden".
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOnlyAlertOnce(true)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setContentIntent(pendingIntent)
                .setOngoing(true)

            if (connected && !onHold && ongoingCallStartMillis > 0L) {
                builder.setWhen(ongoingCallStartMillis).setUsesChronometer(true).setShowWhen(true)
            } else {
                builder.setUsesChronometer(false).setShowWhen(false)
            }

            val isXiaomiFamily = Build.MANUFACTURER.equals("xiaomi", true) ||
                Build.MANUFACTURER.equals("redmi", true) ||
                Build.MANUFACTURER.equals("poco", true)

            val useSystemCallStyle = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && when (callStyleMode) {
                "system" -> true
                "glass" -> false
                else -> !isXiaomiFamily
            }
            val notification = if (!useSystemCallStyle) {
                builder
                    .setStyle(NotificationCompat.DecoratedCustomViewStyle())
                    .setCustomContentView(compactViews)
                    .setCustomBigContentView(views)
                    .setCustomHeadsUpContentView(views)
                    .setContentTitle(callerName)
                    .setContentText(statusLabel)
                    .build()
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val caller = androidx.core.app.Person.Builder()
                    .setName(callerName)
                    .setImportant(true)
                    .build()
                val speakerAction = NotificationCompat.Action.Builder(
                    androidx.core.graphics.drawable.IconCompat.createWithResource(context, R.drawable.ic_notif_speaker),
                    "Speaker", CallActionReceiver.toggleSpeakerIntent(context)
                ).build()
                val muteAction = NotificationCompat.Action.Builder(
                    androidx.core.graphics.drawable.IconCompat.createWithResource(context, R.drawable.ic_notif_mute),
                    "Mute", CallActionReceiver.toggleMuteIntent(context)
                ).build()
                builder
                    .setStyle(NotificationCompat.CallStyle.forOngoingCall(caller, CallActionReceiver.endIntent(context)))
                    .addAction(speakerAction)
                    .addAction(muteAction)
                    .setContentTitle(callerName)
                    .setContentText(statusLabel)
                    .build()
            } else {
                builder
                    .setStyle(NotificationCompat.DecoratedCustomViewStyle())
                    .setCustomContentView(compactViews)
                    .setCustomBigContentView(views)
                    .setCustomHeadsUpContentView(views)
                    .setContentTitle(callerName)
                    .setContentText(statusLabel)
                    .build()
            }

            notify(context, notification)
        } catch (e: Exception) {
            android.util.Log.e("CallNotificationHelper", "Failed to show ongoing call notification", e)
        }
    }


    fun refreshOngoing(context: Context) {
        if (lastIsIncoming || lastCallerName.isBlank()) return
        showOngoingCallNotification(
            context, lastCallerName,
            connected = lastCallConnected,
            connectTimeMillis = ongoingCallStartMillis,
            onHold = lastCallOnHold
        )
    }

    fun clear(context: Context) {
        stopAvatarPulse()
        stopLedFlash()

        val service = PixelInCallService.instance
        if (service != null) {
            try {
                @Suppress("DEPRECATION")
                service.stopForeground(true)
            } catch (e: Exception) {
                android.util.Log.w("CallNotificationHelper", "stopForeground failed", e)
            }
        }

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.cancel(NOTIFICATION_ID)
        lastCallerName = ""
        lastIsIncoming = false
        lastCallConnected = false
        lastCallOnHold = false
        ongoingCallStartMillis = 0L
    }

    private val endedNotificationHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var endedNotificationRunnable: Runnable? = null


    fun showCallEndedNotification(context: Context, callerName: String) {
        try {
            stopAvatarPulse()
            endedNotificationRunnable?.let { endedNotificationHandler.removeCallbacks(it) }
            lastCallerName = ""
            lastIsIncoming = false

            val views = buildGlassLayout(context, callerName, statusText = "Call ended", incoming = false)
            val notification = NotificationCompat.Builder(context, AshuDialerApp.CHANNEL_ONGOING_CALL)
                .setSmallIcon(R.drawable.ic_call_notification)
                .setStyle(NotificationCompat.DecoratedCustomViewStyle())
                .setCustomContentView(views)
                .setCustomBigContentView(views)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setOngoing(false)
                .setAutoCancel(true)
                .build()

            notify(context, notification)

            endedNotificationRunnable = Runnable {
                val service = PixelInCallService.instance
                if (service != null) {
                    try {
                        @Suppress("DEPRECATION")
                        service.stopForeground(true)
                    } catch (e: Exception) {
                        android.util.Log.w("CallNotificationHelper", "stopForeground failed", e)
                    }
                }
                val manager = context.getSystemService(NotificationManager::class.java)
                manager.cancel(NOTIFICATION_ID)
            }
            endedNotificationHandler.postDelayed(endedNotificationRunnable!!, 8_000)
        } catch (e: Exception) {
            android.util.Log.e("CallNotificationHelper", "Failed to show call-ended notification", e)
        }
    }

    private fun buildCompactOngoingLayout(context: Context, callerName: String, statusLabel: String = "Ongoing call"): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.notification_call_compact)
        val style = notificationThemeStyle(context)
        views.setInt(R.id.compact_root, "setBackgroundResource", style.backgroundRes)
        views.setTextColor(R.id.compact_name, style.primaryText)
        views.setTextColor(R.id.compact_status, style.secondaryText)
        views.setTextViewText(R.id.compact_name, callerName)
        views.setTextViewText(R.id.compact_status, statusLabel)
        views.setOnClickPendingIntent(R.id.compact_speaker, CallActionReceiver.toggleSpeakerIntent(context))
        views.setOnClickPendingIntent(R.id.compact_mute, CallActionReceiver.toggleMuteIntent(context))
        views.setOnClickPendingIntent(R.id.compact_end, CallActionReceiver.endIntent(context))

        // Prefer the live service's own CallAudioState (the same Telecom-
        // confirmed object PixelInCallService.onCallAudioStateChanged
        // consumes) over independent raw reads, so this notification
        // reports the exact same route/mute values the in-call screen is
        // showing at the same moment - falls back to the standalone
        // controller/AudioManager reads only if there's no live call
        // service to ask (e.g. notification rebuilt just as the call ends).
        val liveState = PixelInCallService.instance?.callAudioState
        val currentRoute = if (liveState != null) {
            when (liveState.route) {
                android.telecom.CallAudioState.ROUTE_SPEAKER -> AudioRoute.SPEAKER
                android.telecom.CallAudioState.ROUTE_BLUETOOTH -> AudioRoute.BLUETOOTH
                android.telecom.CallAudioState.ROUTE_WIRED_HEADSET -> AudioRoute.WIRED_HEADSET
                else -> AudioRoute.EARPIECE
            }
        } else {
            AudioRouteController(context).currentRoute()
        }
        val muted = liveState?.isMuted ?: CallAudioQuickActions.isMuted(context)
        // 3-way state, 2 backgrounds: Speaker and Bluetooth both read as
        // "active" (highlighted) since either is a deliberate alternate
        // route the person chose, while Earpiece (the quiet default) reads
        // as "off" - which of the two active states it actually is gets
        // conveyed by the icon itself (compact_speaker_icon), not the
        // background, since Bluetooth gets its own distinct glyph.
        views.setInt(
            R.id.compact_speaker, "setBackgroundResource",
            if (currentRoute == AudioRoute.EARPIECE) R.drawable.bg_notification_compact_off else R.drawable.bg_notification_compact_on
        )
        views.setImageViewResource(
            R.id.compact_speaker_icon,
            if (currentRoute == AudioRoute.BLUETOOTH) R.drawable.ic_notif_bluetooth else R.drawable.ic_notif_speaker
        )
        views.setInt(
            R.id.compact_mute, "setBackgroundResource",
            if (muted) R.drawable.bg_notification_compact_on else R.drawable.bg_notification_compact_off
        )
        return views
    }

    private data class NotificationThemeStyle(
        val backgroundRes: Int,
        val activePillRes: Int,
        val primaryText: Int,
        val secondaryText: Int
    )

    /**
     * Resolve the exact same palette used by the Compose UI and map it to
     * RemoteViews-safe XML resources. This keeps the notification card, text,
     * and active controls visually aligned with Gradient/Ocean/Sunset/Violet/
     * Midnight/Dark/Pure Black/Slate/White/Rainbow themes instead of using
     * one hard-coded dark teal or one generic light card.
     */
    private fun notificationThemeStyle(context: Context): NotificationThemeStyle {
        val themeId = com.ashudialer.app.data.ThemePreference.peekLastKnownThemeId(context)
        val nightMode = context.resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK
        val systemIsDark = nightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES
        val resolvedId = com.ashudialer.app.ui.theme.resolveThemeId(themeId, systemIsDark)
        val palette = com.ashudialer.app.ui.theme.paletteById(resolvedId)
        // v5: the call card is always the same classic neutral glass, light or dark. The old
        // theme-specific backgrounds (green, ocean, sunset, rainbow...) made a phone call look
        // like a toy. Only the text colours still follow the active theme so contrast is right.
        val bg = if (palette.isDark) R.drawable.bg_notification_glass else R.drawable.bg_notification_glass_light
        val active = R.drawable.bg_notification_pill_active
        return NotificationThemeStyle(
            backgroundRes = bg,
            activePillRes = active,
            primaryText = palette.textPrimary.toArgb(),
            secondaryText = palette.textSecondary.toArgb()
        )
    }

    private fun buildGlassLayout(
        context: Context,
        callerName: String,
        statusText: String,
        incoming: Boolean,
        timerStartMillis: Long = 0L
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.notification_call_glass)
        val style = notificationThemeStyle(context)
        views.setInt(R.id.notif_root, "setBackgroundResource", style.backgroundRes)
        views.setTextColor(R.id.notif_name, style.primaryText)
        views.setTextColor(R.id.notif_status, style.secondaryText)
        views.setTextViewText(R.id.notif_name, callerName)
        views.setTextViewText(R.id.notif_status, statusText)
        if (!incoming && timerStartMillis > 0L) {
            // a live mm:ss next to the status, so the card shows how long the call has been going
            views.setViewVisibility(R.id.notif_dot, android.view.View.VISIBLE)
            views.setViewVisibility(R.id.notif_timer, android.view.View.VISIBLE)
            views.setTextColor(R.id.notif_dot, style.secondaryText)
            views.setTextColor(R.id.notif_timer, style.secondaryText)
            views.setChronometer(
                R.id.notif_timer,
                android.os.SystemClock.elapsedRealtime() - (System.currentTimeMillis() - timerStartMillis),
                null,
                true
            )
        }

        if (incoming) {
            views.setViewVisibility(R.id.notif_actions_row, android.view.View.GONE)
            views.setViewVisibility(R.id.notif_incoming_actions_row, android.view.View.VISIBLE)
            views.setOnClickPendingIntent(R.id.notif_btn_answer, CallActionReceiver.answerIntent(context))
            views.setOnClickPendingIntent(R.id.notif_btn_decline, CallActionReceiver.declineIntent(context))
        } else {
            views.setViewVisibility(R.id.notif_actions_row, android.view.View.VISIBLE)
            views.setViewVisibility(R.id.notif_incoming_actions_row, android.view.View.GONE)
            views.setOnClickPendingIntent(R.id.notif_btn_speaker, CallActionReceiver.toggleSpeakerIntent(context))
            views.setOnClickPendingIntent(R.id.notif_btn_mute, CallActionReceiver.toggleMuteIntent(context))
            views.setOnClickPendingIntent(R.id.notif_btn_end, CallActionReceiver.endIntent(context))

            // Same live-state-first pattern as buildCompactOngoingLayout above.
            val liveState = PixelInCallService.instance?.callAudioState
            val currentRoute = if (liveState != null) {
                when (liveState.route) {
                    android.telecom.CallAudioState.ROUTE_SPEAKER -> AudioRoute.SPEAKER
                    android.telecom.CallAudioState.ROUTE_BLUETOOTH -> AudioRoute.BLUETOOTH
                    android.telecom.CallAudioState.ROUTE_WIRED_HEADSET -> AudioRoute.WIRED_HEADSET
                    else -> AudioRoute.EARPIECE
                }
            } else {
                AudioRouteController(context).currentRoute()
            }
            val muted = liveState?.isMuted ?: CallAudioQuickActions.isMuted(context)
            views.setTextColor(R.id.notif_btn_speaker_label, style.primaryText)
            views.setTextColor(R.id.notif_btn_mute_label, style.primaryText)
            views.setTextViewText(
                R.id.notif_btn_speaker_label,
                when (currentRoute) {
                    AudioRoute.SPEAKER -> "Speaker on"
                    AudioRoute.BLUETOOTH -> "Bluetooth"
                    AudioRoute.WIRED_HEADSET -> "Headset"
                    else -> "Speaker"
                }
            )
            views.setTextViewText(R.id.notif_btn_mute_label, if (muted) "Muted" else "Mute")
            views.setInt(
                R.id.notif_btn_speaker, "setBackgroundResource",
                if (currentRoute == AudioRoute.EARPIECE) R.drawable.bg_notification_pill_neutral else style.activePillRes
            )
            views.setImageViewResource(
                R.id.notif_btn_speaker_icon,
                if (currentRoute == AudioRoute.BLUETOOTH) R.drawable.ic_notif_bluetooth else R.drawable.ic_notif_speaker
            )
            views.setInt(
                R.id.notif_btn_mute, "setBackgroundResource",
                if (muted) style.activePillRes else R.drawable.bg_notification_pill_neutral
            )
        }
        return views
    }

    // Missed calls stack: each new one bumps the count and refreshes the
    // shown name, matching how every stock dialer's missed-call notification
    // behaves, rather than a single missed call silently replacing/hiding
    // the notification for a previous one still unseen.
    private var missedCallCount: Int = 0
    private var lastMissedCallerName: String = ""
    private const val MISSED_CALL_NOTIFICATION_ID = 7002

    fun showMissedCallNotification(context: Context, callerName: String, callerNumber: String) {
        try {
            missedCallCount += 1
            lastMissedCallerName = callerName

            val contentIntent = Intent(context, com.ashudialer.app.MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val contentPendingIntent = PendingIntent.getActivity(
                context, MISSED_CALL_NOTIFICATION_ID, contentIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val callBackIntent = Intent(context, CallActionReceiver::class.java).apply {
                action = CallActionReceiver.ACTION_CALL_BACK
                putExtra(CallActionReceiver.EXTRA_CALL_BACK_NUMBER, callerNumber)
            }
            val callBackPendingIntent = PendingIntent.getBroadcast(
                context, MISSED_CALL_NOTIFICATION_ID, callBackIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val title = if (missedCallCount > 1) "$missedCallCount missed calls" else "Missed call from $callerName"
            val text = if (missedCallCount > 1) "Most recent: $callerName" else
                (callerNumber.takeIf { it.isNotBlank() && it != "Unknown" && it != callerName } ?: "Tap to open Recents")

            val builder = NotificationCompat.Builder(context, AshuDialerApp.CHANNEL_MISSED_CALL)
                .setSmallIcon(R.drawable.ic_missed_call_notification)
                // a red icon + title colour is what tells you at a glance that this one is a MISSED call
                .setColor(0xFFE5484D.toInt())
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_MISSED_CALL)
                .setAutoCancel(true)
                .setShowWhen(true)
                .setWhen(System.currentTimeMillis())
                .setContentIntent(contentPendingIntent)
                .setNumber(missedCallCount)

            if (callerNumber.isNotBlank() && callerNumber != "Unknown") {
                builder.addAction(0, "Call back", callBackPendingIntent)
                val smsIntent = Intent(Intent.ACTION_SENDTO, android.net.Uri.parse("smsto:" + android.net.Uri.encode(callerNumber))).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                builder.addAction(
                    0, "Message",
                    PendingIntent.getActivity(
                        context, MISSED_CALL_NOTIFICATION_ID + 1, smsIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                )
            }

            val manager = context.getSystemService(NotificationManager::class.java)
            manager.notify(MISSED_CALL_NOTIFICATION_ID, builder.build())
        } catch (e: Exception) {
            android.util.Log.e("CallNotificationHelper", "Failed to show missed call notification", e)
        }
    }

    /** Clears the missed-call badge/count once the person has seen Recents. */
    fun clearMissedCallCount(context: Context) {
        missedCallCount = 0
        lastMissedCallerName = ""
        try {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.cancel(MISSED_CALL_NOTIFICATION_ID)
        } catch (_: Exception) {
        }
    }

    private fun notify(context: Context, notification: Notification) {


        if (!androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            android.util.Log.w(
                "CallNotificationHelper",
                "Skipped posting call notification: notifications are disabled for this app " +
                    "at the OS level (Settings > Apps > Notifications), or POST_NOTIFICATIONS " +
                    "was never granted."
            )
            return
        }

        val service = PixelInCallService.instance
        if (service != null) {
            try {
                service.startForeground(NOTIFICATION_ID, notification)
                return
            } catch (e: Exception) {
                android.util.Log.w("CallNotificationHelper", "startForeground failed, falling back to notify()", e)
            }
        }

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }
}
