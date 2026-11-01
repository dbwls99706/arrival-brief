# Arrival Brief APK

[ArrivalBrief_release.apk](ArrivalBrief_release.apk)는 검증 JSON에 연결된 `0.5.0-prototype` 파일이다. 최신 배포 APK.

| 항목 | 값 |
| --- | --- |
| 패키지 | `studio.ax.arrival` |
| 버전 | `0.5.0-prototype` (versionCode 5) |
| minimum / target / compile SDK | 28 / 35 / 35 |
| 크기 | 5,082,664바이트 |
| 서명 | APK v2, CN=Arrival Brief, RSA 3072비트 |
| SHA-256 | `c8ba298b9396aa567e84b2e6e5cdfb2f0dbcc2c50788042ea85620f9649f39f4` |

이번 0.5.0은 빌드·단위 테스트·Lint·서명을 검증했습니다. 새 UI의 일반 Android 기기/에뮬레이터 실행과 실제 Pleos 실행은 미검증입니다.

현재 소스는 `0.5.0-prototype`이며 NaviHelper 시설 대조·목록 직접 선택·최근 목적지 24시간 복구를 포함한다. 버전이 다르면 위 APK를 해당 소스의 검증 결과로 인용하지 않는다.

이전 0.4.0 APK는 일반 Android 9에서 설치·첫 실행·명시적 데모·높이/시설/정차 확인·브리핑 확정·주행 입력 잠금을 확인했습니다. 1280×800과 960×600 논리 화면에서 핵심 안내를 검토했습니다. 이전 0.3.0의 목적지 저장·프로세스 재시작·초안 복구 관측은 별도 역사 기록입니다. [버전별 런타임 기록](../docs/verification/android-runtime.md)에서 구분합니다. 실제 Pleos SDK 응답과 차량 UX 전환은 아직 재검증해야 합니다.

공식 제출명 APK를 같은 폴더에 함께 제공하며, 검증 기준 파일과 바이트·서명이 같다.

서명키·비밀번호는 저장소에 없다. [빌드 지침](../docs/ANDROID_BUILD.md)의 환경 설정을 사용하고, 제출 전에 실행 가능한 release 서명과 공식 파일명 형식을 확인한다. 서명은 실차 검증이나 마켓 승인을 뜻하지 않는다.

이전 0.1.0 debug·0.2.0 release 파일은 당시 버전의 자료다. 검증한 APK 해시와 소스 상태를 맞춰 사용한다.
