# Arrival Brief 제출 파일 준비

기획서와 선택 산출물은 공식 안내에 따라 이메일로 제출합니다. 접수양식에는 신청자가 승인한 기존 동의·서명·날짜를 유지합니다. 이 저장소의 패키징 도구는 파일을 준비하고 검사하며 이메일을 발송하지 않습니다.

## 두 가지 전달 자료

- 익명 검토 ZIP: 앱 소스, APK, 웹 체험, 영상 3편, 검증 기록과 출처를 포함합니다. 개인 GitHub 링크를 지우고 연락처·서명키·빌드 캐시·과거 debug APK는 제외합니다. 외부 라이선스와 원저작자 표시는 보존합니다. 외부 디자인 근거와 모의 전문가 검토 기록도 포함하되, 공식 심사 결과나 사용자 조사로 표현하지 않습니다. 임시 QA 페이지는 포함하지 않습니다.
- 운영용 체크리스트: 실제 개인 저장소 주소, APK 경로, 파일 해시와 본인/운영 확인 항목을 기록합니다. 익명 ZIP에는 포함하지 않습니다.

공개 저장소의 소스와 APK는 별도 계정 초대 없이 열 수 있습니다. 실제 저장소 링크는 운영 접수 이메일에 기재하고, 심사용 ZIP에서는 신청자 식별 정보를 제외합니다. 이 분리 자체가 운영 측의 익명 심사 승인을 뜻하지는 않습니다. GitHub 링크도 함께 제공하며 ZIP 단독 제출로 대체하지 않습니다.

## 생성

Python 3 표준 라이브러리만 사용합니다. 저장소 최상위에서 실행하고 결과 폴더는 저장소 밖으로 지정합니다. `--repository-url`에는 실제 운영용 저장소 주소를 넣고, 신청자 이름·이메일·전화번호 등 제외할 문자열은 `--private-token`으로 추가합니다.

```sh
python scripts/package-submission.py \
  --output /absolute/path/to/submission-output \
  --repository-url https://github.com/OWNER/REPOSITORY \
  --private-token APPLICANT_NAME \
  --private-token APPLICANT_EMAIL
```

스크립트는 현재 APK와 `docs/verification/release-verification.json`의 해시·크기·버전이 같은지 확인합니다. 저장소 APK와 `dist/downloads/ArrivalBrief_release.apk`의 바이트 동일성을 검사하며 파일을 자동 복사하지 않습니다. 두 파일은 패키징 전에 동일하게 준비해야 합니다. 익명 ZIP에는 `dist/downloads/ArrivalBrief_release.apk` 한 곳에만 APK를 담으며 `dist`를 정적 서버 루트로 사용하는 경우에도 다운로드가 가능합니다. 또한 `build-inputs.json`의 APK 해시·버전·기록된 입력 해시를 현재 소스와 ZIP에 담긴 원본에 대조합니다. 빌드 뒤 소스가 바뀌어 이전 APK와 섞이면 포장을 중단하므로 다시 빌드해야 합니다. 이는 성공한 빌드의 입력과 제출 소스가 같다는 검사이며 바이트 단위 재현 빌드를 주장하지 않습니다. 변경된 파일은 다시 패키징해야 합니다.

## 확인

```sh
python scripts/check-paths.py
python scripts/check-paths.py --static-root dist
python scripts/package-submission.py --verify /absolute/path/to/ArrivalBrief-review-VERSION.zip
```

익명 ZIP은 전송 용량을 줄이기 위해 APK의 루트 `apk` 복사본을 제외하고, 이전 캡처 3쌍에서 바이트가 같은 중복만 한 파일로 통합합니다. 앱 소스·APK 바이트·고유 캡처·30초 편집 영상 3편과 검증 결과는 유지합니다. 178.357초 연속 원본 영상은 전체 운영 ZIP과 공개 저장소의 개별 파일에 별도로 보존합니다. 파일 위치 변경과 해시는 ZIP의 `PACKAGE-CONTENTS.md` 및 manifest에 기록합니다.

ZIP 안의 `SUBMISSION-MANIFEST.json`은 파일별 크기·SHA-256과 앱 소스 목록을 담습니다. 검사는 ZIP의 앱 소스가 현재 작업 폴더와 같은지도 비교합니다. 이 비교는 소스 전달 누락과 변경을 찾는 것이며 APK의 비트 단위 재현 빌드나 실제 Pleos 실행을 증명하지 않습니다.

검토 ZIP을 풀고 저장소 최상위에서 `python -m http.server 8000 --bind 127.0.0.1`을 실행하면 웹 체험과 다운로드를 열 수 있습니다. 정적 배포는 `dist`를 루트로 사용합니다. 실제 HTTP 검사에서는 내려받은 APK의 SHA-256도 원본과 같아야 합니다.

## 최종 확인

운영 제출용 APK는 공식 형식인 `참가명_앱이름_release.apk`를 사용하고, 익명 검토 ZIP에는 `ArrivalBrief_release.apk`로 넣습니다. 파일명은 달라도 바이트와 서명이 같아야 합니다. 실제 파일명·해시는 운영용 제출 목록에서 대조합니다.

현재 후보 버전은 `1.0.0-rc1`입니다. 기능·검증의 한계는 [제품 범위](PRODUCT_SCOPE.md)와 [검증 기록](HYUNDAI_VERIFICATION.md)에 함께 기록합니다.

두 웹 영상은 `1.0.0-rc1`의 실제 화면 캡처를 편집한 자료입니다. 각각 30초·1280×720이며 도착 준비·확정·주행 시뮬레이션, 공식 자료 상충과 운영 제한에 따른 보류를 보여줍니다. 웹의 차량 상태는 시뮬레이션이며 연속 녹화·실차 촬영·Pleos 서비스 응답의 증거가 아닙니다. 영상 metadata에 버전과 캡처 방식을 기록하며 일반 Android·Pleos 검증과 구분합니다.

새 Android 검증 영상 `arrival-native-validation.mp4`는 현재 후보의 일반 Android debug 자동 UI 시험에서 얻은 실제 화면 5장면을 편집한 30초 자료입니다. 확정·자료 상충·메모 복구·QR·주행 잠금을 보여줍니다. 연속 화면 녹화, Pleos 서비스 성공, 휴대폰 광학 스캔의 증거는 아닙니다. APK·캡처의 정확한 버전과 해시는 영상 metadata와 런타임 기록을 따릅니다.

공식 기준: [제출 안내](https://appdevchallenge.auto-hmg.io/guide/submission), [FAQ](https://appdevchallenge.auto-hmg.io/faq), [심사 기준](https://appdevchallenge.auto-hmg.io/guide/judging-awards).
