import io, re

p = r"C:\Users\Administrator\.openclaw\workspace\projects\casio-calc-android\app\src\main\java\io\paimon\fx991\CalcViewModel.kt"
s = io.open(p, encoding="utf-8").read()

# ---------------- 1) 快照带 exit ----------------
s = s.replace("    private data class EditSnapshot(val text: String, val cursor: Int)",
              "    private data class EditSnapshot(val text: String, val cursor: Int, val exit: Int)", 1)
s = s.replace("undoStack.addLast(EditSnapshot(expression, cursor))",
              "undoStack.addLast(EditSnapshot(expression, cursor, cursorExit))")
s = s.replace("redoStack.addLast(EditSnapshot(expression, cursor))",
              "redoStack.addLast(EditSnapshot(expression, cursor, cursorExit))")

# ---------------- 2) cursorExit 状态 ----------------
old_cursor = """    /** 批次 K3-A：主行可见光标 = 表达式字符串里的偏移量（0..length） */
    var cursor by mutableStateOf(0)
        private set
"""
new_cursor = """    /** 批次 K3-A：主行可见光标 = 表达式字符串里的偏移量（0..length） */
    var cursor by mutableStateOf(0)
        private set

    /**
     * 光标的结构层级（v2.1）：
     *   0 = 还在最内层（分母末尾 / 指数末尾 / 根号内末尾）
     *   n = 已从 n 层结构里「跳出」到外侧
     * 分数/指数/根号的内容末尾与结构边界共用同一个偏移量，只有这个层级能区分它们。
     */
    var cursorExit by mutableStateOf(0)
        private set
"""
assert old_cursor in s
s = s.replace(old_cursor, new_cursor, 1)

# ---------------- 3) 左右移动改走 CaretModel ----------------
start = s.index("    fun moveCursorLeft() {")
end = s.index("    /** 批次 K3-symbolic：方向键上下", start)
new_move = '''    fun moveCursorLeft() {
        val next = io.paimon.fx991.ui.CaretModel.left(
            expression,
            io.paimon.fx991.ui.Caret(cursor, cursorExit),
        )
        cursor = next.offset
        cursorExit = next.exit
    }

    fun moveCursorRight() {
        val next = io.paimon.fx991.ui.CaretModel.right(
            expression,
            io.paimon.fx991.ui.Caret(cursor, cursorExit),
        )
        cursor = next.offset
        cursorExit = next.exit
    }

'''
s = s[:start] + new_move + s[end:]

# 上下移动后回到内层
s = s.replace("    fun moveCursorUp() {\n        cursor = io.paimon.fx991.ui.CursorModel.moveUp(expression, cursor)",
              "    fun moveCursorUp() {\n        cursorExit = 0\n        cursor = io.paimon.fx991.ui.CursorModel.moveUp(expression, cursor)", 1)
s = s.replace("    fun moveCursorDown() {\n        val exit = io.paimon.fx991.ui.CursorModel.exitSupIfAtEnd(expression, cursor)",
              "    fun moveCursorDown() {\n        cursorExit = 0\n        val exit = io.paimon.fx991.ui.CursorModel.exitSupIfAtEnd(expression, cursor)", 1)

# ---------------- 4) 插入：外侧插入自动补 × ----------------
old_insert_body = """        } else {
            // 批次 K6：分数槽里直接打 × ÷ 留在槽内（分母吃到乘积，不用手动括号）
            val r = if (text == "\\u00D7" || text == "\\u00F7")
                io.paimon.fx991.ui.CursorModel.insertOperator(expression, cursor, text)
            else
                io.paimon.fx991.ui.CursorModel.insert(expression, cursor, text)
            expression = r.text
            cursor = r.cursor
        }"""
new_insert_body = """        } else {
            // 批次 K6：分数槽里直接打 × ÷ 留在槽内（分母吃到乘积，不用手动括号）
            val r = if (cursorExit > 0)
                insertOutside(text)
            else if (text == "\\u00D7" || text == "\\u00F7")
                io.paimon.fx991.ui.CursorModel.insertOperator(expression, cursor, text)
            else
                io.paimon.fx991.ui.CursorModel.insert(expression, cursor, text)
            expression = r.text
            cursor = r.cursor
            cursorExit = 0
        }"""
assert old_insert_body in s
s = s.replace(old_insert_body, new_insert_body, 1)

# justEvaluated 分支也要复位
s = s.replace("            cursor = expression.length\n        } else {",
              "            cursor = expression.length\n            cursorExit = 0\n        } else {", 1)

# 新增 insertOutside
anchor = "    /** 批次 K3：就地括号调用"
helper = '''    /**
     * 光标已跳出结构（cursorExit > 0）时的插入：
     * 直接追加数字会并进分母/指数（9÷6 + 3 → 9÷63），所以按位置补一个 ×。
     *   在结构右边：9÷6 + 3  → 9÷6×3
     *   在结构左边：2 + 9÷6  → 2×9÷6
     */
    private fun insertOutside(text: String): io.paimon.fx991.ui.EditResult {
        val operand = text.isNotEmpty() && run {
            val c = text[0]
            c.isDigit() || c == '.' || c.isLetter() || c == '\\u221A' || c == '('
        }
        if (!operand) return io.paimon.fx991.ui.CursorModel.insert(expression, cursor, text)
        return if (cursor == 0) {
            val r = io.paimon.fx991.ui.CursorModel.insert(expression, 0, text + "\\u00D7")
            io.paimon.fx991.ui.EditResult(r.text, r.cursor - 1)
        } else {
            io.paimon.fx991.ui.CursorModel.insert(expression, cursor, "\\u00D7" + text)
        }
    }

'''
s = s.replace(anchor, helper + anchor, 1)

# ---------------- 5) 其它会动光标的地方复位 ----------------
def reset_after(marker, count=1):
    global s
    s = s.replace(marker, marker, count)

# backspace 后
s = s.replace("        val r = io.paimon.fx991.ui.CursorModel.backspace(expression, cursor)\n        expression = r.text\n        cursor = r.cursor",
              "        val r = io.paimon.fx991.ui.CursorModel.backspace(expression, cursor)\n        expression = r.text\n        cursor = r.cursor\n        cursorExit = 0", 1)
# 分数 / DMS / 双曲
for fn in ["insertFraction", "insertDmsSeparator"]:
    s = s.replace(f"        val r = io.paimon.fx991.ui.CursorModel.{fn}(expression, cursor)\n        expression = r.text\n        cursor = r.cursor",
                  f"        val r = io.paimon.fx991.ui.CursorModel.{fn}(expression, cursor)\n        expression = r.text\n        cursor = r.cursor\n        cursorExit = 0", 1)
# 求值 / 全清 / 点按定位
s = s.replace("    private fun clearAll() {\n        expression = \"\"\n        cursor = 0",
              "    private fun clearAll() {\n        expression = \"\"\n        cursor = 0\n        cursorExit = 0", 1)
s = s.replace("    fun tapToCursor(fraction: Float) {\n", "    fun tapToCursor(fraction: Float) {\n        cursorExit = 0\n", 1)

# evaluateNow 里设置光标处
s = re.sub(r"(private fun evaluateNow\(\) \{)",
           r"\1\n        cursorExit = 0", s, count=1)

# undo / redo 恢复层级
s = s.replace("        expression = snap.text\n        cursor = snap.cursor\n        justEvaluated = false",
              "        expression = snap.text\n        cursor = snap.cursor\n        cursorExit = snap.exit\n        justEvaluated = false", 1)
s = s.replace("        expression = snap.text\n        cursor = snap.cursor\n        justEvaluated = false",
              "        expression = snap.text\n        cursor = snap.cursor\n        cursorExit = snap.exit\n        justEvaluated = false", 1)

io.open(p, "w", encoding="utf-8", newline="").write(s)
print("viewmodel caret wired; cursorExit occurrences:", s.count("cursorExit"))
