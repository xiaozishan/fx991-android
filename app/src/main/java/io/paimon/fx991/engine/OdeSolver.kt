package io.paimon.fx991.engine

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** 微分方程求解错误 */
class OdeError(message: String) : Exception(message)

/** 数值解采样表 */
class OdeTable(
    val xs: DoubleArray,
    val ys: DoubleArray,
    /** 二阶方程时存 y′，一阶为 null */
    val zs: DoubleArray?,
    val steps: Int,
) {
    fun sampleY(): Double = ys.lastOrNull() ?: Double.NaN
    fun sampleZ(): Double? = zs?.lastOrNull()
}

/**
 * 微分方程数值解（RK4 定步长）+ 常系数线性解析解。
 *
 *  一阶：y′ = f(x, y)
 *  二阶：y″ = f(x, y, y′)，内化为方程组 y′ = z, z′ = f(x, y, z)，复用同一套 RK4
 *  解析：a·y″ + b·y′ + c·y = 0（特征方程），二阶以内
 *
 * 符号解 / 通用 CAS 不做。
 */
object OdeSolver {

    const val MAX_STEPS = 100_000

    private fun f(expr: String, mode: AngleMode, x: Double, y: Double, z: Double): Double =
        CalcEngine.evaluateWith(expr, mode, x, y, z)

    private fun requireStep(x0: Double, xn: Double, h: Double) {
        if (h == 0.0) throw OdeError("步长不能为 0")
        if (!(xn > x0)) throw OdeError("xn 必须大于 x0")
        if (h < 0) throw OdeError("步长必须为正")
        if ((xn - x0) / h > MAX_STEPS) throw OdeError("步数过多（> $MAX_STEPS），请增大步长")
    }

    private fun guard(x: Double, vararg vs: Double) {
        for (v in vs) {
            if (v.isNaN() || v.isInfinite() || abs(v) > 1e300) {
                throw OdeError("在 x=$x 发散（数值解失效）")
            }
        }
    }

    /** 一阶：dy/dx = fn(x, y) */
    fun firstOrder(
        fn: String,
        x0: Double,
        y0: Double,
        xn: Double,
        h: Double,
        mode: AngleMode = AngleMode.RAD,
    ): OdeTable {
        requireStep(x0, xn, h)
        val xs = ArrayList<Double>()
        val ys = ArrayList<Double>()
        var x = x0
        var y = y0
        xs.add(x)
        ys.add(y)
        var steps = 0
        while (x < xn - 1e-12) {
            if (steps >= MAX_STEPS) throw OdeError("步数过多（> $MAX_STEPS），请增大步长")
            val hh = minOf(h, xn - x)          // 最后一步自动收尾到 xn
            val k1 = hh * f(fn, mode, x, y, 0.0)
            val k2 = hh * f(fn, mode, x + hh / 2, y + k1 / 2, 0.0)
            val k3 = hh * f(fn, mode, x + hh / 2, y + k2 / 2, 0.0)
            val k4 = hh * f(fn, mode, x + hh, y + k3, 0.0)
            y += (k1 + 2 * k2 + 2 * k3 + k4) / 6.0
            x += hh
            guard(x, y)
            xs.add(x)
            ys.add(y)
            steps++
        }
        return OdeTable(xs.toDoubleArray(), ys.toDoubleArray(), null, steps)
    }

    /** 二阶：y″ = fn(x, y, y′)，初值 (x0, y0, v0=y′(x0)) */
    fun secondOrder(
        fn: String,
        x0: Double,
        y0: Double,
        v0: Double,
        xn: Double,
        h: Double,
        mode: AngleMode = AngleMode.RAD,
    ): OdeTable {
        requireStep(x0, xn, h)
        val xs = ArrayList<Double>()
        val ys = ArrayList<Double>()
        val zs = ArrayList<Double>()
        var x = x0
        var y = y0
        var z = v0
        xs.add(x)
        ys.add(y)
        zs.add(z)
        var steps = 0
        while (x < xn - 1e-12) {
            if (steps >= MAX_STEPS) throw OdeError("步数过多（> $MAX_STEPS），请增大步长")
            val hh = minOf(h, xn - x)
            // y′ = z, z′ = f(x, y, z)
            val k1y = hh * z
            val k1z = hh * f(fn, mode, x, y, z)
            val k2y = hh * (z + k1z / 2)
            val k2z = hh * f(fn, mode, x + hh / 2, y + k1y / 2, z + k1z / 2)
            val k3y = hh * (z + k2z / 2)
            val k3z = hh * f(fn, mode, x + hh / 2, y + k2y / 2, z + k2z / 2)
            val k4y = hh * (z + k3z)
            val k4z = hh * f(fn, mode, x + hh, y + k3y, z + k3z)
            y += (k1y + 2 * k2y + 2 * k3y + k4y) / 6.0
            z += (k1z + 2 * k2z + 2 * k3z + k4z) / 6.0
            x += hh
            guard(x, y, z)
            xs.add(x)
            ys.add(y)
            zs.add(z)
            steps++
        }
        return OdeTable(xs.toDoubleArray(), ys.toDoubleArray(), zs.toDoubleArray(), steps)
    }

    // -----------------------------------------------------------------------
    // 常系数线性：a·y″ + b·y′ + c·y = 0（二阶以内）→ 特征方程解析解
    // -----------------------------------------------------------------------

    class Analytic(
        val kind: String,
        val formula: String,
        private val fn: (Double) -> Double,
    ) {
        fun value(x: Double): Double = fn(x)
    }

    fun analyticLinear(
        a: Double,
        b: Double,
        c: Double,
        x0: Double,
        y0: Double,
        v0: Double,
    ): Analytic {
        if (a == 0.0 && b == 0.0) throw OdeError("a 与 b 不能同时为 0")
        if (a == 0.0) {
            // 一阶线性：b·y′ + c·y = 0 → y = y0·e^{-(c/b)(x-x0)}
            val r = -c / b
            val c1 = y0
            val formula = "y = ${n(c1)}·e^(${n(r)}·(x ${sgn(-x0)}))"
            return Analytic("一阶线性（单实根 r=$r）", formula) { x ->
                c1 * exp(r * (x - x0))
            }
        }
        val disc = b * b - 4 * a * c
        if (disc > 1e-12) {
            val r1 = (-b + sqrt(disc)) / (2 * a)
            val r2 = (-b - sqrt(disc)) / (2 * a)
            // C1 e^{r1(x-x0)} + C2 e^{r2(x-x0)}
            val e1 = exp(r1 * x0)
            val e2 = exp(r2 * x0)
            // 线性方程组：C1 e^{r1 x0} + C2 e^{r2 x0} = y0
            //             r1 C1 e^{r1 x0} + r2 C2 e^{r2 x0} = v0
            val det = (r2 - r1) * e1 * e2
            if (abs(det) < 1e-300) throw OdeError("初值条件退化")
            val c1 = (y0 * r2 * e2 - v0 * e2) / det
            val c2 = (v0 * e1 - y0 * r1 * e1) / det
            val formula = "y = ${n(c1)}·e^(${n(r1)}(x ${sgn(-x0)})) + ${n(c2)}·e^(${n(r2)}(x ${sgn(-x0)}))"
            return Analytic("两相异实根 r₁=$r1, r₂=$r2", formula) { x ->
                c1 * exp(r1 * (x - x0)) + c2 * exp(r2 * (x - x0))
            }
        }
        if (disc > -1e-12) {
            val r = -b / (2 * a)
            val c1 = y0
            val c2 = v0 - r * y0
            val formula = "y = (${n(c1)} + ${n(c2)}·(x ${sgn(-x0)}))·e^(${n(r)}(x ${sgn(-x0)}))"
            return Analytic("重根 r=$r", formula) { x ->
                (c1 + c2 * (x - x0)) * exp(r * (x - x0))
            }
        }
        val alpha = -b / (2 * a)
        val beta = sqrt(-disc) / (2 * a)
        val c1 = y0
        val c2 = (v0 - alpha * y0) / beta
        val formula = "y = e^(${n(alpha)}(x ${sgn(-x0)}))·(${n(c1)}·cos(${n(beta)}(x ${sgn(-x0)})) " +
            "${sgn(-c2)} ${n(abs(c2))}·sin(${n(beta)}(x ${sgn(-x0)})))"
        return Analytic("共轭复根 α=${n(alpha)} ± ${n(beta)}i", formula) { x ->
            exp(alpha * (x - x0)) * (c1 * cos(beta * (x - x0)) + c2 * sin(beta * (x - x0)))
        }
    }

    private fun sgn(v: Double): String = if (v < 0) "+ ${abs(v)}" else "- ${abs(v)}"

    private fun n(v: Double): String {
        val r = (v * 1e6).toLong() / 1e6
        return if (r == r.toLong().toDouble()) r.toLong().toString() else r.toString()
    }

    /** 供表格使用：把解析解采样成表（末点落在 xn 上） */
    fun sampleAnalytic(sol: Analytic, x0: Double, xn: Double, h: Double): OdeTable {
        requireStep(x0, xn, h)
        val xs = ArrayList<Double>()
        val ys = ArrayList<Double>()
        var x = x0
        xs.add(x)
        ys.add(sol.value(x))
        var steps = 0
        while (x < xn - 1e-12) {
            if (steps >= MAX_STEPS) throw OdeError("步数过多（> $MAX_STEPS），请增大步长")
            x += minOf(h, xn - x)
            xs.add(x)
            ys.add(sol.value(x))
            steps++
        }
        return OdeTable(xs.toDoubleArray(), ys.toDoubleArray(), null, steps)
    }
}
