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

    // -------------------------------------------------------------------
    // 批次 K3-symbolic：方向键上下 = 二维结构内的垂直光标移动（不再是历史）
    //   · 分数：分母 ↔ 分子        · 上标：▲ 进指数；▼ 出到上标块之后的基线位
    //   · 根号：内外（√ 内按 ↑ 出到根号后；根号后按 ↓ 回到 √ 内）
    //   · ∫ 模板：被积式 ↑→上限 / ↓→下限；上限 ↓→下限；下限 ↑→上限
    //   · 相对偏移在槽位间等距映射（分子第 2 列 ↓ 到分母第 2 列），超长钳到槽尾
    //   · 光标不在任何二维结构槽位里时：↑ = 到表达式开头，↓ = 到末尾
    //     （含结构的"边缘位"：√ 符号处、int( 前缀、模板收尾括号之后 ——
    //       旧逻辑把这些位置也算"在结构里"导致 ▲▼ 完全冻死）
    //   · 光标在二维结构槽位里但该方向没有去处（如已在分子再按 ↑）→ 原地不动
    // -------------------------------------------------------------------

    /** 上移一格（二维结构内向「上」走） */
    fun moveUp(expr: String, pos: Int): Int = moveVertical(expr, pos, up = true)

    /** 下移一格（二维结构内向「下」走） */
    fun moveDown(expr: String, pos: Int): Int = moveVertical(expr, pos, up = false)

    private fun moveVertical(expr: String, pos: Int, up: Boolean): Int {
        val p = clamp(expr, pos)
        if (expr.isEmpty()) return 0
        val root = try {
            buildNat(expr)
        } catch (_: Exception) {
            return if (up) 0 else expr.length     // 解析不了就退化为首/尾
        }
        verticalTarget(root, p, up)?.let { return it }
        // 在二维结构的槽位里但该方向没去处 → 原地；
        // 纯线性表达式、或只挨着结构边缘（√ 符号 / int( 前缀 / 收尾括号后）→ 首/尾
        return if (insideSlot(root, p)) p else if (up) 0 else expr.length
    }

    /** 槽位间等距映射：从 from 槽里的相对列 → to 槽里同列（超长钳到槽尾） */
    private fun mapSlot(from: Nat, to: Nat, cur: Int): Int? {
        if (to.srcStart < 0 || to.srcEnd < to.srcStart) return null
        val len = to.srcEnd - to.srcStart
        val rel = if (from.srcStart >= 0) cur - from.srcStart else 0
        return to.srcStart + rel.coerceIn(0, len)
    }

    /**
     * 找垂直移动的目标源码偏移：最深优先（嵌套结构先在内层走），
     * 内层没有可走方向才轮到本层。找不到返回 null。
     */
    private fun verticalTarget(node: Nat, cur: Int, up: Boolean): Int? {
        // 1) 先看子结构（最深优先）
        when (node) {
            is Nat.Row -> for (c in node.items) {
                if (inSpan(c, cur)) verticalTarget(c, cur, up)?.let { return it }
            }
            is Nat.Frac -> {
                if (inSpan(node.n, cur)) verticalTarget(node.n, cur, up)?.let { return it }
                if (inSpan(node.d, cur)) verticalTarget(node.d, cur, up)?.let { return it }
            }
            is Nat.Sqrt -> if (inSpan(node.a, cur)) {
                verticalTarget(node.a, cur, up)?.let { return it }
            }
            is Nat.Sup -> {
                if (inSpan(node.base, cur)) verticalTarget(node.base, cur, up)?.let { return it }
                if (inSpan(node.exp, cur)) verticalTarget(node.exp, cur, up)?.let { return it }
            }
            is Nat.Integ -> {
                if (inSpan(node.body, cur)) verticalTarget(node.body, cur, up)?.let { return it }
                if (inSpan(node.lo, cur)) verticalTarget(node.lo, cur, up)?.let { return it }
                if (inSpan(node.hi, cur)) verticalTarget(node.hi, cur, up)?.let { return it }
            }
            else -> {}
        }
        // 2) 本层映射
        return when (node) {
            is Nat.Frac -> when {
                !up && inSpan(node.n, cur) -> mapSlot(node.n, node.d, cur)
                up && inSpan(node.d, cur) -> mapSlot(node.d, node.n, cur)
                else -> null
            }
            is Nat.Sup -> when {
                up && inSpan(node.base, cur) -> mapSlot(node.base, node.exp, cur)
                // K3-symbolic 修复：▼ 从指数「出」到整个上标块之后的基线位，
                // 不再落进底数内部（旧逻辑落到底数末尾 = "^" 之前，
                // 接着打字会把想要的 x^2+3 错成 x+3^2）
                !up && inSpan(node.exp, cur) -> node.srcEnd.takeIf { it >= 0 }
                else -> null
            }
            is Nat.Sqrt -> when {
                // √ 内 ↑ → 出到根号后；根号后 ↓ → 回到 √ 内（根号内容末尾，闭括号之前）
                up && inSpan(node.a, cur) -> if (node.srcEnd >= 0) node.srcEnd else null
                !up && node.srcEnd == cur && inSpan(node, cur) -> innerEnd(node.a).takeIf { it >= 0 }
                else -> null
            }
            is Nat.Integ -> when {
                up && inSpan(node.body, cur) -> mapSlot(node.body, node.hi, cur)
                !up && inSpan(node.body, cur) -> mapSlot(node.body, node.lo, cur)
                up && inSpan(node.lo, cur) -> mapSlot(node.lo, node.hi, cur)
                !up && inSpan(node.hi, cur) -> mapSlot(node.hi, node.lo, cur)
                else -> null
            }
            else -> null
        }
    }

    /** 根号内容的末尾位置：带括号的组要回到闭括号之前 */
    private fun innerEnd(a: Nat): Int =
        if (a.srcStart < 0) -1
        else if (isGroupNode(a)) (a.srcEnd - 1).coerceAtLeast(a.srcStart)
        else a.srcEnd

    /**
     * 光标是否落在某个二维结构（分数/上标/根号/积分模板）的**槽位**源码区间里。
     * 只数槽位（分子/分母/底数/指数/根号内容/积分三槽），不数容器本身的边缘
     * （√ 符号、int( 前缀、收尾括号之后）—— 那些位置没有垂直去处，按线性式走首/尾。
     */
    private fun insideSlot(node: Nat, cur: Int): Boolean = when (node) {
        is Nat.Row -> node.items.any { insideSlot(it, cur) }
        is Nat.Frac -> inSpan(node.n, cur) || inSpan(node.d, cur)
        is Nat.Sqrt -> inSpan(node.a, cur)
        is Nat.Sup -> inSpan(node.base, cur) || inSpan(node.exp, cur)
        is Nat.Integ -> inSpan(node.body, cur) || inSpan(node.lo, cur) || inSpan(node.hi, cur)
        else -> false
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

    /**
     * 主行分数键（K3-symbolic 修复）：插入 ÷。
     * 分子是空槽（行首 / 运算符 / 开括号 / 分隔符之后）时，光标留在分子空位
     * （与二级界面键盘 a/b 的 cursorBack=1 一致）；分子有内容时进分母槽。
     */
    fun insertFraction(expr: String, pos: Int): EditResult {
        val p = clamp(expr, pos)
        val r = insert(expr, p, "÷")
        val emptyNum = p == 0 || expr[p - 1] in "+−×÷^(,=;∠·±"
        return if (emptyNum) EditResult(r.text, p) else r
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
            // K3-symbolic 修复：cur 落在所有子项之外时按方位放 ——
            // 在行的起点之前就把光标画到行首，而不是一律甩到行尾
            if (cur <= node.srcStart) Nat.Row(listOf(Nat.Cursor) + node.items)
            else Nat.Row(node.items + Nat.Cursor)
        }
    }

    is Nat.Frac -> when {
        inSpan(node.n, cur) -> Nat.Frac(placeCursor(node.n, cur), node.d)
        inSpan(node.d, cur) -> Nat.Frac(node.n, placeCursor(node.d, cur))
        cur <= node.srcStart -> Nat.Row(listOf(Nat.Cursor, node))
        else -> Nat.Row(listOf(node, Nat.Cursor))
    }

    // K3-symbolic 修复：光标在 √ 符号位（内容槽之前）时画到根号前面，
    // 旧逻辑一律甩到根号后面（◀ 走到行首，光标却显示在根号末尾）
    is Nat.Sqrt ->
        if (inSpan(node.a, cur)) Nat.Sqrt(placeCursor(node.a, cur))
        else if (cur <= node.srcStart) Nat.Row(listOf(Nat.Cursor, node))
        else Nat.Row(listOf(node, Nat.Cursor))

    is Nat.Sup -> when {
        inSpan(node.base, cur) -> Nat.Sup(placeCursor(node.base, cur), node.exp)
        inSpan(node.exp, cur) -> Nat.Sup(node.base, placeCursor(node.exp, cur))
        cur <= node.srcStart -> Nat.Row(listOf(Nat.Cursor, node))
        else -> Nat.Row(listOf(node, Nat.Cursor))
    }

    // 批次 K3：光标能落进 ∫ 模板的被积式 / 下限 / 上限三个槽；
    // K3-symbolic 修复：int( 前缀位置画到 ∫ 前面，不再甩到 dx 之后
    is Nat.Integ -> when {
        inSpan(node.body, cur) -> Nat.Integ(placeCursor(node.body, cur), node.lo, node.hi)
        inSpan(node.lo, cur) -> Nat.Integ(node.body, placeCursor(node.lo, cur), node.hi)
        inSpan(node.hi, cur) -> Nat.Integ(node.body, node.lo, placeCursor(node.hi, cur))
        cur <= node.srcStart -> Nat.Row(listOf(Nat.Cursor, node))
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
