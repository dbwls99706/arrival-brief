# Arrival Brief

행사 장소를 확인하고, 출발 전에 진입문·주차 구역을 정리하는 차량용 앱 프로토타입입니다. 초기 대상은 지원하는 코엑스 삼성동 회의실의 행사에 처음 차량으로 방문하는 운전자입니다. 전시장 Hall A/B/C/D는 현재 지원하지 않습니다.

배포 APK는 `0.3.0-prototype`입니다. 아래 기능의 실제 Pleos 서비스 응답은 아직 검증되지 않았습니다.

## 사용 흐름

회의실 목록에서 선택하거나 안내문 입력 → 행사 시설 확인 → 차량 전체 높이·안전 주차 확인 → 브리핑 확정.

- 8개 회의실 분류 중 5개는 안내하고, 공식 자료가 상충하는 컨퍼런스룸 E·300~328호·400~403호는 확정·내보내기를 보류합니다.
- Android는 사용자 요청으로 NaviHelper의 목적지 이름·주소를 읽고 코엑스 삼성동 회의시설과 대조합니다. 이는 조회 시점의 시설 확인이며 회의실·입구 좌표·경로 확인이 아닙니다. SDK 실기 응답은 아직 검증하지 못했습니다.
- 내비 시설 불일치는 확정을 막습니다. 미조회·판정 불가·서비스 오류에는 직접 시설 확인이 필요하며, 이미 확인된 불일치를 수동 체크나 실패한 재시도로 지울 수 없습니다.
- 차량 플랫폼의 입력 허용과 물리적 정차는 구분합니다. `CarUxRestrictions` 미확인·제한·연결 해제 시 입력을 잠그고, 사용자의 안전 주차 확인도 요구합니다.
- 웹에서는 차량 상태를 시뮬레이션합니다. 높이·주차 확인을 모두 거쳐야 확정하며 실제 NaviHelper 연결이 있는 것처럼 표시하지 않습니다.
- 확정한 목적지 1건을 사용자가 명시적으로 24시간 보관·복구·삭제할 수 있습니다. 목적지 ID·시각·자료 버전만 저장하고 원문·높이·내비 주소는 저장하지 않습니다. 복구는 재확인이 필요한 초안입니다.
- 규칙 기반으로 처리하며 행사 안내문을 외부 AI나 서버로 보내지 않습니다. Vehicle·브레이크 진단 의존성과 관련 권한은 제거했습니다.

## 실행

### 웹

저장소 최상위에서 Python 3으로 실행합니다.

```sh
python -m http.server 8000 --bind 127.0.0.1
```

`http://127.0.0.1:8000/`은 소개 페이지, `/dist/arrival/`은 웹 체험입니다. 별도 패키지나 API 키가 필요 없습니다. 웹은 정차·주행 상태를 직접 바꾸는 체험이고 차량에 연결되지 않습니다.

### Android

배포 파일: [apk/ArrivalBrief_release.apk](apk/ArrivalBrief_release.apk).

```sh
adb install -r apk/ArrivalBrief_release.apk
```

일반 Android에서는 안내를 확인한 후 데모를 시작합니다. 자동차 플랫폼에서는 데모 진입을 차단하고 실제 플랫폼의 입력 허용 신호를 기다립니다. 신호를 확인하지 못하면 잠금을 유지합니다. 설치 명령은 실행 방법이며 Pleos 설치 성공 기록이 아닙니다.

JDK 17, Android SDK 35, Build-Tools 35.0.0과 SDK 라이선스가 필요합니다. Gradle 8.13 wrapper가 포함돼 있으며 최초 의존성 다운로드에는 네트워크가 필요합니다.

```sh
cd android
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleRelease
```

Windows는 `gradlew.bat`를 사용합니다. SDK 위치와 release 서명 환경 설정은 [빌드 안내](docs/ANDROID_BUILD.md)를 참고하세요. 키가 없는 재빌드 결과는 제공한 서명 APK와 구분해야 합니다.

## 검증과 자료

| 검사 | 기록 |
| --- | --- |
| Android JUnit | 37개 통과 |
| 웹 시험 | 21개 통과 |
| 공통 판단 사례 | 50개 - 자동 시험 내 입력 사례 |
| APK | `0.3.0-prototype`, 5,067,560바이트 - 최신 배포 APK |
| 서명 | apksigner v2 검증, RSA 3072비트 |
| Lint | 검증 JSON 기준 오류 0개·경고 0개 |
| Pleos 실행 | OS 초기화·설치 실패로 서비스 응답과 차량 UX 전환 미검증 |

```text
SHA-256 2c8c4e1c99ce27f1f5fc988ac1343476cfae24a5d6b30a5bcafea65271eb31c2
```

일반 Android 9에서 `0.3.0-prototype` 설치·첫 실행·데모 확정·주행 예시 잠금과 목적지 저장 후 프로세스 재시작·초안 복구를 확인했습니다. 복구한 초안은 높이가 비어 있고 다시 확인해야 합니다. [런타임 기록](docs/verification/android-runtime.md)에 환경의 응답 지연과 캡처 범위를 남겼습니다. 실제 Pleos 차량 서비스 응답·성능·장시간 안정성을 검증한 결과는 아닙니다.

[도착 준비 영상](dist/assets/media/arrival-reel.mp4)과 [자료 상충 확인 영상](dist/assets/media/arrival-confirm-reel.mp4)은 각 33초 세로 영상입니다. 스톡 도입과 웹 화면 캡처를 편집했으며 실제 차량 영상이 아닙니다. 촬영·캡처 범위와 파일 해시는 [검증 기록](docs/HYUNDAI_VERIFICATION.md)에서 확인할 수 있습니다.

```sh
node --test tests/arrival.test.mjs
python scripts/check-paths.py
```

## 남은 범위

- 공식 환경에서 Pleos 설치·권한·NaviHelper 응답·CarUxRestrictions 전환을 확인해야 합니다. GPU가 없는 현재 환경의 실패 원인을 GPU 하나로 확정하지 않습니다.
- 휴대폰의 행사 안내를 차량으로 자동 전달하지 않습니다. 목록 직접 선택은 긴 문장 입력을 줄이는 대안입니다.
- 주행 중 프로세스가 종료되면 확정 브리핑은 자동 복원되지 않습니다. 최근 목적지 복구는 입력이 허용된 상태에서만 가능한 초안 복원입니다. 운영체제가 주행 중 화면 전체를 막을 수도 있습니다.
- 실시간 빈자리·입구 좌표·실내 길안내·내비 경로 변경·사용자 효과 측정은 제공·완료 범위가 아닙니다.

## 제출 준비

이 저장소는 공개되어 있어 소스 코드와 APK를 로그인 없이 열 수 있습니다. 실행 방법·테스트 환경·필요한 설정은 위에 정리했습니다. `apk/`에는 검증 기준 파일과 공식 제출명으로 된 동일한 APK를 함께 제공합니다. 두 파일의 버전·바이트·서명은 같습니다.

[scripts/package-submission.py](scripts/package-submission.py)는 소스·APK·영상·검증 기록을 묶는 도구입니다. 접수양식·동의서·연락처·서명 이미지와 서명 개인키는 저장소와 검토용 ZIP에 포함하지 않습니다. 공식 접수양식과 저장소 링크는 운영 접수 이메일로 전달합니다.

`submission/ArrivalBrief-review-0.3.0-prototype.zip`은 검증 당시 소스와 산출물을 보존한 검토본입니다. 그 안의 비공개 저장소 접근 관련 안내는 현재 공개 저장소에는 해당하지 않습니다. 이후 제출 경로 정리는 [운영용 제출 목록](submission/ArrivalBrief-operator-checklist.md)을 기준으로 확인합니다.

## 관련 문서

- [기획과 구현 범위](docs/ARRIVAL_PLAN.md) · [사용자 근거·대안·사업 가설](docs/USER_EVIDENCE.md)
- [모의 심사와 수정 내역](docs/REVIEW_ROUND2.md) · [검증 기록](docs/HYUNDAI_VERIFICATION.md)
- [Android 빌드](docs/ANDROID_BUILD.md) · [SDK 연동 범위](android/README.md) · [출처와 라이선스](ATTRIBUTIONS.md)
- [공식 제출 안내](https://appdevchallenge.auto-hmg.io/guide/submission)

시설 자료 확인일은 2026-10-30입니다. 최신 공식 안내와 현장 표지를 우선합니다.
