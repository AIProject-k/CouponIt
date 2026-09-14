package com.couponit.app.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.couponit.app.data.CouponRepository
import com.couponit.app.data.local.CodeCandidateEntity
import com.couponit.app.domain.Coupon
import com.couponit.app.domain.CouponLocation
import com.couponit.app.domain.CouponType
import com.couponit.app.domain.UsageEventType
import com.couponit.app.domain.WalletRules
import com.couponit.app.domain.WalletSummary
import com.couponit.app.importing.CouponImporter
import com.couponit.app.recognition.CouponTextFields
import kotlinx.coroutines.CancellationException
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface WalletScreen {
    data object Home : WalletScreen
    data object Archive : WalletScreen
    data object Settings : WalletScreen
    data class Detail(val couponId: String) : WalletScreen
    data class Present(val couponId: String) : WalletScreen
}

data class WalletUiState(
    val all: List<Coupon> = emptyList(),
    val visible: List<Coupon> = emptyList(),
    val summary: WalletSummary = WalletSummary(0, 0, 0),
    val query: String = "",
    val merchant: String? = null,
    val screen: WalletScreen = WalletScreen.Home,
    val grid: Boolean = true,
    val importing: Boolean = false,
    val message: String? = null,
    val preferredCode: CodeCandidateEntity? = null,
)

class WalletViewModel(
    private val repository: CouponRepository,
    private val importer: CouponImporter,
) : ViewModel() {
    private val query = MutableStateFlow("")
    private val merchant = MutableStateFlow<String?>(null)
    private val screen = MutableStateFlow<WalletScreen>(WalletScreen.Home)
    private val grid = MutableStateFlow(true)
    private val importing = MutableStateFlow(false)
    private val message = MutableStateFlow<String?>(null)
    private val preferredCode = MutableStateFlow<CodeCandidateEntity?>(null)

    val state: StateFlow<WalletUiState> = combine(
        repository.coupons, query, merchant, screen, grid, importing, message, preferredCode,
    ) { values ->
        @Suppress("UNCHECKED_CAST") val coupons = values[0] as List<Coupon>
        val q = values[1] as String
        val selectedMerchant = values[2] as String?
        WalletUiState(
            all = coupons,
            visible = WalletRules.filter(coupons, q, selectedMerchant),
            summary = WalletRules.summary(coupons, LocalDate.now()),
            query = q,
            merchant = selectedMerchant,
            screen = values[3] as WalletScreen,
            grid = values[4] as Boolean,
            importing = values[5] as Boolean,
            message = values[6] as String?,
            preferredCode = values[7] as CodeCandidateEntity?,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WalletUiState())

    fun setQuery(value: String) { query.value = value }
    fun setMerchant(value: String?) { merchant.value = value }
    fun setGrid(value: Boolean) { grid.value = value }
    fun open(screenValue: WalletScreen) {
        screen.value = screenValue
        if (screenValue is WalletScreen.Present) viewModelScope.launch {
            preferredCode.value = repository.preferredCode(screenValue.couponId)
        } else preferredCode.value = null
    }
    fun dismissMessage() { message.value = null }
    fun notify(value: String) { message.value = value }

    fun import(uris: List<Uri>) = viewModelScope.launch {
        if (uris.isEmpty()) return@launch
        importing.value = true
        val results = try { uris.take(30).map { importer.import(it) } } finally { importing.value = false }
        val saved = results.count { it.couponId != null }
        val failed = results.size - saved
        message.value = if (failed == 0) "쿠폰 ${saved}개를 저장했어요. 정보를 확인해 주세요." else "${saved}개 저장, ${failed}개는 추가하지 못했어요."
        if (results.any { it.textRecognitionFailed }) message.value += " 글자 인식에 실패한 쿠폰은 상세에서 다시 인식해 주세요."
    }

    suspend fun recognizeDetails(coupon: Coupon): CouponTextFields? {
        val path = coupon.originalAssetPath ?: return null
        return try {
            importer.recognizeText(path).also {
                message.value = if (it == CouponTextFields()) "인식 가능한 정보를 찾지 못했어요. 원본을 보고 직접 입력해 주세요."
                else "빈 항목에 인식 결과를 채웠어요. 원본과 비교한 뒤 저장해 주세요."
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            message.value = "글자를 인식하지 못했어요. 다시 시도하거나 직접 입력해 주세요."
            null
        }
    }

    fun saveDetails(coupon: Coupon, title: String, merchantName: String, expiry: String, type: CouponType, issuerName: String?) = viewModelScope.launch {
        val parsed = runCatching { LocalDate.parse(expiry.trim()) }.getOrNull()
        if (expiry.isNotBlank() && parsed == null) {
            message.value = "종료일을 YYYY-MM-DD 형식의 올바른 날짜로 입력해 주세요."
            return@launch
        }
        repository.save(coupon.copy(
            title = title.ifBlank { "이름 미확인" }, merchantName = merchantName.ifBlank { null }, type = type, issuerName = issuerName,
            expiryDate = parsed, expiryConfirmed = parsed != null,
            needsReview = title.isBlank() || merchantName.isBlank() || parsed == null || (coupon.codeValue == null),
        ))
        message.value = "쿠폰 정보를 저장했어요."
        screen.value = WalletScreen.Home
    }

    fun toggleFavorite(coupon: Coupon) = viewModelScope.launch { repository.save(coupon.copy(favorite = !coupon.favorite)) }
    fun move(couponId: String, location: CouponLocation) = viewModelScope.launch {
        repository.setLocation(couponId, location)
        screen.value = if (location == CouponLocation.WALLET) WalletScreen.Archive else WalletScreen.Home
        message.value = if (location == CouponLocation.WALLET) "지갑으로 복원했어요." else "보관함으로 옮겼어요."
    }
    fun markRedeemed(couponId: String) = viewModelScope.launch {
        repository.record(couponId, UsageEventType.REDEEM_MARKED, operationId = "redeem-${UUID.randomUUID()}")
        screen.value = WalletScreen.Home
        message.value = "사용한 쿠폰으로 기록했어요."
    }
    fun setBalance(couponId: String, amount: Long) = viewModelScope.launch {
        repository.record(couponId, UsageEventType.BALANCE_SET, amountMinor = amount)
        message.value = "기준 잔액을 기록했어요."
        screen.value = WalletScreen.Detail(couponId)
    }
    fun recordSpend(couponId: String, amount: Long) = viewModelScope.launch {
        repository.record(couponId, UsageEventType.SPEND_RECORDED, amountMinor = amount)
        message.value = "사용 금액을 기록했어요."
        screen.value = WalletScreen.Detail(couponId)
    }
}

class WalletViewModelFactory(
    private val repository: CouponRepository,
    private val importer: CouponImporter,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = WalletViewModel(repository, importer) as T
}
