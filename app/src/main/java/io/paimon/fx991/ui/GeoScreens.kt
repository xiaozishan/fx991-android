package io.paimon.fx991.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.paimon.fx991.CalcViewModel
import io.paimon.fx991.engine.AngleMode
import io.paimon.fx991.engine.CalcEngine
import io.paimon.fx991.engine.ComplexFunc
import io.paimon.fx991.engine.Cx
import io.paimon.fx991.engine.FourierOps
import io.paimon.fx991.engine.UserFunctions
import io.paimon.fx991.engine.Value
import kotlin.math.abs

// ---------------------------------------------------------------------------
// 批次 G：三个新界面 —— 代数区（GeoGebra 式定义管理 + 画图）/ 傅里叶级数 / 复变函数
// 观感与批次 C / D 一致：SubHeader + NumField + SubButton + ResultBox，
// 根节点一律 Modifier.safeAreaPadding()，深浅主题走同一套 LocalCalcColors 令牌。
// ---------------------------------------------------------------------------

private fun fmt(v: Double): String = CalcEngine.format(v, 10, null)

private fun fmtCx(z: Cx): String {
    val re = fmt(z.re)
    if (abs(z.im) <= 1e-12) return re
    val im = fmt(abs(z.im))
    return if (z.im < 0.0) "$re − ${im}i" else "$re + ${im}i"
}

/** 一条待画曲线（采样点 + 颜色） */
private class PlotSeries(val pts: List<Pair<Double, Double>>, val color: Color)

/** 函数采样（无定义 / 非有限 / 爆值的点直接跳过） */
private fun sampleFn(f: (Double) -> Double?, xmin: Double, xmax: Double, n: Int = 241): List<Pair<Double, Double>> {
    val out = ArrayList<Pair<Double, Double>>()
    for (k in 0 until n) {
        val x = xmin + (xmax - xmin) * k / (n - 1)
        val y = try {
            f(x)
        } catch (_: Exception) {
            null
        }
        if (y != null && y.isFinite() && abs(y) < 1e6) out.add(x to y)
    }
    return out
}

/** ○ 通用曲线图：Canvas 手绘坐标轴 + 多条折线（复用 ODE 曲线图的观感） */
@Composable
private fun FunPlot(series: List<PlotSeries>, modifier: Modifier) {
    val c = LocalCalcColors.current
    Box(
        modifier
            .clip(RoundedCornerShape(6.dp))
            .background(c.lcdBg)
            .border(1.dp, c.lcdEdge, RoundedCornerShape(6.dp))
    ) {
        Canvas(Modifier.fillMaxSize().padding(10.dp)) {
            val w = size.width
            val h = size.height
            if (w <= 0f || h <= 0f) return@Canvas
            val all = series.flatMap { it.pts }
            if (all.isEmpty()) return@Canvas
            val xMin = all.minOf { it.first }
            val xMax = all.maxOf { it.first }
            var yMin = all.minOf { it.second }
            var yMax = all.maxOf { it.second }
            if (xMax - xMin < 1e-12) return@Canvas
            if (yMax - yMin < 1e-12) {
                yMin -= 1.0
                yMax += 1.0
            }
            val rawRange = yMax - yMin
            val padY = rawRange * 0.08
            yMin -= padY
            yMax += padY

            fun sx(x: Double): Float = ((x - xMin) / (xMax - xMin)).toFloat() * w
            fun sy(y: Double): Float = h - ((y - yMin) / (yMax - yMin)).toFloat() * h

            val axisColor = c.lcdDim
            if (yMin <= 0.0 && yMax >= 0.0) {
                val y0 = sy(0.0)
                drawLine(axisColor, Offset(0f, y0), Offset(w, y0), strokeWidth = 1.2f)
            }
            if (xMin <= 0.0 && xMax >= 0.0) {
                val x0 = sx(0.0)
                drawLine(axisColor, Offset(x0, 0f), Offset(x0, h), strokeWidth = 1.2f)
            }
            drawRect(axisColor, style = Stroke(width = 1f))

            for (s in series) {
                val path = Path()
                var started = false
                var prevY = 0.0
                for ((x, y) in s.pts) {
                    // 跳跃超过 4 倍值域 → 视为间断点，断开折线（如 1/x 的渐近线）
                    val jump = started && abs(y - prevY) > 4.0 * rawRange
                    if (!started || jump) {
                        path.moveTo(sx(x), sy(y))
                        started = true
                    } else {
                        path.lineTo(sx(x), sy(y))
                    }
                    prevY = y
                }
                drawPath(path, s.color, style = Stroke(width = 2.2f))
            }
        }
    }
}

private val SERIES_COLORS = listOf(ShiftOrange, AlphaPurple, Color(0xFF2E9E5B), Color(0xFF3B7DD8))

// ---------------------------------------------------------------------------
// ① 代数区（GeoGebra 式：已定义函数 / 变量的查看 · 插入 · 删除 · 画图）
// ---------------------------------------------------------------------------

@Composable
fun AlgebraScreen(vm: CalcViewModel, onBack: () -> Unit) {
    val c = LocalCalcColors.current
    var defineInput by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var msgError by remember { mutableStateOf(false) }
    val plotOn = remember { mutableStateListOf<String>() }
    var xminText by remember { mutableStateOf("-10") }
    var xmaxText by remember { mutableStateOf("10") }

    Column(
        Modifier
            .fillMaxSize()
            .background(c.body)
            .safeAreaPadding()
            .padding(horizontal = 12.dp)
            .verticalScroll(rememberScrollState())
    ) {
        SubHeader("代数区", "GeoGebra 式：主行敲 f(x)=x^2 按 = 定义；f(3) 调用 · f'(2) 求导 · 嵌套都行", onBack)
        Spacer(Modifier.height(10.dp))

        // 快速定义
        Row(verticalAlignment = Alignment.CenterVertically) {
            NumField("在此定义，如 f(x)=x^2 或 g(t):=t^2+1", defineInput, { defineInput = it }, Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            SubButton("定义", primary = true) {
                try {
                    message = vm.defineFromAlgebra(defineInput)
                    msgError = false
                    defineInput = ""
                } catch (e: Exception) {
                    message = e.message ?: "定义失败"
                    msgError = true
                }
            }
        }
        if (message.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            ResultBox(message, error = msgError)
        }
        Spacer(Modifier.height(14.dp))

        // 已定义函数
        Text("已定义函数（${vm.definedFunctions.size}）", color = c.chromeInk, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(5.dp))
        if (vm.definedFunctions.isEmpty()) {
            Text(
                "还没有自定义函数。主行敲 f(x)=x^2 按 =，或在上面输入框里定义。",
                color = c.keyNeutralInk.copy(alpha = 0.65f), fontSize = 11.sp,
            )
        }
        vm.definedFunctions.forEach { def ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    def.fullText(),
                    color = c.bodyInk, fontFamily = Mono, fontSize = 13.sp,
                    modifier = Modifier.weight(1f),
                )
                SubButton("插入") { vm.insertFromHelp(def.name + "(") }
                Spacer(Modifier.width(5.dp))
                SubButton(if (def.name in plotOn) "隐藏" else "画图",
                    primary = def.name in plotOn) {
                    if (def.name in plotOn) plotOn.remove(def.name) else plotOn.add(def.name)
                }
                Spacer(Modifier.width(5.dp))
                SubButton("删除") {
                    plotOn.remove(def.name)
                    vm.removeUserFunction(def.name)
                }
            }
        }
        Spacer(Modifier.height(14.dp))

        // STO 变量
        Text("变量（STO 存入）", color = c.chromeInk, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(5.dp))
        if (vm.variables.isEmpty()) {
            Text(
                "还没有变量。主行算出结果后按 RCL（SHIFT 层 STO）可存入 A–F / x / y。",
                color = c.keyNeutralInk.copy(alpha = 0.65f), fontSize = 11.sp,
            )
        }
        vm.variables.forEach { (name, v) ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "$name = ${vm.formatNumber(v)}",
                    color = c.bodyInk, fontFamily = Mono, fontSize = 13.sp,
                    modifier = Modifier.weight(1f),
                )
                SubButton("插入") { vm.insertFromHelp(name) }
                Spacer(Modifier.width(5.dp))
                SubButton("删除") { vm.removeVariable(name) }
            }
        }

        // 画图
        if (plotOn.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Text("函数图象", color = c.chromeInk, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(5.dp))
            Row {
                NumField("x 下限", xminText, { xminText = it }, Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                NumField("x 上限", xmaxText, { xmaxText = it }, Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            val xmin = xminText.toDoubleOrNull() ?: -10.0
            val xmax = xmaxText.toDoubleOrNull() ?: 10.0
            if (xmax > xmin) {
                val series = plotOn.mapIndexed { idx, name ->
                    PlotSeries(
                        sampleFn({ x ->
                            UserFunctions.call(name, listOf(Value.of(x)), 0, AngleMode.RAD).toDouble()
                        }, xmin, xmax),
                        SERIES_COLORS[idx % SERIES_COLORS.size],
                    )
                }
                FunPlot(series, Modifier.fillMaxWidth().height(220.dp))
                Spacer(Modifier.height(4.dp))
                Text(
                    plotOn.mapIndexed { idx, n -> "${n}(x)" }.joinToString(" · ") + "（弧度制采样）",
                    color = c.keyNeutralInk.copy(alpha = 0.65f), fontSize = 10.sp,
                )
            } else {
                Text("x 上限需大于下限", color = c.lcdError, fontSize = 11.sp)
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

// ---------------------------------------------------------------------------
// ② 傅里叶级数：f(x) 在 [a, b] 上前 n 项系数 + 部分和 + 与原函数对照曲线
// ---------------------------------------------------------------------------

@Composable
fun FourierScreen(onBack: () -> Unit) {
    val c = LocalCalcColors.current
    var f by remember { mutableStateOf("x^2") }
    var aText by remember { mutableStateOf("-pi") }
    var bText by remember { mutableStateOf("pi") }
    var nText by remember { mutableStateOf("5") }
    var result by remember { mutableStateOf<FourierOps.Result?>(null) }
    var report by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }

    Column(
        Modifier
            .fillMaxSize()
            .background(c.body)
            .safeAreaPadding()
            .padding(horizontal = 12.dp)
            .verticalScroll(rememberScrollState())
    ) {
        SubHeader("傅里叶级数", "主行也可直接敲 fourier(x^2, -pi, pi, 5)", onBack)
        Spacer(Modifier.height(10.dp))
        NumField("f(x)", f, { f = it }, Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        Row {
            NumField("下限 a", aText, { aText = it }, Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            NumField("上限 b", bText, { bText = it }, Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            NumField("项数 n", nText, { nText = it }, Modifier.weight(0.7f))
        }
        Spacer(Modifier.height(8.dp))
        SubButton("计算傅里叶系数", primary = true, modifier = Modifier.fillMaxWidth()) {
            error = ""
            try {
                val args = FourierOps.parseMain("fourier($f,$aText,$bText,$nText)")
                    ?: throw IllegalStateException("parse")
                val r = FourierOps.compute(args.f, args.a, args.b, args.n)
                result = r
                report = FourierOps.formatReport(r)
            } catch (e: Exception) {
                result = null
                report = ""
                error = e.message ?: "计算失败"
            }
        }
        Spacer(Modifier.height(8.dp))
        if (error.isNotEmpty()) ResultBox(error, error = true)
        if (report.isNotEmpty()) {
            ResultBox(report)
            Spacer(Modifier.height(6.dp))
            Text(
                "系数为自适应 Simpson 数值积分（容差 1e-9，弧度制）；部分和表达式可粘回主行（注意主行三角跟随角度制）。",
                color = c.keyNeutralInk.copy(alpha = 0.65f), fontSize = 10.sp,
            )
        }
        val r = result
        if (r != null) {
            Spacer(Modifier.height(12.dp))
            Text("原函数 vs 部分和 S${r.n}(x)", color = c.chromeInk, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(5.dp))
            val orig = sampleFn({ x -> r.fAt(x) }, r.a, r.b)
            val part = sampleFn({ x -> r.partialAt(x) }, r.a, r.b)
            FunPlot(
                listOf(PlotSeries(orig, ShiftOrange), PlotSeries(part, AlphaPurple)),
                Modifier.fillMaxWidth().height(220.dp),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "橙：f(x)　紫：S${r.n}(x)（间断点附近部分和会有 Gibbs 振荡）",
                color = c.keyNeutralInk.copy(alpha = 0.65f), fontSize = 10.sp,
            )
        }
        Spacer(Modifier.height(16.dp))
    }
}

// ---------------------------------------------------------------------------
// ③ 复变函数：求值 / 留数 / 留数定理围道积分
// ---------------------------------------------------------------------------

@Composable
fun CplxFuncScreen(onBack: () -> Unit) {
    val c = LocalCalcColors.current
    // 求值
    var evalF by remember { mutableStateOf("exp(z)") }
    var evalZ by remember { mutableStateOf("i*pi") }
    var evalOut by remember { mutableStateOf("") }
    var evalErr by remember { mutableStateOf(false) }
    // 留数
    var resF by remember { mutableStateOf("1/(z^2+1)") }
    var resZ by remember { mutableStateOf("i") }
    var resOut by remember { mutableStateOf("") }
    var resErr by remember { mutableStateOf(false) }
    // 围道积分
    var cintF by remember { mutableStateOf("1/(z^2+1)") }
    var cintPoles by remember { mutableStateOf("i, -i") }
    var cintOut by remember { mutableStateOf("") }
    var cintErr by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .background(c.body)
            .safeAreaPadding()
            .padding(horizontal = 12.dp)
            .verticalScroll(rememberScrollState())
    ) {
        SubHeader("复变函数", "z 为自变量，i 为虚数单位；主行也可直接 res(f(z), z0) / cint(f(z), p1, …)", onBack)
        Spacer(Modifier.height(6.dp))
        Text(
            ComplexFunc.BRANCH_NOTE + "；三角 / 双曲一律弧度。模 / 辐角 / 共轭的逐格输入见 CMPLX 模式。",
            color = c.keyNeutralInk.copy(alpha = 0.65f), fontSize = 10.sp,
        )
        Spacer(Modifier.height(12.dp))

        // ---- 复函数求值 ----
        Text("复函数求值", color = c.chromeInk, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(5.dp))
        Row {
            NumField("f(z)，如 exp(z) / sin(z) / z^2+1", evalF, { evalF = it }, Modifier.weight(1.4f))
            Spacer(Modifier.width(8.dp))
            NumField("z =", evalZ, { evalZ = it }, Modifier.weight(1f))
        }
        Spacer(Modifier.height(6.dp))
        SubButton("求值", primary = true, modifier = Modifier.fillMaxWidth()) {
            try {
                val z = ComplexFunc.evalExpr(evalZ, Cx.ZERO)
                val v = ComplexFunc.evalExpr(evalF, z)
                evalOut = "f(${fmtCx(z)}) = ${fmtCx(v)}\n|值| = ${fmt(v.modulus())}，arg = ${fmt(v.arg())} rad"
                evalErr = false
            } catch (e: Exception) {
                evalOut = e.message ?: "计算失败"
                evalErr = true
            }
        }
        if (evalOut.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            ResultBox(evalOut, error = evalErr)
        }
        Spacer(Modifier.height(14.dp))

        // ---- 留数 ----
        Text("留数 Res(f, z0)", color = c.chromeInk, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(5.dp))
        Row {
            NumField("f(z)", resF, { resF = it }, Modifier.weight(1.4f))
            Spacer(Modifier.width(8.dp))
            NumField("z0 =", resZ, { resZ = it }, Modifier.weight(1f))
        }
        Spacer(Modifier.height(6.dp))
        SubButton("求留数", primary = true, modifier = Modifier.fillMaxWidth()) {
            try {
                val z0 = ComplexFunc.evalExpr(resZ, Cx.ZERO)
                val r = ComplexFunc.residueOf(resF, z0.re, z0.im)
                resOut = "Res = ${fmtCx(r.value)}（${r.note}）"
                resErr = false
            } catch (e: Exception) {
                resOut = e.message ?: "计算失败"
                resErr = true
            }
        }
        if (resOut.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            ResultBox(resOut, error = resErr)
        }
        Spacer(Modifier.height(14.dp))

        // ---- 围道积分 ----
        Text("围道积分 ∮ f(z) dz（留数定理）", color = c.chromeInk, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(5.dp))
        NumField("f(z)", cintF, { cintF = it }, Modifier.fillMaxWidth())
        Spacer(Modifier.height(6.dp))
        NumField("围道内的极点（逗号分隔），如 i, -i", cintPoles, { cintPoles = it }, Modifier.fillMaxWidth())
        Spacer(Modifier.height(6.dp))
        SubButton("2πi·ΣRes 求积分", primary = true, modifier = Modifier.fillMaxWidth()) {
            try {
                val poles = cintPoles.split(',', ';', '，')
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .map { ComplexFunc.evalExpr(it, Cx.ZERO) }
                val (v, note) = ComplexFunc.contourOf(cintF, poles)
                cintOut = "∮ f dz = ${fmtCx(v)}\n$note"
                cintErr = false
            } catch (e: Exception) {
                cintOut = e.message ?: "计算失败"
                cintErr = true
            }
        }
        if (cintOut.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            ResultBox(cintOut, error = cintErr)
        }
        Spacer(Modifier.height(16.dp))
    }
}
