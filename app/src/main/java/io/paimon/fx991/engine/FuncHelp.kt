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
    const val CAT_INT = "整数与取整"
    const val CAT_CONST = "常量与寄存器"
    const val CAT_UNIFIED = "矩阵 / 向量（主行可用）"
    const val CAT_STAT = "统计 / 分布（主行可调）"
    const val CAT_EQUATION = "方程求解（主行）"
    const val CAT_GEOGEBRA = "自定义函数（GeoGebra 式）"
    const val CAT_FOURIER = "傅里叶级数（主行）"
    const val CAT_CPLXF = "复变函数（主行）"

    @JvmField
    val ALL: List<FuncHelpEntry> = listOf(
        // ---- 三角函数 ----
        FuncHelpEntry("sin", CAT_TRIG, "sin(θ)", "正弦；θ 按当前角度制（DEG/RAD/GRAD）", "sin(", "sin(30)"),
        FuncHelpEntry("cos", CAT_TRIG, "cos(θ)", "余弦；θ 按当前角度制", "cos(", "cos(60)"),
        FuncHelpEntry("tan", CAT_TRIG, "tan(θ)", "正切；θ 按当前角度制", "tan(", "tan(45)"),
        FuncHelpEntry("cot", CAT_TRIG, "cot(θ)", "余切 = cos/sin（批次 K4，ENG 的 ALPHA 层）", "cot(", "cot(30)"),

        // ---- 反三角 ----
        FuncHelpEntry("sin⁻¹", CAT_INVTRIG, "sin⁻¹(x)", "反正弦，定义域 [−1, 1]，按当前角度制输出", "sin\u207B\u00B9(", "sin\u207B\u00B9(0.5)"),
        FuncHelpEntry("cos⁻¹", CAT_INVTRIG, "cos⁻¹(x)", "反余弦，定义域 [−1, 1]", "cos\u207B\u00B9(", "cos\u207B\u00B9(0.5)"),
        FuncHelpEntry("tan⁻¹", CAT_INVTRIG, "tan⁻¹(x)", "反正切", "tan\u207B\u00B9(", "tan\u207B\u00B9(1)"),
        FuncHelpEntry("cot⁻¹", CAT_INVTRIG, "acot(x)", "反余切，值域 (0, 180°)（批次 K4，( 的 ALPHA 层）", "acot(", "acot(1)"),

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

        // ---- 整数与取整（批次 K4）----
        FuncHelpEntry("GCD", CAT_INT, "gcd(a, b)", "最大公约数（整数，允许负数；× 的 ALPHA 层）", "gcd(", "gcd(12, 18)"),
        FuncHelpEntry("LCM", CAT_INT, "lcm(a, b)", "最小公倍数（÷ 的 ALPHA 层）", "lcm(", "lcm(4, 6)"),
        FuncHelpEntry("mod", CAT_INT, "mod(a, b)", "取模（向下取整式；√x 的 ALPHA 层）", "mod(", "mod(7, 3)"),
        FuncHelpEntry("Ceil", CAT_INT, "ceil(x)", "向上取整（+ 的 ALPHA 层）", "ceil(", "ceil(2.3)"),
        FuncHelpEntry("Floor", CAT_INT, "floor(x)", "向下取整（− 的 ALPHA 层）", "floor(", "floor(2.7)"),

        // ---- 常量与寄存器 ----
        FuncHelpEntry("π", CAT_CONST, "π", "圆周率", "\u03C0", "\u03C0"),
        FuncHelpEntry("e", CAT_CONST, "e", "自然常数", "e", "e"),
        FuncHelpEntry("Ans", CAT_CONST, "Ans", "上一次计算结果", "Ans", "Ans"),
        FuncHelpEntry("PreAns", CAT_CONST, "PreAns", "上上次计算结果", "PreAns", "PreAns"),
        FuncHelpEntry("M", CAT_CONST, "M", "独立存储器（M+ / M− / MRC）", "M", "M"),
        FuncHelpEntry("变量 A–F", CAT_CONST, "A .. F", "STO 存入的变量，可在表达式里直接引用", "A", "A+B"),
        FuncHelpEntry("∠", CAT_CONST, "r∠θ", "极坐标运算符（可多个）：9∠60+5∠6 就是两个复数相加", "\u2220", "2"),
        FuncHelpEntry("∞", CAT_CONST, "∞", "正无穷符号（9 的 ALPHA 层；1÷∞→0，单独求值报数学错误）", "\u221E", "1\u00F7\u221E"),
        FuncHelpEntry("Σ", CAT_CONST, "Σ(f(x), x, a, b)", "求和（数值功能对话框）", "\u03A3", "1"),

        // ---- 统一输入面：矩阵 / 向量（主行可直接用，无需切模式）----
        FuncHelpEntry("MatA", CAT_UNIFIED, "MatA .. MatD", "矩阵变量；在 MATRIX 界面定义数据，主行可直接参与运算", "MatA", "MatA"),
        FuncHelpEntry("VctA", CAT_UNIFIED, "VctA .. VctD", "向量变量；在 VECTOR 界面定义数据，主行可直接参与运算", "VctA", "VctA"),
        FuncHelpEntry("det", CAT_UNIFIED, "det(MatA)", "矩阵行列式（方阵）", "det(", "det(MatA)"),
        FuncHelpEntry("inv", CAT_UNIFIED, "inv(MatA)", "矩阵的逆（不可逆时报错）", "inv(", "inv(MatA)"),
        FuncHelpEntry("trn", CAT_UNIFIED, "trn(MatA)", "矩阵转置", "trn(", "trn(MatA)"),
        FuncHelpEntry("×", CAT_UNIFIED, "MatA×MatB", "矩阵乘法（左列数 = 右行数）/ 标量乘", "\u00D7", "MatA\u00D7MatB"),
        FuncHelpEntry("·", CAT_UNIFIED, "VctA·VctB", "向量点积（也可写 dot(A,B)）", "\u00B7", "VctA\u00B7VctB"),
        FuncHelpEntry("cross", CAT_UNIFIED, "cross(VctA, VctB)", "向量叉积", "cross(", "cross(VctA,VctB)"),
        FuncHelpEntry("dot", CAT_UNIFIED, "dot(VctA, VctB)", "向量点积", "dot(", "dot(VctA,VctB)"),
        FuncHelpEntry("abs", CAT_UNIFIED, "abs(VctA)", "向量取模（标量则取绝对值）", "abs(", "abs(1)"),

        // ---- 统一输入面：统计 / 分布（主行可调）----
        FuncHelpEntry("mean", CAT_STAT, "mean(数据…)", "均值（数据直接逗号分隔）", "mean(", "mean(1,2,3)"),
        FuncHelpEntry("sd", CAT_STAT, "sd(数据…)", "总体标准差 σ（分母 n）", "sd(", "sd(1,2,3)"),
        FuncHelpEntry("ssd", CAT_STAT, "ssd(数据…)", "样本标准差 s（分母 n−1）", "ssd(", "ssd(1,2,3)"),
        FuncHelpEntry("normpdf", CAT_STAT, "normpdf(x[, μ, σ])", "正态概率密度；缺省为标准正态", "normpdf(", "normpdf(0,1,1)"),
        FuncHelpEntry("normcdf", CAT_STAT, "normcdf(x[, μ, σ])", "正态累积分布 P(X < x)", "normcdf(", "normcdf(0,1,1)"),
        FuncHelpEntry("invnorm", CAT_STAT, "invnorm(p[, μ, σ])", "正态分位数（已知概率求 x）", "invnorm(", "invnorm(0.5,0,1)"),
        FuncHelpEntry("binompdf", CAT_STAT, "binompdf(n, k, p)", "二项分布 P(X = k)", "binompdf(", "binompdf(10,3,0.5)"),
        FuncHelpEntry("binomcdf", CAT_STAT, "binomcdf(n, k, p)", "二项分布 P(X ≤ k)", "binomcdf(", "binomcdf(10,3,0.5)"),
        FuncHelpEntry("poissonpdf", CAT_STAT, "poissonpdf(λ, k)", "泊松分布 P(X = k)", "poissonpdf(", "poissonpdf(2,1)"),
        FuncHelpEntry("poissoncdf", CAT_STAT, "poissoncdf(λ, k)", "泊松分布 P(X ≤ k)", "poissoncdf(", "poissoncdf(2,1)"),

        // ---- 主行求解（REFERENCE 第 7 条）----
        FuncHelpEntry("方程等号", CAT_EQUATION, "左式 = 右式", "含未知量时按 = 即求解（如 2x+3=7）", "=", "1+1"),
        FuncHelpEntry("方程组", CAT_EQUATION, "式1, 式2", "用逗号或分号分隔（如 2x+y=5, x-y=1）", ",", "1+1"),

        // ---- 批次 G：自定义函数（GeoGebra 式）----
        FuncHelpEntry("定义函数", CAT_GEOGEBRA, "f(x) = x^2", "主行定义函数；之后 f(3)、f(x)+1、嵌套调用直接可用；代数区可查看/改/删", "f(x)=x^2", "1+1"),
        FuncHelpEntry("重定义", CAT_GEOGEBRA, "f(x) := x^3", ":= 强制重定义已存在的函数；同名再写 = 会当方程求解", "f(x):=x^3", "1+1"),
        FuncHelpEntry("调用与求导", CAT_GEOGEBRA, "f(3) · f'(2)", "调用已定义函数；' 一阶导、'' 二阶导（仅单变量函数）", "f'(", "1+1"),

        // ---- 批次 G：傅里叶级数 ----
        FuncHelpEntry("fourier", CAT_FOURIER, "fourier(f(x), a, b, n)", "傅里叶级数展开：区间 [a,b] 上前 n 项 a0/an/bn 与部分和；奇偶自动识别；弧度制", "fourier(", "fourier(1, 0, 1, 1)"),

        // ---- 批次 G：复变函数 ----
        FuncHelpEntry("i", CAT_CPLXF, "i", "虚数单位（ENG 的 SHIFT 层）：exp(i*π) = −1；复函数取主值分支（辐角 ∈ (−π, π]）", "i", "i"),
        FuncHelpEntry("conj", CAT_CPLXF, "conj(z)", "复共轭（实数时不变）；模 abs(z)、辐角见 CMPLX 模式", "conj(", "conj(2)"),
        FuncHelpEntry("res", CAT_CPLXF, "res(f(z), z0)", "留数：自动判定极点阶数（1–8），可去奇点明说，本性奇点拒绝", "res(", "res(1/z, 0)"),
        FuncHelpEntry("cint", CAT_CPLXF, "cint(f(z), p1, …)", "留数定理围道积分 ∮f dz = 2πi·ΣRes（列出围道内全部极点）", "cint(", "cint(1/z, 0)"),
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
