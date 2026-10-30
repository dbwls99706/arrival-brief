# Arrival Brief

행사 안내문에서 세부 목적지를 확인하고, 출발 전에 진입문과 주차 구역을 정리하는 차량용 앱 프로토타입입니다.

현대자동차그룹 차량용 앱 개발 공모전을 준비하는 독립 프로젝트입니다. 2026-10-30 기준 서명한 release APK와 소스가 있으며, Pleos 에뮬레이터 실행 시도는 OS 초기화와 APK 설치 단계에서 실패해 재검증이 필요합니다. 참가 신청이나 마켓 입점을 완료했다는 뜻은 아닙니다.

## 사용 흐름

안내문 붙여넣기 → 목적지 확인 → 차량 전체 높이 확인 → 도착 브리핑 확정 → 입력 제한 시 확정 내용 유지.

- 코엑스 삼성동 8개 목적지 분류를 다룹니다. 5개는 안내 가능하고, 컨퍼런스룸 E·300~328호·400~403호는 공식 자료의 추천 구역이 서로 달라 확정과 내보내기를 막습니다.
- 복수 후보, 취소·변경 표현, 층수 충돌과 높이 제한을 확인합니다. 지하 진입 제한 2.2m를 넘으면 해당 동선을 안내하지 않습니다.
- 웹의 정차·주행·상태 미확인은 시뮬레이션입니다. Android는 기본 상태를 `UNKNOWN`으로 두고 입력을 잠급니다.
- Android에는 `CarUxRestrictions` 연결 코드를 구현했습니다. 플랫폼이 입력을 허용한다는 신호와 물리적 정차는 구분하며, 브리핑 확정 전 전체 높이와 안전한 주차 상태를 사용자가 확인해야 합니다.
- 규칙 기반 해석입니다. 행사 안내문을 외부 AI API나 서버로 보내지 않습니다. 계정·API 키 없이 웹을 체험할 수 있습니다.

## 실행

### 웹

Python 3으로 저장소 최상위 폴더에서 실행합니다.

```sh
python -m http.server 8000 --bind 127.0.0.1
```

`http://127.0.0.1:8000/`에서 소개 페이지, `/dist/arrival/`에서 웹 체험을 엽니다. 최신 브라우저를 권장하며 별도 패키지 설치나 환경변수 설정은 필요 없습니다.

### Android

최신 파일은 [apk/ArrivalBrief_release.apk](apk/ArrivalBrief_release.apk)입니다. `0.2.0-prototype`, versionCode 2, 최소 Android API 28입니다.

```sh
adb install -r apk/ArrivalBrief_release.apk
```

설치 명령은 실행 방법입니다. 이번 Pleos 실행 시도에서는 OS 초기화 실패와 시스템 설치 오류로 APK 설치·첫 실행을 확인하지 못했습니다. [런타임 기록](docs/verification/android-runtime.md)에 관측 결과와 재검증 조건을 남겼습니다. 일반 Android 환경에서는 안내에 동의한 뒤 데모를 시작할 수 있습니다. 자동차 플랫폼에서는 데모 버튼을 제공하지 않고 실제 플랫폼 UX 제한 신호를 기다립니다. 플랫폼이 신호를 주지 않으면 입력 잠금을 유지합니다.

소스 빌드에는 JDK 17, Android SDK 35, Build-Tools 35.0.0과 SDK 라이선스가 필요합니다. Gradle 8.13 wrapper가 포함되어 있으며 첫 의존성 다운로드에는 네트워크가 필요합니다. SDK 위치는 `ANDROID_HOME` 또는 로컬 `android/local.properties`로 지정합니다.

```sh
cd android
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleRelease
```

Windows는 `gradlew.bat`를 사용합니다. release 서명은 저장소 밖의 키와 로컬 환경 설정으로 처리합니다. 서명 설정이 없는 재빌드는 unsigned 결과일 수 있으므로 제공된 서명 APK와 구분해야 합니다. 자세한 설정과 근거는 [Android 빌드 기록](docs/ANDROID_BUILD.md)에 있습니다.

## 검증 범위

2026-10-30 기준 결과입니다. 단위 테스트는 실제 차량 실행을 대신하지 않습니다.

| 검사 | 결과 |
| --- | --- |
| 웹 엔진 | 14개 테스트 통과 |
| Android JUnit | 23개 통과 - Engine 13개, Session 10개 |
| release 빌드 | `0.2.0-prototype` APK 생성 |
| APK 서명 | apksigner v2 검증 성공, RSA 3072비트 |
| Android Lint | 오류 0개, 경고 4개 |
| 일반 Android 9(API 28) | release 설치·첫 실행·명시적 데모 진입 확인 |
| 실제 Pleos 설치·서비스 연결·주행 제한 전환 | OS 초기화·설치 실패. 공식 환경 요건을 충족하는 환경에서 재검증 필요 |

Lint 보고서에는 백업 설정 관련 1개와 하드코딩된 표시 문자열 관련 3개 경고가 남아 있습니다. `fullBackupContent="false"`를 추가한 소스 수정과 경고가 남아 있는 현재 보고서를 구분해 기록했습니다.

```sh
node --test tests/arrival.test.mjs
python scripts/check-paths.py
```

브레이크 페달 값으로 정차 여부를 추정하지 않습니다. 실시간 주차 빈자리, 입구 좌표, 실내 경로와 내비 목적지 전달은 현재 기능에 포함하지 않습니다. 운영체제가 주행 중 앱 화면 전체를 제한할 수도 있으므로, 확정 브리핑의 실제 표시 가능 범위도 차량 환경에서 확인해야 합니다.

[일반 Android 실행 화면과 Pleos 재검증 기록](docs/verification/android-runtime.md)에서 두 환경의 결과를 구분해 확인할 수 있습니다.

## APK와 시연 자료

| 자료 | 위치 | 상태 |
| --- | --- | --- |
| 최신 release APK | [ArrivalBrief_release.apk](apk/ArrivalBrief_release.apk) | 2026-10-30, v0.2.0, release 서명 확인. Pleos 설치 실패·재검증 필요 |
| 도착 준비 영상 | [arrival-reel.mp4](dist/assets/media/arrival-reel.mp4) | 33초 세로 영상. 최신 웹 화면으로 갱신 완료 |
| 자료 상충 확인 영상 | [arrival-confirm-reel.mp4](dist/assets/media/arrival-confirm-reel.mp4) | 33초 세로 영상. 최신 웹 화면으로 갱신 완료 |

최신 release APK는 8,620,730바이트입니다.

```text
SHA-256 079ce46a3b6f5660e8808d0109033c5004c9fa8d910ac93d68668cdb2d0df16e
```

두 영상은 최신 웹 화면으로 교체했고 각 792프레임의 전체 디코딩과 자막 구간을 확인했습니다. 스톡 도입 4초와 웹 화면 캡처 29초를 편집한 자료이며 실제 차량 촬영이나 연속 화면 녹화가 아닙니다. [검증 기록](docs/HYUNDAI_VERIFICATION.md)에 파일 해시와 확인 범위를 남겼습니다.

## 제출 전 확인할 사항

공식 접수양식은 심사용 공유 링크와 첨부자료에서도 개인정보·계정정보·개발자 실명 노출을 제한합니다. 개인 GitHub 계정이 드러나는 저장소 주소를 어떻게 제출할지 운영 측 확인이 필요합니다. 비공개 저장소는 심사용 계정의 접근 권한이 필요한데, 2026-10-30 확인 범위에서는 초대할 심사용 GitHub 계정이 공지되지 않았습니다. 비공개 설정만으로 심사 접근이 준비됐다고 보지 않습니다.

공식 APK 파일명 형식은 `팀명_앱이름_release.apk`입니다. 최종 참가명에 맞춘 파일명과 접수 자료를 확인해야 합니다. 개인정보가 있는 접수양식, 동의서, 서명, 연락처와 서명 개인키는 이 저장소에 넣지 않습니다.

## 문서와 출처

- [기획과 구현 범위](docs/ARRIVAL_PLAN.md)
- [검증 기록](docs/HYUNDAI_VERIFICATION.md) · [Android 빌드 근거](docs/ANDROID_BUILD.md) · [Pleos 실행 시도](docs/verification/android-runtime.md)
- [SDK 연동 범위](android/README.md) · [자료 출처와 라이선스](ATTRIBUTIONS.md)
- [공모전 공식 제출 안내](https://appdevchallenge.auto-hmg.io/guide/submission)
- [코엑스 공식 일반차량 주차안내](https://business.coex.co.kr/guide/conference-room-parking-information/general-vehicle/)

시설 자료 확인일은 2026-10-30입니다. 최신 공식 안내와 현장 표지를 우선합니다.
