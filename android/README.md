# Arrival Brief Android prototype

Kotlin/native Android app for parked arrival preparation. Select a supported room directly or paste an event notice, review the facility and total vehicle height, and confirm a sourced briefing. There is no external AI request, analytics, current-position tracking, fabricated route or vehicle-control call.

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
- Basement floors, unsupported room numbers, other/nearby facilities, cancelled notices and invalid heights require review or block confirmation. The common corpus `tests/decision-cases.tsv` is used by both web and Kotlin tests.
- Explicit height review and safe-parking acknowledgement are required. Destination/height edits invalidate previous confirmations. Height above 2.2m is blocked; exactly 2.2m retains a boundary warning.
- Rotation retains the volatile session. Process recreation drops the notice, height and confirmed briefing. A live in-memory confirmation may remain visible in restricted mode, subject to OS blocking.
- The user can explicitly save one confirmed destination ID, timestamp and dataset version in private preferences for 24 hours. No raw notice, height, navigation name/address or confirmed driving card is persisted. Expired, future-dated, stale-version, unknown and source-conflict records are rejected.
- Restore/delete require input access. Restore creates an unconfirmed draft with empty height and renewed checks. It does not recover a confirmed briefing during a restricted/driving restart and does not transfer anything from a phone.
- Automatic backup and sensitive view-state saving are disabled. Copying the derived briefing is explicit and includes sources, not the pasted notice.

## Verification boundaries

The 0.5.0 release updates the native visual interface with a vehicle-height illustration, preparation and destination relationship diagrams, and semantic step colors. These diagrams are not geographic routes. Compilation, unit tests, lint and release signing were checked; the new native UI has not yet been run on a device or emulator. The older runtime observations below do not validate the 0.5.0 UI.

The current version, APK hash, test counts and lint findings are in [release-verification.json](../docs/verification/release-verification.json). Unit tests and compilation are separate from real SDK service behavior. See [runtime observations](../docs/verification/android-runtime.md) for the recorded APK version and environment. The previous 0.4.0 release was installed and launched on AOSP Android 9. Explicit demo entry, height/facility/parking review, confirmation, driving input lock, and landscape readability at 1280×800 and logical 960×600 were observed. The following destination-storage observations belong to the earlier 0.3.0 release. Explicit demo confirmation, restricted-mode display, destination-only saving and unconfirmed draft restore after force-stop/restart were observed. Restored height and review checks were empty; confirmation remained disabled. System UI ANR and intermittent UIAutomator null-root failures limit this software-emulated session. These observations do not validate real NaviHelper callbacks, automotive restrictions, performance or long-duration stability.

The Pleos emulator attempt failed during system initialization and installation on the available host. Actual permissions, destination responses, driving restrictions and activity blocking need a supported Pleos environment. There is no live parking service, indoor navigation, passenger drop-off recommendation or entrance-coordinate handoff.

## Official sources

- [COEX general-vehicle guidance](https://business.coex.co.kr/guide/conference-room-parking-information/general-vehicle/)
- [COEX 2026-02-11 parking notice](https://business.coex.co.kr/post-meeting-notice/%EC%95%88%EB%82%B4%EC%9E%90%EB%A3%8C-%EC%BD%94%EC%97%91%EC%8A%A4-%EC%A3%BC%EC%B0%A8%EC%9E%A5-%ED%99%94%EB%AC%BC%EC%B0%A8-%EC%A7%84%EC%9E%85-%EC%95%BD%EB%8F%84/)
- [Pleos driving restrictions](https://document.pleos.ai/docs/pleos-only/vehicle-app-developer-guide/driving-restriction-development-guide)
- [Android parked-app integration](https://developer.android.com/training/cars/parked/automotive-os)
- [NaviHelper lifecycle](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/initialization-and-release)
- [Official SDK Maven](https://nexus-playground.pleos.ai/repository/maven-releases/)

SDK vendor licensing and permission requirements still apply. Native widgets and an independently designed vector icon are used. No Hyundai or Pleos logo is included.
