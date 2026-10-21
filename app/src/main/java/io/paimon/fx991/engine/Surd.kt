package io.paimon.fx991.engine

/**
 * 根式（surd）精确算术：coef · √radicand，radicand 为无平方因子正整数。
 *
 * 对应 Sumi 的 core/value/surd_ops.dart 思路（按设计重写，非逐行搬运）：
 *   - √8        → 2√2            （提出平方因子）
 *   - √2 + √8   → 3√2            （同类根合并）
 *   - √2 · √8   → 4              （根号内相乘后化简）
 *   - 1/√2      → √2/2           （分母有理化）
 *   - √(a/b)    → √a/√b          （分数被开方）
 *
 * 全部用 Rational 承载系数，保持精确；radicand=1 表示已退化成有理数。
 */
object Surd {

    /** coef * √radicand */
    data class Radical(val coef: Rational, val radicand: Int) {
        val isRational: Boolean get() = radicand == 1
        fun toRational(): Rational? = if (isRational) coef else null
        fun toDouble(): Double = coef.toDouble() * Math.sqrt(radicand.toDouble())
    }

    /** 把整数分解成 平方因子 * 无平方因子 */
    fun squareFree(n: Int): Pair<Int, Int> {
        require(n > 0)
        var rest = n
        var outside = 1
        var p = 2
        while (p * p <= rest) {
            while (rest % (p * p) == 0) {
                rest /= (p * p)
                outside *= p
            }
            p++
        }
        return outside to rest
    }

    /** 化简 √n（n ≥ 0）；n 为完全平方时返回 radicand=1 */
    fun sqrt(n: Long): Radical {
        require(n >= 0) { "负数没有实平方根" }
        return sqrt(Rational.of(n))
    }

    /** 化简 √r（r ≥ 0 的有理数）：√(a/b) = √(a·b)/b */
    fun sqrt(r: Rational): Radical {
        if (r.signum < 0) throw CalcMathError("数学错误")
        if (r.isZero) return Radical(Rational.ZERO, 1)
        // √(a/b) = √(a·b) / b  → 先化成整数被开方数
        val a = r.num
        val b = r.den
        val under = a * b           // BigInteger
        if (under.bitLength() > 62) {
            // 规模过大：交给浮点轨
            return Radical(Rational.ZERO, 1)
        }
        val n = under.toLong()
        val (outside, rad) = squareFreeLong(n)
        val coef = Rational.of(outside.toBigInteger(), b)
        // 再对 radicand 抽一次（rad 已无平方因子，这里只需处理 b 的平方部分已并入 outside）
        return Radical(coef, rad.toInt())
    }

    private fun squareFreeLong(n: Long): Pair<Long, Long> {
        var rest = n
        var outside = 1L
        var p = 2L
        while (p * p <= rest) {
            while (rest % (p * p) == 0L) {
                rest /= (p * p)
                outside *= p
            }
            p++
        }
        return outside to rest
    }

    /** 同类根相加；不同类返回 null（调用方落回浮点或保留和式） */
    fun add(a: Radical, b: Radical): Radical? {
        if (a.radicand != b.radicand) return null
        return normalize(Radical(a.coef.plus(b.coef), a.radicand))
    }

    fun sub(a: Radical, b: Radical): Radical? {
        if (a.radicand != b.radicand) return null
        return normalize(Radical(a.coef.minus(b.coef), a.radicand))
    }

    fun mul(a: Radical, b: Radical): Radical {
        val under = a.radicand.toLong() * b.radicand.toLong()
        val (outside, rad) = squareFreeLong(under)
        val coef = a.coef.times(b.coef).times(Rational.of(outside))
        return normalize(Radical(coef, rad.toInt()))
    }

    /** a / b：b 必须非零；带分母有理化 */
    fun div(a: Radical, b: Radical): Radical {
        if (b.coef.isZero) throw CalcMathError("数学错误")
        // a/b = (a.coef/b.coef) * √(a.rad/b.rad)
        // 先把 1/√b 有理化：1/√q = √q/q
        val invCoef = a.coef.div(b.coef)
        val under = a.radicand.toLong() * b.radicand.toLong()
        val (outside, rad) = squareFreeLong(under)
        // √(a/b) 展开后分母再乘 b.radicand
        val coef = invCoef.div(Rational.of(b.radicand.toLong())).times(Rational.of(outside))
        return normalize(Radical(coef, rad.toInt()))
    }

    /** 系数为 0 或 radicand=1 时退化成有理数 */
    fun normalize(r: Radical): Radical {
        if (r.coef.isZero) return Radical(Rational.ZERO, 1)
        if (r.radicand == 1) return Radical(r.coef, 1)
        return r
    }

    /** 显示：3√2 / -√2 / √2/2 / 4 */
    fun format(r: Radical): String {
        val n = normalize(r)
        if (n.isRational) return n.coef.toString()
        val absCoef = n.coef.abs()
        val sign = if (n.coef.signum < 0) "-" else ""
        val numPart =
            if (absCoef.num == java.math.BigInteger.ONE) "$sign√${n.radicand}"
            else "$sign${absCoef.num}√${n.radicand}"
        return if (absCoef.isInteger) numPart else "$numPart/${absCoef.den}"
    }
}
