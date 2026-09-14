package com.couponit.app.ui

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.room.Room
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import com.couponit.app.data.CouponRepository
import com.couponit.app.data.local.CouponDatabase
import com.couponit.app.domain.Coupon
import com.couponit.app.domain.CouponLocation
import com.couponit.app.domain.CouponType
import com.couponit.app.domain.UsageState
import com.couponit.app.importing.CouponImporter
import com.couponit.app.recognition.BarcodeRecognizer
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** 사용성 흐름: 뒤로가기, 카드 탭, 코드 화면 닫기, 미저장 경고, 사용 확인·복원. 메모리 DB만 쓴다. */
class WalletNavigationUiTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val visible = mutableStateOf(true)
    private lateinit var database: CouponDatabase
    private lateinit var repository: CouponRepository
    private lateinit var image: File

    @Before fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, CouponDatabase::class.java).build()
        repository = CouponRepository(database.couponDao())
        image = File.createTempFile("nav-", ".png", context.cacheDir).also { file ->
            val bitmap = Bitmap.createBitmap(600, 400, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
        runBlocking {
            repository.save(Coupon(ID, TITLE, "테스트 매장", type = CouponType.EXCHANGE, originalAssetPath = image.path, codeValue = "test-code"))
        }
        val model = WalletViewModel(repository, CouponImporter(context, repository, BarcodeRecognizer()))
        compose.setContent { if (visible.value) CouponItApp(model, {}) }
        waitText(TITLE)
    }

    @After fun tearDown() {
        compose.runOnIdle { visible.value = false }
        compose.waitForIdle()
        database.close()
        image.delete()
    }

    @Test fun cardBodyOpensEditAndBackReturnsHome() {
        compose.onNodeWithText(TITLE).performClick()
        waitText("정보 저장")
        Espresso.pressBack()
        waitText("쿠폰잇")
        compose.onAllNodesWithText("정보 저장").assertCountEquals(0)
    }

    @Test fun codeButtonOpensCodeScreenAndCloseOrBackReturnsHome() {
        compose.onNodeWithText("원본·바코드").performClick()
        waitText("직원에게 코드를 보여주세요.")
        compose.onNodeWithText("닫기").performClick()
        waitText("쿠폰잇")
        compose.onAllNodesWithText("정보 저장").assertCountEquals(0)

        compose.onNodeWithText("원본·바코드").performClick()
        waitText("직원에게 코드를 보여주세요.")
        Espresso.pressBack()
        waitText("쿠폰잇")
        compose.onAllNodesWithText("정보 저장").assertCountEquals(0)
    }

    @Test fun unsavedEditAsksBeforeLeaving() {
        compose.onNodeWithText(TITLE).performClick()
        waitText("정보 저장")
        // 글자 칸에 포커스가 있으면 첫 뒤로가기는 키보드를 닫는 Android 기본 동작이라, 시스템 뒤로가기는 칩 변경으로 확인한다.
        compose.onNodeWithText("할인권").performClick()
        Espresso.pressBack()
        waitText("저장하지 않은 변경이 있어요")
        compose.onNodeWithText("계속 수정").performClick()
        compose.onAllNodesWithText("저장하지 않은 변경이 있어요").assertCountEquals(0)
        compose.onNodeWithText("정보 저장").assertExists()

        compose.onNodeWithText("상품명").performTextReplacement("바뀐 이름")
        compose.onNodeWithText("← 뒤로").performClick()
        waitText("저장하지 않은 변경이 있어요")
        compose.onNodeWithText("저장하지 않고 나가기").performClick()
        waitText("쿠폰잇")
        val saved = runBlocking { repository.coupon(ID) }!!
        assertEquals(TITLE, saved.title)
        assertEquals(CouponType.EXCHANGE, saved.type)
    }

    @Test fun redeemAsksConfirmationAndArchiveRestoreUndoes() {
        compose.onNodeWithText(TITLE).performClick()
        waitText("정보 저장")
        compose.onNodeWithText("사용했어요").performScrollTo().performClick()
        waitText("사용한 쿠폰으로 기록할까요?")
        compose.onNodeWithText("취소").performClick()
        assertEquals(UsageState.UNUSED, runBlocking { repository.coupon(ID) }!!.usageState)

        compose.onNodeWithText("사용했어요").performScrollTo().performClick()
        waitText("사용한 쿠폰으로 기록할까요?")
        compose.onNodeWithText("사용 완료로 기록").performClick()
        compose.waitUntil(5_000) { runBlocking { repository.coupon(ID) }!!.usageState == UsageState.COMPLETED }
        waitText("쿠폰잇")

        compose.onNodeWithText("보관함").performClick()
        compose.onNodeWithText("복원").performClick()
        compose.waitUntil(5_000) {
            runBlocking { repository.coupon(ID) }!!.let { it.usageState == UsageState.UNUSED && it.location == CouponLocation.WALLET }
        }
    }

    private fun waitText(text: String) =
        compose.waitUntil(10_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    private companion object {
        const val ID = "nav-ui-test"
        const val TITLE = "내비게이션 테스트 쿠폰"
    }
}
