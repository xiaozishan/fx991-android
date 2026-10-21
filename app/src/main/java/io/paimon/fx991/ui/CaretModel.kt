package io.paimon.fx991.ui

// ---------------------------------------------------------------------------
// 光标模型 v2：结构层级（Caret = 偏移量 + 已跳出的层数）
//
//   问题：分数/指数/根号的「内容末尾」和「结构右边界」在字符串里是同一个偏移量。
//   旧模型只有 Int 偏移，所以 9÷6 末尾按 → 只能直接绕回开头，跳过了
//   「分数右边 → 分数左边 → 分子开头」这两级。
//
//   新模型：Caret(offset, exit)
//     exit = 0      在最内层（分母末尾 / 指数末尾）
//     exit = 1      跳出一层（分数整体的右边）
//     exit = n      跳出 n 层
//
//   向右：内部末尾 → 逐层跳出 → 到文末再绕到文首的最外层 → 再逐层进入
//   向左：镜像。
//   结构区间直接取自 buildNat（渲染同一套解析器），不另写一份语法。
// ---------------------------------------------------------------------------

data class Caret(val offset: Int, val exit: Int = 0)

object CaretModel {

    private data class Span(val s: Int, val e: Int)

    /** 有「内容边界 == 结构边界」歧义的结构：分数 / 上标 / 根号 */
    private fun spans(expr: String): List<Span> {
        val out = ArrayList<Span>()
        val root = try {
            buildNat(expr)
        } catch (_: Exception) {
            return out
        }
        collect(root, out)
        return out
    }

    private fun collect(n: Nat, out: MutableList<Span>) {
        when (n) {
            is Nat.Row -> n.items.forEach { collect(it, out) }
            is Nat.Frac -> {
                collect(n.n, out)
                collect(n.d, out)
                push(n, out)
            }
            is Nat.Sup -> {
                collect(n.base, out)
                collect(n.exp, out)
                push(n, out)
            }
            is Nat.Sqrt -> {
                collect(n.a, out)
                push(n, out)
            }
            else -> Unit
        }
    }

    private fun push(n: Nat, out: MutableList<Span>) {
        if (n.srcStart >= 0 && n.srcEnd > n.srcStart) out.add(Span(n.srcStart, n.srcEnd))
    }

    /** 在当前偏移处「结束」的结构，最内层在前 */
    private fun endsAt(sp: List<Span>, o: Int): List<Span> =
        sp.filter { it.e == o }.sortedByDescending { it.s }

    /** 在当前偏移处「开始」的结构，最内层在前 */
    private fun startsAt(sp: List<Span>, o: Int): List<Span> =
        sp.filter { it.s == o }.sortedBy { it.e }

    /** 该偏移处允许的最大 exit（= 包着它的结构层数） */
    fun maxExit(expr: String, offset: Int): Int {
        if (expr.isEmpty()) return 0
        val o = offset.coerceIn(0, expr.length)
        val sp = spans(expr)
        return maxOf(endsAt(sp, o).size, startsAt(sp, o).size)
    }

    /** 把任意 (offset, exit) 收敛到合法状态 */
    fun normalize(expr: String, caret: Caret): Caret {
        val o = caret.offset.coerceIn(0, expr.length)
        return Caret(o, caret.exit.coerceIn(0, maxExit(expr, o)))
    }

    /**
     * 右移一格（视觉阅读顺序）：
     *   1. 内容末尾还在结构里 → 先跳出一层（同一偏移，exit+1）
     *   2. 已在最右端且跳完 → 绕到文首最外层
     *   3. 在结构的左外侧 → 进入一层（exit-1）
     *   4. 普通右移
     */
    fun right(expr: String, caret: Caret): Caret {
        val len = expr.length
        if (len == 0) return Caret(0, 0)
        val o = caret.offset.coerceIn(0, len)
        val e = caret.exit.coerceAtLeast(0)
        val sp = spans(expr)
        val ends = endsAt(sp, o)
        val starts = startsAt(sp, o)

        if (e < ends.size) return Caret(o, e + 1)

        if (o >= len) {
            return Caret(0, startsAt(sp, 0).size)
        }

        if (e > 0 && starts.size >= e) return Caret(o, e - 1)

        return Caret(o + 1, 0)
    }

    /**
     * 左移一格（与 right 镜像）：
     *   1. 已在最左端 → 绕到末尾最外层
     *   2. 在结构右外侧 → 进入一层
     *   3. 普通左移
     */
    fun left(expr: String, caret: Caret): Caret {
        val len = expr.length
        if (len == 0) return Caret(0, 0)
        val o = caret.offset.coerceIn(0, len)
        val e = caret.exit.coerceAtLeast(0)
        val sp = spans(expr)

        if (o == 0) return Caret(len, endsAt(sp, len).size)
        if (e > 0) return Caret(o, e - 1)
        return Caret(o - 1, 0)
    }

    /** 调试/测试：当前光标的结构路径描述 */
    fun describe(expr: String, caret: Caret): String {
        val sp = spans(expr)
        val e = endsAt(sp, caret.offset).getOrNull(caret.exit - 1)
        val s = startsAt(sp, caret.offset).getOrNull(caret.exit - 1)
        return when {
            caret.exit == 0 -> "inner@${caret.offset}"
            e != null -> "after(${e.s}..${e.e})"
            s != null -> "before(${s.s}..${s.e})"
            else -> "exit${caret.exit}@${caret.offset}"
        }
    }
}
