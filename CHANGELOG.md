# 변경 이력

이 저장소의 릴리스 이력. 형식은 [Keep a Changelog](https://keepachangelog.com/ko/1.0.0/)를 참고하되,
검증되지 않은 항목은 "미검증"으로 명시한다 — 근거 없는 완료 표시를 하지 않는다.

## [v0.1.0] - 2026-09-13

이 저장소(`AIProject-k/CouponIt`)의 최초 배포. 기획 v2.0(`CouponIt_자체쿠폰지갑_상세기획_개발계획_v2.0.md`)의
P0/P1 범위를 Kotlin 네이티브 Android 단일 모듈로 구현한 개발 검증 APK다. 서버·계정 없이 기기 내부에서만 동작한다.

### 다운로드

[릴리스 페이지](https://github.com/AIProject-k/CouponIt/releases/tag/v0.1.0)에 디버그 APK를 첨부했다.

| 파일 | SHA-256 |
| --- | --- |
| `CouponIt-v0.1.0-debug.apk` | `753bcab44b0490ff410b67d3002c8ac6942a57cd13f0fc7e03d19fef7168c6bd` |

서명은 디버그 키다. 배포용 서명이 아니므로 개인 테스트 기기 설치 용도로만 쓴다. 다른 서명의 기존 설치가 있으면
먼저 제거해야 설치된다. 체크섬은 `SHA256SUMS.txt`(release 자산)에도 함께 올려뒀다.

### 추가됨

**가져오기 · 저장**
- Android Photo Picker로 이미지 최대 30장 선택, 다른 앱의 JPEG/PNG 공유 Intent 수신
- 선택한 원본을 앱 전용 저장소에 먼저 복사 후 SHA-256 해시·크기·MIME·해상도 기록 (`ImportPolicy`, `CouponImporter`)

**인식**
- ML Kit 온디바이스 바코드 인식으로 후보와 원본 좌표 보존 (`BarcodeRecognizer`)
- ML Kit 한국어 OCR(`text-recognition-korean:16.0.1`)로 상품명·사용처·유효기간 자동 추출 (`CouponTextRecognizer`, `CouponTextParser`)
  - 라벨(사용처/교환처/브랜드, 상품명/교환상품, 유효기간 계열) 기반 줄 파싱
  - 날짜는 명확한 라벨·형식(점/하이픈/슬래시/한국어, 범위 종료일)에서만 확정 — 모호하면 미확정으로 남김
  - 상세 화면 재인식은 **빈 항목만** 채움, "정보 저장"을 눌러야 DB에 반영

**화면**
- 홈 요약(미사용·임박·확인 필요)·검색·사용처 필터·임박순 정렬·2열/목록/큰 글자 1열 보기
- 상세 편집, 원본 이미지 보기, 검출 영역 확대 제시(화면 밝기·화면 유지 후 원복)
- 보관함 이동/복원, 교환권 사용 처리, 금액권 잔액·사용 금액 기록

**저장소**
- Room 스키마: 쿠폰·원본 자산·코드 후보·사용 이벤트·제시 세션
- 코드 제시 화면을 닫는 것만으로 사용 완료 처리되지 않는 분리된 흐름

### 검증됨 (근거: `docs/verification/`)

- JVM 단위 테스트 27개 통과 (`./gradlew testDebugUnitTest`)
- Android Lint 오류 0개
- 에뮬레이터(API 36) 설치·실행·핵심 UI 확인
- 실제 사용자 제공 쿠폰 이미지 1장으로 바코드+OCR 동시 인식, 저장, 재인식 회귀 확인
- Android 계측 테스트 8개(OCR 3개 + 실사진 회귀 5개) 통과

### 아직 없음 / 미검증

- 다중 코드 선택 UI, 사용 이벤트 이력 목록과 개별 취소
- 만료 알림, 앱 바로가기, 사용자 설정(DataStore)
- 암호화 백업·복원
- 실사용 쿠폰 이미지 10~20장 규모의 인식률 측정, 타 기기 화면 재촬영 인식(V2), 매장 실제 승인(V3)

### 알려진 환경 이슈

- Android Studio 번들 JBR가 JDK 25로 갱신되면 Gradle 8.13 내장 Kotlin DSL 컴파일러가 버전 문자열을 파싱하지
  못해 빌드가 실패한다. `JAVA_HOME`을 JDK 17/21(예: Zulu)로 지정해 실행한다. 코드 결함이 아니라 툴체인 조합 문제다.

### 참고

- 이 코드는 이전 프로젝트(`OptionFit`, 차량 옵션 비교 웹앱)와 같은 로컬 작업 폴더를 재사용해 작성됐다.
  이 저장소의 이력은 CouponIt으로 전환한 시점부터 새로 구성했고, OptionFit 쪽 커밋·파일은 포함하지 않는다.

[v0.1.0]: https://github.com/AIProject-k/CouponIt/releases/tag/v0.1.0
