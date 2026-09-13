# 쿠폰 OCR 구현 계획

목표: 새 이미지에서 상품명, 사용처, 종료일을 추출하고 기존 쿠폰 상세에서 재인식한다.

기기 내 ML Kit 한국어 모델을 APK에 포함한다. 텍스트 추출과 순수 Kotlin 필드 해석을 분리한다. 유효기간 문맥의 단일 종료일만 확정하고 불명확한 날짜는 자동 확정하지 않는다. OCR 실패로 원본/바코드 저장을 실패시키지 않는다. 재인식은 편집 중 비어 있는 필드만 채우며 정보 저장 전에는 DB를 변경하지 않는다.

- [x] CouponTextParserTest: 사용처/상품명 라벨, 브랜드, 날짜 범위/줄바꿈/잘못된 날짜/모호한 날짜 검증. 먼저 testDebugUnitTest 실패 확인.
- [x] CouponTextParser 및 CouponTextRecognizer 구현. 한국어 번들 의존성 추가.
- [x] CouponImporter에 메타데이터 병합, OCR 실패 격리.
- [x] WalletViewModel과 상세 UI에 재인식 및 실패/처리 중 상태 연결.
- [x] testDebugUnitTest assembleDebug lintDebug 검증 후 README와 검증 기록 갱신.
- [x] 실행 중인 에뮬레이터에서 OCR/가져오기/재인식 저장 통합 테스트 3개 통과.

실기기 실제 쿠폰 사진 인식률은 로컬 단위 테스트/빌드와 별도로 보고한다.
