# Arrival Brief Android release candidate

Kotlin/native Android app for parked arrival preparation. Import a text share from another app on the same Android device, select a supported room or paste an event notice, review the facility and total vehicle height, and confirm a sourced briefing. An explicitly entered parking memo can be saved and reopened on that device. There is no external AI request, analytics, current-position tracking, fabricated route or vehicle-control call.

## Build

JDK 17, SDK platform 35, Build-Tools 35.0.0, Gradle 8.13 wrapper, AGP 8.7.3 and Kotlin 2.0.21. Minimum API 28, target API 35. The wrapper distribution is checksum-pinned.

```sh
cd android
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleRelease
```

Release keys and passwords stay outside the repository. See [release build instructions](../docs/ANDROID_BUILD.md). Debug output is `app/build/outputs/apk/debug/app-debug.apk`; it is not the submission artifact.

## Vehicle access

The initial state is **UNKNOWN** and input remains locked. On automotive hardware, `PlatformVehicleGateway` uses the optional `android.car` library and `CarUxRestrictionsManager`. Input is allowed only when distraction optimization is not required and active restrictions are zero. Missing values, disconnection, exceptions or leaving the foreground lock input. API 28 automotive stays locked because the adapter uses the API 29 asynchronous lifecycle API.

Platform input permission is not a physical parking measurement. The user must also confirm safe parking. No activity declares distraction optimization; the OS may block the whole activity while driving. The clearly labeled ordinary-Android demo is entered explicitly and is unavailable on hardware declaring `android.hardware.type.automotive`.

## NaviHelper in the review flow

`NavigationDestinationReader` requests destination information from official **NaviHelper 3.0.5**, only on user request and while the automotive platform allows input. It consumes `DestinationInfo.name` and `address` in memory; neither raw value is logged, stored or shown. No current location is read and no route is set.

`NavigationMatcher` distinguishes MATCHED, MISMATCH, UNKNOWN and UNAVAILABLE. A match requires an explicitly recognized convention-facility or supported meeting-room name and a supported exact Samseong address format. Nearby hotels, COEX Mall and arbitrary businesses containing “COEX” are not accepted as the convention facility. This is a facility-name/address check at one moment, not proof of the selected room, entrance or route. It does not continuously monitor navigation changes.

A known mismatch blocks briefing confirmation and cannot be cleared by manual acknowledgement, editing the notice, restoring a recent destination or a failed retry. A new matching check can resolve it. Unchecked/unknown/unavailable states require explicit manual facility review. CHECKING and MISMATCH block confirmation. Manual review is never labeled a successful SDK response.

The reader has a six-second timeout, one-shot request tokens and listener release. Input lock, notice changes and background transitions cancel pending work. Vehicle 2.3.2, brake-pedal reads and their unrelated permissions were removed. The manifest retains `pleos.car.permission.NAVI_ROUTE`; permission declaration is not proof it has been granted.

## Source and session rules

- Eight COEX meeting-room groups are recognized. Five are guideable; Conference Room E, rooms 300-328 and rooms 400-403 are blocked due to conflicting official sources. Exhibition halls A/B/C/D are outside the scope.
- Explicit parking clauses can be separated from the venue clause only when their grammar is recognized. Their floor, zone and gate are checked against the selected official recommendation. A sponsor or reference mentioning COEX is not sufficient venue ownership; unparsed parking/entry restrictions and explicit non-occurrence or prohibition remain blocked. Contradictory or unrecognized clauses, unsupported room numbers, other/nearby facilities, cancelled notices and invalid heights require review or block confirmation. English Room/Rooms numbering and separators remain part of the venue decision; negative, zero, decimal and leading-zero floor tokens are held for review. Manual venue selection cannot bypass these conflicts. The common corpus `tests/decision-cases.tsv` is used by both web and Kotlin tests.
- RC3 separates only complete supported schedule and registration clauses. A schedule in the form `행사는 HH:mm에 시작하여 HH:mm에 종료됩니다.` must contain valid same-day times with start before end. `등록은 [1-4]층 로비에서 진행합니다.` and `참가 등록은 [1-4]층 로비에서 진행합니다.` are retained separately from the event-room floor. The original clauses remain available under `일정·등록 원문 보기` while input is allowed and are included in briefing export. Ambiguous registration locations, differing multiple notes, negation, changes and other facilities remain subject to review. This does not establish current event occurrence, dates, lobby availability or an indoor route.
- Explicit review of the current event venue, total vehicle height and safe parking is required. Destination/height edits invalidate previous confirmations. Height above 2.2m is blocked; exactly 2.2m retains a boundary warning.
- After confirmation, Android hides the preparation form and explanatory diagram to focus on the arrival pass. `안내 수정` is available only when platform input is allowed; it clears the confirmation and requires venue, height and safe-parking review again. A stale click cannot reopen preparation after restrictions change.
- Rotation retains the volatile session. Process recreation drops the notice, height and confirmed briefing. A live in-memory confirmation may remain visible in restricted mode, subject to OS blocking.
- The user can explicitly save one confirmed destination ID, timestamp and dataset version in private preferences for 24 hours. No raw notice, height, navigation name/address or confirmed driving card is persisted. Expired, future-dated, stale-version, unknown and source-conflict records are rejected.
- Restore/delete require input access. Restore creates an unconfirmed draft with empty height and renewed checks. It does not recover a confirmed briefing during a restricted/driving restart and does not transfer anything from a phone.
- Automatic backup and sensitive view-state saving are disabled. Copying the derived briefing is explicit and includes sources and any recognized schedule/registration clauses preserved verbatim; it does not export the entire pasted notice.

The parser recognizes supported patterns; it is not a general language-understanding system. Undeclared unfamiliar proper nouns and the assignment of registration floors across multiple events are not fully resolved. For ambiguous notices, the user must verify the original event, facility, floor and parking details, then separate the relevant clauses without dropping or altering those details.

## Notice sharing and parking memo

`ACTION_SEND` with `text/plain` accepts a bounded in-memory notice. Raw intent contents are immediately scrubbed. A pending share never automatically replaces a confirmed briefing: platform input permission, a separate safe-parking confirmation, and an explicit import action are required. Cold and warm activity entry follow the same policy. This is same-device Android sharing, not phone-to-car transport.

A manual parking memo holds at most 64 UTF-16 code units for 24 hours in private preferences, matching the web input and Android length filter. Viewing, saving and deleting require platform input permission and a separate safe-parking confirmation. Driving/unknown state, background transition and recreation clear sensitive visible fields and require review again. No raw notice or navigation address is stored. The user can delete the memo, and expired or invalid records are rejected. Automatic backup/transfer excludes the preferences.

The memo is distinct from the official recommended parking zone and is not live localization or a walking route. After explicit safe-parking review, Android can display a plain-text QR containing the saved memo. A phone camera must read it and the user must separately save the text; this does not open a URL, use a server, or synchronize devices. The app's expiry/deletion policy does not delete phone copies. Expiry is enforced by foreground checks and on the next read/launch; no exact-time background deletion is scheduled while the process is stopped. QR visibility is cleared by restrictions, backgrounding and expiry. Real camera-to-display optical scanning remains a separate unverified check.

## Verification boundaries

The current source is `1.0.0-rc3` (versionCode 12). The vehicle-height illustration, semantic state colors and destination relationship diagram support the preparation flow; the diagram is not a geographic route. The QR payload preserves the original memo and save time with shorter supporting text, and the display adapts to the available width and height up to 400dp. These changes improve the software handoff without establishing a successful physical-phone scan.

The exact current APK version, hash, unit tests, lint and signing results are in [release-verification.json](../docs/verification/release-verification.json). [Runtime observations](../docs/verification/android-runtime.md) identify the build and environment for each install, launch and interaction result. Release APK observations and debug instrumentation results must be reported separately. Historical 0.3/0.4/0.5/RC1/RC2 observations are not evidence for RC3.

The reported RC3 build contains 77 Android unit tests, 60 web tests and 433 shared decision inputs, with zero Lint errors or warnings. The signed candidate is 6,668,191 bytes with SHA-256 `fbdfc4dda2ee7618876d2fc360ce7880d88ad1e95dc6e092fd245b05b411ed50`. The RC3 standard UI run passed 18 cases, and six font-scale1.3 repetitions passed separately. Seven actual signed-release UI states were recorded and matched to one continuous VFR screenrecord. Its encoded timeline is 98.1939 seconds in the source and 98.25 seconds in the published version, distinct from the helper elapsed time of 110.511 seconds; this is not a performance measurement. The original host exit 1 and the subsequent frame/state verification are preserved in the runtime evidence. Check each recorded APK hash and build type before using a result.

The [fixed scenario comparison](../docs/verification/award-scenarios.md) changed from 8/10 to 10/10 on the same machine-generated development examples. It is a regression check on cases used during implementation, not a held-out evaluation or user study. Historical RC2 [QR evidence](../docs/verification/completion-qr.md) retains a 3/4 screenshot-decoder result, and its [log review](../docs/verification/android-aosp28-rc2-log-review.json) preserves one OS `cmd` SIGSEGV distinct from the app. Neither observation is silently assigned to RC3. The prior 88-point review is historical RC1 preparation feedback, not an RC3 score.

Compilation, fake-adapter contract tests and ordinary Android demo execution do not validate real NaviHelper callbacks, permissions, automotive restrictions, performance or long-duration stability. A supported Pleos runtime is still required for those checks. The host's GPU and virtualization limitations are recorded with actual emulator output rather than assigning an unproven single cause.

## Official sources

- [COEX general-vehicle guidance](https://business.coex.co.kr/guide/conference-room-parking-information/general-vehicle/)
- [COEX 2026-02-11 parking notice](https://business.coex.co.kr/post-meeting-notice/%EC%95%88%EB%82%B4%EC%9E%90%EB%A3%8C-%EC%BD%94%EC%97%91%EC%8A%A4-%EC%A3%BC%EC%B0%A8%EC%9E%A5-%ED%99%94%EB%AC%BC%EC%B0%A8-%EC%A7%84%EC%9E%85-%EC%95%BD%EB%8F%84/)
- [Pleos driving restrictions](https://document.pleos.ai/docs/pleos-only/vehicle-app-developer-guide/driving-restriction-development-guide)
- [Android parked-app integration](https://developer.android.com/training/cars/parked/automotive-os)
- [NaviHelper lifecycle](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/initialization-and-release)
- [Official SDK Maven](https://nexus-playground.pleos.ai/repository/maven-releases/)

SDK vendor licensing and permission requirements still apply. Native widgets and the custom Arrival Brief brand icon are used; asset provenance and the monochrome vector fallback are documented in [ATTRIBUTIONS.md](../ATTRIBUTIONS.md). No Hyundai or Pleos logo is included.
