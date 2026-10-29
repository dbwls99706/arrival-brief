# Arrival Brief Android 빌드 검증

검증일: 2026-10-28 KST (실행 호스트 로그는 다른 시간대로 기록될 수 있음).

## 결과

`testDebugUnitTest assembleDebug assembleRelease`가 성공했다. 공식 Maven 저장소에서 Pleos Vehicle 2.3.2 및 NaviHelper 3.0.5를 받아 실제 Kotlin 소스와 함께 컴파일했다. Android 실제 기기 또는 Pleos 에뮬레이터에서 앱 실행·SDK 서비스 응답을 확인한 것은 아니다.

| 항목 | 결과 |
|---|---|
| Gradle 빌드 | 성공, 3m 21s, 83 tasks |
| Native JUnit | 8 tests, 0 failures, 0 errors, 0 skipped |
| Debug APK | 생성 및 APK Signature Scheme v2 검증 성공 |
| Release APK | unsigned 파일 생성, 배포용 서명 미완료 |
| Package | `studio.ax.arrival` |
| Version | `0.1.0-prototype`, versionCode 1 |
| Minimum / target SDK | 28 / 35 |
| 실차·Pleos 에뮬레이터 실행 | 미검증 |

## 파일

이 저장소에는 debug APK만 포함한다. 아래 unsigned release 행은 이전 빌드 기록이며, 해당 바이너리는 최종 제출물 오인 방지를 위해 제외했다.

| 파일 | 크기(byte) | SHA-256 |
|---|---:|---|
| `apk/ArrivalBrief_debug.apk` | 10,222,568 | `b251608ee160252a4c0c741731f3fa352861bc456537c9df4c4cd8ec5f41b663` |
| `apk/ArrivalBrief_release_unsigned.apk` | 8,586,418 | `df7c6eebe1c50d210a97eab2b4bd37fa9f6b66e5c7a72fdd190b3741c0cf9093` |

Debug APK는 Android Debug 인증서로 서명된 테스트용 빌드다. Release unsigned는 설치·배포 전에 별도 서명이 필요하다. 대회 최종 제출용 release APK 완료로 표현하지 않는다. 서명 개인키와 빌드 환경의 프록시·개인 설정은 프로젝트에 포함하지 않았다.

## 재현 도구

- Microsoft Build of OpenJDK 17.0.20.1+1
- Gradle 8.13 (wrapper 포함, 공식 배포 SHA-256 고정)
- Android Gradle Plugin 8.7.3
- Kotlin Android plugin 2.0.21
- Android compile SDK 35
- Android SDK Build-Tools 34.0.0 (AGP 기본 선택; 35.0.0도 설치하여 서명/패키지 검사에 사용)
- Pleos `ai.pleos.playground:Vehicle:2.3.2`, `ai.pleos.playground:NaviHelper:3.0.5`

JDK 17, Android SDK 35 및 SDK 라이선스가 준비된 환경에서:

```bash
cd android
./gradlew testDebugUnitTest assembleDebug assembleRelease
```

SDK 위치는 표준 `ANDROID_HOME` 환경변수 또는 사용자의 `local.properties`로 지정한다. SDK와 JDK 바이너리는 저장소에 포함하지 않는다.

## 검증 근거

- `docs/verification/android-build.log`: 성공 빌드 로그 (환경의 절대 경로만 상대 경로로 변경)
- `docs/verification/TEST-studio.ax.arrival.BriefEngineTest.xml`: JUnit 결과 (호스트 이름만 익명화)
- `docs/verification/android-signature.txt`: apksigner 검증 결과
- `docs/verification/android-package.txt`: aapt manifest/package 검사

패키지 검사에서 `CAR_DRIVE`, `NAVI_ROUTE`, SDK의 `VEHICLE_BINDING` 권한과 MainActivity를 확인했다. 실제 차량/플랫폼의 권한 승인과 서비스 가용성을 검증한 것은 아니다.

## 해결한 빌드 환경 문제

1. 실행 환경에는 Java runtime만 있고 `javac`가 없어 별도 JDK17을 설치했다.
2. 실행별 네트워크 프록시 주소가 바뀌는 환경이어서 현재 프로세스의 프록시로 Maven 다운로드를 구성했다. 이 임시 설정은 배포 자료에 포함하지 않는다.
3. SDK platform 압축 해제 후 이중 디렉터리 구조를 도구 경로 내부에서 정상화했다.
4. SDK 코드 추가 도중 시작된 이전 빌드는 의존성 설정을 다시 읽어 최신 소스로 재빌드했다.

현재 남은 경고는 status/navigation bar 색상 API deprecation과 Gradle 9 호환성 안내다. 이번 Gradle 8.13 빌드 실패 요인은 아니다.

## 본선 준비 한계

현재 SDK 연동은 read-only 진단이다. 실제 차량 상태를 제품의 UX 제한에 연결하고 `CarUxRestrictions`에 따라 입력/내보내기를 제한하는 통합은 추가 작업이 필요하다. NaviHelper 실제 목적지 전달/동작과 에뮬레이터 시연도 남아 있다. 이 환경은 `/dev/kvm`이 없어 가속 에뮬레이터 검증을 진행하지 않았다.
