package io.paimon.fx991.engine

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.cosh
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sinh
import kotlin.math.sqrt

// ---------------------------------------------------------------------------
// 批次 G：复变函数（纯 Kotlin，可 JVM 回归）
//
// 在批次 C 的 CMPLX 上扩展，不推翻重写：
//   1. 复函数求值：exp / ln / 幂 / 三角 / 双曲 / 开方（主值分支，跨分支切割时给提示）
//   2. 留数 Res(f, z0)：用小圆上的 Laurent 系数数值公式（梯形谱方法），
//      自动判定极点阶数（1..8），可去奇点明说「不是极点」，本性奇点拒绝
//   3. 留数定理围道积分：cint(f, p1, p2, …) = 2πi·ΣRes(f, pk)
//
// 复变表达式以 z 为自变量，支持 i（虚数单位）与全部实函数记号的复数推广；
// 三角 / 双曲一律弧度（复数辐角本无角度制）。
// ---------------------------------------------------------------------------

/** 轻量复数（Double 双精度；展示层再转回双轨 ComplexNum） */
class Cx(val re: Double, val im: Double) {

    operator fun plus(o: Cx): Cx = Cx(re + o.re, im + o.im)
    operator fun minus(o: Cx): Cx = Cx(re - o.re, im - o.im)
    operator fun unaryMinus(): Cx = Cx(-re, -im)

    operator fun times(o: Cx): Cx = Cx(re * o.re - im * o.im, re * o.im + im * o.re)

    operator fun div(o: Cx): Cx {
        val d = o.re * o.re + o.im * o.im
        if (d == 0.0) throw CalcMathError("数学错误")
        return Cx((re * o.re + im * o.im) / d, (im * o.re - re * o.im) / d)
    }

    fun conj(): Cx = Cx(re, -im)
    fun modulus(): Double = hypot(re, im)
    fun arg(): Double = atan2(im, re)
    fun finite(): Boolean = re.isFinite() && im.isFinite()
    fun isRealish(eps: Double = 1e-12): Boolean = abs(im) <= eps

    override fun toString(): String = "($re${if (im < 0) "" else "+"}${im}i)"

    companion object {
        @JvmField
        val ZERO = Cx(0.0, 0.0)
        @JvmField
        val ONE = Cx(1.0, 0.0)
        @JvmField
        val I = Cx(0.0, 1.0)
        @JvmStatic
        fun of(re: Double, im: Double = 0.0): Cx = Cx(re, im)
    }
}

/** 留数结果：value 为留数，order 为极点阶数（0 = 可去奇点 / 解析点） */
class ResidueResult(val value: Cx, val order: Int, val note: String)

object ComplexFunc {

    /** 主值分支提示（ln / 开方 / 一般幂会跨过分支切割） */
    const val BRANCH_NOTE = "复函数取主值分支（辐角 ∈ (−π, π]，负实轴为分支切割）"

    /** 主行可用的复函数（实参数时仍走原实数轨，不受影响） */
    @JvmField
    val FN1_NAMES: Set<String> = setOf(
        "sin", "cos", "tan", "sinh", "cosh", "tanh", "exp", "ln", "sqrt", "conj", "abs",
    )

    /** 使用主值分支的函数（触发分支提示） */
    private val BRANCHY = setOf("ln", "sqrt")

    // -----------------------------------------------------------------------
    // 复数初等函数
    // -----------------------------------------------------------------------

    fun fn1(name: String, z: Cx): Cx {
        val r = when (name) {
            "exp" -> {
                val e = exp(z.re)
                Cx(e * cos(z.im), e * sin(z.im))
            }
            "ln" -> {
                if (z.re == 0.0 && z.im == 0.0) throw CalcMathError("数学错误")
                Cx(ln(z.modulus()), z.arg())
            }
            "sin" -> Cx(sin(z.re) * cosh(z.im), cos(z.re) * sinh(z.im))
            "cos" -> Cx(cos(z.re) * cosh(z.im), -sin(z.re) * sinh(z.im))
            "tan" -> fn1("sin", z) / fn1("cos", z)
            "sinh" -> Cx(sinh(z.re) * cos(z.im), cosh(z.re) * sin(z.im))
            "cosh" -> Cx(cosh(z.re) * cos(z.im), sinh(z.re) * sin(z.im))
            "tanh" -> fn1("sinh", z) / fn1("cosh", z)
            "sqrt" -> sqrtC(z)
            "conj" -> z.conj()
            "abs" -> Cx(z.modulus(), 0.0)
            else -> throw CalcMathError("复数暂不支持函数 $name")
        }
        if (!r.finite()) throw CalcMathError("数学错误")
        return r
    }

    /** 主值平方根：实部 ≥ 0，负实轴上映到正虚轴 */
    fun sqrtC(z: Cx): Cx {
        val r = z.modulus()
        if (r == 0.0) return Cx.ZERO
        val rePart = sqrt((r + z.re) / 2.0)
        val imPart = sqrt(maxOf(0.0, (r - z.re) / 2.0)) * if (z.im < 0.0) -1.0 else 1.0
        return Cx(rePart, imPart)
    }

    /** 主值幂 a^b = exp(b·Ln a)；整数次幂走连乘（更准） */
    fun powC(a: Cx, b: Cx): Cx {
        if (a.re == 0.0 && a.im == 0.0) {
            if (b.re == 0.0 && b.im == 0.0) return Cx.ONE
            if (b.im == 0.0 && b.re > 0.0) return Cx.ZERO
            throw CalcMathError("数学错误")
        }
        if (b.isRealish() && b.re == floor(b.re) && abs(b.re) <= 64.0) {
            return powIntC(a, b.re.toInt())
        }
        return fn1("exp", b * fn1("ln", a))
    }

    private fun powIntC(a: Cx, k: Int): Cx {
        if (k == 0) return Cx.ONE
        var base = if (k < 0) Cx.ONE / a else a
        var n = abs(k)
        var acc = Cx.ONE
        while (n > 0) {
            if (n and 1 == 1) acc = acc * base
            base = base * base
            n = n shr 1
        }
        return acc
    }

    // -----------------------------------------------------------------------
    // 复变表达式求值（z 为自变量；复用同一词法 / 语法）
    // -----------------------------------------------------------------------

    /**
     * 求值 f(z)。表达式里：`z` 是自变量，`i` 是虚数单位，
     * 其余记号（+ − × ÷ ^ √ 函数…）与主行一致，但按复数语义计算（弧度制）。
     */
    @JvmStatic
    fun evalExpr(expr: String, z: Cx): Cx {
        val ast = Parser(Lexer(expr).lex()).parse()
        val r = evalNode(ast, z)
        if (!r.finite()) throw CalcMathError("数学错误")
        return r
    }

    internal fun evalNode(n: Node, z: Cx): Cx = when (n) {
        is Node.Num -> Cx(n.v, 0.0)
        is Node.IRef -> Cx.I
        is Node.ZRef -> z
        is Node.XRef, is Node.YRef ->
            throw CalcMathError("复变表达式请用 z 作自变量")
        is Node.Neg -> -evalNode(n.a, z)
        is Node.Add -> evalNode(n.a, z) + evalNode(n.b, z)
        is Node.Sub -> evalNode(n.a, z) - evalNode(n.b, z)
        is Node.Mul -> evalNode(n.a, z) * evalNode(n.b, z)
        is Node.Div -> evalNode(n.a, z) / evalNode(n.b, z)
        is Node.Pow -> powC(evalNode(n.a, z), evalNode(n.b, z))
        is Node.Recip -> Cx.ONE / evalNode(n.a, z)
        is Node.Sqrt -> sqrtC(evalNode(n.a, z))
        is Node.Pct -> evalNode(n.a, z) / Cx(100.0, 0.0)
        is Node.Fact -> {
            val v = evalNode(n.a, z)
            if (!v.isRealish()) throw CalcMathError("阶乘只支持实数")
            Cx(MathOps.factorial(v.re), 0.0)
        }
        is Node.Fn -> {
            val v = evalNode(n.a, z)
            if (v.isRealish()) {
                // 实参数保持实数语义（弧度制，与复变一致）
                Cx(MathOps.fn(AngleMode.RAD, n.name, v.re), 0.0)
            } else {
                fn1(n.name, v)
            }
        }
        is Node.Polar -> {
            val r = evalNode(n.r, z)
            val t = evalNode(n.t, z)
            if (!r.isRealish() || !t.isRealish()) throw CalcMathError("∠ 的两边需为实数")
            Cx(r.re * cos(t.re), r.re * sin(t.re))
        }
        is Node.UFn -> {
            if (n.deriv > 0) throw CalcMathError("复变表达式暂不支持自定义函数求导")
            val args = n.args.map {
                val c = evalNode(it, z)
                if (!c.isRealish()) throw CalcMathError("自定义函数参数需为实数")
                Value.of(c.re)
            }
            Cx(UserFunctions.call(n.name, args, 0, AngleMode.RAD).toDouble(), 0.0)
        }
        else -> throw CalcMathError("复变表达式不支持该记号")
    }

    // -----------------------------------------------------------------------
    // 留数：小圆 Laurent 系数（梯形谱方法）
    //
    //   a_n = (1/(2π rⁿ)) ∫₀^{2π} f(z0 + r·e^{it})·e^{−int} dt
    // 梯形公式对周期解析函数谱收敛；a₋₁ 即留数，无需先知道阶数。
    // 用两个半径交叉验证；由 a₋₁…a₋₈ 的第一个显著项定极点阶数。
    // -----------------------------------------------------------------------

    private const val QUAD_N = 96

    @JvmStatic
    internal fun residue(f: Node, z0: Cx): ResidueResult {
        val scale = 1.0 + z0.modulus()
        val r1 = 0.05 * scale
        val r2 = 0.025 * scale

        fun coeff(r: Double, n: Int): Cx {
            var sum = Cx.ZERO
            val rn = Math.pow(r, n.toDouble())
            for (k in 0 until QUAD_N) {
                val t = 2.0 * Math.PI * k / QUAD_N
                val zz = z0 + Cx(r * cos(t), r * sin(t))
                val fz = try {
                    evalNode(f, zz)
                } catch (e: Exception) {
                    throw NumericError("f 在 z0 附近数值不稳定（可能有分支切割穿过小圆）")
                }
                if (!fz.finite()) throw NumericError("f 在 z0 附近数值不稳定")
                sum += fz * Cx(cos(n * t), -sin(n * t))
            }
            return sum / Cx(QUAD_N * rn, 0.0)
        }

        val res1 = coeff(r1, -1)
        val res2 = coeff(r2, -1)
        val resMag = maxOf(1.0, res1.modulus())
        val agree = (res1 - res2).modulus() <= 1e-5 * resMag

        // 极点阶数：a₋₈ … a₋₁ 中最后一个显著的负幂
        var order = 0
        for (k in 8 downTo 1) {
            val a = coeff(r1, -k)
            if (a.modulus() > 1e-7 * resMag) {
                order = k
                break
            }
        }
        val higher = coeff(r1, -9).modulus()
        if (order >= 8 && higher > 1e-7 * resMag) {
            throw NumericError("不能判定为有限阶极点（可能是本性奇点），不敢算")
        }

        // 可去奇点 / 解析点：留数就是 0，明说「不是极点」
        if (order == 0) {
            return ResidueResult(Cx.ZERO, 0, "z0 不是极点（可去奇点或解析点），留数为 0")
        }
        if (!agree) {
            return ResidueResult(res1, order, "两个半径结果不一致，数值置信度低（附近可能还有其他奇点）")
        }
        val ordText = when (order) {
            1 -> "一阶（简单）极点"
            2 -> "二阶极点"
            3 -> "三阶极点"
            else -> "${order} 阶极点"
        }
        return ResidueResult(res1, order, ordText)
    }

    /** 便捷入口：字符串表达式 + 直角坐标 z0（供界面 / 回归直接调） */
    @JvmStatic
    fun residueOf(expr: String, zRe: Double, zIm: Double): ResidueResult {
        val ast = Parser(Lexer(expr).lex()).parse()
        return residue(ast, Cx(zRe, zIm))
    }

    /** 便捷入口：字符串表达式 + 极点列表（供界面直接调） */
    @JvmStatic
    fun contourOf(expr: String, poles: List<Cx>): Pair<Cx, String> =
        contourByResidues(Parser(Lexer(expr).lex()).parse(), poles)

    // -----------------------------------------------------------------------
    // 留数定理围道积分：∮ f dz = 2πi·ΣRes(f, pk)
    // -----------------------------------------------------------------------

    /** @return 积分值 + 每个极点的留数明细（展示用） */
    internal fun contourByResidues(f: Node, poles: List<Cx>): Pair<Cx, String> {
        if (poles.isEmpty()) throw NumericError("至少列出一个围道内的极点")
        var sum = Cx.ZERO
        val detail = StringBuilder()
        for ((idx, p) in poles.withIndex()) {
            val r = residue(f, p)
            sum += r.value
            if (idx > 0) detail.append("，")
            detail.append("Res(${fmtC(p)})=${fmtC(r.value)}")
        }
        val value = Cx(-2.0 * Math.PI * sum.im, 2.0 * Math.PI * sum.re)
        val note = "留数定理：∮f dz = 2πi·ΣRes（围道须为正向简单闭曲线，且只含所列极点）\n$detail"
        return value to note
    }

    private fun fmtC(z: Cx): String {
        val re = CalcEngine.format(z.re, 10, null)
        if (abs(z.im) <= 1e-12) return re
        val im = CalcEngine.format(abs(z.im), 10, null)
        return if (z.im < 0.0) "$re−${im}i" else "$re+${im}i"
    }

    /** 双轨 ComplexNum → Cx */
    fun toCx(c: ComplexNum): Cx = Cx(c.re.toDouble(), c.im.toDouble())

    /** Cx → 双轨 ComplexNum（浮点轨） */
    fun toComplex(z: Cx): ComplexNum = ComplexNum(Value.of(clean(z.re)), Value.of(clean(z.im)))

    private fun clean(v: Double): Double = if (abs(v) < 1e-12) 0.0 else v

    /** fn1 的双轨包装（UnifiedEvaluator 用） */
    fun fn1Value(name: String, c: ComplexNum): ComplexNum = toComplex(fn1(name, toCx(c)))

    /** 该函数是否走主值分支（界面提示用） */
    @JvmStatic
    fun isBranchy(name: String): Boolean = name in BRANCHY
}
