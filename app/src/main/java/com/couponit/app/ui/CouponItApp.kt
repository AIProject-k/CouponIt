package com.couponit.app.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.PersistableBundle
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Wallet
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.couponit.app.data.local.CodeCandidateEntity
import com.couponit.app.domain.Coupon
import com.couponit.app.domain.CouponLocation
import com.couponit.app.domain.CouponIssuer
import com.couponit.app.domain.CouponType
import com.couponit.app.domain.IssuerLookup
import com.couponit.app.domain.PixelRect
import com.couponit.app.domain.UsageState
import com.couponit.app.ui.theme.Card
import com.couponit.app.ui.theme.Ink
import com.couponit.app.ui.theme.Muted
import com.couponit.app.ui.theme.Navy
import com.couponit.app.ui.theme.NavySoft
import com.couponit.app.ui.theme.Paper
import com.couponit.app.ui.theme.Rule
import com.couponit.app.ui.theme.Warning
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.launch

@Composable
fun CouponItApp(model: WalletViewModel, onPickImages: () -> Unit) {
    val state by model.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(it); model.dismissMessage() }
    }
    // 하위 화면에서는 앱을 나가지 않고 이전 화면으로, 홈에서는 검색·필터부터 해제한다.
    BackHandler(enabled = state.screen != WalletScreen.Home || state.query.isNotEmpty() || state.merchant != null) { model.back() }

    Scaffold(
        containerColor = Paper,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (state.screen is WalletScreen.Home || state.screen is WalletScreen.Archive || state.screen is WalletScreen.Settings) {
                NavigationBar(containerColor = Card) {
                    NavigationBarItem(state.screen is WalletScreen.Home, { model.open(WalletScreen.Home) }, { Icon(Icons.Outlined.Wallet, null) }, label = { Text("지갑") })
                    NavigationBarItem(state.screen is WalletScreen.Archive, { model.open(WalletScreen.Archive) }, { Icon(Icons.Outlined.Archive, null) }, label = { Text("보관함") })
                    NavigationBarItem(state.screen is WalletScreen.Settings, { model.open(WalletScreen.Settings) }, { Icon(Icons.Outlined.Settings, null) }, label = { Text("설정") })
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (val screen = state.screen) {
                WalletScreen.Home -> HomeScreen(state, model, onPickImages)
                WalletScreen.Archive -> ArchiveScreen(state, model)
                WalletScreen.Settings -> SettingsScreen()
                is WalletScreen.Detail -> state.all.firstOrNull { it.id == screen.couponId }?.let { DetailScreen(it, screen, model) }
                is WalletScreen.Present -> state.all.firstOrNull { it.id == screen.couponId }?.let { PresentScreen(it, state.preferredCode, screen, model) }
            }
            if (state.importing) Box(Modifier.fillMaxSize().background(Color(0x99000000)), contentAlignment = Alignment.Center) {
                Text("원본을 안전하게 저장하고 있어요…", color = Color.White, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun HomeScreen(state: WalletUiState, model: WalletViewModel, onPickImages: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(16.dp, 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("쿠폰잇", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("MY WALLET", fontSize = 10.sp, letterSpacing = 1.4.sp, color = Muted, fontFamily = FontFamily.Monospace)
            }
            Button(onClick = onPickImages, shape = RoundedCornerShape(6.dp)) { Icon(Icons.Outlined.Add, null); Spacer(Modifier.width(4.dp)); Text("쿠폰 추가") }
        }
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SummaryCell(state.summary.unused.toString(), "사용 전", Modifier.weight(1f))
            SummaryCell(state.summary.expiringInSevenDays.toString(), "7일 안에", Modifier.weight(1f), Warning)
            SummaryCell(state.summary.needsReview.toString(), "정보 확인", Modifier.weight(1f))
        }
        OutlinedTextField(
            state.query, model::setQuery, Modifier.fillMaxWidth().padding(16.dp, 10.dp, 16.dp, 0.dp),
            placeholder = { Text("사용처 · 상품명 검색") }, leadingIcon = { Icon(Icons.Outlined.Search, null) }, singleLine = true,
        )
        val merchants = state.all.filter { it.location == CouponLocation.WALLET }.mapNotNull { it.merchantName }.distinct().sorted()
        LazyRow(Modifier.padding(top = 8.dp), contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            item { FilterChip(state.merchant == null, { model.setMerchant(null) }, { Text("전체") }) }
            items(merchants) { merchant -> FilterChip(state.merchant == merchant, { model.setMerchant(merchant) }, { Text(merchant) }) }
        }
        Row(Modifier.fillMaxWidth().padding(16.dp, 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("임박순 · 기간 미확인은 뒤로", color = Muted, fontSize = 12.sp)
            Row { FilterChip(state.grid, { model.setGrid(true) }, { Text("2열") }); Spacer(Modifier.width(4.dp)); FilterChip(!state.grid, { model.setGrid(false) }, { Text("목록") }) }
        }
        if (state.visible.isEmpty()) EmptyWallet(onPickImages, state.query.isNotBlank())
        else {
            val forceList = LocalConfiguration.current.fontScale >= 1.3f
            if (state.grid && !forceList) LazyVerticalGrid(
                GridCells.Fixed(2), Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 20.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
            ) { items(state.visible, key = { it.id }) { CouponCard(it, model, true) } }
            else LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.visible, key = { it.id }) { CouponCard(it, model, false) }
            }
        }
    }
}

@Composable
private fun SummaryCell(value: String, label: String, modifier: Modifier, valueColor: Color = Ink) {
    Column(modifier.border(1.dp, Rule, RoundedCornerShape(6.dp)).background(Card).padding(12.dp)) {
        Text(value, fontSize = 21.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace, color = valueColor)
        Text(label, fontSize = 11.sp, color = Muted)
    }
}

@Composable
private fun CouponCard(coupon: Coupon, model: WalletViewModel, grid: Boolean) {
    // 카드 어디를 눌러도 정보 수정 화면. 원본·바코드 버튼만 코드 제시 화면으로 간다.
    Card(
        onClick = { model.open(WalletScreen.Detail(coupon.id)) },
        colors = CardDefaults.cardColors(containerColor = Card), border = androidx.compose.foundation.BorderStroke(1.dp, Rule), shape = RoundedCornerShape(6.dp),
    ) {
        if (grid) {
            Column {
                OriginalImage(coupon.originalAssetPath, coupon.crop, Modifier.fillMaxWidth().height(150.dp).background(Color.White))
                CouponInfo(coupon, Modifier.padding(10.dp))
                PresentButton(coupon, model, Modifier.fillMaxWidth().padding(8.dp))
            }
        } else Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OriginalImage(coupon.originalAssetPath, coupon.crop, Modifier.padding(10.dp).size(72.dp))
            CouponInfo(coupon, Modifier.weight(1f).padding(vertical = 10.dp))
            PresentButton(coupon, model, Modifier.fillMaxHeight().width(76.dp).padding(6.dp))
        }
    }
}

@Composable
private fun CouponInfo(coupon: Coupon, modifier: Modifier) {
    Column(modifier) {
        Text(coupon.merchantName ?: "사용처 미확인", fontSize = 11.sp, color = Muted)
        Text(coupon.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(6.dp)); Text(expiryLabel(coupon), fontSize = 10.sp, color = if (coupon.expiryConfirmed) Warning else Muted)
    }
}

@Composable
private fun PresentButton(coupon: Coupon, model: WalletViewModel, modifier: Modifier) {
    Button(onClick = { if (coupon.originalAssetPath != null) model.open(WalletScreen.Present(coupon.id)) else model.open(WalletScreen.Detail(coupon.id)) }, modifier = modifier, shape = RoundedCornerShape(5.dp), contentPadding = PaddingValues(6.dp)) {
        Text(if (coupon.codeValue != null) "원본·바코드" else "원본", fontSize = 12.sp)
    }
}

@Composable
private fun DetailScreen(coupon: Coupon, screen: WalletScreen.Detail, model: WalletViewModel) {
    val initialTitle = if (coupon.title == "정보 확인 필요") "" else coupon.title
    var title by remember(coupon.id) { mutableStateOf(initialTitle) }
    var merchant by remember(coupon.id) { mutableStateOf(coupon.merchantName.orEmpty()) }
    var expiry by remember(coupon.id) { mutableStateOf(coupon.expiryDate?.toString().orEmpty()) }
    var type by remember(coupon.id) { mutableStateOf(coupon.type) }
    var recognizing by remember(coupon.id) { mutableStateOf(false) }
    var issuerName by remember(coupon.id) { mutableStateOf(IssuerLookup.byName(coupon.issuerName)?.name) }
    val suggestedIssuer = IssuerLookup.suggest(merchant)
    val issuer = IssuerLookup.byName(issuerName) ?: suggestedIssuer
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val inWallet = coupon.location == CouponLocation.WALLET && coupon.usageState != UsageState.COMPLETED
    val dirty = title != initialTitle || merchant != coupon.merchantName.orEmpty() ||
        expiry != coupon.expiryDate?.toString().orEmpty() || type != coupon.type ||
        issuerName != IssuerLookup.byName(coupon.issuerName)?.name
    // 저장하지 않은 수정이 있으면 화면을 떠나기 전에 확인한다.
    var pendingLeave by remember(coupon.id) { mutableStateOf<(() -> Unit)?>(null) }
    var confirmRedeem by remember(coupon.id) { mutableStateOf(false) }
    fun leaveThen(action: () -> Unit) { if (dirty) pendingLeave = action else action() }
    BackHandler(enabled = dirty) { pendingLeave = { model.back() } }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            OutlinedButton({ leaveThen { model.back() } }) { Text("← 뒤로") }
            Row {
                OutlinedButton({ model.toggleFavorite(coupon) }) { Text(if (coupon.favorite) "즐겨찾기 해제" else "즐겨찾기") }
                Spacer(Modifier.width(6.dp))
                if (inWallet) OutlinedButton({ leaveThen { model.archive(coupon.id) } }) { Text("보관") }
                else OutlinedButton({ leaveThen { model.restore(coupon) } }) { Text("지갑으로 복원") }
            }
        }
        Spacer(Modifier.height(16.dp))
        OriginalImage(coupon.originalAssetPath, coupon.crop, Modifier.fillMaxWidth().height(230.dp))
        OutlinedButton({
            recognizing = true
            scope.launch {
                try {
                    model.recognizeDetails(coupon)?.let { fields ->
                        if (title.isBlank() || title == "이름 미확인") title = fields.title.orEmpty()
                        if (merchant.isBlank()) merchant = fields.merchantName.orEmpty()
                        if (expiry.isBlank()) expiry = fields.expiryDate?.toString().orEmpty()
                    }
                } finally { recognizing = false }
            }
        }, Modifier.fillMaxWidth(), enabled = !recognizing && coupon.originalAssetPath != null) {
            Text(if (recognizing) "글자 인식 중…" else "이미지에서 정보 다시 인식")
        }
        Text("재인식은 빈 항목만 채웁니다. 원본과 비교한 뒤 저장해 주세요.", color = Muted, fontSize = 11.sp)
        Spacer(Modifier.height(12.dp))
        val next = KeyboardOptions(imeAction = ImeAction.Next)
        OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), label = { Text("상품명") }, singleLine = true, keyboardOptions = next)
        OutlinedTextField(merchant, { merchant = it }, Modifier.fillMaxWidth(), label = { Text("사용처") }, singleLine = true, keyboardOptions = next)
        OutlinedTextField(
            expiry, { expiry = it }, Modifier.fillMaxWidth(), label = { Text("사용 기간 종료") }, singleLine = true,
            placeholder = { Text("예: 2026-12-09 또는 20261209") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { items(CouponType.entries) { value -> FilterChip(type == value, { type = value }, { Text(typeLabel(value)) }) } }
        Spacer(Modifier.height(8.dp))
        Text("발행사", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(IssuerLookup.issuers) { value -> FilterChip(issuer?.name == value.name, { issuerName = value.name }, { Text(value.name) }) }
        }
        Text(
            when {
                issuer == null -> "쿠폰 원본에 적힌 발행사를 고르면 사용 여부 조회 페이지를 열 수 있어요."
                issuerName == null -> "사용처 기준 추천이에요. 원본의 발행사 표기가 다르면 바꿔 주세요."
                else -> issuer.howTo
            },
            color = Muted, fontSize = 11.sp,
        )
        Button({ model.saveDetails(coupon, title, merchant, expiry, type, issuer?.name) }, Modifier.fillMaxWidth(), enabled = !recognizing) { Text("정보 저장") }
        Button({ leaveThen { model.open(WalletScreen.Present(coupon.id, from = screen)) } }, Modifier.fillMaxWidth(), enabled = coupon.originalAssetPath != null) { Text(if (coupon.codeValue != null) "원본·바코드 크게 보기" else "원본 크게 보기") }
        OutlinedButton({ issuer?.let { openIssuerLookup(context, it, coupon.codeValue, model) } }, Modifier.fillMaxWidth(), enabled = issuer != null) {
            Text("발행사에서 사용 여부 조회")
        }
        Text("쿠폰 번호를 복사하고 조회 페이지를 열어요. 사용 완료로 나오면 아래에서 기록해 주세요.", color = Muted, fontSize = 11.sp)
        when {
            coupon.usageState == UsageState.COMPLETED -> Text("사용 완료로 기록된 쿠폰이에요. 다시 쓰려면 위의 지갑으로 복원을 눌러 주세요.", color = Muted, fontSize = 12.sp)
            coupon.type == CouponType.AMOUNT -> AmountControls(coupon, model)
            else -> Button({ confirmRedeem = true }, Modifier.fillMaxWidth()) { Text("사용했어요") }
        }
        Text("코드를 열어 본 사실과 실제 사용 기록은 별도로 저장됩니다.", color = Muted, fontSize = 11.sp)
    }
    pendingLeave?.let { action ->
        LeaveConfirmDialog(onLeave = { pendingLeave = null; action() }, onDismiss = { pendingLeave = null })
    }
    if (confirmRedeem) RedeemConfirmDialog(onConfirm = { confirmRedeem = false; model.markRedeemed(coupon.id) }, onDismiss = { confirmRedeem = false })
}

private fun openIssuerLookup(context: Context, issuer: CouponIssuer, code: String?, model: WalletViewModel) {
    if (code != null) {
        val clip = ClipData.newPlainText("쿠폰 번호", code)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // 시스템 붙여넣기 미리보기에 쿠폰 번호가 그대로 보이지 않게 한다.
            clip.description.extras = PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
        }
        context.getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
    }
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, issuer.lookupUrl.toUri()))
        model.notify(if (code != null) "쿠폰 번호를 복사했어요. ${issuer.name} 조회 페이지에 붙여 넣어 확인해 주세요." else "인식된 쿠폰 번호가 없어요. 원본을 보고 직접 입력해 주세요.")
    } catch (_: ActivityNotFoundException) {
        model.notify("웹 페이지를 열 수 있는 앱이 없어요.")
    }
}

@Composable
private fun AmountControls(coupon: Coupon, model: WalletViewModel) {
    var amount by remember { mutableStateOf("") }
    OutlinedTextField(amount, { amount = it.filter(Char::isDigit) }, Modifier.fillMaxWidth(), label = { Text("금액(원)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton({ amount.toLongOrNull()?.let { model.setBalance(coupon.id, it) } }, Modifier.weight(1f)) { Text("기준 잔액") }
        Button({ amount.toLongOrNull()?.let { model.recordSpend(coupon.id, it) } }, Modifier.weight(1f)) { Text("사용 금액 기록") }
    }
    Text("액면가를 현재 잔액으로 자동 확정하지 않습니다.", color = Muted, fontSize = 11.sp)
}

@Composable
private fun PresentScreen(coupon: Coupon, code: CodeCandidateEntity?, screen: WalletScreen.Present, model: WalletViewModel) {
    val activity = LocalActivity.current ?: return
    var confirmRedeem by remember(coupon.id) { mutableStateOf(false) }
    DisposableEffect(coupon.id) {
        val oldBrightness = activity.window.attributes.screenBrightness
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        activity.window.attributes = activity.window.attributes.apply { screenBrightness = 1f }
        onDispose {
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            activity.window.attributes = activity.window.attributes.apply { screenBrightness = oldBrightness }
        }
    }
    Column(Modifier.fillMaxSize().background(Paper).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton({ model.back() }) { Text("닫기") }
            Text(coupon.merchantName ?: "사용처 미확인", color = Muted)
        }
        Spacer(Modifier.height(18.dp)); Text(coupon.title, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("쿠폰 원본", modifier = Modifier.padding(8.dp), color = Muted)
            OriginalImage(coupon.originalAssetPath, coupon.crop, Modifier.fillMaxWidth().height(420.dp).background(Color.White))
            if (code != null) {
                Text("바코드 확대", modifier = Modifier.padding(8.dp), color = Muted)
                OriginalImage(
                    coupon.originalAssetPath, code.rect(), Modifier.fillMaxWidth().height(180.dp).background(Color.White).padding(12.dp),
                    description = "바코드 확대 이미지", pad = true,
                )
            }
            if (coupon.crop != null) {
                Text("전체 스크린샷", modifier = Modifier.padding(8.dp), color = Muted)
                OriginalImage(
                    coupon.originalAssetPath, null, Modifier.fillMaxWidth().height(260.dp).background(Color.White),
                    description = "전체 스크린샷 이미지",
                )
            }
        }
        coupon.codeValue?.let { Text(it, fontFamily = FontFamily.Monospace, letterSpacing = 1.2.sp, modifier = Modifier.semantics { contentDescription = "현재 쿠폰 번호" }) }
        Text("직원에게 코드를 보여주세요.", color = Muted, modifier = Modifier.padding(10.dp))
        when {
            coupon.usageState == UsageState.COMPLETED -> Text("사용 완료로 기록된 쿠폰이에요.", color = Muted, modifier = Modifier.padding(6.dp))
            coupon.type == CouponType.AMOUNT -> Button({ model.open(WalletScreen.Detail(coupon.id, from = screen)) }, Modifier.fillMaxWidth()) { Text("사용 금액 기록하러 가기") }
            else -> Button({ confirmRedeem = true }, Modifier.fillMaxWidth()) { Text("사용했어요") }
        }
        Text("보기만 하고 닫으면 사용 전 상태로 남습니다.", color = Muted, fontSize = 11.sp)
    }
    if (confirmRedeem) RedeemConfirmDialog(onConfirm = { confirmRedeem = false; model.markRedeemed(coupon.id) }, onDismiss = { confirmRedeem = false })
}

@Composable
private fun RedeemConfirmDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("사용한 쿠폰으로 기록할까요?") },
        text = { Text("지갑 목록에서 빠지고 보관함으로 옮겨져요. 잘못 눌렀다면 보관함에서 복원할 수 있어요.") },
        confirmButton = { TextButton(onConfirm) { Text("사용 완료로 기록") } },
        dismissButton = { TextButton(onDismiss) { Text("취소") } },
    )
}

@Composable
private fun LeaveConfirmDialog(onLeave: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("저장하지 않은 변경이 있어요") },
        text = { Text("지금 나가면 수정한 내용이 사라져요. 남기려면 계속 수정한 뒤 정보 저장을 눌러 주세요.") },
        confirmButton = { TextButton(onLeave) { Text("저장하지 않고 나가기") } },
        dismissButton = { TextButton(onDismiss) { Text("계속 수정") } },
    )
}

@Composable
private fun OriginalImage(
    path: String?,
    crop: PixelRect?,
    modifier: Modifier,
    description: String = "쿠폰 원본 이미지",
    pad: Boolean = false,
) {
    val bitmap = remember(path, crop, pad) { path?.let { decodeBitmap(it, crop, pad) } }
    if (bitmap != null) Image(bitmap.asImageBitmap(), description, modifier.clip(RoundedCornerShape(6.dp)), contentScale = ContentScale.Fit)
    else Box(modifier.background(NavySoft, RoundedCornerShape(6.dp)), contentAlignment = Alignment.Center) { Text("저장된 원본이 없어요.", color = Muted) }
}

private fun CodeCandidateEntity.rect() = PixelRect(left, top, right, bottom)

private fun decodeBitmap(path: String, crop: PixelRect?, pad: Boolean): Bitmap? {
    val source = BitmapFactory.decodeFile(path) ?: return null
    if (crop == null || crop.width <= 0 || crop.height <= 0) return source
    val padX = if (pad) (crop.width * .12f).toInt() else 0
    val padY = if (pad) (crop.height * .18f).toInt() else 0
    val left = (crop.left - padX).coerceIn(0, source.width - 1)
    val top = (crop.top - padY).coerceIn(0, source.height - 1)
    val right = (crop.right + padX).coerceIn(left + 1, source.width)
    val bottom = (crop.bottom + padY).coerceIn(top + 1, source.height)
    return Bitmap.createBitmap(source, left, top, right - left, bottom - top)
}

@Composable
private fun ArchiveScreen(state: WalletUiState, model: WalletViewModel) {
    val archived = state.all.filter { it.location != CouponLocation.WALLET || it.usageState == UsageState.COMPLETED }
    Column(Modifier.fillMaxSize()) {
        Text("보관함", fontSize = 21.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp))
        if (archived.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("보관된 쿠폰이 없어요.", color = Muted) }
        else LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(archived, key = { it.id }) { coupon ->
                Card(
                    onClick = { model.open(WalletScreen.Detail(coupon.id, from = WalletScreen.Archive)) },
                    colors = CardDefaults.cardColors(containerColor = Card), border = androidx.compose.foundation.BorderStroke(1.dp, Rule),
                ) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(coupon.merchantName ?: "사용처 미확인", color = Muted, fontSize = 11.sp)
                            Text(coupon.title, fontWeight = FontWeight.SemiBold)
                            Text(if (coupon.usageState == UsageState.COMPLETED) "사용 완료" else "보관됨", color = Muted, fontSize = 11.sp)
                        }
                        OutlinedButton({ model.restore(coupon) }) { Text("복원") }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen() {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("설정", fontSize = 21.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(18.dp))
        Card(colors = CardDefaults.cardColors(containerColor = Card), border = androidx.compose.foundation.BorderStroke(1.dp, Rule)) {
            Column(Modifier.padding(14.dp)) {
                Text("로컬 우선", fontWeight = FontWeight.SemiBold)
                Text("저장한 쿠폰은 로그인이나 서버 연결 없이 이 기기에서 열립니다.", color = Muted, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(12.dp))
        Card(colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color(0xFFF8ECE3))) {
            Column(Modifier.padding(14.dp)) {
                Text("백업 준비 중", fontWeight = FontWeight.SemiBold)
                Text("현재 빌드는 P0/P1 검증용입니다. 장기 보관 쿠폰은 암호화 백업·빈 설치 복원 검증 후 맡겨 주세요.", fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun EmptyWallet(onPickImages: () -> Unit, filtered: Boolean) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(if (filtered) "찾는 쿠폰이 없어요." else "쿠폰 이미지를 추가해 보세요.", fontWeight = FontWeight.SemiBold)
        if (!filtered) OutlinedButton(onPickImages) { Text("쿠폰 추가") }
    }
}

private fun typeLabel(type: CouponType) = when (type) {
    CouponType.EXCHANGE -> "교환권"; CouponType.AMOUNT -> "금액권"; CouponType.DISCOUNT -> "할인권"; CouponType.NUMBER -> "번호"; CouponType.UNKNOWN -> "미확인"
}

private fun expiryLabel(coupon: Coupon): String {
    if (!coupon.expiryConfirmed || coupon.expiryDate == null) return "기간 확인"
    val days = ChronoUnit.DAYS.between(LocalDate.now(), coupon.expiryDate)
    return when { days < 0 -> "기한 경과"; days == 0L -> "오늘까지"; else -> "D-$days · ${coupon.expiryDate}" }
}
