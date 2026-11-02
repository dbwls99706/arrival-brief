# Arrival Brief Android 빌드 검증

배포 후보는 `1.0.0-rc1`입니다. 빌드·단위시험·서명과 일반 Android 실행, 실제 Pleos 서비스 응답을 각각 구분합니다.

## APK와 시험 근거

| 항목 | 기록 |
| --- | --- |
| 증거 버전 | `1.0.0-rc1` (versionCode 10) |
| APK | `apk/ArrivalBrief_release.apk`, 6,658,807바이트 |
| 패키지 | `studio.ax.arrival` |
| compile / minimum / target | 35 / 28 / 35 |
| 서명 | APK v2 검증, RSA 3072비트, CN=Arrival Brief |
| Lint | 오류 0개, 경고 0개 |
| 일반 Android / Pleos 실행 | APK 버전·해시별 [런타임 기록](verification/android-runtime.md) 참조 |

```text
SHA-256 04114df0de41218e21b293609e6625983f4eb615d77ade959ec3bb2a8c48e8bf
```

| JUnit 클래스 | 시험 | 실패 | 오류 |
| --- | ---: | ---: | ---: |
| BriefEngineTest | 42 | 0 | 0 |
| BriefSessionTest | 12 | 0 | 0 |
| NavigationCheckTest | 8 | 0 | 0 |
| ParkingMemoQrTest | 4 | 0 | 0 |
| RecentDestinationTest | 2 | 0 | 0 |
| VisitCompanionTest | 5 | 0 | 0 |



합계 73개는 검증 JSON에 보존된 단위시험 결과입니다. 웹 50개와 공통 입력 287건을 별도로 기록하며 공통 사례를 시험 수에 중복 합산하지 않습니다. 실제 차량 시나리오를 같은 수만큼 통과했다는 뜻은 아닙니다.

## 현재 소스의 주요 구조

`BriefEngine`과 웹 엔진은 같은 입력 fixture를 검사한다. `BriefSession`은 장소·높이·주차·시설 확인과 확정 상태를 관리한다. `PlatformVehicleGateway`는 실제 플랫폼의 UX 제한을 입력 허용 판단에 연결하며 물리적 정차를 추정하지 않는다.

`NavigationDestinationReader`는 NaviHelper 3.0.5의 목적지 이름·주소를 조회하고 순수한 `NavigationMatcher`에 전달한다. 시설명·주소가 일치한 결과만 MATCHED로 분류한다. 미확인·오류·미설정·불일치와 수동 확인을 구분한다. 이름·주소는 저장하지 않고 목적지나 경로도 바꾸지 않는다. Vehicle SDK와 브레이크 읽기는 제거했다.

`ParkingMemoStore`는 명시적으로 입력한 64자 메모 1건을 24시간 보관합니다. 별도의 안전 확인과 플랫폼 입력 허용이 필요하며 기기 간 동기화는 하지 않습니다. `SharedNoticePolicy`는 같은 기기의 텍스트 공유를 제한된 메모리 원문으로 받아 명시적 가져오기까지 보류합니다.

`RecentDestinationStore`는 사용자가 저장한 목적지 ID·시각·자료 버전 1건을 24시간 유지한다. 원문·높이·확정 브리핑은 저장하지 않는다. 복구는 입력 허용 시에만 가능한 초안 복원이고, 높이·장소·주차를 다시 확인해야 한다. 주행 중 프로세스 종료 후 확정 정보를 자동 복원하는 기능은 없다.

SDK 콜백의 데이터 타입을 확인하고 상태 로직을 시험한 사실과 실제 Pleos 서비스가 응답했다는 사실은 다르다. 실제 실행 한계는 [런타임 기록](verification/android-runtime.md)에 있다.

## 일반 Android와 Pleos 실행

일반 Android 9에서 최종 서명 release의 설치·첫 실행과 기기 내 APK SHA-256 일치를 확인했습니다. 같은 앱 소스로 만든 별도 debug APK의 표준 UI 13개와 글자 크기 1.3배 반복 6개가 통과했으며 해당 로그에서 FATAL·ANR은 0건입니다. 반복 6개는 새로운 기능 수로 합산하지 않습니다. [최종 런타임 JSON](verification/android-runtime-rc1.json)에 환경·빌드 종류·해시를 기록했습니다.

현재 APK의 설치·업데이트·첫 실행 관찰은 [런타임 기록](verification/android-runtime.md)에서 해당 버전과 해시를 대조합니다. Debug instrumentation으로 조작한 시나리오는 서명 release APK의 단순 설치·실행 결과와 별도로 표시합니다. 환경의 실제 오류와 미완료 항목을 보존하고 일반 Android 데모를 Pleos 서비스의 응답 성공으로 바꾸지 않습니다.

과거 0.3·0.4·0.5 기록은 각 버전의 역사 자료입니다. 현재 버전의 새 공유·메모·파서 동작을 검증한 것으로 자동 승계하지 않습니다. 성능·장시간 안정성·물리적 차량 시인성은 단위시험과 CPU 에뮬레이션으로 보증하지 않습니다.

## 정적 검사

최종 Lint의 오류·경고는 위 표와 검증 JSON을 따릅니다. 백업 제외 규칙, 입력 잠금과 민감한 원문 처리 정책은 소스와 실제 동작을 함께 확인해야 합니다.

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

- [build-inputs.json](verification/build-inputs.json): 성공한 빌드의 APK·입력 해시. 패키징 시 현재 소스와 ZIP 원본을 대조하며 바이트 재현 빌드를 주장하지 않음

- [release-verification.json](verification/release-verification.json): APK·시험·Lint·런타임 버전별 상태
- [android-release-build.log](verification/android-release-build.log): 보존 빌드 로그
- [android-release-signature.txt](verification/android-release-signature.txt): 서명 검사
- [android-release-package.txt](verification/android-release-package.txt): 패키지 검사
- `TEST-*.xml`: 해당 빌드의 JUnit 결과
- [현재 검증 기록](HYUNDAI_VERIFICATION.md): 기능·수정·미완료 경계

이전 커밋의 0.1.0 debug와 0.2.0 release 증거는 이전 버전의 결과다. 새 기능의 성공 근거로 재사용하지 않는다. 파일명에 `android-release-`가 없는 이전 로그는 현행 빌드 검사와 구분한다.
