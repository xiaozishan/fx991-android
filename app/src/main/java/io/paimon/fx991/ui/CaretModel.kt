package io.paimon.fx991.ui

/** 同一个源码偏移可以落在结构内部，也可以落在结构外的不同层级。 */
data class Caret(val offset: Int, val exit: Int = 0)

internal data class CaretBoundary(val node: Nat, val before: Boolean)

/** 移动和渲染共用同一棵布局树来识别结构边界。 */
internal class CaretLayout(val root: Nat) {
    private val structures = ArrayList<Nat>()
    private val hiddenGroupEdges = HashMap<Int, Int>()

    init {
        collect(root)
    }

    private fun collect(node: Nat) {
        when (node) {
            is Nat.Row -> node.items.forEach(::collect)
            is Nat.Frac -> {
                for (slot in listOf(node.n, node.d)) {
                    val content = groupContent(slot)
                    if (content != null) {
                        hiddenGroupEdges[slot.srcStart] = content.srcStart
                        if (content.srcEnd < slot.srcEnd) hiddenGroupEdges[slot.srcEnd] = content.srcEnd
                    }
                    collect(slot)
                }
            }
            is Nat.Sup -> {
                collect(node.base)
                collect(node.exp)
            }
            is Nat.Sqrt -> collect(node.a)
            is Nat.Integ -> {
                collect(node.body)
                collect(node.lo)
                collect(node.hi)
            }
            else -> Unit
        }
        if ((node is Nat.Frac || node is Nat.Sup || node is Nat.Sqrt) &&
            node.srcStart >= 0 && node.srcEnd > node.srcStart
        ) {
            structures.add(node)
        }
    }

    private fun endsAt(offset: Int) = structures.filter { it.srcEnd == offset }.sortedByDescending { it.srcStart }
    private fun startsAt(offset: Int) = structures.filter { it.srcStart == offset }.sortedBy { it.srcEnd }

    fun boundary(caret: Caret): CaretBoundary? {
        if (caret.exit <= 0) return null
        val index = caret.exit - 1
        endsAt(caret.offset).getOrNull(index)?.let { return CaretBoundary(it, before = false) }
        return startsAt(caret.offset).getOrNull(index)?.let { CaretBoundary(it, before = true) }
    }

    fun maxExit(offset: Int): Int = maxOf(endsAt(offset).size, startsAt(offset).size)

    fun normalize(caret: Caret): Caret {
        val exit = caret.exit.coerceIn(0, maxExit(caret.offset))
        if (exit > 0) return Caret(caret.offset, exit)
        var offset = caret.offset
        repeat(hiddenGroupEdges.size) {
            offset = hiddenGroupEdges[offset] ?: return Caret(offset)
        }
        return Caret(offset)
    }

    /** 返回该偏移从左到右的层级，略过分数槽里被隐藏的括号边缘。 */
    fun exitsAt(offset: Int): IntProgression {
        val inner = if (offset in hiddenGroupEdges) 1 else 0
        val ends = endsAt(offset).size
        return if (ends > 0) inner..ends else startsAt(offset).size downTo inner
    }
}

object CaretModel {
    fun maxExit(expr: String, offset: Int): Int =
        CaretLayout(buildNat(expr)).maxExit(offset.coerceIn(0, expr.length))

    fun normalize(expr: String, caret: Caret): Caret =
        CaretLayout(buildNat(expr)).normalize(caret.copy(offset = caret.offset.coerceIn(0, expr.length)))

    fun right(expr: String, caret: Caret): Caret = horizontal(expr, caret, right = true)
    fun left(expr: String, caret: Caret): Caret = horizontal(expr, caret, right = false)

    private fun horizontal(expr: String, caret: Caret, right: Boolean): Caret {
        val layout = CaretLayout(buildNat(expr))
        val current = layout.normalize(caret.copy(offset = caret.offset.coerceIn(0, expr.length)))
        var offset = current.offset
        val exits = layout.exitsAt(offset).let { if (right) it else it.reversed() }
        if (current.exit in exits && current.exit != exits.last) {
            return Caret(offset, current.exit + exits.step)
        }

        repeat(expr.length + 1) {
            offset = if (right) {
                if (offset == expr.length) 0 else offset + 1
            } else {
                if (offset == 0) expr.length else offset - 1
            }
            val next = layout.exitsAt(offset)
            if (!next.isEmpty()) return Caret(offset, if (right) next.first else next.last)
        }
        return Caret(offset)
    }

    fun up(expr: String, caret: Caret): Caret = vertical(expr, caret, up = true)
    fun down(expr: String, caret: Caret): Caret = vertical(expr, caret, up = false)

    private fun vertical(expr: String, caret: Caret, up: Boolean): Caret {
        val layout = CaretLayout(buildNat(expr))
        val current = layout.normalize(caret.copy(offset = caret.offset.coerceIn(0, expr.length)))
        val offset = current.offset
        val boundary = layout.boundary(current)?.node
        CursorModel.verticalTarget(layout.root, offset, up, boundary)?.let { return Caret(it) }
        if (CursorModel.insideSlot(layout.root, offset, boundary)) return current
        val edge = if (up) 0 else expr.length
        return Caret(edge, layout.maxExit(edge))
    }

    fun isBeforeStructure(expr: String, caret: Caret): Boolean =
        CaretLayout(buildNat(expr)).boundary(caret)?.before == true

    /** 调试/测试：当前光标的结构路径描述 */
    fun describe(expr: String, caret: Caret): String {
        val boundary = CaretLayout(buildNat(expr)).boundary(caret) ?: return "inner@${caret.offset}"
        val side = if (boundary.before) "before" else "after"
        return "$side(${boundary.node.srcStart}..${boundary.node.srcEnd})"
    }
}
