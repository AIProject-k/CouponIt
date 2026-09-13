# 한국어 쿠폰 OCR 검증

검증일: 2026-09-13. 실행 환경: Windows, Zulu JDK 21, FaceScope_API36 에뮬레이터.

## 구현

- `com.google.mlkit:text-recognition-korean:16.0.1` 번들 모델을 사용하는 기기 내 OCR.
- 사용처/교환처/브랜드 및 상품명 라벨, 단독으로 표시된 알려진 브랜드와 다음 상품명 줄을 추출.
- 유효기간/사용기간/만료일 등 라벨, 점/하이픈/슬래시/한국어 날짜와 범위 종료일 지원.
- 구매일/무라벨 날짜, 불가능한 날짜, 서로 다른 만료일은 자동 확정하지 않음.
- 가져오기에서 메타데이터를 바코드와 함께 저장. OCR 예외는 별도 안내하고 원본/바코드는 유지.
- 상세 재인식은 현재 편집 중 빈 항목만 채움. DB는 정보 저장을 눌러야 변경.
- 수동 날짜 오류는 저장 전 안내. 날짜 미확인은 정보 확인 상태에 포함.

API 기준: [Google ML Kit Android 문서](https://developers.google.com/ml-kit/vision/text-recognition/v2/android).

## 실행 결과

`JAVA_HOME=C:/Program Files/Zulu/zulu-21`

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug lintDebug --console=plain
.\gradlew.bat connectedDebugAndroidTest --console=plain
```

- 단위 테스트 24개 통과 (기존 12개 + 파서 12개).
- APK 빌드 성공. `app/build/outputs/apk/debug/app-debug.apk`.
- Android Lint 오류 0개, 의존성/Gradle 업데이트 권고 경고 9개.
- 에뮬레이터 통합 테스트 3개 통과:
  1. Canvas로 생성한 한국어 PNG에서 실제 번들 OCR이 사용처/상품명/날짜 추출.
  2. MediaStore URI 가져오기 → OCR → Room 메타데이터 저장.
  3. Compose 상세 화면 재인식 → 기존 상품명 유지 → 빈 사용처/날짜 입력 → 저장 전 DB 불변 → 정보 저장 후 DB 반영.
- 테스트는 별도 메모리 DB와 생성 이미지 사용. 기존 사용자 쿠폰 DB를 수정하지 않음.
- 처음 지정한 Android Studio JBR 25는 현재 Gradle/Kotlin 조합에서 실행 실패하여 설치된 Zulu 21로 전환함.

## 한계

실제 사용자 쿠폰 사진 모음의 인식률과 물리 기기 검증은 수행하지 않았다. 아래 후속 검증은 실제 이미지 한 장에 대한 결과다. 사진 흐림, 로고, 작은 글자, 다른 다단 배치의 정확도를 보장하지 않는다. OCR 결과는 원본 대조가 필요하다. 발행일과 구분할 수 없는 날짜 및 지원하지 않는 표기는 수동 입력으로 남긴다.

바코드가 있는 실제 쿠폰과 OCR의 동시 성공, OCR 엔진 오류 주입 및 생명주기 취소는 이번 통합 테스트에서 별도로 검증하지 않았다.

## 후속 수정: 실제 11번가 쿠폰 및 원본 표시

- 실제 이미지의 `기간 2026-11-10`과 `사용 유효기간` 라벨 누락을 수정.
- `[버거킹] 불고기와퍼주니어세트` 형식을 지원하고 `상품 이용안내`를 상품명으로 잘못 읽는 규칙 제거.
- 목록의 글자 대체 이미지를 원본 썸네일로 변경. 원본·바코드 화면에서 전체 원본과 별도의 바코드 확대 이미지를 스크롤로 함께 제공.
- 단위 테스트 27개 통과. 실제 이미지의 날짜/브랜드/상품명, 목록 원본 및 원본+바코드 동시 존재 검증을 포함한 Android 통합 테스트 5개 통과.
- 실제 이미지는 저장소에 추가하지 않고 앱 캐시의 로컬 입력으로 시험. `am instrument -w -e realCouponPath <앱 캐시 이미지 경로> com.couponit.app.test/androidx.test.runner.AndroidJUnitRunner` 결과 `OK (5 tests)`.
- 새 APK를 에뮬레이터에 `install -r`로 설치. 기존 쿠폰 상세에서 재인식하여 상품명/사용처/종료일 저장 후 홈에 `D-58 · 2026-11-10` 표시를 확인.
- 실제 화면에서 원본과 바코드 확대를 확인. 화면 캡처는 개인정보가 포함된 로컬 빌드 산출물에만 보관하고 저장소에 추가하지 않음.
- 이전 잘못 인식된 상품명은 빈칸으로 만든 뒤 재인식하여 수정. 일반 사용자가 직접 입력한 값은 자동 덮어쓰지 않음.
