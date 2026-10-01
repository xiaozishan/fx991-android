package io.paimon.fx991.engine

// ---------------------------------------------------------------------------
// 批次 K3：主行「就地括号调用」的入口拦截（纯 Kotlin，JVM 可回归）
//
//   solve(方程)  → 剥掉外壳交给 EquationSolver（solve(x^2-1=0) / solve(2x+3=7)）
//   sto(式, 变量) → 求值后存入寄存器（sto(5+3, A)；M 走存储器）
//
// 这两个不是纯函数（一个有求解报告、一个有写寄存器副作用），
// 所以由 ViewModel 在按 = 时拦截；这里只放无副作用的拆解逻辑。
// ---------------------------------------------------------------------------

object InlineFuncs {

    /**
     * 剥掉整串最外层的 `name(...)` 包装；括号不配平 / 不是整串包装时返回 null。
     * 例：unwrapCall("solve(2x+3=7)", "solve") = "2x+3=7"；unwrapCall("solve(x)+1", "solve") = null
     */
    @JvmStatic
    fun unwrapCall(src: String, name: String): String? {
        val t = src.trim()
        val head = name + "("
        if (!t.startsWith(head) || !t.endsWith(")")) return null
        val inner = t.substring(head.length, t.length - 1)
        var depth = 0
        for (c in inner) {
            when (c) {
                '(' -> depth++
                ')' -> {
                    depth--
                    if (depth < 0) return null      // solve(a),(b) 之类：不是整串包装
                }
            }
        }
        return if (depth == 0) inner else null
    }

    /**
     * solve(...) 外壳 → 交给方程求解器的内串。
     * 内串不带等号时按 f(x)=0 处理（追加 "=0"）。空白 / 非 solve 包装返回 null。
     */
    @JvmStatic
    fun unwrapSolve(src: String): String? {
        val inner = unwrapCall(src, "solve") ?: return null
        val t = inner.trim()
        if (t.isEmpty()) return null
        return if (t.contains('=')) t else "$t=0"
    }

    /**
     * sto(式, 变量) → (式子, 变量名)；变量只认 A–F / x / y / M（大小写归一：
     * 单字母 a–f 归大写，m 归大写 M）。结构不对返回 null。
     * 变量名取最后一个**顶层**逗号之后的内容（式子里允许嵌套逗号，如 sto(mean(1,2),B)）。
     */
    @JvmStatic
    fun parseSto(src: String): Pair<String, String>? {
        val inner = unwrapCall(src, "sto") ?: return null
        var depth = 0
        var cut = -1
        for (i in inner.indices) {
            when (inner[i]) {
                '(' -> depth++
                ')' -> depth--
                ',' -> if (depth == 0) cut = i
            }
        }
        if (cut < 0) return null
        val expr = inner.substring(0, cut).trim()
        var name = inner.substring(cut + 1).trim()
        if (expr.isEmpty()) return null
        name = when (name) {
            "a", "b", "c", "d", "e", "f" -> name.uppercase()
            "m" -> "M"
            else -> name
        }
        if (name !in Registers.VARIABLES && name != "M") return null
        return expr to name
    }
}
