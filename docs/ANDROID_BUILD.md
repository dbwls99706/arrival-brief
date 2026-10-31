# Arrival Brief Android 빌드 검증

배포 APK는 `0.4.0-prototype`입니다. 아래 기능의 실제 Pleos 서비스 응답은 아직 검증되지 않았습니다.

## APK와 시험 근거

| 항목 | 기록 |
| --- | --- |
| 증거 버전 | `0.4.0-prototype` - 최신 배포 APK |
| APK | `apk/ArrivalBrief_release.apk`, 5,077,368바이트 |
| 패키지 | `studio.ax.arrival` |
| compile / minimum / target | 35 / 28 / 35 |
| 서명 | APK v2 검증, RSA 3072비트, CN=Arrival Brief |
| Lint | 오류 0개, 경고 0개 |
| Pleos 실행 | 시스템 초기화·설치 실패, 실제 서비스 응답 미검증 |

```text
SHA-256 40ce148be83d608acae1e7bd1bb381619c8ad7896ee7f01fa2cd354d6e4a276f
```

| JUnit 클래스 | 시험 | 실패 | 오류 |
| --- | ---: | ---: | ---: |
| BriefEngineTest | 17 | 0 | 0 |
| BriefSessionTest | 10 | 0 | 0 |
| NavigationCheckTest | 8 | 0 | 0 |
| RecentDestinationTest | 2 | 0 | 0 |

위 개수는 검증 JSON에 보존된 결과다. 실제 차량 시나리오를 같은 수만큼 통과했다는 뜻은 아니다. 최신 시험 XML과 빌드·서명 로그는 `docs/verification/`에서 확인한다.

## 현재 소스의 주요 구조

`BriefEngine`과 웹 엔진은 같은 입력 fixture를 검사한다. `BriefSession`은 장소·높이·주차·시설 확인과 확정 상태를 관리한다. `PlatformVehicleGateway`는 실제 플랫폼의 UX 제한을 입력 허용 판단에 연결하며 물리적 정차를 추정하지 않는다.

`NavigationDestinationReader`는 NaviHelper 3.0.5의 목적지 이름·주소를 조회하고 순수한 `NavigationMatcher`에 전달한다. 시설명·주소가 일치한 결과만 MATCHED로 분류한다. 미확인·오류·미설정·불일치와 수동 확인을 구분한다. 이름·주소는 저장하지 않고 목적지나 경로도 바꾸지 않는다. Vehicle SDK와 브레이크 읽기는 제거했다.

`RecentDestinationStore`는 사용자가 저장한 목적지 ID·시각·자료 버전 1건을 24시간 유지한다. 원문·높이·확정 브리핑은 저장하지 않는다. 복구는 입력 허용 시에만 가능한 초안 복원이고, 높이·장소·주차를 다시 확인해야 한다. 주행 중 프로세스 종료 후 확정 정보를 자동 복원하는 기능은 없다.

SDK 콜백의 데이터 타입을 확인하고 상태 로직을 시험한 사실과 실제 Pleos 서비스가 응답했다는 사실은 다르다. 실제 실행 한계는 [런타임 기록](verification/android-runtime.md)에 있다.

## 일반 Android 실행

현재 0.4.0 APK의 일반 Android 9 설치·첫 실행·명시적 데모·확정·주행 잠금과 가로 화면 표시를 확인했습니다. 실제 Pleos 환경 검증과는 구분합니다.

아래는 이전 0.3.0의 기록입니다. 일반 Android 9에서 0.3.0의 업그레이드 설치·첫 실행·명시적 데모·시설/높이/안전 주차 확인 후 확정·주행 예시 잠금·목적지 보관·프로세스 종료 후 초안 복구를 확인했다. 복구한 초안은 높이가 비어 있고 모든 확인이 해제되며 확정 버튼도 비활성 상태였다. 실제 NaviHelper 응답과 차량 UX 전환을 확인한 결과는 아니다. 소프트웨어 에뮬레이션 환경의 System UI 응답 지연·UIAutomator null root로 성능과 장시간 안정성은 평가하지 않았다. 상세 캡처는 [런타임 기록](verification/android-runtime.md)에 있다.

## 정적 검사

보존한 검사 결과에 Lint 오류·경고가 없다.

현재 소스는 한국어 표시 문구를 리소스로 분리하고 백업 제외 규칙을 사용한다. 이전 경고가 없어졌다는 판단은 새 Lint 결과가 기록된 경우에만 한다.

## 재현 방법

JDK 17, Gradle 8.13 wrapper, AGP 8.7.3, Kotlin 2.0.21, Android SDK 35와 Build-Tools 35.0.0을 사용한다. SDK는 `ANDROID_HOME` 또는 로컬 `local.properties`로 지정한다.

```sh
cd android
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleRelease
```

release 서명 설정은 `ARRIVAL_RELEASE_KEYSTORE`와 `ARRIVAL_RELEASE_PASSWORD`를 읽으며 키 별칭은 `arrival-release`다. 값은 로컬 비밀 설정으로 전달하고 로그나 저장소에 남기지 않는다. 둘 중 하나라도 없으면 서명이 적용되지 않을 수 있으므로 재빌드 파일을 확인 없이 제출하지 않는다.

서명 인증서 SHA-256은 `5beae876e311240cd57d9a18d9cad248a29b6ffd4e2d7e4e37fecf808f6b0893`다. 이는 독립 프로젝트의 인증서이며 현대자동차그룹 승인 표시가 아니다.

```sh
adb install -r apk/ArrivalBrief_release.apk
```

저장소 최상위에서 실행한다. 다른 키로 서명한 debug APK가 설치된 경우 업데이트 설치가 실패할 수 있다. 앱을 제거하면 로컬 데이터가 삭제되므로 별도 시험 환경에서 실행한다.

## 증거 파일

- [release-verification.json](verification/release-verification.json): APK·시험·Lint·런타임 버전별 상태
- [android-release-build.log](verification/android-release-build.log): 보존 빌드 로그
- [android-release-signature.txt](verification/android-release-signature.txt): 서명 검사
- [android-release-package.txt](verification/android-release-package.txt): 패키지 검사
- `TEST-*.xml`: 해당 빌드의 JUnit 결과
- [모의심사 수정 내역](REVIEW_ROUND2.md): 결함·수정·미완료 경계

이전 커밋의 0.1.0 debug와 0.2.0 release 증거는 이전 버전의 결과다. 새 기능의 성공 근거로 재사용하지 않는다. 파일명에 `android-release-`가 없는 이전 로그는 현행 빌드 검사와 구분한다.
