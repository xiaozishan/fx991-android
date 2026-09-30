package io.paimon.fx991.engine

import java.math.BigInteger
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sqrt

// ---------------------------------------------------------------------------
// 统一输入面（REFERENCE 第 6 条）
//
// 主计算行一个入口：标量 / 复数 / 矩阵 / 向量 / 统计 / 分布都在这里求值，
// 不需要先切 CMPLX / MATRIX / VECTOR / STAT / DISTR 模式。
//
// 设计：**纯数值子表达式完全复用 ValueEvaluator**（双轨精确分数内核一字不改），
// 只有出现「新语法」（∠ / · / MatA–D / VctA–D / 可变参数函数）时才走联合求值分支，
// 因此原有标量语义不可能漂移。
// ---------------------------------------------------------------------------

/** 主计算行可承载的值：标量（含复数）/ 矩阵 / 向量 */
sealed interface CalcValue {

    /** 标量（虚部为 0 时就是普通实数，精确轨照旧有效） */
    class Scalar(val c: ComplexNum) : CalcValue

    class MatVal(val m: Matrix) : CalcValue

    class VecVal(val v: Vector3) : CalcValue
}

// ---------------------------------------------------------------------------

object Unified {

    /**
     * 主计算行求值。失败抛 [CalcSyntaxError] / [CalcMathError] / [NumericError]。
     *
     * @param lastExact 上一次的精确结果（Ans 的精确轨）
     * @param mats 矩阵变量（MatA–MatD），可为 null
     * @param vecs 向量变量（VctA–VctD），可为 null
     */
    @JvmStatic
    @JvmOverloads
    fun evaluate(
        expr: String,
        mode: AngleMode,
        ans: Double = 0.0,
        mem: Double = 0.0,
        lastExact: Value? = null,
        preAns: Double = 0.0,
        vars: Map<String, Double> = emptyMap(),
        mats: MatrixStore? = null,
        vecs: VectorStore? = null,
    ): CalcValue {
        val ast = Parser(Lexer(expr).lex()).parse()
        val ansValue = lastExact ?: Value.Floating(ans)
        val ev = UnifiedEvaluator(mode, ansValue, Value.Floating(mem), preAns, vars, mats, vecs)
        val r = ev.eval(ast)
        if (r is CalcValue.Scalar) {
            val d = r.c.re.toDouble()
            if (d.isNaN() || d.isInfinite()) throw CalcMathError("数学错误")
        }
        return r
    }

    /** 结果是否落在复数轨（虚部非零） */
    @JvmStatic
    fun isComplex(v: CalcValue): Boolean =
        v is CalcValue.Scalar && abs(v.c.im.toDouble()) > 1e-12

    /** 纯实数值（非标量 / 虚部非零时抛错） */
    @JvmStatic
    fun asValue(v: CalcValue): Value {
        if (v !is CalcValue.Scalar) throw CalcMathError("结果不是数值")
        if (abs(v.c.im.toDouble()) > 1e-12) throw CalcMathError("结果不是实数")
        return v.c.re
    }

    /** 复数直角坐标（非标量返回 null） */
    @JvmStatic
    fun asComplex(v: CalcValue): ComplexNum? = if (v is CalcValue.Scalar) v.c else null

    /** 主行显示文本 */
    @JvmStatic
    fun format(v: CalcValue): String = when (v) {
        is CalcValue.Scalar -> v.c.format()
        is CalcValue.MatVal -> v.m.format()
        is CalcValue.VecVal -> v.v.format()
    }
}

// ---------------------------------------------------------------------------

internal class UnifiedEvaluator(
    private val mode: AngleMode,
    private val ans: Value,
    private val mem: Value,
    private val preAns: Double,
    private val vars: Map<String, Double>,
    private val mats: MatrixStore?,
    private val vecs: VectorStore?,
) {
    private val scalar = ValueEvaluator(mode, ans, mem, preAns = preAns, vars = vars)
    private val zeroV: Value = Value.of(Rational.ZERO)
    private val oneV: Value = Value.of(Rational.ONE)
    private val zeroI: ComplexNum = ComplexNum(zeroV, zeroV)
    private val oneI: ComplexNum = ComplexNum(oneV, zeroV)

    fun eval(n: Node): CalcValue {
        // 纯标量子表达式：原样交给双轨内核，语义与 evaluateValue 完全一致
        if (!hasNew(n)) return CalcValue.Scalar(ComplexNum(scalar.eval(n), zeroV))
        return when (n) {
            is Node.Polar -> polar(one(eval(n.r)), one(eval(n.t)))
            is Node.Dot -> dotOp(eval(n.a), eval(n.b))
            is Node.MatRef -> {
                val m = mats?.get(n.name) ?: throw CalcMathError("未定义矩阵 Mat${n.name}")
                CalcValue.MatVal(m)
            }
            is Node.VecRef -> {
                val v = vecs?.get(n.name) ?: throw CalcMathError("未定义向量 Vct${n.name}")
                CalcValue.VecVal(v)
            }
            is Node.FnN -> fnN(n.name, n.args.map { eval(it) })
            is Node.Neg -> CalcValue.Scalar(negC(one(eval(n.a))))
            is Node.Add -> add(eval(n.a), eval(n.b))
            is Node.Sub -> sub(eval(n.a), eval(n.b))
            is Node.Mul -> mul(eval(n.a), eval(n.b))
            is Node.Div -> div(eval(n.a), eval(n.b))
            is Node.Pow -> CalcValue.Scalar(powC(one(eval(n.a)), one(eval(n.b))))
            is Node.Fact -> CalcValue.Scalar(factC(one(eval(n.a))))
            is Node.Pct -> CalcValue.Scalar(pctC(one(eval(n.a))))
            is Node.Recip -> CalcValue.Scalar(recipC(one(eval(n.a))))
            is Node.Sqrt -> CalcValue.Scalar(sqrtC(one(eval(n.a))))
            is Node.Fn -> fn(n.name, eval(n.a))
            is Node.Fn2 -> CalcValue.Scalar(
                ComplexNum(Value.of(MathOps.fn2(n.name, real(one(eval(n.a))), real(one(eval(n.b))))), zeroV)
            )
            else -> CalcValue.Scalar(ComplexNum(scalar.eval(n), zeroV))
        }
    }

    // ---- 帮助函数 ----

    private fun one(v: CalcValue): ComplexNum = when (v) {
        is CalcValue.Scalar -> v.c
        else -> throw CalcMathError("该位置需要数值")
    }

    /** 取实数（虚部非零或非标量则报错） */
    private fun real(c: ComplexNum): Double {
        if (abs(c.im.toDouble()) > 1e-9) throw CalcMathError("该位置需要实数")
        return c.re.toDouble()
    }

    private fun negC(c: ComplexNum): ComplexNum =
        ComplexNum(ExactMath.neg(c.re), ExactMath.neg(c.im))

    private fun add(a: CalcValue, b: CalcValue): CalcValue = when {
        a is CalcValue.Scalar && b is CalcValue.Scalar -> CalcValue.Scalar(a.c.plus(b.c))
        a is CalcValue.MatVal && b is CalcValue.MatVal -> CalcValue.MatVal(a.m.plus(b.m))
        a is CalcValue.VecVal && b is CalcValue.VecVal -> CalcValue.VecVal(a.v.plus(b.v))
        else -> throw CalcMathError("不支持该加法（类型不一致）")
    }

    private fun sub(a: CalcValue, b: CalcValue): CalcValue = when {
        a is CalcValue.Scalar && b is CalcValue.Scalar -> CalcValue.Scalar(a.c.minus(b.c))
        a is CalcValue.MatVal && b is CalcValue.MatVal -> CalcValue.MatVal(a.m.minus(b.m))
        a is CalcValue.VecVal && b is CalcValue.VecVal -> CalcValue.VecVal(a.v.minus(b.v))
        else -> throw CalcMathError("不支持该减法（类型不一致）")
    }

    private fun mul(a: CalcValue, b: CalcValue): CalcValue = when {
        a is CalcValue.Scalar && b is CalcValue.Scalar -> CalcValue.Scalar(a.c.times(b.c))
        a is CalcValue.MatVal && b is CalcValue.MatVal -> CalcValue.MatVal(a.m.times(b.m))
        a is CalcValue.MatVal && b is CalcValue.Scalar -> CalcValue.MatVal(a.m.scalar(b.c.re))
        a is CalcValue.Scalar && b is CalcValue.MatVal -> CalcValue.MatVal(b.m.scalar(a.c.re))
        a is CalcValue.VecVal && b is CalcValue.Scalar -> CalcValue.VecVal(a.v.scale(b.c.re))
        a is CalcValue.Scalar && b is CalcValue.VecVal -> CalcValue.VecVal(b.v.scale(a.c.re))
        else -> throw CalcMathError("不支持该乘法（类型不一致）")
    }

    private fun div(a: CalcValue, b: CalcValue): CalcValue = when {
        a is CalcValue.Scalar && b is CalcValue.Scalar -> CalcValue.Scalar(a.c.div(b.c))
        else -> throw CalcMathError("不支持该除法（类型不一致）")
    }

    /** r∠θ → 直角坐标复数 */
    private fun polar(r: ComplexNum, t: ComplexNum): CalcValue {
        val rv = real(r)
        val tv = real(t)
        return CalcValue.Scalar(
            ComplexNum(Value.of(clean(rv * kotlin.math.cos(rad(tv)))), Value.of(clean(rv * kotlin.math.sin(rad(tv)))))
        )
    }

    private fun rad(theta: Double): Double = CalcEngine.toRadians(mode, theta)

    private fun clean(v: Double): Double = if (abs(v) < 1e-12) 0.0 else v

    /** ·：向量点积；标量则退化为乘法 */
    private fun dotOp(a: CalcValue, b: CalcValue): CalcValue = when {
        a is CalcValue.VecVal && b is CalcValue.VecVal -> CalcValue.Scalar(ComplexNum(a.v.dot(b.v), zeroV))
        else -> mul(a, b)
    }

    // ---- 标量函数 ----

    private fun powC(a: ComplexNum, b: ComplexNum): ComplexNum {
        val aIm = abs(a.im.toDouble()) > 1e-12
        val bIm = abs(b.im.toDouble()) > 1e-12
        val eb = b.re.exact
        if (!bIm && eb != null && eb.isInteger && eb.num.abs() <= BigInteger.valueOf(1000)) {
            return powIntC(a, eb.num.toInt())
        }
        if (!aIm && !bIm) {
            val ea = a.re.exact
            if (ea != null && eb != null) {
                if (eb.isInteger && eb.num.abs() <= BigInteger.valueOf(1000)) {
                    return ComplexNum(Value.of(ea.pow(eb.num.toInt())), zeroV)
                }
                if (eb.num == BigInteger.ONE && eb.den == BigInteger.TWO) {
                    val s = ea.sqrtExact()
                    if (s != null) return ComplexNum(Value.of(s), zeroV)
                }
            }
            return ComplexNum(Value.of(MathOps.pow(a.re.toDouble(), b.re.toDouble())), zeroV)
        }
        throw CalcMathError("数学错误")
    }

    private fun powIntC(a: ComplexNum, k: Int): ComplexNum {
        if (k == 0) return oneI
        var base = if (k < 0) oneI.div(a) else a
        var n = abs(k)
        var acc = oneI
        while (n > 0) {
            if (n and 1 == 1) acc = acc.times(base)
            base = base.times(base)
            n = n shr 1
        }
        return acc
    }

    private fun factC(c: ComplexNum): ComplexNum {
        if (abs(c.im.toDouble()) > 1e-12) throw CalcMathError("数学错误")
        val e = c.re.exact
        if (e != null && e.isInteger && e.signum >= 0 && e.num <= BigInteger.valueOf(200)) {
            var r = BigInteger.ONE
            var k = 2
            val nn = e.num.toInt()
            while (k <= nn) {
                r = r.multiply(BigInteger.valueOf(k.toLong()))
                k++
            }
            return ComplexNum(Value.of(Rational.of(r, BigInteger.ONE)), zeroV)
        }
        return ComplexNum(Value.of(MathOps.factorial(c.re.toDouble())), zeroV)
    }

    private fun pctC(c: ComplexNum): ComplexNum {
        if (abs(c.im.toDouble()) > 1e-12) throw CalcMathError("数学错误")
        val e = c.re.exact
        if (e != null) return ComplexNum(Value.of(e.div(Rational.of(100))), zeroV)
        return ComplexNum(Value.of(c.re.toDouble() / 100.0), zeroV)
    }

    private fun recipC(c: ComplexNum): ComplexNum = oneI.div(c)

    private fun sqrtC(c: ComplexNum): ComplexNum {
        if (abs(c.im.toDouble()) > 1e-12) throw CalcMathError("数学错误")
        val e = c.re.exact
        if (e != null) {
            if (e.signum < 0) throw CalcMathError("数学错误")
            val s = e.sqrtExact()
            if (s != null) return ComplexNum(Value.of(s), zeroV)
        }
        val d = c.re.toDouble()
        if (d < 0.0) throw CalcMathError("数学错误")
        return ComplexNum(Value.of(sqrt(d)), zeroV)
    }

    private fun fn(name: String, v: CalcValue): CalcValue {
        if (name == "abs") {
            if (v is CalcValue.VecVal) {
                val ex = v.v.normExact()
                val value = if (ex != null) Value.of(ex) else Value.of(v.v.norm())
                return CalcValue.Scalar(ComplexNum(value, zeroV))
            }
            val c = one(v)
            if (abs(c.im.toDouble()) > 1e-12) throw CalcMathError("数学错误")
            val e = c.re.exact
            if (e != null) return CalcValue.Scalar(ComplexNum(Value.of(e.abs()), zeroV))
            return CalcValue.Scalar(ComplexNum(Value.of(abs(c.re.toDouble())), zeroV))
        }
        val c = one(v)
        return CalcValue.Scalar(ComplexNum(Value.of(MathOps.fn(mode, name, real(c))), zeroV))
    }

    // ---- 可变参数函数：矩阵 / 向量 / 统计 / 分布 ----

    private fun fnN(name: String, args: List<CalcValue>): CalcValue = when (name) {
        "det" -> {
            val m = mat(args, 0, "det")
            if (m.rows != m.cols) throw NumericError("行列式要求方阵")
            CalcValue.Scalar(ComplexNum(m.det(), zeroV))
        }
        "inv" -> CalcValue.MatVal(mat(args, 0, "inv").inverse())
        "trn" -> CalcValue.MatVal(mat(args, 0, "trn").transpose())
        "cross" -> {
            if (args.size != 2) throw CalcSyntaxError("cross 需要两个向量")
            CalcValue.VecVal(vec(args, 0, "cross").cross(vec(args, 1, "cross")))
        }
        "dot" -> {
            if (args.size != 2) throw CalcSyntaxError("dot 需要两个向量")
            CalcValue.Scalar(ComplexNum(vec(args, 0, "dot").dot(vec(args, 1, "dot")), zeroV))
        }
        "mean", "sd", "ssd", "sigma" -> {
            val xs = args.map { real(one(it)) }
            val s = StatOps.oneVar(xs)
            val v = when (name) {
                "mean" -> s.mean
                "sd", "sigma" -> s.popSigma
                else -> s.sampleS
            }
            CalcValue.Scalar(ComplexNum(Value.of(v), zeroV))
        }
        "normpdf" -> {
            val (x, mu, sigma) = normArgs(args, name)
            CalcValue.Scalar(ComplexNum(Value.of(DistrOps.normPdf((x - mu) / sigma) / sigma), zeroV))
        }
        "normcdf" -> {
            val (x, mu, sigma) = normArgs(args, name)
            CalcValue.Scalar(ComplexNum(Value.of(DistrOps.normCdf((x - mu) / sigma)), zeroV))
        }
        "invnorm" -> {
            val (x, mu, sigma) = normArgs(args, name)
            CalcValue.Scalar(ComplexNum(Value.of(mu + sigma * DistrOps.invNormCdf(x)), zeroV))
        }
        "binompdf", "binomcdf" -> {
            if (args.size != 3) throw CalcSyntaxError("$name 需要三个参数 (n, k, p)")
            val n = intArg(args[0], "n")
            val k = intArg(args[1], "k")
            val p = real(one(args[2]))
            val v = if (name == "binompdf") DistrOps.binomialPdf(n, k, p) else DistrOps.binomialCdf(n, k, p)
            CalcValue.Scalar(ComplexNum(Value.of(v), zeroV))
        }
        "poissonpdf", "poissoncdf" -> {
            if (args.size != 2) throw CalcSyntaxError("$name 需要两个参数 (λ, k)")
            val lambda = real(one(args[0]))
            val k = intArg(args[1], "k")
            val v = if (name == "poissonpdf") DistrOps.poissonPdf(lambda, k) else DistrOps.poissonCdf(lambda, k)
            CalcValue.Scalar(ComplexNum(Value.of(v), zeroV))
        }
        else -> throw CalcSyntaxError("语法错误")
    }

    /** normpdf / normcdf / invnorm：1 个参数（标准正态）或 3 个参数（x, μ, σ） */
    private fun normArgs(args: List<CalcValue>, name: String): Triple<Double, Double, Double> = when (args.size) {
        1 -> Triple(real(one(args[0])), 0.0, 1.0)
        3 -> Triple(real(one(args[0])), real(one(args[1])), real(one(args[2])))
        else -> throw CalcSyntaxError("$name 需要 1 个或 3 个参数")
    }

    private fun intArg(v: CalcValue, label: String): Int {
        val d = real(one(v))
        if (d != kotlin.math.floor(d)) throw NumericError("$label 必须是整数")
        return d.toInt()
    }

    private fun mat(args: List<CalcValue>, i: Int, fn: String): Matrix {
        val a = args.getOrNull(i) ?: throw CalcSyntaxError("$fn 需要矩阵参数")
        return (a as? CalcValue.MatVal)?.m ?: throw CalcMathError("$fn 需要矩阵参数")
    }

    private fun vec(args: List<CalcValue>, i: Int, fn: String): Vector3 {
        val a = args.getOrNull(i) ?: throw CalcSyntaxError("$fn 需要向量参数")
        return (a as? CalcValue.VecVal)?.v ?: throw CalcMathError("$fn 需要向量参数")
    }

    // ---- 「含新语法」判定：决定哪些子树走联合求值 ----

    private fun hasNew(n: Node): Boolean = when (n) {
        is Node.Polar, is Node.Dot, is Node.MatRef, is Node.VecRef, is Node.FnN -> true
        is Node.Neg -> hasNew(n.a)
        is Node.Add -> hasNew(n.a) || hasNew(n.b)
        is Node.Sub -> hasNew(n.a) || hasNew(n.b)
        is Node.Mul -> hasNew(n.a) || hasNew(n.b)
        is Node.Div -> hasNew(n.a) || hasNew(n.b)
        is Node.Pow -> hasNew(n.a) || hasNew(n.b)
        is Node.Fact -> hasNew(n.a)
        is Node.Pct -> hasNew(n.a)
        is Node.Recip -> hasNew(n.a)
        is Node.Sqrt -> hasNew(n.a)
        is Node.Fn -> hasNew(n.a)
        is Node.Fn2 -> hasNew(n.a) || hasNew(n.b)
        else -> false
    }
}
