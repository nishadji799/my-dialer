# Ashu Phone 2.0.x - plan and status

## The plan (from the owner's brief)
1. Fix the two bugs: Recents search crash; notification timer running before the call is answered.
2. Complete UI redesign with deeper animation. Simple White and Black are the defaults; everything else
   (Liquid Glass, aurora, extra themes) is optional.
3. Redesign onboarding, the Recordings screen, and the More screen.
4. Many new call-recording features, all optional, keeping the existing recorder untouched.
5. Optional Google Drive backup, and an optional mode that does not keep recordings on the phone.
6. Deep customisation of everything.
7. Learn from JemRec, CallVault and Ever Dialer; credit everything used.

## Status
| Item | State |
|---|---|
| Recents search crash | Fixed (root cause: duplicate LazyColumn key) |
| Notification timer before answer | Fixed |
| White / Black defaults, others optional | Done |
| Liquid Glass + Frost Glass + aurora | Done (optional themes) |
| Personalize screen + live preview | Done |
| Onboarding redesign (5 animated pages) | Done |
| More screen redesign (search, tiles, sections) | Done |
| Recordings hub redesign | Done |
| Waveform player, speed, bookmarks, stars, tags, notes | Done |
| Optional Drive backup + cloud-only mode | Done |
| Storage tools, discard short recordings, quality presets | Done |
| Swipe actions on Recents, dial pad styles, bottom bar options | Done |
| About screen with credits + fork notice | Done |
| JemRec-style direct wireless-debugging recorder (no Shizuku) | **Not included - planned for 2.1** |

### Why the direct recorder is not in 2.0.0
JemRec's no-Shizuku approach embeds an ADB client (libadb-android, Conscrypt, BouncyCastle), pairs with the
phone's own wireless debugging, starts a small daemon and talks to it over an authenticated loopback socket.
That is a second recording engine. It must be built and tested on real phones across Android 11-15, and a
half-tested engine could break the working Shizuku recorder. Proposal for 2.1: add it as a separate opt-in
backend ("Direct mode"), keep Shizuku as the default, port only the pairing + daemon launch pieces
(JemRec is Apache-2.0, compatible; keep its NOTICE entries) and ship it only after device testing.
2.0.0 takes JemRec's non-engine ideas instead (marks, discard short recordings).

## Build status (please read)
This update was written without access to an Android SDK, Gradle or the network, so it has **not been
compiled or run**. What was done instead:
- every edit to existing files was an exact-match patch that fails loudly if the target text differs;
- brace/parenthesis balance checked on all 41 Kotlin files touched;
- project imports and Compose imports checked by script, no duplicate top-level declarations;
- new code follows APIs already used in this codebase (Compose BOM 2024.06, Material3 1.2.1, Kotlin 1.9.24).

So the first CI build may stop on a small compile error. Most likely spots, in order:
1. `RecentsScreen.kt` (`SwipeableCallRow`: Material3 swipe API, needs ExperimentalMaterial3Api opt-in)
2. `RecordingsHub.kt` (`animateItemPlacement`, experimental foundation API)
3. `RecordingPlayerPanel.kt` (MediaCodec waveform decoder, PlaybackParams)
4. `OnboardingScreen.kt` / `PersonalizeScreen.kt` (icon names, Pager API)
5. `appcalls/.../cloud/*` (WorkManager / DocumentFile)
Paste the error and it is a one-line fix each time.

## Device test checklist
**Bugs**
- Recents: find a contact with two numbers, type part of its name or number in the search box -> no crash,
  one row for that person.
- Outgoing call: the notification says "Calling..." with no timer; the timer starts at 0:00 only after the
  other person answers. Put the call on hold -> "On hold".

**Look and feel**
- Fresh install (or clear data): onboarding pages, pick Auto/White/Black.
- More > Personalize: move every control and watch the live preview; Reset.
- Pick Liquid Glass: aurora moves behind every screen, overlays included. Turn on Battery Saver or set
  Animation speed to Off -> it freezes. Pick White/Black again -> no aurora, normal backgrounds.
- Bottom bar: Floating / Docked, tab names, light tick when switching tabs.
- Dial pad key style: Glass / Flat / Outline. Recents: swipe right = call, left = message.

**Recording** (needs your normal Shizuku setup)
- Record a call, tap the flag on the recording bar twice. Open Recordings: waveform, speed, the two
  bookmarks, star, tag, note, search, filters, group by person.
- Settings > Smart extras: discard recordings under 3 s (make a 1-second recording to test).
- Drive backup: switch on, pick a Drive folder, record a call, confirm the file appears in Drive.
  Turn on Cloud-only: after upload the file is gone from the phone but its Drive copy remains.
  Test with Wi-Fi off: the recording stays, shows the waiting cloud icon, uploads when Wi-Fi returns.

## Where things live
- Design system: `ui/theme/AshuStyle.kt`, `ui/components/LiquidGlass.kt`, `AshuMotion.kt`, `AuroraBackground.kt`
- Settings plumbing: `data/AppSettingsRepository.kt` (`PKey`, `toAshuStyle`), `viewmodel/MainViewModel.kt`
- Screens: `ui/screens/PersonalizeScreen.kt`, `OnboardingScreen.kt`, `MoreScreen.kt`, `AboutScreen.kt`
- Recordings: `ui/screens/RecordingsHub.kt`, `RecordingsHubParts.kt`, `RecordingPlayerPanel.kt`,
  `RecordingCloudSection.kt`
- Recording data: `appcalls/.../recording/RecordingMetaStore.kt` (stars, tags, notes, marks, cloud state)
- Cloud backup: `appcalls/.../cloud/` (`CloudPrefs`, `CloudSync`, `CloudCopyWorker`)


## 2.0.1 additions (after the first real-device test)
See CHANGELOG.md for the full list. New things worth testing first:
- Recents tab: the round Dialer button above the bar; tap it, then tap it again (or the backdrop) to close.
- Contacts / More tab: the round Recents button.
- Personalize > Bottom bar: move tabs, hide Dialer / Recents. Personalize > Dialer: floating window, layouts, wide suggestions.
- A call with call waiting, then merge: title says "Conference call (2)", Manage shows both people.
- Recording settings > Back up recordings > Test backup.
- More > Voicemail, and hold 1 on the dial pad.
Build status is the same as before: written without an Android SDK, not compiled here.
