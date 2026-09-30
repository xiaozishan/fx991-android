package io.paimon.fx991.engine

import java.math.BigInteger
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.round
import kotlin.math.sin

/**
 * 数值算法（批次 A）：数值求根 / 定积分 / 数值导数 / 求和 / 极限 / 排列组合 / 度分秒 / 极坐标。
 *
 * 全部手写，零第三方依赖。表达式里的自变量统一是 `x`（沿用微分方程那条
 * `CalcEngine.evaluateWith` 通道）。
 */

/** 数值算法错误（求根失败 / 不收敛 / 参数越界…） */
class NumericError(message: String) : Exception(message)

/** 数值求根结果；能识别成精确有理数时 `exact` 非空 */
class RootResult(
    val root: Double,
    val exact: Rational?,
    val iterations: Int,
    val method: String,
)

/** 极限结果：左右极限分别给出，不相等时 `equal` 为 false */
class LimitResult(val left: Double, val right: Double) {
    val equal: Boolean
        get() = abs(left - right) <= 1e-7 * (1.0 + maxOf(abs(left), abs(right)))

    /** 双侧极限相等时给出取值，否则 NaN */
    fun value(): Double = if (equal) (left + right) / 2.0 else Double.NaN
}

/** 度分秒（deg / min / sec 均为非负量值，符号落在 negative 上，避免 -0 丢符号） */
class Dms(val negative: Boolean, val deg: Long, val min: Long, val sec: Double) {
    override fun toString(): String {
        val sign = if (negative) "\u2212" else ""
        return "$sign$deg\u00B0$min\u2032${trim(sec)}\u2033"
    }

    private fun trim(v: Double): String {
        val r = round(v * 1e4) / 1e4
        return if (r == floor(r)) r.toLong().toString() else r.toString()
    }
}

/** 直角坐标复数（只用来承载 a∠θ 的结果，不含完整复数运算） */
class ComplexRect(val re: Double, val im: Double)

/** 极坐标 */
class PolarPair(val r: Double, val theta: Double)

object NumericOps {

    const val MAX_TERMS = 1_000_000L
    private const val MAX_BISECT_SCAN = 400

    // -----------------------------------------------------------------------
    // 公共求值入口
    // -----------------------------------------------------------------------

    /** 失败（未定义 / 非有限）时返回 null */
    private fun safeEval(expr: String, mode: AngleMode, x: Double): Double? = try {
        val v = CalcEngine.evaluateWith(expr, mode, x, 0.0)
        if (v.isNaN() || v.isInfinite()) null else v
    } catch (_: Exception) {
        null
    }

    private fun evalAt(expr: String, mode: AngleMode, x: Double): Double =
        CalcEngine.evaluateWith(expr, mode, x, 0.0)

    // -----------------------------------------------------------------------
    // 数值导数：中心差分 + Richardson 外推
    // -----------------------------------------------------------------------

    fun derivative(expr: String, mode: AngleMode, x: Double): Double {
        val h0 = 1e-3 * maxOf(1.0, abs(x))
        fun d(h: Double): Double {
            val a = evalAt(expr, mode, x + h)
            val b = evalAt(expr, mode, x - h)
            return (a - b) / (2.0 * h)
        }
        val d1 = d(h0)
        val d2 = d(h0 / 2.0)
        val r = d2 + (d2 - d1) / 3.0        // 4 阶外推
        if (r.isNaN() || r.isInfinite()) throw NumericError("导数计算失败")
        return r
    }

    // -----------------------------------------------------------------------
    // 数值定积分：自适应 Simpson，容差可显式设置
    // -----------------------------------------------------------------------

    fun integrate(expr: String, mode: AngleMode, a: Double, b: Double, tol: Double): Double {
        if (tol <= 0.0 || tol.isNaN()) throw NumericError("容差必须为正")
        if (a == b) return 0.0
        val fa = safeEval(expr, mode, a) ?: throw NumericError("积分端点无定义")
        val fb = safeEval(expr, mode, b) ?: throw NumericError("积分端点无定义")
        val fm = safeEval(expr, mode, (a + b) / 2.0) ?: throw NumericError("积分区间内无定义")
        val whole = (b - a) / 6.0 * (fa + 4.0 * fm + fb)
        val r = adaptive(expr, mode, a, b, tol, whole, fa, fm, fb, 60)
        if (r.isNaN() || r.isInfinite()) throw NumericError("积分不收敛")
        return r
    }

    private fun adaptive(
        expr: String,
        mode: AngleMode,
        a: Double,
        b: Double,
        eps: Double,
        whole: Double,
        fa: Double,
        fm: Double,
        fb: Double,
        depth: Int,
    ): Double {
        val m = (a + b) / 2.0
        val lm = (a + m) / 2.0
        val rm = (m + b) / 2.0
        val flm = evalAt(expr, mode, lm)
        val frm = evalAt(expr, mode, rm)
        val left = (m - a) / 6.0 * (fa + 4.0 * flm + fm)
        val right = (b - m) / 6.0 * (fm + 4.0 * frm + fb)
        val delta = left + right - whole
        if (depth <= 0 || abs(delta) <= 15.0 * eps) {
            return left + right + delta / 15.0
        }
        return adaptive(expr, mode, a, m, eps / 2.0, left, fa, flm, fm, depth - 1) +
            adaptive(expr, mode, m, b, eps / 2.0, right, fm, frm, fb, depth - 1)
    }

    // -----------------------------------------------------------------------
    // 求和：Σ(f(x), x, a, b)，a / b 为整数
    // -----------------------------------------------------------------------

    fun summation(expr: String, mode: AngleMode, a: Long, b: Long): Double {
        if (a > b) throw NumericError("求和下界不能大于上界")
        if (b - a + 1 > MAX_TERMS) throw NumericError("求和项数过多（> $MAX_TERMS）")
        var s = 0.0
        var k = a
        while (k <= b) {
            s += evalAt(expr, mode, k.toDouble())
            k++
        }
        if (s.isNaN() || s.isInfinite()) throw NumericError("求和发散")
        return s
    }

    // -----------------------------------------------------------------------
    // 方程数值求根：牛顿法为主，不收敛 / 导数退化时退二分法
    // -----------------------------------------------------------------------

    fun solveRoot(expr: String, mode: AngleMode, guess: Double): RootResult {
        val newton = tryNewton(expr, mode, guess)
        if (newton != null) {
            val (x, it) = newton
            return RootResult(x, rationalize(expr, mode, x), it, "牛顿法")
        }
        val bisect = bisect(expr, mode, guess)
            ?: throw NumericError("求根失败：未找到变号区间，请换一个初值")
        val (x, it) = bisect
        return RootResult(x, rationalize(expr, mode, x), it, "二分法")
    }

    private fun tryNewton(expr: String, mode: AngleMode, guess: Double): Pair<Double, Int>? {
        var x = guess
        var k = 0
        while (k < 100) {
            k++
            val fx = safeEval(expr, mode, x) ?: return null
            if (abs(fx) < 1e-13) return x to k
            val dfx = try {
                derivative(expr, mode, x)
            } catch (_: Exception) {
                return null
            }
            if (dfx == 0.0 || dfx.isNaN() || dfx.isInfinite()) return null
            val nx = x - fx / dfx
            if (nx.isNaN() || nx.isInfinite() || abs(nx) > 1e100) return null
            if (abs(nx - x) < 1e-14 * maxOf(1.0, abs(nx))) {
                return if (abs(safeEval(expr, mode, nx) ?: Double.MAX_VALUE) < 1e-6) nx to k else null
            }
            x = nx
        }
        return null
    }

    private fun bisect(expr: String, mode: AngleMode, guess: Double): Pair<Double, Int>? {
        // 以初值为中心扫描，找离初值最近的变号区间
        val span = 1e3
        var bestL = Double.NaN
        var bestR = Double.NaN
        var bestD = Double.MAX_VALUE
        var prevX = guess - span
        var prevF = safeEval(expr, mode, prevX)
        var i = 1
        while (i <= MAX_BISECT_SCAN) {
            val xcur = guess - span + 2.0 * span * i / MAX_BISECT_SCAN
            val fcur = safeEval(expr, mode, xcur)
            if (prevF != null && fcur != null && prevF != 0.0 && (prevF > 0.0) != (fcur > 0.0)) {
                val mid = (prevX + xcur) / 2.0
                val dist = abs(mid - guess)
                if (dist < bestD) {
                    bestD = dist
                    bestL = prevX
                    bestR = xcur
                }
            }
            prevX = xcur
            prevF = fcur
            i++
        }
        if (bestL.isNaN()) return null
        var lo = bestL
        var hi = bestR
        var flo = evalAt(expr, mode, lo)
        var mid = (lo + hi) / 2.0
        var it = 0
        while (it < 200) {
            it++
            mid = (lo + hi) / 2.0
            val fm = safeEval(expr, mode, mid) ?: return null
            if (abs(fm) < 1e-15 || (hi - lo) < 1e-15 * (1.0 + abs(mid))) break
            if ((flo > 0.0) != (fm > 0.0)) hi = mid else { lo = mid; flo = fm }
        }
        return mid to it
    }

    /** 若根能写成小分母的精确分数且严格过零点，就把它还原出来 */
    private fun rationalize(expr: String, mode: AngleMode, x: Double): Rational? {
        val r = Rational.approx(x, 1000L) ?: return null
        val d = r.toDouble()
        if (abs(d - x) > 1e-9 * maxOf(1.0, abs(x))) return null
        val fv = safeEval(expr, mode, d) ?: return null
        return if (abs(fv) < 1e-9) r else null
    }

    // -----------------------------------------------------------------------
    // 数值极限：h→0 外推，左右各给一个
    // -----------------------------------------------------------------------

    fun limit(expr: String, mode: AngleMode, x0: Double): LimitResult =
        LimitResult(oneSided(expr, mode, x0, -1.0), oneSided(expr, mode, x0, 1.0))

    private fun oneSided(expr: String, mode: AngleMode, x0: Double, dir: Double): Double {
        val hs = doubleArrayOf(1e-2, 1e-3, 1e-4, 1e-5, 1e-6)
        val vals = ArrayList<Double>()
        for (h in hs) {
            val v = safeEval(expr, mode, x0 + dir * h)
            if (v != null) vals.add(v)
        }
        if (vals.isEmpty()) throw NumericError("极限计算失败：函数在该点附近无定义")
        val n = vals.size
        if (n == 1) return vals[0]
        // f(h) ≈ L + c·h，两点外推（步长比 10：L = (10·f(h/10) − f(h))/9）
        val a = vals[n - 1]
        val b = vals[n - 2]
        val r = (10.0 * a - b) / 9.0
        return if (r.isNaN() || r.isInfinite()) vals[n - 1] else r
    }

    // -----------------------------------------------------------------------
    // 排列组合：大数走 BigInteger，不溢出
    // -----------------------------------------------------------------------

    fun nPr(n: Long, r: Long): BigInteger {
        if (n < 0 || r < 0 || r > n) throw NumericError("排列数要求 n ≥ r ≥ 0")
        if (n > 1_000_000L) throw NumericError("n 过大")
        var res = BigInteger.ONE
        var i = 0L
        while (i < r) {
            res = res.multiply(BigInteger.valueOf(n - i))
            i++
        }
        return res
    }

    fun nCr(n: Long, r: Long): BigInteger {
        if (n < 0 || r < 0 || r > n) throw NumericError("组合数要求 n ≥ r ≥ 0")
        val k = if (r > n - r) n - r else r
        var res = BigInteger.ONE
        var i = 1L
        while (i <= k) {
            res = res.multiply(BigInteger.valueOf(n - k + i)).divide(BigInteger.valueOf(i))
            i++
        }
        return res
    }
}

// ---------------------------------------------------------------------------
// 度分秒（60 进制换算）
// ---------------------------------------------------------------------------

object Sexagesimal {

    /** 度分秒 → 十进制度（分/秒跟随度的符号，与参考机一致） */
    fun toDegrees(deg: Double, min: Double, sec: Double): Double {
        val negative = deg < 0.0 || (deg == 0.0 && (min < 0.0 || sec < 0.0))
        val mag = abs(deg) + abs(min) / 60.0 + abs(sec) / 3600.0
        return if (negative) -mag else mag
    }

    /** 十进制度 → 度分秒 */
    fun fromDegrees(x: Double): Dms {
        val neg = x < 0.0
        val a = abs(x)
        var d = floor(a).toLong()
        val remMin = (a - d) * 60.0
        var m = floor(remMin).toLong()
        var s = round(((remMin - m) * 60.0) * 1e4) / 1e4
        if (s >= 60.0) {
            s -= 60.0
            m += 1
        }
        if (m >= 60L) {
            m -= 60L
            d += 1
        }
        return Dms(neg, d, m, s)
    }
}

// ---------------------------------------------------------------------------
// 极坐标 ⇄ 直角坐标（复数形式）
// ---------------------------------------------------------------------------

object PolarForm {

    /** r∠θ → 直角坐标 */
    fun toRect(r: Double, theta: Double, mode: AngleMode): ComplexRect {
        val t = CalcEngine.toRadians(mode, theta)
        return ComplexRect(clean(r * cos(t)), clean(r * sin(t)))
    }

    /** 直角坐标 → r∠θ */
    fun toPolar(re: Double, im: Double, mode: AngleMode): PolarPair {
        val r = hypot(re, im)
        val theta = CalcEngine.fromRadians(mode, atan2(im, re))
        return PolarPair(clean(r), clean(theta))
    }

    /**
     * 极坐标运算符 `∠` 已提升为表达式语法里的真运算符（见 Unified），
     * 这里不再保留「整串特例解析、最多一个 ∠」的旧分支。
     */

    /** 极坐标形式文本：r∠θ */
    fun formatPolar(p: PolarPair): String =
        "${CalcEngine.format(p.r)}$ANGLE_MARK${CalcEngine.format(p.theta)}"

    private fun clean(v: Double): Double = if (abs(v) < 1e-12) 0.0 else v
}

internal const val ANGLE_MARK = "\u2220"
