# Arrival Brief Android prototype

Kotlin/native Android app for parked arrival preparation. Paste an event message, confirm the COEX room and the vehicle's total height, and keep a short, sourced arrival briefing. No remote AI call, API key, analytics, GPS fabrication, route mutation, or vehicle-control API is used.

## Build and tests

JDK 17, Android SDK platform 35 and build-tools 35.0.0 are required. The Gradle 8.13 wrapper distribution is checksum-pinned. Android Gradle Plugin 8.7.3 / Kotlin 2.0.21 / minSdk 28 / targetSdk 35.

```sh
cd android
# Set JAVA_HOME and ANDROID_HOME to your local installations.
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

Debug output: `app/build/outputs/apk/debug/app-debug.apk`. Release signing and the submission artifact are managed from the repository's release instructions. A compiled or signed APK does not establish successful execution on a Pleos device or market approval.

## Use and restrictions

The initial state is **UNKNOWN**, with input, confirmation and copying locked. On an Android Automotive device, `PlatformVehicleGateway` connects to the public AAOS `CarUxRestrictionsManager` through the optional `android.car` library. Input is enabled only after the platform reports `isRequiresDistractionOptimization == false` **and** no active restrictions. Missing values, unsupported platform, exceptions, disconnection and leaving the foreground lock input. Android 28 remains locked on automotive devices because this adapter uses the API 29 asynchronous lifecycle API.

A platform permission to interact is **not a physical parking measurement**. The UI says "플랫폼 UX 입력 허용" and asks the user to confirm safe parking before confirming or copying a briefing. Brake-pedal readings never unlock input. No activity declares distraction optimization; the OS may block the entire screen while driving, including a previously confirmed briefing.

For desktop/phone review, a clearly labeled demo can be entered only after an explicit explanation and confirmation. Demo controls can simulate parked, driving and unknown states, but are unavailable on devices declaring `android.hardware.type.automotive`. This is an interaction demonstration, not a vehicle simulator. Returning from the background resets the demo to UNKNOWN; explicitly select the parked example to continue.

## Decision and session behavior

- Deterministic matching covers eight COEX meeting-room groups. Five groups can produce a briefing. Conference Room E, rooms 300-328 and rooms 400-403 return `source_conflict`: two official sources recommend different parking zones, so these briefings cannot be confirmed or copied.
- Missing or conflicting locations, floor mismatches, cancelled venue notices, Magok facilities, malformed heights and excessive total heights block confirmation.
- Selecting a room cannot bypass a contradictory floor. Correcting height preserves an explicitly selected room but clears its previous confirmation and height acknowledgement.
- Total height must be entered in metres, with at most two decimal places. It must include roof boxes and other loads. Above 2.2 m is blocked. Exactly 2.2 m retains a prominent boundary warning in both the screen and copied text.
- Both total-height review and safe-parking acknowledgement are required before confirmation. Editing the destination or height invalidates the old confirmation.
- Restriction changes retain the already confirmed briefing in volatile memory while hiding input and copy actions. The OS can still restrict the full activity.
- Rotation retains the session only in memory. Process recreation drops pasted messages, height and the confirmed briefing. Editable fields disable automatic view-state saving and autofill. There is no database or raw-ticket logging. Clipboard export is explicit and contains derived briefing information and its source, not the pasted message.

`BriefEngineTest` and `BriefSessionTest` cover mapping, source conflicts, height limits and units, contradictory selections, default locking, separation of demo/platform signals, confirmation requirements, and invalidation/retention across state transitions.

## Read-only Pleos SDK diagnostics

`PleosDiagnostics` uses official **Vehicle 2.3.2** and **NaviHelper 3.0.5** dependencies. On request, it initializes services, checks brake-pedal read capability, reads the pedal state, and retrieves destination information through an event listener. Personal destination information is neither displayed nor retained. Failure and timeout are surfaced. Diagnostic results do not decide input access. Services and listeners are released when the activity leaves the foreground.

Automotive runtime permissions, service availability, the Car UX callbacks and OS activity blocking still require execution on the supplied Pleos environment and a compatible vehicle. Unit tests and compilation verify code behavior only. There is no verified entrance-coordinate handoff, live parking availability or passenger drop-off recommendation.

## Sources checked 2026-10-30

- COEX general-vehicle guidance: https://business.coex.co.kr/guide/conference-room-parking-information/general-vehicle/
- COEX 2026-02-11 parking notice, whose destination recommendations conflict for three groups: https://business.coex.co.kr/post-meeting-notice/%EC%95%88%EB%82%B4%EC%9E%90%EB%A3%8C-%EC%BD%94%EC%97%91%EC%8A%A4-%EC%A3%BC%EC%B0%A8%EC%9E%A5-%ED%99%94%EB%AC%BC%EC%B0%A8-%EC%A7%84%EC%9E%85-%EC%95%BD%EB%8F%84/
- Official Pleos UX restriction integration: https://document.pleos.ai/docs/pleos-only/vehicle-app-developer-guide/driving-restriction-development-guide
- Public Android parked-app integration: https://developer.android.com/training/cars/parked/automotive-os
- Vehicle SDK setup: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/project-setup
- Vehicle capability check: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/check-capability
- NaviHelper lifecycle: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/initialization-and-release
- Official SDK Maven: https://nexus-playground.pleos.ai/repository/maven-releases/

SDKs retain their vendor licenses and permissions requirements. Android native widgets and a project-owned vector launcher icon are used; no Hyundai or Pleos logo is included. SDK compilation is not evidence of real vehicle validation.
