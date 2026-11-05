# Ashu Dialer

A modern Android phone app with built-in **call recording** for phone calls, WhatsApp, Telegram,
Instagram and Snapchat.

## Install

Download `AshuPhone-<version>.apk` from the [Releases](../../releases) page and install it.
There is only one APK. No root, no Magisk module. The app checks for updates itself
(Settings > About > Check for updates) and installs them from the same Releases page.

## Call recording setup (one time, about 3 minutes)

Recording works through [Shizuku](https://github.com/RikkaApps/Shizuku), a free helper app.

1. Install **Shizuku**.
2. Open Ashu Dialer > **Settings** > **Call recording** and switch **Enable call recording** on.
3. Follow the checklist. Each step turns green when done. New to Shizuku? Tap the **?** button for a
   step-by-step guide in Hindi and English.
4. Optional: switch **Auto-record all calls** on. It is off by default.

Rooted phone: start Shizuku with root, allow Ashu Dialer, and turn on "Start on boot" in Shizuku.

Recordings are saved in `Music/Ashu Dialer/`, in one folder per source: `Phone`, `WhatsApp`,
`Telegram`, `Instagram`, `Snapchat`.

If a recording turns out to contain only silence, the app tries the next audio source by itself
(for app calls) and otherwise tells you, so you do not find an empty file after the call.

## Build

Pushing to `main` or `master` (or running the workflow by hand) builds and signs the APK and attaches
it to a release. It needs these repository secrets: `GOOGLE_SERVICES_JSON`,
`ASHU_RELEASE_KEYSTORE_BASE64`, `ASHU_RELEASE_STORE_PASSWORD`, `ASHU_RELEASE_KEY_ALIAS`,
`ASHU_RELEASE_KEY_PASSWORD`.

## Usage statistics

The app sends only anonymous, aggregate events through Firebase Analytics: app opened, and which
features were switched on. Never phone numbers, contacts, names or recordings. Active users and new
installs are visible in the Firebase console. Download counts are per release asset on the Releases
page.

## Security

Release builds carry the fingerprint of the official signing certificate (CI reads it from the keystore). A copy that was
modified and re-signed with another key opens a "not the official app" screen instead of the app; calls keep working.
Debug and local builds skip the check. This raises the bar, it cannot make modification impossible: keep the signing key
secret and share only the Releases page. Auto Backup is off, Private Space blocks screenshots, and debug/verbose logging
is stripped from release builds.

## What's new in 2.0.1 (on top of 2.0.0)

- **Smoother**: lighter glass drawing, cached aurora, faster Recordings.
- **Floating Dialer / Recents buttons** above the bar that open glass windows; optional floating dialer from the Dialer tab; bottom bar order and show/hide.
- **Conference calls** (Merge, Swap, Manage, remove people) and **redesigned call notifications**.
- **Voicemail screen**, dial pad layouts, wide suggestions.
- **Google Drive backup** by signing in, or by picking a folder, with a Test button ([docs/DRIVE_BACKUP.md](docs/DRIVE_BACKUP.md)).

## What's new in 2.0.0

- **Simple by default**: plain White and plain Black ("Auto" follows your phone). Liquid Glass, Frost
  Glass, Obsidian Gold, Emerald Night and the older colour themes are optional extras.
- **Personalize** (More > Personalize): accent colour, glass frosting, corner roundness, avatar shape,
  list spacing, text size, animation speed, haptic strength, bottom bar and dial pad style, with a live
  preview. One Reset button puts it all back.
- **Recordings hub**: waveform player with speed control, bookmarks ("mark a moment" during the call),
  stars, tags, notes, search, filters, sorting, group by person and storage tools.
- **Optional Google Drive backup** (no login) and a **cloud-only** mode. See below.
- Redesigned onboarding and More screen, swipe actions on recent calls.
- Fixed a crash when searching some number combinations in Recents, and the call notification timer that
  started before the other person answered.

Everything new that touches recording is optional and off unless you turn it on; the existing recorder
works exactly as before. Details: [CHANGELOG.md](CHANGELOG.md) and [docs/V2_PLAN_AND_STATUS.md](docs/V2_PLAN_AND_STATUS.md).

## Optional: back up recordings to Google Drive

No Google login and no developer-console setup. Ashu Phone copies each finished recording into a folder
you choose, and the Drive app uploads it.

1. Open **Recordings > Settings (gear)** and switch on **Back up to Google Drive**.
2. In the picker that opens, tap the menu (three lines) > **Drive**, open or create a folder, then
   **Use this folder**.
3. Choose **Wi-Fi only**, **Only while charging** and **Back up automatically** as you like.
4. Optional **Keep recordings only in the cloud**: after a successful upload the copy on the phone is
   deleted. Until then the file waits in private app storage (never in your Music folder). If an upload
   can't finish, the recording stays on the phone and you get a notification.

Any app that offers a folder in Android's picker (OneDrive, Dropbox, Nextcloud...) works the same way.

## Credits

Ashu Phone stands on open-source work. Thank you to:

- **[ShizuCallRecorder](https://github.com/kitsumed/ShizuCallRecorder)** by kitsumed (GPL-3.0-or-later):
  the call-recording engine is adapted from it. Ashu Phone's recorder is a modified fork derived from it.
- **[Ever Dialer](https://github.com/hari161008/Ever-Dialer)** by hari161008 (GPL-3.0): ideas only -
  recorder settings, haptic strength, bottom-bar customisation and glass looks. No Ever Dialer code is
  included.
- **[CallVault](https://github.com/madkongo/CallVault)** by madkongo (GPL-3.0 with additional terms) and
  **[JemRec](https://github.com/jemcik/JemRec)** by Yevhen Yemchynskyi (Apache-2.0): ideas only - Drive
  backup through the system file picker, marks/stars/tags/notes, discarding very short recordings and
  recorder health checks. No code from either project is included.
- **[scrcpy](https://github.com/Genymobile/scrcpy)** by Genymobile (Apache-2.0): the bundled, unmodified
  scrcpy-server used for app-call recording.
- **[Shizuku](https://github.com/RikkaApps/Shizuku)** and Shizuku-API by RikkaApps (Apache-2.0).

The same credits are on the in-app About screen and in [NOTICE](NOTICE).

## Notification/UI polish

- Notifications now resolve the active app palette for light, dark, AMOLED and accent themes.
- Launcher icon styles can be changed in Personalize (Default, Classic, Midnight, White Glass).
