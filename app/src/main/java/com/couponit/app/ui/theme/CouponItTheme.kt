package com.couponit.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Paper = Color(0xFFF5F3EF)
val Card = Color(0xFFFFFFFF)
val Ink = Color(0xFF14171C)
val Muted = Color(0xFF6B7078)
val Rule = Color(0xFFE2DFD8)
val Navy = Color(0xFF1B3A6B)
val NavySoft = Color(0xFFEAEFF7)
val Warning = Color(0xFFB4551F)
val WarningSoft = Color(0xFFF8ECE3)

@Composable
fun CouponItTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(primary = Navy, onPrimary = Paper, background = Paper, surface = Card, onSurface = Ink, outline = Rule, error = Warning),
        content = content,
    )
}
