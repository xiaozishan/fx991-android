package io.paimon.fx991.engine

import java.math.BigInteger
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

// ---------------------------------------------------------------------------
// 批次 C：复数运算（纯 Kotlin，无 Compose 依赖 → 可在 JVM 上直接回归测试）
//
// 双轨内核：实部 / 虚部各是 Value（Exact(Rational) | Floating(Double)），
// 所以「能精确时就精确」在复数域同样成立：分数四则保持分数。
// ---------------------------------------------------------------------------

/** 双轨算术的公共小工具（复数 / 矩阵 / 向量共用） */
object ExactMath {

    fun zero(): Value = Value.of(Rational.ZERO)
    fun one(): Value = Value.of(Rational.ONE)
    fun long(n: Long): Value = Value.of(Rational.of(BigInteger.valueOf(n), BigInteger.ONE))

    fun add(a: Value, b: Value): Value {
        val ea = a.exact
        val eb = b.exact
        return if (ea != null && eb != null) Value.of(ea.plus(eb))
        else Value.of(a.toDouble() + b.toDouble())
    }

    fun sub(a: Value, b: Value): Value {
        val ea = a.exact
        val eb = b.exact
        return if (ea != null && eb != null) Value.of(ea.minus(eb))
        else Value.of(a.toDouble() - b.toDouble())
    }

    fun mul(a: Value, b: Value): Value {
        val ea = a.exact
        val eb = b.exact
        return if (ea != null && eb != null) Value.of(ea.times(eb))
        else Value.of(a.toDouble() * b.toDouble())
    }

    fun div(a: Value, b: Value): Value {
        val ea = a.exact
        val eb = b.exact
        if (ea != null && eb != null) return Value.of(ea.div(eb))
        val d = b.toDouble()
        if (d == 0.0) throw CalcMathError("数学错误")
        return Value.of(a.toDouble() / d)
    }

    fun neg(a: Value): Value {
        val e = a.exact
        return if (e != null) Value.of(e.negate()) else Value.of(-a.toDouble())
    }

    fun isZero(a: Value): Boolean {
        val e = a.exact
        return if (e != null) e.isZero else a.toDouble() == 0.0
    }

    fun sum(list: List<Value>): Value {
        var s = zero()
        for (v in list) s = add(s, v)
        return s
    }

    /** 双轨显示（精确轨给分数，浮点轨给 10 位有效数字） */
    fun str(v: Value): String =
        CalcEngine.formatValue(v, false, false, 10, null, NumberNotation.NORM)
}

/** 复数 a + bi（实部 / 虚部均为双轨 Value） */
class ComplexNum(val re: Value, val im: Value) {

    /** 实部与虚部都在精确轨上 */
    val exact: Boolean get() = re.exact != null && im.exact != null

    fun plus(o: ComplexNum): ComplexNum =
        ComplexNum(ExactMath.add(re, o.re), ExactMath.add(im, o.im))

    fun minus(o: ComplexNum): ComplexNum =
        ComplexNum(ExactMath.sub(re, o.re), ExactMath.sub(im, o.im))

    fun times(o: ComplexNum): ComplexNum = ComplexNum(
        ExactMath.sub(ExactMath.mul(re, o.re), ExactMath.mul(im, o.im)),
        ExactMath.add(ExactMath.mul(re, o.im), ExactMath.mul(im, o.re)),
    )

    fun div(o: ComplexNum): ComplexNum {
        val d = ExactMath.add(ExactMath.mul(o.re, o.re), ExactMath.mul(o.im, o.im))
        if (ExactMath.isZero(d)) throw CalcMathError("数学错误")
        val nr = ExactMath.add(ExactMath.mul(re, o.re), ExactMath.mul(im, o.im))
        val ni = ExactMath.sub(ExactMath.mul(im, o.re), ExactMath.mul(re, o.im))
        return ComplexNum(ExactMath.div(nr, d), ExactMath.div(ni, d))
    }

    /** 共轭 a − bi */
    fun conjugate(): ComplexNum = ComplexNum(re, ExactMath.neg(im))

    /** 模 |a + bi| = √(a² + b²) */
    fun modulus(): Double = hypot(re.toDouble(), im.toDouble())

    /** 模的精确值（a²+b² 能开尽时给有理数，否则 null） */
    fun modulusExact(): Rational? {
        val er = re.exact
        val ei = im.exact
        if (er == null || ei == null) return null
        val s = er.times(er).plus(ei.times(ei))
        return s.sqrtExact()
    }

    /** 辐角（按角度制返回） */
    fun arg(mode: AngleMode): Double =
        CalcEngine.fromRadians(mode, atan2(im.toDouble(), re.toDouble()))

    /** 辐角（弧度） */
    fun argRad(): Double = atan2(im.toDouble(), re.toDouble())

    fun isReal(eps: Double = 1e-12): Boolean = abs(im.toDouble()) <= eps

    /** 极坐标 ⇄ 直角坐标 */
    fun toPolar(mode: AngleMode): PolarPair =
        PolarForm.toPolar(re.toDouble(), im.toDouble(), mode)

    /** 显示 a + bi / a − bi / a */
    fun format(): String {
        val s = ExactMath.str(re)
        if (isReal()) return s
        val imv = im.toDouble()
        return if (imv < 0.0) "$s \u2212 ${ExactMath.str(ExactMath.neg(im))}i"
        else "$s + ${ExactMath.str(im)}i"
    }

    override fun toString(): String = format()

    companion object {

        /** 由两个浮点构造 */
        @JvmStatic
        fun of(re: Double, im: Double = 0.0): ComplexNum =
            ComplexNum(Value.of(re), Value.of(im))

        /** 由两个字面量构造：字面量能转有理数时走精确轨 */
        @JvmStatic
        fun ofLiteral(re: String, im: String): ComplexNum =
            ComplexNum(literal(re), literal(im))

        /** 由两个有理数构造（精确） */
        @JvmStatic
        fun ofExact(re: Rational, im: Rational = Rational.ZERO): ComplexNum =
            ComplexNum(Value.of(re), Value.of(im))

        /** 极坐标 → 复数（r∠θ，按角度制） */
        @JvmStatic
        fun fromPolar(r: Double, theta: Double, mode: AngleMode): ComplexNum {
            val c = PolarForm.toRect(r, theta, mode)
            return ComplexNum(Value.of(c.re), Value.of(c.im))
        }

        /** 把数值字面量解析成 Value：支持小数与 a/b 分数，非法时抛错 */
        @JvmStatic
        fun literal(s: String): Value {
            val t = s.trim()
            if (t.isEmpty()) return Value.of(0.0)
            val slash = t.indexOf('/')
            if (slash > 0) {
                val n = t.substring(0, slash).trim().toBigDecimalOrNull()
                val d = t.substring(slash + 1).trim().toBigDecimalOrNull()
                if (n != null && d != null && d.signum() != 0) {
                    val rn = Rational.fromDecimal(n.toPlainString())!!
                    val rd = Rational.fromDecimal(d.toPlainString())!!
                    return Value.of(rn.div(rd))
                }
            }
            val d = t.toDoubleOrNull() ?: throw NumericError("不是合法数字：$s")
            return Value.of(d, t)
        }
    }
}
