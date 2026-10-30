# Arrival Brief Android 빌드 검증

검증일: 2026-10-30. 실제 코드, JUnit XML, Gradle 로그, aapt와 apksigner 결과를 대조했다. 이 문서의 빌드·서명 결과와 실제 Pleos 런타임 검증은 구분한다.

## 최신 결과

| 항목 | 확인 결과 |
| --- | --- |
| Gradle | release 빌드 성공. 보존 로그 기준 30초, 70개 작업 중 30개 실행·40개 최신 상태 |
| Android JUnit | 23개 통과, 실패·오류·누락 0개 - BriefEngineTest 13개, BriefSessionTest 10개 |
| Lint | 오류 0개, 경고 4개 |
| APK | `apk/ArrivalBrief_release.apk`, 8,620,730바이트 |
| 패키지 | `studio.ax.arrival` |
| 버전 | `0.2.0-prototype`, versionCode 2 |
| compile / minimum / target SDK | 35 / 28 / 35 |
| 서명 | APK Signature Scheme v2 검증 성공, 서명자 1명, RSA 3072비트 |
| 실제 Pleos 설치·실행·SDK 응답 | OS 초기화·APK 설치 실패. 설치·첫 실행 성공 아님, 재검증 필요 |

최신 APK의 SHA-256:

```text
079ce46a3b6f5660e8808d0109033c5004c9fa8d910ac93d68668cdb2d0df16e
```

서명 인증서 DN은 `CN=Arrival Brief, OU=Independent Development, O=Arrival Brief, C=KR`이며 인증서 SHA-256은 `5beae876e311240cd57d9a18d9cad248a29b6ffd4e2d7e4e37fecf808f6b0893`이다. 이는 출품 프로젝트의 서명이며 현대자동차그룹의 인증·승인 표시가 아니다. 개인키와 비밀번호는 저장소 밖에서 관리한다.

## 구현과 실행 검증의 구분

`PlatformVehicleGateway`는 `CarUxRestrictionsManager`에서 받은 값을 입력 허용 판단에 연결한다. distraction optimization이 요구되지 않고 활성 제한이 0일 때만 `PLATFORM_ALLOWED`가 된다. 값이 없거나 서비스 연결이 끊기면 `UNKNOWN`으로 잠근다. 연결별 토큰으로 이전 서비스의 늦은 콜백을 무시하며, 이 동작은 순수 상태 객체의 회귀 테스트로 확인했다.

`PLATFORM_ALLOWED`는 물리적으로 정차했다는 뜻이 아니다. 확정 전 사용자가 전체 높이와 안전한 주차 상태를 각각 확인해야 한다. 자동차 플랫폼에서는 시뮬레이션 버튼을 숨기고 데모 진입도 차단한다. 일반 Android의 데모는 별도 안내에 동의한 뒤 사용할 수 있다.

Pleos Vehicle 2.3.2와 NaviHelper 3.0.5를 사용한 읽기 진단도 컴파일했다. 차량 속성 접근 가능 여부와 목적지 정보 응답을 확인하는 코드다. 실제 Pleos 실행 시도는 OS 초기화와 설치 단계에서 실패해 서비스 응답을 확인하지 못했다. 브레이크 페달 값으로 정차를 추정하거나 내비 경로를 변경하지 않는다. manifest에 권한이 선언됐다는 사실도 실제 권한 부여와 구분한다.

## 남은 Lint 경고

| 항목 | 수 | 내용 |
| --- | ---: | --- |
| DataExtractionRules | 1 | API 28 지원과 Android 12 이상 백업 규칙의 병행 설정 관련 |
| SetTextI18n | 3 | 데모 높이값과 두 SDK 오류 안내를 문자열 리소스로 분리하라는 경고 |

현재 소스에는 `allowBackup="false"`, `fullBackupContent="false"`, `dataExtractionRules`가 있다. `fullBackupContent` 추가와 별개로 보존한 Lint 보고서에는 위 4개 경고가 남아 있으므로 '경고 없음'으로 표시하지 않는다. Gradle의 향후 버전 호환성 안내도 빌드 로그에 남아 있다.

## 재현 도구와 명령

- JDK 17
- Gradle 8.13 wrapper, 공식 배포 SHA-256 고정
- Android Gradle Plugin 8.7.3
- Kotlin Android plugin 2.0.21
- Android compile SDK 35, Build-Tools 35.0.0
- Pleos `ai.pleos.playground:Vehicle:2.3.2`, `ai.pleos.playground:NaviHelper:3.0.5`

```sh
cd android
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleRelease
```

SDK 위치는 `ANDROID_HOME` 또는 로컬 `local.properties`로 지정한다. release 서명 설정은 `ARRIVAL_RELEASE_KEYSTORE`와 `ARRIVAL_RELEASE_PASSWORD` 환경변수를 읽고 키 별칭 `arrival-release`를 사용한다. 값은 로컬 비밀 설정으로 전달하고 파일·명령 로그·저장소에 비밀번호를 남기지 않는다. 두 환경변수가 없으면 서명 설정이 적용되지 않으므로 재빌드 파일을 확인 없이 제출하지 않는다.

설치 방법:

```sh
adb install -r apk/ArrivalBrief_release.apk
```

이 명령은 저장소 최상위에서 실행한다. 과거 debug 빌드가 설치돼 있다면 서명 차이로 덮어쓰기 설치가 실패할 수 있다. 기존 앱을 제거할 경우 세션 데이터가 사라지므로 별도 테스트 환경을 사용한다. 이번 Pleos 설치 명령은 시스템 오류로 실패했다. 실행 방법 기재를 설치 성공 기록으로 읽지 않는다. [런타임 기록](verification/android-runtime.md)에 이미지 해시, 시스템 오류와 재검증 조건을 정리했다.

## 증거 파일

- [release-verification.json](verification/release-verification.json): 버전·해시·테스트·Lint·런타임 상태 요약
- [android-release-build.log](verification/android-release-build.log): 최신 성공 빌드 로그
- [BriefEngineTest XML](verification/TEST-studio.ax.arrival.BriefEngineTest.xml): 엔진 13개 테스트
- [BriefSessionTest XML](verification/TEST-studio.ax.arrival.BriefSessionTest.xml): 세션·플랫폼 상태 10개 테스트
- [android-release-signature.txt](verification/android-release-signature.txt): apksigner 검사
- [android-release-package.txt](verification/android-release-package.txt): aapt 패키지 검사

Lint 원본은 로컬 빌드 결과의 `android/app/build/reports/lint-results-debug.xml`과 `.txt`에 있다. 경고 요약은 `release-verification.json`에 보존했다. Pleos 부팅·설치 시도는 공식 환경 요구사항 미충족 상태에서 OS 초기화·패키지 서비스 오류로 실패했다. GPU 부재를 단일 원인으로 단정하지 않으며, 적합한 환경에서 설치·시작·서비스 응답·제한 전환을 재검증해야 한다.

## 이전 파일

이전 커밋의 `apk/ArrivalBrief_debug.apk`는 2026-10-28의 `0.1.0-prototype`이다. 10,222,568바이트, SHA-256은 `b251608ee160252a4c0c741731f3fa352861bc456537c9df4c4cd8ec5f41b663`이며 최신 출처 상충 처리와 플랫폼 연결 수정이 없다. 최신 release 대신 설치하거나 현행 동작의 증거로 사용하지 않는다.

`android-build.log`, `android-signature.txt`, `android-package.txt`는 당시 빌드의 이전 기록이다. 최신 증거는 파일명에 `android-release-`가 붙은 기록을 사용한다.
