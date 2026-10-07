package com.example.ui.theme

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Screen size classifications for phone form factors:
 * - COMPACT: Small phones (screen width < 360dp, e.g. 4.0" - 4.7" screens)
 * - MEDIUM: Standard phones (screen width 360dp - 410dp, e.g. 5.0" - 6.0" screens)
 * - EXPANDED: Large phones / Phablets (screen width > 410dp, e.g. 6.5" - 7.0"+ screens)
 */
enum class WindowWidthSizeClass {
    COMPACT,
    MEDIUM,
    EXPANDED
}

data class ResponsiveDimensions(
    val widthClass: WindowWidthSizeClass,
    val screenWidthDp: Int,
    val screenHeightDp: Int,
    val isSmallPhone: Boolean,
    val isLargePhone: Boolean,
    val horizontalPadding: Dp,
    val cardPadding: Dp,
    val itemSpacing: Dp,
    val buttonMinHeight: Dp,
    val touchTargetMinSize: Dp,
    val screenPaddingHorizontal: Dp = horizontalPadding,
    val screenPaddingVertical: Dp = if (isSmallPhone) 10.dp else 16.dp
)

@Composable
@ReadOnlyComposable
fun rememberResponsiveDimensions(): ResponsiveDimensions {
    val configuration = LocalConfiguration.current
    val width = configuration.screenWidthDp
    val height = configuration.screenHeightDp

    val widthClass = when {
        width < 360 -> WindowWidthSizeClass.COMPACT
        width <= 412 -> WindowWidthSizeClass.MEDIUM
        else -> WindowWidthSizeClass.EXPANDED
    }

    val isSmall = widthClass == WindowWidthSizeClass.COMPACT
    val isLarge = widthClass == WindowWidthSizeClass.EXPANDED

    return ResponsiveDimensions(
        widthClass = widthClass,
        screenWidthDp = width,
        screenHeightDp = height,
        isSmallPhone = isSmall,
        isLargePhone = isLarge,
        horizontalPadding = if (isSmall) 12.dp else if (isLarge) 18.dp else 16.dp,
        cardPadding = if (isSmall) 12.dp else if (isLarge) 16.dp else 14.dp,
        itemSpacing = if (isSmall) 10.dp else if (isLarge) 16.dp else 14.dp,
        buttonMinHeight = 48.dp,
        touchTargetMinSize = 48.dp
    )
}

/**
 * Clamp a font size smoothly between [minSp] and [maxSp] based on the current screen width.
 * Standard phone width is ~390dp.
 */
@Composable
@ReadOnlyComposable
fun responsiveSp(minSp: Float, standardSp: Float, maxSp: Float): TextUnit {
    val configuration = LocalConfiguration.current
    val width = configuration.screenWidthDp
    val scale = when {
        width < 360 -> (width / 360f).coerceIn(0.85f, 1.0f)
        width > 420 -> (width / 420f).coerceIn(1.0f, 1.25f)
        else -> 1.0f
    }
    val target = (standardSp * scale).coerceIn(minSp, maxSp)
    return target.sp
}

/**
 * Scalable Dp value that adjusts slightly for small (<360dp) or large (>420dp) screens.
 */
@Composable
@ReadOnlyComposable
fun responsiveDp(smallDp: Dp, standardDp: Dp, largeDp: Dp): Dp {
    val configuration = LocalConfiguration.current
    val width = configuration.screenWidthDp
    return when {
        width < 360 -> smallDp
        width > 420 -> largeDp
        else -> standardDp
    }
}
