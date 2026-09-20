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
import com.couponit.app.domain.DateInput
import com.couponit.app.domain.UsageEventType
import com.couponit.app.domain.WalletRules
import com.couponit.app.domain.WalletSummary
import com.couponit.app.importing.CouponImporter
import com.couponit.app.recognition.CouponTextFields
import kotlinx.coroutines.CancellationException
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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
            summary = WalletRules.summary(coupons, java.time.LocalDate.now()),
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
    /** 시스템 뒤로가기. 처리했으면 true, 최상위라 앱 기본 동작에 맡겨야 하면 false. */
    fun back(): Boolean {
        val current = screen.value
        if (current == WalletScreen.Home) {
            if (query.value.isEmpty() && merchant.value == null) return false
            query.value = ""
            merchant.value = null
            return true
        }
        open(WalletNavigation.backTarget(current) ?: WalletScreen.Home)
        return true
    }

    fun dismissMessage() { message.value = null }
    fun notify(value: String) { message.value = value }

    fun import(uris: List<Uri>) = viewModelScope.launch {
        if (uris.isEmpty() || importing.value) return@launch
        importing.value = true
        val results = try { uris.take(30).map { importer.import(it) } } finally { importing.value = false }
        val found = results.sumOf { it.found }
        val saved = results.sumOf { it.saved }
        val duplicates = results.sumOf { it.duplicates }
        val review = results.sumOf { it.needsReview }
        val failed = results.count { it.error != null }
        message.value = buildString {
            append(if (saved > 0) "쿠폰 ${saved}개를 저장했어요." else "새로 저장한 쿠폰이 없어요.")
            if (found > saved + duplicates) append(" 이미지에서 ${found}개를 찾았어요.")
            if (duplicates > 0) append(" 이미 있는 쿠폰 ${duplicates}개는 건너뛰었어요.")
            if (review > 0) append(" ${review}개는 정보 확인이 필요해요.")
            if (failed > 0) append(" ${failed}장은 추가하지 못했어요.")
            if (results.any { it.textRecognitionFailed }) append(" 글자 인식에 실패한 쿠폰은 상세에서 다시 인식해 주세요.")
        }
    }

    suspend fun recognizeDetails(coupon: Coupon): CouponTextFields? {
        val path = coupon.originalAssetPath ?: return null
        return try {
            importer.recognizeText(path, coupon.crop)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            message.value = "글자를 인식하지 못했어요. 다시 시도하거나 직접 입력해 주세요."
            null
        }
    }

    fun saveDetails(coupon: Coupon, title: String, merchantName: String, expiry: String, type: CouponType, issuerName: String?) = viewModelScope.launch {
        val parsed = DateInput.parse(expiry)
        if (expiry.isNotBlank() && parsed == null) {
            message.value = "종료일은 2026-12-09 또는 20261209처럼 올바른 날짜로 입력해 주세요."
            return@launch
        }
        repository.save(coupon.copy(
            title = title.ifBlank { "이름 미확인" }, merchantName = merchantName.ifBlank { null }, type = type, issuerName = issuerName,
            expiryDate = parsed, expiryConfirmed = parsed != null,
            needsReview = title.isBlank() || merchantName.isBlank() || parsed == null || (coupon.codeValue == null),
        ))
        message.value = "쿠폰 정보를 저장했어요."
        open(WalletNavigation.backTarget(screen.value) ?: WalletScreen.Home)
    }

    fun toggleFavorite(coupon: Coupon) = viewModelScope.launch { repository.save(coupon.copy(favorite = !coupon.favorite)) }
    fun archive(couponId: String) = viewModelScope.launch {
        repository.setLocation(couponId, CouponLocation.ARCHIVED)
        open(WalletScreen.Home)
        message.value = "보관함으로 옮겼어요."
    }
    fun restore(coupon: Coupon) = viewModelScope.launch {
        repository.restoreToWallet(coupon)
        if (screen.value is WalletScreen.Detail) open(WalletNavigation.backTarget(screen.value) ?: WalletScreen.Home)
        message.value = "지갑으로 복원했어요."
    }
    fun markRedeemed(couponId: String) = viewModelScope.launch {
        repository.record(couponId, UsageEventType.REDEEM_MARKED, operationId = "redeem-${UUID.randomUUID()}")
        open(WalletScreen.Home)
        message.value = "사용한 쿠폰으로 기록했어요. 잘못 눌렀다면 보관함에서 복원할 수 있어요."
    }
    fun setBalance(couponId: String, amount: Long) = viewModelScope.launch {
        repository.record(couponId, UsageEventType.BALANCE_SET, amountMinor = amount)
        message.value = "기준 잔액을 기록했어요."
    }
    fun recordSpend(couponId: String, amount: Long) = viewModelScope.launch {
        repository.record(couponId, UsageEventType.SPEND_RECORDED, amountMinor = amount)
        message.value = "사용 금액을 기록했어요."
    }
}

class WalletViewModelFactory(
    private val repository: CouponRepository,
    private val importer: CouponImporter,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = WalletViewModel(repository, importer) as T
}
