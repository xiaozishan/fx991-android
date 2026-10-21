package io.paimon.fx991.ui

// ---------------------------------------------------------------------------
// 输入体验重构 · 第一批：结构感知编辑核心（纯 Kotlin，JVM 可测）
//
//   旧实现的痛点：退格只会「删一个字符」，于是删完 3÷ 的 3 会剩下一个光秃秃的 ÷，
//   删 sin() 里的字符会留下 sin( 残骸，用户得连按好几次才能收拾干净。
//
//   这里不重写表达式、不改语法 —— 只在**原有线性文本**上按括号/运算符的配对关系
//   判断「一次删除该吃掉多少」，并做「悬挂结构清理」：编辑后绝不留下空的 ÷ / ^ / √。
//   这样渲染层（buildNat）与求值层（CalcEngine）完全不用动。
// ---------------------------------------------------------------------------

object InputCore {

    private const val SQRT = '\u221A'
    private const val DIV = '\u00F7'
    private const val MUL = '\u00D7'
    private const val MINUS = '\u2212'

    /** 算式里的运算符：用于判断「一侧为空」 */
    private fun isOp(c: Char) =
        c == '+' || c == MINUS || c == '-' || c == MUL || c == DIV || c == '^' || c == '(' || c == ',' || c == '='

    private fun isDelim(c: Char) = c == '(' || c == ')' || c == ',' || c == '=' || c.isWhitespace()

    /** 括号配对表：open → close，close → open；未配对的留 -1 */
    fun pairs(t: String): IntArray {
        val m = IntArray(t.length) { -1 }
        val st = ArrayDeque<Int>()
        for (i in t.indices) {
            when (t[i]) {
                '(' -> st.addLast(i)
                ')' -> if (st.isNotEmpty()) {
                    val o = st.removeLast()
                    m[o] = i
                    m[i] = o
                }
            }
        }
        return m
    }

    /**
     * 光标所在的最内层「空括号组」。空组 = 括号之间没有任何字符。
     * pos 允许落在 (open, close] 区间（即光标在括号内侧）。
     */
    fun emptyGroupAt(t: String, pos: Int): Pair<Int, Int>? {
        val m = pairs(t)
        var best: Pair<Int, Int>? = null
        for (i in t.indices) {
            if (t[i] != '(') continue
            val c = m[i]
            if (c == i + 1 && pos in (i + 1)..c) {
                if (best == null || i > best.first) best = i to c
            }
        }
        return best
    }

    private fun isNameChar(c: Char) =
        c.isLetter() || c == '\u00B2' || c == '\u00B3' || c == '\u207B' || c == '\u00B9' || c == '\''

    /** 空组前面的函数名起点：sin( → 0；+sin( → 1 */
    fun nameStart(t: String, open: Int): Int {
        var s = open
        while (s > 0 && isNameChar(t[s - 1])) s--
        return s
    }

    /** 展开到「整个结构」的起点：函数名 + 可选的 √ 前缀 */
    private fun structStart(t: String, open: Int): Int {
        var s = nameStart(t, open)
        // √ 可能是函数名的一部分（如 √( ），也可能独立：√(x) 的 √ 在 open-1
        if (s > 0 && t[s - 1] == SQRT) s--
        return s
    }

    /** 右侧（pos 之后）到下一个运算符之间是否为空 */
    private fun rightEmpty(t: String, pos: Int): Boolean {
        if (pos >= t.length) return true
        val c = t[pos]
        return isOp(c) || c == ')'
    }

    /** 左侧（pos 之前）到上一个运算符之间是否为空 */
    private fun leftEmpty(t: String, pos: Int): Boolean {
        if (pos <= 0) return true
        val c = t[pos - 1]
        return isOp(c) || c == '('
    }

    /** 退格：结构优先（空组整体删 / 悬挂运算符清理），其次原子，最后单字符 */
    fun backspace(text: String, pos: Int): EditResult {
        val p = pos.coerceIn(0, text.length)
        if (p == 0) return EditResult(text, 0)

        // 1) 光标在空括号组里：整个调用一起删（sin() / √() / () ）
        emptyGroupAt(text, p)?.let { (o, c) ->
            val s = structStart(text, o)
            return EditResult(text.removeRange(s, c + 1), s)
        }

        // 1b) 光标刚好停在空组的 ) 后面：从外面删也整个拿掉
        if (text[p - 1] == ')') {
            val m = pairs(text)
            val o = m[p - 1]
            if (o >= 0 && o + 1 == p - 1) {
                val s = structStart(text, o)
                return EditResult(text.removeRange(s, p), s)
            }
        }

        // 2) 光标紧跟在 ÷ / ^ / √ 后面，且该结构另一侧为空 → 连结构一起删
        val prev = text[p - 1]
        if (prev == DIV && (leftEmpty(text, p - 1) || rightEmpty(text, p))) {
            return EditResult(text.removeRange(p - 1, p), p - 1)
        }
        if (prev == '^' && rightEmpty(text, p)) {
            return EditResult(text.removeRange(p - 1, p), p - 1)
        }
        if (prev == SQRT && rightEmpty(text, p)) {
            return EditResult(text.removeRange(p - 1, p), p - 1)
        }

        // 3) 常规删除：先按原子（sin( / √( / 10^ …），再按单字符
        for (a in CursorModel.ATOMS) {
            if (p >= a.length && text.regionMatches(p - a.length, a, 0, a.length)) {
                return cleanup(text.removeRange(p - a.length, p), p - a.length)
            }
        }
        return cleanup(text.removeRange(p - 1, p), p - 1)
    }

    /** 前删（Delete 语义），规则与退格镜像 */
    fun deleteForward(text: String, pos: Int): EditResult {
        val p = pos.coerceIn(0, text.length)
        if (p >= text.length) return EditResult(text, p)

        emptyGroupAt(text, p)?.let { (o, c) ->
            val s = structStart(text, o)
            return EditResult(text.removeRange(s, c + 1), s)
        }

        // 光标停在 ( 上、且组是空的：整个调用一起删
        if (text[p] == '(') {
            val m = pairs(text)
            val c = m[p]
            if (c == p + 1) {
                val s = structStart(text, p)
                return EditResult(text.removeRange(s, c + 1), s)
            }
        }

        val c0 = text[p]
        if (c0 == DIV && (leftEmpty(text, p) || rightEmpty(text, p + 1))) {
            return EditResult(text.removeRange(p, p + 1), p)
        }
        if (c0 == '^' && rightEmpty(text, p + 1)) return EditResult(text.removeRange(p, p + 1), p)
        if (c0 == SQRT && rightEmpty(text, p + 1)) return EditResult(text.removeRange(p, p + 1), p)

        for (a in CursorModel.ATOMS) {
            if (text.startsWith(a, p)) {
                return cleanup(text.removeRange(p, (p + a.length).coerceAtMost(text.length)), p)
            }
        }
        return cleanup(text.removeRange(p, p + 1), p)
    }

    /**
     * 悬挂结构清理：删完字符后，若留下「两侧全空的 ÷ / ^」或首尾的 ÷ / ^，
     * 一并抹掉。只清理确定无意义的结构，不动用户正在输入的空槽
     * （如刚按下 a/b 得到的 "3÷" 光标在末尾 —— 这是合法待输入状态）。
     */
    fun cleanup(t: String, cursor: Int): EditResult {
        var s = t
        var c = cursor.coerceIn(0, s.length)
        // 结尾的 ÷ / ^ / √：删（此时右侧不可能再输入了）
        while (s.isNotEmpty() && (s.last() == DIV || s.last() == '^' || s.last() == '√') && c >= s.length) {
            s = s.dropLast(1)
            c = s.length
        }
        // 开头的 ÷ / × / ^
        while (s.isNotEmpty() && (s.first() == DIV || s.first() == MUL || s.first() == '^')) {
            s = s.drop(1)
            c = (c - 1).coerceAtLeast(0)
        }
        // 运算符相邻：a÷÷b → a÷b；a+÷b → a+b
        var i = 1
        while (i < s.length) {
            if ((s[i] == DIV || s[i] == MUL || s[i] == '^') && isOp(s[i - 1])) {
                val dropIdx = i
                s = s.removeRange(dropIdx, dropIdx + 1)
                if (c > dropIdx) c--
                i = 1
                continue
            }
            i++
        }
        return EditResult(s, c.coerceIn(0, s.length))
    }

    /**
     * 光标位置是否处在「结构槽」里（分数分子/分母、根号内、括号内、上标指数）。
     * 供 UI 判断按键要不要走槽内语义（比如 × ÷ 留在分数槽里）。
     */
    fun inSlot(t: String, pos: Int): Boolean {
        val m = pairs(t)
        for (i in t.indices) {
            if (t[i] != '(') continue
            val c = m[i]
            if (c > i && pos in (i + 1)..c) return true
        }
        return false
    }
}
