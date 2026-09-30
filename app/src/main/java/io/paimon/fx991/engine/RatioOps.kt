package io.paimon.fx991.engine

// ---------------------------------------------------------------------------
// 批次 D：比例 RATIO
//
// 两种形式：
//   a:b = c:x  →  x = b·c ÷ a
//   a:b = x:d  →  x = a·d ÷ b
// 输入小数当场转有理数，能精确就给精确分数（顺带附小数近似）。
// ---------------------------------------------------------------------------

object RatioOps {

    /** 比例形式：CX = a:b = c:x；XD = a:b = x:d */
    enum class Form(val title: String) {
        CX("a:b = c:x"),
        XD("a:b = x:d"),
    }

    /** 解析系数输入（小数 / 整数 → 精确有理数） */
    fun parseCoefficient(s: String, what: String): Rational {
        val t = s.trim().replace('−', '-')
        if (t.isEmpty()) throw NumericError("请输入 $what")
        return Rational.fromDecimal(t) ?: throw NumericError("$what 不是合法数字")
    }

    /**
     * 解比例。other 在 CX 形式下是 c、在 XD 形式下是 d。
     * 分母（CX 的 a / XD 的 b）为 0 时报 NumericError。
     */
    fun solve(form: Form, a: Rational, b: Rational, other: Rational): Rational = when (form) {
        Form.CX -> {
            if (a.isZero) throw NumericError("a 不能为 0（a:b = c:x 中 a 作除数）")
            b.times(other).div(a)
        }
        Form.XD -> {
            if (b.isZero) throw NumericError("b 不能为 0（a:b = x:d 中 b 作除数）")
            a.times(other).div(b)
        }
    }

    /** 结果文本：整数给整数；分数给「分数（≈ 小数）」 */
    fun textOf(x: Rational): String {
        val exact = CalcEngine.formatRational(x, false)
        if (x.isInteger) return exact
        val dec = CalcEngine.format(x.toDouble(), 10, null)
        return "$exact（≈ $dec）"
    }
}
