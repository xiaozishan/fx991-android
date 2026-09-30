package io.paimon.fx991.engine

import kotlin.math.abs

// ---------------------------------------------------------------------------
// 批次 D：方程模式 EQN
//
// · 多项式方程（2 / 3 / 4 次）：系数 → 表达式串 → 复用主行 EquationSolver 的
//   精确根逻辑（有理根 / 根式 / 复根）；精确化失败（3/4 次无有理根）时用
//   Durand-Kerner 数值兜底，给出全部根（含复根）。
// · 联立线性方程组（2~4 元）：高斯消元 + 精确分数，无解 / 无穷多解明确告知。
//   （主行 EquationSolver 只支持 x/y/z 三元，这里独立实现到四元 w。）
// ---------------------------------------------------------------------------

object EqnMode {

    /** 联立方程未知量命名 */
    private val VAR_NAMES = listOf("x", "y", "z", "w")

    /** 解析系数输入（小数 / 整数 → 精确有理数） */
    fun parseCoeff(s: String, what: String): Rational {
        val t = s.trim().replace('−', '-')
        if (t.isEmpty()) throw NumericError("请输入 $what")
        return Rational.fromDecimal(t) ?: throw NumericError("$what 不是合法数字")
    }

    // -----------------------------------------------------------------------
    // 多项式方程
    // -----------------------------------------------------------------------

    /**
     * 解 n 次多项式方程。coeffsDesc 从高次项到常数项，长度 n+1（n = 2 / 3 / 4）。
     * 能精确时给精确根；不能精确时给数值根（含复根）。
     */
    fun solvePolynomial(coeffsDesc: List<Rational>): SolveResult {
        val n = coeffsDesc.size - 1
        if (n < 1) throw NumericError("次数至少是 1")
        if (coeffsDesc[0].isZero) throw NumericError("最高次系数不能为 0")
        val expr = polyString(coeffsDesc) + "=0"
        val r = EquationSolver.solve(expr, AngleMode.DEG)
        if (r.kind != SolveKind.ERROR) return r
        // 3 / 4 次且无有理根 → 精确化失败，走数值兜底
        return numericPoly(coeffsDesc)
    }

    /** 系数列表 → "1x^2-3x+2" 形式的表达式串（零系数项省略） */
    fun polyString(coeffsDesc: List<Rational>): String {
        val n = coeffsDesc.size - 1
        val sb = StringBuilder()
        var terms = 0
        for (i in coeffsDesc.indices) {
            val c = coeffsDesc[i]
            if (c.isZero) continue
            val deg = n - i
            sb.append(coeffTerm(c, terms == 0))
            when (deg) {
                0 -> Unit
                1 -> sb.append("x")
                else -> sb.append("x^").append(deg)
            }
            terms++
        }
        if (terms == 0) sb.append("0")
        return sb.toString()
    }

    /**
     * 单个系数的表达式片段（自带符号；非首项正系数加 +）。
     * 系数经 fromDecimal 进来，分母只含因子 2 / 5，十进制展开有限 → 精确往返。
     */
    private fun coeffTerm(c: Rational, first: Boolean): String {
        val neg = c.signum < 0
        val mag = if (neg) c.negate() else c
        val body = if (mag.isInteger) {
            mag.num.toString()
        } else {
            mag.toBigDecimalString(40).trimEnd('0').trimEnd('.')
        }
        return when {
            first && !neg -> body
            first -> "-$body"
            neg -> "-$body"
            else -> "+$body"
        }
    }

    // ---- Durand-Kerner 数值兜底 ----

    private class Cx(val re: Double, val im: Double)

    /**
     * Durand-Kerner 求 n 次多项式全部根（含复根）。
     * coeffsDesc 从高次到常数项；返回长度 2n 的数组 [re0, im0, re1, im1, …]。
     */
    fun durandKerner(coeffsDesc: DoubleArray): DoubleArray {
        val n = coeffsDesc.size - 1
        require(n >= 1) { "degree >= 1" }
        val lead = coeffsDesc[0]
        require(lead != 0.0) { "leading coefficient != 0" }
        val c = DoubleArray(n + 1) { coeffsDesc[it] / lead } // 首一化

        // Cauchy 根半径上界（首一多项式）：|x| ≤ 1 + max|c_i|
        var m = 0.0
        for (i in 1..n) m = maxOf(m, abs(c[i]))
        val radius = 1.0 + m

        // 初值：(0.4 + 0.9i)^(k+1) × radius —— 经典取法，避免对称陷阱
        val re = DoubleArray(n)
        val im = DoubleArray(n)
        var br = 0.4
        var bi = 0.9
        for (k in 0 until n) {
            re[k] = br * radius
            im[k] = bi * radius
            val nr = br * 0.4 - bi * 0.9
            val ni = br * 0.9 + bi * 0.4
            br = nr
            bi = ni
        }

        var iter = 0
        while (iter < 500) {
            iter++
            var maxDelta = 0.0
            for (k in 0 until n) {
                // p = f(roots[k])（Horner）
                var pr = c[0]
                var pi = 0.0
                for (j in 1..n) {
                    val nr = pr * re[k] - pi * im[k] + c[j]
                    val ni = pr * im[k] + pi * re[k]
                    pr = nr
                    pi = ni
                }
                // denom = ∏_{j≠k} (roots[k] − roots[j])
                var dr = 1.0
                var di = 0.0
                for (j in 0 until n) {
                    if (j == k) continue
                    val er = re[k] - re[j]
                    val ei = im[k] - im[j]
                    val nr = dr * er - di * ei
                    val ni = dr * ei + di * er
                    dr = nr
                    di = ni
                }
                var den = dr * dr + di * di
                if (den < 1e-300) {
                    dr += 1e-9 // 初值撞车兜底：微扰
                    den = dr * dr + di * di
                }
                val qr = (pr * dr + pi * di) / den
                val qi = (pi * dr - pr * di) / den
                re[k] -= qr
                im[k] -= qi
                val d = abs(qr) + abs(qi)
                if (d > maxDelta) maxDelta = d
            }
            if (maxDelta < 1e-13) break
        }
        val out = DoubleArray(2 * n)
        for (k in 0 until n) {
            out[2 * k] = re[k]
            out[2 * k + 1] = im[k]
        }
        return out
    }

    /** 精确化失败的 3 / 4 次多项式：数值给出全部根（实根在前，复根按共轭对合并显示） */
    private fun numericPoly(coeffsDesc: List<Rational>): SolveResult {
        val cf = DoubleArray(coeffsDesc.size) { coeffsDesc[it].toDouble() }
        val flat = durandKerner(cf)
        val roots = (0 until flat.size / 2).map { Cx(flat[2 * it], flat[2 * it + 1]) }

        fun isReal(c: Cx): Boolean = abs(c.im) <= 1e-7 * (1.0 + abs(c.re))

        // 实根按值升序在前；复根按实部、|虚部| 排序在后
        val sorted = roots.sortedWith(
            compareBy({ if (isReal(it)) 0 else 1 }, { it.re }, { abs(it.im) }),
        )
        val texts = ArrayList<String>()
        val items = ArrayList<SolveItem>()
        val used = BooleanArray(sorted.size)
        for (i in sorted.indices) {
            if (used[i]) continue
            used[i] = true
            val r = sorted[i]
            if (isReal(r)) {
                val s = CalcEngine.format(r.re)
                texts.add(s)
                items.add(SolveItem("x = $s", s))
            } else {
                // 找共轭，合并成 a ± bi
                var conj = -1
                for (j in sorted.indices) {
                    if (used[j]) continue
                    val q = sorted[j]
                    if (abs(q.re - r.re) < 1e-7 * (1.0 + abs(r.re)) &&
                        abs(q.im + r.im) < 1e-7 * (1.0 + abs(r.im))
                    ) {
                        conj = j
                        break
                    }
                }
                if (conj >= 0) used[conj] = true
                val a = CalcEngine.format(r.re)
                val b = CalcEngine.format(abs(r.im))
                val t = "$a ± ${b}i"
                texts.add(t)
                items.add(SolveItem("x = $t", null))
            }
        }
        return SolveResult(
            SolveKind.SOLUTIONS,
            "x = " + texts.joinToString(", "),
            items,
            note = "数值解：该方程没有可精确化的有理根，以下为 Durand-Kerner 数值根（含复根）",
        )
    }

    // -----------------------------------------------------------------------
    // 联立线性方程组（2~4 元，高斯消元 + 精确分数）
    // -----------------------------------------------------------------------

    /**
     * 解 n 元线性方程组（2 ≤ n ≤ 4）。
     * a[i][j] = 第 i 式第 j 个未知量的系数；b[i] = 第 i 式等号右侧常数。
     * 唯一解给精确分数；无解 / 无穷多解明确告知。
     */
    fun solveLinear(a: List<List<Rational>>, b: List<Rational>): SolveResult {
        val n = b.size
        if (n < 2 || n > 4) throw NumericError("未知量个数须为 2~4")
        if (a.size != n || a.any { it.size != n }) throw NumericError("系数矩阵形状不正确")

        // 增广矩阵（原地消元）
        val m = Array(n) { i -> Array(n + 1) { j -> if (j < n) a[i][j] else b[i] } }
        var rank = 0
        val pivotCol = IntArray(n) { -1 }
        for (col in 0 until n) {
            var sel = -1
            for (r in rank until n) {
                if (!m[r][col].isZero) {
                    sel = r
                    break
                }
            }
            if (sel < 0) continue
            val tmp = m[rank]; m[rank] = m[sel]; m[sel] = tmp
            val pv = m[rank][col]
            for (cc in 0..n) m[rank][cc] = m[rank][cc].div(pv)
            for (r in 0 until n) {
                if (r == rank) continue
                val f = m[r][col]
                if (f.isZero) continue
                for (cc in 0..n) m[r][cc] = m[r][cc].minus(f.times(m[rank][cc]))
            }
            pivotCol[rank] = col
            rank++
        }
        // 不相容：0 = 非零
        for (r in rank until n) {
            if (!m[r][n].isZero) {
                return SolveResult(SolveKind.NONE, "无解", note = "方程组不相容（化简后出现 0 = 非零 的行）")
            }
        }
        if (rank < n) {
            return SolveResult(
                SolveKind.INFINITE, "无穷多解",
                note = "独立方程数（$rank）少于未知量数（$n），存在自由变量",
            )
        }
        val values = arrayOfNulls<Rational>(n)
        for (r in 0 until rank) {
            val colIdx = pivotCol[r]
            if (colIdx >= 0) values[colIdx] = m[r][n]
        }
        val parts = ArrayList<String>()
        val items = ArrayList<SolveItem>()
        for (j in 0 until n) {
            val v = values[j] ?: Rational.ZERO
            val s = CalcEngine.formatRational(v, false)
            parts.add("${VAR_NAMES[j]} = $s")
            items.add(SolveItem("${VAR_NAMES[j]} = $s", s))
        }
        return SolveResult(SolveKind.SOLUTIONS, parts.joinToString(", "), items)
    }
}
