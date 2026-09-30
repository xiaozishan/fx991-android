package io.paimon.fx991.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.core.view.WindowCompat

val Mono = FontFamily.Monospace

// ---------------------------------------------------------------------------
// 识别色：橙 SHIFT / 紫 ALPHA —— 浅色与深色两种模式下都必须保留
// ---------------------------------------------------------------------------

val ShiftOrange = Color(0xFFF2A23C)
val ShiftOrangeDeep = Color(0xFFD9822B)
val ShiftOrangeInk = Color(0xFF2A1B08)

val AlphaPurple = Color(0xFF9B7BE0)
val AlphaPurpleDeep = Color(0xFF8A63D2)
val AlphaPurpleInk = Color(0xFF1B1030)

val AccentOrange = Color(0xFFE0873A)   // DEL / AC
val AccentOrangeInk = Color(0xFF2A1806)

// ---------------------------------------------------------------------------
// 计算器专用颜色令牌（Material 颜色方案的补充，全部由这两种主题派生）
// ---------------------------------------------------------------------------

data class CalcColors(
    val body: Color,
    val bodyEdge: Color,
    val bodyInk: Color,
    val lcdBg: Color,
    val lcdFg: Color,
    val lcdDim: Color,
    val lcdEdge: Color,
    val lcdError: Color,
    val keyNeutral: Color,
    val keyNeutralInk: Color,
    val keyDigit: Color,
    val keyDigitInk: Color,
    val keyOp: Color,
    val keyOpInk: Color,
    val keyAccent: Color,
    val keyAccentInk: Color,
    val keyShift: Color,
    val keyShiftInk: Color,
    val keyAlpha: Color,
    val keyAlphaInk: Color,
    val keyEquals: Color,
    val keyEqualsInk: Color,
    val keyEdge: Color,
    val chrome: Color,
    val chromeInk: Color,
    val chromeActive: Color,
)

private val LightCalc = CalcColors(
    body = Color(0xFFE5E9EE),
    bodyEdge = Color(0xFFD2D8E0),
    bodyInk = Color(0xFF23272E),
    lcdBg = Color(0xFFC9D6B4),
    lcdFg = Color(0xFF141C0F),
    lcdDim = Color(0xFF5C6B4A),
    lcdEdge = Color(0xFF8A9A76),
    lcdError = Color(0xFF9B2C1C),
    keyNeutral = Color(0xFFDCE1E8),
    keyNeutralInk = Color(0xFF23272E),
    keyDigit = Color(0xFFF1F4F8),
    keyDigitInk = Color(0xFF1A1D22),
    keyOp = Color(0xFFD2D8E0),
    keyOpInk = Color(0xFF1A1D22),
    keyAccent = AccentOrange,
    keyAccentInk = AccentOrangeInk,
    keyShift = ShiftOrange,
    keyShiftInk = ShiftOrangeInk,
    keyAlpha = AlphaPurpleDeep,
    keyAlphaInk = Color(0xFFF6F2FF),
    keyEquals = Color(0xFF4C5560),
    keyEqualsInk = Color(0xFFF5F7FA),
    keyEdge = Color(0x22000000),
    chrome = Color(0x00000000),
    chromeInk = Color(0xFF3A4048),
    chromeActive = Color(0xFF3A4048),
)

private val DarkCalc = CalcColors(
    body = Color(0xFF111418),
    bodyEdge = Color(0xFF1C2126),
    bodyInk = Color(0xFFEDF0F4),
    lcdBg = Color(0xFF2C3427),
    lcdFg = Color(0xFFE4EFD6),
    lcdDim = Color(0xFF7C8C6C),
    lcdEdge = Color(0xFF3B4630),
    lcdError = Color(0xFFE2705C),
    keyNeutral = Color(0xFF2B3138),
    keyNeutralInk = Color(0xFFE7EAEF),
    keyDigit = Color(0xFF363D46),
    keyDigitInk = Color(0xFFF3F6F9),
    keyOp = Color(0xFF31383F),
    keyOpInk = Color(0xFFF0F3F6),
    keyAccent = AccentOrange,
    keyAccentInk = AccentOrangeInk,
    keyShift = ShiftOrange,
    keyShiftInk = ShiftOrangeInk,
    keyAlpha = AlphaPurple,
    keyAlphaInk = AlphaPurpleInk,
    keyEquals = Color(0xFFDCE3EB),
    keyEqualsInk = Color(0xFF14181D),
    keyEdge = Color(0x1AFFFFFF),
    chrome = Color(0x00000000),
    chromeInk = Color(0xFFC7CED6),
    chromeActive = Color(0xFFC7CED6),
)

val LocalCalcColors = staticCompositionLocalOf { DarkCalc }

/** 深度自适应主题：跟随系统深/浅色，两套配色都保留橙 SHIFT / 紫 ALPHA */
@Composable
fun CalcTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val calc = if (darkTheme) DarkCalc else LightCalc
    val scheme = if (darkTheme) {
        darkColorScheme(
            primary = ShiftOrange,
            onPrimary = ShiftOrangeInk,
            secondary = AlphaPurple,
            onSecondary = AlphaPurpleInk,
            tertiary = AccentOrange,
            onTertiary = AccentOrangeInk,
            background = calc.body,
            onBackground = calc.bodyInk,
            surface = calc.body,
            onSurface = calc.bodyInk,
            surfaceVariant = calc.keyNeutral,
            onSurfaceVariant = calc.keyNeutralInk,
            error = calc.lcdError,
        )
    } else {
        lightColorScheme(
            primary = ShiftOrange,
            onPrimary = ShiftOrangeInk,
            secondary = AlphaPurpleDeep,
            onSecondary = Color.White,
            tertiary = AccentOrange,
            onTertiary = AccentOrangeInk,
            background = calc.body,
            onBackground = calc.bodyInk,
            surface = calc.body,
            onSurface = calc.bodyInk,
            surfaceVariant = calc.keyNeutral,
            onSurfaceVariant = calc.keyNeutralInk,
            error = calc.lcdError,
        )
    }
    // edge-to-edge 下状态栏 / 导航栏图标是深是浅必须自己说了算：
    // 跟随「应用内」主题（可能是手动覆盖，未必等于系统深色），浅色底用深色图标，反之用浅色图标。
    val view = LocalView.current
    SideEffect {
        val activity = view.context.findActivity() ?: return@SideEffect
        val controller = WindowCompat.getInsetsController(activity.window, view)
        controller.isAppearanceLightStatusBars = !darkTheme
        controller.isAppearanceLightNavigationBars = !darkTheme
    }
    CompositionLocalProvider(LocalCalcColors provides calc) {
        MaterialTheme(colorScheme = scheme, typography = Typography(), content = content)
    }
}

/** 从 Compose 的 Context 里把宿主 Activity 找出来（可能是被包装过的 Context）。 */
private fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c != null) {
        if (c is Activity) return c
        c = (c as? ContextWrapper)?.baseContext
    }
    return null
}
