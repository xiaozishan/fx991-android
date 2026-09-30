import io.paimon.fx991.ui.SafeArea;

/**
 * 系统 Insets 避让的布局策略回归（纯 Kotlin 逻辑，无 Compose 依赖）。
 * 对应 ui/SafeArea.kt：决定「扣掉安全区之后的可用高度」够不够摆下固定行高的键盘，
 * 不够就切紧凑滚动布局，避免横屏 / 小屏下键盘被压扁或被系统栏挤掉。
 */
public class InsetsTest {

    static int pass = 0;
    static int fail = 0;

    static void check(String what, boolean ok) {
        if (ok) pass++; else fail++;
        System.out.println((ok ? "  PASS  " : "  FAIL  ") + what);
    }

    static void checkEq(String what, long expect, long actual) {
        boolean ok = expect == actual;
        if (ok) pass++; else fail++;
        System.out.println((ok ? "  PASS  " : "  FAIL  ") + what + "  expect=" + expect + " actual=" + actual);
    }

    public static void main(String[] args) {
        System.out.println("== Insets 避让布局策略 ==");

        // 常量自检
        checkEq("键盘行数 = 9", 9, SafeArea.KEYPAD_ROWS);
        checkEq("顶栏高度 = 46dp", 46, SafeArea.UTILITY_BAR_DP);
        checkEq("LCD 最小高度 = 150dp", 150, SafeArea.MIN_LCD_DP);
        checkEq("按键行最小高度 = 34dp", 34, SafeArea.MIN_ROW_DP);
        check("行最小高度不低于 24dp（可点）", SafeArea.MIN_ROW_DP >= 24);
        check("LCD 权重在 (0,1) 内", SafeArea.LCD_WEIGHT > 0f && SafeArea.LCD_WEIGHT < 1f);

        // 所需最小高度：顶栏 + LCD + 9 行
        checkEq("所需最小高度 = 46+150+9*34 = 502dp", 502, SafeArea.requiredHeightDp());
        checkEq("自定义 rows 参与计算", 46 + 150 + 3 * 34, SafeArea.requiredHeightDp(3, 150, 34, 46));

        // 边界：刚好等于所需高度 → 不滚动；差 1dp → 滚动
        int need = SafeArea.requiredHeightDp();
        check("高度刚好够 → 不滚动", !SafeArea.needsScrollLayout(need));
        check("高度差 1dp → 滚动", SafeArea.needsScrollLayout(need - 1));

        // 真实机型（扣掉安全区后的可用高度，dp）
        // 横屏小屏 / 折叠外屏：约 356dp（891x412 px @ ~2.5x）—— 装不下 502dp
        check("横屏小屏 356dp → 紧凑滚动布局", SafeArea.needsScrollLayout(356));
        // 挖孔竖屏：640dp 可用 —— 富裕
        check("常见竖屏 640dp → 权重布局", !SafeArea.needsScrollLayout(640));
        // 平板横屏：700dp 可用 —— 富裕（此时横向的安全区由 safeDrawing 负责，不在这里）
        check("平板横屏 700dp → 权重布局", !SafeArea.needsScrollLayout(700));
        // 极端矮窗口（分屏 / 自由窗口）：300dp
        check("分屏矮窗口 300dp → 紧凑滚动布局", SafeArea.needsScrollLayout(300));
        check("可用高度为 0 → 紧凑滚动布局", SafeArea.needsScrollLayout(0));

        // 行高兜底
        checkEq("行高非法(0) 回退到最小行高", SafeArea.MIN_ROW_DP, SafeArea.rowHeightDp(0));
        checkEq("行高非法(-8) 回退到最小行高", SafeArea.MIN_ROW_DP, SafeArea.rowHeightDp(-8));
        checkEq("行高合法则原样返回", 48, SafeArea.rowHeightDp(48));

        System.out.println();
        System.out.println("RESULT: pass=" + pass + "  fail=" + fail);
        if (fail > 0) System.exit(1);
    }
}
