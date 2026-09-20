package com.couponit.app.recognition

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.provider.MediaStore
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.couponit.app.data.CouponRepository
import com.couponit.app.data.local.CouponDatabase
import com.couponit.app.data.local.CodeCandidateEntity
import com.couponit.app.domain.Coupon
import com.couponit.app.domain.CouponType
import com.couponit.app.importing.CouponImporter
import com.couponit.app.ui.CouponItApp
import com.couponit.app.ui.WalletScreen
import com.couponit.app.ui.WalletViewModel
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class CouponOcrIntegrationTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun realMarketplaceImageRecognizesPeriodAndProduct() = runBlocking {
        val path = InstrumentationRegistry.getArguments().getString("realCouponPath")
        org.junit.Assume.assumeTrue("Local private fixture is optional", path != null)
        val fields = CouponTextRecognizer(context).recognize(File(path!!))
        assertEquals(LocalDate.of(2026, 11, 10), fields.expiryDate)
        assertEquals("버거킹", fields.merchantName)
        assertEquals("불고기와퍼주니어세트", fields.title?.replace(" ", ""))
    }

    @Test fun walletAndPresentationKeepOriginalAlongsideBarcode() {
        val file = fixture()
        val database = Room.inMemoryDatabaseBuilder(context, CouponDatabase::class.java).build()
        val repository = CouponRepository(database.couponDao())
        val coupon = Coupon("image-ui-test", "원본 표시 테스트", "테스트 매장", type = CouponType.EXCHANGE,
            originalAssetPath = file.path, codeValue = "test-code")
        runBlocking {
            repository.save(coupon)
            repository.saveCandidates(listOf(CodeCandidateEntity("test-code", coupon.id, "1", "test-code", 70, 100, 1000, 550, true, false)))
        }
        val model = WalletViewModel(repository, CouponImporter(context, repository, BarcodeRecognizer()))
        val visible = mutableStateOf(true)
        try {
            compose.setContent { if (visible.value) CouponItApp(model, {}) }
            compose.waitUntil(10_000) { compose.onAllNodes(androidx.compose.ui.test.hasContentDescription("쿠폰 원본 이미지")).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithContentDescription("쿠폰 원본 이미지").assertExists()
            compose.runOnIdle { model.open(WalletScreen.Present(coupon.id)) }
            compose.waitUntil(10_000) { compose.onAllNodes(androidx.compose.ui.test.hasContentDescription("바코드 확대 이미지")).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithContentDescription("쿠폰 원본 이미지").assertExists()
            compose.onNodeWithContentDescription("바코드 확대 이미지").assertExists()
        } finally {
            compose.runOnIdle { visible.value = false }
            compose.waitForIdle()
            database.close()
            file.delete()
        }
    }

    private fun fixture(): File {
        val bitmap = Bitmap.createBitmap(1400, 900, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 48f }
        listOf("사용처: 스타벅스", "상품명: 아이스 아메리카노", "유효기간: 2026.12.31")
            .forEachIndexed { index, line -> canvas.drawText(line, 70f, 150f + index * 160f, paint) }
        return File.createTempFile("ocr-fixture-", ".png", context.cacheDir).also { file ->
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }

    @Test fun bundledKoreanModelReadsImage() = runBlocking {
        val file = fixture()
        try {
            val fields = CouponTextRecognizer(context).recognize(file)
            assertEquals("스타벅스", fields.merchantName)
            assertEquals("아이스 아메리카노", fields.title)
            assertEquals(LocalDate.of(2026, 12, 31), fields.expiryDate)
            assertTrue(fields.expiryConfirmed)
        } finally { file.delete() }
    }

    @Test fun importPersistsRecognizedFields() = runBlocking {
        val file = fixture()
        val database = Room.inMemoryDatabaseBuilder(context, CouponDatabase::class.java).build()
        val repository = CouponRepository(database.couponDao())
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, file.name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        })!!
        var importedPath: String? = null
        try {
            resolver.openOutputStream(uri)!!.use { output -> file.inputStream().use { it.copyTo(output) } }
            val result = CouponImporter(context, repository, BarcodeRecognizer()).import(uri)
            assertNull(result.error)
            assertFalse(result.textRecognitionFailed)
            val coupon = repository.coupon(result.couponId!!)!!
            importedPath = coupon.originalAssetPath
            assertEquals("스타벅스", coupon.merchantName)
            assertEquals("아이스 아메리카노", coupon.title)
            assertEquals(LocalDate.of(2026, 12, 31), coupon.expiryDate)
            assertTrue(coupon.expiryConfirmed)
            assertTrue(coupon.needsReview) // Fixture deliberately has no barcode.
        } finally {
            resolver.delete(uri, null, null)
            importedPath?.let { File(it).delete() }
            file.delete()
            database.close()
        }
    }

    @Test fun rerecognitionFillsBlanksPreservesTitleAndSaves() {
        val file = fixture()
        val database = Room.inMemoryDatabaseBuilder(context, CouponDatabase::class.java).build()
        val repository = CouponRepository(database.couponDao())
        val coupon = Coupon("ocr-ui-test", "직접 입력한 상품", null, type = CouponType.UNKNOWN, originalAssetPath = file.path)
        runBlocking { repository.save(coupon) }
        val model = WalletViewModel(repository, CouponImporter(context, repository, BarcodeRecognizer()))
        val visible = mutableStateOf(true)
        try {
            compose.setContent { if (visible.value) CouponItApp(model, {}) }
            compose.runOnIdle { model.open(WalletScreen.Detail(coupon.id)) }
            compose.waitUntil(10_000) { compose.onAllNodes(androidx.compose.ui.test.hasText("이미지에서 정보 다시 인식")).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("이미지에서 정보 다시 인식").performScrollTo().performClick()
            compose.waitUntil(30_000) { compose.onAllNodes(androidx.compose.ui.test.hasText("2026-12-31")).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("상품명").assertTextContains("직접 입력한 상품")
            compose.onNodeWithText("사용처", substring = false).assertTextContains("스타벅스")
            assertNull(runBlocking { repository.coupon(coupon.id) }!!.merchantName)
            compose.onNodeWithText("상품명 원문 보기").performScrollTo().performClick()
            compose.onNodeWithText("쿠폰 전체 영역 보기").performScrollTo().performClick()
            compose.onNodeWithText("정보 저장").performScrollTo().performClick()
            compose.waitUntil(10_000) { runBlocking { repository.coupon(coupon.id) }?.merchantName == "스타벅스" }
            val saved = runBlocking { repository.coupon(coupon.id) }!!
            assertEquals("직접 입력한 상품", saved.title)
            assertEquals(LocalDate.of(2026, 12, 31), saved.expiryDate)
        } finally {
            compose.runOnIdle { visible.value = false }
            compose.waitForIdle()
            file.delete()
            database.close()
        }
    }
}
