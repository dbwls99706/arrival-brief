# Pleos 실행 조건과 후속 검증

검토일: 2026-11-02 KST. 이 문서는 공식 문서와 실제 호스트 점검, 이전 부팅 로그, 추가 진단의 범위를 구분한다.

## 확인한 실행 조건

- 공식 현행 버전은 Pleos Connect Emulator 2.1.0이며, NaviHelper는 3.0.5다. 현재 앱 의존성은 해당 SDK 버전과 일치한다.
- 공식 설치 안내는 API 34, Cold boot, RAM 4 GB와 Hardware graphics를 안내한다. 그래픽 설정 문서는 GPU를 필수로 명시하며 최소 예시로 RTX 3060 Laptop 6 GB 또는 RX 6600M/RX 6700S 8 GB를 제시한다.
- 현재 호스트에는 `/dev/kvm`, `/dev/dri`, NVIDIA 장치가 없다. `emulator -accel-check`는 종료 코드 3과 `KVM requires a CPU that supports vmx or svm`을 반환했다. 컨테이너 메모리 한도는 8 GiB이며 연결된 실제 Android 기기도 없었다.
- 따라서 이 호스트를 공식 지원 조건에 맞게 복구했다고 주장할 수 없다. GPU 부재만으로 과거 부팅 정지의 단일 원인을 확정하지도 않는다.

## 기존 20분 시도의 상세 재검토

기존 공식 이미지 체크섬은 SHA-1 `b4f858d5bcc6c71506d7edfc872bcb9e5a11acaa`이며, [기존 최종 결과](pleos-final-04114df-summary.json)는 앱 설치 이전에 부팅이 막힌 상태다.

첫 부팅에서 default-permission callback 대기는 16:05:08.707에 시작했고 Watchdog kill은 16:05:52.247에 기록됐다. 두 번째 대기 시작은 16:13:28.885, kill은 16:14:22.857이다. 각 약 43.5초와 54.0초이므로, Watchdog의 전체 main-thread 정체 131초·129초가 이 callback 하나에서 발생했다고 해석하면 안 된다.

처음 boot dexopt는 약 0.4초, 재시작에서는 약 6.8초 후 종료됐다. 수집 로그에서 PermissionController APK 등록은 확인했지만 해당 서비스의 성공적인 시작·callback 완료는 확인하지 못했다. `android.perm` ServiceConnector 작업의 6.583초·4.343초 전달 지연과 runtime-permission persistence의 37.881초·23.782초 처리 지연이 있었다. 전반적인 느린 부팅/스케줄링은 조사할 가설이며, 이 로그만으로 데드락이나 유일한 근본 원인을 확정할 수 없다. [발췌 근거](completion-pleos-prior-excerpt.txt)를 보존했다.

## 8분 추가 진단 결과

원본 AVD를 보존한 별도 복사본에서 vCPU를 2개에서 4개로 늘리고, RAM 4 GiB·Cold boot·소프트웨어 그래픽으로 480초를 관찰했다. 정리 시간을 포함한 실제 경과는 481.22초다. 시스템 이미지·앱·차량 상태 판정 코드는 바꾸지 않았다.

AOSP가 느린 소프트웨어 에뮬레이터용으로 제공하는 `ro.hw_timeout_multiplier=5`를 요청했지만, 현재 Android Emulator는 `qemu.*` 외 속성이라는 경고로 이를 거부했다. `getprop`에서도 적용값을 확인하지 못했다. 이어 `settings put global watchdog_timeout_millis 300000`을 실행했으나 Settings 서비스가 아직 없어 명령·readback 모두 종료 코드 20으로 실패했다. **두 timeout 변경 모두 적용되지 않았다.** [명령 결과](completion-pleos-setting-results.txt)와 [에뮬레이터 경고](completion-pleos-emulator.log)를 보존했다.

이 시도는 package·activity 서비스 단계까지 진행했지만 `sys.boot_completed=1`에 도달하지 못했다. **APK 설치는 시도하지 않았고 실제 SDK·주행 규제 응답도 관측하지 못했다.** 관찰 종료 후 에뮬레이터는 종료 코드 0으로 정리됐고, OOM·OOM kill 증가량은 0이었다. 이는 부팅 성공이나 앱 통과를 뜻하지 않는다. 설정이 적용되지 않았으므로 timeout 완화의 효과도 판정할 수 없다. 지원 호스트 확보 없이 같은 설정의 장시간 재시도를 반복하지 않는다.

[진단 명령·관측](completion-pleos-diagnostic.json), [로그 발췌](completion-pleos-diagnostic-excerpt.txt), [결과 요약](completion-pleos.json)을 함께 보존했다. 전체 원본 로그는 전체 제출 묶음의 `evidence/completion-pleos-diagnostic.log.gz`로 전달한다.

## 소스와 공식 SDK 계약 대조

앱은 `NAVI_ROUTE` 권한을 선언하고 NaviHelper 3.0.5로 초기화 → 리스너 등록 → `getDestinationInfo()` → callback 처리 → 리스너 해제·release 순서를 사용한다. 목적지 이름·주소는 대조 결과만 남기고 원문을 저장하지 않는다. Car UX 제한은 `CarUxRestrictionsManager`의 현재 값과 변경 리스너를 사용한다. 이 대조는 코드 계약 확인이며 실제 차량 서비스 callback을 수신했다는 증거가 아니다.

## 지원 PC에서 실행할 최종 확인

1. 공식 GPU·가상화 요건을 갖춘 PC에서 Pleos 2.1.0을 Cold boot로 실행한다. 아래 `emulator-5554`는 `adb devices`에서 확인한 실제 일련번호로 바꾼다. `sys.boot_completed`가 `1`일 때 진행한다.

```sh
adb devices
adb -s emulator-5554 shell getprop sys.boot_completed
python scripts/verify-android-runtime.py --serial emulator-5554 --release-apk apk/ArrivalBrief_release.apk --output ../arrival-pleos-check
adb -s emulator-5554 forward tcp:8088 tcp:8088
```

위 스크립트는 설치·실행·설치본 SHA-256을 기록한다. `runtime-result.json`의 release 성공만으로 아래 SDK·주행 규제 확인을 완료 처리하지 않는다.

2. PC 브라우저에서 `http://127.0.0.1:8088/`의 공식 Virtual Console을 연다. 앱 자체의 차량 밖 데모를 사용하지 않는다.

| 확인 | 조작 및 통과 조건 | 남길 증거 |
| --- | --- | --- |
| 내비 목적지 일치 | 실제 내비에서 코엑스 삼성동 회의시설을 설정하고 앱의 내비 대조 실행. 실제 callback에 따른 일치 문구를 확인 | 설정 화면·앱 결과·시각 |
| 다른 목적지 | 지원 시설과 다른 실제 내비 목적지로 바꾸어 불일치와 확정 차단 확인 | 설정 화면·앱 결과 |
| 목적지 없음/연결 불가 | 실제 내비 상태를 비운 뒤 대조. 성공으로 꾸미지 않고 없음·불가 결과 확인 | 앱 결과와 관찰한 원인 |
| 플랫폼 제한 활성 | 공식 콘솔에서 기어 등 차량 상태를 바꾸고 실제 UX 제한이 활성화되는 조건을 확인. 입력·복사·QR이 잠기고 확정된 안내는 유지되는지 확인 | 콘솔 상태·차량 서비스 상태·앱 화면을 같은 시나리오로 기록 |
| 제한 해제·복귀 | 플랫폼 제한을 해제하고 복귀. 재확인 없이 새 확정·반출이 자동 허용되지 않는지 확인 | 복귀 화면·재확인 동작 |
| 휴대폰 QR | 정차 확인 후 저장한 메모 QR을 실제 휴대폰 카메라로 읽고 한글·기호·길이가 같은지 비교 | 휴대폰 모델·판독 결과·테스트 메모 |

기어 P/D를 바꾼 사실만으로 UX 제한 callback의 수신을 입증하지 않는다. 플랫폼이 실제로 제공한 제한 상태와 앱 반응을 함께 확인해야 한다. 실제 차량의 주행 조작을 이 절차로 자동화하지 않는다.

## 공식 자료

- [Pleos 설치·실행](https://document.pleos.ai/en/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator)
- [Pleos GPU·Cold boot 설정](https://document.pleos.ai/en/docs/connect/guide/getting-started/application-development/configure-pleos-connect-emulator)
- [Virtual Console 설정](https://document.pleos.ai/en/docs/connect/guide/getting-started/application-development/use-vehicle-emulator)
- [Virtual Console 사용](https://document.pleos.ai/en/docs/pleos-only/app-test-guide/virtual-console-guide)
- [SDK 릴리스](https://document.pleos.ai/en/docs/pleos-only/release-note/sdk-release-note)
- [NaviHelper 권한](https://document.pleos.ai/en/api-reference/connect-sdk-pleos/NaviHelper/declaring-permissions)
- [NaviHelper 사용 순서](https://document.pleos.ai/en/api-reference/connect-sdk-pleos/NaviHelper/intro)
- [CarUxRestrictionsManager](https://developer.android.com/reference/android/car/drivingstate/CarUxRestrictionsManager)
- [AOSP Watchdog 설정 관찰자](https://android.googlesource.com/platform/frameworks/base/+/edb639d88f39/services/core/java/com/android/server/Watchdog.java)
- [AOSP 소프트웨어 에뮬레이터 timeout multiplier 목적](https://android.googlesource.com/platform/system/libbase/+/acf2f2ba8147af581b129b5ac904fad868919f98)
