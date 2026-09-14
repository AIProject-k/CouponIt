package com.couponit.app.ui

sealed interface WalletScreen {
    data object Home : WalletScreen
    data object Archive : WalletScreen
    data object Settings : WalletScreen
    /** from: 뒤로가기·닫기 시 돌아갈 화면 */
    data class Detail(val couponId: String, val from: WalletScreen = Home) : WalletScreen
    data class Present(val couponId: String, val from: WalletScreen = Home) : WalletScreen
}

object WalletNavigation {
    /** 시스템 뒤로가기의 목적지. null이면 최상위 화면이라 앱 기본 동작(종료)에 맡긴다. */
    fun backTarget(screen: WalletScreen): WalletScreen? = when (screen) {
        WalletScreen.Home -> null
        WalletScreen.Archive, WalletScreen.Settings -> WalletScreen.Home
        is WalletScreen.Detail -> screen.from
        is WalletScreen.Present -> screen.from
    }
}
