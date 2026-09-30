package io.paimon.fx991.ui

import io.paimon.fx991.FuncKind
import io.paimon.fx991.ModeEntry
import io.paimon.fx991.Screen
import io.paimon.fx991.engine.AngleMode
import io.paimon.fx991.engine.label

/** 按键动作 */
sealed interface KeyAction {
    data class Insert(val text: String) : KeyAction
    data object Ac : KeyAction
    data object Del : KeyAction
    data object Equals : KeyAction
    data object ToggleAngle : KeyAction
    data object MPlus : KeyAction
    data object MMinus : KeyAction
    data object Mrc : KeyAction
    data object HistUp : KeyAction
    data object HistDown : KeyAction
    data object SignToggle : KeyAction
    data object Fraction : KeyAction
    data object Sd : KeyAction
    /** 分数显示格式循环：假分数 ⇄ 带分数 ⇄ 小数（与 S⇔D 联动） */
    data object FracFormat : KeyAction
    data object Shift : KeyAction
    data object Alpha : KeyAction
    data object Mode : KeyAction
    data object PadUp : KeyAction
    data object PadDown : KeyAction
    data object PadLeft : KeyAction
    data object PadRight : KeyAction
    data object PadOk : KeyAction

    // ---- 批次 A：顶栏 6 项 + 数值功能入口 ----
    data object Menu : KeyAction
    data object Settings : KeyAction
    data object More : KeyAction
    data object Pro : KeyAction
    data object Photo : KeyAction
    /** 打开某个数值功能对话框（∫dx / d/dx / Σ / SOLVE / Limit / CALC / °′″ / hyp / Pol / Rec / RanInt） */
    data class OpenFunc(val kind: FuncKind) : KeyAction

    // ---- 批次 B ----
    /** STO：存入变量（A–F / x / y / M） */
    data object OpenSto : KeyAction
    /** CONST：科学常数表 */
    data object OpenConst : KeyAction
    /** CONV：单位换算 */
    data object OpenConv : KeyAction
    /** SI：SI 前缀换算 */
    data object OpenSi : KeyAction
    /** History：历史记录页（− 的 SHIFT 层） */
    data object OpenHistory : KeyAction
    /** CLR ALL：全清（先弹确认） */
    data object ClrAll : KeyAction
    /** ENG：工程记数法显示切换 */
    data object EngToggle : KeyAction
    /** Ran#：插入一个 0–1 随机数 */
    data object RandomInsert : KeyAction

    // ---- 批次 C：六大子系统入口（直接切到对应界面） ----
    data class GoScreen(val screen: Screen) : KeyAction
}

/** 手绘矢量图标（不引任何图片/图标资源） */
enum class KeyIcon { NONE, MENU, PRO, SIGMA, GEAR, PLUSMINUS, CAMERA, GALLERY, BACKSPACE }

/** 键帽分类（决定配色） */
enum class KeyKind { UTIL, PRO, MORE, DIGIT, FUNC, OP, EQUALS, DANGER, MEM, SHIFT, ALPHA, NAV }

/** 第二功能（SHIFT / ALPHA 层） */
data class SubKey(val label: String, val action: KeyAction)

data class Key(
    val label: String,
    val kind: KeyKind,
    val action: KeyAction,
    /** 橙色小字（SHIFT 层），印在键帽上方 */
    val shift: SubKey? = null,
    /** 紫色小字（ALPHA 层），印在键帽上方 */
    val alpha: SubKey? = null,
    /** 横向权重，方向键等宽键用 */
    val weight: Float = 1f,
    val icon: KeyIcon = KeyIcon.NONE,
    /** 主标签字号倍率 */
    val labelScale: Float = 1f,
    val subBelow: String? = null,
)

private fun ins(t: String) = KeyAction.Insert(t)

/** MODE 菜单：全部模式均已实现（批次 D 补齐了最后 4 个） */
fun modeEntries(): List<ModeEntry> = listOf(
    ModeEntry("计算", Screen.CALC),
    ModeEntry("微分方程", Screen.ODE),
    ModeEntry("方程（多项式 / 联立）", Screen.EQUATION),
    ModeEntry("矩阵", Screen.MATRIX),
    ModeEntry("向量", Screen.VECTOR),
    ModeEntry("统计与回归", Screen.STAT),
    ModeEntry("复数", Screen.CMPLX),
    ModeEntry("基数换算", Screen.BASEN),
    ModeEntry("函数表", Screen.TABLE),
    ModeEntry("概率分布", Screen.DISTR),
    ModeEntry("函数帮助", Screen.FUNC_HELP),
    ModeEntry("比例", Screen.RATIO),
)

/** 顶栏：菜单 · PRO · Σ · 齿轮 · ± · 相机 ｜ MORE · DEG */
fun utilityBar(mode: AngleMode): List<Key> = listOf(
    Key("", KeyKind.UTIL, KeyAction.Menu, icon = KeyIcon.MENU),
    Key("PRO", KeyKind.PRO, KeyAction.Pro, icon = KeyIcon.PRO),
    Key("", KeyKind.UTIL, KeyAction.OpenFunc(FuncKind.SUMMATION), icon = KeyIcon.SIGMA),
    Key("", KeyKind.UTIL, KeyAction.Settings, icon = KeyIcon.GEAR),
    Key("", KeyKind.UTIL, KeyAction.SignToggle, icon = KeyIcon.PLUSMINUS),
    Key("", KeyKind.UTIL, KeyAction.Photo, icon = KeyIcon.CAMERA),
    Key("MORE", KeyKind.MORE, KeyAction.More),
    Key(mode.label, KeyKind.UTIL, KeyAction.ToggleAngle),
)

/** 键盘主体：9 行，照参考图排布 */
fun keypadRows(): List<List<Key>> = listOf(

    // 第 2 排：SHIFT(橙) · ALPHA(紫) · 五向方向键 · MODE · 2nd
    listOf(
        Key("SHIFT", KeyKind.SHIFT, KeyAction.Shift, labelScale = 0.85f),
        Key("ALPHA", KeyKind.ALPHA, KeyAction.Alpha, labelScale = 0.85f),
        Key("", KeyKind.NAV, KeyAction.PadOk, weight = 1.9f, icon = KeyIcon.NONE),
        Key("MODE", KeyKind.FUNC, KeyAction.Mode, labelScale = 0.85f),
        // 2nd 与 SHIFT 同义：等价第二功能切换
        Key("2nd", KeyKind.FUNC, KeyAction.Shift, labelScale = 0.85f),
    ),

    // 第 3 排：CALC · ∫dx · x⁻¹ · logₓy
    listOf(
        Key("CALC", KeyKind.FUNC, KeyAction.OpenFunc(FuncKind.CALC),
            shift = SubKey("SOLVE", KeyAction.OpenFunc(FuncKind.SOLVE))),
        Key("∫dx", KeyKind.FUNC, KeyAction.OpenFunc(FuncKind.INTEGRAL),
            shift = SubKey("d/dx", KeyAction.OpenFunc(FuncKind.DERIV))),
        Key("x\u207B\u00B9", KeyKind.FUNC, ins("\u207B\u00B9("), shift = SubKey("x!", ins("!"))),
        Key("log\u2093y", KeyKind.FUNC, ins("logb("),
            shift = SubKey("Σ", KeyAction.OpenFunc(FuncKind.SUMMATION))),
    ),

    // 第 4 排：分数 · 根号 · 幂 · 对数
    listOf(
        Key("a/b", KeyKind.FUNC, KeyAction.Fraction,
            shift = SubKey("▸r", KeyAction.FracFormat)),
        Key("\u221Ax", KeyKind.FUNC, ins("\u221A("), shift = SubKey("\u00B3\u221Ax", ins("cbrt("))),
        Key("x\u00B2", KeyKind.FUNC, ins("\u00B2"), shift = SubKey("x\u00B3", ins("\u00B3"))),
        Key("x\u02B8", KeyKind.FUNC, ins("^"), shift = SubKey("\u02E3\u221Ay", ins("root("))),
        Key("log", KeyKind.FUNC, ins("log("), shift = SubKey("10\u02E3", ins("10^"))),
        Key("ln", KeyKind.FUNC, ins("ln("), shift = SubKey("e\u02E3", ins("exp("))),
    ),

    // 第 5 排：(-) · °'" · hyp · sin · cos · tan
    listOf(
        Key("(\u2212)", KeyKind.FUNC, KeyAction.SignToggle,
            shift = SubKey("∠", ins("\u2220"))),
        Key("\u00B0\u2032\u2033", KeyKind.FUNC, KeyAction.OpenFunc(FuncKind.DMS),
            shift = SubKey("FACT", ins("!"))),
        Key("hyp", KeyKind.FUNC, KeyAction.OpenFunc(FuncKind.HYPER),
            shift = SubKey("|x|", ins("abs(")),
            alpha = SubKey("c", ins("C"))),
        Key("sin", KeyKind.FUNC, ins("sin("), shift = SubKey("sin\u207B\u00B9", ins("sin\u207B\u00B9(")),
            alpha = SubKey("d", ins("D"))),
        Key("cos", KeyKind.FUNC, ins("cos("), shift = SubKey("cos\u207B\u00B9", ins("cos\u207B\u00B9(")),
            alpha = SubKey("e", ins("E"))),
        Key("tan", KeyKind.FUNC, ins("tan("), shift = SubKey("tan\u207B\u00B9", ins("tan\u207B\u00B9(")),
            alpha = SubKey("f", ins("F"))),
    ),

    // 第 6 排：RCL · ENG · ( · ) · S⇔D · M+
    listOf(
        Key("RCL", KeyKind.MEM, KeyAction.Mrc, shift = SubKey("STO", KeyAction.OpenSto)),
        Key("ENG", KeyKind.MEM, KeyAction.EngToggle, shift = SubKey("CONST", KeyAction.OpenConst)),
        Key("(", KeyKind.FUNC, ins("("), shift = SubKey("CONV", KeyAction.OpenConv)),
        Key(")", KeyKind.FUNC, ins(")"), shift = SubKey("SI", KeyAction.OpenSi)),
        Key("S\u21D4D", KeyKind.FUNC, KeyAction.Sd,
            shift = SubKey("Limit", KeyAction.OpenFunc(FuncKind.LIMIT))),
        Key("M+", KeyKind.MEM, KeyAction.MPlus, shift = SubKey("CLR ALL", KeyAction.ClrAll),
            alpha = SubKey("M\u2212", KeyAction.MMinus)),
    ),

    // 第 7 排：7 8 9 ⌫ AC
    listOf(
        Key("7", KeyKind.DIGIT, ins("7"), shift = SubKey("MATRIX", KeyAction.GoScreen(Screen.MATRIX))),
        Key("8", KeyKind.DIGIT, ins("8"), shift = SubKey("VECTOR", KeyAction.GoScreen(Screen.VECTOR))),
        Key("9", KeyKind.DIGIT, ins("9"), shift = SubKey("FUNC HELP", KeyAction.GoScreen(Screen.FUNC_HELP))),
        Key("", KeyKind.DANGER, KeyAction.Del, shift = SubKey("nPr", ins("npr(")),
            icon = KeyIcon.BACKSPACE),
        Key("AC", KeyKind.DANGER, KeyAction.Ac, labelScale = 0.9f,
            shift = SubKey("nCr", ins("ncr("))),
    ),

    // 第 8 排：4 5 6 × ÷
    listOf(
        Key("4", KeyKind.DIGIT, ins("4"), shift = SubKey("STAT", KeyAction.GoScreen(Screen.STAT))),
        Key("5", KeyKind.DIGIT, ins("5"), shift = SubKey("CMPLX", KeyAction.GoScreen(Screen.CMPLX))),
        Key("6", KeyKind.DIGIT, ins("6"), shift = SubKey("DISTR", KeyAction.GoScreen(Screen.DISTR))),
        Key("\u00D7", KeyKind.OP, ins("\u00D7"), shift = SubKey("Pol", KeyAction.OpenFunc(FuncKind.POL)),
            alpha = SubKey("\u00B7", ins("\u00B7"))),
        Key("\u00F7", KeyKind.OP, ins("\u00F7"), shift = SubKey("Rec", KeyAction.OpenFunc(FuncKind.REC))),
    ),

    // 第 9 排：1 2 3 + −
    listOf(
        Key("1", KeyKind.DIGIT, ins("1"), shift = SubKey("Ran#", KeyAction.RandomInsert)),
        Key("2", KeyKind.DIGIT, ins("2"), shift = SubKey("\u03C0", ins("\u03C0"))),
        Key("3", KeyKind.DIGIT, ins("3"), shift = SubKey("e", ins("e"))),
        Key("+", KeyKind.OP, ins("+"), shift = SubKey("PreAns", ins("PreAns"))),
        Key("\u2212", KeyKind.OP, ins("\u2212"), shift = SubKey("History", KeyAction.OpenHistory)),
    ),

    // 第 10 排：0 . Exp Ans =
    listOf(
        Key("0", KeyKind.DIGIT, ins("0")),
        Key(".", KeyKind.DIGIT, ins(".")),
        Key("Exp", KeyKind.FUNC, ins("\u00D710^"), labelScale = 0.8f,
            shift = SubKey("RanInt", KeyAction.OpenFunc(FuncKind.RANINT))),
        Key("Ans", KeyKind.MEM, ins("Ans"), labelScale = 0.8f,
            alpha = SubKey("x", ins("x")), shift = SubKey("y", ins("y"))),
        Key("=", KeyKind.EQUALS, KeyAction.Equals, alpha = SubKey("=", ins("="))),
    ),
)
