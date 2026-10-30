# Arrival Brief 검증 기록과 남은 범위

갱신일: 2026-10-30. 코드·단위 테스트·빌드·서명에서 확인한 결과를 기록한다. 실제 차량 사용성, 고객 성과와 공모전 심사 결과는 포함하지 않는다.

## 최신 확인 결과

| 범위 | 결과 | 증거 또는 한계 |
| --- | --- | --- |
| 웹 엔진 | 14개 테스트 통과 | `tests/arrival.test.mjs`. 지원 분류, 복수 후보, 층수, 높이, 출처 상충과 상태 제한 검사 |
| Android 엔진 | 13개 JUnit 테스트 통과 | `verification/TEST-studio.ax.arrival.BriefEngineTest.xml` |
| Android 세션·상태 | 10개 JUnit 테스트 통과 | `verification/TEST-studio.ax.arrival.BriefSessionTest.xml`. 이전 서비스 콜백 차단 포함 |
| release 빌드 | 성공, `0.2.0-prototype`, versionCode 2 | `verification/android-release-build.log` |
| APK 패키지 | `studio.ax.arrival`, minSdk 28, target/compile SDK 35 | `verification/android-release-package.txt` |
| APK 서명 | apksigner v2 검증 성공, RSA 3072비트 | `verification/android-release-signature.txt` |
| Android Lint | 오류 0개, 경고 4개 | 백업 설정 1개, 표시 문자열 리소스 관련 3개 |
| 실제 Pleos 설치·서비스 응답·UX 제한 전환 | OS 초기화·설치 실패, 재검증 필요 | [런타임 기록](verification/android-runtime.md). 앱 설치·첫 실행 성공 아님 |

Android 테스트 합계는 23개다. 웹 14개와 실행 환경·검사 범위가 다르므로 실제 차량 시나리오 37개를 통과한 것으로 표현하지 않는다. 요약과 파일 해시는 [release-verification.json](verification/release-verification.json), 재현 방법은 [ANDROID_BUILD.md](ANDROID_BUILD.md)에 있다.

## 2026-10-30 · 공식 자료 상충 차단

[코엑스 일반차량 주차안내](https://business.coex.co.kr/guide/conference-room-parking-information/general-vehicle/)와 [2026-02-11 공식 주차장 공지](https://business.coex.co.kr/post-meeting-notice/%EC%95%88%EB%82%B4%EC%9E%90%EB%A3%8C-%EC%BD%94%EC%97%91%EC%8A%A4-%EC%A3%BC%EC%B0%A8%EC%9E%A5-%ED%99%94%EB%AC%BC%EC%B0%A8-%EC%A7%84%EC%9E%85-%EC%95%BD%EB%8F%84/)의 추천 구역을 대조했다. 컨퍼런스룸 E와 300~328호·400~403호의 안내가 서로 달라 어느 쪽이 현재 유효한지 확정하지 않았다.

웹과 Android 모두 해당 세 분류를 `source_conflict`로 처리하고 확정·내보내기를 막는다. 따라서 현재는 8개 목적지 분류 중 5개 안내 가능, 3개 확인 보류다. 하역 동선을 승용차 진입이나 승객 하차 경로로 사용하지 않는다.

## 2026-10-30 · 웹 수정

- 마곡 코엑스의 역순 표기와 여러 자리 층수도 검사한다.
- 직접 목적지를 고른 경우에도 층수 충돌을 다시 확인한다. 알 수 없는 목적지 ID를 전달하면 확정할 수 없다.
- 안내문을 바꾸면 이전 선택과 확정을 재사용하지 않는다.
- 주행·상태 미확인 예시에서는 입력·복사·저장을 제한한다.
- 루트 작업에서 브라우저로 출처 상충 화면과 비활성 버튼을 확인했다. 로컬 정적 참조 97개 검사도 통과했다.

위 기록은 웹 프로토타입에 대한 검사다. 실제 차량 상태나 모든 모바일 기기·브라우저의 동작을 검증한 것은 아니다.

## 2026-10-30 · Android 수정

- 초기 상태를 `UNKNOWN`으로 두고, 플랫폼 UX 제한 값을 받기 전에는 입력을 열지 않는다.
- `CarUxRestrictionsManager`의 제한 여부와 활성 제한 값을 읽는 코드를 구현했다. 플랫폼 입력 허용 상태 `PLATFORM_ALLOWED`는 물리적 정차 판정과 다르게 표시한다.
- 자동차 플랫폼에서는 데모 버튼과 데모 진입 경로를 막는다. 일반 Android에서는 안내 확인 후에만 시뮬레이션을 시작한다.
- 브리핑 확정에 전체 높이 확인과 안전한 주차 상태 확인을 모두 요구한다. 높이 2.2m 경계 경고를 화면과 복사 내용에 남긴다.
- 출처 상충·층수 충돌·높이 초과 상태는 확정할 수 없다.
- 제한 상태로 전환되면 입력을 잠그고 마지막 확정 브리핑을 유지한다. 다시 입력이 허용돼도 주차 상태를 재확인해야 확정·복사가 가능하다.
- 화면 회전에는 메모리의 세션을 넘기고, 프로세스 종료 후에는 붙여넣은 원문을 복원하지 않는다.
- 연결이 끊기거나 재연결되면 이전 서비스의 늦은 콜백이 입력 잠금을 풀지 못하도록 연결별 토큰을 검사한다. 해당 두 경로의 회귀 테스트를 추가했다.

이 항목들은 코드와 JUnit으로 확인한 구현이다. Pleos 실행 시도는 OS 초기화와 APK 설치 단계에서 실패했으며 실제 서비스 신호에 따라 UI가 바뀌는 장면을 확인하지 못했다. 앱은 distraction-optimized 활동으로 선언하지 않았으며, 주행 중 운영체제가 화면 전체를 막을 수 있다.

## 2026-10-30 · Pleos 실행 시도 실패

공식 v2.1.0 API 34 x86_64 이미지의 SHA-1을 확인한 뒤 부팅을 시도했다. GPU와 KVM이 없는 현재 환경에서 `sys.boot_completed` 완료를 확인하지 못했고 OS 서비스의 `DeadSystemException`을 관측했다. `adb install`도 `PackageManagerInternal.freeStorage` 관련 null 시스템 오류로 실패했다. 공식 문서의 GPU 요구사항은 충족하지 못했으나 GPU 부재를 단일 실패 원인으로 확정하지 않는다. 상세 관측과 재검증 조건은 [android-runtime.md](verification/android-runtime.md)에 있다.

## release APK와 Lint

현재 release 파일은 `apk/ArrivalBrief_release.apk`, 8,620,730바이트다.

```text
SHA-256 079ce46a3b6f5660e8808d0109033c5004c9fa8d910ac93d68668cdb2d0df16e
```

서명 인증서 CN은 Arrival Brief이고 RSA 3072비트 키를 사용했다. 개인키는 저장소에 포함하지 않았다. 서명 검증은 바이너리의 서명을 확인한 것이며 앱 설치·서비스 연결·주행 안전성 검증을 대신하지 않는다.

Lint 보고서에는 `DataExtractionRules` 1개와 `SetTextI18n` 3개가 남아 있다. 현재 manifest에 `fullBackupContent="false"`를 추가했지만, 보존한 보고서의 경고가 사라졌다고 기록하지 않는다.

## 영상과 화면 자료

최신 영상 2편은 각각 33.000초, 720×1280, 24fps, H.264/yuv420p, 무음이다. 각 792프레임의 전체 디코딩, VTT의 0~33초 연속 구간, 메타데이터의 파일 해시를 확인했다. 도입용 스톡 4초와 실제 웹 화면 캡처 29초를 편집했으며 실차 촬영·연속 화면 녹화·고객 사용 영상이 아니다.

- `arrival-reel.mp4`: 입력 → 확정 → 주행 시뮬레이션. SHA-256 `7d18d350a8b284e731be20f13d66e46c528994fad85f4fb2ed58c94d27ec97b5`
- `arrival-confirm-reel.mp4`: 컨퍼런스룸 E 입력 → 원문 비교 → 확정·저장 보류. SHA-256 `46c00e67e39996938f04a9dc87aaf9e70c379b679ea4af49f04cb08086cc3b98`

## 이전 빌드와 구분

2026-10-28에는 웹 12개, Android 8개 테스트와 debug 빌드를 확인했다. 현재 JUnit XML은 최신 13개·10개 결과로 교체돼 있으므로 과거 개수의 증거로 인용하지 않는다.

이전 커밋의 `apk/ArrivalBrief_debug.apk`는 당시 `0.1.0-prototype` 파일이며 최신 수정이 없다. 이전 `android-build.log`, `android-signature.txt`, `android-package.txt`와 최신 `android-release-*` 기록을 구분한다.

## 제출 운영 확인 사항

1. 공식 접수양식은 심사용 공유 링크·첨부자료의 개인정보·계정정보·개발자 실명 노출을 제한한다. 개인 GitHub 계정을 포함한 저장소 주소와 커밋 기록을 어떤 방식으로 제출할지 운영 측 확인이 필요하다. 개인 계정 주소를 넣고 익명화가 끝났다고 표시하지 않는다.
2. 비공개 저장소 제출에는 심사용 GitHub 계정의 접근 권한이 필요하다. 2026-10-30 확인 범위에서는 초대할 계정이 공지되지 않았다. 계정을 추측해 초대하지 않으며, 운영 측 안내를 받은 뒤 실제 접근 여부를 확인해야 한다.
3. 공식 APK 파일명 형식과 최종 참가명, 접수양식의 파일 경로를 맞춰야 한다. 현재 파일명은 `ArrivalBrief_release.apk`다.
4. 개인정보를 적은 접수양식, 동의서·서명·연락처는 이 저장소에 추가하지 않는다. 메일 접수와 개인정보 동의는 이 검증 작업에 포함하지 않았다.

## 아직 확인하지 않은 것

- 공식 환경 요구사항을 충족하는 환경에서 Pleos APK 설치·첫 실행·서비스 응답·권한·UI 제한을 재검증해야 한다. 이번 시도는 OS 초기화·설치 단계에서 실패했다.
- 실제 차량에서의 확정 브리핑 표시 범위 및 운전 중 사용성
- 공식 자료가 상충하는 세 분류의 최신 주차 구역
- 진입구 좌표, 실시간 주차 빈자리, 실내 경로, 내비 목적지 전달
- 실제 방문자의 시간 절감·만족도·재사용률과 다른 시설로 확장했을 때의 성능
- 최종 접수 자료·심사용 저장소 접근·참가 신청 완료

별도 검토 에이전트는 주최 측 심사위원이 아니다. 단위 테스트 개수나 내부 평가를 수상 가능성과 연결하지 않는다.
