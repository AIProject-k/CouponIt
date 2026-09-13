# CouponIt P0/P1 로컬 검증 기록

검증일: 2026-09-13 (Asia/Seoul)

## 구현된 범위

- 기존 Vite 차량 옵션 앱을 Kotlin 네이티브 Android 단일 앱 모듈로 교체
- Android Photo Picker 최대 30장 및 JPEG/PNG 공유 Intent 수신
- 선택 원본을 앱 전용 저장소에 먼저 복사하고 SHA-256·크기·MIME·해상도 기록
- ML Kit 로컬 바코드 후보 분석, raw 문자열과 영역 보존
- Room 쿠폰·원본 자산·코드 후보·사용 이벤트·제시 세션 스키마
- 홈 요약, 검색, 사용처 필터, 임박순, 2열/목록, 큰 글자 1열 전환
- 상세 수동 편집, 원본 보기, 검출 영역 확대 제시, 화면 밝기/화면 유지 원복
- 교환권 사용 기록, 금액권 기준 잔액/사용 금액 기록, 보관함 이동/복원
- 코드 제시 화면을 닫는 것만으로 사용 상태가 바뀌지 않는 분리된 UI 흐름

## 현재 제외된 범위

- 한국어 OCR 기반 상품명·사용처·날짜 자동 정리와 다중 코드 선택 UI
- 사용 이벤트별 이력 목록과 특정 이벤트 취소 UI
- 만료 알림, 앱 바로가기, DataStore 사용자 설정
- 암호화 백업 파일 및 빈 설치 복원
- 실제 쿠폰 이미지 10~20장 인식률 측정, 다른 기기 화면 해독, 매장 승인 시험

현재 APK는 P0/P1 개발 검증용이다. 장기 보관할 실제 쿠폰을 맡기는 개인 실사용 MVP로 표시하지 않는다.

## 자동 검증

- JVM 단위 테스트: 10개 (지갑 필터·정렬·요약·잔액·중복 작업·엔티티 매핑·입력 정책)
- `gradlew testDebugUnitTest lintDebug assembleDebug`: BUILD SUCCESSFUL
- Android lint: 오류 0개, 업데이트 권고 등 경고 9개
- Debug APK: `app/build/outputs/apk/debug/app-debug.apk`

## 에뮬레이터 확인

- AVD: `FaceScope_API36`, API 36, `sdk_gphone64_x86_64`
- 설치: `adb install -r` 성공
- 실행: `com.couponit.app/.MainActivity`가 top resumed activity임을 확인
- UI 트리: 쿠폰잇 제목, 쿠폰 추가, 3개 요약, 검색, 보기 전환, 빈 상태, 하단 내비게이션 확인
- 스크린샷: 로컬 빌드 산출물 `app/build/couponit-home.png`

이 확인은 이미지 선택기 상호작용, 실제 쿠폰 코드 해독, 밝기 원복의 실기기 동작, 매장 승인을 입증하지 않는다.
