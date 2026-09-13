# CouponIt Native Android MVP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the OptionFit vehicle-comparison web app with an offline-first native Android CouponIt wallet implementing the approved v2.0 P0/P1 flow and the core usage ledger.

**Architecture:** A single Android `app` module uses Jetpack Compose for UI, Room for authoritative structured data, app-private files for imported originals, and ML Kit for local barcode candidates. Pure domain functions own filtering, summaries, balance calculation, and idempotent usage rules so they can be verified without Android runtime.

**Tech Stack:** Kotlin 2.1.20, Android Gradle Plugin 8.12.0, Gradle 8.13, compile/target SDK 36, min SDK 30, Compose BOM 2025.08.01, Room 2.8.5, ML Kit Barcode Scanning 17.3.0.

---

### Task 1: Replace the web scaffold with Android

**Files:**
- Delete: `package.json`, `package-lock.json`, `vite.config.ts`, `tsconfig.json`, `index.html`, `src/**`, `public/**`, `docs/DATA.md`
- Create: `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `gradle/wrapper/**`, `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`, Android resources

- [ ] Pin the Android toolchain and repositories.
- [ ] Create the single application module and launcher activity.
- [ ] Run `./gradlew.bat tasks` and expect exit code 0.
- [ ] Commit the scaffold.

### Task 2: Define and verify wallet rules

**Files:**
- Test: `app/src/test/java/com/couponit/app/domain/WalletRulesTest.kt`
- Create: `app/src/main/java/com/couponit/app/domain/WalletModels.kt`
- Create: `app/src/main/java/com/couponit/app/domain/WalletRules.kt`

- [ ] Write tests for expiry sorting, unknown dates, filtering, wallet summaries, balance baselines, reversals, and duplicate operation IDs.
- [ ] Run the focused tests and verify they fail because the domain API is absent.
- [ ] Implement only the tested immutable domain rules.
- [ ] Run the focused test and all unit tests; expect PASS.
- [ ] Commit the domain rules.

### Task 3: Add Room persistence and repository boundaries

**Files:**
- Test: `app/src/test/java/com/couponit/app/data/EntityMappingTest.kt`
- Create: `app/src/main/java/com/couponit/app/data/local/Entities.kt`
- Create: `app/src/main/java/com/couponit/app/data/local/CouponDao.kt`
- Create: `app/src/main/java/com/couponit/app/data/local/CouponDatabase.kt`
- Create: `app/src/main/java/com/couponit/app/data/CouponRepository.kt`

- [ ] Write mapping and operation-id behavior tests first and verify RED.
- [ ] Add separate coupon, asset, code-candidate, usage-event, and presentation-session tables.
- [ ] Make usage updates transactional and idempotent by unique `operationId`.
- [ ] Expose wallet state as Flow and preserve user-confirmed fields.
- [ ] Run unit tests and Room schema compilation; expect PASS.
- [ ] Commit persistence.

### Task 4: Import private image copies and recognize codes

**Files:**
- Test: `app/src/test/java/com/couponit/app/importing/ImportNamingTest.kt`
- Create: `app/src/main/java/com/couponit/app/importing/CouponImporter.kt`
- Create: `app/src/main/java/com/couponit/app/recognition/BarcodeRecognizer.kt`

- [ ] Write tests for stable asset naming and MIME rejection, then verify RED.
- [ ] Copy selected/shared JPEG and PNG inputs into app-private storage before analysis.
- [ ] Hash every copy and store width, height, bytes, and source MIME.
- [ ] Run ML Kit barcode analysis without blocking original access; preserve raw strings and bounds.
- [ ] Verify unit tests and debug compilation.
- [ ] Commit import and recognition.

### Task 5: Implement the approved Compose experience

**Files:**
- Create: `app/src/main/java/com/couponit/app/MainActivity.kt`
- Create: `app/src/main/java/com/couponit/app/ui/CouponItApp.kt`
- Create: `app/src/main/java/com/couponit/app/ui/WalletViewModel.kt`
- Create: `app/src/main/java/com/couponit/app/ui/screens/*.kt`
- Create: `app/src/main/java/com/couponit/app/ui/theme/*.kt`

- [ ] Implement Photo Picker and multi-image import with per-item errors.
- [ ] Implement home summary, search, merchant chips, expiry sorting, and adaptive grid/list cards.
- [ ] Implement detail, original preview, code presentation, archive, and settings screens.
- [ ] Keep presentation sessions separate from usage events; closing presentation must not mark used.
- [ ] Add exchange redemption/undo and amount balance/spend flows.
- [ ] Apply the supplied paper/card/navy visual language and large-font single-column fallback.
- [ ] Run unit tests, lint, and debug build; expect PASS.
- [ ] Commit UI.

### Task 6: Verify the deliverable honestly

**Files:**
- Modify: `README.md`
- Create: `docs/verification/P0_P1_LOCAL_VERIFICATION.md`

- [ ] Document setup, APK path, implemented scope, and excluded P2-P5 work.
- [ ] Run `./gradlew.bat testDebugUnitTest lintDebug assembleDebug` fresh.
- [ ] Record exact results and distinguish local build evidence from device, screen-decoding, and store-use evidence.
- [ ] Inspect `git diff --check` and `git status --short`.
- [ ] Commit documentation.
