package io.paimon.fx991.engine

import java.math.BigDecimal
import java.math.BigInteger
import java.math.MathContext
import java.math.RoundingMode
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.acosh
import kotlin.math.asin
import kotlin.math.asinh
import kotlin.math.atan
import kotlin.math.atanh
import kotlin.math.cos
import kotlin.math.cosh
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sinh
import kotlin.math.sqrt
import kotlin.math.tan
import kotlin.math.tanh

/** 角度制（DEG 度 / RAD 弧度 / GRAD 百分度，400 grad = 360°） */
enum class AngleMode { DEG, RAD, GRAD }

/** 数字显示模式（普通 / 科学记数 / 工程记数） */
enum class NumberNotation(val shortLabel: String) {
    NORM("NORM"),
    SCI("SCI"),
    ENG("ENG");

    fun next(): NumberNotation = when (this) {
        NORM -> SCI
        SCI -> ENG
        ENG -> NORM
    }
}

/** 角度制标签 */
val AngleMode.label: String
    get() = when (this) {
        AngleMode.DEG -> "DEG"
        AngleMode.RAD -> "RAD"
        AngleMode.GRAD -> "GRAD"
    }

/** 语法错误 */
class CalcSyntaxError(message: String) : Exception(message)

/** 数学错误（定义域、除零、溢出等） */
class CalcMathError(message: String) : Exception(message)

// ---------------------------------------------------------------------------
// 词法
// ---------------------------------------------------------------------------

private enum class Tok {
    NUM, IDENT, PLUS, MINUS, MUL, DIV, POW,
    FACT, PCT, SQ2, CUBE, RECIP, LP, RP, SQRT, COMMA, END
}

private class Token(val t: Tok, val s: String)

private const val CH_MINUS = '\u2212'   // −
private const val CH_MUL = '\u00D7'     // ×
private const val CH_DIV = '\u00F7'     // ÷
private const val CH_SQ2 = '\u00B2'     // ²
private const val CH_CUBE = '\u00B3'    // ³
private const val CH_SUP_MINUS = '\u207B' // ⁻
private const val CH_SUP_ONE = '\u00B9'   // ¹
private const val CH_SQRT = '\u221A'    // √

private class Lexer(private val src: String) {
    private var i = 0

    fun lex(): List<Token> {
        val out = ArrayList<Token>()
        while (i < src.length) {
            val c = src[i]
            when {
                c.isWhitespace() -> i++
                c.isDigit() || c == '.' -> out.add(number())
                c.isLetter() -> out.add(identifier())
                c == '+' -> { out.add(Token(Tok.PLUS, "+")); i++ }
                c == '-' || c == CH_MINUS -> { out.add(Token(Tok.MINUS, "-")); i++ }
                c == '*' || c == CH_MUL -> { out.add(Token(Tok.MUL, "\u00D7")); i++ }
                c == '/' || c == CH_DIV -> { out.add(Token(Tok.DIV, "\u00F7")); i++ }
                c == '^' -> { out.add(Token(Tok.POW, "^")); i++ }
                c == '!' -> { out.add(Token(Tok.FACT, "!")); i++ }
                c == '%' -> { out.add(Token(Tok.PCT, "%")); i++ }
                c == '(' -> { out.add(Token(Tok.LP, "(")); i++ }
                c == ')' -> { out.add(Token(Tok.RP, ")")); i++ }
                c == ',' -> { out.add(Token(Tok.COMMA, ",")); i++ }
                c == CH_SQRT -> { out.add(Token(Tok.SQRT, "\u221A")); i++ }
                c == CH_SQ2 -> { out.add(Token(Tok.SQ2, "\u00B2")); i++ }
                c == CH_CUBE -> { out.add(Token(Tok.CUBE, "\u00B3")); i++ }
                c == CH_SUP_MINUS -> {
                    if (i + 1 < src.length && src[i + 1] == CH_SUP_ONE) {
                        out.add(Token(Tok.RECIP, "\u207B\u00B9"))
                        i += 2
                    } else {
                        throw CalcSyntaxError("语法错误")
                    }
                }
                else -> throw CalcSyntaxError("语法错误")
            }
        }
        out.add(Token(Tok.END, ""))
        return out
    }

    private fun number(): Token {
        val start = i
        var dot = false
        while (i < src.length) {
            val c = src[i]
            if (c.isDigit()) {
                i++
            } else if (c == '.' && !dot) {
                dot = true
                i++
            } else {
                break
            }
        }
        return Token(Tok.NUM, src.substring(start, i))
    }

    private fun identifier(): Token {
        val start = i
        while (i < src.length && src[i].isLetter()) i++
        // 允许 sin⁻¹ / cos⁻¹ / tan⁻¹ 这类带下标后缀的函数名
        if (i + 1 < src.length && src[i] == CH_SUP_MINUS && src[i + 1] == CH_SUP_ONE) {
            i += 2
        }
        return Token(Tok.IDENT, src.substring(start, i))
    }
}

// ---------------------------------------------------------------------------
// 语法树
// ---------------------------------------------------------------------------

private sealed class Node {
    /** lit 保留字面量原文，供精确轨直接转有理数；π/e 等无常量原文 → null */
    class Num(val v: Double, val lit: String? = null) : Node()
    data object AnsRef : Node()
    data object MemRef : Node()
    data object XRef : Node()
    data object YRef : Node()
    data object ZRef : Node()

    // ---- 批次 B ----
    /** 上上次结果 PreAns */
    data object PreAnsRef : Node()
    /** STO 变量：A–F（x / y 走 XRef / YRef） */
    class VarRef(val name: String) : Node()
    class Neg(val a: Node) : Node()
    class Add(val a: Node, val b: Node) : Node()
    class Sub(val a: Node, val b: Node) : Node()
    class Mul(val a: Node, val b: Node) : Node()
    class Div(val a: Node, val b: Node) : Node()
    class Pow(val a: Node, val b: Node) : Node()
    class Fact(val a: Node) : Node()
    class Pct(val a: Node) : Node()
    class Recip(val a: Node) : Node()
    class Sqrt(val a: Node) : Node()
    class Fn(val name: String, val a: Node) : Node()

    /** 双参数函数：logb(底, 真数) / root(次数, 被开方数) / npr(n, r) / ncr(n, r) */
    class Fn2(val name: String, val a: Node, val b: Node) : Node()
}

private val FUNC_NAMES = setOf(
    "sin", "cos", "tan", "asin", "acos", "atan", "log", "ln", "exp", "sqrt",
    "sinh", "cosh", "tanh", "asinh", "acosh", "atanh", "cbrt", "abs"
)

/** STO 变量名（A–F），大小写不敏感只在单字母且为大写时生效 */
private val VAR_NAMES = setOf("A", "B", "C", "D", "E", "F")

private val FUNC2_NAMES = setOf("logb", "root", "npr", "ncr")

// ---------------------------------------------------------------------------
// 语法制导翻译（递归下降）
//
//   expr    -> term (('+'|'-') term)*              // 加减，% 参与“相对百分比”
//   term    -> factor (('*'|'/') factor)*          // 乘除
//   factor  -> unary (unary)*                      // 隐式乘法（比 ÷ 更紧，同参考机）
//   unary   -> ('-'|'+') unary | power
//   power   -> postfix ('^' unary)?                // 右结合：2^3^2 = 512；2^-3 合法
//   postfix -> primary ('!'|'%'|'²'|'³'|'⁻¹')*
//   primary -> NUM | CONST | FUNC '(' expr ')' | '√' unary | '(' expr ')'
// ---------------------------------------------------------------------------

private class Parser(private val ts: List<Token>) {
    private var i = 0

    private fun cur(): Token = ts[i]

    private fun eat(t: Tok): Boolean {
        if (ts[i].t == t) {
            i++
            return true
        }
        return false
    }

    fun parse(): Node {
        if (cur().t == Tok.END) throw CalcSyntaxError("语法错误")
        val n = expr()
        if (cur().t != Tok.END) throw CalcSyntaxError("语法错误")
        return n
    }

    private fun expr(): Node {
        var left = term()
        while (true) {
            when {
                eat(Tok.PLUS) -> {
                    val r = term()
                    left = if (r is Node.Pct) {
                        // 参考机语义：A + B% = A + A*B/100
                        Node.Add(left, Node.Div(Node.Mul(left, r.a), Node.Num(100.0, "100")))
                    } else {
                        Node.Add(left, r)
                    }
                }
                eat(Tok.MINUS) -> {
                    val r = term()
                    left = if (r is Node.Pct) {
                        Node.Sub(left, Node.Div(Node.Mul(left, r.a), Node.Num(100.0, "100")))
                    } else {
                        Node.Sub(left, r)
                    }
                }
                else -> return left
            }
        }
    }

    private fun term(): Node {
        var left = factor()
        while (true) {
            when {
                eat(Tok.MUL) -> left = Node.Mul(left, factor())
                eat(Tok.DIV) -> left = Node.Div(left, factor())
                else -> return left
            }
        }
    }

    private fun factor(): Node {
        var left = unary()
        while (startsPrimary()) {
            left = Node.Mul(left, unary())
        }
        return left
    }

    private fun startsPrimary(): Boolean = when (ts[i].t) {
        Tok.NUM, Tok.IDENT, Tok.LP, Tok.SQRT -> true
        else -> false
    }

    private fun unary(): Node {
        if (eat(Tok.MINUS)) return Node.Neg(unary())
        if (eat(Tok.PLUS)) return unary()
        return power()
    }

    private fun power(): Node {
        val base = postfix()
        if (eat(Tok.POW)) return Node.Pow(base, unary())
        return base
    }

    private fun postfix(): Node {
        var n = primary()
        while (true) {
            when {
                eat(Tok.FACT) -> n = Node.Fact(n)
                eat(Tok.PCT) -> n = Node.Pct(n)
                eat(Tok.SQ2) -> n = Node.Pow(n, Node.Num(2.0, "2"))
                eat(Tok.CUBE) -> n = Node.Pow(n, Node.Num(3.0, "3"))
                eat(Tok.RECIP) -> n = Node.Recip(n)
                else -> return n
            }
        }
    }

    private fun primary(): Node {
        val t = cur()
        return when (t.t) {
            Tok.NUM -> {
                i++
                Node.Num(t.s.toDoubleOrNull() ?: throw CalcSyntaxError("语法错误"), t.s)
            }
            Tok.LP -> paren()
            Tok.SQRT -> {
                i++
                Node.Sqrt(unary())
            }
            Tok.IDENT -> {
                i++
                ident(t.s)
            }
            else -> throw CalcSyntaxError("语法错误")
        }
    }

    private fun paren(): Node {
        eat(Tok.LP)
        val e = expr()
        if (!eat(Tok.RP)) throw CalcSyntaxError("缺少右括号")
        return e
    }

    private fun ident(text: String): Node {
        when (text) {
            "\u03C0", "pi", "PI" -> return Node.Num(Math.PI)
            "e" -> return Node.Num(Math.E)
            "Ans" -> return Node.AnsRef
            "PreAns" -> return Node.PreAnsRef
            "M" -> return Node.MemRef
            "x" -> return Node.XRef
            "y" -> return Node.YRef
            "z" -> return Node.ZRef
        }
        // STO 变量：单字母 A–F
        if (text.length == 1 && text in VAR_NAMES) return Node.VarRef(text)
        val name = when (text) {
            "sin\u207B\u00B9", "asin" -> "asin"
            "cos\u207B\u00B9", "acos" -> "acos"
            "tan\u207B\u00B9", "atan" -> "atan"
            else -> text.lowercase()
        }
        if (name in FUNC2_NAMES) {
            if (!eat(Tok.LP)) throw CalcSyntaxError("语法错误")
            val a = expr()
            if (!eat(Tok.COMMA)) throw CalcSyntaxError("语法错误")
            val b = expr()
            if (!eat(Tok.RP)) throw CalcSyntaxError("缺少右括号")
            return Node.Fn2(name, a, b)
        }
        if (name !in FUNC_NAMES) throw CalcSyntaxError("语法错误")
        val arg = if (ts[i].t == Tok.LP) paren() else unary()
        return Node.Fn(name, arg)
    }
}

// ---------------------------------------------------------------------------
// 数学函数（浮点轨共用）
// ---------------------------------------------------------------------------

private object MathOps {

    fun toRad(mode: AngleMode, x: Double): Double = when (mode) {
        AngleMode.DEG -> x * Math.PI / 180.0
        AngleMode.GRAD -> x * Math.PI / 200.0
        AngleMode.RAD -> x
    }

    fun fromRad(mode: AngleMode, x: Double): Double = when (mode) {
        AngleMode.DEG -> x * 180.0 / Math.PI
        AngleMode.GRAD -> x * 200.0 / Math.PI
        AngleMode.RAD -> x
    }

    /** 抹掉浮点噪声，让 sin(180°) 刚好等于 0、tan(45°) 刚好等于 1 */
    fun clean(v: Double): Double = when {
        abs(v) < 1e-12 -> 0.0
        abs(v - 1.0) < 1e-12 -> 1.0
        abs(v + 1.0) < 1e-12 -> -1.0
        abs(v - 0.5) < 1e-12 -> 0.5
        abs(v + 0.5) < 1e-12 -> -0.5
        else -> v
    }

    fun pow(a: Double, b: Double): Double {
        if (a == 0.0 && b == 0.0) return 1.0
        if (a < 0.0 && b != floor(b)) throw CalcMathError("数学错误")
        val r = a.pow(b)
        if (r.isNaN() || r.isInfinite()) throw CalcMathError("数学错误")
        return r
    }

    fun factorial(x: Double): Double {
        if (x < 0.0 || x != floor(x) || x > 170.0) throw CalcMathError("数学错误")
        var r = 1.0
        var k = 2
        val n = x.toInt()
        while (k <= n) {
            r *= k
            k++
        }
        return r
    }

    fun fn(mode: AngleMode, name: String, x: Double): Double {
        val r = when (name) {
            "sin" -> clean(sin(toRad(mode, x)))
            "cos" -> clean(cos(toRad(mode, x)))
            "tan" -> {
                if (mode != AngleMode.RAD) {
                    val half = if (mode == AngleMode.DEG) 180.0 else 200.0
                    val m = ((x % half) + half) % half
                    if (abs(m - half / 2.0) < 1e-9) throw CalcMathError("数学错误")
                }
                clean(tan(toRad(mode, x)))
            }
            "asin" -> {
                if (x < -1.0 || x > 1.0) throw CalcMathError("数学错误")
                fromRad(mode, asin(x))
            }
            "acos" -> {
                if (x < -1.0 || x > 1.0) throw CalcMathError("数学错误")
                fromRad(mode, acos(x))
            }
            "atan" -> fromRad(mode, atan(x))
            "log" -> {
                if (x <= 0.0) throw CalcMathError("数学错误")
                log10(x)
            }
            "ln" -> {
                if (x <= 0.0) throw CalcMathError("数学错误")
                ln(x)
            }
            "exp" -> exp(x)
            "sqrt" -> {
                if (x < 0.0) throw CalcMathError("数学错误")
                sqrt(x)
            }
            "sinh" -> sinh(x)
            "cosh" -> cosh(x)
            "tanh" -> tanh(x)
            "asinh" -> asinh(x)
            "acosh" -> {
                if (x < 1.0) throw CalcMathError("数学错误")
                acosh(x)
            }
            "atanh" -> {
                if (x <= -1.0 || x >= 1.0) throw CalcMathError("数学错误")
                atanh(x)
            }
            "cbrt" -> {
                val mag = abs(x).pow(1.0 / 3.0)
                if (x < 0.0) -mag else mag
            }
            "abs" -> abs(x)
            else -> throw CalcSyntaxError("语法错误")
        }
        if (r.isNaN() || r.isInfinite()) throw CalcMathError("数学错误")
        return r
    }

    /** 双参数函数求值 */
    fun fn2(name: String, a: Double, b: Double): Double {
        val r = when (name) {
            "logb" -> {
                if (a <= 0.0 || a == 1.0) throw CalcMathError("数学错误")
                if (b <= 0.0) throw CalcMathError("数学错误")
                ln(b) / ln(a)
            }
            "root" -> {
                if (a != floor(a) || a < 1.0 || a > 1e6) throw CalcMathError("数学错误")
                val n = a.toInt()
                if (b < 0.0 && n % 2 == 0) throw CalcMathError("数学错误")
                val mag = abs(b).pow(1.0 / n)
                if (b < 0.0) -mag else mag
            }
            "npr" -> NumericOps.nPr(countOf(a), countOf(b)).toDouble()
            "ncr" -> NumericOps.nCr(countOf(a), countOf(b)).toDouble()
            else -> throw CalcSyntaxError("语法错误")
        }
        if (r.isNaN() || r.isInfinite()) throw CalcMathError("数学错误")
        return r
    }

    private fun countOf(v: Double): Long {
        if (v != floor(v) || v < 0.0 || v > 1.0e9) throw CalcMathError("数学错误")
        return v.toLong()
    }
}

// ---------------------------------------------------------------------------
// 浮点轨求值（v1 行为，保持不变）
// ---------------------------------------------------------------------------

private class Evaluator(
    private val mode: AngleMode,
    private val ans: Double,
    private val mem: Double,
    private val xv: Double = 0.0,
    private val yv: Double = 0.0,
    private val zv: Double = 0.0,
    private val preAns: Double = 0.0,
    private val vars: Map<String, Double> = emptyMap(),
) {
    private fun v(name: String, fallback: Double): Double = vars[name] ?: fallback

    fun eval(n: Node): Double = when (n) {
        is Node.Num -> n.v
        is Node.AnsRef -> ans
        is Node.PreAnsRef -> preAns
        is Node.VarRef -> v(n.name, 0.0)
        is Node.MemRef -> mem
        is Node.XRef -> v("x", xv)
        is Node.YRef -> v("y", yv)
        is Node.ZRef -> zv
        is Node.Neg -> -eval(n.a)
        is Node.Add -> eval(n.a) + eval(n.b)
        is Node.Sub -> eval(n.a) - eval(n.b)
        is Node.Mul -> eval(n.a) * eval(n.b)
        is Node.Div -> {
            val d = eval(n.b)
            if (d == 0.0) throw CalcMathError("数学错误")
            eval(n.a) / d
        }
        is Node.Pow -> MathOps.pow(eval(n.a), eval(n.b))
        is Node.Fact -> MathOps.factorial(eval(n.a))
        is Node.Pct -> eval(n.a) / 100.0
        is Node.Recip -> {
            val v = eval(n.a)
            if (v == 0.0) throw CalcMathError("数学错误")
            1.0 / v
        }
        is Node.Sqrt -> {
            val v = eval(n.a)
            if (v < 0.0) throw CalcMathError("数学错误")
            sqrt(v)
        }
        is Node.Fn -> MathOps.fn(mode, n.name, eval(n.a))
        is Node.Fn2 -> MathOps.fn2(n.name, eval(n.a), eval(n.b))
    }
}

// ---------------------------------------------------------------------------
// 精确轨求值：能精确就精确，只在无理运算时落回 Double
// ---------------------------------------------------------------------------

private class ValueEvaluator(
    private val mode: AngleMode,
    private val ans: Value,
    private val mem: Value,
    private val xv: Double = 0.0,
    private val yv: Double = 0.0,
    private val zv: Double = 0.0,
    private val preAns: Double = 0.0,
    private val vars: Map<String, Double> = emptyMap(),
) {
    private fun v(name: String, fallback: Double): Double = vars[name] ?: fallback

    fun eval(n: Node): Value = when (n) {
        is Node.Num -> Value.of(n.v, n.lit)
        is Node.AnsRef -> ans
        is Node.PreAnsRef -> Value.Floating(preAns)
        is Node.VarRef -> Value.Floating(v(n.name, 0.0))
        is Node.MemRef -> mem
        is Node.XRef -> Value.Floating(v("x", xv))
        is Node.YRef -> Value.Floating(v("y", yv))
        is Node.ZRef -> Value.Floating(zv)
        is Node.Neg -> neg(eval(n.a))
        is Node.Add -> combine(eval(n.a), eval(n.b), { a, b -> a.plus(b) }, { a, b -> a + b })
        is Node.Sub -> combine(eval(n.a), eval(n.b), { a, b -> a.minus(b) }, { a, b -> a - b })
        is Node.Mul -> combine(eval(n.a), eval(n.b), { a, b -> a.times(b) }, { a, b -> a * b })
        is Node.Div -> combine(eval(n.a), eval(n.b), { a, b -> a.div(b) }, { a, b ->
            if (b == 0.0) throw CalcMathError("数学错误") else a / b
        })
        is Node.Pow -> pow(eval(n.a), eval(n.b))
        is Node.Fact -> fact(eval(n.a))
        is Node.Pct -> {
            val v = eval(n.a)
            val e = v.exact
            if (e != null) Value.of(Rational.of(e.num, e.den.multiply(BigInteger.TEN)))
            else Value.Floating(v.toDouble() / 100.0)
        }
        is Node.Recip -> {
            val v = eval(n.a)
            val e = v.exact
            if (e != null) Value.of(Rational.ONE.div(e)) else {
                val d = v.toDouble()
                if (d == 0.0) throw CalcMathError("数学错误")
                Value.Floating(1.0 / d)
            }
        }
        is Node.Sqrt -> sqrtValue(eval(n.a))
        is Node.Fn -> fn1(n.name, eval(n.a))
        is Node.Fn2 -> fn2(n.name, eval(n.a), eval(n.b))
    }

    /** 单参数函数：cbrt 能开尽时保持精确；abs 保持精确 */
    private fun fn1(name: String, v: Value): Value {
        if (name == "cbrt") {
            val e = v.exact
            if (e != null) {
                val r = e.nthRootExact(3)
                if (r != null) return Value.of(r)
            }
        }
        if (name == "abs") {
            val e = v.exact
            if (e != null) return Value.of(e.abs())
        }
        return Value.Floating(MathOps.fn(mode, name, v.toDouble()))
    }

    /** 双参数函数：root / logb 能精确就精确；npr / ncr 走 BigInteger 保大数 */
    private fun fn2(name: String, av: Value, bv: Value): Value {
        val ea = av.exact
        val eb = bv.exact
        when (name) {
            "root" -> {
                if (ea != null && eb != null && ea.isInteger && ea.signum > 0 &&
                    ea.num <= BigInteger.valueOf(1000)
                ) {
                    val r = eb.nthRootExact(ea.num.toInt())
                    if (r != null) return Value.of(r)
                }
            }
            "logb" -> {
                if (ea != null && eb != null && ea.isInteger && eb.isInteger &&
                    ea.signum > 0 && ea != Rational.ONE && eb.signum > 0 &&
                    ea.num.bitLength() < 64 && eb.num.bitLength() < 200
                ) {
                    val d = MathOps.fn2(name, av.toDouble(), bv.toDouble())
                    val k = Math.round(d)
                    if (k in 0..1000 && abs(d - k) < 1e-9 && ea.pow(k.toInt()) == eb) {
                        return Value.of(Rational.of(BigInteger.valueOf(k), BigInteger.ONE))
                    }
                    return Value.Floating(d)
                }
            }
            "npr", "ncr" -> {
                if (ea != null && eb != null && ea.isInteger && eb.isInteger &&
                    ea.signum >= 0 && eb.signum >= 0 &&
                    ea.num.bitLength() < 62 && eb.num.bitLength() < 62
                ) {
                    val big = if (name == "npr") NumericOps.nPr(ea.num.toLong(), eb.num.toLong())
                    else NumericOps.nCr(ea.num.toLong(), eb.num.toLong())
                    if (big.bitLength() <= 128) return Value.of(Rational.of(big, BigInteger.ONE))
                    return Value.Floating(big.toDouble())
                }
            }
        }
        return Value.Floating(MathOps.fn2(name, av.toDouble(), bv.toDouble()))
    }

    private fun sqrtValue(v: Value): Value {
        val e = v.exact
        if (e != null) {
            if (e.signum < 0) throw CalcMathError("数学错误")
            val s = e.sqrtExact()
            if (s != null) return Value.of(s)
        }
        val d = v.toDouble()
        if (d < 0.0) throw CalcMathError("数学错误")
        return Value.Floating(sqrt(d))
    }

    private fun neg(v: Value): Value {
        val e = v.exact
        return if (e != null) Value.of(e.negate()) else Value.Floating(-v.toDouble())
    }

    private inline fun combine(
        a: Value,
        b: Value,
        exact: (Rational, Rational) -> Rational,
        float: (Double, Double) -> Double,
    ): Value {
        val ea = a.exact
        val eb = b.exact
        if (ea != null && eb != null) return Value.of(exact(ea, eb))
        return Value.Floating(float(a.toDouble(), b.toDouble()))
    }

    private fun pow(a: Value, b: Value): Value {
        val ea = a.exact
        val eb = b.exact
        if (ea != null && eb != null) {
            if (eb.isInteger && eb.num.abs() <= BigInteger.valueOf(1000)) {
                return Value.of(ea.pow(eb.num.toInt()))
            }
            // x^(1/2) 且能开尽
            if (eb.num == BigInteger.ONE && eb.den == BigInteger.TWO) {
                val s = ea.sqrtExact()
                if (s != null) return Value.of(s)
            }
        }
        return Value.Floating(MathOps.pow(a.toDouble(), b.toDouble()))
    }

    private fun fact(v: Value): Value {
        val e = v.exact
        if (e != null && e.isInteger && e.signum >= 0 && e.num <= BigInteger.valueOf(200)) {
            var r = BigInteger.ONE
            var k = 2
            val nn = e.num.toInt()
            while (k <= nn) {
                r = r.multiply(BigInteger.valueOf(k.toLong()))
                k++
            }
            return Value.of(Rational.of(r, BigInteger.ONE))
        }
        return Value.Floating(MathOps.factorial(v.toDouble()))
    }
}

// ---------------------------------------------------------------------------
// 对外门面
// ---------------------------------------------------------------------------

object CalcEngine {

    /** 角度 → 弧度（供极坐标等外部算法复用） */
    fun toRadians(mode: AngleMode, x: Double): Double = MathOps.toRad(mode, x)

    /** 弧度 → 角度 */
    fun fromRadians(mode: AngleMode, x: Double): Double = MathOps.fromRad(mode, x)

    /** 求值（浮点轨，v1 行为）；失败抛 CalcSyntaxError / CalcMathError */
    @JvmOverloads
    fun evaluate(
        expr: String,
        mode: AngleMode,
        ans: Double,
        mem: Double,
        preAns: Double = 0.0,
        vars: Map<String, Double> = emptyMap(),
    ): Double {
        val ast = Parser(Lexer(expr).lex()).parse()
        val v = Evaluator(mode, ans, mem, preAns = preAns, vars = vars).eval(ast)
        if (v.isNaN() || v.isInfinite()) throw CalcMathError("数学错误")
        return v
    }

    /** 求值（双轨：精确有理数优先，无理运算落回浮点） */    @JvmOverloads
    fun evaluateValue(
        expr: String,
        mode: AngleMode,
        ans: Double,
        mem: Double,
        lastExact: Value? = null,
        preAns: Double = 0.0,
        vars: Map<String, Double> = emptyMap(),
    ): Value {
        val ast = Parser(Lexer(expr).lex()).parse()
        val ansValue = lastExact ?: Value.Floating(ans)
        val v = ValueEvaluator(mode, ansValue, Value.Floating(mem), preAns = preAns, vars = vars).eval(ast)
        val d = v.toDouble()
        if (d.isNaN() || d.isInfinite()) throw CalcMathError("数学错误")
        return v
    }

    /** 带变量 x / y / z 的求值（微分方程 f(x,y)、f(x,y,z) 用） */
    fun evaluateWith(
        expr: String,
        mode: AngleMode,
        x: Double,
        y: Double,
        z: Double = 0.0,
    ): Double {
        val ast = Parser(Lexer(expr).lex()).parse()
        val v = Evaluator(mode, 0.0, 0.0, x, y, z).eval(ast)
        if (v.isNaN() || v.isInfinite()) throw CalcMathError("数学错误")
        return v
    }

    /** 补齐未闭合的左括号（参考机在按 = 时自动补右括号） */
    fun autoClose(expr: String): String {
        var open = 0
        for (c in expr) {
            when (c) {
                '(' -> open++
                ')' -> open--
            }
        }
        if (open <= 0) return expr
        return expr + ")".repeat(open)
    }

    private const val SUP_DIGITS = "\u2070\u00B9\u00B2\u00B3\u2074\u2075\u2076\u2077\u2078\u2079"

    /**
     * 默认 10 位有效数字；科学计数法用 ×10ⁿ 上标显示。
     * `sigDigits` 为有效数字位数；`fixDecimals` 非空时改用固定小数位（NORM / FIX 设置）；
     * `notation` 选择显示模式（普通 / 科学记数 SCI / 工程记数 ENG）。
     */
    @JvmOverloads
    fun format(
        v: Double,
        sigDigits: Int = 10,
        fixDecimals: Int? = null,
        notation: NumberNotation = NumberNotation.NORM,
    ): String {
        if (v.isNaN() || v.isInfinite()) throw CalcMathError("数学错误")
        if (v == 0.0) return "0"
        when (notation) {
            NumberNotation.SCI -> return scientific(v, sigDigits, fixDecimals)
            NumberNotation.ENG -> return engineering(v, sigDigits, fixDecimals)
            NumberNotation.NORM -> Unit
        }
        val a = abs(v)
        if (fixDecimals != null && a >= 1e-9 && a < 1e10) return fixed(v, fixDecimals)
        if (a >= 1e10 || a < 1e-9) {
            var e = floor(log10(a)).toInt()
            var m = v / 10.0.pow(e)
            if (abs(m) >= 10.0) {
                m /= 10.0
                e++
            } else if (abs(m) < 1.0) {
                m *= 10.0
                e--
            }
            return mantissa(m, sigDigits, fixDecimals) + "\u00D710" + superscript(e)
        }
        return sig(v, sigDigits)
    }

    /** 科学记数法：尾数 ×10ⁿ（n 为任意整数） */
    fun scientific(v: Double, sigDigits: Int = 10, fixDecimals: Int? = null): String {
        if (v == 0.0) return "0"
        var e = floor(log10(abs(v))).toInt()
        var m = v / 10.0.pow(e)
        if (abs(m) >= 10.0) {
            m /= 10.0
            e++
        } else if (abs(m) < 1.0) {
            m *= 10.0
            e--
        }
        return mantissa(m, sigDigits, fixDecimals) + "\u00D710" + superscript(e)
    }

    /** 工程记数法：指数必为 3 的倍数，尾数落在 [1, 1000) */
    fun engineering(v: Double, sigDigits: Int = 10, fixDecimals: Int? = null): String {
        if (v == 0.0) return "0"
        val a = abs(v)
        var e3 = Math.floorDiv(floor(log10(a)).toInt(), 3) * 3
        var m = v / 10.0.pow(e3)
        if (abs(m) >= 1000.0) {
            m /= 1000.0
            e3 += 3
        } else if (abs(m) < 1.0) {
            m *= 1000.0
            e3 -= 3
        }
        val mt = mantissa(m, sigDigits, fixDecimals)
        return if (e3 == 0) mt else mt + "\u00D710" + superscript(e3)
    }

    private fun mantissa(m: Double, sigDigits: Int, fixDecimals: Int?): String =
        if (fixDecimals != null) fixed(m, fixDecimals) else sig(m, sigDigits)

    /**
     * 把常量转成能写回表达式的字面量：
     * 普通量级用十进制小数，极大/极小量级用 `尾数×10^指数`（自然书写会显示上标）。
     */
    @JvmOverloads
    fun literal(v: Double, sigDigits: Int = 12): String {
        if (v == 0.0) return "0"
        val a = abs(v)
        if (a >= 1e-4 && a < 1e15) return plainDecimal(v, sigDigits)
        var e = floor(log10(a)).toInt()
        var m = v / 10.0.pow(e)
        if (abs(m) >= 10.0) {
            m /= 10.0
            e++
        } else if (abs(m) < 1.0) {
            m *= 10.0
            e--
        }
        return plainDecimal(m, sigDigits) + "\u00D710^" + e
    }

    private fun plainDecimal(v: Double, sigDigits: Int): String =
        BigDecimal(v)
            .round(MathContext(sigDigits.coerceIn(1, 15), RoundingMode.HALF_UP))
            .stripTrailingZeros()
            .toPlainString()

    private fun fixed(v: Double, decimals: Int): String {
        val d = decimals.coerceIn(0, 12)
        val bd = BigDecimal(v).setScale(d, RoundingMode.HALF_UP).stripTrailingZeros()
        var s = bd.toPlainString()
        if (s.contains('.')) s = s.trimEnd('0').trimEnd('.')
        return if (s == "-0") "0" else s
    }

    /**
     * 双轨结果的显示：
     *  - 精确轨：分数 / 带分数（decimal=true 或非 NORM 记数法时强制小数）
     *  - 浮点轨：10 位有效数字小数
     */
    @JvmOverloads
    fun formatValue(
        v: Value,
        decimal: Boolean = false,
        mixed: Boolean = false,
        sigDigits: Int = 10,
        fixDecimals: Int? = null,
        notation: NumberNotation = NumberNotation.NORM,
    ): String {
        if (!decimal && fixDecimals == null && notation == NumberNotation.NORM) {
            val r = v.exact
            if (r != null) return formatRational(r, mixed)
        }
        return format(v.toDouble(), sigDigits, fixDecimals, notation)
    }

    /** 有理数显示：整数 / 假分数 / 带分数 */
    fun formatRational(r: Rational, mixed: Boolean): String {
        if (r.isInteger) return r.num.toString()
        if (mixed) {
            val (q, rem, den) = r.toMixed()
            return if (q.signum() == 0) "$rem/$den" else "$q $rem/$den"
        }
        return "${r.num}/${r.den}"
    }

    private fun sig(v: Double, digits: Int): String {
        val bd = BigDecimal(v).round(MathContext(digits.coerceIn(1, 15), RoundingMode.HALF_UP)).stripTrailingZeros()
        var s = bd.toPlainString()
        if (s.contains('.')) s = s.trimEnd('0').trimEnd('.')
        return s
    }

    private fun superscript(e: Int): String {
        val sb = StringBuilder()
        for (c in e.toString()) {
            if (c == '-') sb.append('\u207B') else sb.append(SUP_DIGITS[c - '0'])
        }
        return sb.toString()
    }
}
