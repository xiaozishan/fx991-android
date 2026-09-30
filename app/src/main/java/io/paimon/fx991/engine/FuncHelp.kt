package io.paimon.fx991.engine

// ---------------------------------------------------------------------------
// 批次 C：函数帮助 FUNC HELP（函数目录 + 语法说明；纯 Kotlin，可 JVM 回归）
//
// 每条都带一个「插入表达式」的文本（点一条写进计算式）与一个可求值的样例，
// 回归测试用样例确认语法键与引擎解析器一致。
// ---------------------------------------------------------------------------

/** 一条函数帮助 */
class FuncHelpEntry(
    /** 短名（唯一） */
    val name: String,
    val category: String,
    /** 语法写法，如 logb(底, 真数) */
    val syntax: String,
    /** 中文说明 */
    val desc: String,
    /** 点一条插入到表达式的文本 */
    val insert: String,
    /** 可求值样例（用于校验语法键完整性） */
    val sample: String,
)

object FuncHelp {

    const val CAT_TRIG = "三角函数"
    const val CAT_INVTRIG = "反三角函数"
    const val CAT_HYPER = "双曲与反双曲"
    const val CAT_LOG = "对数与指数"
    const val CAT_ROOT = "根式与幂"
    const val CAT_COMB = "排列组合"
    const val CAT_CONST = "常量与寄存器"

    @JvmField
    val ALL: List<FuncHelpEntry> = listOf(
        // ---- 三角函数 ----
        FuncHelpEntry("sin", CAT_TRIG, "sin(θ)", "正弦；θ 按当前角度制（DEG/RAD/GRAD）", "sin(", "sin(30)"),
        FuncHelpEntry("cos", CAT_TRIG, "cos(θ)", "余弦；θ 按当前角度制", "cos(", "cos(60)"),
        FuncHelpEntry("tan", CAT_TRIG, "tan(θ)", "正切；θ 按当前角度制", "tan(", "tan(45)"),

        // ---- 反三角 ----
        FuncHelpEntry("sin⁻¹", CAT_INVTRIG, "sin⁻¹(x)", "反正弦，定义域 [−1, 1]，按当前角度制输出", "sin\u207B\u00B9(", "sin\u207B\u00B9(0.5)"),
        FuncHelpEntry("cos⁻¹", CAT_INVTRIG, "cos⁻¹(x)", "反余弦，定义域 [−1, 1]", "cos\u207B\u00B9(", "cos\u207B\u00B9(0.5)"),
        FuncHelpEntry("tan⁻¹", CAT_INVTRIG, "tan⁻¹(x)", "反正切", "tan\u207B\u00B9(", "tan\u207B\u00B9(1)"),

        // ---- 双曲与反双曲 ----
        FuncHelpEntry("sinh", CAT_HYPER, "sinh(x)", "双曲正弦", "sinh(", "sinh(1)"),
        FuncHelpEntry("cosh", CAT_HYPER, "cosh(x)", "双曲余弦", "cosh(", "cosh(1)"),
        FuncHelpEntry("tanh", CAT_HYPER, "tanh(x)", "双曲正切", "tanh(", "tanh(1)"),
        FuncHelpEntry("sinh⁻¹", CAT_HYPER, "sinh⁻¹(x)", "反双曲正弦（asinh）", "asinh(", "asinh(1)"),
        FuncHelpEntry("cosh⁻¹", CAT_HYPER, "cosh⁻¹(x)", "反双曲余弦（acosh），定义域 x ≥ 1", "acosh(", "acosh(2)"),
        FuncHelpEntry("tanh⁻¹", CAT_HYPER, "tanh⁻¹(x)", "反双曲正切（atanh），定义域 |x| < 1", "atanh(", "atanh(0.5)"),

        // ---- 对数与指数 ----
        FuncHelpEntry("log", CAT_LOG, "log(x)", "常用对数（底 10）", "log(", "log(100)"),
        FuncHelpEntry("ln", CAT_LOG, "ln(x)", "自然对数（底 e）", "ln(", "ln(1)"),
        FuncHelpEntry("logb", CAT_LOG, "logb(底, 真数)", "任意底对数；底/真数均为整数且结果为整数时保持精确", "logb(", "logb(2, 8)"),
        FuncHelpEntry("10ˣ", CAT_LOG, "10^(x)", "10 的 x 次幂", "10^", "10^3"),
        FuncHelpEntry("eˣ", CAT_LOG, "e^(x)", "e 的 x 次幂（exp）", "exp(", "exp(0)"),

        // ---- 根式与幂 ----
        FuncHelpEntry("√", CAT_ROOT, "√(x)", "平方根；能开尽时保持精确分数", "\u221A(", "\u221A(9)"),
        FuncHelpEntry("³√", CAT_ROOT, "³√(x)", "立方根（cbrt），负数照算", "cbrt(", "cbrt(27)"),
        FuncHelpEntry("ˣ√y", CAT_ROOT, "root(次数, 被开方数)", "任意次根；开得尽保持精确", "root(", "root(3, 27)"),
        FuncHelpEntry("x²", CAT_ROOT, "x²", "平方", "\u00B2", "3\u00B2"),
        FuncHelpEntry("x³", CAT_ROOT, "x³", "立方", "\u00B3", "2\u00B3"),
        FuncHelpEntry("xʸ", CAT_ROOT, "x^(y)", "幂运算（右结合：2^3^2 = 512）", "^", "2^3"),
        FuncHelpEntry("x⁻¹", CAT_ROOT, "x⁻¹", "倒数", "\u207B\u00B9(", "2\u207B\u00B9"),
        FuncHelpEntry("|x|", CAT_ROOT, "abs(x)", "绝对值；精确轨保持精确", "abs(", "abs(\u2212(3))"),

        // ---- 排列组合 ----
        FuncHelpEntry("nPr", CAT_COMB, "npr(n, r)", "排列数；大数走 BigInteger 不溢出", "npr(", "npr(5, 2)"),
        FuncHelpEntry("nCr", CAT_COMB, "ncr(n, r)", "组合数；≤128bit 时精确", "ncr(", "ncr(5, 2)"),
        FuncHelpEntry("!", CAT_COMB, "x!", "阶乘（非负整数）", "!", "5!"),
        FuncHelpEntry("%", CAT_COMB, "x%", "百分号；A + B% = A + A·B/100", "%", "200+10%"),

        // ---- 常量与寄存器 ----
        FuncHelpEntry("π", CAT_CONST, "π", "圆周率", "\u03C0", "\u03C0"),
        FuncHelpEntry("e", CAT_CONST, "e", "自然常数", "e", "e"),
        FuncHelpEntry("Ans", CAT_CONST, "Ans", "上一次计算结果", "Ans", "Ans"),
        FuncHelpEntry("PreAns", CAT_CONST, "PreAns", "上上次计算结果", "PreAns", "PreAns"),
        FuncHelpEntry("M", CAT_CONST, "M", "独立存储器（M+ / M− / MRC）", "M", "M"),
        FuncHelpEntry("变量 A–F", CAT_CONST, "A .. F", "STO 存入的变量，可在表达式里直接引用", "A", "A+B"),
        FuncHelpEntry("∠", CAT_CONST, "r∠θ", "极坐标输入，如 2∠60 → 直角坐标（在复数模式 / 计算界面使用）", "\u2220", "2"),
        FuncHelpEntry("Σ", CAT_CONST, "Σ(f(x), x, a, b)", "求和（数值功能对话框）", "\u03A3", "1"),
    )

    /** 按类别分组（保持定义顺序） */
    @JvmStatic
    fun byCategory(): List<Pair<String, List<FuncHelpEntry>>> {
        val order = ArrayList<String>()
        val map = LinkedHashMap<String, MutableList<FuncHelpEntry>>()
        for (e in ALL) {
            if (!map.containsKey(e.category)) {
                map[e.category] = ArrayList()
                order.add(e.category)
            }
            map[e.category]!!.add(e)
        }
        return order.map { it to (map[it]!!.toList()) }
    }

    @JvmField
    val COUNT: Int = ALL.size
}
