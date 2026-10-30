# Arrival Brief APK

최신 파일은 [ArrivalBrief_release.apk](ArrivalBrief_release.apk)다. 2026-10-30 생성한 서명 release 빌드이며, Pleos 에뮬레이터에서는 OS 초기화와 APK 설치가 실패해 재검증이 필요하다. 앱 설치·첫 실행 성공 결과는 아니다. [런타임 기록](../docs/verification/android-runtime.md)에 관측 결과와 재검증 조건을 남겼다.

| 항목 | 값 |
| --- | --- |
| 패키지 | `studio.ax.arrival` |
| 버전 | `0.2.0-prototype`, versionCode 2 |
| minimum / target / compile SDK | 28 / 35 / 35 |
| 크기 | 8,620,730바이트 |
| 서명 | apksigner v2 검증 성공, CN=Arrival Brief, RSA 3072비트 |
| SHA-256 | `079ce46a3b6f5660e8808d0109033c5004c9fa8d910ac93d68668cdb2d0df16e` |

서명키와 비밀번호는 저장소 외부에서 관리한다. 이 서명은 프로젝트의 release 서명이며 플랫폼 승인이나 실차 검증을 의미하지 않는다. 설치·검사 방법과 로그는 [Android 빌드 기록](../docs/ANDROID_BUILD.md)에 있다.

## 이전 버전

0.1.0 debug 파일은 최신 출처 상충 처리와 입력 제한을 포함하지 않아 현행 배포 목록에서 제외했습니다. 과거 파일은 저장소의 이전 커밋에 남아 있습니다. 현재 사용할 파일은 위 release APK입니다.
