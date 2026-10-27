# Flytly login UI parity — 26 October 2026

**final result: passed**

Scope: the signed-out **login presentation** requested for this phase. The app now
opens here by default. Provider buttons display an explicit preview notice; there
is no authentication, account/session creation or simulated authenticated navigation.
The original atlas implementation and local records are retained in a debug-only
preview route. The earlier home/map review is archived at
`artifacts/ui-parity/design-qa-atlas.md`.

## Visual truth and comparison evidence

- iOS source: `/Users/mountzou/ios-flytly/ios-where-travel/SignInView.swift`,
  `AtlasTypography.swift`, `AtlasIcons.swift` and the adaptive colors in `HomeView.swift`.
- Fresh source captures: `artifacts/login-parity/ios-login-light.png` and
  `artifacts/login-parity/ios-login-dark.png`. Captured from the actual unmodified
  iOS app, signed out, on the isolated iPhone 17 reference simulator.
- Final Android captures: `artifacts/login-parity/android-login-light.png` and
  `artifacts/login-parity/android-login-dark.png`.
- Full-view comparisons: `login-comparison-light-final.png` and
  `login-comparison-dark-final.png` in the same directory.
- Focused headline/provider-mark/button/footer comparisons:
  `login-detail-light-final.png` and `login-detail-dark-final.png`.
- Earlier comparison evidence is retained as `*-iteration1.png`.
- Compact scrolling evidence: `android-login-compact-footer.png`.

Both normal-view references and implementations are **1206 × 2622 physical px**,
logical **402 × 874**, density **3**. Android was temporarily set to 480 dpi. Full
comparison panels are equally resized to 402 × 874; detail crops use the identical
physical region `(0,1410)-(1206,2510)` at half scale. Both source and implementation
were opened together in each comparison image; focused details were also inspected
at readable size. The compact check is **960 × 1704 px**, logical **320 × 568**,
density 3, scrolled to reveal the footer. This compact capture is a usability check,
not a claim of paired iOS visual sign-off at that smaller viewport.

OS status bars and navigation indicators remain genuine native infrastructure.
They are not cloned or included in app artwork. Android's original display settings
(1080 × 2400, 420 dpi) were restored after verification.

## Findings and iteration history

1. **[P1, resolved] Login did not exist on Android.** Implemented the actual source
   composition rather than inventing a welcome screen: 160-point original launch
   logo, 40%-of-viewport logo center, 5% bottom inset, 56-point total horizontal
   inset with 420-point maximum content width, and the same content-group gaps.
   MainActivity now opens the login presentation by default. Source and Android
   final full-view comparisons show the same composition in both themes.
2. **[P2, resolved] Headline optical width differed in iteration 1.** The existing
   global Android font instance produced a slightly narrower headline. Used the
   original Bricolage variable font with a login-specific optical instance,
   calibrated against the source capture, retaining 36-point semibold text and
   the original line break. Global atlas typography was not changed.
3. **[P2, resolved] Apple mark included export padding.** The native iOS SDK export
   contained transparent canvas margins, making the mark smaller than SwiftUI's
   resizable symbol. Alpha-trimmed the exported image without redrawing its path.
   Final focused comparisons show the correct mark size inside the 20-point slot.
4. **[P2, resolved] Compact footer could be scrolled behind Android's home bar.**
   For layouts that need scrolling, the visible scroll area now excludes Android's
   real navigation inset. The full-viewport source composition is retained when
   content fits. The compact test passed again and its final screenshot shows the
   version above the navigation indicator. Both normal themes were recaptured and
   compared after this fix.

There are no actionable P0/P1/P2 findings in this login UI scope.

## Required fidelity surfaces

- **Typography:** same Bricolage family and source sizes/weights: headline 36/600,
  body 17/400, provider labels 17/500, privacy copy 13/400, version 11/400. The
  two-line headline, caption wrapping, provider text baseline and shared reserved
  title width were checked in the focused comparisons. Native rasterization and
  very small baseline differences are P3 platform polish.
- **Spacing/layout:** source LoginViewportLayout algorithm ported directly; gaps
  28/14/10/3, 52-point provider buttons, 14-point button separation, 12-point corner
  radii, shared 20-point icon slots, 18-point Google mark and 12-point icon/text gap.
  Logo position, footer anchor and button bounds align in the same-size captures.
- **Colors/tokens:** paper #FBFCFD / #111111, ink #111111 / #F2F2F2, secondary
  #757575 / #A3A3A3. Apple and Google provider backgrounds, foregrounds and Google
  outline use the same source values in light/dark appearance. Checked both full
  and focused comparisons for contrast and state consistency.
- **Assets/image quality:** the launch and Google PNGs are byte-identical source
  assets. Apple is the same `apple.logo` system symbol exported by UIKit, not a
  substitute logo or approximation. The external-link mark translates the owned
  AtlasExternalLinkIcon path. All copied/rendered assets are sharp at the checked
  viewport, and tested APK asset bytes match the current source files. Source
  hashes and routes are in `artifacts/login-parity/asset-provenance.json`.
- **Copy/content:** original headline, subtitle, Apple/Google labels, acknowledgment
  text, Privacy Policy link and version-label formatting are retained. The privacy
  URL was checked against SignInView.swift and is identical. Android displays its
  actual package version (1.0); the source iOS capture displays its version (1.1.13).
  This is a build-value difference, not placeholder text or a claimed release.

## Runtime verification

- Build, test APK build, ten existing host tests and lint passed. Lint has zero
  errors; dependency/tooling/draw-allocation warnings remain.
- Three login device tests passed: default entry and provider preview behavior;
  recreation remaining on login; light/dark presentation; scroll reachability.
- Both Apple and Google taps show the UI-only notice and never navigate to the
  atlas or set an account/session flag.
- Existing Greek-search/map-focus test passed through the explicit debug atlas
  preview, validating that the previous implementation remains available.
- The compact test was run at 320 × 568, the footer overlap was fixed, and this
  test passed again. The light/dark normal capture test also passed again afterward.
- Tests use separate settings namespaces; existing local travel files are retained.
  Release builds ignore all development preview extras.

Evidence logs: `/private/tmp/flytly-login-build2.log`,
`/private/tmp/flytly-login-verified-device-tests.log`,
`/private/tmp/flytly-login-final-build.log`,
`/private/tmp/flytly-login-compact-final-test.log`,
`/private/tmp/flytly-login-normal-final-test.log`.

## Accepted differences and limits

- **[P3]** Genuine Android status/navigation bars, current time/battery and font
  antialiasing differ from iOS. App-owned content was compared independently of
  that platform chrome.
- The actual package-version value differs as described above.
- Provider taps have preview behavior by request. No Firebase/OAuth integration,
  authentication-state handling or real sign-in validation was added in this phase.
- Compact Android scrolling was checked. Tablet, landscape and every accessibility
  font setting are outside the visual sign-off scope.

## Implementation checklist

- [x] Default entry opens the new login presentation.
- [x] Reuse original assets, copy, layout algorithm and typography.
- [x] Match both light and dark source captures, including focused details.
- [x] Provider taps remain explicitly UI-only; privacy link uses the source URL.
- [x] Verify compact scrolling and preserve native system bars.
- [x] Retain local atlas implementation/data and validate its debug preview route.
- [x] Restore emulator display settings and leave the app on login.
