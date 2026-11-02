# Arrival Brief APK

[ArrivalBrief_release.apk](ArrivalBrief_release.apk)는 `1.0.0-rc1` 서명 release 후보입니다. APK 설치·실행과 실제 Pleos 서비스의 검증 범위는 별도로 확인합니다.

| 항목 | 값 |
| --- | --- |
| 패키지 | `studio.ax.arrival` |
| 버전 | `1.0.0-rc1` (versionCode 10) |
| minimum / target / compile SDK | 28 / 35 / 35 |
| 크기 | 6,658,807바이트 |
| 서명 | APK v2, CN=Arrival Brief, RSA 3072비트 |
| SHA-256 | `04114df0de41218e21b293609e6625983f4eb615d77ade959ec3bb2a8c48e8bf` |
| 단위시험 | Android JUnit 73개 통과 |
| 정적검사 | Lint 오류 0개·경고 0개 |
| 일반 Android 9 | release 설치·첫 실행·기기 내 SHA-256 일치 |
| 별도 debug UI | 표준 13개·글자 크기 1.3배 반복 6개 통과 |

같은 기기의 텍스트 공유, 안내문의 행사장·주차 문장 구분, NaviHelper 시설 대조, 최근 목적지와 수동 주차 메모를 포함합니다. 주차 메모는 64자·1건·24시간이며 같은 기기 재열람과 Android의 명시적 텍스트 QR 반출을 지원합니다. 휴대폰 카메라로 읽고 사용자가 별도 저장하며 실제 광학 스캔은 미검증입니다. 앱의 24시간 만료·삭제 규칙은 휴대폰 복사본에 적용되지 않습니다. 서버·URL·자동 동기화·위치 추적은 제공하지 않습니다.

[버전별 런타임 기록](../docs/verification/android-runtime.md)은 release 설치·실행과 debug instrumentation, 일반 Android와 Pleos 환경을 구분합니다. 0.3·0.4·0.5의 과거 실행 기록을 이번 후보의 성공 근거로 재사용하지 않습니다. `1.0.0-rc1`은 정식 출시나 실차 적합성 인증을 뜻하지 않습니다.

공식 제출명 APK와 검증 기준 APK는 동일한 바이트와 서명이어야 합니다. 파일명만 바꿔도 위 SHA-256을 다시 비교합니다. 서명키·비밀번호는 저장소에 없습니다. [빌드 지침](../docs/ANDROID_BUILD.md)에 따라 재빌드하며, 다른 키로 서명한 APK를 동일 배포 파일로 간주하지 않습니다.
