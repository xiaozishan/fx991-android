package io.paimon.fx991.engine

import java.math.BigInteger
import kotlin.math.abs
import kotlin.math.floor

// ---------------------------------------------------------------------------
// 主行直接求解（REFERENCE 第 7 条）
//
// 判定规则：
//   · 式子含未知变量（x / y / z）且带 `=` → 当方程求根
//   · 不含未知变量 → 普通求值（由调用方处理）
//   · 变量赋值走 STO 面板 / ▶，不靠 `=`
//
// 一元：多项式给全部根（含复根，能精确时给精确分数 / 根式）；
//       超越方程自动多初值扫多个根。
// 方程组：`,` 或 `;` 分隔；线性走高斯消元 + 精确分数，非线性走数值牛顿。
// 无解 / 无穷多解都明确告知。
// ---------------------------------------------------------------------------

enum class SolveKind { SOLUTIONS, NONE, INFINITE, ERROR }

/** 一个可逐条插入主行的解（insert 为空表示不可插入，如复根） */
class SolveItem(val label: String, val insert: String?)

class SolveResult(
    val kind: SolveKind,
    /** 主行显示文本 */
    val text: String,
    val items: List<SolveItem> = emptyList(),
    val note: String = "",
)

// ---------------------------------------------------------------------------

private typealias Mono = List<Int>

private class RootText(val display: String, val insert: String?)

object EquationSolver {

    private val VARS3 = listOf("x", "y", "z")

    /** 含等号 → 交给方程求解（是否真当方程由变量判定） */
    @JvmStatic
    fun looksLikeEquation(expr: String): Boolean = expr.contains('=')

    /** 表达式里出现的未知变量（按 x / y / z 顺序） */
    @JvmStatic
    fun unknowns(expr: String): List<String> {
        val found = LinkedHashSet<String>()
        var i = 0
        while (i < expr.length) {
            val c = expr[i]
            if (c == 'x' || c == 'y' || c == 'z') {
                // 只在“独立变量”位置算（前面不能是字母，如 exp / preAns 里的 x）
                val prev = if (i > 0) expr[i - 1] else ' '
                val next = if (i + 1 < expr.length) expr[i + 1] else ' '
                if (!prev.isLetter() && !next.isLetter()) found.add(c.toString())
            }
            i++
        }
        return VARS3.filter { found.contains(it) }
    }

    /**
     * 主行求解入口。expr 应当是自动补全右括号后的整串。
     */
    @JvmStatic
    fun solve(expr: String, mode: AngleMode): SolveResult {
        val parts = splitTop(expr, ",;")
        val eqs = ArrayList<Pair<String, String>>()
        for (p in parts) {
            if (p.isBlank()) continue
            val sides = splitTop(p, "=")
            if (sides.size != 2) throw CalcSyntaxError("语法错误")
            if (sides[1].isBlank()) throw CalcSyntaxError("语法错误")
            eqs.add(sides[0] to sides[1])
        }
        if (eqs.isEmpty()) throw CalcSyntaxError("语法错误")

        val vars = unknowns(expr)
        if (vars.isEmpty()) throw CalcMathError("等式里没有未知变量")
        return if (eqs.size == 1 && vars.size == 1) {
            solveSingle(vars[0], eqs[0].first, eqs[0].second, mode)
        } else {
            solveSystem(eqs, vars, mode)
        }
    }

    // -----------------------------------------------------------------------
    // 一元方程
    // -----------------------------------------------------------------------

    private fun solveSingle(varName: String, lhsS: String, rhsS: String, mode: AngleMode): SolveResult {
        val lhs = parse(lhsS)
        val rhs = parse(rhsS)
        val idx = mapOf("x" to 0, "y" to 1, "z" to 2)
        val v = idx.getValue(varName)

        val pl = PolyTools.from(lhs, idx, 3)
        val pr = PolyTools.from(rhs, idx, 3)
        val coeffs = if (pl != null && pr != null) {
            val diff = PolyTools.sub(3, pl, pr)
            PolyTools.univariate(diff, v)
        } else null

        if (coeffs != null) return solvePolynomial(varName, coeffs)
        return solveNumericSingle(varName, lhs, rhs, mode)
    }

    private fun solvePolynomial(varName: String, raw: List<Rational>): SolveResult {
        val c = trim(raw)
        if (c.isEmpty()) {
            return SolveResult(SolveKind.INFINITE, "无穷多解", note = "恒等式：任意 $varName 都满足")
        }
        if (c.size == 1) {
            return SolveResult(SolveKind.NONE, "无解", note = "方程化简后不含 $varName 且不为 0")
        }
        val deg = c.size - 1
        val roots: List<RootText>? = when (deg) {
            1 -> listOfRoots(c[0].div(c[1]).negate(), null)
            2 -> quadraticRoots(c[0], c[1], c[2])
            else -> rationalFactorRoots(c)
        }
        if (roots == null) {
            return SolveResult(SolveKind.ERROR, "求根失败", note = "高次多项式未能给出精确根")
        }
        if (roots.isEmpty()) return SolveResult(SolveKind.NONE, "无解")
        // 全部是有理根时按大小排序（好看且与参考机一致）
        val ordered = if (roots.all { it.insert != null }) {
            roots.sortedBy { it.insert!!.toDoubleOrNull() ?: 0.0 }
        } else roots
        val text = varName + " = " + ordered.joinToString(", ") { it.display }
        val items = ordered.map { SolveItem("$varName = ${it.display}", it.insert) }
        return SolveResult(SolveKind.SOLUTIONS, text, items)
    }

    private fun listOfRoots(r: Rational, exact: Rational?): List<RootText> =
        listOf(RootText(rt(r), rt(exact ?: r)))

    private fun rt(r: Rational): String = CalcEngine.formatRational(r, false)

    /**
     * 二次方程 ax² + bx + c = 0：
     * 判别式能开尽 → 精确分数；开不尽 → 精确根式（如 ±√2）；负 → 复根（如 ±i）。
     */
    private fun quadraticRoots(c0: Rational, c1: Rational, c2: Rational): List<RootText> {
        val disc = c1.times(c1).minus(c2.times(c0).times(Rational.of(4)))
        val den = c2.times(Rational.of(2))
        val p = c1.negate().div(den)
        if (disc.isZero) return listOfRoots(p, null)
        if (disc.signum > 0) {
            val s = disc.sqrtExact()
            if (s != null) {
                return listOf(
                    RootText(rt(p.plus(s.div(den))), rt(p.plus(s.div(den)))),
                    RootText(rt(p.minus(s.div(den))), rt(p.minus(s.div(den)))),
                )
            }
        }
        val neg = disc.signum < 0
        val mag = if (neg) disc.negate() else disc
        val simp = simplifySqrt(mag) ?: return numericQuadratic(p, mag, den, neg)
        val (outer, rad) = simp
        val q = outer.div(den)
        val radPart = radText(q, rad, neg)
        return if (p.isZero) {
            listOf(RootText("\u00B1$radPart", null))
        } else {
            listOf(RootText("${rt(p)} \u00B1 $radPart", null))
        }
    }

    /** 根式部分：√m 的形式（负数带 i），q 为系数 */
    private fun radText(q: Rational, m: Long, imaginary: Boolean): String {
        val i = if (imaginary) "i" else ""
        val core = if (m == 1L) "1" else "\u221A$m"
        val coef = if (m == 1L) {
            if (q == Rational.ONE) "i" else if (q == Rational.of(-1L)) "\u2212i" else rt(q) + "i"
        } else {
            when {
                q == Rational.ONE -> core + i
                q == Rational.of(-1L) -> "\u2212" + core + i
                else -> rt(q) + core + i
            }
        }
        return coef
    }

    /** 根式太大 / 太复杂 → 数值回退 */
    private fun numericQuadratic(p: Rational, mag: Rational, den: Rational, imaginary: Boolean): List<RootText> {
        val half = mag.toDouble().let { kotlin.math.sqrt(it) }.let { it / den.toDouble() }
        val pr = p.toDouble()
        return if (imaginary) {
            listOf(RootText("${CalcEngine.format(pr)} \u00B1 ${CalcEngine.format(half)}i", null))
        } else {
            listOf(
                RootText(CalcEngine.format(pr + half), CalcEngine.format(pr + half)),
                RootText(CalcEngine.format(pr - half), CalcEngine.format(pr - half)),
            )
        }
    }

    /** 三次及以上：先抽有理根（有理根定理）降阶，剩下二次用公式 */
    private fun rationalFactorRoots(coeffs: List<Rational>): List<RootText>? {
        var cur = coeffs
        val out = ArrayList<RootText>()
        var guard = 0
        while (cur.size - 1 > 2 && guard++ < 8) {
            val root = findRationalRoot(cur) ?: return null
            out.add(RootText(rt(root), rt(root)))
            val (q, rest) = syntheticDivide(cur, root)
            if (!q.isZero) return null
            cur = trim(rest)
            if (cur.size <= 1) break
        }
        val deg = cur.size - 1
        when (deg) {
            2 -> out.addAll(quadraticRoots(cur[0], cur[1], cur[2]))
            1 -> out.addAll(listOfRoots(cur[0].div(cur[1]).negate(), null))
            0 -> Unit
            else -> return null
        }
        return dedupe(out)
    }

    private fun findRationalRoot(coeffs: List<Rational>): Rational? {
        val a0 = coeffs[0]
        val an = coeffs[coeffs.size - 1]
        if (a0.isZero) return Rational.ZERO
        val ps = divisors(a0.num.abs())
        val qs = divisors(an.num.abs())
        for (p in ps) {
            for (q in qs) {
                for (sign in intArrayOf(1, -1)) {
                    val cand = Rational.of(BigInteger.valueOf(sign * p), BigInteger.valueOf(q))
                    if (evalPoly(coeffs, cand).isZero) return cand
                }
            }
        }
        return null
    }

    private fun divisors(v: BigInteger): List<Long> {
        if (v > BigInteger.valueOf(1_000_000L)) return emptyList()
        val n = v.toLong()
        val out = ArrayList<Long>()
        var d = 1L
        while (d * d <= n) {
            if (n % d == 0L) {
                out.add(d)
                if (d != n / d) out.add(n / d)
            }
            d++
        }
        return out
    }

    private fun evalPoly(c: List<Rational>, x: Rational): Rational {
        var acc = Rational.ZERO
        for (i in c.indices.reversed()) acc = acc.times(x).plus(c[i])
        return acc
    }

    /** 合成除法：返回 (余数, 商) */
    private fun syntheticDivide(c: List<Rational>, root: Rational): Pair<Rational, List<Rational>> {
        val n = c.size - 1
        val out = MutableList(n) { Rational.ZERO }
        var carry = c[n]
        out[n - 1] = carry
        for (i in n - 1 downTo 0) {
            carry = c[i].plus(carry.times(root))
            if (i > 0) out[i - 1] = carry
        }
        return carry to out
    }

    private fun dedupe(list: List<RootText>): List<RootText> {
        val seen = LinkedHashSet<String>()
        val out = ArrayList<RootText>()
        for (r in list) if (seen.add(r.display)) out.add(r)
        return out
    }

    // -----------------------------------------------------------------------
    // 一元超越方程：多初值扫描
    // -----------------------------------------------------------------------

    private fun solveNumericSingle(varName: String, lhs: Node, rhs: Node, mode: AngleMode): SolveResult {
        val (lo, hi) = scanRange(mode)
        val roots = numericRoots(varName, lhs, rhs, lo, hi, 4000, mode)
        if (roots.isEmpty()) {
            return SolveResult(
                SolveKind.NONE, "无解",
                note = "在 [${CalcEngine.format(lo)}, ${CalcEngine.format(hi)}] 内未找到实根",
            )
        }
        val cap = 12
        val shown = roots.take(cap)
        val suffix = angleSuffix(mode)
        val texts = shown.map { RootText(CalcEngine.format(it) + suffix, CalcEngine.format(it)) }
        val more = if (roots.size > cap) " …" else ""
        val text = varName + " = " + texts.joinToString(", ") { it.display } + more
        val items = texts.map { SolveItem("$varName = ${it.display}", it.insert) }
        val note = "数值解：在 [${CalcEngine.format(lo)}, ${CalcEngine.format(hi)}] 内自动扫描到 ${roots.size} 个根"
        return SolveResult(SolveKind.SOLUTIONS, text, items, note)
    }

    private fun scanRange(mode: AngleMode): Pair<Double, Double> {
        val period = when (mode) {
            AngleMode.DEG -> 360.0
            AngleMode.GRAD -> 400.0
            AngleMode.RAD -> 2.0 * Math.PI
        }
        return -period to 3.0 * period
    }

    private fun angleSuffix(mode: AngleMode): String = when (mode) {
        AngleMode.DEG -> "\u00B0"
        AngleMode.GRAD -> " grad"
        AngleMode.RAD -> ""
    }

    private fun numericRoots(
        varName: String,
        lhs: Node,
        rhs: Node,
        lo: Double,
        hi: Double,
        samples: Int,
        mode: AngleMode,
    ): List<Double> {
        val f = fun(t: Double): Double? {
            val m = mapOf(varName to t)
            return try {
                val a = scalarEval(lhs, m, mode)
                val b = scalarEval(rhs, m, mode)
                val r = a - b
                if (r.isNaN() || r.isInfinite()) null else r
            } catch (_: Exception) {
                null
            }
        }
        val out = ArrayList<Double>()
        var prevT = lo
        var prevF = f(lo)
        for (i in 1..samples) {
            val t = lo + (hi - lo) * i / samples
            val ft = f(t)
            if (prevF != null && ft != null) {
                if (prevF == 0.0) addRoot(out, prevT)
                if ((prevF > 0.0) != (ft > 0.0)) {
                    val r = bisect(f, prevT, t)
                    if (r != null) addRoot(out, r)
                } else if (ft == 0.0) {
                    addRoot(out, t)
                }
            }
            prevT = t
            prevF = ft
        }
        return out.sorted()
    }

    private fun addRoot(out: MutableList<Double>, r: Double) {
        for (e in out) {
            val scale = 1.0 + maxOf(abs(e), abs(r))
            if (abs(e - r) <= 1e-7 * scale) return
        }
        out.add(r)
    }

    private fun bisect(f: (Double) -> Double?, lo0: Double, hi0: Double): Double? {
        var lo = lo0
        var hi = hi0
        var flo = f(lo) ?: return null
        var mid = (lo + hi) / 2.0
        var i = 0
        while (i < 200) {
            i++
            mid = (lo + hi) / 2.0
            val fm = f(mid) ?: return null
            if (abs(fm) < 1e-15 || (hi - lo) < 1e-14 * (1.0 + abs(mid))) break
            if ((flo > 0.0) != (fm > 0.0)) hi = mid else { lo = mid; flo = fm }
        }
        return mid
    }

    private fun scalarEval(node: Node, vars: Map<String, Double>, mode: AngleMode): Double =
        ValueEvaluator(
            mode, Value.Floating(0.0), Value.Floating(0.0), preAns = 0.0, vars = vars,
        ).eval(node).toDouble()

    // -----------------------------------------------------------------------
    // 方程组
    // -----------------------------------------------------------------------

    private fun solveSystem(
        eqs: List<Pair<String, String>>,
        varNames: List<String>,
        mode: AngleMode,
    ): SolveResult {
        val idx = mapOf("x" to 0, "y" to 1, "z" to 2)
        val polys = ArrayList<Map<Mono, Rational>>()
        for ((l, r) in eqs) {
            val pl = PolyTools.from(parse(l), idx, 3)
            val pr = PolyTools.from(parse(r), idx, 3)
            if (pl == null || pr == null) return numericSystem(eqs, varNames, mode)
            polys.add(PolyTools.sub(3, pl, pr))
        }
        val linear = polys.all { p -> p.keys.all { k -> k.sum() <= 1 } }
        if (!linear) return numericSystem(eqs, varNames, mode)

        // 构造增广矩阵（未知量按 varNames 顺序）
        val k = varNames.size
        val m = Array(eqs.size) { arrayOfNulls<Rational>(k + 1) }
        for (i in polys.indices) {
            val p = polys[i]
            val constTerm = p[listOf(0, 0, 0)] ?: Rational.ZERO
            for (j in 0 until k) {
                val v = idx.getValue(varNames[j])
                val mono = MutableList(3) { 0 }.also { it[v] = 1 }
                m[i][j] = p[mono] ?: Rational.ZERO
            }
            m[i][k] = constTerm.negate()
        }
        return gaussian(m, varNames)
    }

    private fun gaussian(m: Array<Array<Rational?>>, varNames: List<String>): SolveResult {
        val rows = m.size
        val cols = varNames.size
        val hasSol = BooleanArray(rows) { false }
        var rank = 0
        val pivotCol = IntArray(rows) { -1 }
        for (col in 0 until cols) {
            var sel = -1
            for (r in rank until rows) {
                val v = m[r][col]
                if (v != null && !v.isZero) { sel = r; break }
            }
            if (sel < 0) continue
            val tmp = m[rank]; m[rank] = m[sel]; m[sel] = tmp
            val pv = m[rank][col]!!
            for (c in 0..cols) {
                m[rank][c] = m[rank][c]!!.div(pv)
            }
            for (r in 0 until rows) {
                if (r == rank) continue
                val factor = m[r][col]!!
                if (factor.isZero) continue
                for (c in 0..cols) {
                    m[r][c] = m[r][c]!!.minus(factor.times(m[rank][c]!!))
                }
            }
            pivotCol[rank] = col
            hasSol[rank] = true
            rank++
        }
        // 检查不一致行：0 = 非零
        for (r in rank until rows) {
            val b = m[r][cols]!!
            if (!b.isZero) return SolveResult(SolveKind.NONE, "无解", note = "方程组不相容")
        }
        if (rank < cols) {
            return SolveResult(
                SolveKind.INFINITE, "无穷多解",
                note = "独立方程数（$rank）少于未知量数（$cols），存在自由变量",
            )
        }
        val values = arrayOfNulls<Rational>(cols)
        for (r in 0 until rank) {
            val col = pivotCol[r]
            if (col < 0) continue
            values[col] = m[r][cols]
        }
        val parts = ArrayList<String>()
        val items = ArrayList<SolveItem>()
        for (j in 0 until cols) {
            val v = values[j] ?: Rational.ZERO
            parts.add("${varNames[j]} = ${rt(v)}")
            items.add(SolveItem("${varNames[j]} = ${rt(v)}", rt(v)))
        }
        return SolveResult(SolveKind.SOLUTIONS, parts.joinToString(", "), items)
    }

    /** 非线性方程组：数值牛顿（多起点） */
    private fun numericSystem(
        eqs: List<Pair<String, String>>,
        varNames: List<String>,
        mode: AngleMode,
    ): SolveResult {
        val n = varNames.size
        if (n == 0 || n > 3) return SolveResult(SolveKind.ERROR, "求根失败", note = "未知量太多")
        val nodes = eqs.map { (l, r) -> parse(l) to parse(r) }
        val roots = ArrayList<DoubleArray>()
        val starts = ArrayList<DoubleArray>()
        starts.add(DoubleArray(n))
        for (j in 0 until n) {
            for (s in doubleArrayOf(1.0, -1.0, 2.0, -2.0)) {
                val p = DoubleArray(n)
                p[j] = s
                starts.add(p)
            }
        }
        for (st in starts) {
            val r = newtonSystem(nodes, varNames, st, mode) ?: continue
            if (roots.none { same(it, r) }) roots.add(r)
        }
        if (roots.isEmpty()) {
            return SolveResult(SolveKind.ERROR, "求根失败", note = "非线性方程组未收敛到解")
        }
        val parts = ArrayList<String>()
        val items = ArrayList<SolveItem>()
        for (j in varNames.indices) {
            val vals = roots.map { CalcEngine.format(it[j]) }
            val text = vals.joinToString(", ")
            parts.add("${varNames[j]} = $text")
            if (roots.size == 1) items.add(SolveItem("${varNames[j]} = $text", vals[0]))
        }
        return SolveResult(
            SolveKind.SOLUTIONS, parts.joinToString(", "), items,
            note = if (roots.size > 1) "找到 ${roots.size} 组数值解（略）" else "数值解",
        )
    }

    private fun same(a: DoubleArray, b: DoubleArray): Boolean =
        a.indices.all { abs(a[it] - b[it]) <= 1e-6 * (1.0 + abs(a[it])) }

    private fun newtonSystem(
        nodes: List<Pair<Node, Node>>,
        varNames: List<String>,
        start: DoubleArray,
        mode: AngleMode,
    ): DoubleArray? {
        val n = varNames.size
        val x = start.copyOf()
        var iter = 0
        while (iter < 200) {
            iter++
            val f = DoubleArray(n) { evalDiff(nodes[it], varNames, x, mode) ?: return null }
            if (f.indices.all { abs(f[it]) < 1e-13 }) return x
            // 数值 Jacobian
            val jac = Array(n) { DoubleArray(n) }
            for (c in 0 until n) {
                val h = 1e-7 * maxOf(1.0, abs(x[c]))
                val xp = x.copyOf()
                xp[c] += h
                for (r in 0 until n) {
                    val fr = evalDiff(nodes[r], varNames, xp, mode) ?: return null
                    jac[r][c] = (fr - f[r]) / h
                }
            }
            val dx = solveLinear(jac, f) ?: return null
            var converged = true
            for (c in 0 until n) {
                x[c] -= dx[c]
                if (abs(dx[c]) > 1e-10 * (1.0 + abs(x[c]))) converged = false
            }
            if (converged) return x
        }
        return if (DoubleArray(n) { evalDiff(nodes[it], varNames, x, mode) ?: Double.MAX_VALUE }
                .all { abs(it) < 1e-8 }) x else null
    }

    private fun evalDiff(eq: Pair<Node, Node>, varNames: List<String>, x: DoubleArray, mode: AngleMode): Double? = try {
        val map = HashMap<String, Double>()
        for (i in varNames.indices) map[varNames[i]] = x[i]
        val f = ValueEvaluator(
            mode, Value.Floating(0.0), Value.Floating(0.0), preAns = 0.0, vars = map,
        )
        val v = f.eval(eq.first).toDouble() - f.eval(eq.second).toDouble()
        if (v.isNaN() || v.isInfinite()) null else v
    } catch (_: Exception) {
        null
    }

    /** 高斯消元解 J·dx = f（浮点） */
    private fun solveLinear(a: Array<DoubleArray>, b: DoubleArray): DoubleArray? {
        val n = b.size
        val m = Array(n) { r -> DoubleArray(n + 1) { c -> if (c < n) a[r][c] else b[r] } }
        for (col in 0 until n) {
            var sel = col
            for (r in col + 1 until n) if (abs(m[r][col]) > abs(m[sel][col])) sel = r
            if (abs(m[sel][col]) < 1e-14) return null
            val t = m[col]; m[col] = m[sel]; m[sel] = t
            for (r in col + 1 until n) {
                val f = m[r][col] / m[col][col]
                for (c in col..n) m[r][c] -= f * m[col][c]
            }
        }
        val x = DoubleArray(n)
        for (r in n - 1 downTo 0) {
            var s = m[r][n]
            for (c in r + 1 until n) s -= m[r][c] * x[c]
            x[r] = s / m[r][r]
        }
        return x
    }

    // -----------------------------------------------------------------------
    // 工具
    // -----------------------------------------------------------------------

    private fun parse(s: String): Node = Parser(Lexer(s.trim()).lex()).parse()

    private fun trim(c: List<Rational>): List<Rational> {
        var n = c.size
        while (n > 0 && c[n - 1].isZero) n--
        return c.subList(0, n).toList()
    }

    /** 按分隔符在「括号深度 0」处切分 */
    private fun splitTop(s: String, seps: String): List<String> {
        val out = ArrayList<String>()
        var depth = 0
        val sb = StringBuilder()
        for (ch in s) {
            when {
                ch == '(' -> { depth++; sb.append(ch) }
                ch == ')' -> { depth--; sb.append(ch) }
                depth == 0 && seps.indexOf(ch) >= 0 -> { out.add(sb.toString()); sb.clear() }
                else -> sb.append(ch)
            }
        }
        out.add(sb.toString())
        return out.map { it.trim() }
    }

    /** 把 √(a/b) 拆成 (系数, 平方因子外的整数)：√(a/b) = 系数·√m */
    private fun simplifySqrt(r: Rational): Pair<Rational, Long>? {
        val a = r.num.abs()
        val b = r.den
        if (b.bitLength() > 62) return null
        val k = a.multiply(b)
        if (k.bitLength() > 62) return null
        var m = k.toLong()
        var s = 1L
        var d = 2L
        while (d * d <= m && d <= 1_000_000L) {
            while (m % (d * d) == 0L) {
                m /= d * d
                s *= d
            }
            d++
        }
        if (d * d <= m) return null
        if (s > 1_000_000_000L) return null
        return Rational.of(s, b.toLong()) to m
    }
}

// ---------------------------------------------------------------------------
// 多项式（多变量，指数向量为键；系数走精确有理数）
// ---------------------------------------------------------------------------

private object PolyTools {

    fun from(n: Node, varIndex: Map<String, Int>, nVars: Int): Map<Mono, Rational>? {
        return when (n) {
        is Node.Num -> {
            val lit = n.lit ?: return null
            val r = Rational.fromDecimal(lit) ?: return null
            mapOf(List(nVars) { 0 } to r)
        }
        is Node.XRef -> mono(varIndex["x"], nVars)
        is Node.YRef -> mono(varIndex["y"], nVars)
        is Node.ZRef -> mono(varIndex["z"], nVars)
        is Node.Neg -> from(n.a, varIndex, nVars)?.let { neg(it) }
        is Node.Add -> {
            val a = from(n.a, varIndex, nVars) ?: return null
            val b = from(n.b, varIndex, nVars) ?: return null
            add(a, b)
        }
        is Node.Sub -> {
            val a = from(n.a, varIndex, nVars) ?: return null
            val b = from(n.b, varIndex, nVars) ?: return null
            add(a, neg(b))
        }
        is Node.Mul -> {
            val a = from(n.a, varIndex, nVars) ?: return null
            val b = from(n.b, varIndex, nVars) ?: return null
            mul(a, b)
        }
        is Node.Div -> {
            val a = from(n.a, varIndex, nVars) ?: return null
            val b = from(n.b, varIndex, nVars) ?: return null
            val c = constantOf(b) ?: return null
            if (c.isZero) return null
            divBy(a, c)
        }
        is Node.Pow -> {
            val a = from(n.a, varIndex, nVars) ?: return null
            val e = (n.b as? Node.Num)?.lit?.toIntOrNull() ?: return null
            if (e < 0 || e > 12) return null
            powInt(a, e)
        }
        else -> null
        }
    }

    private fun mono(i: Int?, nVars: Int): Map<Mono, Rational>? {
        if (i == null) return null
        val m = MutableList(nVars) { 0 }
        m[i] = 1
        return mapOf(m to Rational.ONE)
    }

    private fun constantOf(p: Map<Mono, Rational>): Rational? {
        if (p.isEmpty()) return Rational.ZERO
        if (p.size != 1) return null
        val (k, v) = p.entries.first()
        return if (k.all { it == 0 }) v else null
    }

    fun add(a: Map<Mono, Rational>, b: Map<Mono, Rational>): Map<Mono, Rational> {
        val out = LinkedHashMap(a)
        for ((k, v) in b) {
            val cur = out[k]
            val nv = if (cur == null) v else cur.plus(v)
            if (nv.isZero) out.remove(k) else out[k] = nv
        }
        return out
    }

    fun sub(nVars: Int, a: Map<Mono, Rational>, b: Map<Mono, Rational>): Map<Mono, Rational> =
        add(a, neg(b))

    fun neg(a: Map<Mono, Rational>): Map<Mono, Rational> =
        a.mapValues { it.value.negate() }

    fun mul(a: Map<Mono, Rational>, b: Map<Mono, Rational>): Map<Mono, Rational> {
        val out = LinkedHashMap<Mono, Rational>()
        for ((ka, va) in a) {
            for ((kb, vb) in b) {
                val key = ka.indices.map { ka[it] + kb[it] }
                val v = va.times(vb)
                val cur = out[key]
                val nv = if (cur == null) v else cur.plus(v)
                if (nv.isZero) out.remove(key) else out[key] = nv
            }
        }
        return out
    }

    private fun divBy(a: Map<Mono, Rational>, c: Rational): Map<Mono, Rational> =
        a.mapValues { it.value.div(c) }

    private fun powInt(a: Map<Mono, Rational>, e: Int): Map<Mono, Rational> {
        var acc = mapOf(List(a.keys.firstOrNull()?.size ?: 0) { 0 } to Rational.ONE)
        var base = a
        var n = e
        while (n > 0) {
            if (n and 1 == 1) acc = mul(acc, base)
            n = n shr 1
            if (n > 0) base = mul(base, base)
        }
        return acc
    }

    /** 一元化：取出某变量的系数向量（index = 该变量指数） */
    fun univariate(p: Map<Mono, Rational>, varIdx: Int): List<Rational>? {
        var maxDeg = 0
        for (k in p.keys) {
            if (k.indices.any { it != varIdx && k[it] != 0 }) return null
            if (k[varIdx] > maxDeg) maxDeg = k[varIdx]
        }
        val out = MutableList(maxDeg + 1) { Rational.ZERO }
        for ((k, v) in p) out[k[varIdx]] = v
        return out
    }
}
