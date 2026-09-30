package io.paimon.fx991.ui

// ---------------------------------------------------------------------------
// 自然书写布局树的解析（纯 Kotlin，无 Compose 依赖 → 可在 JVM 上直接回归测试）
//   - 分数：a÷b → 上下堆叠
//   - √  ：带上横线
//   - 上标：x² / x^y / 科学计数法 ×10ⁿ
// ---------------------------------------------------------------------------

sealed interface Nat {
    class Row(val items: List<Nat>) : Nat
    class Frac(val n: Nat, val d: Nat) : Nat
    class Sqrt(val a: Nat) : Nat
    class Sup(val base: Nat, val exp: Nat) : Nat
    class Sym(val text: String) : Nat
}

internal enum class DT {
    NUM, IDENT, PLUS, MINUS, MUL, DIV, POW, FACT, PCT, SQ2, CUBE, RECIP, SUP, LP, RP, SQRT, COMMA, ANGLE, EQ, SEMI, DOT, END
}

internal class DTok(val t: DT, val s: String)

private const val CH_MINUS = '\u2212'
private const val CH_MUL = '\u00D7'
private const val CH_DIV = '\u00F7'
private const val CH_SQ2 = '\u00B2'
private const val CH_CUBE = '\u00B3'
private const val CH_SUP_MINUS = '\u207B'
private const val CH_SUP_ONE = '\u00B9'
private const val CH_SQRT = '\u221A'
private const val CH_SIGMA = '\u03A3'

internal const val SUP_ALL = "\u2070\u00B9\u00B2\u00B3\u2074\u2075\u2076\u2077\u2078\u2079"

/** 上标字符 → 普通字符 */
internal fun deSup(s: String): String {
    val sb = StringBuilder()
    for (ch in s) {
        when (ch) {
            '\u207B' -> sb.append('-')
            else -> {
                val k = SUP_ALL.indexOf(ch)
                sb.append(if (k >= 0) ('0' + k) else ch)
            }
        }
    }
    return sb.toString()
}

internal fun dlex(src: String): List<DTok> {
    val out = ArrayList<DTok>()
    var i = 0
    while (i < src.length) {
        val c = src[i]
        when {
            c.isWhitespace() -> i++
            c.isDigit() || c == '.' -> {
                val s = i
                var dot = false
                while (i < src.length) {
                    val d = src[i]
                    if (d.isDigit()) i++ else if (d == '.' && !dot) { dot = true; i++ } else break
                }
                out.add(DTok(DT.NUM, src.substring(s, i)))
            }
            c.isLetter() -> {
                val s = i
                while (i < src.length && src[i].isLetter()) i++
                if (i + 1 < src.length && src[i] == CH_SUP_MINUS && src[i + 1] == CH_SUP_ONE) i += 2
                out.add(DTok(DT.IDENT, src.substring(s, i)))
            }
            c == '+' -> { out.add(DTok(DT.PLUS, "+")); i++ }
            c == '-' || c == CH_MINUS -> { out.add(DTok(DT.MINUS, "\u2212")); i++ }
            c == '*' || c == CH_MUL -> { out.add(DTok(DT.MUL, "\u00D7")); i++ }
            c == '/' || c == CH_DIV -> { out.add(DTok(DT.DIV, "\u00F7")); i++ }
            c == '^' -> { out.add(DTok(DT.POW, "^")); i++ }
            c == '!' -> { out.add(DTok(DT.FACT, "!")); i++ }
            c == '%' -> { out.add(DTok(DT.PCT, "%")); i++ }
            c == '(' -> { out.add(DTok(DT.LP, "(")); i++ }
            c == ')' -> { out.add(DTok(DT.RP, ")")); i++ }
            c == CH_SQRT -> { out.add(DTok(DT.SQRT, "\u221A")); i++ }
            c == CH_SQ2 -> { out.add(DTok(DT.SQ2, CH_SQ2.toString())); i++ }
            c == CH_CUBE -> { out.add(DTok(DT.CUBE, CH_CUBE.toString())); i++ }
            c == CH_SIGMA -> { out.add(DTok(DT.IDENT, "\u03A3")); i++ }
            c == ',' -> { out.add(DTok(DT.COMMA, ",")); i++ }
            c == '=' -> { out.add(DTok(DT.EQ, "=")); i++ }
            c == ';' -> { out.add(DTok(DT.SEMI, ";")); i++ }
            c == '\u00B7' -> { out.add(DTok(DT.DOT, "\u00B7")); i++ }
            c == '\u2220' -> { out.add(DTok(DT.ANGLE, "\u2220")); i++ }
            c in SUP_ALL || c == CH_SUP_MINUS -> {
                val s = i
                while (i < src.length && (src[i] in SUP_ALL || src[i] == CH_SUP_MINUS)) i++
                val raw = src.substring(s, i)
                if (raw == "\u207B\u00B9") {
                    out.add(DTok(DT.RECIP, raw))
                } else {
                    out.add(DTok(DT.SUP, raw))
                }
            }
            else -> i++
        }
    }
    out.add(DTok(DT.END, ""))
    return out
}

internal class NatParser(private val ts: List<DTok>) {
    private var i = 0
    private fun cur() = ts[i]
    private fun eat(t: DT): Boolean = if (ts[i].t == t) { i++; true } else false
    private fun startsPrimary(): Boolean = when (ts[i].t) {
        DT.NUM, DT.IDENT, DT.LP, DT.SQRT -> true
        else -> false
    }

    fun parse(): Nat = expr()

    private fun expr(): Nat {
        val parts = ArrayList<Nat>()
        parts.add(term())
        while (true) {
            when {
                eat(DT.PLUS) -> { parts.add(Nat.Sym("+")); parts.add(term()) }
                eat(DT.MINUS) -> { parts.add(Nat.Sym("\u2212")); parts.add(term()) }
                // 参数分隔 / 极坐标 / 等号 / 分号 / 点乘：都当作行内符号，保证自然书写里能画出来
                eat(DT.COMMA) -> { parts.add(Nat.Sym(",")); parts.add(term()) }
                eat(DT.ANGLE) -> { parts.add(Nat.Sym("\u2220")); parts.add(term()) }
                eat(DT.EQ) -> { parts.add(Nat.Sym("=")); parts.add(term()) }
                eat(DT.SEMI) -> { parts.add(Nat.Sym(";")); parts.add(term()) }
                eat(DT.DOT) -> { parts.add(Nat.Sym("\u00B7")); parts.add(term()) }
                else -> break
            }
        }
        return if (parts.size == 1) parts[0] else Nat.Row(parts)
    }

    private fun term(): Nat {
        var left = factor()
        while (true) {
            when {
                eat(DT.MUL) -> left = Nat.Row(listOf(left, Nat.Sym("\u00D7"), factor()))
                eat(DT.DIV) -> left = Nat.Frac(left, factor())
                else -> return left
            }
        }
    }

    private fun factor(): Nat {
        val parts = ArrayList<Nat>()
        parts.add(unary())
        while (startsPrimary()) parts.add(unary())
        return if (parts.size == 1) parts[0] else Nat.Row(parts)
    }

    private fun unary(): Nat {
        if (eat(DT.MINUS)) return Nat.Row(listOf(Nat.Sym("\u2212"), unary()))
        if (eat(DT.PLUS)) return unary()
        return power()
    }

    private fun power(): Nat {
        val base = postfix()
        if (eat(DT.POW)) return Nat.Sup(base, unary())
        return base
    }

    private fun postfix(): Nat {
        var n = primary()
        while (true) {
            when {
                eat(DT.FACT) -> n = Nat.Row(listOf(n, Nat.Sym("!")))
                eat(DT.PCT) -> n = Nat.Row(listOf(n, Nat.Sym("%")))
                eat(DT.SQ2) -> n = Nat.Sup(n, Nat.Sym("2"))
                eat(DT.CUBE) -> n = Nat.Sup(n, Nat.Sym("3"))
                eat(DT.RECIP) -> n = Nat.Frac(Nat.Sym("1"), n)
                eat(DT.SUP) -> n = Nat.Sup(n, Nat.Sym(deSup(ts[i - 1].s)))
                else -> return n
            }
        }
    }

    private fun primary(): Nat = when (cur().t) {
        DT.NUM -> Nat.Sym(ts[i++].s)
        DT.SQRT -> { i++; Nat.Sqrt(unary()) }
        DT.IDENT -> {
            val name = ts[i++].s
            val arg = when {
                cur().t == DT.LP -> group()
                // 隐式参数（如 sin x、x y）只允许从 数字/标识符/根号/负号 开始；
                // 绝不能吃 PLUS —— unary() 会静默吃掉 '+'，导致 "x+3" 渲染成 "x3"（修 pluskey bug）
                cur().t == DT.NUM || cur().t == DT.IDENT ||
                    cur().t == DT.SQRT || cur().t == DT.MINUS -> unary()
                else -> Nat.Sym("")
            }
            Nat.Row(listOf(Nat.Sym(name), arg))
        }
        DT.LP -> group()
        else -> Nat.Sym("")
    }

    private fun group(): Nat {
        eat(DT.LP)
        val e = if (cur().t == DT.RP) Nat.Sym("") else expr()
        eat(DT.RP)
        return Nat.Row(listOf(Nat.Sym("("), e, Nat.Sym(")")))
    }
}

/** 把表达式字符串解析成自然书写布局树（解析失败时退化为纯文本） */
fun buildNat(expr: String): Nat = try {
    if (expr.isBlank()) Nat.Sym("") else NatParser(dlex(expr)).parse()
} catch (_: Exception) {
    Nat.Sym(expr)
}

// ---------------------------------------------------------------------------
// ASCII 投影：把自然书写布局摊平，供 JVM 回归测试核对「分数确实上下堆叠」
// ---------------------------------------------------------------------------

class AsciiBlock(val lines: List<String>, val baseline: Int)

private fun blockedWidth(b: AsciiBlock): Int = b.lines.maxOfOrNull { it.length } ?: 0

private fun padRight(s: String, w: Int): String =
    if (s.length >= w) s else s + " ".repeat(w - s.length)

private fun center(s: String, w: Int): String {
    if (s.length >= w) return s
    val total = w - s.length
    val l = total / 2
    return " ".repeat(l) + s + " ".repeat(total - l)
}

private fun hcat(a: AsciiBlock, b: AsciiBlock): AsciiBlock {
    val base = maxOf(a.baseline, b.baseline)
    val below = maxOf(a.lines.size - 1 - a.baseline, b.lines.size - 1 - b.baseline)
    val h = base + 1 + below
    val out = MutableList(h) { StringBuilder() }
    fun place(x: AsciiBlock) {
        val w = blockedWidth(x)
        val top = base - x.baseline
        for (ix in x.lines.indices) out[top + ix].append(padRight(x.lines[ix], w))
    }
    place(a)
    place(b)
    return AsciiBlock(out.map { it.toString().trimEnd() }, base)
}

fun natAscii(n: Nat): AsciiBlock = when (n) {
    is Nat.Sym -> AsciiBlock(listOf(if (n.text.isEmpty()) " " else n.text), 0)

    is Nat.Row -> {
        var acc = AsciiBlock(listOf(""), 0)
        for (item in n.items) acc = hcat(acc, natAscii(item))
        acc
    }

    is Nat.Frac -> {
        val a = natAscii(n.n)
        val b = natAscii(n.d)
        val w = maxOf(blockedWidth(a), blockedWidth(b))
        val lines = ArrayList<String>()
        a.lines.forEach { lines.add(center(it.trim(), w)) }
        lines.add("\u2500".repeat(w))
        b.lines.forEach { lines.add(center(it.trim(), w)) }
        AsciiBlock(lines, a.lines.size)
    }

    is Nat.Sqrt -> {
        val a = natAscii(n.a)
        val w = blockedWidth(a)
        val lines = ArrayList<String>()
        lines.add("  " + "\u2500".repeat(w))
        a.lines.forEachIndexed { ix, s ->
            lines.add((if (ix == 0) "\u221A " else "  ") + s)
        }
        AsciiBlock(lines, a.baseline + 1)
    }

    is Nat.Sup -> {
        val b = natAscii(n.base)
        val e = natAscii(n.exp)
        hcat(b, AsciiBlock(e.lines, e.baseline + 1))
    }
}

/** 单行落地（测试/日志用） */
fun natAsciiText(expr: String): String =
    natAscii(buildNat(expr)).lines.joinToString("\n")
