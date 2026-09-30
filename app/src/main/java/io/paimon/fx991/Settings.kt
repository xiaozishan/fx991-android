package io.paimon.fx991

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.paimon.fx991.engine.NumberNotation

// ---------------------------------------------------------------------------
// 应用设置（设置面板：主题 / 角度制 / 显示精度 / 分数显示 / 按键震动）
// ---------------------------------------------------------------------------

/** 主题：跟随系统 / 浅色 / 深色 */
enum class AppTheme(val title: String) {
    SYSTEM("跟随系统"),
    LIGHT("浅色"),
    DARK("深色"),
}

/** 显示精度：有效数字 / 固定小数位 */
enum class PrecisionMode(val title: String) {
    SIGFIG("有效数字"),
    DECIMALS("小数位数"),
}

data class CalculatorSettings(
    val theme: AppTheme = AppTheme.SYSTEM,
    val precision: PrecisionMode = PrecisionMode.SIGFIG,
    val sigDigits: Int = 10,
    val decimals: Int = 3,
    val vibration: Boolean = true,
    // ---- 批次 B：数字显示模式（普通 / 科学记数 / 工程记数），与 ENG 键联动 ----
    val notation: NumberNotation = NumberNotation.NORM,
)

// ---------------------------------------------------------------------------
// 批次 A 数值功能：对话框种类
// ---------------------------------------------------------------------------

enum class FuncKind(val title: String) {
    INTEGRAL("定积分 ∫dx"),
    DERIV("数值导数 d/dx"),
    SUMMATION("求和 Σ"),
    SOLVE("方程求根 SOLVE"),
    LIMIT("极限 Limit"),
    CALC("代入求值 CALC"),
    DMS("度分秒 °′″"),
    HYPER("双曲函数 hyp"),

    // ---- 批次 B ----
    POL("直角 → 极坐标 Pol"),
    REC("极坐标 → 直角 Rec"),
    RANINT("整数区间随机 RanInt"),
}

/** 对话框里要填的字段标题 */
fun fieldLabels(kind: FuncKind): List<String> = when (kind) {
    FuncKind.INTEGRAL -> listOf("f(x)", "下限 a", "上限 b", "容差")
    FuncKind.DERIV -> listOf("f(x)", "x 值")
    FuncKind.SUMMATION -> listOf("f(x)", "下界 a", "上界 b")
    FuncKind.SOLVE -> listOf("f(x) = 0", "初值 x0")
    FuncKind.LIMIT -> listOf("f(x)", "x →")
    FuncKind.CALC -> listOf("表达式", "x =", "y =")
    FuncKind.DMS -> listOf("度", "分", "秒")
    FuncKind.HYPER -> emptyList()
    FuncKind.POL -> listOf("x", "y")
    FuncKind.REC -> listOf("r", "θ")
    FuncKind.RANINT -> listOf("下界", "上界", "次数")
}

/**
 * 数值功能对话框的一次输入会话（字段用 Compose 状态，便于 TextField 双向绑定）。
 */
class FuncDialog(val kind: FuncKind) {
    var f by mutableStateOf("")
    var a by mutableStateOf("")
    var b by mutableStateOf("")
    var c by mutableStateOf("")
    var result by mutableStateOf("")
    var error by mutableStateOf("")

    val fields: List<String> get() = fieldLabels(kind)
}

/** 打开对话框时的初始值（从当前表达式里带一点上下文） */
fun newFuncDialog(kind: FuncKind, seed: String): FuncDialog = FuncDialog(kind).apply {
    val usable = if ('x' in seed) seed else ""
    when (kind) {
        FuncKind.INTEGRAL -> {
            f = usable
            a = "0"
            b = "1"
            c = "1e-10"
        }
        FuncKind.DERIV -> {
            f = usable
            a = "1"
        }
        FuncKind.SUMMATION -> {
            f = usable
            a = "1"
            b = "10"
        }
        FuncKind.SOLVE -> {
            f = usable
            a = "1"
        }
        FuncKind.LIMIT -> {
            f = usable
            a = "0"
        }
        FuncKind.CALC -> {
            f = seed
            a = ""
            b = ""
        }
        FuncKind.DMS -> {
            a = ""
            b = ""
            c = ""
        }
        FuncKind.HYPER -> Unit
        FuncKind.POL -> {
            a = ""
            b = ""
        }
        FuncKind.REC -> {
            a = "1"
            b = "0"
        }
        FuncKind.RANINT -> {
            a = "1"
            b = "6"
            c = "1"
        }
    }
}

// ---------------------------------------------------------------------------
// 覆盖层（菜单 / 设置 / 更多 / 关于 / 拍照说明 / 历史 / 数值功能对话框）
// ---------------------------------------------------------------------------

enum class Overlay {
    NONE, MODE, SETTINGS, MORE, PRO, HISTORY, FUNC, HELP,

    // ---- 批次 B ----
    /** STO 变量存入 / 插入 */
    STO,
    /** 科学常数表 */
    CONST,
    /** 单位换算 */
    CONV,
    /** SI 前缀换算 */
    SI,
    // ---- 批次 D ----
    /** CLR ALL 确认对话框 */
    CLRCONFIRM,

    // ---- 批次 E ----
    //（原 PHOTO 覆盖层已删：拍照键现在直达 Screen.PHOTO_SOLVE 真界面）
}

/** MODE 菜单条目（批次 D 后全部模式都有真入口） */
class ModeEntry(val title: String, val screen: Screen?)
