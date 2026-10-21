import io

BASE = r"C:\Users\Administrator\.openclaw\workspace\projects\casio-calc-android\app\src\main\java\io\paimon\fx991"

# ---------- CalcViewModel ----------
p = BASE + r"\CalcViewModel.kt"
s = io.open(p, encoding="utf-8").read()

old = """    fun applyPhotoExpr(expr: String, explain: String) {
        goto(Screen.CALC)
        justEvaluated = false
        expression = expr
        cursor = expr.length"""
new = """    fun applyPhotoExpr(expr: String, explain: String) {
        goto(Screen.CALC)
        justEvaluated = false
        val clean = io.paimon.fx991.engine.InputNorm.normalize(expr)
        expression = clean
        cursor = clean.length"""
assert old in s, "applyPhotoExpr anchor missing"
if "InputNorm.normalize(expr)" not in s:
    s = s.replace(old, new, 1)

anchor = """    private companion object {
        const val ANGLE_SIGN"""
method = '''    /**
     * 外部文本入口（ProcessText 选中文字 / 快捷方式 / 分享）：
     * 先把「长得像但不是」的字符归一化（– ✕ ⁄ 全角括号 零宽空格……），
     * 再插进主行并立即求值，与键盘键入共用同一套管线。
     */
    fun applyIncomingText(raw: String) {
        val clean = io.paimon.fx991.engine.InputNorm.normalize(raw)
        if (clean.isBlank()) return
        goto(Screen.CALC)
        justEvaluated = false
        expression = clean
        cursor = clean.length
        resultText = ""
        previewText = ""
        isError = false
        solutionItems = emptyList()
        resultNote = ""
        lastComplex = null
        polarFormShown = false
        evaluateNow()
    }

''' + anchor
if "fun applyIncomingText" not in s:
    assert anchor in s, "companion anchor missing"
    s = s.replace(anchor, method, 1)

io.open(p, "w", encoding="utf-8", newline="").write(s)
print("CalcViewModel.kt patched")

# ---------- CalcScreen ----------
p2 = BASE + r"\ui\CalcScreen.kt"
s2 = io.open(p2, encoding="utf-8").read()
if "import androidx.compose.runtime.LaunchedEffect" not in s2:
    s2 = s2.replace(
        "import androidx.compose.runtime.CompositionLocalProvider",
        "import androidx.compose.runtime.CompositionLocalProvider\nimport androidx.compose.runtime.LaunchedEffect",
        1,
    )

old3 = """fun CalcApp(vm: CalcViewModel = viewModel()) {
    val dark = when (vm.settings.theme) {"""
new3 = """fun CalcApp(vm: CalcViewModel = viewModel()) {
    // 外部文本（ProcessText 选中文字 / 快捷方式）→ 主行并立即求值
    val incoming = io.paimon.fx991.IncomingText.pending
    LaunchedEffect(incoming) {
        if (incoming != null) {
            vm.applyIncomingText(incoming)
            io.paimon.fx991.IncomingText.consume()
        }
    }
    val dark = when (vm.settings.theme) {"""
assert old3 in s2, "CalcApp anchor missing"
if "IncomingText.pending" not in s2:
    s2 = s2.replace(old3, new3, 1)
io.open(p2, "w", encoding="utf-8", newline="").write(s2)
print("CalcScreen.kt patched")
