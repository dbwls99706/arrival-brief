# Arrival Brief Android prototype

Kotlin/native Android executable source. No external runtime API key and no telemetry. Native Android views; official Pleos SDK brings AndroidX and other transitive runtime dependencies. AGP 8.7.3 / Kotlin 2.0.21 / compileSdk 35 / minSdk 28 / JDK 17. Gradle 8.13 wrapper scripts and JAR are included; the official distribution SHA-256 is pinned in `gradle/wrapper/gradle-wrapper.properties`:

```sh
cd android
# set ANDROID_HOME to installed Android SDK
# install platform android-35 and build-tools 35.0.0
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

`app/build/outputs/apk/debug/app-debug.apk` is a development artifact signed with the Android debug key. A release APK needs the participant's signing configuration. It is not competition-ready or Pleos-approved.

## What runs

- Paste an event message; extract supported COEX rooms via deterministic rules.
- Pick ambiguous destinations explicitly, validate vehicle height, fail closed on contradictions.
- Confirm a sourced gate/parking briefing, copy it, and simulate parked/driving/unknown modes.
- Unknown real vehicle mode locks editing. No fake GPS, navigation transfer, or vehicle-control claim.

## Integration boundary

`PleosDiagnostics` integrates the official Vehicle 2.3.2 and NaviHelper 3.0.5 libraries. On user request it initializes services, checks brake-pedal read capability, reads the brake-pedal state, and retrieves destination info with an event listener. Failure/timeout is surfaced; personal destination data is not displayed or retained. API classes and signatures were checked against official documentation and AAR artifacts.

`VehicleGateway` separates simulation from actual service diagnostics. `UnconnectedVehicleGateway` keeps interaction mode UNKNOWN: a brake-pedal reading does not establish that the car is parked. OS CarUxRestrictions integration, verified entrance coordinates before route handoff, and automotive device testing remain required. There is no route mutation or vehicle control permission. No code declares itself distraction-optimized or bypasses platform restrictions. Compiled SDK integration does not establish runtime service success.

Official SDK status on 2026-10-28: https://document.pleos.ai/docs/pleos-only/create-my-project listed Vehicle State and NaviHelper as available without application; FusedLocation / TextToSpeech / SpeechToText / Vehicle Control as coming soon. Account/project creation and runtime/permission validation remain necessary. Browser demo is at `../dist/arrival`.

No admission ticket, private customer data, or location is stored across process recreation. Copying briefing to clipboard is user initiated. It includes only the derived briefing and source, not the pasted ticket.

## SDK references and dependencies

- Vehicle setup: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/project-setup
- Vehicle capability: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/check-capability
- NaviHelper init: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/initialization-and-release
- SDK artifacts: official Maven https://nexus-playground.pleos.ai/repository/maven-releases/

SDKs retain their vendor licenses and permissions requirements; they are dependencies, not code relicensed by this project. JUnit 4.13.2 tests native decision-engine parity.
