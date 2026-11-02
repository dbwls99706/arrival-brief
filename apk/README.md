# Arrival Brief APK

[ArrivalBrief_release.apk](ArrivalBrief_release.apk)는 `1.0.0-rc4` 서명 release 후보입니다. APK 설치·실행과 실제 Pleos 서비스의 검증 범위는 별도로 확인합니다.

| 항목 | 값 |
| --- | --- |
| 패키지 | `studio.ax.arrival` |
| 버전 | `1.0.0-rc4` (versionCode 13) |
| minimum / target / compile SDK | 28 / 35 / 35 |
| 크기 | 6,670,935바이트 |
| 서명 | APK v2, CN=Arrival Brief, RSA 3072비트 |
| SHA-256 | `865fb8368a81351575790fade68d5d27d805c1cfa8290897082fef056faa9da9` |
| 단위시험 | Android JUnit 77개 통과 |
| 정적검사 | Lint 오류 0개·경고 0개 |
| 일반 Android 9 | RC3에서 RC4로 업데이트 설치·첫 실행·기기 내 APK 해시 일치를 확인했습니다. |
| 별도 debug UI | RC4 QR 중심 4개·글자 크기 1.3배 같은 4개 반복 통과. 전체 19개 재실행 아님 |

같은 기기의 텍스트 공유, 안내문의 행사장·주차·정형 일정·등록 로비 문장 구분, NaviHelper 시설 대조, 최근 목적지와 수동 주차 메모를 포함합니다. 주차 메모는 64자·1건·24시간이며 같은 기기 재열람과 Android의 명시적 텍스트 QR 반출과 기본/확대 크기 전환을 지원합니다. 확대할 때도 메모와 저장 시각은 유지하고 주차·만료 조건을 다시 확인합니다. 휴대폰 카메라로 읽고 사용자가 별도 저장하며 실제 광학 스캔은 미검증입니다. 앱의 24시간 만료·삭제 규칙은 휴대폰 복사본에 적용되지 않습니다. 서버·URL·자동 동기화·위치 추적은 제공하지 않습니다.

[버전별 런타임 기록](../docs/verification/android-runtime.md)은 release 설치·실행과 debug instrumentation, 일반 Android와 Pleos 환경을 구분합니다. 0.3·0.4·0.5·RC1·RC2·RC3의 과거 실행 기록을 이번 후보의 성공 근거로 재사용하지 않습니다. `1.0.0-rc4`은 정식 출시나 실차 적합성 인증을 뜻하지 않습니다.

공식 제출명 APK와 검증 기준 APK는 동일한 바이트와 서명이어야 합니다. 파일명만 바꿔도 위 SHA-256을 다시 비교합니다. 서명키·비밀번호는 저장소에 없습니다. [빌드 지침](../docs/ANDROID_BUILD.md)에 따라 재빌드하며, 다른 키로 서명한 APK를 동일 배포 파일로 간주하지 않습니다.
