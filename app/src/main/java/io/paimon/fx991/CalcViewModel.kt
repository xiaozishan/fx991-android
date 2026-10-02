package io.paimon.fx991

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.paimon.fx991.engine.AngleMode
import io.paimon.fx991.engine.ApiConfig
import io.paimon.fx991.engine.ApiPreset
import io.paimon.fx991.engine.CalcEngine
import io.paimon.fx991.engine.CalcMathError
import io.paimon.fx991.engine.CalcSyntaxError
import io.paimon.fx991.engine.CalcValue
import io.paimon.fx991.engine.ComplexRect
import io.paimon.fx991.engine.EquationSolver
import io.paimon.fx991.engine.FourierOps
import io.paimon.fx991.engine.InlineFuncs
import io.paimon.fx991.engine.MatrixStore
import io.paimon.fx991.engine.NumberNotation
import io.paimon.fx991.engine.NumericError
import io.paimon.fx991.engine.NumericOps
import io.paimon.fx991.engine.PhotoNet
import io.paimon.fx991.engine.PolarForm
import io.paimon.fx991.engine.RandomOps
import io.paimon.fx991.engine.Registers
import io.paimon.fx991.engine.Sexagesimal
import io.paimon.fx991.engine.SolveItem
import io.paimon.fx991.engine.SolveKind
import io.paimon.fx991.engine.Unified
import io.paimon.fx991.engine.UpdateCheck
import io.paimon.fx991.engine.UpdateException
import io.paimon.fx991.engine.UpdateNetException
import io.paimon.fx991.engine.ReleaseInfo
import io.paimon.fx991.engine.UserFnDef
import io.paimon.fx991.engine.UserFunctions
import io.paimon.fx991.engine.Value
import io.paimon.fx991.engine.VectorStore
import io.paimon.fx991.ui.KeyAction
import io.paimon.fx991.ui.APP_VERSION
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.floor

data class HistoryEntry(val expr: String, val result: String)

/** 结果显示格式：分数 / 带分数 / 小数（S⇔D 循环切换） */enum class DisplayMode(val shortLabel: String) {
    FRAC("FRAC"),
    MIXED("MXD"),
    DEC("DEC");

    fun next(): DisplayMode = when (this) {
        FRAC -> MIXED
        MIXED -> DEC
        DEC -> FRAC
    }
}

class CalcViewModel(app: Application) : AndroidViewModel(app) {

    var expression by mutableStateOf("")
        private set

    /** 批次 K3-A：主行可见光标 = 表达式字符串里的偏移量（0..length） */
    var cursor by mutableStateOf(0)
        private set

    /** 批次 K3-B：二级界面自然输入的焦点状态（哪个字段在被自家键盘编辑） */
    val natInput = io.paimon.fx991.ui.NatInput()

    fun moveCursorLeft() {
        cursor = io.paimon.fx991.ui.CursorModel.moveLeft(expression, cursor)
    }

    fun moveCursorRight() {
        cursor = io.paimon.fx991.ui.CursorModel.moveRight(expression, cursor)
    }

    /** 批次 K3-symbolic：方向键上下 = 二维结构内垂直移光标（分数分子↔分母、上标↔基线、根号内外、∫上下限） */
    fun moveCursorUp() {
        cursor = io.paimon.fx991.ui.CursorModel.moveUp(expression, cursor)
    }

    fun moveCursorDown() {
        cursor = io.paimon.fx991.ui.CursorModel.moveDown(expression, cursor)
    }

    var resultText by mutableStateOf("")
        private set

    var previewText by mutableStateOf("")
        private set

    var isError by mutableStateOf(false)
        private set

    var angleMode by mutableStateOf(AngleMode.DEG)
        private set

    var memory by mutableStateOf(0.0)
        private set

    var memorySet by mutableStateOf(false)
        private set

    var ans by mutableStateOf(0.0)
        private set

    /** 上上次结果 PreAns（与 Ans 并存，历史里维护） */
    var preAns by mutableStateOf(0.0)
        private set

    /** 变量寄存器（STO 存入 A–F / x / y） */
    private val registers = Registers()

    /** STO 面板里的确认提示 */
    var storeMessage by mutableStateOf("")
        private set

    /** 已存入的变量（用于界面回显） */
    var variables by mutableStateOf<Map<String, Double>>(emptyMap())
        private set

    /** SHIFT / ALPHA 双功能层 */
    var shiftActive by mutableStateOf(false)
        private set

    var alphaActive by mutableStateOf(false)
        private set

    var stoActive by mutableStateOf(false)
        private set

    var rclActive by mutableStateOf(false)
        private set

    var displayMode by mutableStateOf(DisplayMode.FRAC)
        private set

    /** 主界面：计算 / 微分方程（MODE 菜单切换） */
    var screen by mutableStateOf(Screen.CALC)
        private set

    /** 当前覆盖层：菜单 / 设置 / 更多 / 关于 / 拍照说明 / 历史 / 数值功能对话框 */
    var overlay by mutableStateOf(Overlay.NONE)
        private set

    /** 应用设置（主题 / 精度 / 震动） */
    var settings by mutableStateOf(CalculatorSettings())
        private set

    /** 数值功能对话框（∫dx / d/dx / Σ / SOLVE / Limit / CALC / °′″ / hyp） */
    var funcDialog by mutableStateOf<FuncDialog?>(null)
        private set

    /** 最近一次 a∠θ / 复数结果的直角坐标；S⇔D 在 直角 ⇄ 极坐标 之间切 */
    private var lastComplex: ComplexRect? = null
    private var polarFormShown = false

    /** 矩阵 / 向量变量（统一输入面：主行可直接引用 MatA / VctA） */
    val matrixStore = MatrixStore()
    val vectorStore = VectorStore()

    /** 批次 G：已定义的 GeoGebra 式用户函数（镜像 UserFunctions 登记表，代数区展示用） */
    var definedFunctions by mutableStateOf<List<UserFnDef>>(emptyList())
        private set

    fun syncUserFuncs() {
        definedFunctions = UserFunctions.list()
    }

    /** 代数区：删一个函数 */
    fun removeUserFunction(name: String) {
        UserFunctions.remove(name)
        syncUserFuncs()
    }

    /** 代数区：直接输入定义（返回提示文案；非法抛 NumericError / CalcSyntaxError） */
    fun defineFromAlgebra(src: String): String {
        val def = UserFunctions.tryDefine(src.trim())
            ?: throw NumericError("不是有效的定义；若函数已存在，请用 := 重定义")
        syncUserFuncs()
        return "已定义 ${def.fullText()}"
    }

    /** 代数区：删一个 STO 变量 */
    fun removeVariable(name: String) {
        registers.remove(name)
        variables = registers.snapshot()
    }

    /** 方程求解的多解列表（可逐条插入主行） */
    var solutionItems by mutableStateOf<List<SolveItem>>(emptyList())
        private set

    /** 求解附注（无解原因 / 扫描区间 …） */
    var resultNote by mutableStateOf("")
        private set

    fun openOverlay(o: Overlay) {
        overlay = o
    }

    fun closeOverlay() {
        overlay = Overlay.NONE
        funcDialog = null
    }

    fun goto(target: Screen) {
        screen = target
        overlay = Overlay.NONE
        funcDialog = null
        natInput.clear()
    }

    fun openFunc(kind: FuncKind) {
        funcDialog = newFuncDialog(kind, expression)
        overlay = Overlay.FUNC
        natInput.dismiss()
        cancelLayers()
    }

    /** hyp 对话框里点一个双曲函数 → 直接写进表达式 */
    fun insertHyper(name: String) {
        closeOverlay()
        insert(name + "(")
    }

    // ---- 设置项 ----
    fun setTheme(t: AppTheme) {
        settings = settings.copy(theme = t)
    }

    fun setAngle(m: AngleMode) {
        angleMode = m
        reformatResult()
        refreshPreview()
    }

    fun setPrecision(p: PrecisionMode) {
        settings = settings.copy(precision = p)
        reformatResult()
    }

    fun setSigDigits(n: Int) {
        settings = settings.copy(sigDigits = n.coerceIn(1, 15))
        reformatResult()
    }

    fun setDecimals(n: Int) {
        settings = settings.copy(decimals = n.coerceIn(0, 12))
        reformatResult()
    }

    fun setVibration(b: Boolean) {
        settings = settings.copy(vibration = b)
    }

    /** 数字显示模式：普通 / 科学记数 / 工程记数 */
    fun setNotation(n: NumberNotation) {
        settings = settings.copy(notation = n)
        reformatResult()
    }

    /** ENG 键：切工程记数法显示（再按一次回普通） */
    fun toggleEng() {
        settings = settings.copy(
            notation = if (settings.notation == NumberNotation.ENG) NumberNotation.NORM
            else NumberNotation.ENG,
        )
        reformatResult()
    }

    /** Ran#：插入一个 0–1 均匀随机数 */
    fun insertRandom() {
        insert(CalcEngine.format(RandomOps.uniform(), 10, null))
    }

    /** 分数显示：假分数 / 带分数 / 小数（与 S⇔D 同一个状态） */
    fun chooseDisplayMode(m: DisplayMode) {
        displayMode = m
        reformatResult()
    }

    val history = mutableStateListOf<HistoryEntry>()

    /** 求值后置位（此时主行显示结果、光标隐藏）；用 Compose 状态让 LCD 联动 */
    var justEvaluated by mutableStateOf(false)
        private set
    private var mrcArmed = false
    private var histCursor = -1
    private var lastValue: Double? = null

    /** 上一次的精确结果（用于 S⇔D 在分数/小数之间切换） */
    private var lastExact: io.paimon.fx991.engine.Value? = null

    private fun memValue(): Double = if (memorySet) memory else 0.0

    fun cancelLayers() {
        shiftActive = false
        alphaActive = false
    }

    private fun currentValue(): Double {
        if (expression.isNotBlank()) {
            try {
                return CalcEngine.evaluate(
                    CalcEngine.autoClose(expression), angleMode, ans, memValue(), preAns, varsMap(),
                )
            } catch (_: Exception) {
                // 落到下面的兜底
            }
        }
        lastValue?.let { return it }
        return ans
    }

    /** 供表达式代入的 STO 变量表（A–F / x / y） */
    private fun varsMap(): Map<String, Double> = registers.snapshot()

    fun onAction(action: KeyAction) {
        val layer = shiftActive || alphaActive
        when (action) {
            KeyAction.Shift -> {
                shiftActive = !shiftActive
                alphaActive = false
                return
            }
            KeyAction.Alpha -> {
                alphaActive = !alphaActive
                shiftActive = false
                return
            }
            else -> Unit
        }
        if (layer) cancelLayers()
        if (action !is KeyAction.Mrc) mrcArmed = false
        when (action) {
            KeyAction.Ac -> clearAll()
            KeyAction.Del -> backspace()
            KeyAction.Equals -> evaluateNow()
            KeyAction.ToggleAngle -> toggleAngle()
            KeyAction.MPlus -> memoryOp(1.0)
            KeyAction.MMinus -> memoryOp(-1.0)
            KeyAction.Mrc -> mrc()
            KeyAction.HistUp -> historyUp()
            KeyAction.HistDown -> historyDown()
            // 批次 K3-symbolic：方向键四向全是主行光标移动（上下 = 二维结构内垂直走；历史改走 = 的 SHIFT 层面板）
            KeyAction.PadUp -> moveCursorUp()
            KeyAction.PadDown -> moveCursorDown()
            KeyAction.PadLeft -> moveCursorLeft()
            KeyAction.PadRight -> moveCursorRight()
            KeyAction.PadOk -> evaluateNow()
            KeyAction.Sd -> cycleDisplay()
            KeyAction.FracFormat -> cycleDisplay()
            KeyAction.Fraction -> insertFractionKey()
            KeyAction.SignToggle -> insert("\u2212")
            KeyAction.Mode, KeyAction.Menu -> openOverlay(Overlay.MODE)
            KeyAction.Settings -> openOverlay(Overlay.SETTINGS)
            KeyAction.More -> openOverlay(Overlay.MORE)
            KeyAction.Pro -> openOverlay(Overlay.PRO)
            // 批次 E：拍照键直达真界面（不再是说明页）
            KeyAction.Photo -> goto(Screen.PHOTO_SOLVE)
            is KeyAction.OpenFunc -> openFunc(action.kind)
            // 批次 K3：就地括号调用（不弹面板）
            is KeyAction.Template -> insertTemplate(action.text, action.cursorBack)
            KeyAction.HypCycle -> insertHyperCycle()
            KeyAction.OpenSto -> {
                storeMessage = ""
                openOverlay(Overlay.STO)
            }
            KeyAction.OpenConst -> openOverlay(Overlay.CONST)
            KeyAction.OpenConv -> openOverlay(Overlay.CONV)
            KeyAction.OpenSi -> openOverlay(Overlay.SI)
            KeyAction.OpenHistory -> openOverlay(Overlay.HISTORY)
            KeyAction.ClrAll -> requestClearAll()
            KeyAction.EngToggle -> toggleEng()
            KeyAction.RandomInsert -> insertRandom()
            // 批次 K4：COPY / PASTE / CLRv / x⇄y
            KeyAction.CopyExpr -> copyExpr()
            KeyAction.PasteExpr -> pasteExpr()
            KeyAction.ClrVars -> clearVars()
            KeyAction.SwapXY -> swapXY()
            is KeyAction.GoScreen -> goto(action.screen)
            KeyAction.Shift, KeyAction.Alpha -> Unit
            is KeyAction.Insert -> insert(action.text)
        }
    }

    private fun shouldRemainNewEntry(): Boolean = justEvaluated

    private fun insert(text: String) {
        if (shouldRemainNewEntry()) {
            val c = text.firstOrNull()
            val startsOperand = c != null && (c.isDigit() || c == '.' || c == '(' || c == '\u221A' ||
                c.isLetter() || c == '\u2212')
            expression = if (startsOperand) text else "Ans$text"
            justEvaluated = false
            resultText = ""
            isError = false
            cursor = expression.length
        } else {
            val r = io.paimon.fx991.ui.CursorModel.insert(expression, cursor, text)
            expression = r.text
            cursor = r.cursor
        }
        histCursor = -1
        solutionItems = emptyList()
        resultNote = ""
        refreshPreview()
    }

    /** 批次 K3：就地括号调用 —— 插入模板文本，光标回退 back 格落进括号 / 空槽里 */
    private fun insertTemplate(text: String, back: Int) {
        insert(text)
        cursor = (cursor - back).coerceIn(0, expression.length)
    }

    /**
     * 分数键（K3-symbolic 修复）：分子为空槽（行首 / 运算符后）时光标留在分子，
     * 与二级界面键盘 a/b（cursorBack=1）行为一致；分子有内容时照常进分母槽。
     * 求值后首按仍走 insert() 的 Ans 前缀逻辑（Ans 当分子，光标进分母）。
     */
    private fun insertFractionKey() {
        if (shouldRemainNewEntry()) {
            insert("\u00F7")
            return
        }
        val r = io.paimon.fx991.ui.CursorModel.insertFraction(expression, cursor)
        expression = r.text
        cursor = r.cursor
        histCursor = -1
        solutionItems = emptyList()
        resultNote = ""
        refreshPreview()
    }

    /** 批次 K3：hyp —— 插入 sinh()；光标还停在双曲调用口时重复按 → sinh → cosh → tanh 循环 */
    private fun insertHyperCycle() {
        val names = listOf("sinh", "cosh", "tanh")
        names.forEachIndexed { ix, nm ->
            val s = cursor - 1 - nm.length
            if (s >= 0 && expression.regionMatches(s, nm, 0, nm.length) &&
                expression.getOrNull(cursor - 1) == '(' && expression.getOrNull(cursor) == ')'
            ) {
                val next = names[(ix + 1) % names.size]
                expression = expression.substring(0, s) + next + expression.substring(cursor - 1)
                cursor = s + next.length + 1
                refreshPreview()
                return
            }
        }
        insertTemplate("sinh()", 1)
    }

    private fun clearAll() {
        expression = ""
        cursor = 0
        resultText = ""
        previewText = ""
        isError = false
        justEvaluated = false
        histCursor = -1
        lastExact = null
        lastComplex = null
        polarFormShown = false
        solutionItems = emptyList()
        resultNote = ""
        cancelLayers()
    }

    // ---- 批次 B：CLR ALL（先弹确认）/ STO 变量 ----

    /** CLR ALL：先弹确认对话框 */
    fun requestClearAll() {
        cancelLayers()
        overlay = Overlay.CLRCONFIRM
    }

    /** 确认后：清历史 / 变量 / M / 设置 */
    fun doClearAll() {
        clearAll()
        history.clear()
        registers.clearAll()
        // 批次 G：自定义函数一并清空
        UserFunctions.clear()
        syncUserFuncs()
        memory = 0.0
        memorySet = false
        ans = 0.0
        preAns = 0.0
        lastValue = null
        displayMode = DisplayMode.FRAC
        settings = CalculatorSettings()
        storeMessage = ""
        variables = emptyMap()
        closeOverlay()
    }

    /** STO：把当前结果存入变量（A–F / x / y / M） */
    fun storeVariable(name: String) {
        val v = currentValue()
        storeMessage = if (name == "M") {
            memory = v
            memorySet = true
            "已存入 M = " + formatNumber(v)
        } else {
            registers.store(name, v)
        }
        variables = registers.snapshot()
    }

    /** 变量插入表达式（A–F / x / y / M） */
    fun insertVariable(name: String) {
        closeOverlay()
        insert(name)
    }

    /** 科学常数：把值以可写回表达式的字面量插入 */
    fun insertConstant(v: Double) {
        closeOverlay()
        insert(CalcEngine.literal(v))
    }

    /** 历史记录：删一条 */
    fun removeHistory(index: Int) {
        if (index in history.indices) history.removeAt(index)
    }

    private fun backspace() {
        if (justEvaluated) {
            justEvaluated = false
            resultText = ""
            isError = false
            cursor = expression.length
        }
        if (expression.isEmpty()) return
        val r = io.paimon.fx991.ui.CursorModel.backspace(expression, cursor)
        expression = r.text
        cursor = r.cursor
        histCursor = -1
        refreshPreview()
    }

    private fun toggleAngle() {
        angleMode = if (angleMode == AngleMode.DEG) AngleMode.RAD else AngleMode.DEG
        refreshPreview()
    }

    private fun cycleDisplay() {
        val c = lastComplex
        if (c != null) {
            polarFormShown = !polarFormShown
            resultText = if (polarFormShown) polarText(c) else rectText(c)
            isError = false
            return
        }
        displayMode = displayMode.next()
        val v = lastExact ?: return
        resultText = formatValue(v)
        isError = false
    }

    /** 显示精度 / 角度制改了之后，把已有结果按新设置重算一遍 */
    private fun reformatResult() {
        val c = lastComplex
        if (c != null) {
            resultText = if (polarFormShown) polarText(c) else rectText(c)
            return
        }
        val v = lastExact ?: return
        resultText = formatValue(v)
    }

    private fun evaluateNow() {
        val src = CalcEngine.autoClose(expression)
        if (src.isBlank()) return
        expression = src
        cursor = src.length
        solutionItems = emptyList()
        resultNote = ""
        try {
            // 批次 K3：就地调用拦截 —— solve(方程) 出求解报告 / sto(式,变量) 写寄存器，都不是纯求值
            InlineFuncs.unwrapSolve(src)?.let { inner ->
                runEquation(inner)
                return
            }
            InlineFuncs.parseSto(src)?.let { (ex, name) ->
                runSto(src, ex, name)
                return
            }
            // 批次 G：GeoGebra 式函数定义优先于方程判定（f(x)=x^2 定义；已定义时同形按方程解）
            val defined = UserFunctions.tryDefine(src)
            if (defined != null) {
                presentDefinition(src, defined)
                return
            }
            // 批次 G：傅里叶主行 fourier(f(x), 下限, 上限, 项数)
            if (Regex("^fourier\\s*\\(", RegexOption.IGNORE_CASE).containsMatchIn(src.trim())) {
                runFourier(src)
                return
            }
            // REFERENCE 第 7 条：含未知变量且带 `=` → 当方程求根；不含未知变量 → 普通求值
            if (EquationSolver.looksLikeEquation(src)) {
                if (EquationSolver.unknowns(src).isNotEmpty()) {
                    runEquation(src)
                    return
                }
                val lhs = src.substringBefore('=').trim()
                if (lhs.isEmpty()) {
                    syntaxError()
                    return
                }
                presentValue(src, Unified.evaluate(
                    lhs, angleMode, ans, memValue(), lastExact, preAns, varsMap(), matrixStore, vectorStore,
                ))
                if (Unified.lastNote.isNotEmpty()) resultNote = Unified.lastNote
                return
            }
            // REFERENCE 第 6 条：主行统一输入面（标量 / 复数 / 矩阵 / 向量 / 统计 / 分布）
            val v = Unified.evaluate(
                src, angleMode, ans, memValue(), lastExact, preAns, varsMap(), matrixStore, vectorStore,
            )
            presentValue(src, v)
            if (Unified.lastNote.isNotEmpty()) resultNote = Unified.lastNote
        } catch (e: CalcSyntaxError) {
            // 批次 G：带具体原因的语法错误（如「不能用这个名字定义函数」）直接展示
            val msg = e.message
            if (!msg.isNullOrBlank() && msg != "语法错误") {
                resultText = msg
                previewText = ""
                isError = true
                solutionItems = emptyList()
                justEvaluated = false
            } else {
                syntaxError()
            }
        } catch (_: CalcMathError) {
            mathError()
        } catch (e: NumericError) {
            resultText = e.message ?: "\u6570\u5B66\u9519\u8BEF"
            previewText = ""
            isError = true
            solutionItems = emptyList()
            justEvaluated = false
        } catch (_: Exception) {
            mathError()
        }
    }

    /** 批次 G：函数定义结果的呈现（进历史，可继续运算） */
    private fun presentDefinition(src: String, def: UserFnDef) {
        resultText = "已定义 ${def.fullText()}"
        resultNote = "之后可直接用：${def.name}(3) · ${def.name}'(2) · 嵌套调用；MODE → 代数区 可查看 / 改 / 删"
        previewText = ""
        isError = false
        solutionItems = emptyList()
        lastComplex = null
        lastExact = null
        syncUserFuncs()
        pushHistory(src, resultText)
        justEvaluated = true
    }

    /** 批次 G：傅里叶主行 fourier(f(x), a, b, n) —— 系数 + 奇偶 + 部分和 */
    private fun runFourier(src: String) {
        try {
            val args = FourierOps.parseMain(src)
                ?: throw NumericError("fourier 用法：fourier(f(x), 下限, 上限, 项数)")
            val r = FourierOps.compute(args.f, args.a, args.b, args.n)
            resultText = FourierOps.formatReport(r)
            resultNote = "系数为自适应 Simpson 数值积分（弧度制）；末行是部分和 S${r.n}(x)"
            previewText = ""
            isError = false
            // 部分和表达式可点一下带回主行（注意主行三角函数跟随角度制）
            solutionItems = listOf(SolveItem("代入 S${r.n}(x)", FourierOps.partialSumExpr(r)))
            lastComplex = null
            lastExact = null
            pushHistory(src, "fourier → a0=${CalcEngine.format(r.a0, 10, null)} …")
            justEvaluated = true
        } catch (e: NumericError) {
            resultText = e.message ?: "数值错误"
            previewText = ""
            isError = true
            solutionItems = emptyList()
            justEvaluated = false
        } catch (e: CalcSyntaxError) {
            syntaxError()
        }
    }

    private fun syntaxError() {
        resultText = "\u8BED\u6CD5\u9519\u8BEF"
        previewText = ""
        isError = true
        solutionItems = emptyList()
        justEvaluated = false
    }

    private fun mathError() {
        resultText = "\u6570\u5B66\u9519\u8BEF"
        previewText = ""
        isError = true
        solutionItems = emptyList()
        justEvaluated = false
    }

    /** 批次 K3：sto(式, 变量) —— 就地变量赋值（原 STO 面板的就地形态） */
    private fun runSto(src: String, exprSrc: String, varName: String) {
        try {
            val v = Unified.evaluate(
                exprSrc, angleMode, ans, memValue(), lastExact, preAns, varsMap(), matrixStore, vectorStore,
            )
            val cx = Unified.asComplex(v) ?: throw NumericError("STO 只能存数值")
            if (abs(cx.im.toDouble()) > 1e-12) throw NumericError("复数不能存入变量")
            val d = cx.re.toDouble()
            val msg = if (varName == "M") {
                memory = d
                memorySet = true
                "已存入 M = " + formatNumber(d)
            } else {
                registers.store(varName, d)
            }
            variables = registers.snapshot()
            presentValue(src, v)
            resultNote = msg
        } catch (e: CalcSyntaxError) {
            syntaxError()
        } catch (e: NumericError) {
            resultText = e.message ?: "\u6570\u5B66\u9519\u8BEF"
            previewText = ""
            isError = true
            solutionItems = emptyList()
            justEvaluated = false
        } catch (_: CalcMathError) {
            mathError()
        } catch (_: Exception) {
            mathError()
        }
    }

    /** 普通求值结果：复数走复数轨，矩阵 / 向量直接上屏 */
    private fun presentValue(src: String, v: CalcValue) {
        when (v) {
            is CalcValue.Scalar -> {
                val c = v.c
                if (abs(c.im.toDouble()) > 1e-12 || src.contains(ANGLE_SIGN)) {
                    // 复数轨：不进 Ans（与原行为一致），S⇔D 可切 直角 ⇄ r∠θ
                    lastComplex = ComplexRect(c.re.toDouble(), c.im.toDouble())
                    lastExact = null
                    polarFormShown = false
                    resultText = rectText(lastComplex!!)
                } else {
                    val value = c.re
                    val d = value.toDouble()
                    val text = formatValue(value)
                    preAns = ans
                    ans = d
                    lastValue = d
                    lastExact = value
                    lastComplex = null
                    resultText = text
                }
            }
            is CalcValue.MatVal -> {
                lastComplex = null
                lastExact = null
                resultText = v.m.format()
            }
            is CalcValue.VecVal -> {
                lastComplex = null
                lastExact = null
                resultText = v.v.format()
            }
        }
        previewText = ""
        isError = false
        pushHistory(src, resultText)
        justEvaluated = true
    }

    /** REFERENCE 第 7 条：主行直接求解（方程 / 方程组） */
    private fun runEquation(src: String) {
        val r = EquationSolver.solve(src, angleMode)
        resultText = r.text
        resultNote = r.note
        solutionItems = r.items
        previewText = ""
        lastComplex = null
        lastExact = null
        isError = r.kind == SolveKind.ERROR
        if (r.kind == SolveKind.SOLUTIONS) {
            val first = r.items.firstOrNull()?.insert?.toDoubleOrNull()
            if (first != null) {
                preAns = ans
                ans = first
                lastValue = first
                lastExact = Value.Floating(first)
            }
        }
        pushHistory(src, r.text)
        justEvaluated = r.kind != SolveKind.ERROR
    }

    private fun refreshPreview() {
        if (justEvaluated) {
            previewText = ""
            return
        }
        val src = CalcEngine.autoClose(expression)
        if (src.isBlank()) {
            previewText = ""
            return
        }
        // 批次 G：GeoGebra 定义外形预览（f(x)=… / f(x):=…）
        val shape = try {
            UserFunctions.definitionShape(src)
        } catch (_: Exception) {
            null
        }
        if (shape != null) {
            previewText = if (UserFunctions.contains(shape.name) && !shape.colonEq) {
                "${shape.name} 已定义：按 = 当方程求解，重定义请用 :="
            } else {
                "按 = 定义 ${shape.name}(${shape.params.joinToString(",")}) = ${shape.bodyText}"
            }
            return
        }
        // 批次 G：傅里叶主行预览
        if (Regex("^fourier\\s*\\(", RegexOption.IGNORE_CASE).containsMatchIn(src.trim())) {
            previewText = "按 = 计算傅里叶系数"
            return
        }
        if (src.contains('=')) {
            // 批次 G：方程即输即解（无歧义时预览直接给解，按 = 确认）
            // 批次 K3：solve(方程) 外壳先剥掉再预览
            previewText = try {
                val eqSrc = InlineFuncs.unwrapSolve(src) ?: src
                if (EquationSolver.unknowns(eqSrc).isEmpty()) ""
                else {
                    val r = EquationSolver.solve(eqSrc, angleMode)
                    if (r.kind == SolveKind.ERROR) "" else r.text + "（按 = 确认）"
                }
            } catch (_: Exception) {
                ""
            }
            return
        }
        // 批次 K3：重数值调用（自适应积分 / 导数 / 求和 / 极限）不做实时预览，避免每次击键都跑一轮
        if (Regex("(int|deriv|sum|lim)\\s*\\(").containsMatchIn(src)) {
            previewText = "按 = 计算"
            return
        }
        previewText = try {
            when (val v = Unified.evaluate(
                src, angleMode, ans, memValue(), lastExact, preAns, varsMap(), matrixStore, vectorStore,
            )) {
                is CalcValue.Scalar ->
                    // 批次 G：复数结果也实时预览（i / ∠ / res / cint）
                    if (abs(v.c.im.toDouble()) > 1e-12) {
                        rectText(ComplexRect(v.c.re.toDouble(), v.c.im.toDouble()))
                    } else formatValue(v.c.re)
                is CalcValue.MatVal -> ""
                is CalcValue.VecVal -> ""
            }
        } catch (_: Exception) {
            ""
        }
    }

    // -----------------------------------------------------------------------
    // 批次 K4：COPY / PASTE / CLRv / x⇄y
    // -----------------------------------------------------------------------

    /** 表达式剪贴板（COPY / PASTE） */
    private var exprClipboard = ""

    /** COPY：复制主行表达式；主行为空时复制当前结果文本 */
    private fun copyExpr() {
        val src = if (expression.isNotBlank()) expression else resultText
        if (src.isBlank()) return
        exprClipboard = src
        storeMessage = "已复制：$src"
    }

    /** PASTE：把剪贴板内容插到光标处 */
    private fun pasteExpr() {
        if (exprClipboard.isBlank()) {
            storeMessage = "剪贴板为空（先用 SHIFT+0 复制）"
            return
        }
        insert(exprClipboard)
    }

    /** CLRv：清空 STO 变量（A–F / x / y）；M 与 Ans 不动 */
    private fun clearVars() {
        registers.clearAll()
        variables = registers.snapshot()
        storeMessage = "变量已清除（A–F / x / y）"
    }

    /** x⇄y：交换 x 与 y 的值 */
    private fun swapXY() {
        val xv = registers.get("x") ?: 0.0
        val yv = registers.get("y") ?: 0.0
        registers.store("x", yv)
        registers.store("y", xv)
        variables = registers.snapshot()
        storeMessage = "x⇄y：x=${formatNumber(yv)}，y=${formatNumber(xv)}"
        refreshPreview()
    }

    private fun memoryOp(sign: Double) {
        if (expression.isBlank() && lastValue == null) return
        memory += sign * currentValue()
        memorySet = true
        resultText = formatNumber(memory)
        isError = false
        justEvaluated = true
        previewText = ""
        lastExact = Value.Floating(memory)
        lastComplex = null
    }

    private fun mrc() {
        if (mrcArmed) {
            memory = 0.0
            memorySet = false
            mrcArmed = false
            resultText = "0"
            isError = false
            return
        }
        insert("M")
        mrcArmed = true
    }

    private fun historyUp() {
        if (history.isEmpty()) return
        histCursor = if (histCursor < 0) 0 else minOf(histCursor + 1, history.size - 1)
        loadFromHistory()
    }

    private fun historyDown() {
        if (history.isEmpty()) return
        if (histCursor <= 0) {
            histCursor = -1
            expression = ""
        } else {
            histCursor -= 1
        }
        loadFromHistory()
    }

    private fun loadFromHistory() {
        justEvaluated = false
        resultText = ""
        isError = false
        if (histCursor in history.indices) {
            expression = history[histCursor].expr
        }
        cursor = expression.length
        refreshPreview()
    }

    // -----------------------------------------------------------------------
    // 批次 A：显示格式化 / 极坐标文本 / 数值功能对话框
    // -----------------------------------------------------------------------

    private fun pushHistory(expr: String, text: String) {
        history.add(0, HistoryEntry(expr, text))
        while (history.size > 40) history.removeAt(history.size - 1)
        histCursor = -1
    }

    private fun fixDecimals(): Int? =
        if (settings.precision == PrecisionMode.DECIMALS) settings.decimals else null

    /** 双轨结果按当前设置显示 */
    private fun formatValue(v: Value): String = CalcEngine.formatValue(
        v,
        displayMode == DisplayMode.DEC,
        displayMode == DisplayMode.MIXED,
        settings.sigDigits,
        fixDecimals(),
        settings.notation,
    )

    /** 预估值 / 对话框里的纯浮点显示 */
    fun formatNumber(v: Double): String =
        CalcEngine.format(v, settings.sigDigits, fixDecimals(), settings.notation)

    /** 当前角度制的单位后缀 */
    private fun angleUnit(): String = when (angleMode) {
        AngleMode.DEG -> "\u00B0"
        AngleMode.GRAD -> " grad"
        AngleMode.RAD -> " rad"
    }

    /** a∠θ 结果：直角坐标 x+yi */
    private fun rectText(c: ComplexRect): String {
        val re = formatNumber(c.re)
        return when {
            abs(c.im) < 1e-12 -> re
            c.im > 0.0 -> "$re + ${formatNumber(c.im)}i"
            else -> "$re \u2212 ${formatNumber(-c.im)}i"
        }
    }

    /** a∠θ 结果：极坐标 r∠θ */
    private fun polarText(c: ComplexRect): String {
        val p = PolarForm.toPolar(c.re, c.im, angleMode)
        return "${formatNumber(p.r)}${ANGLE_SIGN}${formatNumber(p.theta)}${angleUnit()}"
    }

    /** 函数帮助：把语法键写进表达式并回到计算界面 */
    fun insertFromHelp(text: String) {
        goto(Screen.CALC)
        insert(text)
    }

    /** 历史记录面板里点一条 → 回填表达式 */
    fun selectHistory(expr: String) {
        justEvaluated = false
        resultText = ""
        isError = false
        expression = expr
        cursor = expr.length
        lastComplex = null
        polarFormShown = false
        solutionItems = emptyList()
        resultNote = ""
        closeOverlay()
        refreshPreview()
    }

    /** 方程多解：把某一条解插入主行（继续参与运算） */
    fun insertSolution(text: String) {
        if (text.isEmpty()) return
        justEvaluated = false
        resultText = ""
        isError = false
        solutionItems = emptyList()
        resultNote = ""
        expression = text
        cursor = text.length
        refreshPreview()
    }

    /** 对话框里的“计算”按钮 */
    fun runFunc() {
        val d = funcDialog ?: return
        d.error = ""
        d.result = ""
        try {
            when (d.kind) {
                FuncKind.INTEGRAL -> {
                    val f = need(d.f, "f(x)")
                    val a = num(d.a, "下限")
                    val b = num(d.b, "上限")
                    val tol = d.c.trim().toDoubleOrNull() ?: 1e-10
                    d.result = "= " + formatNumber(NumericOps.integrate(f, angleMode, a, b, tol))
                }
                FuncKind.DERIV -> {
                    val f = need(d.f, "f(x)")
                    val x = num(d.a, "x 值")
                    d.result = "= " + formatNumber(NumericOps.derivative(f, angleMode, x))
                }
                FuncKind.SUMMATION -> {
                    val f = need(d.f, "f(x)")
                    val a = longNum(d.a, "下界")
                    val b = longNum(d.b, "上界")
                    d.result = "= " + formatNumber(NumericOps.summation(f, angleMode, a, b))
                }
                FuncKind.SOLVE -> {
                    val f = need(d.f, "f(x)")
                    val g = num(d.a, "初值")
                    val r = NumericOps.solveRoot(f, angleMode, g)
                    val ex = r.exact
                    d.result = if (ex != null)
                        "x = ${CalcEngine.formatRational(ex, false)}（${r.method}）"
                    else "x = ${formatNumber(r.root)}（${r.method}）"
                }
                FuncKind.LIMIT -> {
                    val f = need(d.f, "f(x)")
                    val x0 = num(d.a, "x →")
                    val r = NumericOps.limit(f, angleMode, x0)
                    d.result = if (r.equal)
                        "lim = ${formatNumber(r.value())}"
                    else "左 ${formatNumber(r.left)}｜右 ${formatNumber(r.right)}（左右不等）"
                }
                FuncKind.CALC -> {
                    val f = need(d.f, "表达式")
                    val x = d.a.trim().toDoubleOrNull() ?: 0.0
                    val y = d.b.trim().toDoubleOrNull() ?: 0.0
                    val v = CalcEngine.evaluateWith(f, angleMode, x, y)
                    ans = v
                    d.result = "= " + formatNumber(v)
                }
                FuncKind.DMS -> {
                    val deg = d.a.trim().toDoubleOrNull() ?: throw NumericError("请输入度")
                    val minv = d.b.trim().toDoubleOrNull() ?: 0.0
                    val sec = d.c.trim().toDoubleOrNull() ?: 0.0
                    d.result = if (d.b.isBlank() && d.c.isBlank()) {
                        Sexagesimal.fromDegrees(deg).toString()
                    } else {
                        formatNumber(Sexagesimal.toDegrees(deg, minv, sec)) + "\u00B0"
                    }
                }
                FuncKind.HYPER -> Unit
                FuncKind.POL -> {
                    val x = num(d.a, "x")
                    val y = num(d.b, "y")
                    val p = PolarForm.toPolar(x, y, angleMode)
                    d.result = "r = ${formatNumber(p.r)}，θ = ${formatNumber(p.theta)}${angleUnit()}"
                }
                FuncKind.REC -> {
                    val r = num(d.a, "r")
                    val th = num(d.b, "θ")
                    val c = PolarForm.toRect(r, th, angleMode)
                    d.result = "x = ${formatNumber(c.re)}，y = ${formatNumber(c.im)}"
                }
                FuncKind.RANINT -> {
                    val a = longNum(d.a, "下界")
                    val b = longNum(d.b, "上界")
                    val n = longNum(d.c, "次数").toInt()
                    val list = RandomOps.ranInts(a, b, n)
                    d.result = list.joinToString(" ")
                }
            }
        } catch (e: Exception) {
            d.error = e.message ?: "计算失败"
        }
    }

    private fun need(s: String, what: String): String {
        val t = CalcEngine.autoClose(s.trim())
        if (t.isBlank()) throw NumericError("请输入 $what")
        return t
    }

    private fun num(s: String, what: String): Double =
        s.trim().toDoubleOrNull() ?: throw NumericError("$what 不是合法数字")

    private fun longNum(s: String, what: String): Long {
        val v = s.trim().toDoubleOrNull() ?: throw NumericError("$what 不是合法数字")
        if (v != floor(v)) throw NumericError("$what 必须是整数")
        return v.toLong()
    }

    // -----------------------------------------------------------------------
    // 批次 E：解题 API 配置（用户自由填写；SharedPreferences 明文存储，界面已告知）
    // -----------------------------------------------------------------------

    private val apiPrefs = app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var apiBaseUrl by mutableStateOf(
        apiPrefs.getString(KEY_API_BASE, ApiConfig.DEFAULT_BASE) ?: ApiConfig.DEFAULT_BASE
    )
        private set

    var apiKey by mutableStateOf(apiPrefs.getString(KEY_API_KEY, "") ?: "")
        private set

    var apiModel by mutableStateOf(
        apiPrefs.getString(KEY_API_MODEL, ApiConfig.DEFAULT_MODEL) ?: ApiConfig.DEFAULT_MODEL
    )
        private set

    /** 超时秒数（输入框是文本，读配置时才转 Int 并收紧到合法区间） */
    var apiTimeoutText by mutableStateOf(
        apiPrefs.getInt(KEY_API_TIMEOUT, ApiConfig.DEFAULT_TIMEOUT_SEC).toString()
    )
        private set

    /** 测试连接：进行中 / 结果文案（成功失败都是人话） */
    var apiTestBusy by mutableStateOf(false)
        private set
    var apiTestResult by mutableStateOf("")
        private set

    /** 当前生效的配置（超时文本非法时回落默认，并收紧到 5–180 秒） */
    fun apiConfig(): ApiConfig = ApiConfig(
        baseUrl = apiBaseUrl.trim(),
        apiKey = apiKey.trim(),
        model = apiModel.trim(),
        timeoutSec = apiTimeoutText.trim().toIntOrNull() ?: ApiConfig.DEFAULT_TIMEOUT_SEC,
    ).clamped()

    /** 拍照解题可用前提：三项都填了 */
    fun apiReady(): Boolean = apiConfig().isComplete()

    fun updateApiBase(v: String) {
        apiBaseUrl = v
        persistApi()
    }

    fun updateApiKey(v: String) {
        apiKey = v
        persistApi()
    }

    fun updateApiModel(v: String) {
        apiModel = v
        persistApi()
    }

    fun updateApiTimeout(v: String) {
        apiTimeoutText = v.filter { it.isDigit() }.take(3)
        persistApi()
    }

    /** 预设一键填充：只填 Base URL + 模型名，Key 永远不动 */
    fun applyApiPreset(p: ApiPreset) {
        apiBaseUrl = p.baseUrl
        apiModel = p.model
        persistApi()
    }

    private fun persistApi() {
        apiPrefs.edit()
            .putString(KEY_API_BASE, apiBaseUrl.trim())
            .putString(KEY_API_KEY, apiKey.trim())
            .putString(KEY_API_MODEL, apiModel.trim())
            .putInt(
                KEY_API_TIMEOUT,
                (apiTimeoutText.trim().toIntOrNull() ?: ApiConfig.DEFAULT_TIMEOUT_SEC)
                    .coerceIn(ApiConfig.MIN_TIMEOUT_SEC, ApiConfig.MAX_TIMEOUT_SEC),
            )
            .apply()
        apiTestResult = ""
    }

    /** 「测试连接」：发一个最小请求（协程 IO，不碰主线程） */
    fun testApiConnection() {
        if (apiTestBusy) return
        apiTestBusy = true
        apiTestResult = ""
        val cfg = apiConfig()
        viewModelScope.launch {
            val r = withContext(Dispatchers.IO) { PhotoNet.testConnection(cfg) }
            apiTestResult = r
            apiTestBusy = false
        }
    }

    // -----------------------------------------------------------------------
    // 批次 K5：自动检查更新（GitHub Releases 公共 API；12 小时缓存窗口）
    // -----------------------------------------------------------------------

    private val updatePrefs = app.getSharedPreferences(UPDATE_PREFS, Context.MODE_PRIVATE)

    /** 设置开关：启动时自动检查更新（默认开） */
    var autoCheckUpdate by mutableStateOf(updatePrefs.getBoolean(KEY_UPDATE_AUTO, true))
        private set

    /** 手动检查进行中 / 结果文案（人话；自动检查失败时保持静默不写这里） */
    var updateCheckBusy by mutableStateOf(false)
        private set
    var updateCheckResult by mutableStateOf("")
        private set

    /** 最近一次确认过的最新版本 tag（来自缓存或本次请求，用于设置页回显） */
    var latestVersionText by mutableStateOf(updatePrefs.getString(KEY_UPDATE_TAG, "") ?: "")
        private set

    /** 发现的新版本（null = 没有新版 / 还没查过）；启动弹层与设置页下载按钮共用 */
    var updateAvailable by mutableStateOf<ReleaseInfo?>(null)
        private set

    /** 本次会话里被「稍后」 dismiss 掉的 tag（同一会话不再反复弹同一个版本） */
    var updateDismissedTag by mutableStateOf("")
        private set

    fun setAutoCheck(b: Boolean) {
        autoCheckUpdate = b
        updatePrefs.edit().putBoolean(KEY_UPDATE_AUTO, b).apply()
    }

    fun dismissUpdate() {
        updateDismissedTag = updateAvailable?.tag ?: ""
    }

    /** 启动时静默检查：开关关 → 直接返回；12 小时窗口内 → 不请求，用缓存结果决定是否提示 */
    fun autoCheckUpdateIfDue() {
        if (!autoCheckUpdate) return
        val last = updatePrefs.getLong(KEY_UPDATE_AT, 0L)
        if (!UpdateCheck.dueForAutoCheck(last, System.currentTimeMillis())) {
            val cachedTag = updatePrefs.getString(KEY_UPDATE_TAG, "") ?: ""
            if (cachedTag.isNotEmpty() && UpdateCheck.isNewer(cachedTag, APP_VERSION)) {
                updateAvailable = ReleaseInfo(
                    tag = cachedTag,
                    version = cachedTag.removePrefix("v").removePrefix("V"),
                    pageUrl = updatePrefs.getString(KEY_UPDATE_PAGE, "") ?: "",
                    apkUrl = updatePrefs.getString(KEY_UPDATE_APK, "") ?: "",
                    title = "",
                )
            }
            return
        }
        fetchUpdate(auto = true)
    }

    /** 设置页「立即检查」：不受 12 小时窗口限制 */
    fun checkUpdateNow() {
        if (updateCheckBusy) return
        fetchUpdate(auto = false)
    }

    private fun fetchUpdate(auto: Boolean) {
        if (updateCheckBusy) return
        updateCheckBusy = true
        if (!auto) updateCheckResult = ""
        viewModelScope.launch {
            val r = withContext(Dispatchers.IO) {
                try {
                    Result.success(UpdateCheck.fetchLatest())
                } catch (e: UpdateException) {
                    Result.failure(e)
                } catch (e: Exception) {
                    Result.failure(UpdateException("检查失败：${e.message ?: e.javaClass.simpleName}"))
                }
            }
            updateCheckBusy = false
            r.onSuccess { info ->
                updatePrefs.edit()
                    .putLong(KEY_UPDATE_AT, System.currentTimeMillis())
                    .putString(KEY_UPDATE_TAG, info.tag)
                    .putString(KEY_UPDATE_PAGE, info.pageUrl)
                    .putString(KEY_UPDATE_APK, info.apkUrl)
                    .apply()
                latestVersionText = info.tag
                if (UpdateCheck.isNewer(info.tag, APP_VERSION)) {
                    updateAvailable = info
                    updateCheckResult = "发现新版本 ${info.tag}（当前 $APP_VERSION）"
                } else {
                    updateAvailable = null
                    updateCheckResult = "已是最新版本（${info.tag}）"
                }
            }.onFailure { e ->
                // 到达过服务器（限流 / 解析失败）也记入窗口，避免反复轰炸；纯网络失败不计
                if (e !is UpdateNetException) {
                    updatePrefs.edit().putLong(KEY_UPDATE_AT, System.currentTimeMillis()).apply()
                }
                // 自动检查失败静默；手动检查给人话
                if (!auto) updateCheckResult = e.message ?: "检查失败，请稍后再试"
            }
        }
    }

    init {
        // 启动静默检查：延后 5 秒、协程 IO，不阻塞 UI、不抢焦点；只在有新版时才弹提示
        viewModelScope.launch {
            delay(5000)
            autoCheckUpdateIfDue()
        }
    }

    /**
     * 拍照解题回填：把模型给的表达式插进主计算行并立即求值（复用主行解题管线），
     * 模型的解释文字显示在结果区附注里。
     */
    fun applyPhotoExpr(expr: String, explain: String) {
        goto(Screen.CALC)
        justEvaluated = false
        expression = expr
        cursor = expr.length
        resultText = ""
        previewText = ""
        isError = false
        solutionItems = emptyList()
        resultNote = ""
        lastComplex = null
        polarFormShown = false
        evaluateNow()
        if (explain.isNotBlank() && !isError) {
            resultNote = if (resultNote.isBlank()) explain else "$resultNote ｜ $explain"
        }
    }

    private companion object {
        const val ANGLE_SIGN = '\u2220'

        // ---- 批次 E：解题 API 配置持久化（SharedPreferences 明文，界面有告知） ----
        const val PREFS_NAME = "fx991_api_config"
        const val KEY_API_BASE = "api_base_url"
        const val KEY_API_KEY = "api_key"
        const val KEY_API_MODEL = "api_model"
        const val KEY_API_TIMEOUT = "api_timeout_sec"

        // ---- 批次 K5：检查更新持久化（独立 prefs 文件） ----
        const val UPDATE_PREFS = "fx991_update"
        const val KEY_UPDATE_AUTO = "auto_check"
        const val KEY_UPDATE_AT = "last_check_ms"
        const val KEY_UPDATE_TAG = "last_tag"
        const val KEY_UPDATE_PAGE = "last_page_url"
        const val KEY_UPDATE_APK = "last_apk_url"
    }
}
