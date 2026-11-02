# RC4 변경 코드 독립 검토

검토 시각: 2026-11-02T10:49:54.061649+00:00. 비교 기준 커밋: `501e9e90a202d0b1ec133e5621450c805388c94a`.

## 범위와 결론

QR 크기 전환과 안내문 보존 문구를 작성하지 않은 별도 검토자가 현재 작업 트리의 변경 및 관련 정책·수명주기 코드를 검토했다. 검토 대상에서 수정이 필요한 차단 회귀는 발견하지 못했다. 이 결과는 점수, 출시 승인, QR 광학 판독 성공 또는 실제 Pleos 연동 성공을 뜻하지 않는다. 검토 시점에 RC4 실제 APK 빌드·UI 실행·화면 캡처 검증은 진행 중이므로, 이후 실행 결과를 이 문서의 결과로 미리 간주하지 않는다.

검토를 위해 제품 소스나 UI 시험 코드는 변경하지 않았다. 아래 해시는 읽은 파일의 바이트를 고정하며 이후 변경에는 재검토가 필요하다.

## 확인한 코드 경로

| 대상 | 확인한 동작과 근거 | 코드 검토의 한계 |
| --- | --- | --- |
| 확대·축소 | 확대 버튼의 `!memoQrEnlarged`는 함수 진입 전 평가된다. `showMemoQr`가 기존 표시를 먼저 지운 뒤 요청 크기로 새 QR을 만든다. 닫기는 크기·버튼 문구·확대 상태를 기본값으로 돌린다. | 실제 버튼 조작·프레임 배치는 실행 결과 필요 |
| 내용·시각 유지 | 확대 시에도 저장된 `ParkingMemo`와 `savedAt`을 다시 읽는다. 기존 payload·UTF-8·M 오류 정정·quiet zone·mask 선택 코드는 바뀌지 않았다. 화면 크기에 맞게 비트맵을 다시 생성한다. | 다른 화면 크기를 선택했다고 모든 판독기에서 성공하는 것은 아님 |
| 입력 잠금 | `showMemoQr`는 매번 플랫폼/데모 입력 허용과 안전 주차 체크를 다시 확인하고, 저장 메모와 입력 초안의 동일성 및 만료를 `canDisplay`로 재확인한다. 확대 버튼은 이 경로를 우회하지 않는다. | 실제 플랫폼 제한 수신은 미검증 |
| 수정·삭제·정차 해제 | 메모 입력 변경, 저장, 삭제, 안전 체크 해제, 주행·미확인 전환이 공통 `clearMemoQr`를 호출한다. 저장 전 수정 내용으로 QR을 재생성하지 않는다. | 파일 삭제 실패의 별도 동작은 기존 정책 그대로이며 이번 수정 대상이 아님 |
| 백그라운드·재생성 | `onPause`에서 QR 제거와 입력 잠금, `onDestroy`에서 QR 제거·만료 작업 취소가 수행된다. ImageView 저장은 비활성이고 확대 상태는 saved instance state에 넣지 않는다. | OS 화면 캐시·실차 디스플레이 동작은 코드만으로 확인 불가 |
| 만료 | 표시 중에도 기존 저장 시각의 24시간 기한 작업이 QR을 지우고 메모를 다시 조회한다. 확대·축소로 저장 시각을 갱신하거나 만료 기한을 연장하지 않는다. | 실제 기한을 넘기는 UI 시험 결과는 별도 필요 |
| 비트맵 제거 | drawable을 먼저 분리하고 mutable 비트맵을 흰색으로 지운 뒤 참조를 해제한다. 생성 중 임시 색 배열·BitMatrix도 지운다. 이전 예약 scroll 작업은 비트맵 동일성·패널 표시·입력 허용을 다시 검사한다. | 메모리의 모든 복사본이나 GPU 캐시의 포렌식 삭제를 입증하는 구현은 아님 |
| 화면 크기 | 확대 크기는 가용 폭, 화면 높이와 fontScale, 상한 480dp/1024px로 제한된다. 기본보다 커질 수 없으면 확대 버튼을 숨긴다. 버튼 두 개는 같은 행이며 QR·닫기를 위한 높이를 남긴다. | 실제 창 크기를 측정한 보편적 적합성 증명은 아님. 최소 창·높은 글자 배율·시스템 바 조건은 실행 확인 필요 |
| 안내문 보존 | Android/Web의 입력 도움말과 혼합 층 복구 안내가 개인정보만 제거하도록 바뀌었다. 일정·등록·변경·취소·진입·주차 제한을 제거하라는 기존 지시는 사라졌다. 분기 조건·상충 차단·확정 허용 계산은 바뀌지 않았다. | 안내문의 임의 문장형을 새로 해석하는 기능 추가는 아님 |

## 시험 코드 검토

새 확대 시험은 실제 저장 객체의 시각이 저장 전후 테스트 시계 범위에 있는지 확인하고, 기본→확대→기본 payload 동일성, 이전 비트맵 픽셀 제거, 닫기·재열기 기본 크기 복귀를 검사한다. 기존 수정·주행·백그라운드·재생성·삭제·실제 기한 만료 시험도 확대가 가능한 경우 확대 상태에서 진행하도록 바뀌었다.

확대 시험의 기본 payload를 다시 디코드해 비교하는 부분은 **확대 전후 동일성** 검사다. 독립적인 원본 내용·저장 시각 판독 검증의 대체물이 아니다. 별도로 저장하는 합성 메모와 실제 저장 시각/테스트 시계 범위가 이후 캡처 판독의 원본 기준으로 사용되어야 한다.

크기 제한 화면에서는 확대 버튼이 없어도 시험의 축소 화면 분기가 통과하도록 설계됐다. 따라서 통과 개수만으로 큰 화면에서 확대가 실제 제공됐다고 단정하지 말고, 해당 환경의 `qr`·`qr-expanded` 원본 캡처와 크기 차이를 확인해야 한다. 코드 검토 시점에는 이 RC4 실행 결과를 아직 검증하지 않았다.

## 별도 도구 시험 기록

앞서 추가한 `scripts/collect-pleos-evidence.py`의 합성 fake-adb 계약시험은 다음 명령으로 **8개 통과**했다. 실행 도구에 `Ran 8 tests in 1.508s / OK`가 반환됐으며 별도 raw stdout 파일은 저장하지 않았다. 여기서는 같은 시험을 다시 실행하지 않았다.

```sh
python scripts/test-collect-pleos-evidence.py
```

이 8개는 개인정보 출력 제외, 기존 증거 덮어쓰기 거부, 없는 기기·서비스, 권한 불명/충돌, 다른 앱·입력 필드 배제, APK 해시 불일치와 셸 경로 검사를 다루는 **합성 도구 시험**이다. Android 앱 UI 시험·실제 Pleos 실행 수와 합산하지 않는다. 해당 수집 도구의 작성자와 본 검토자는 같으므로, 이 문서의 독립 검토 범위는 위 QR·안내문 변경에 한정한다.

## 검토 파일 SHA-256

| 파일 | SHA-256 |
| --- | --- |
| `android/app/src/main/java/studio/ax/arrival/MainActivity.kt` | `782092123ed7c236dc3cc0b53825af2ab0605a698b1413e539ab4f4e1b5b6019` |
| `android/app/src/androidTest/java/studio/ax/arrival/VisitFlowInstrumentedTest.kt` | `b0914ad5c1f6ffe33e1a3388d63e6758f20e7ba05ea05387d6a36ed3b18d4921` |
| `android/app/src/main/java/studio/ax/arrival/ParkingMemoQr.kt` | `59a1b3808f059bdf94cec6c9168fd2b1afebd5e58f88ae2f94b2c1af328e796b` |
| `android/app/src/main/java/studio/ax/arrival/VisitCompanion.kt` | `95d3d82e64ad710aab1fe15669403acf4b1a2ba23559376d6c5ef19e4539234e` |
| `android/app/src/main/java/studio/ax/arrival/ParkingMemoStore.kt` | `c809ba00ce87a9a8997689417bd56148aade6bbe123c03961676475691d74200` |
| `android/app/src/main/java/studio/ax/arrival/BriefEngine.kt` | `10fee6506bdd76c19c9892324add33cd4a6ef9cca192a88eac3369bf9492304a` |
| `android/app/src/test/java/studio/ax/arrival/BriefEngineTest.kt` | `1dda94f62be839be34556d57a7a8cdf385865dde0427c74b6f6002c89063f832` |
| `dist/arrival/engine.mjs` | `479bb9aa1b2984a1ae538644137f2d4c50cb2d827898197e31fbbd1e0fa4dd1f` |
| `dist/arrival/index.html` | `dd8672ad0ff9f3cb5217784c5eea535fc4fe07b31a84f5a877b4bb7564357273` |
| `tests/arrival.test.mjs` | `c440b587616fd525e85322ade352a19b8dfe0462f7eb08777b2efb8b766ea05c` |
