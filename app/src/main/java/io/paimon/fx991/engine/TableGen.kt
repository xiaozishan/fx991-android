package io.paimon.fx991.engine

import kotlin.math.floor

// ---------------------------------------------------------------------------
// 批次 D：函数表 TABLE
//
// 输入 f(x)（可选 g(x) 双函数对照），给起值 / 终值 / 步长，出数值表。
// 求值复用主引擎 CalcEngine.evaluateWith（三角函数跟随角度制）。
// 某个点无定义（如 1/x 在 x=0）时该格显示「错误」，不中断整张表。
// ---------------------------------------------------------------------------

object TableGen {

    /** 行数上限（翻页展示，避免一次生成过多） */
    const val MAX_ROWS = 200

    /** 一行：x 处的 f(x) 与 g(x)（null = 该点无定义 / 计算出错） */
    class TableRow(val x: Double, val f: Double?, val g: Double?)

    class TableData(val rows: List<TableRow>, val hasG: Boolean)

    fun parseNumber(s: String, what: String): Double =
        s.trim().toDoubleOrNull() ?: throw NumericError("$what 不是合法数字")

    /**
     * 生成数值表。step 不能为 0，方向必须与 终值−起值 一致；
     * f(x) 语法错误直接报，单点数学错误只落到那一格。
     */
    fun generate(
        fx: String,
        gx: String,
        start: Double,
        end: Double,
        step: Double,
        mode: AngleMode,
    ): TableData {
        val f = fx.trim()
        if (f.isEmpty()) throw NumericError("请输入 f(x)")
        val g = gx.trim()
        if (step.isNaN() || step == 0.0) throw NumericError("步长不能为 0")
        val span = end - start
        if (span.isNaN()) throw NumericError("起值 / 终值不是合法数字")
        if (span * step < 0) throw NumericError("步长方向与起终值不一致")
        val count = if (span == 0.0) 1L else floor(span / step + 1e-9).toLong() + 1L
        if (count > MAX_ROWS) throw NumericError("行数过多（上限 $MAX_ROWS 行，请增大步长或缩小范围）")

        // 先做一次语法预检：语法错误直接报，数学错误留给单元格
        try {
            CalcEngine.evaluateWith(f, mode, start, 0.0)
        } catch (_: CalcSyntaxError) {
            throw NumericError("f(x) 语法错误")
        } catch (_: Exception) {
            // 单点无定义不拦
        }
        if (g.isNotEmpty()) {
            try {
                CalcEngine.evaluateWith(g, mode, start, 0.0)
            } catch (_: CalcSyntaxError) {
                throw NumericError("g(x) 语法错误")
            } catch (_: Exception) {
            }
        }

        val rows = ArrayList<TableRow>()
        var i = 0L
        while (i < count) {
            val x = start + step * i
            val fv = try {
                CalcEngine.evaluateWith(f, mode, x, 0.0)
            } catch (_: Exception) {
                null
            }
            val gv = if (g.isEmpty()) null else try {
                CalcEngine.evaluateWith(g, mode, x, 0.0)
            } catch (_: Exception) {
                null
            }
            rows.add(TableRow(x, fv, gv))
            i++
        }
        return TableData(rows, g.isNotEmpty())
    }
}
