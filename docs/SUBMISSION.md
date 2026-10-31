# Arrival Brief 제출 파일 준비

공식 접수양식은 신청자 본인이 동의·서명·날짜를 작성해야 합니다. 기획서와 선택 산출물은 이메일로 제출합니다. 이 저장소의 패키징 도구는 파일을 준비하고 검사하며 접수·동의·초대를 대신하지 않습니다.

## 두 가지 전달 자료

- 익명 검토 ZIP: 앱 소스, APK, 웹 체험, 영상 2편, 검증 기록과 출처를 포함합니다. 개인 GitHub 링크를 지우고 연락처·서명키·빌드 캐시·과거 debug APK는 제외합니다. 외부 라이선스와 원저작자 표시는 보존합니다. 외부 디자인 근거와 모의 전문가 검토 기록도 포함하되, 공식 심사 결과나 사용자 조사로 표현하지 않습니다. 임시 QA 페이지는 포함하지 않습니다.
- 운영용 체크리스트: 실제 개인 저장소 주소, APK 경로, 파일 해시와 본인/운영 확인 항목을 기록합니다. 익명 ZIP에는 포함하지 않습니다.

이 분리 자체가 운영 측 승인을 의미하지 않습니다. 개인 GitHub 주소를 어떤 경로로 전달해야 하는지와 심사용 계정 접근은 공식 안내 또는 운영 확인이 필요합니다. 공식 안내는 소스와 APK를 GitHub에 함께 제출하도록 하므로 ZIP 단독 제출로 대체하지 않습니다.

## 생성

Python 3 표준 라이브러리만 사용합니다. 저장소 최상위에서 실행하고 결과 폴더는 저장소 밖으로 지정합니다. `--repository-url`에는 실제 운영용 저장소 주소를 넣고, 신청자 이름·이메일·전화번호 등 제외할 문자열은 `--private-token`으로 추가합니다.

```sh
python scripts/package-submission.py \
  --output /absolute/path/to/submission-output \
  --repository-url https://github.com/OWNER/REPOSITORY \
  --private-token APPLICANT_NAME \
  --private-token APPLICANT_EMAIL
```

스크립트는 현재 APK와 `docs/verification/release-verification.json`의 해시·크기·버전이 같은지 확인합니다. APK를 `dist/downloads/ArrivalBrief_release.apk`로 동기화하므로 `dist`를 정적 서버 루트로 사용하는 경우에도 다운로드가 가능합니다. 변경된 파일은 다시 패키징해야 합니다.

## 확인

```sh
python scripts/check-paths.py
python scripts/check-paths.py --static-root dist
python scripts/package-submission.py --verify /absolute/path/to/ArrivalBrief-review-VERSION.zip
```

ZIP 안의 `SUBMISSION-MANIFEST.json`은 파일별 크기·SHA-256과 앱 소스 목록을 담습니다. 검사는 ZIP의 앱 소스가 현재 작업 폴더와 같은지도 비교합니다. 이 비교는 소스 전달 누락과 변경을 찾는 것이며 APK의 비트 단위 재현 빌드나 실제 Pleos 실행을 증명하지 않습니다.

검토 ZIP을 풀고 저장소 최상위에서 `python -m http.server 8000 --bind 127.0.0.1`을 실행하면 웹 체험과 다운로드를 열 수 있습니다. 정적 배포는 `dist`를 루트로 사용합니다. 실제 HTTP 검사에서는 내려받은 APK의 SHA-256도 원본과 같아야 합니다.

## 최종 확인

공식 APK 파일명 형식은 `팀명_앱이름_release.apk`입니다. 참가명과 익명 심사 지침의 관계를 운영 안내에 따라 확인한 뒤 최종 제출명을 정합니다. 파일명을 바꾸면 제출 목록도 함께 갱신합니다.

두 시연 영상은 동봉한 웹 프로토타입의 화면 캡처 편집본입니다. 버전과 캡처 방식은 영상 metadata와 검증 기록에서 확인합니다. 세부 동작은 실행 체험과 현재 검증 기록을 기준으로 확인하며, 웹 영상이 실제 차량 SDK 응답을 보여준다고 표현하지 않습니다. 일반 Android 실행과 Pleos 차량 서비스 검증은 별도입니다.

공식 기준: [제출 안내](https://appdevchallenge.auto-hmg.io/guide/submission), [FAQ](https://appdevchallenge.auto-hmg.io/faq), [심사 기준](https://appdevchallenge.auto-hmg.io/guide/judging-awards).
