import io

p = r"C:\Users\Administrator\.openclaw\workspace\projects\casio-calc-android\app\src\main\java\io\paimon\fx991\engine\Surd.kt"
s = io.open(p, encoding="utf-8").read()
i = s.index("    /** 显示")
tail = '''    /** 显示：3√2 / -√2 / √2/2 / 4 */
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
'''
s = s[:i] + tail
io.open(p, "w", encoding="utf-8", newline="").write(s)
print("Surd.kt format fixed")
