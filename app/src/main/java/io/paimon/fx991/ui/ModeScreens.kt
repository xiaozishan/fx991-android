package io.paimon.fx991.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.paimon.fx991.CalcViewModel
import io.paimon.fx991.engine.AngleMode
import io.paimon.fx991.engine.BaseN
import io.paimon.fx991.engine.EqnMode
import io.paimon.fx991.engine.RatioOps
import io.paimon.fx991.engine.SolveItem
import io.paimon.fx991.engine.SolveKind
import io.paimon.fx991.engine.SolveResult
import io.paimon.fx991.engine.TableGen
import io.paimon.fx991.engine.label

// ---------------------------------------------------------------------------
// 批次 D：四个新模式的独立界面（方程 EQN / 基数换算 BASE-N / 函数表 TABLE / 比例 RATIO）
// 与批次 C 六个子系统同一套观感：SubHeader + NumField + SubButton + ResultBox，
// 根节点一律挂 Modifier.safeAreaPadding()，深浅主题都走 LocalCalcColors。
// ---------------------------------------------------------------------------

/** 小节标题 */
@Composable
private fun SectionTitle(text: String) {
    val c = LocalCalcColors.current
    Text(text, color = c.chromeInk, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(5.dp))
}

/** 单选芯片行（等宽，选中高亮） */
@Composable
private fun ChoiceRow(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        options.forEachIndexed { i, t ->
            SubButton(label = t, primary = i == selected, modifier = Modifier.weight(1f)) {
                onSelect(i)
            }
        }
    }
}

/** 求解结果 + 可点击逐条插入主行的解 */
@Composable
private fun SolveResultView(r: SolveResult?, error: String, vm: CalcViewModel?, onBack: () -> Unit) {
    val c = LocalCalcColors.current
    if (error.isNotEmpty()) {
        ResultBox(error, error = true)
        return
    }
    if (r == null) return
    val text = if (r.note.isNotEmpty()) r.text + "\n" + r.note else r.text
    ResultBox(text, error = r.kind == SolveKind.ERROR)
    if (r.items.isNotEmpty()) {
        Spacer(Modifier.height(8.dp))
        Text(
            if (vm != null) "点一条解 → 带回主计算行" else "解",
            color = c.keyNeutralInk.copy(alpha = 0.65f), fontSize = 11.sp,
        )
        Spacer(Modifier.height(5.dp))
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            r.items.forEach { s: SolveItem ->
                Button(
                    onClick = {
                        val ins = s.insert ?: return@Button
                        if (vm != null) {
                            vm.insertSolution(ins)
                            onBack()
                        }
                    },
                    enabled = vm == null || s.insert != null,
                    shape = RoundedCornerShape(7.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 8.dp, vertical = 0.dp,
                    ),
                    border = BorderStroke(1.dp, c.keyEdge),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = c.keyNeutral, contentColor = c.bodyInk,
                        disabledContainerColor = c.keyNeutral.copy(alpha = 0.4f),
                        disabledContentColor = c.bodyInk.copy(alpha = 0.4f),
                    ),
                    modifier = Modifier.height(30.dp),
                ) { Text(s.label, fontFamily = Mono, fontSize = 11.sp, maxLines = 1) }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// ① 方程 EQN：多项式（2/3/4 次） · 联立线性（2~4 元）
// ---------------------------------------------------------------------------

private val POLY_SUP = mapOf(2 to "²", 3 to "³", 4 to "⁴")
private val LIN_NAMES = listOf("x", "y", "z", "w")

@Composable
fun EquationScreen(vm: CalcViewModel, onBack: () -> Unit) {
    val c = LocalCalcColors.current
    var kind by remember { mutableIntStateOf(0) } // 0 多项式 / 1 联立
    var degree by remember { mutableIntStateOf(2) }
    // 多项式系数槽：index 0 = 最高次；用前 degree+1 个
    val coeffs = remember { mutableStateListOf("1", "-3", "2", "0", "0") }
    var nVars by remember { mutableIntStateOf(2) }
    // 联立槽：row*5 + col（col 0..3 = x,y,z,w 系数；col 4 = 常数项）
    val lin = remember {
        mutableStateListOf(
            "2", "1", "0", "0", "5",
            "1", "-1", "0", "0", "1",
            "1", "1", "1", "0", "6",
            "1", "1", "1", "1", "10",
        )
    }
    var result by remember { mutableStateOf<SolveResult?>(null) }
    var error by remember { mutableStateOf("") }

    fun runPoly() {
        error = ""
        result = null
        try {
            val list = (0..degree).map { i ->
                val p = degree - i
                val what = if (p == 0) "常数项" else "x${POLY_SUP[p] ?: "^$p"} 系数"
                EqnMode.parseCoeff(coeffs[i], what)
            }
            result = EqnMode.solvePolynomial(list)
        } catch (e: Exception) {
            error = e.message ?: "计算失败"
        }
    }

    fun runLin() {
        error = ""
        result = null
        try {
            val a = (0 until nVars).map { i ->
                (0 until nVars).map { j ->
                    EqnMode.parseCoeff(lin[i * 5 + j], "式${i + 1} 的 ${LIN_NAMES[j]} 系数")
                }
            }
            val b = (0 until nVars).map { i ->
                EqnMode.parseCoeff(lin[i * 5 + 4], "式${i + 1} 的常数项")
            }
            result = EqnMode.solveLinear(a, b)
        } catch (e: Exception) {
            error = e.message ?: "计算失败"
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(c.body)
            .safeAreaPadding()
            .padding(10.dp)
            .verticalScroll(rememberScrollState())
    ) {
        SubHeader("方程 EQN", "多项式（2/3/4 次，含复根）· 联立线性（2~4 元，精确分数）", onBack)
        Spacer(Modifier.height(10.dp))

        SectionTitle("类型")
        ChoiceRow(listOf("多项式方程", "联立方程"), kind) {
            kind = it
            result = null
            error = ""
        }
        Spacer(Modifier.height(12.dp))

        if (kind == 0) {
            SectionTitle("次数")
            ChoiceRow(listOf("2 次", "3 次", "4 次"), degree - 2) {
                degree = it + 2
                result = null
                error = ""
            }
            Spacer(Modifier.height(12.dp))
            SectionTitle("系数（a·x${POLY_SUP[degree]} + … = 0）")
            (0..degree).forEach { i ->
                val p = degree - i
                val label = when (p) {
                    0 -> "常数项"
                    1 -> "x 系数"
                    else -> "x${POLY_SUP[p]} 系数"
                }
                NumField(label, coeffs[i], { coeffs[i] = it }, Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
            }
            Spacer(Modifier.height(4.dp))
            SubButton("求解", Modifier.fillMaxWidth(), primary = true) { runPoly() }
        } else {
            SectionTitle("未知量个数")
            ChoiceRow(listOf("2 元", "3 元", "4 元"), nVars - 2) {
                nVars = it + 2
                result = null
                error = ""
            }
            Spacer(Modifier.height(12.dp))
            SectionTitle("各方程系数（a·x + b·y + … = 常数）")
            (0 until nVars).forEach { i ->
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        "式${i + 1}", color = c.bodyInk, fontSize = 11.sp,
                        modifier = Modifier.width(24.dp),
                    )
                    (0 until nVars).forEach { j ->
                        NumField(
                            LIN_NAMES[j], lin[i * 5 + j], { lin[i * 5 + j] = it },
                            Modifier.weight(1f),
                        )
                    }
                    NumField(
                        "=", lin[i * 5 + 4], { lin[i * 5 + 4] = it },
                        Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(6.dp))
            }
            Spacer(Modifier.height(4.dp))
            SubButton("求解", Modifier.fillMaxWidth(), primary = true) { runLin() }
        }

        Spacer(Modifier.height(12.dp))
        SolveResultView(result, error, vm, onBack)
        Spacer(Modifier.height(8.dp))
        Text(
            "多项式能精确时给精确根（分数 / 根式 / 复根），否则给数值根；" +
                "联立线性方程组走高斯消元 + 精确分数，无解 / 无穷多解会明说。",
            color = c.keyNeutralInk.copy(alpha = 0.65f), fontSize = 11.sp,
        )
    }
}

// ---------------------------------------------------------------------------
// ② 基数换算 BASE-N：DEC / HEX / BIN / OCT + 位运算 + 字长选择
// ---------------------------------------------------------------------------

/** 二进制串按 4 位分组（从右往左），便于阅读 */
private fun group4(s: String): String {
    if (s.length <= 4) return s
    val sb = StringBuilder()
    var i = s.length
    while (i > 0) {
        val j = maxOf(0, i - 4)
        if (sb.isNotEmpty()) sb.insert(0, " ")
        sb.insert(0, s.substring(j, i))
        i = j
    }
    return sb.toString()
}

/** 一个值在四种进制下的显示面板 */
@Composable
private fun BasePanel(raw: Long, bits: Int) {
    val c = LocalCalcColors.current
    Column(
        Modifier
            .fillMaxWidth()
            .background(c.lcdBg, RoundedCornerShape(6.dp))
            .padding(8.dp)
    ) {
        BaseN.BASE_VALUES.forEachIndexed { idx, base ->
            val label = BaseN.BASE_LABELS[idx]
            val v = BaseN.format(raw, base, bits)
            val shown = if (base == 2) group4(v) else v
            Row(Modifier.fillMaxWidth()) {
                Text(
                    label, color = c.lcdDim, fontFamily = Mono, fontSize = 11.sp,
                    modifier = Modifier.width(38.dp),
                )
                Text(shown, color = c.lcdFg, fontFamily = Mono, fontSize = 13.sp)
            }
            Spacer(Modifier.height(2.dp))
        }
        if (BaseN.toSigned(raw, bits) < 0) {
            Row(Modifier.fillMaxWidth()) {
                Text(
                    "无符号", color = c.lcdDim, fontFamily = Mono, fontSize = 11.sp,
                    modifier = Modifier.width(38.dp),
                )
                Text(
                    BaseN.unsignedDec(raw, bits),
                    color = c.lcdFg, fontFamily = Mono, fontSize = 13.sp,
                )
            }
        }
    }
}

@Composable
fun BaseNScreen(onBack: () -> Unit) {
    val c = LocalCalcColors.current
    var bits by remember { mutableIntStateOf(32) }
    var baseA by remember { mutableIntStateOf(10) }
    var textA by remember { mutableStateOf("255") }
    var op by remember { mutableIntStateOf(0) } // 0 AND / 1 OR / 2 XOR / 3 XNOR
    var baseB by remember { mutableIntStateOf(10) }
    var textB by remember { mutableStateOf("15") }
    var resTitle by remember { mutableStateOf("") }
    var resRaw by remember { mutableStateOf<Long?>(null) }
    var resError by remember { mutableStateOf("") }

    // A 的实时解析（改动即刷新）
    val parsedA: Long? = try {
        BaseN.parse(textA, baseA, bits)
    } catch (_: Exception) {
        null
    }
    val errA: String = try {
        BaseN.parse(textA, baseA, bits); ""
    } catch (e: Exception) {
        e.message ?: "不是合法数字"
    }

    fun showUnary(title: String, f: (Long, Int) -> Long) {
        resError = ""
        val a = parsedA
        if (a == null) {
            resError = "A：$errA"
            resRaw = null
            return
        }
        resTitle = title
        resRaw = f(a, bits)
    }

    fun runBinary() {
        resError = ""
        val a = parsedA
        if (a == null) {
            resError = "A：$errA"
            resRaw = null
            return
        }
        val b = try {
            BaseN.parse(textB, baseB, bits)
        } catch (e: Exception) {
            resError = "B：" + (e.message ?: "不是合法数字")
            resRaw = null
            return
        }
        val name = listOf("AND", "OR", "XOR", "XNOR")[op]
        resTitle = "A $name B"
        resRaw = when (op) {
            0 -> BaseN.bitAnd(a, b, bits)
            1 -> BaseN.bitOr(a, b, bits)
            2 -> BaseN.bitXor(a, b, bits)
            else -> BaseN.bitXnor(a, b, bits)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(c.body)
            .safeAreaPadding()
            .padding(10.dp)
            .verticalScroll(rememberScrollState())
    ) {
        SubHeader("基数换算 BASE-N", "DEC/HEX/BIN/OCT 互转 · 位运算 · 字长 16/32/64 · 补码", onBack)
        Spacer(Modifier.height(10.dp))

        SectionTitle("字长")
        ChoiceRow(BaseN.WORD_SIZES.map { "$it 位" }, BaseN.WORD_SIZES.indexOf(bits)) {
            bits = BaseN.WORD_SIZES[it]
            resRaw = null
            resError = ""
        }
        Spacer(Modifier.height(12.dp))

        SectionTitle("数值 A（选进制后输入）")
        ChoiceRow(BaseN.BASE_LABELS, BaseN.BASE_VALUES.indexOf(baseA)) { baseA = BaseN.BASE_VALUES[it] }
        Spacer(Modifier.height(6.dp))
        NumField("A（可带负号）", textA, { textA = it }, Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        if (parsedA == null) {
            Text(errA, color = c.lcdError, fontSize = 12.sp)
        } else {
            BasePanel(parsedA, bits)
        }
        Spacer(Modifier.height(12.dp))

        SectionTitle("一元运算")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            SubButton("NOT A（按位取反）", Modifier.weight(1f)) { showUnary("NOT A", BaseN::bitNot) }
            SubButton("NEG A（补码取负）", Modifier.weight(1f)) { showUnary("NEG A", BaseN::neg) }
        }
        Spacer(Modifier.height(12.dp))

        SectionTitle("二元位运算")
        ChoiceRow(listOf("AND", "OR", "XOR", "XNOR"), op) { op = it }
        Spacer(Modifier.height(6.dp))
        ChoiceRow(BaseN.BASE_LABELS, BaseN.BASE_VALUES.indexOf(baseB)) { baseB = BaseN.BASE_VALUES[it] }
        Spacer(Modifier.height(6.dp))
        NumField("B（可带负号）", textB, { textB = it }, Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        SubButton("计算", Modifier.fillMaxWidth(), primary = true) { runBinary() }

        Spacer(Modifier.height(12.dp))
        if (resError.isNotEmpty()) {
            ResultBox(resError, error = true)
        } else if (resRaw != null) {
            SectionTitle(resTitle)
            BasePanel(resRaw!!, bits)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "HEX / BIN / OCT 显示补码位形；DEC 显示有符号值（负数附无符号幅值）。" +
                "运算结果均按所选字长截断。",
            color = c.keyNeutralInk.copy(alpha = 0.65f), fontSize = 11.sp,
        )
    }
}

// ---------------------------------------------------------------------------
// ③ 函数表 TABLE：f(x)（可选 g(x)）数值表，起值 / 终值 / 步长，翻页
// ---------------------------------------------------------------------------

private const val TABLE_PAGE = 8

@Composable
fun TableScreen(onBack: () -> Unit) {
    val c = LocalCalcColors.current
    var fx by remember { mutableStateOf("x^2") }
    var useG by remember { mutableStateOf(false) }
    var gx by remember { mutableStateOf("2x") }
    var startT by remember { mutableStateOf("1") }
    var endT by remember { mutableStateOf("5") }
    var stepT by remember { mutableStateOf("1") }
    var mode by remember { mutableStateOf(AngleMode.DEG) }
    var data by remember { mutableStateOf<TableGen.TableData?>(null) }
    var error by remember { mutableStateOf("") }
    var page by remember { mutableIntStateOf(0) }

    fun generate() {
        error = ""
        data = null
        try {
            val s = TableGen.parseNumber(startT, "起值")
            val e = TableGen.parseNumber(endT, "终值")
            val st = TableGen.parseNumber(stepT, "步长")
            data = TableGen.generate(fx, if (useG) gx else "", s, e, st, mode)
            page = 0
        } catch (ex: Exception) {
            error = ex.message ?: "计算失败"
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(c.body)
            .safeAreaPadding()
            .padding(10.dp)
            .verticalScroll(rememberScrollState())
    ) {
        SubHeader("函数表 TABLE", "f(x) 数值表 · 可选 g(x) 双函数对照 · 翻页", onBack)
        Spacer(Modifier.height(10.dp))

        NumField("f(x)", fx, { fx = it }, Modifier.fillMaxWidth())
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("双函数对照 g(x)", color = c.bodyInk, fontSize = 12.sp)
            Spacer(Modifier.width(8.dp))
            Switch(checked = useG, onCheckedChange = { useG = it })
        }
        if (useG) {
            NumField("g(x)", gx, { gx = it }, Modifier.fillMaxWidth())
            Spacer(Modifier.height(6.dp))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            NumField("起值", startT, { startT = it }, Modifier.weight(1f))
            NumField("终值", endT, { endT = it }, Modifier.weight(1f))
            NumField("步长", stepT, { stepT = it }, Modifier.weight(1f))
        }
        Spacer(Modifier.height(6.dp))
        SectionTitle("角度制（三角函数用）")
        ChoiceRow(AngleMode.entries.map { it.label }, AngleMode.entries.indexOf(mode)) {
            mode = AngleMode.entries[it]
        }
        Spacer(Modifier.height(8.dp))
        SubButton("生成表格", Modifier.fillMaxWidth(), primary = true) { generate() }

        Spacer(Modifier.height(12.dp))
        if (error.isNotEmpty()) {
            ResultBox(error, error = true)
        } else data?.let { d ->
            val pages = (d.rows.size + TABLE_PAGE - 1) / TABLE_PAGE
            val cur = page.coerceIn(0, pages - 1)
            val from = cur * TABLE_PAGE
            val to = minOf(from + TABLE_PAGE, d.rows.size)

            // 表头
            Row(Modifier.fillMaxWidth()) {
                Text("x", color = c.chromeInk, fontFamily = Mono, fontSize = 12.sp,
                    fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("f(x)", color = c.chromeInk, fontFamily = Mono, fontSize = 12.sp,
                    fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.2f))
                if (d.hasG) {
                    Text("g(x)", color = c.chromeInk, fontFamily = Mono, fontSize = 12.sp,
                        fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.2f))
                }
            }
            Spacer(Modifier.height(4.dp))
            (from until to).forEach { i ->
                val r = d.rows[i]
                Row(Modifier.fillMaxWidth()) {
                    Text(dstr(r.x), color = c.bodyInk, fontFamily = Mono, fontSize = 12.sp,
                        modifier = Modifier.weight(1f))
                    Text(r.f?.let { dstr(it) } ?: "错误", fontFamily = Mono, fontSize = 12.sp,
                        color = if (r.f == null) c.lcdError else c.bodyInk,
                        modifier = Modifier.weight(1.2f))
                    if (d.hasG) {
                        Text(r.g?.let { dstr(it) } ?: "错误", fontFamily = Mono, fontSize = 12.sp,
                            color = if (r.g == null) c.lcdError else c.bodyInk,
                            modifier = Modifier.weight(1.2f))
                    }
                }
                Spacer(Modifier.height(3.dp))
            }
            Spacer(Modifier.height(6.dp))
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = { if (cur > 0) page = cur - 1 },
                    enabled = cur > 0,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, c.keyEdge),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = c.keyNeutral, contentColor = c.bodyInk,
                        disabledContainerColor = c.keyNeutral.copy(alpha = 0.4f),
                        disabledContentColor = c.bodyInk.copy(alpha = 0.4f),
                    ),
                ) { Text("◀ 上一页", fontSize = 12.sp) }
                Text(
                    "第 ${cur + 1} / $pages 页（共 ${d.rows.size} 行）",
                    color = c.bodyInk, fontSize = 12.sp,
                )
                Button(
                    onClick = { if (cur < pages - 1) page = cur + 1 },
                    enabled = cur < pages - 1,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, c.keyEdge),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = c.keyNeutral, contentColor = c.bodyInk,
                        disabledContainerColor = c.keyNeutral.copy(alpha = 0.4f),
                        disabledContentColor = c.bodyInk.copy(alpha = 0.4f),
                    ),
                ) { Text("下一页 ▶", fontSize = 12.sp) }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "单点无定义（如 1/x 在 x = 0）时该格显示「错误」，不中断整张表；行数上限 ${TableGen.MAX_ROWS}。",
            color = c.keyNeutralInk.copy(alpha = 0.65f), fontSize = 11.sp,
        )
    }
}

// ---------------------------------------------------------------------------
// ④ 比例 RATIO：a:b = c:x 与 a:b = x:d
// ---------------------------------------------------------------------------

@Composable
fun RatioScreen(onBack: () -> Unit) {
    val c = LocalCalcColors.current
    var form by remember { mutableStateOf(RatioOps.Form.CX) }
    var ta by remember { mutableStateOf("2") }
    var tb by remember { mutableStateOf("3") }
    var tc by remember { mutableStateOf("4") }
    var result by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }

    fun solve() {
        error = ""
        result = ""
        try {
            val a = RatioOps.parseCoefficient(ta, "a")
            val b = RatioOps.parseCoefficient(tb, "b")
            val otherLabel = if (form == RatioOps.Form.CX) "c" else "d"
            val other = RatioOps.parseCoefficient(tc, otherLabel)
            val x = RatioOps.solve(form, a, b, other)
            result = "x = " + RatioOps.textOf(x)
        } catch (e: Exception) {
            error = e.message ?: "计算失败"
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(c.body)
            .safeAreaPadding()
            .padding(10.dp)
            .verticalScroll(rememberScrollState())
    ) {
        SubHeader("比例 RATIO", "a:b = c:x → x = b·c÷a ｜ a:b = x:d → x = a·d÷b", onBack)
        Spacer(Modifier.height(10.dp))

        SectionTitle("形式")
        ChoiceRow(RatioOps.Form.entries.map { it.title }, RatioOps.Form.entries.indexOf(form)) {
            form = RatioOps.Form.entries[it]
            result = ""
            error = ""
        }
        Spacer(Modifier.height(12.dp))

        val thirdLabel = if (form == RatioOps.Form.CX) "c" else "d"
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            NumField("a", ta, { ta = it }, Modifier.weight(1f))
            NumField("b", tb, { tb = it }, Modifier.weight(1f))
            NumField(thirdLabel, tc, { tc = it }, Modifier.weight(1f))
        }
        Spacer(Modifier.height(4.dp))
        Text(
            if (form == RatioOps.Form.CX) "$ta : $tb = $tc : x"
            else "$ta : $tb = x : $tc",
            color = c.keyNeutralInk.copy(alpha = 0.75f),
            fontFamily = Mono, fontSize = 13.sp,
        )
        Spacer(Modifier.height(10.dp))
        SubButton("求解 x", Modifier.fillMaxWidth(), primary = true) { solve() }

        Spacer(Modifier.height(12.dp))
        if (error.isNotEmpty()) {
            ResultBox(error, error = true)
        } else if (result.isNotEmpty()) {
            ResultBox(result)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "输入小数当场转有理数，能精确就给精确分数（括号内为小数近似）。",
            color = c.keyNeutralInk.copy(alpha = 0.65f), fontSize = 11.sp,
        )
    }
}
