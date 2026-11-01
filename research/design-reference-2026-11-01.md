# 도착 준비 UI 설계 근거 · 2026-11-01

이번 개편은 새 테마를 얹는 대신 입력과 결과의 시각적 위계를 바꿨다.

- Impeccable 원저자의 Slop catalog: https://impeccable.style/slop/ — 중첩 카드, 반복하는 정보 패널, 모든 요소가 움직이는 모션을 피한다. 입력 영역의 외곽 카드를 제거하고 결과만 패스로 묶었다. 기존 저장된 frontend-design skill도 참고했으며 외부 스크립트를 설치·실행하지 않았다.
- Anthony Hobday: https://anthonyhobday.com/sideprojects/saferules/ — 중요한 정보에 높은 대비, 연관 요소의 정렬, 중립색의 색조 일치를 적용했다. 네이비는 확정 결과, 블루는 행동, 그린은 확인, 앰버는 주의/보류를 나타낸다.
- Google Design for Driving: https://developers.google.com/cars/design/design-foundations/visual-principles — 짧고 읽기 쉬운 핵심 정보와 상태 구분을 참고했다. 주행 화면은 확정한 입구·주차만 크게 유지하며 그림·모션·입력을 숨긴다. 이 참조가 Pleos 인증이나 규격 적합성 검증을 뜻하지 않는다.

도식은 직접 제작한 SVG/Android Canvas다. 자동차+루프박스 도식은 전체 높이 입력 이유를 설명한다. 건물은 코엑스 실측 외관이 아닌 개념 일러스트다. 안내 관계도는 실제 지도나 이동 경로가 아니다. 실제 데이터는 기존 엔진의 공식 자료/상충 처리 그대로 사용한다. 생성 이미지로 입구나 실내 지리를 지어내지 않았다.

웹 첫 화면은 오디토리움과 스튜디오159의 진입문 차이를 직접 바꿔 볼 수 있다. 입력 예시는 높이와 안전 확인을 자동 통과시키지 않는다. 확정 패스는 한 번 짧게 나타나고 반복되는 자동차 애니메이션은 검토 후 제거했다. reduced-motion과 주행 상태에서 모션을 끈다.
