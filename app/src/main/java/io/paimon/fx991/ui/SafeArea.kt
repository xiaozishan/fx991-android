package io.paimon.fx991.ui

/**
 * 避让系统栏之后的布局策略 —— 纯 Kotlin，无 Compose / Android 依赖，可直接跑 JVM 回归
 * （见 `tools/InsetsTest.java`）。
 *
 * Compose 侧由 `Modifier.safeAreaPadding()` 按系统真实上报的 Insets 做 padding；
 * 这里负责「扣掉安全区之后，剩下的高度还摆不摆得下键盘」这类判断：
 *
 *  - 竖屏正常机型（高度富裕）→ 权重布局：顶栏固定，LCD 占 [LCD_WEIGHT]，键盘占其余；
 *  - 横屏 / 小屏（可用高度不足）→ 紧凑布局：LCD 与按键行都用最小高度，整体可滚动，
 *    避免键盘被压扁到点不动、或内容被挤出屏幕。
 */
object SafeArea {

    /** 键盘行数（与 `ui/Keys.kt` 的 `keypadRows()` 一致：顶栏之外共 9 行） */
    const val KEYPAD_ROWS = 9

    /** 顶栏（菜单 / PRO / Σ / 设置 / 拍照 / 更多 那一排）固定高度（dp） */
    const val UTILITY_BAR_DP = 46

    /** 紧凑布局下 LCD 的最小高度（dp） */
    const val MIN_LCD_DP = 150

    /** 紧凑布局下单行按键的最小高度（dp）—— 低于这个值按键就点不动了 */
    const val MIN_ROW_DP = 34

    /** 竖屏权重布局下 LCD 占的比例（其余给键盘） */
    const val LCD_WEIGHT = 0.30f

    /** 紧凑布局所需的最小总高度（dp）：顶栏 + LCD + 全部按键行 */
    @JvmStatic
    @JvmOverloads
    fun requiredHeightDp(
        rows: Int = KEYPAD_ROWS,
        lcdMinDp: Int = MIN_LCD_DP,
        rowMinDp: Int = MIN_ROW_DP,
        utilityBarDp: Int = UTILITY_BAR_DP,
    ): Int = utilityBarDp + lcdMinDp + rows * rowMinDp

    /**
     * 可用高度（已扣掉安全区）是否装不下固定行高布局。
     * true → 用「固定行高 + 整体可滚动」的紧凑布局；false → 用权重布局。
     */
    @JvmStatic
    @JvmOverloads
    fun needsScrollLayout(
        availableHeightDp: Int,
        rows: Int = KEYPAD_ROWS,
        lcdMinDp: Int = MIN_LCD_DP,
        rowMinDp: Int = MIN_ROW_DP,
        utilityBarDp: Int = UTILITY_BAR_DP,
    ): Boolean = availableHeightDp < requiredHeightDp(rows, lcdMinDp, rowMinDp, utilityBarDp)

    /** 键盘行高兜底：请求高度非法（<=0）时回退到最小值 */
    @JvmStatic
    fun rowHeightDp(availableRowDp: Int): Int =
        if (availableRowDp <= 0) MIN_ROW_DP else availableRowDp
}
