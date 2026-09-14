# CouponIt · 쿠폰잇

쿠폰 이미지를 앱 안에 보관하고, 필요한 쿠폰을 빠르게 찾아 원본 코드 영역을 제시하며, 사용 기록을 남기는 개인용 Android 쿠폰 지갑입니다.

현재 저장소는 v2.0 기획(`CouponIt_자체쿠폰지갑_상세기획_개발계획_v2.0.md`)의 P0/P1 범위를 네이티브 Android로
구현한 개발 검증 단계입니다. 서버나 계정 없이 앱 전용 저장소와 Room만 사용합니다. 최신 배포는
[v0.1.2](https://github.com/AIProject-k/CouponIt/releases/tag/v0.1.2) — 자세한 변경 내역은 [CHANGELOG.md](CHANGELOG.md).

빌드 없이 바로 써보려면 [릴리스 페이지](https://github.com/AIProject-k/CouponIt/releases/tag/v0.1.2)에서
`CouponIt-v0.1.2-debug.apk`를 내려받아 설치하세요. 디버그 서명이라 배포용이 아니고, 다른 서명으로 이미
설치돼 있으면 먼저 지우고 설치해야 합니다.

## 요구 사항

- Android Studio (Kotlin, Jetpack Compose, Room, KSP)
- **JDK 17 또는 21** — Android Studio 번들 JBR가 JDK 25면 Gradle 8.13 내장 Kotlin DSL 컴파일러가 버전 문자열을
  못 읽어 빌드가 실패합니다. `JAVA_HOME`을 JDK 17/21로 맞추세요(예: Zulu 21).

## 빌드

```powershell
$env:JAVA_HOME = "C:\Program Files\Zulu\zulu-21"   # 위 이슈 회피용, 필요할 때만
.\gradlew.bat testDebugUnitTest assembleDebug
```

디버그 APK는 `app/build/outputs/apk/debug/app-debug.apk`에 생성됩니다.

## 현재 구현

- 여러 JPEG/PNG 이미지 선택 및 다른 앱의 이미지 공유 수신
- 앱 전용 원본 사본·해시·Room 데이터 저장
- ML Kit 바코드 후보 검출과 원본 영역 제시
- 번들 한국어 OCR로 상품명·사용처·유효기간 자동 입력, 상세 화면에서 빈 항목 재인식
- 상세 화면에서 발행사 선택 후 사용 여부 조회 페이지 열기(쿠폰 번호 복사)
- 홈 요약·검색·사용처 필터·임박순·2열/목록
- 상세 편집·보관함·교환권/금액권 사용 기록

현재는 P0/P1 개발 검증 APK입니다. 알림, 암호화 백업·복원과 실매장 검증은 아직 포함되지 않습니다. 근거는 [로컬 검증 기록](docs/verification/P0_P1_LOCAL_VERIFICATION.md)에 분리해 기록합니다.

기존 쿠폰은 상세 화면의 **이미지에서 정보 다시 인식**을 누른 뒤 결과를 확인하고 **정보 저장**을 누르세요. 재인식은 빈 항목만 채우므로 수정하려는 기존 값은 먼저 지우면 됩니다. 종료일은 유효기간 문맥에서 해석할 수 있을 때만 자동 입력하며, 인식하지 못한 값은 직접 입력할 수 있습니다. 로고만 있는 사용처나 지원하지 않는 표기에는 수동 확인이 필요합니다. [OCR 검증 기록](docs/verification/OCR_VERIFICATION.md)

사용 여부는 앱이 자동으로 조회하지 않습니다. 상세 화면에서 쿠폰 원본에 적힌 **발행사**(쿠프마케팅·기프티쇼·페이즈)를 고르고 **발행사에서 사용 여부 조회**를 누르면, 쿠폰 번호를 복사한 뒤 발행사 조회 페이지를 엽니다. 조회 페이지는 보통 휴대폰 인증이 필요합니다. 사용 완료로 나오면 앱에서 **사용했어요**를 눌러 기록하세요. 사용처가 메가MGC커피면 쿠프마케팅을 추천하지만, 원본 표기가 다르면 바꿔 주세요. 선택한 발행사는 **정보 저장**을 눌러야 저장됩니다.

## 화면 조작

- **쿠폰 카드**: 카드를 누르면 정보 수정 화면, **원본·바코드** 버튼을 누르면 코드 제시 화면이 열립니다.
- **뒤로가기**: 수정·코드·보관함·설정 화면에서는 앱을 나가지 않고 이전 화면으로 돌아갑니다. 홈에서 검색어나 사용처 필터가 있으면 먼저 해제하고, 그다음 뒤로가기에서 앱을 나갑니다.
- **코드 제시 화면**: **닫기**와 뒤로가기 모두 열었던 화면(홈 또는 수정 화면)으로 돌아갑니다.
- **수정 화면**: 저장하지 않은 변경이 있으면 나가기 전에 확인합니다. 글자 입력 중에는 첫 뒤로가기가 키보드를 닫습니다. 종료일은 `2026-12-09`, `2026.12.09`, `20261209` 형식을 받습니다.
- **사용했어요**: 한 번 더 확인한 뒤 기록합니다. 잘못 기록했다면 **보관함**에서 **복원**하면 사용 전 상태로 돌아갑니다. 보관함 카드를 누르면 정보 수정 화면이 열립니다.
- **금액권**: 코드 제시 화면의 **사용 금액 기록하러 가기**로 수정 화면의 금액 기록으로 이동합니다.

## 구조

```
app/src/main/java/com/couponit/app/
  data/          Room 엔티티·DAO·Repository
  domain/        Coupon·UsageEvent 등 도메인 모델, 지갑 규칙(WalletRules), 발행사 조회 목록(IssuerLookup)
  importing/     이미지 가져오기 정책과 파이프라인(CouponImporter)
  recognition/   바코드(BarcodeRecognizer)·한국어 OCR(CouponTextRecognizer/Parser)
  ui/            Compose 화면(CouponItApp)과 WalletViewModel
docs/
  verification/  실행 근거를 코드 변경과 분리해 기록 (P0/P1, OCR)
  superpowers/plans/  작업 계획 문서
```

## 중요한 범위 구분

- 앱 화면에 코드를 표시하는 것은 쿠폰의 유효성이나 매장 승인을 보증하지 않습니다.
- 코드 화면을 열고 닫는 것만으로 쿠폰을 사용 완료 처리하지 않습니다.
- 기획 전체와 실기기 검증 상태는 `CouponIt_자체쿠폰지갑_상세기획_개발계획_v2.0.md`를 기준으로 별도 기록합니다.
