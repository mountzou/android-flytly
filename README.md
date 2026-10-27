# Flytly for Android

Native Android companion to the existing Flytly iOS app.

**Archived prototype — 27 October 2026.** Development stopped before Google Play
publication. The snapshot preserves version **1.0.0 (1)** for future reference.
See [Restore the archive](docs/archive/RESTORE.md) and the
[verification summary](docs/archive/verification.json).

Production Firebase configuration, upload signing keys, personal account data,
raw QA output and generated build folders are excluded from Git. The signed
AAB/APK and an owner restore package are retained in the repository's **draft**
release `archive-1.0.0-1`, accessible to users with repository write access.
Temporarily unarchive the repository before retrieving the draft release; see
the restore instructions for the exact steps.
Restore `app/google-services.json` before building; the checked-in
`app/google-services.example.json` is a template, not production configuration.

## Open and run

1. Open this folder in Android Studio and let Gradle sync finish.
2. Select the `app` run configuration and an Android emulator.
3. Click Run.

The project uses Kotlin, Jetpack Compose, and Kotlin DSL build files. The minimum
supported version is Android 8.0 (API 26). Gradle uses Java 25, provided by the
installed Android Studio runtime.

For a command-line build on this Mac:

```sh
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.
`local.properties` contains the local Android SDK path and is excluded from
version control.

On this Mac, the emulator performance settings are now persistent: this virtual
phone uses four CPU cores and host graphics, and the user's
`~/.android/advancedFeatures.ini` sets `Vulkan = off`. The Vulkan setting applies
to all emulators launched by this macOS user. Start `Medium Phone API 37.0` normally
from Android Studio's Device Manager, then Run `app`; no launch script is required.
The emulator's Android apps, Google session and account data are retained.
The emulator opens in its own window: **Settings → Tools → Emulator → Launch
in the Running Devices tool window** is disabled on this Mac after an embedded
frame-streaming crash in the macOS OpenGL driver. Device Manager and Run still
start the same virtual phone normally. This is an Android Studio user preference.
Verification and a record of the previous settings are in
`artifacts/emulator-permanent-settings/`.

If the emulator loses DNS after the Mac sleeps and Google login shows “Something
went wrong”, `sh scripts/run-emulator.sh` remains an optional recovery launcher.
It supplies Google Public DNS to that emulator only, with the same performance
settings. It does not change macOS network settings or an already-running AVD.

## Authentication and account data

The normal app entry now displays the iOS sign-in screen recreated in Compose.
It uses the original launch/Google logo assets, the same Apple system mark,
Bricolage typography, copy, provider-button geometry, privacy link and viewport
layout. Both light and dark appearance are implemented. Compact screens scroll;
Android's real navigation area remains clear when scrolling the footer into view.
The footer displays the actual Android build version (currently 1.0.0).

Apple and Google buttons now use Firebase Authentication in the existing iOS
project, `flytly-ddc3c`, with Android package `com.mountzou.flytly`. Google uses
Credential Manager and the web OAuth client in `google-services.json`; Apple
uses Firebase's browser OAuth flow. Firebase restores the session after restart.
Anonymous accounts and unsupported providers cannot open the normal atlas.
Settings links to Account, which displays the signed-in name, email and provider,
with Log out and Delete my account actions.

Travels, Wishlist and preferences share the iOS Firestore paths:

- `users/{uid}/travelPlaces/{kind}:{placeID}`
- `users/{uid}/travelSyncSources/{installationUUID}`
- `users/{uid}/profile/travelPreferences`

The durable cache and outbox are isolated by Firebase project and UID in
Android's no-backup directory. Changes are saved atomically before upload,
removals remain tombstones, and per-installation transaction receipts prevent
replayed uploads from overwriting newer changes. Server snapshots are validated
in full; pending local edits overlay remote state. Changes retry after network
failures. Profile updates merge only edited fields and preserve other choices.
Pre-authentication local preview records are preserved separately and are never
automatically assigned to whichever account signs in next.

The existing Google and Apple providers were read back as enabled. Apple's
Service ID and code-flow configuration already exist. The debug and upload-key
SHA-1/SHA-256 fingerprints are registered. Play-managed app-signing fingerprints
and Play Integrity configuration still need verification for distribution. Production
Auth/Firestore rules and App Check enforcement were not changed.

Debug uses the App Check debug provider; release uses Play Integrity. The project
currently does not enforce App Check for Auth/Firestore. Do not disable or loosen
enforcement to troubleshoot a future production issue. Before enabling it for
Android, register the appropriate debug tokens / release attestation settings.

Real Google login and authenticated production data loading were verified in the
debug prototype, including bidirectional collection updates with iPhone and
matching server/local records. Personal test identities, collections and raw
readback evidence are deliberately excluded from the public archive. Production
Apple login remains untested. Emulator Suite tests use mock identity tokens and
do not exercise production consent services. These historical debug results do
not prove authentication or attestation on the final signed release.

The existing atlas can still be opened in debug builds for development:

```sh
adb shell am start -f 0x10008000 -n com.mountzou.flytly/.MainActivity --es ui_preview atlas
```

`ui_preview` and `ui_preview_theme` intent extras are ignored in release builds.
They are development previews, not authentication/session state. All atlas device
tests explicitly select the atlas preview. Login tests cover the default entry,
signed-out routing, recreation, both themes and compact scrolling.

Asset provenance is recorded in `artifacts/login-parity/asset-provenance.json`.
`scripts/import_ios_login_assets.py` recopies original assets and pins the optical
headline instance (requires fonttools 4.60.1). The Apple mark was rendered by the
installed iOS SDK using `scripts/export_ios_login_symbol.swift` and alpha-trimmed
without changing the symbol path. `design-qa.md` contains the current login review;
the earlier home review is archived at `artifacts/ui-parity/design-qa-atlas.md`.

## App icon and startup

The launcher now uses the owned Cruise airplane from the active iOS Icon Composer
source, with its rendered light/dark shading and a separate Android adaptive
background. Android supplies the icon mask; monochrome themed icons use the
original airplane alpha. The default Android Studio robot assets are replaced.

The native Android splash uses the same 224-unit centered LaunchLogo and paper/ink
colors as the iOS storyboard, through AndroidX SplashScreen 1.2.0. It follows the
system appearance before application code runs; the saved app appearance applies
to the subsequent loading UI, as in iOS.

Session restoration, catalogue/account loading and the first map frame use one
shared 32dp metaball loader with the original copy, 18dp gap and 1.6-second cycle.
Its 96 masks derive from the original Swift implementation, decoded off the UI
thread. Android 9+ uses native animated WebP; Android 8 uses the same sprite frames.
Playback pauses in the background and stops on disposal. Disabling system
animations selects a still mask rather than moving dots. The beUI MIT notice is
bundled and listed under Sources and licenses.

The loader covers map preparation until the real map reports its first complete
frame. It adds no minimum presentation delay. `scripts/import_ios_startup_assets.py`
reproduces the assets with Pillow; ordinary Android Studio builds do not need that
script. Source hashes, native iOS reference captures, frame comparison and final
verification are in `artifacts/startup-parity/`.

## Atlas

The app now renders the actual iOS atlas using MapLibre Native 13.7.1 (OpenGL ES)
and bundled GeoJSON. Pan, pinch, double-tap, continent shortcuts,
country selection, and place search work offline. Native Android text layouts
render labels with the Bricolage typeface and system fallbacks for other scripts;
there is no tile or glyph server, API token, or paid map subscription.

- 258 Natural Earth countries and territories, with their original polygons,
  interior rings, IDs, aliases and reviewed label positions.
- 26,288 catalog records, including legacy city IDs and successor mappings.
- 11,471 selectable places: 11,422 GHSL urban centres strictly above 50,000
  people plus 49 reviewed GeoNames capital/administrative-seat fallbacks.
- 20 map units with country-only coverage. These remain searchable and tappable.
- Country lists retain the iOS population ordering and capital exceptions.
  The population setting only filters country browsing; search covers the entire
  selectable catalog, including Greek aliases.
- Light/dark palettes, map label switches, and camera position persist locally.

The four original data files in `app/src/main/assets/atlas/` are byte-identical
to the iOS originals. The manifest records SHA-256 digests and rendering indexes.
Country flag SVGs and their 239-entry mapping are rendered on country pages.
Sources and notices are available from Menu → Settings → Sources and licenses;
full notices and detailed provenance are bundled under `atlas/licenses/` and
`atlas/DATA_SOURCES.md`.

### Startup performance

The current debug APK reaches its first fully rendered map in **1.23–1.77 seconds**
across five process-cold launches on this Apple M5 / API 37 emulator once Android
has finished its boot work. A separate launch rebuilding the public catalogue
cache took **2.11 seconds**. The prior APK on the former two-core software renderer
took **11.15–11.57 seconds** in this session. These retain the signed-in account,
camera and OS page cache; they are not clean-install or emulator-boot timings.

The largest observed improvement came from host graphics and four CPU cores.
Immediately after a cold emulator boot, the final app also took **4.09–6.66 seconds**
under Android background load. The under-five-second target is verified for the
ready emulator, not guaranteed during system boot or on untested physical devices.

The lossless read-only `catalog.sqlite` opens just the countries and label metadata
at startup. Saved cities, country lists, normalized multilingual search and visible
city labels query the index as needed. It replaces the eager decoding/sorting of
26,288 city records. Opening the catalogue took **45–75 ms** in the final five runs,
or **118 ms** when rebuilding its public cache. The original iOS JSON/GeoJSON files
remain byte-identical. Existing sessions preload this public offline data alongside
UI/session restoration. Country geometry, map detail, search ordering, legacy IDs
and all aliases are preserved. Account records never enter this catalogue database.

`scripts/compile_atlas_catalog.py` rebuilds the index; `--check` verifies it. The
iOS import calls this automatically, and Gradle rejects stale source/index hashes.
No Python installation is needed for an ordinary Android Studio build. Runtime
SQLite/GeoJSON assets are uncompressed for faster I/O. The current debug APK is
98.7 MiB; its SQLite catalogue is 17.5 MiB. Measurements, screenshots, exact hashes,
emulator settings and validation are in `artifacts/map-startup-under5/README.md`.
`artifacts/map-performance/` retains the earlier JSON-to-binary optimization report.

## Preference onboarding

The account flow now uses the original iOS nine-step editor: Travel pace, Nature,
Culture, Food, Crowds, Mobility, Weather, Budget, then Must have / Must avoid.
Copy, choices, default level 2, progress, button labels, Bricolage optical font
instances and light/dark colors follow `TravelPreferences.swift`. The same
Firestore profile document is used by both platforms.

New accounts and profiles with pending/missing onboarding status open this editor
automatically. Existing completed/skipped accounts retain their status. Save and
Skip write all current draft values; Skip uses the iOS `completed: true,
skipped: true` convention. The editor closes only after server acknowledgement.
Offline/failed saves retain the draft and allow retry. A recreated screen keeps
its current step and choices; cancelling its UI coroutine does not cancel an
account-owned save. Signing out closes that account's store.

Menu → Preferences shows the completed profile's eight original SVG cards and
individual choice sheets, or the iOS empty illustration and setup action after
Skip. Cancelling setup discards the draft. Preferences are cached per project/UID;
remote updates replace untouched values while protecting an active draft. Cached
pending onboarding can resume offline, while editing a completed profile requires
a successful server read. Old queued profile changes drain before a new full save.

Visual references, screenshots, QA and test evidence are in
`artifacts/onboarding-parity/`. `scripts/capture_ios_onboarding_reference.py`
compiles the original SwiftUI view body with mock state and no Firebase SDK.
`scripts/import_ios_preferences_fonts.py` reproduces the native optical instances
(fonttools 4.60.1). Reference app setup and screenshots do not alter the iOS app
or its production data.

## iOS interface alignment

The iOS implementation remains the reference for the interface. The home screen
uses “Your footprint / Your reach”, the five continent shortcuts, and the two
original menu/add icons instead of the catalog header and vertical zoom toolbar.
Menu, Add a place, country/city details, collections and Settings now open as full
pages. Bricolage weights, grayscale flag assets, typography, paper/ink colors and
geometry-derived artwork come from the iOS source. The eight preference icons
are copied directly from its SVG assets; `scripts/import_ios_ui_icons.py` translates
the owned menu/plus/close/theme Shape geometry into Android VectorDrawables. Android keeps its native
status/navigation bars, dialogs and touch behavior; SwiftUI glass has an opaque
native surface equivalent.

Visited and Wishlist records work locally. Home counts derive from unique visited
countries and the same six continents as iOS. Each place has one active status;
different cities in a country can independently be visited or wishlisted. Local
records use the iOS place key vocabulary and preserve predecessor/successor IDs.
Atomic writes retain the previous file if a save fails. A damaged file is reported,
not replaced by an empty collection. Preferences and map settings persist locally.

Authentication, account synchronization and preference onboarding are connected.
Country/city pages now use source-framed country artwork, city markers, source
flags, five suggestions, a searchable full city list, and a separate saved-city
section. Population and country rank use the same full catalog as iOS, regardless
of the city browsing filter. Country links, nested Back navigation and collection
selection survive drilldown and Activity recreation.

My travels and Wishlist use the six-continent snapping carousel, highlighted
maps, country grids, source counts including Other / Not listed, and empty states.
Visited/Wishlist actions save through the existing account repository and its
offline queue. Undo restores the actual preceding state and rejects a stale
snapshot rather than overwriting newer edits; removing a visit asks for
confirmation. Large text and wide layouts adapt without hiding the city list.

Destination matches (JEV) and travel sharing now have their own Android implementation,
described below. See `artifacts/place-parity/design-qa.md` for the
country/city and collections review, `design-qa.md` for the login visual review and
`artifacts/onboarding-parity/design-qa.md` for the preference onboarding review.

### JEV and sharing

City and Other / Not listed actions include the same JEV sparkle control as iOS.
It waits for confirmed, completed travel preferences before calling the existing
`analyzeDestinationFit` callable in `europe-west3`. Auth and App Check remain
required; the provider key and usage limits stay on the existing server. There is
no automatic retry of a paid analysis. Closing the page cancels its presentation,
and duplicate taps are disabled while loading. The result uses the original fit
thresholds, four-segment bars, overall expression and eight preference illustrations
in a swipeable sheet. The error alert retains the current iOS wording.

My travels has the iOS share toolbar control whenever the collection contains a
visited country. It captures every visited country when tapped, independent of
the selected continent. The preview and exported files use the same fixed light
card: 1080×1350 PNG, plus its inset 1080×1920 Story canvas. Both formats can open
the Android system chooser through a read-only, narrow FileProvider grant. No
identity, GPS or preference data is included. Exports are generated locally in
private cache storage, kept for recipients after dismissal, and old exports are
removed after seven days when another export begins.

Instagram Stories uses the native Android intent when Instagram is installed and
the public Meta app ID in `res/values/sharing.xml` is configured. The current
empty value presents the same **Share story image** fallback as iOS. A real
Instagram editor handoff remains a physical-device integration check. There is
no additional sharing control on country/city pages, matching the iOS source.

The emulator's debug App Check registration is a separate Firebase allowlist
entry, never a credential in source code or the APK. Release continues to use
Play Integrity. After explicit user approval, this emulator was registered and
one real Android JEV request succeeded in 6.186 seconds. The same UI check
confirmed that the account, travel documents and preference profile were unchanged.
Exact live-call, visual, artifact and account verification is
recorded in `artifacts/jev-sharing/design-qa.md`.

City pages now include the iOS location prompt, straight-line distance and time
difference. Permission is requested only through **Use my location** or the
**Settings → Privacy → Location access** row. Only approximate foreground access
is requested. After denial/revocation, **Open Settings** opens Android's app
settings. One-shot requests stop when the city page leaves the foreground.
Google's location service and the native Android provider provide a bounded,
cancellable first-fresh-fix fallback; no location is added to account records.

Origin and destination timezone identities come from offline geographic boundary
lookup, independently of the phone's selected timezone. Android's IANA rules
compute current offsets, including DST and quarter/half-hour differences; the
display updates every 30 seconds. The zone caches and user fix remain in memory.
The bundled 2026e boundary database adds about 36.3 MiB to the APK, is opened only
after a city has an authorized location fix, and is never loaded during Home/map
startup. Its data, encoding specification, source notice and ODbL license are in
`app/src/main/assets/timezones/`. Rebuild with `scripts/prepare_timezone_catalog.py`;
Gradle verifies its fingerprints. See `artifacts/location-parity/design-qa.md`.

The earlier place-page verification did not reconfirm the initial sub-5-second map
startup result: the new APK measured 5.19–5.77 seconds, and the previous verified
APK measured 5.75–5.88 seconds on the same current emulator. The earlier timing
reports remain historical measurements; see `artifacts/place-parity/design-qa.md`
and its controlled comparison for the current result.
The subsequent location pass reconfirmed **1.406–2.796 seconds** through the full
map on the ready emulator, retaining session/data and OS page cache. See
`artifacts/location-parity/startup-measurements/` and `delivery-launch/`; this is
not a guarantee for emulator boot time or different host/runtime conditions.

## Settings and Account

Settings follows the current iOS section order, copy, rounded cards, switches,
population chooser, location access, privacy link and account navigation. Account
uses the original typography, profile fields, provider labels, button styles,
deletion confirmation, busy state and error copy. Both themes, larger text and
wide layouts are supported. Android retains its native system bars and dialogs.
Sources and licenses uses the source layout with the notices required by the
Android dependencies and data files.

Appearance, map labels and the minimum population are app-wide preferences, as
on iOS. Existing per-account settings migrate only into missing app-wide values;
later accounts cannot overwrite those choices. The default is Light, and logout
retains the current appearance on the sign-in screen. Travels, preference profiles,
camera state and pending writes remain account scoped.

Deleting an account requires confirmation and fresh Google/Apple authentication
for that same Firebase user. A different Google identity cannot replace the
current session during deletion. Apple reauthentication obtains a fresh access
token and revokes authorization before Auth deletion. Cancelling a provider or
failing reauthentication leaves account records intact. While deletion is busy,
duplicate actions and Back navigation are disabled.

Android deletes the Auth user first, activating the already-deployed, retryable
`cleanupDeletedUserData` trigger. That existing server handler recursively removes
the user's profile, travels, sync receipts and JEV usage. Unlike the internal iOS
ordering, no profile is removed before the Auth request succeeds. Server cleanup
is asynchronous. The active deployed handler was downloaded read-only and its
source hash matches the handler tested locally; no backend deployment was needed.

Before deleting Auth, account listeners and writes are stopped and joined.
Successful deletion clears that owner's durable cache, preferences, outbox,
legacy local records and camera settings, plus the Firestore persistence cache.
The Firestore SDK operation is logical cache removal, not secure media erasure.
App-wide appearance/map preferences remain. A no-backup phase journal supports
interrupted-operation recovery without automatically repeating a destructive
request. Late map callbacks cannot recreate a deleted owner's camera settings.

Deletion verification uses secondary Firebase apps in `demo-flytly-lifecycle`
with loopback Auth/Firestore emulators on ports 19099/18080. Google and Apple
mock credentials exercise the actual Android Firebase SDK, followed by the
unchanged cleanup handler against the same deleted demo UID. No real account
was deleted. Real provider consent, Apple token revocation and deletion on a
physical device remain release checks; emulator credentials do not prove those
provider integrations. Visual comparisons, test logs and exact build evidence
are in `artifacts/settings-account/`.

## Reimport the reviewed iOS data

The reference implementation is `/Users/mountzou/ios-flytly`. The import script
verifies catalog coverage and the iOS city digest, copies the source files,
prepares map point indexes, and instantiates the variable font at weights 400/500/600/700
(the source font defaults to weight 800).

```sh
python3 -m venv /tmp/flytly-fonts
/tmp/flytly-fonts/bin/pip install fonttools==4.60.1
/tmp/flytly-fonts/bin/python scripts/import_ios_atlas.py --source /Users/mountzou/ios-flytly
```

Run `python3 scripts/prepare_atlas_art.py` to derive country/continent SVG artwork
from the unchanged source polygons. No substitute map or flag artwork is used.

Keep the preserved city IDs and successor mappings when adding account storage
and sync; follow the existing iOS offline queue and deletion semantics.

## Validation

```sh
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew assembleDebug testDebugUnitTest lintDebug
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew assembleDebugAndroidTest
```

Unit tests cover threshold/fallback rules, ordering, legacy resolution, and
normalized search. Device tests verify the complete imported catalog and hashes,
Greek search with camera focus, camera/zoom preservation after recreation,
Visited/Wishlist status transitions and persistence, and full-page navigation.
Artwork readiness is asserted before capturing the country and continent screens.
Debug UI tests use a separate `ui_test_namespace` for records, camera and settings;
release builds ignore that intent extra.

Verified on 26 October 2026 on the ARM64 Medium Phone API 37 emulator: ten
unit tests passed. All five device tests passed, including navigation/artwork readiness and
light/dark map pixel checks. The affected two tests passed again after the final
selection/capture fixes; see `design-qa.md` for visual evidence. Build and
lint completed without errors (dependency/tooling/style and draw-allocation warnings
remain). The earlier atlas-only suite also passed with airplane mode enabled,
Wi-Fi/mobile data disabled, and no active default network; network settings were
restored. That historical atlas-only pass preceded the Firebase integration described above.

For tests against an existing installation, build both APKs, install with `adb install -r`,
and use `adb shell am instrument -w com.mountzou.flytly.test/androidx.test.runner.AndroidJUnitRunner`.
This keeps the application and its existing local data installed. The connected
Gradle test runner can uninstall its target after completion; use a disposable
emulator for that workflow.

## Firebase integration tests

Start official local Firebase emulators (Node 20+, Java 25 on this Mac):

```sh
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" npx -y firebase-tools@latest emulators:start --only auth,firestore --project demo-flytly-android --config tests/firebase/firebase.json
adb reverse tcp:9099 tcp:9099
adb reverse tcp:8080 tcp:8080
```

`tests/firebase/firestore.rules` is an unchanged copy of the iOS rules at the
integration baseline. No rules deployment is involved. The test-only secondary
Firebase apps use `demo-flytly-android` and localhost over ADB; production app
initialization always uses the real Firebase configuration.

Build/install both APKs with `adb install -r` and run:

```sh
adb shell am instrument -w -e class com.mountzou.flytly.FirebaseAccountInstrumentedTest com.mountzou.flytly.test/androidx.test.runner.AndroidJUnitRunner
adb shell am instrument -w -e class com.mountzou.flytly.PreferencesFirebaseInstrumentedTest com.mountzou.flytly.test/androidx.test.runner.AndroidJUnitRunner
adb shell am instrument -w -e class com.mountzou.flytly.PreferencesWizardInstrumentedTest com.mountzou.flytly.test/androidx.test.runner.AndroidJUnitRunner
adb shell am instrument -w -e class com.mountzou.flytly.FirebaseSessionPersistenceInstrumentedTest -e session_phase save com.mountzou.flytly.test/androidx.test.runner.AndroidJUnitRunner
adb shell am instrument -w -e class com.mountzou.flytly.FirebaseSessionPersistenceInstrumentedTest -e session_phase restore com.mountzou.flytly.test/androidx.test.runner.AndroidJUnitRunner
```

The last two commands deliberately use separate Android processes to verify
Firebase's persisted session. Test accounts and writes are confined to the local
demo project. Tests do not sign in or sign out the default production Firebase app.
See `artifacts/firebase-auth/verification.md` for the exact verification outcome.
Preference test outcomes and current APK fingerprints are recorded separately in
`artifacts/onboarding-parity/verification.json`.
The preference pass verified 20 unit tests, 6 preference Firebase tests, 5 editor
UI tests, 10 account/atlas regression tests and 2 opt-in real-account read-only
checks. Two layout checks were repeated after the final wrapping adjustment.
Signed-out login tests require a disposable signed-out emulator. On an existing
signed-in installation, select the relevant test classes rather than running
every class indiscriminately.

## Signed first Android release

The local 1.0.0 (1) AAB/APK candidate and verification evidence are in
`artifacts/google-play/1.0.0-1/`. Read `PLAY-READINESS.md` there for exact hashes,
private upload-key backup instructions, build commands and remaining Play gates.
The private signing profile is outside the checkout at
`~/.android/flytly-release/signing.properties`; release tasks fail if it is absent.
The upload certificate is registered with Firebase; Play-managed app-signing
certificates and Play-attested login/sync/JEV checks are still pending. No Play
upload or publication has been performed.
