# Restore the Flytly Android archive

This repository preserves the Android prototype, version **1.0.0 (1)**, as of
27 October 2026. It was not uploaded to Google Play or publicly released. The
repository is intended to be read-only after archival; unarchive or fork it if
development resumes.

## What is preserved

- Kotlin/Compose app source, debug/release variants and unit/instrumented tests.
- Gradle wrapper and pinned dependency versions.
- Bundled atlas and offline time-zone catalogs, artwork, icons, fonts and their
  existing source/attribution/license files.
- Import/generation and emulator helper scripts, plus Firebase emulator rules
  and deletion-verification fixtures.
- A curated verification summary and the source fingerprints corresponding to
  the signed candidate. Historical `artifacts/` links in the development notes
  refer to local evidence that is not in Git.
- Draft release `archive-1.0.0-1`: exact signed AAB/APK, an owner restore ZIP and
  SHA-256 checksums. Draft releases require write access to view; do not publish
  this draft as a public app release.

## Restore source and configuration

```sh
git clone https://github.com/mountzou/android-flytly.git
cd android-flytly
git switch --detach archive-1.0.0-1
gh release download archive-1.0.0-1 --repo mountzou/android-flytly --dir owner-archive
```

The owner restore ZIP contains the original Android `google-services.json`,
public upload certificate, signed-candidate verification reports/source hashes,
and signing-profile instructions. Extract `app/google-services.json` into the
checkout. Treat the restore package as owner material even though Firebase
client configuration is not a service-account private key. Do not commit it.

Alternatively, register an Android app with package `com.mountzou.flytly` in a
Firebase project you control and download its configuration. Use
`app/google-services.example.json` as a structure reference only. Google/Apple
providers, native signing fingerprints, Firestore rules and App Check must be
configured in that project; a JSON file alone does not configure those services.

The original Firebase backend is shared with the iOS app. Its production Cloud
Functions and deployed rules are owned by the separate iOS/backend project;
archiving Android does not authorize deleting, replacing or deploying that
backend. `tests/firebase/firestore.rules` is a local test fixture.

## Build

Use Android Studio's Java 25 runtime, Android SDK platform API 37 and the
checked-in Gradle 9.6.0 wrapper. AGP/Kotlin versions are pinned in
`gradle/libs.versions.toml`. Configure your own SDK path through Android Studio
or an untracked `local.properties` file. First-time dependency downloads require
network access.

```sh
export JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home'
./gradlew --max-workers=2 :app:assembleDebug :app:testDebugUnitTest :app:lintRelease
```

To inspect the map without a production account, the debug build supports:

```sh
adb shell am start -f 0x10008000 -n com.mountzou.flytly/.MainActivity --es ui_preview atlas
```

Release builds ignore these development extras. Debug App Check may require
registering a **new** emulator debug token. Old tokens and real account sessions
are not preserved in this archive. Do not disable enforcement to make a test pass.

Instrumented tests require a suitable emulator. Run sign-in/deletion fixtures on
disposable test devices/accounts; avoid a runner that uninstalls the owner's
signed-in app. Firebase emulators use the `demo-flytly-android` project.

## Release signing

The existing upload keystore remains outside this project under the owner's
`~/.android/flytly-release/` directory. **No private key or signing password is
uploaded to GitHub**, including the draft release. Keep a separate encrypted
backup of both `flytly-upload.p12` and `signing.properties` if retaining the
ability to sign with the original key matters.

Gradle reads that private profile automatically, or one selected through
`FLYTLY_RELEASE_SIGNING_PROPERTIES`. Release tasks fail closed when it is absent.
`scripts/setup_release_signing.py` is for first setup on a new machine/project;
it refuses to replace an existing key. A newly generated key will not reproduce
the original certificate.

## Verification and remaining limits

The archived signed candidate passed 44 unit tests and release lint with zero
errors/81 warnings. Its exact AAB-generated splits were installed and hashed
back from a separate API 37 emulator. Signed-out/compact UI, signatures,
permissions and 64-bit native/ZIP 16 KB alignment were checked. Runtime page size
was 4 KB; 16 KB device execution was not checked.

Real Google/Apple login, authenticated map/sync/JEV, Play Integrity attestation
and real-provider account deletion remain unverified on the final signed
candidate. Debug prototype tests are separate historical evidence. Future Play
publication requires current store/account requirements and certificate setup
to be rechecked; it is not implied by this archival snapshot.
