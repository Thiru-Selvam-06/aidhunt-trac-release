package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class AppColorScheme(
    val isDark: Boolean,
    val background: Color,
    val surface: Color,
    val cardBg: Color,
    val cardBorder: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val primary: Color,
    val primaryContainer: Color,
    val onPrimary: Color,
    val onPrimaryContainer: Color,
    val accent: Color,
    val inputBg: Color,
    val inputBorder: Color,
    val success: Color,
    val successBg: Color,
    val alert: Color,
    val alertBg: Color,
    val gold: Color,
    val goldBg: Color,
    val divider: Color
)

val LightAppColors = AppColorScheme(
    isDark = false,
    background = Color(0xFFFFFFFF),
    surface = Color(0xFFFFFFFF),
    cardBg = Color(0xFFF9FAFB),
    cardBorder = Color(0xFFE5E7EB),
    textPrimary = Color(0xFF111827),
    textSecondary = Color(0xFF4B5563),
    textMuted = Color(0xFF9CA3AF),
    primary = Color(0xFF0F5132),
    primaryContainer = Color(0xFFE8F5E9),
    onPrimary = Color(0xFFFFFFFF),
    onPrimaryContainer = Color(0xFF0F5132),
    accent = Color(0xFF198754),
    inputBg = Color(0xFFF9FAFB),
    inputBorder = Color(0xFFD1D5DB),
    success = Color(0xFF16A34A),
    successBg = Color(0xFFDCFCE7),
    alert = Color(0xFFDC2626),
    alertBg = Color(0xFFFEE2E2),
    gold = Color(0xFFB8860B),
    goldBg = Color(0xFFFEF9E7),
    divider = Color(0xFFE5E7EB)
)

val DarkAppColors = AppColorScheme(
    isDark = true,
    background = Color(0xFF121413),
    surface = Color(0xFF1B1F1D),
    cardBg = Color(0xFF232825),
    cardBorder = Color(0xFF37423B),
    textPrimary = Color(0xFFF3F4F6),
    textSecondary = Color(0xFFD1D5DB),
    textMuted = Color(0xFF9CA3AF),
    primary = Color(0xFF4ADE80),
    primaryContainer = Color(0xFF1B432C),
    onPrimary = Color(0xFF052E16),
    onPrimaryContainer = Color(0xFFBBF7D0),
    accent = Color(0xFF34D399),
    inputBg = Color(0xFF262D29),
    inputBorder = Color(0xFF3E4C42),
    success = Color(0xFF4ADE80),
    successBg = Color(0xFF143823),
    alert = Color(0xFFF87171),
    alertBg = Color(0xFF451A1A),
    gold = Color(0xFFFBBF24),
    goldBg = Color(0xFF382C10),
    divider = Color(0xFF2F3832)
)

val LocalAppColorScheme = staticCompositionLocalOf { LightAppColors }

object AppTheme {
    val colors: AppColorScheme
        @Composable
        @ReadOnlyComposable
        get() = LocalAppColorScheme.current
}

private val LightColorScheme = lightColorScheme(
    primary = DeepSageGreen,
    onPrimary = Color.White,
    primaryContainer = SoftSageGreen,
    onPrimaryContainer = ForestGreenHeader,
    secondary = SageAccent,
    onSecondary = Color.White,
    secondaryContainer = SageCardBg,
    onSecondaryContainer = DeepSageGreen,
    tertiary = EarthGold,
    onTertiary = Color.White,
    tertiaryContainer = EarthGoldSoft,
    onTertiaryContainer = EarthGoldText,
    background = Color.White,
    onBackground = TextPrimaryDark,
    surface = Color.White,
    onSurface = TextPrimaryDark,
    surfaceVariant = SageCardBg,
    onSurfaceVariant = TextSecondaryDark,
    outline = SageOutline,
    error = AlertDueRed,
    errorContainer = AlertDueRedBg,
    onError = Color.White
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF4ADE80),
    onPrimary = Color(0xFF052E16),
    primaryContainer = Color(0xFF1B432C),
    onPrimaryContainer = Color(0xFFBBF7D0),
    secondary = Color(0xFF34D399),
    onSecondary = Color(0xFF064E3B),
    secondaryContainer = Color(0xFF232825),
    onSecondaryContainer = Color(0xFFE2F0E5),
    tertiary = Color(0xFFFBBF24),
    onTertiary = Color(0xFF452B00),
    background = Color(0xFF121413),
    onBackground = Color(0xFFF3F4F6),
    surface = Color(0xFF1B1F1D),
    onSurface = Color(0xFFF3F4F6),
    surfaceVariant = Color(0xFF232825),
    onSurfaceVariant = Color(0xFFD1D5DB),
    outline = Color(0xFF37423B),
    error = Color(0xFFF87171),
    onError = Color(0xFF451A1A)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    // Forced Light Mode globally regardless of system setting
    val colorScheme = LightColorScheme
    val customColors = LightAppColors

    CompositionLocalProvider(LocalAppColorScheme provides customColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}

