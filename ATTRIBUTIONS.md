# 자료 출처와 이용 범위

## 시설 정보

- [코엑스 회의실 주차안내](https://business.coex.co.kr/guide/conference-room-parking-information/general-vehicle/): 2026-10-30 확인한 목적지별 진입문·주차층·구역과 높이 제한을 정리했습니다. 실시간 운영 정보가 아니며 현장 표지와 최신 공식 안내를 우선합니다.

- [코엑스 주차장·화물차 진입 약도 공지](https://business.coex.co.kr/post-meeting-notice/%EC%95%88%EB%82%B4%EC%9E%90%EB%A3%8C-%EC%BD%94%EC%97%91%EC%8A%A4-%EC%A3%BC%EC%B0%A8%EC%9E%A5-%ED%99%94%EB%AC%BC%EC%B0%A8-%EC%A7%84%EC%9E%85-%EC%95%BD%EB%8F%84/): 2026-02-11 게시, 2026-10-30 확인. 회의실 고객 주차 추천 표와 일반차량 안내가 다른 세 분류의 상충 근거로 사용합니다. 화물차 진입 동선을 승용차 경로로 사용하지 않습니다.

## 시연 영상

- 이전 0.4.0 영상의 실사 도입 (0.5.0 영상에서는 사용하지 않음): [Rasul Yarichev / Pexels](https://www.pexels.com/video/high-rise-cityscape-overlooking-parking-lot-30527770/), [Pexels License](https://www.pexels.com/license/). 일반 도시와 주차장 촬영이며 코엑스·서울의 실제 장면이나 제공 기관의 제휴·추천을 뜻하지 않습니다. 이전 소개 영상의 일부로 편집했으며 원본 스톡 파일은 배포하지 않습니다.
- 0.5.0 영상 전체: 직접 구현한 웹 프로토타입을 실행해 얻은 화면 캡처의 편집입니다. 실시간 화면 녹화·실제 차량 연결·고객 사용 검증으로 표현하지 않습니다.
- 서체: [Noto Sans KR](https://github.com/google/fonts/tree/main/ofl/notosanskr), SIL Open Font License 1.1. [라이선스 사본](research/media/fonts/OFL.txt)을 보존합니다.
- 1.0.0-rc1: 웹 영상 두 편과 일반 Android debug 자동 UI 시험의 캡처 편집 영상 한 편을 제공합니다. 각각의 원본 화면·버전·해시를 영상 metadata에 기록합니다. 연속 녹화·Pleos 실제 연동·휴대폰 광학 QR 스캔 성공 영상이 아닙니다.
- 현재 세 영상은 무음이며 음악·음성 트랙을 포함하지 않습니다.

## 화면과 아이콘

Arrival Brief의 1.0 브랜드 아이콘은 내장 이미지 생성 도구로 새로 만들었습니다. A·도착 경로·노란 목적지 점을 결합했으며 [생성 프롬프트와 원본 해시](docs/verification/brand-icon-generation.json)를 보존합니다. 화면의 SVG 기능 심벌과 일러스트는 코드로 제작했습니다. 현대자동차그룹의 로고·공식 서비스 표시는 사용하지 않으며 자체 배색을 사용합니다.

Linear·Stripe·Apple의 정보 배치, Carbon 데이터 표와 GOV.UK 확인 흐름을 참고했으며 외부 브랜드의 로고나 페이지 코드를 복제하지 않았습니다. 외부 frontend-design 가이드와 그 라이선스는 [가이드 사본](research/frontend-design-SKILL.md), [Apache 2.0 라이선스](research/frontend-design-LICENSE.txt)에 보존합니다.

## 개발 의존성

Android Gradle Plugin, Kotlin, Gradle wrapper, JUnit 및 Pleos SDK와 그 전이 의존성에는 각각의 원저작자 라이선스·이용 조건이 적용됩니다. Pleos SDK 코드는 저장소에 복제하지 않으며 공식 Maven 저장소에서 받습니다. 실제 서비스 실행에는 플랫폼 권한 확인이 필요합니다.

별도 명시가 없는 자체 코드·자료에 새 오픈소스 라이선스를 부여한 것은 아닙니다. 외부 자료의 이용 조건을 자체 코드 전체의 라이선스로 해석하지 않습니다.

## 주차 메모 QR

[ZXing core 3.5.4](https://github.com/zxing/zxing/releases/tag/zxing-3.5.4)의 [QRCodeWriter API](https://zxing.github.io/zxing/apidocs/com/google/zxing/qrcode/QRCodeWriter.html)를 사용해 기기 안에서 일반 텍스트 QR을 생성합니다. ZXing은 Apache License 2.0으로 배포되며 원본 라이브러리는 수정하지 않았습니다. [라이선스 사본](android/app/src/main/assets/third_party/ZXing-LICENSE.txt)과 [NOTICE 사본](android/app/src/main/assets/third_party/ZXing-NOTICE.txt)을 앱 assets에도 포함합니다. QR에는 사용자가 저장한 주차 메모와 저장 시각만 포함하며 서버·계정·자동 동기화가 필요하지 않습니다. 폰 카메라를 통한 실제 스캔 성공은 별도 검증 대상입니다.
