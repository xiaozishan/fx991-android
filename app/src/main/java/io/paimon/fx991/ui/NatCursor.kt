package io.paimon.fx991.ui

// ---------------------------------------------------------------------------
// 批次 K3-A：自然书写编辑器的「可见光标」—— 位置模型 + 光标落点（纯 Kotlin，JVM 可测）
//   · 光标 = 表达式字符串里的一个偏移量（0..length）
//   · 左右移动 / 退格按「原子」跳格（sin(、√(、10^ 等当一个整体），逐字符兜底
//   · 插入 / 退格 / 前删后光标跟随（EditResult 同时给新文本与新偏移）
//   · buildNatCursor：把 Nat.Cursor 标记插进自然书写布局树 —— 可深入
//     分数分子/分母、根号内、上下标、括号组、空槽位，渲染层据此画闪烁竖线
//   · natLinear：自然书写节点树 → 线性文本（B4 双向转换的反向通道）
// ---------------------------------------------------------------------------

/** 一次编辑的结果：新文本 + 新光标偏移 */
data class EditResult(val text: String, val cursor: Int)

object CursorModel {

    /**
     * 移动 / 删除时视为一个整体的原子（长的优先匹配）。
     * 与主行退格语义一致：按一次 DEL 删掉一整个 sin( / √( / 10^。
     */
    val ATOMS: List<String> = listOf(
        "sin\u207B\u00B9(", "cos\u207B\u00B9(", "tan\u207B\u00B9(",
        "asinh(", "acosh(", "atanh(", "sinh(", "cosh(", "tanh(",
        "cbrt(", "abs(", "fourier(", "cint(", "conj(", "res(",
        "sin(", "cos(", "tan(", "logb(", "log(", "ln(",
        "root(", "npr(", "ncr(", "exp(",
        // 批次 K4
        "acot(", "cot(", "ceil(", "floor(", "gcd(", "lcm(", "mod(",
        // 批次 K3：就地括号调用模板（长的优先匹配；ranint( 先于 int(）
        "ranint(", "deriv(", "solve(", "const(", "conv(", "calc(",
        "dms(", "int(", "sum(", "lim(", "pol(", "rec(", "sto(", "si(",
        "\u221A(",
        "sin\u207B\u00B9", "cos\u207B\u00B9", "tan\u207B\u00B9",
        "\u00D710^", "PreAns", "10^", "Ans", "\u207B\u00B9",
    ).sortedByDescending { it.length }

    fun clamp(expr: String, pos: Int): Int = pos.coerceIn(0, expr.length)

    /** 左移一格（光标前的原子整体跳过） */
    fun moveLeft(expr: String, pos: Int): Int {
        val p = clamp(expr, pos)
        if (p == 0) return 0
        for (a in ATOMS) {
            if (p >= a.length && expr.regionMatches(p - a.length, a, 0, a.length)) return p - a.length
        }
        return p - 1
    }

    /** 右移一格（光标处的原子整体跳过） */
    fun moveRight(expr: String, pos: Int): Int {
        val p = clamp(expr, pos)
        if (p >= expr.length) return expr.length
        for (a in ATOMS) {
            if (expr.startsWith(a, p)) return (p + a.length).coerceAtMost(expr.length)
        }
        return p + 1
    }

    /** 在光标处插入文本；新光标落在插入文本之后 */
    fun insert(expr: String, pos: Int, text: String): EditResult {
        val p = clamp(expr, pos)
        return EditResult(expr.substring(0, p) + text + expr.substring(p), p + text.length)
    }

    /** 退格：删掉光标前一个原子（或一个字符）；光标跟随 */
    fun backspace(expr: String, pos: Int): EditResult {
        val p = clamp(expr, pos)
        if (p == 0) return EditResult(expr, 0)
        for (a in ATOMS) {
            if (p >= a.length && expr.regionMatches(p - a.length, a, 0, a.length)) {
                return EditResult(expr.removeRange(p - a.length, p), p - a.length)
            }
        }
        return EditResult(expr.removeRange(p - 1, p), p - 1)
    }

    /** 前删（Delete 键语义）：删掉光标处一个原子（或一个字符）；光标不动 */
    fun deleteForward(expr: String, pos: Int): EditResult {
        val p = clamp(expr, pos)
        if (p >= expr.length) return EditResult(expr, p)
        for (a in ATOMS) {
            if (expr.startsWith(a, p)) {
                return EditResult(expr.removeRange(p, (p + a.length).coerceAtMost(expr.length)), p)
            }
        }
        return EditResult(expr.removeRange(p, p + 1), p)
    }
}

// ---------------------------------------------------------------------------
// 光标落点：把 Nat.Cursor 插进布局树
// ---------------------------------------------------------------------------

private fun inSpan(n: Nat, cur: Int): Boolean =
    n.srcStart >= 0 && cur >= n.srcStart && cur <= n.srcEnd

/**
 * 在源码区间为 [srcStart, srcEnd] 的节点里落光标（调用方保证 cur 落在节点区间内）。
 * 优先深入包含 cur 的子节点 —— 所以光标能走进分数分子/分母、根号、上标、括号组、空槽位。
 */
private fun placeCursor(node: Nat, cur: Int): Nat = when (node) {
    is Nat.Sym -> {
        val s = node.srcStart
        val e = node.srcEnd
        when {
            s < 0 || e < s -> Nat.Row(listOf(node, Nat.Cursor))
            s == e -> Nat.Row(listOf(Nat.Cursor, node))               // 空槽位
            cur <= s -> Nat.Row(listOf(Nat.Cursor, node))
            cur >= e -> Nat.Row(listOf(node, Nat.Cursor))
            // deSup 是 1:1 映射，所以 token 文本长度 == 源码长度时才允许从中间劈开
            e - s == node.text.length -> {
                val k = cur - s
                Nat.Row(
                    listOf(
                        Nat.Sym(node.text.substring(0, k)),
                        Nat.Cursor,
                        Nat.Sym(node.text.substring(k)),
                    )
                )
            }
            else -> Nat.Row(listOf(node, Nat.Cursor))
        }
    }

    is Nat.Row -> {
        val idx = node.items.indexOfFirst { inSpan(it, cur) }
        if (idx >= 0) {
            val items = node.items.toMutableList()
            items[idx] = placeCursor(items[idx], cur)
            Nat.Row(items)
        } else {
            Nat.Row(node.items + Nat.Cursor)
        }
    }

    is Nat.Frac -> when {
        inSpan(node.n, cur) -> Nat.Frac(placeCursor(node.n, cur), node.d)
        inSpan(node.d, cur) -> Nat.Frac(node.n, placeCursor(node.d, cur))
        else -> Nat.Row(listOf(node, Nat.Cursor))
    }

    is Nat.Sqrt ->
        if (inSpan(node.a, cur)) Nat.Sqrt(placeCursor(node.a, cur)) else Nat.Row(listOf(node, Nat.Cursor))

    is Nat.Sup -> when {
        inSpan(node.base, cur) -> Nat.Sup(placeCursor(node.base, cur), node.exp)
        inSpan(node.exp, cur) -> Nat.Sup(node.base, placeCursor(node.exp, cur))
        else -> Nat.Row(listOf(node, Nat.Cursor))
    }

    // 批次 K3：光标能落进 ∫ 模板的被积式 / 下限 / 上限三个槽
    is Nat.Integ -> when {
        inSpan(node.body, cur) -> Nat.Integ(placeCursor(node.body, cur), node.lo, node.hi)
        inSpan(node.lo, cur) -> Nat.Integ(node.body, placeCursor(node.lo, cur), node.hi)
        inSpan(node.hi, cur) -> Nat.Integ(node.body, node.lo, placeCursor(node.hi, cur))
        else -> Nat.Row(listOf(node, Nat.Cursor))
    }

    is Nat.Cursor -> node
}

/**
 * 解析表达式并把光标标记插进布局树（恰好一个 Nat.Cursor）。
 * 解析失败时退化为「纯文本 + 末尾光标」，保证光标永远不丢。
 */
fun buildNatCursor(expr: String, cursor: Int): Nat {
    val root = buildNat(expr)
    return placeCursor(root, cursor.coerceIn(0, expr.length))
}

/**
 * 光标在布局树里的结构路径（测试 / 调试用），如 root/Row[1]/num、root/exp。
 * 找不到返回空串。
 */
fun natCursorPath(n: Nat): String {
    fun walk(node: Nat, path: String): String? = when (node) {
        is Nat.Cursor -> path
        is Nat.Sym -> null
        is Nat.Row -> {
            for ((ix, c) in node.items.withIndex()) {
                walk(c, "$path/Row[$ix]")?.let { return it }
            }
            null
        }
        is Nat.Frac -> walk(node.n, "$path/num") ?: walk(node.d, "$path/den")
        is Nat.Sqrt -> walk(node.a, "$path/sqrt")
        is Nat.Sup -> walk(node.base, "$path/base") ?: walk(node.exp, "$path/exp")
        is Nat.Integ -> walk(node.body, "$path/body")
            ?: walk(node.lo, "$path/lo")
            ?: walk(node.hi, "$path/hi")
    }
    return walk(n, "root") ?: ""
}

// ---------------------------------------------------------------------------
// 自然书写节点树 → 线性文本（B4：线性文本 ↔ 节点树 双向转换的反向）
// ---------------------------------------------------------------------------

/** 是否是一个「自带括号」的组节点（自身已有分组能力，外面不用再包） */
private fun isGroupNode(n: Nat): Boolean = n is Nat.Row && n.items.size >= 2 &&
    (n.items.first() as? Nat.Sym)?.text == "(" &&
    (n.items.last() as? Nat.Sym)?.text == ")"

/** 作为分数的分子/分母、上标的指数输出时，要不要加括号 */
private fun argText(n: Nat, forSup: Boolean): String {
    val t = natLinear(n)
    val need = when {
        isGroupNode(n) -> false
        n is Nat.Sym -> false
        n is Nat.Sqrt -> false            // √(...) 自带括号
        n is Nat.Frac -> true             // a÷b÷c 结合性歧义
        n is Nat.Sup -> false             // ^ 右结合，x^2^3 == x^(2^3)
        n is Nat.Row -> n.items.any {
            (it is Nat.Sym && (it.text == "+" || it.text == "\u2212" || it.text == "\u00F7" ||
                (forSup && it.text == "\u00D7"))) || it is Nat.Frac
        }
        else -> false
    }
    return if (need) "($t)" else t
}

/** 自然书写节点树 → 线性文本（光标标记不产出字符） */
fun natLinear(n: Nat): String = when (n) {
    is Nat.Sym -> n.text
    is Nat.Cursor -> ""
    is Nat.Row -> buildString { n.items.forEach { append(natLinear(it)) } }
    is Nat.Frac -> argText(n.n, forSup = false) + "\u00F7" + argText(n.d, forSup = false)
    is Nat.Sqrt -> if (isGroupNode(n.a)) "\u221A" + natLinear(n.a) else "\u221A(" + natLinear(n.a) + ")"
    is Nat.Sup -> natLinear(n.base) + "^" + argText(n.exp, forSup = true)
    // 批次 K3：积分模板回写为 int(式,下,上)（槽位内容原样线性化，空槽即空）
    is Nat.Integ -> "int(" + natLinear(n.body) + "," + natLinear(n.lo) + "," + natLinear(n.hi) + ")"
}

/** 表达式字符串 → 节点树 → 线性文本（规范化后的同义线性式） */
fun natLinearOf(expr: String): String = natLinear(buildNat(expr))
