package io.paimon.fx991.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.paimon.fx991.engine.AngleMode
import io.paimon.fx991.engine.CalcEngine
import io.paimon.fx991.engine.ComplexNum
import io.paimon.fx991.engine.DistrOps
import io.paimon.fx991.engine.ExactMath
import io.paimon.fx991.engine.FuncHelp
import io.paimon.fx991.engine.Matrix
import io.paimon.fx991.engine.MatrixStore
import io.paimon.fx991.engine.StatOps
import io.paimon.fx991.engine.Value
import io.paimon.fx991.engine.Vector3
import io.paimon.fx991.engine.VectorStore
import io.paimon.fx991.engine.label
import io.paimon.fx991.CalcViewModel

// ---------------------------------------------------------------------------
// 批次 C：六大子系统的独立界面（复数 / 矩阵 / 向量 / 统计 / 分布 / 函数帮助）
// 每个界面都是 Material 3 组件，可从模式菜单或 SHIFT 键进入，均可返回主计算界面。
// ---------------------------------------------------------------------------

/** 数值显示（10 位有效数字） */
internal fun dstr(v: Double): String = CalcEngine.format(v, 10, null)

internal fun angleUnit(mode: AngleMode): String = when (mode) {
    AngleMode.DEG -> "\u00B0"
    AngleMode.GRAD -> " grad"
    AngleMode.RAD -> " rad"
}

/** 子系统页面顶栏（标题 + 返回） */
@Composable
internal fun SubHeader(title: String, subtitle: String? = null, onBack: () -> Unit) {
    val c = LocalCalcColors.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Button(
            onClick = onBack,
            colors = ButtonDefaults.buttonColors(
                containerColor = c.keyNeutral,
                contentColor = c.bodyInk,
            ),
            shape = RoundedCornerShape(8.dp),
        ) { Text("\u25C0 返回", fontSize = 13.sp) }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(title, color = c.bodyInk, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            subtitle?.let {
                Text(it, color = c.keyNeutralInk.copy(alpha = 0.7f), fontSize = 10.sp)
            }
        }
    }
}

/** 单行数值输入框 */
@Composable
internal fun NumField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier,
) {
    val c = LocalCalcColors.current
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label, fontSize = 11.sp) },
        singleLine = true,
        textStyle = androidx.compose.ui.text.TextStyle(
            fontFamily = Mono, fontSize = 14.sp, color = c.bodyInk
        ),
        colors = androidx.compose.material3.TextFieldDefaults.colors(
            focusedContainerColor = c.keyNeutral,
            unfocusedContainerColor = c.keyNeutral,
            focusedIndicatorColor = ShiftOrange,
            unfocusedIndicatorColor = c.keyEdge,
            focusedLabelColor = ShiftOrange,
            unfocusedLabelColor = c.bodyInk,
            cursorColor = ShiftOrange,
        ),
        modifier = modifier,
    )
}

/** 一排操作按钮 */
@Composable
internal fun SubButton(
    label: String,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    onClick: () -> Unit,
) {
    val c = LocalCalcColors.current
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (primary) c.keyEquals else c.keyNeutral,
            contentColor = if (primary) c.keyEqualsInk else c.bodyInk,
        ),
        border = if (primary) null else BorderStroke(1.dp, c.keyEdge),
        shape = RoundedCornerShape(9.dp),
        modifier = modifier.height(42.dp),
    ) { Text(label, fontSize = 12.sp, maxLines = 1) }
}

/** 结果展示区（等宽、LCD 底色） */
@Composable
internal fun ResultBox(text: String, error: Boolean = false) {
    val c = LocalCalcColors.current
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(c.lcdBg)
            .border(1.dp, c.lcdEdge, RoundedCornerShape(6.dp))
            .padding(8.dp)
    ) {
        Text(
            text = text.ifEmpty { "（等待计算）" },
            color = if (error) c.lcdError else c.lcdFg,
            fontFamily = Mono,
            fontSize = 14.sp,
        )
    }
}

private val CM_OPS = listOf("A + B", "A \u2212 B", "A \u00D7 B", "A \u00F7 B")

/**
 * ① 复数模式 CMPLX：a+bi 输入 / 四则 / 模 · 辐角 · 共轭 / 直角⇄极坐标。
 * 能精确时走精确分数轨（输入小数当场转有理数）。
 */
@Composable
fun CmplxScreen(onBack: () -> Unit) {
    val c = LocalCalcColors.current
    var op by remember { mutableStateOf(0) }
    var are by remember { mutableStateOf("1") }
    var aim by remember { mutableStateOf("2") }
    var bre by remember { mutableStateOf("3") }
    var bim by remember { mutableStateOf("\u22124") }
    var mode by remember { mutableStateOf(AngleMode.DEG) }
    var out by remember { mutableStateOf("") }
    var extra by remember { mutableStateOf("") }
    var err by remember { mutableStateOf(false) }
    var polar by remember { mutableStateOf(false) }

    fun a(): ComplexNum = ComplexNum.ofLiteral(are, aim)
    fun b(): ComplexNum = ComplexNum.ofLiteral(bre, bim)

    fun run() {
        err = false
        try {
            val x = a()
            val y = b()
            val r = when (op) {
                0 -> x.plus(y)
                1 -> x.minus(y)
                2 -> x.times(y)
                else -> x.div(y)
            }
            out = if (polar) polarText(r, mode) else r.format()
            extra = ""
        } catch (e: Exception) {
            out = e.message ?: "计算失败"
            err = true
            extra = ""
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
        SubHeader("复数 CMPLX", "a+bi · 四则 · 模/辐角/共轭 · 直角⇄极坐标", onBack)
        Spacer(Modifier.height(10.dp))

        Text("运算", color = c.chromeInk, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(5.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            CM_OPS.forEachIndexed { i, t ->
                SubButton(
                    label = t,
                    primary = i == op,
                    modifier = Modifier.weight(1f),
                ) { op = i; run() }
            }
        }
        Spacer(Modifier.height(12.dp))

        Text("复数 A", color = c.chromeInk, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(5.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            NumField("实部 a", are, { are = it }, Modifier.weight(1f))
            NumField("虚部 b", aim, { aim = it }, Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Text("复数 B", color = c.chromeInk, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(5.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            NumField("实部 c", bre, { bre = it }, Modifier.weight(1f))
            NumField("虚部 d", bim, { bim = it }, Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SubButton("A + B", Modifier.weight(1f), primary = true) { op = 0; run() }
            SubButton("A \u2212 B", Modifier.weight(1f)) { op = 1; run() }
            SubButton("A \u00D7 B", Modifier.weight(1f)) { op = 2; run() }
            SubButton("A \u00F7 B", Modifier.weight(1f)) { op = 3; run() }
        }
        Spacer(Modifier.height(8.dp))

        Text("单复数运算", color = c.chromeInk, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(5.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SubButton("模 |A|", Modifier.weight(1f)) {
                err = false
                try {
                    val x = a()
                    val ex = x.modulusExact()
                    out = "|A| = " + (if (ex != null) CalcEngine.formatRational(ex, false) else dstr(x.modulus()))
                    extra = ""
                } catch (e: Exception) { out = e.message ?: ""; err = true }
            }
            SubButton("辐角 arg A", Modifier.weight(1f)) {
                err = false
                try {
                    out = "arg A = " + dstr(a().arg(mode)) + angleUnit(mode)
                    extra = ""
                } catch (e: Exception) { out = e.message ?: ""; err = true }
            }
            SubButton("共轭 A", Modifier.weight(1f)) {
                err = false
                try { out = a().conjugate().format() } catch (e: Exception) { out = e.message ?: ""; err = true }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SubButton("A → r∠θ", Modifier.weight(1f)) {
                err = false
                try { out = polarText(a(), mode) } catch (e: Exception) { out = e.message ?: ""; err = true }
            }
            SubButton("A → a+bi", Modifier.weight(1f)) {
                err = false
                try { out = a().format() } catch (e: Exception) { out = e.message ?: ""; err = true }
            }
            SubButton("r∠θ → A", Modifier.weight(1f)) {
                err = false
                try {
                    val r = are.trim().toDoubleOrNull() ?: throw Exception("r 不是合法数字")
                    val th = aim.trim().toDoubleOrNull() ?: throw Exception("θ 不是合法数字")
                    out = ComplexNum.fromPolar(r, th, mode).format()
                } catch (e: Exception) { out = e.message ?: ""; err = true }
            }
        }
        Spacer(Modifier.height(10.dp))

        Text("角度制", color = c.chromeInk, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(5.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AngleMode.entries.forEach { m ->
                SubButton(m.label, Modifier.weight(1f), primary = m == mode) { mode = m }
            }
        }
        Spacer(Modifier.height(12.dp))

        Text("结果", color = c.chromeInk, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(5.dp))
        ResultBox(out, err)
        if (extra.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            ResultBox(extra)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "输入的小数当场转有理数，四则运算在分数轨上做；模能开尽时也给精确值。",
            color = c.keyNeutralInk.copy(alpha = 0.65f), fontSize = 11.sp,
        )
    }
}

/** 复数 → r∠θ 文本 */
private fun polarText(z: ComplexNum, mode: AngleMode): String {
    val p = z.toPolar(mode)
    return "${dstr(p.r)}\u2220${dstr(p.theta)}${angleUnit(mode)}"
}

private val MAT_NAMES = listOf("A", "B", "C", "D")

/**
 * ② 矩阵模式 MATRIX：MatA–MatD（最大 4×4）；输入/编辑、加减乘、行列式、逆、
 * 转置、单位阵、标量乘。整数 / 分数矩阵全程精确。
 */
@Composable
fun MatrixScreen(onBack: () -> Unit, store: MatrixStore = MatrixStore()) {
    val c = LocalCalcColors.current
    val cells = remember { mutableStateListOf(*Array(16) { "0" }) }
    var cur by remember { mutableStateOf("A") }
    var other by remember { mutableStateOf("B") }
    var rows by remember { mutableStateOf(2) }
    var cols by remember { mutableStateOf(2) }
    var k by remember { mutableStateOf("2") }
    var out by remember { mutableStateOf("") }
    var err by remember { mutableStateOf(false) }

    fun build(r: Int, cc: Int): Matrix {
        val list = ArrayList<Value>()
        for (i in 0 until r) for (j in 0 until cc) list.add(ComplexNum.literal(cells[i * 4 + j]))
        return Matrix.of(r, cc, list)
    }

    fun writeBack(m: Matrix) {
        rows = m.rows
        cols = m.cols
        for (i in 0 until m.rows) for (j in 0 until m.cols) cells[i * 4 + j] = ExactMath.str(m[i, j])
    }

    fun load(name: String) {
        val m = store.get(name)
        if (m != null) {
            writeBack(m)
        } else {
            for (i in 0 until 16) cells[i] = "0"
            rows = 2; cols = 2
        }
        out = ""
        err = false
    }

    fun guard(block: () -> Unit) {
        err = false
        try {
            block()
        } catch (e: Exception) {
            out = e.message ?: "计算失败"
            err = true
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
        SubHeader("矩阵 MATRIX", "MatA\u2013D（≤4\u00D74）· 加减乘 · det · 逆 · 转置 · 单位阵 · 标量乘", onBack)
        Spacer(Modifier.height(10.dp))

        Text("当前矩阵", color = c.chromeInk, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(5.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            MAT_NAMES.forEach { n ->
                SubButton("Mat$n", Modifier.weight(1f), primary = n == cur) { cur = n; load(n) }
            }
        }
        Spacer(Modifier.height(10.dp))

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("行 $rows", color = c.chromeInk, fontSize = 12.sp, modifier = Modifier.weight(1f))
            SubButton("−", Modifier.width(52.dp)) { if (rows > 1) rows-- }
            Spacer(Modifier.width(6.dp))
            SubButton("+", Modifier.width(52.dp)) { if (rows < 4) rows++ }
            Spacer(Modifier.width(16.dp))
            Text("列 $cols", color = c.chromeInk, fontSize = 12.sp, modifier = Modifier.weight(1f))
            SubButton("−", Modifier.width(52.dp)) { if (cols > 1) cols-- }
            Spacer(Modifier.width(6.dp))
            SubButton("+", Modifier.width(52.dp)) { if (cols < 4) cols++ }
        }
        Spacer(Modifier.height(10.dp))

        for (i in 0 until rows) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                for (j in 0 until cols) {
                    val idx = i * 4 + j
                    NumField("$i,$j", cells[idx], { cells[idx] = it }, Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(6.dp))
        }
        Spacer(Modifier.height(4.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SubButton("存入 Mat$cur", Modifier.weight(1f), primary = true) {
                guard { store.set(cur, build(rows, cols)); out = "已存入 Mat$cur" }
            }
            SubButton("单位阵", Modifier.weight(1f)) {
                guard { val m = Matrix.identity(rows); store.set(cur, m); writeBack(m); out = "单位阵 $rows\u00D7$rows" }
            }
            SubButton("零阵", Modifier.weight(1f)) {
                guard { val m = Matrix.zeros(rows, cols); store.set(cur, m); writeBack(m); out = "零阵" }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SubButton("转置", Modifier.weight(1f)) { guard { writeBack(build(rows, cols).transpose()); out = "已转置" } }
            SubButton("行列式", Modifier.weight(1f)) { guard { out = "det = " + ExactMath.str(build(rows, cols).det()) } }
            SubButton("逆", Modifier.weight(1f)) { guard { writeBack(build(rows, cols).inverse()); out = "已求逆" } }
        }
        Spacer(Modifier.height(10.dp))

        Text("第二矩阵", color = c.chromeInk, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(5.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            MAT_NAMES.forEach { n ->
                SubButton("Mat$n", Modifier.weight(1f), primary = n == other) { other = n }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SubButton("$cur + $other", Modifier.weight(1f)) {
                guard { writeBack(build(rows, cols).plus(store.get(other) ?: throw Exception("Mat$other 未存入"))); out = "Mat$cur + Mat$other" }
            }
            SubButton("$cur − $other", Modifier.weight(1f)) {
                guard { writeBack(build(rows, cols).minus(store.get(other) ?: throw Exception("Mat$other 未存入"))); out = "Mat$cur − Mat$other" }
            }
            SubButton("$cur × $other", Modifier.weight(1f)) {
                guard { writeBack(build(rows, cols).times(store.get(other) ?: throw Exception("Mat$other 未存入"))); out = "Mat$cur × Mat$other" }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            NumField("标量 k", k, { k = it }, Modifier.weight(1f))
            SubButton("$cur × k", Modifier.weight(1f)) {
                guard { writeBack(build(rows, cols).scalar(ComplexNum.literal(k))); out = "Mat$cur × $k" }
            }
        }
        Spacer(Modifier.height(12.dp))

        Text("结果", color = c.chromeInk, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(5.dp))
        ResultBox(out, err)
        Spacer(Modifier.height(8.dp))
        Text(
            "元素支持小数与 a/b 分数；整数 / 分数矩阵的加减乘、det、逆全程精确。",
            color = c.keyNeutralInk.copy(alpha = 0.65f), fontSize = 11.sp,
        )
    }
}

private val VEC_NAMES = listOf("A", "B", "C", "D")

/**
 * ③ 向量模式 VECTOR：VctA–VctD（三维）；加减、点积、叉积、模、单位化、夹角。
 */
@Composable
fun VectorScreen(onBack: () -> Unit, store: VectorStore = VectorStore()) {
    val c = LocalCalcColors.current
    var cur by remember { mutableStateOf("A") }
    var other by remember { mutableStateOf("B") }
    var ax by remember { mutableStateOf("1") }
    var ay by remember { mutableStateOf("2") }
    var az by remember { mutableStateOf("3") }
    var mode by remember { mutableStateOf(AngleMode.DEG) }
    var out by remember { mutableStateOf("") }
    var err by remember { mutableStateOf(false) }

    fun cur3(): Vector3 = Vector3.ofLiteral(ax, ay, az)
    fun writeBack(v: Vector3) {
        ax = ExactMath.str(v.x)
        ay = ExactMath.str(v.y)
        az = ExactMath.str(v.z)
    }
    fun load(name: String) {
        val v = store.get(name)
        if (v != null) writeBack(v) else { ax = "0"; ay = "0"; az = "0" }
        out = ""
        err = false
    }
    fun guard(block: () -> Unit) {
        err = false
        try {
            block()
        } catch (e: Exception) {
            out = e.message ?: "计算失败"
            err = true
        }
    }
    fun other3(): Vector3 = store.get(other) ?: throw Exception("Vct$other 未存入")

    Column(
        Modifier
            .fillMaxSize()
            .background(c.body)
            .safeAreaPadding()
            .padding(10.dp)
            .verticalScroll(rememberScrollState())
    ) {
        SubHeader("向量 VECTOR", "VctA\u2013D（三维）· 加减 · 点积 · 叉积 · 模 · 单位化 · 夹角", onBack)
        Spacer(Modifier.height(10.dp))

        Text("当前向量", color = c.chromeInk, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(5.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            VEC_NAMES.forEach { n ->
                SubButton("Vct$n", Modifier.weight(1f), primary = n == cur) { cur = n; load(n) }
            }
        }
        Spacer(Modifier.height(10.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            NumField("x", ax, { ax = it }, Modifier.weight(1f))
            NumField("y", ay, { ay = it }, Modifier.weight(1f))
            NumField("z", az, { az = it }, Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SubButton("存入 Vct$cur", Modifier.weight(1f), primary = true) {
                guard { store.set(cur, cur3()); out = "已存入 Vct$cur = ${cur3().format()}" }
            }
            SubButton("模 |$cur|", Modifier.weight(1f)) {
                guard {
                    val v = cur3()
                    val ex = v.normExact()
                    out = "|Vct$cur| = " + (if (ex != null) CalcEngine.formatRational(ex, false) else dstr(v.norm()))
                }
            }
            SubButton("单位化 $cur", Modifier.weight(1f)) {
                guard { val u = cur3().unit(); writeBack(u); out = "已单位化 = ${u.format()}" }
            }
        }
        Spacer(Modifier.height(10.dp))

        Text("第二向量", color = c.chromeInk, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(5.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            VEC_NAMES.forEach { n ->
                SubButton("Vct$n", Modifier.weight(1f), primary = n == other) { other = n }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SubButton("$cur + $other", Modifier.weight(1f)) {
                guard { val r = cur3().plus(other3()); writeBack(r); out = r.format() }
            }
            SubButton("$cur − $other", Modifier.weight(1f)) {
                guard { val r = cur3().minus(other3()); writeBack(r); out = r.format() }
            }
            SubButton("$cur · $other", Modifier.weight(1f)) {
                guard { out = "${cur}·${other} = " + ExactMath.str(cur3().dot(other3())) }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SubButton("$cur × $other", Modifier.weight(1f)) {
                guard { val r = cur3().cross(other3()); writeBack(r); out = r.format() }
            }
            SubButton("夹角 θ", Modifier.weight(1f)) {
                guard { out = "θ = " + dstr(cur3().angleTo(other3(), mode)) + angleUnit(mode) }
            }
        }
        Spacer(Modifier.height(10.dp))

        Text("角度制（夹角输出）", color = c.chromeInk, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(5.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AngleMode.entries.forEach { m ->
                SubButton(m.label, Modifier.weight(1f), primary = m == mode) { mode = m }
            }
        }
        Spacer(Modifier.height(12.dp))

        Text("结果", color = c.chromeInk, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(5.dp))
        ResultBox(out, err)
        Spacer(Modifier.height(8.dp))
        Text(
            "整数 / 分数分量的加减、点积、叉积保持精确；模能开尽时也给精确值。",
            color = c.keyNeutralInk.copy(alpha = 0.65f), fontSize = 11.sp,
        )
    }
}

/**
 * ④ 统计模式 STAT：单变量（n / Σx / Σx² / 均值 / 总体σ / 样本s / 最大最小 / 中位数），
 * 双变量线性回归 y = a + b·x 与相关系数 r；数据表可编辑 / 删除。
 */
@Composable
fun StatScreen(onBack: () -> Unit) {
    val c = LocalCalcColors.current
    var twoVar by remember { mutableStateOf(false) }
    var xIn by remember { mutableStateOf("") }
    var yIn by remember { mutableStateOf("") }
    val xs = remember { mutableStateListOf<Double>() }
    val ys = remember { mutableStateListOf<Double>() }
    var msg by remember { mutableStateOf("") }

    fun parse(s: String): Double? = try {
        if (s.isBlank()) null else ComplexNum.literal(s).toDouble()
    } catch (_: Exception) {
        null
    }

    fun add() {
        val x = parse(xIn)
        if (x == null) { msg = "x 不是合法数字"; return }
        if (twoVar) {
            val y = parse(yIn)
            if (y == null) { msg = "y 不是合法数字"; return }
            xs.add(x); ys.add(y)
        } else {
            xs.add(x)
        }
        xIn = ""; yIn = ""; msg = "已添加（当前 ${xs.size} 组）"
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(c.body)
            .safeAreaPadding()
            .padding(10.dp)
            .verticalScroll(rememberScrollState())
    ) {
        SubHeader("统计与回归 STAT", "单变量描述统计 · 双变量线性回归 y = a + b·x", onBack)
        Spacer(Modifier.height(10.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SubButton("单变量", Modifier.weight(1f), primary = !twoVar) { twoVar = false }
            SubButton("双变量", Modifier.weight(1f), primary = twoVar) { twoVar = true }
        }
        Spacer(Modifier.height(10.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            NumField("x", xIn, { xIn = it }, Modifier.weight(1f))
            if (twoVar) NumField("y", yIn, { yIn = it }, Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SubButton("添加", Modifier.weight(1f), primary = true) { add() }
            SubButton("清除全部", Modifier.weight(1f)) { xs.clear(); ys.clear(); msg = "已清空" }
        }
        if (msg.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(msg, color = c.keyNeutralInk.copy(alpha = 0.7f), fontSize = 11.sp)
        }
        Spacer(Modifier.height(10.dp))

        Text("数据表（点右侧删除）", color = c.chromeInk, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(5.dp))
        if (xs.isEmpty()) {
            Text("（无数据）", color = c.keyNeutralInk.copy(alpha = 0.6f), fontSize = 12.sp)
        } else {
            xs.indices.forEach { i ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (twoVar) "${i + 1}.  x=${dstr(xs[i])}   y=${dstr(ys[i])}"
                        else "${i + 1}.  x=${dstr(xs[i])}",
                        fontFamily = Mono, fontSize = 12.sp, color = c.bodyInk,
                        modifier = Modifier.weight(1f),
                    )
                    SubButton("删除", Modifier.width(60.dp)) {
                        xs.removeAt(i)
                        if (twoVar) ys.removeAt(i)
                    }
                }
                Spacer(Modifier.height(6.dp))
            }
        }
        Spacer(Modifier.height(10.dp))

        Text("结果", color = c.chromeInk, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(5.dp))
        if (xs.isEmpty()) {
            ResultBox("")
        } else if (!twoVar) {
            val r = StatOps.oneVar(xs.toList())
            ResultBox(
                listOf(
                    "n     = ${r.n}",
                    "Σx    = ${dstr(r.sum)}",
                    "Σx²   = ${dstr(r.sumSq)}",
                    "x̄     = ${dstr(r.mean)}",
                    "σx    = ${dstr(r.popSigma)}   （总体）",
                    "sx    = ${if (r.sampleS.isNaN()) "—" else dstr(r.sampleS)}   （样本）",
                    "min   = ${dstr(r.min)}",
                    "max   = ${dstr(r.max)}",
                    "中位数 = ${dstr(r.median)}",
                ).joinToString("\n")
            )
        } else {
            val r = StatOps.linearRegression(xs.toList(), ys.toList())
            ResultBox(
                listOf(
                    "n  = ${r.n}",
                    "a  = ${dstr(r.a)}",
                    "b  = ${dstr(r.b)}",
                    "r  = ${dstr(r.r)}",
                    "y = a + b·x",
                ).joinToString("\n")
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "σ 用分母 n，s 用分母 n−1；回归形式为 y = a + b·x。",
            color = c.keyNeutralInk.copy(alpha = 0.65f), fontSize = 11.sp,
        )
    }
}

private val DISTR_TABS = listOf("正态分布", "二项分布", "泊松分布")

/**
 * ⑤ 分布模式 DISTR：正态（给定 μ,σ 求 P/Q/R）、二项分布、泊松分布。
 */
@Composable
fun DistrScreen(onBack: () -> Unit) {
    val c = LocalCalcColors.current
    var tab by remember { mutableStateOf(0) }
    var a1 by remember { mutableStateOf("0") }
    var a2 by remember { mutableStateOf("1") }
    var a3 by remember { mutableStateOf("1") }
    var out by remember { mutableStateOf("") }
    var err by remember { mutableStateOf(false) }

    fun num(s: String, what: String): Double =
        s.trim().toDoubleOrNull() ?: throw Exception("$what 不是合法数字")

    fun intNum(s: String, what: String): Int {
        val d = num(s, what)
        if (d != Math.floor(d)) throw Exception("$what 必须是整数")
        return d.toInt()
    }

    fun run() {
        err = false
        try {
            out = when (tab) {
                0 -> {
                    val r = DistrOps.normal(num(a3, "x"), num(a1, "μ"), num(a2, "σ"))
                    listOf(
                        "把 x 标准化：t = (x − μ) / σ",
                        "P = P(X < x)  = ${dstr(r.p)}",
                        "Q = P(X > x)  = ${dstr(r.q)}",
                        "R = 中央对称 = ${dstr(r.r)}",
                        "密度 φ = ${dstr(DistrOps.normPdf((num(a3, "x") - num(a1, "μ")) / num(a2, "σ")))}",
                    ).joinToString("\n")
                }
                1 -> {
                    val n = intNum(a1, "n")
                    val p = num(a2, "p")
                    val k = intNum(a3, "k")
                    listOf(
                        "P(X = k) = ${dstr(DistrOps.binomialPdf(n, k, p))}",
                        "P(X ≤ k) = ${dstr(DistrOps.binomialCdf(n, k, p))}",
                        "P(X ≥ k) = ${dstr(1.0 - DistrOps.binomialCdf(n, k - 1, p))}",
                    ).joinToString("\n")
                }
                else -> {
                    val lam = num(a1, "λ")
                    val k = intNum(a3, "k")
                    listOf(
                        "P(X = k) = ${dstr(DistrOps.poissonPdf(lam, k))}",
                        "P(X ≤ k) = ${dstr(DistrOps.poissonCdf(lam, k))}",
                        "P(X ≥ k) = ${dstr(1.0 - DistrOps.poissonCdf(lam, k - 1))}",
                    ).joinToString("\n")
                }
            }
        } catch (e: Exception) {
            out = e.message ?: "计算失败"
            err = true
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
        SubHeader("概率分布 DISTR", "正态 · 二项 · 泊松", onBack)
        Spacer(Modifier.height(10.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            DISTR_TABS.forEachIndexed { i, t ->
                SubButton(t, Modifier.weight(1f), primary = i == tab) { tab = i }
            }
        }
        Spacer(Modifier.height(12.dp))

        when (tab) {
            0 -> Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                NumField("μ", a1, { a1 = it }, Modifier.weight(1f))
                NumField("σ", a2, { a2 = it }, Modifier.weight(1f))
                NumField("x", a3, { a3 = it }, Modifier.weight(1f))
            }
            1 -> Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                NumField("n", a1, { a1 = it }, Modifier.weight(1f))
                NumField("p", a2, { a2 = it }, Modifier.weight(1f))
                NumField("k", a3, { a3 = it }, Modifier.weight(1f))
            }
            else -> Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                NumField("λ", a1, { a1 = it }, Modifier.weight(1f))
                NumField("k", a3, { a3 = it }, Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(10.dp))
        SubButton("计算", Modifier.fillMaxWidth(), primary = true) { run() }
        Spacer(Modifier.height(12.dp))

        Text("结果", color = c.chromeInk, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(5.dp))
        ResultBox(out, err)
        Spacer(Modifier.height(8.dp))
        Text(
            "正态：P 是左尾、Q 是右尾、R 是以均值为中心的对称区间；\n" +
                "二项 / 泊松给出单点概率与累积概率。",
            color = c.keyNeutralInk.copy(alpha = 0.65f), fontSize = 11.sp,
        )
    }
}

/**
 * ⑥ 函数帮助 FUNC HELP：函数目录 + 语法说明页；点一条把语法键插入表达式并回到计算界面。
 */
@Composable
fun FuncHelpScreen(vm: CalcViewModel, onBack: () -> Unit) {
    val c = LocalCalcColors.current
    Column(
        Modifier
            .fillMaxSize()
            .background(c.body)
            .safeAreaPadding()
            .padding(10.dp)
            .verticalScroll(rememberScrollState())
    ) {
        SubHeader("函数帮助 FUNC HELP", "共 ${FuncHelp.COUNT} 条 · 点一条插入表达式", onBack)
        Spacer(Modifier.height(10.dp))

        FuncHelp.byCategory().forEach { (cat, list) ->
            Text(cat, color = c.chromeInk, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            list.forEach { e ->
                Button(
                    onClick = { vm.insertFromHelp(e.insert) },
                    shape = RoundedCornerShape(9.dp),
                    border = BorderStroke(1.dp, c.keyEdge),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = c.keyNeutral,
                        contentColor = c.bodyInk,
                    ),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 12.dp, vertical = 6.dp
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.fillMaxWidth()) {
                        Text("${e.name}    ${e.syntax}", fontFamily = Mono, fontSize = 12.sp, maxLines = 1)
                        Text(
                            e.desc,
                            fontSize = 11.sp, maxLines = 2,
                            color = c.keyNeutralInk.copy(alpha = 0.7f),
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}
