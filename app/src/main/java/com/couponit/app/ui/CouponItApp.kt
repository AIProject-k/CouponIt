package com.couponit.app.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.couponit.app.data.local.CodeCandidateEntity
import com.couponit.app.domain.Coupon
import com.couponit.app.domain.CouponLocation
import com.couponit.app.domain.CouponType
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

@Composable
fun CouponItApp(model: WalletViewModel, onPickImages: () -> Unit) {
    val state by model.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(it); model.dismissMessage() }
    }

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
                is WalletScreen.Detail -> state.all.firstOrNull { it.id == screen.couponId }?.let { DetailScreen(it, model) }
                is WalletScreen.Present -> state.all.firstOrNull { it.id == screen.couponId }?.let { PresentScreen(it, state.preferredCode, model) }
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
    Card(colors = CardDefaults.cardColors(containerColor = Card), border = androidx.compose.foundation.BorderStroke(1.dp, Rule), shape = RoundedCornerShape(6.dp)) {
        if (grid) {
            Column {
                Box(Modifier.fillMaxWidth().height(74.dp).background(typeColor(coupon.type)), contentAlignment = Alignment.Center) {
                    Text(coupon.merchantName?.take(1) ?: "?", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Navy.copy(alpha = .55f))
                }
                CouponInfo(coupon, Modifier.clickable { model.open(WalletScreen.Detail(coupon.id)) }.padding(10.dp))
                PresentButton(coupon, model, Modifier.fillMaxWidth().padding(8.dp))
            }
        } else Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.padding(10.dp).size(48.dp).background(typeColor(coupon.type), RoundedCornerShape(4.dp)), contentAlignment = Alignment.Center) { Text(coupon.merchantName?.take(1) ?: "?", fontWeight = FontWeight.Bold) }
            CouponInfo(coupon, Modifier.weight(1f).clickable { model.open(WalletScreen.Detail(coupon.id)) }.padding(vertical = 10.dp))
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
        Text(if (coupon.codeValue != null) "바코드" else "원본", fontSize = 12.sp)
    }
}

@Composable
private fun DetailScreen(coupon: Coupon, model: WalletViewModel) {
    var title by remember(coupon.id) { mutableStateOf(if (coupon.title == "정보 확인 필요") "" else coupon.title) }
    var merchant by remember(coupon.id) { mutableStateOf(coupon.merchantName.orEmpty()) }
    var expiry by remember(coupon.id) { mutableStateOf(coupon.expiryDate?.toString().orEmpty()) }
    var type by remember(coupon.id) { mutableStateOf(coupon.type) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            OutlinedButton({ model.open(WalletScreen.Home) }) { Text("← 지갑") }
            Row { OutlinedButton({ model.toggleFavorite(coupon) }) { Text(if (coupon.favorite) "즐겨찾기 해제" else "즐겨찾기") }; Spacer(Modifier.width(6.dp)); OutlinedButton({ model.move(coupon.id, CouponLocation.ARCHIVED) }) { Text("보관") } }
        }
        Spacer(Modifier.height(16.dp))
        OriginalImage(coupon.originalAssetPath, null, Modifier.fillMaxWidth().height(230.dp))
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), label = { Text("상품명") }, singleLine = true)
        OutlinedTextField(merchant, { merchant = it }, Modifier.fillMaxWidth(), label = { Text("사용처") }, singleLine = true)
        OutlinedTextField(expiry, { expiry = it }, Modifier.fillMaxWidth(), label = { Text("사용 기간 종료 (YYYY-MM-DD)") }, singleLine = true)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { items(CouponType.entries) { value -> FilterChip(type == value, { type = value }, { Text(typeLabel(value)) }) } }
        Button({ model.saveDetails(coupon, title, merchant, expiry, type) }, Modifier.fillMaxWidth()) { Text("정보 저장") }
        Button({ model.open(WalletScreen.Present(coupon.id)) }, Modifier.fillMaxWidth(), enabled = coupon.originalAssetPath != null) { Text(if (coupon.codeValue != null) "바코드 크게 보기" else "원본 크게 보기") }
        if (coupon.type == CouponType.AMOUNT) AmountControls(coupon, model)
        else Button({ model.markRedeemed(coupon.id) }, Modifier.fillMaxWidth()) { Text("사용했어요") }
        Text("코드를 열어 본 사실과 실제 사용 기록은 별도로 저장됩니다.", color = Muted, fontSize = 11.sp)
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
private fun PresentScreen(coupon: Coupon, code: CodeCandidateEntity?, model: WalletViewModel) {
    val activity = LocalActivity.current ?: return
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
            OutlinedButton({ model.open(WalletScreen.Detail(coupon.id)) }) { Text("닫기") }
            Text(coupon.merchantName ?: "사용처 미확인", color = Muted)
        }
        Spacer(Modifier.height(18.dp)); Text(coupon.title, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            OriginalImage(coupon.originalAssetPath, code, Modifier.fillMaxWidth().height(360.dp).background(Color.White).padding(18.dp))
        }
        coupon.codeValue?.let { Text(it, fontFamily = FontFamily.Monospace, letterSpacing = 1.2.sp, modifier = Modifier.semantics { contentDescription = "현재 쿠폰 번호" }) }
        Text("직원에게 코드를 보여주세요.", color = Muted, modifier = Modifier.padding(10.dp))
        Button({ model.markRedeemed(coupon.id) }, Modifier.fillMaxWidth()) { Text(if (coupon.type == CouponType.AMOUNT) "사용 금액 기록은 상세에서" else "사용했어요") }
        Text("보기만 하고 닫으면 사용 전 상태로 남습니다.", color = Muted, fontSize = 11.sp)
    }
}

@Composable
private fun OriginalImage(path: String?, code: CodeCandidateEntity?, modifier: Modifier) {
    val bitmap = remember(path, code?.id) { path?.let { decodeBitmap(it, code) } }
    if (bitmap != null) Image(bitmap.asImageBitmap(), "현재 쿠폰 원본 코드", modifier.clip(RoundedCornerShape(6.dp)), contentScale = ContentScale.Fit)
    else Box(modifier.background(NavySoft, RoundedCornerShape(6.dp)), contentAlignment = Alignment.Center) { Text("저장된 원본이 없어요.", color = Muted) }
}

private fun decodeBitmap(path: String, code: CodeCandidateEntity?): Bitmap? {
    val source = BitmapFactory.decodeFile(path) ?: return null
    if (code == null || code.right <= code.left || code.bottom <= code.top) return source
    val padX = ((code.right - code.left) * .12f).toInt()
    val padY = ((code.bottom - code.top) * .18f).toInt()
    val left = (code.left - padX).coerceAtLeast(0)
    val top = (code.top - padY).coerceAtLeast(0)
    val right = (code.right + padX).coerceAtMost(source.width)
    val bottom = (code.bottom + padY).coerceAtMost(source.height)
    return Bitmap.createBitmap(source, left, top, right - left, bottom - top)
}

@Composable
private fun ArchiveScreen(state: WalletUiState, model: WalletViewModel) {
    val archived = state.all.filter { it.location != CouponLocation.WALLET || it.usageState.name == "COMPLETED" }
    Column(Modifier.fillMaxSize()) {
        Text("보관함", fontSize = 21.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp))
        if (archived.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("보관된 쿠폰이 없어요.", color = Muted) }
        else LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(archived) { coupon -> Card(colors = CardDefaults.cardColors(containerColor = Card), border = androidx.compose.foundation.BorderStroke(1.dp, Rule)) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text(coupon.merchantName ?: "사용처 미확인", color = Muted, fontSize = 11.sp); Text(coupon.title, fontWeight = FontWeight.SemiBold) }
                    OutlinedButton({ model.move(coupon.id, CouponLocation.WALLET) }) { Text("복원") }
                }
            } }
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

private fun typeColor(type: CouponType) = when (type) {
    CouponType.EXCHANGE -> Color(0xFFEAEFF7)
    CouponType.AMOUNT -> Color(0xFFE8F0EC)
    CouponType.DISCOUNT -> Color(0xFFF8ECE3)
    CouponType.NUMBER -> Color(0xFFEDEAE3)
    CouponType.UNKNOWN -> Color(0xFFE9E9E9)
}

private fun typeLabel(type: CouponType) = when (type) {
    CouponType.EXCHANGE -> "교환권"; CouponType.AMOUNT -> "금액권"; CouponType.DISCOUNT -> "할인권"; CouponType.NUMBER -> "번호"; CouponType.UNKNOWN -> "미확인"
}

private fun expiryLabel(coupon: Coupon): String {
    if (!coupon.expiryConfirmed || coupon.expiryDate == null) return "기간 확인"
    val days = ChronoUnit.DAYS.between(LocalDate.now(), coupon.expiryDate)
    return when { days < 0 -> "기한 경과"; days == 0L -> "오늘까지"; else -> "D-$days · ${coupon.expiryDate}" }
}
