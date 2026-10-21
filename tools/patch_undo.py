import io, re

base = r"C:\Users\Administrator\.openclaw\workspace\projects\casio-calc-android\app\src\main\java\io\paimon\fx991"

# ---------------- Keys.kt：新增 Undo / Redo 动作 ----------------
p = base + r"\ui\Keys.kt"
s = io.open(p, encoding="utf-8").read()
if "data object Undo" not in s:
    s = s.replace(
        "    data object PadOk : KeyAction\n",
        "    data object PadOk : KeyAction\n\n    // ---- 输入体验重构：撤销 / 重做 ----\n    data object Undo : KeyAction\n    data object Redo : KeyAction\n",
        1,
    )
io.open(p, "w", encoding="utf-8", newline="").write(s)

# ---------------- CalcViewModel.kt ----------------
p = base + r"\CalcViewModel.kt"
s = io.open(p, encoding="utf-8").read()

anchor = "    /** 批次 K3-A：主行可见光标 = 表达式字符串里的偏移量（0..length） */"
assert anchor in s

undo_block = '''    // ---- 输入体验重构（v1.13）：撤销 / 重做 ----
    // 每次结构性编辑前把 (表达式, 光标) 压栈；上限 200 步。
    // undoCount/redoCount 是给 Compose 的重组信号（栈本身不可观察）。

    private data class EditSnapshot(val text: String, val cursor: Int)

    private val undoStack = ArrayDeque<EditSnapshot>()
    private val redoStack = ArrayDeque<EditSnapshot>()

    var undoCount by mutableStateOf(0)
        private set
    var redoCount by mutableStateOf(0)
        private set

    val canUndo: Boolean get() = undoCount > 0
    val canRedo: Boolean get() = redoCount > 0

    private fun pushUndo() {
        val last = undoStack.lastOrNull()
        if (last != null && last.text == expression && last.cursor == cursor) return
        undoStack.addLast(EditSnapshot(expression, cursor))
        if (undoStack.size > 200) undoStack.removeFirst()
        redoStack.clear()
        undoCount = undoStack.size
        redoCount = 0
    }

    fun undoEdit() {
        val snap = undoStack.removeLastOrNull() ?: return
        redoStack.addLast(EditSnapshot(expression, cursor))
        expression = snap.text
        cursor = snap.cursor
        justEvaluated = false
        resultText = ""
        isError = false
        histCursor = -1
        undoCount = undoStack.size
        redoCount = redoStack.size
        refreshPreview()
    }

    fun redoEdit() {
        val snap = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(EditSnapshot(expression, cursor))
        expression = snap.text
        cursor = snap.cursor
        justEvaluated = false
        resultText = ""
        isError = false
        histCursor = -1
        undoCount = undoStack.size
        redoCount = redoStack.size
        refreshPreview()
    }

'''
s = s.replace(anchor, undo_block + anchor, 1)

# 分发
s = s.replace(
    "            KeyAction.PadOk -> evaluateNow()\n",
    "            KeyAction.PadOk -> evaluateNow()\n            KeyAction.Undo -> undoEdit()\n            KeyAction.Redo -> redoEdit()\n",
    1,
)

# 在各类编辑入口压栈
s = s.replace(
    "    private fun insert(text: String) {\n        if (shouldRemainNewEntry()) {",
    "    private fun insert(text: String) {\n        pushUndo()\n        if (shouldRemainNewEntry()) {",
    1,
)
s = s.replace(
    "        val r = io.paimon.fx991.ui.CursorModel.insertFraction(expression, cursor)",
    "        pushUndo()\n        val r = io.paimon.fx991.ui.CursorModel.insertFraction(expression, cursor)",
    1,
)
s = s.replace(
    "        val r = io.paimon.fx991.ui.CursorModel.insertDmsSeparator(expression, cursor)",
    "        pushUndo()\n        val r = io.paimon.fx991.ui.CursorModel.insertDmsSeparator(expression, cursor)",
    1,
)
s = s.replace(
    "        if (expression.isEmpty()) return\n        val r = io.paimon.fx991.ui.CursorModel.backspace(expression, cursor)",
    "        if (expression.isEmpty()) return\n        pushUndo()\n        val r = io.paimon.fx991.ui.CursorModel.backspace(expression, cursor)",
    1,
)
# 双曲循环里替换函数名的那一步
s = s.replace(
    "                val next = names[(ix + 1) % names.size]",
    "                pushUndo()\n                val next = names[(ix + 1) % names.size]",
    1,
)
io.open(p, "w", encoding="utf-8", newline="").write(s)
print("viewmodel patched; pushUndo sites:", s.count("pushUndo()"))
