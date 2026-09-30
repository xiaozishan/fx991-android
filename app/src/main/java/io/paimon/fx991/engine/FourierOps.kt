package io.paimon.fx991.engine

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

// ---------------------------------------------------------------------------
// 批次 G：傅里叶级数展开（纯 Kotlin，可 JVM 回归）
//
// 主行语法：fourier(f(x), a, b, n)
//   fourier(x^2, -pi, pi, 5)     x² 在 [−π, π] 展 5 项
//   fourier(x, 0, 2pi, 4)        x 在 [0, 2π] 展 4 项
//
// 约定（同济高数教科书）：周期 T = b − a，ω = 2π/T，
//   f(x) ≈ a0/2 + Σ [ an·cos(nωx) + bn·sin(nωx) ]
//   an = (2/T)∫[a,b] f·cos(nωx) dx     bn = (2/T)∫[a,b] f·sin(nωx) dx
// 系数用自适应 Simpson 数值积分（与 NumericOps 同方法，容差 1e-9）。
// 区间关于原点对称时自动做奇偶识别：偶函数 bn 全 0、奇函数 an/a0 全 0。
// 傅里叶内部的三角一律按弧度（与用户角度制无关）。
// ---------------------------------------------------------------------------

object FourierOps {

    enum class Parity(val label: String) {
        EVEN("偶函数"),
        ODD("奇函数"),
        GENERAL("一般（非奇非偶）"),
    }

    /** 主行 fourier(...) 解析出的参数 */
    class MainArgs(val f: String, val a: Double, val b: Double, val n: Int)

    class Result(
        val f: String,
        val a: Double,
        val b: Double,
        val n: Int,
        /** 教科书 a0（常数项是 a0/2） */
        val a0: Double,
        /** an[1..n]（下标 0 不用） */
        val an: DoubleArray,
        /** bn[1..n] */
        val bn: DoubleArray,
        val parity: Parity,
        val period: Double,
        val omega: Double,
        /** 被当做 0 抹掉的系数个数（|c| < 1e-9·规模） */
        val dropped: Int,
    ) {
        /** 部分和 S_n(x) 的数值（弧度制） */
        fun partialAt(x: Double): Double {
            var s = a0 / 2.0
            for (k in 1..n) {
                val t = k * omega * x
                s += an[k] * cos(t) + bn[k] * sin(t)
            }
            return s
        }

        /** 原函数值（弧度制；无定义时 null） */
        fun fAt(x: Double): Double? = evalF(f, x)
    }

    // -----------------------------------------------------------------------
    // 主行解析：fourier(f, a, b, n)
    // -----------------------------------------------------------------------

    /** 是 fourier 主行调用则返回参数（惰性：端点表达式在这里求值），否则 null */
    @JvmStatic
    fun parseMain(src: String): MainArgs? {
        val t = src.trim()
        val low = t.lowercase()
        if (!low.startsWith("fourier")) return null
        val rest = t.substring(7).trim()
        if (!rest.startsWith("(")) {
            throw NumericError("fourier 用法：fourier(f(x), 下限, 上限, 项数)")
        }
        val inner = rest.substring(1)
        val closed = if (inner.endsWith(")")) inner.substring(0, inner.length - 1) else inner
        val parts = splitTop(closed)
        if (parts.size != 4) throw NumericError("fourier 需要 4 个参数：f(x), 下限, 上限, 项数")
        val f = parts[0].trim()
        if (f.isEmpty()) throw NumericError("fourier 缺少 f(x)")
        val a = evalBound(parts[1], "下限")
        val b = evalBound(parts[2], "上限")
        val nd = evalBound(parts[3], "项数")
        if (nd != floor(nd) || nd < 1.0 || nd > 64.0) {
            throw NumericError("项数 n 必须是 1..64 的正整数")
        }
        return MainArgs(f, a, b, nd.toInt())
    }

    private fun evalBound(s: String, what: String): Double {
        val t = s.trim()
        if (t.isEmpty()) throw NumericError("fourier 缺少$what")
        val v = try {
            // 端点允许 -pi / 2pi 这类写法；三角常数按弧度语义（端点本是弧度量）
            CalcEngine.evaluate(t, AngleMode.RAD, 0.0, 0.0)
        } catch (e: Exception) {
            throw NumericError("fourier 的${what}算不出来：$t")
        }
        if (v.isNaN() || v.isInfinite()) throw NumericError("fourier 的${what}不是有限数")
        return v
    }

    /** 顶层逗号切分（括号内不切） */
    private fun splitTop(s: String): List<String> {
        val out = ArrayList<String>()
        var depth = 0
        val sb = StringBuilder()
        for (ch in s) {
            when {
                ch == '(' -> { depth++; sb.append(ch) }
                ch == ')' -> { depth--; sb.append(ch) }
                depth == 0 && ch == ',' -> { out.add(sb.toString()); sb.clear() }
                else -> sb.append(ch)
            }
        }
        out.add(sb.toString())
        return out
    }

    // -----------------------------------------------------------------------
    // 系数计算
    // -----------------------------------------------------------------------

    @JvmStatic
    fun compute(f: String, a: Double, b: Double, n: Int): Result {
        if (a.isNaN() || b.isNaN() || a.isInfinite() || b.isInfinite()) {
            throw NumericError("区间端点必须是有限数")
        }
        if (a == b) throw NumericError("区间长度不能为 0")
        if (a > b) throw NumericError("区间反了：下限必须小于上限")
        if (n <= 0) throw NumericError("项数 n 必须是正整数")
        if (n > 64) throw NumericError("项数 n 上限 64")
        val fexpr = f.trim()
        if (fexpr.isEmpty()) throw NumericError("请输入 f(x)")
        // 解析检查（把语法错误提前报出来）
        Parser(Lexer(fexpr).lex()).parse()

        val period = b - a
        val omega = 2.0 * Math.PI / period

        // 区间内定义检查：端点 / 中点 + 11 个采样点，任一无定义即拒绝
        val probes = ArrayList<Double>()
        probes.add(a); probes.add(b); probes.add((a + b) / 2.0)
        for (k in 1..11) probes.add(a + period * k / 12.0)
        for (p in probes) {
            if (evalF(fexpr, p) == null) {
                throw NumericError("函数在区间内有无定义点（x ≈ ${CalcEngine.format(p)}）")
            }
        }

        val tol = 1e-9
        val a0 = (2.0 / period) * integratePanels({ x -> evalF(fexpr, x) }, a, b, tol, 1)
        val an = DoubleArray(n + 1)
        val bn = DoubleArray(n + 1)
        for (k in 1..n) {
            // 载波 cos(kωx) 在 [a,b] 上振荡 k 个周期：每周期至少 8 个面板，
            // 否则自适应 Simpson 会在采样点混叠（全落在整数相位 → delta=0 假收敛）
            val panels = 8 * k
            an[k] = (2.0 / period) * integratePanels({ x ->
                evalF(fexpr, x)?.let { it * cos(k * omega * x) }
            }, a, b, tol, panels)
            bn[k] = (2.0 / period) * integratePanels({ x ->
                evalF(fexpr, x)?.let { it * sin(k * omega * x) }
            }, a, b, tol, panels)
        }

        // 奇偶识别（仅对称区间有意义）
        val parity = detectParity(fexpr, a, b)

        // 数值噪声抹零
        var scale = maxOf(1.0, abs(a0))
        for (k in 1..n) {
            scale = maxOf(scale, abs(an[k]), abs(bn[k]))
        }
        val zeroTol = 1e-8 * scale
        var dropped = 0
        var a0c = a0
        if (abs(a0c) < zeroTol) { if (a0c != 0.0) dropped++; a0c = 0.0 }
        for (k in 1..n) {
            if (abs(an[k]) < zeroTol) { if (an[k] != 0.0) dropped++; an[k] = 0.0 }
            if (abs(bn[k]) < zeroTol) { if (bn[k] != 0.0) dropped++; bn[k] = 0.0 }
        }
        // 奇偶确定后直接按对称性抹零（数值积分对消失系数只给到 1e-9 量级）
        if (parity == Parity.EVEN) for (k in 1..n) bn[k] = 0.0
        if (parity == Parity.ODD) {
            a0c = 0.0
            for (k in 1..n) an[k] = 0.0
        }
        return Result(fexpr, a, b, n, a0c, an, bn, parity, period, omega, dropped)
    }

    private fun evalF(f: String, x: Double): Double? = try {
        val v = CalcEngine.evaluateWith(f, AngleMode.RAD, x, 0.0)
        if (v.isNaN() || v.isInfinite()) null else v
    } catch (_: Exception) {
        null
    }

    /** 对称区间 [−L, L] 上的奇偶识别：采样 32 对点比较 f(−x) 与 ±f(x) */
    private fun detectParity(f: String, a: Double, b: Double): Parity {
        val period = b - a
        if (abs(a + b) > 1e-10 * period) return Parity.GENERAL
        var evenErr = 0.0
        var oddErr = 0.0
        var scale = 0.0
        var cnt = 0
        for (k in 1..32) {
            val x = period / 2.0 * k / 33.0
            val fp = evalF(f, x) ?: return Parity.GENERAL
            val fn = evalF(f, -x) ?: return Parity.GENERAL
            evenErr += abs(fp - fn)
            oddErr += abs(fp + fn)
            scale += abs(fp) + abs(fn)
            cnt++
        }
        val tol = 1e-7 * maxOf(1.0, scale / (2.0 * cnt))
        val meanEven = evenErr / cnt
        val meanOdd = oddErr / cnt
        return when {
            meanEven <= tol && meanOdd > tol -> Parity.EVEN
            meanOdd <= tol && meanEven > tol -> Parity.ODD
            meanEven <= tol -> Parity.EVEN   // 零函数之类：两边都成立，按偶报
            else -> Parity.GENERAL
        }
    }

    // -----------------------------------------------------------------------
    // 自适应 Simpson（函数句柄版，与 NumericOps 同方法；容差 1e-9，深度 40）
    // -----------------------------------------------------------------------

    /** 先均分成 panels 个面板再各自自适应 Simpson（抗振荡混叠） */
    private fun integratePanels(g: (Double) -> Double?, a: Double, b: Double, tol: Double, panels: Int): Double {
        val m = maxOf(1, panels)
        if (m == 1) return integrate(g, a, b, tol)
        val h = (b - a) / m
        var sum = 0.0
        var j = 0
        while (j < m) {
            sum += integrate(g, a + j * h, a + (j + 1) * h, tol / m)
            j++
        }
        return sum
    }

    private fun integrate(g: (Double) -> Double?, a: Double, b: Double, tol: Double): Double {
        val fa = g(a) ?: throw NumericError("积分端点无定义")
        val fb = g(b) ?: throw NumericError("积分端点无定义")
        val fm = g((a + b) / 2.0) ?: throw NumericError("积分区间内无定义")
        val whole = (b - a) / 6.0 * (fa + 4.0 * fm + fb)
        val r = adaptive(g, a, b, tol, whole, fa, fm, fb, 40)
        if (r.isNaN() || r.isInfinite()) throw NumericError("积分不收敛")
        return r
    }

    private fun adaptive(
        g: (Double) -> Double?,
        a: Double, b: Double, eps: Double, whole: Double,
        fa: Double, fm: Double, fb: Double, depth: Int,
    ): Double {
        val m = (a + b) / 2.0
        val lm = (a + m) / 2.0
        val rm = (m + b) / 2.0
        val flm = g(lm) ?: throw NumericError("积分区间内无定义")
        val frm = g(rm) ?: throw NumericError("积分区间内无定义")
        val left = (m - a) / 6.0 * (fa + 4.0 * flm + fm)
        val right = (b - m) / 6.0 * (fm + 4.0 * frm + fb)
        val delta = left + right - whole
        if (depth <= 0 || abs(delta) <= 15.0 * eps) {
            return left + right + delta / 15.0
        }
        return adaptive(g, a, m, eps / 2.0, left, fa, flm, fm, depth - 1) +
            adaptive(g, m, b, eps / 2.0, right, fm, frm, fb, depth - 1)
    }

    // -----------------------------------------------------------------------
    // 输出
    // -----------------------------------------------------------------------

    private fun fmt(v: Double): String = CalcEngine.format(v, 10, null)

    /** 多行报告（主行结果区 / MODE 界面共用） */
    @JvmStatic
    fun formatReport(r: Result): String {
        val sb = StringBuilder()
        sb.append("区间 [${fmt(r.a)}, ${fmt(r.b)}]，T = ${fmt(r.period)}，ω = ${fmt(r.omega)}；${r.parity.label}\n")
        sb.append("a0 = ${fmt(r.a0)}（常数项 a0/2 = ${fmt(r.a0 / 2.0)}）\n")
        val anList = (1..r.n).joinToString("  ") { "a$it=${fmt(r.an[it])}" }
        val bnList = (1..r.n).joinToString("  ") { "b$it=${fmt(r.bn[it])}" }
        sb.append("an: $anList\n")
        when (r.parity) {
            Parity.EVEN -> sb.append("bn: 全为 0（偶函数）\n")
            Parity.ODD -> sb.append("an/a0: 全为 0（奇函数）\nbn: $bnList\n")
            Parity.GENERAL -> sb.append("bn: $bnList\n")
        }
        sb.append("S${r.n}(x) = ${partialSumText(r)}")
        return sb.toString()
    }

    /** 部分和的可读文本：3.289868134 − 4·cos(x) + cos(2x) − … */
    @JvmStatic
    fun partialSumText(r: Result): String {
        val sb = StringBuilder()
        sb.append(fmt(r.a0 / 2.0))
        for (k in 1..r.n) {
            appendTerm(sb, r.an[k], "cos", k, r.omega)
            appendTerm(sb, r.bn[k], "sin", k, r.omega)
        }
        return sb.toString()
    }

    private fun appendTerm(sb: StringBuilder, coef: Double, fn: String, k: Int, omega: Double) {
        if (coef == 0.0) return
        val sign = if (coef < 0.0) " − " else " + "
        val mag = abs(coef)
        val arg = if (abs(omega - 1.0) < 1e-12) {
            if (k == 1) "x" else "${k}x"
        } else {
            "${fmt(k * omega)}x"
        }
        val coefText = if (mag == 1.0) "" else fmt(mag) + "·"
        sb.append(sign).append(coefText).append(fn).append('(').append(arg).append(')')
    }

    /**
     * 部分和的可求值表达式（可粘回主行 / 画图；内部三角按弧度制理解，
     * 主行若处于 DEG/GRAD 请先用 RAD 或注意单位）。
     */
    @JvmStatic
    fun partialSumExpr(r: Result): String {
        val sb = StringBuilder()
        sb.append(CalcEngine.literal(r.a0 / 2.0, 12))
        for (k in 1..r.n) {
            appendExprTerm(sb, r.an[k], "cos", k, r.omega)
            appendExprTerm(sb, r.bn[k], "sin", k, r.omega)
        }
        return sb.toString()
    }

    private fun appendExprTerm(sb: StringBuilder, coef: Double, fn: String, k: Int, omega: Double) {
        if (coef == 0.0) return
        val arg = if (abs(omega - 1.0) < 1e-12) {
            if (k == 1) "x" else "$k*x"
        } else {
            "${CalcEngine.literal(k * omega, 12)}*x"
        }
        sb.append(if (coef < 0.0) "-" else "+")
        sb.append('(').append(CalcEngine.literal(abs(coef), 12)).append(')')
        sb.append('*').append(fn).append('(').append(arg).append(')')
    }
}
