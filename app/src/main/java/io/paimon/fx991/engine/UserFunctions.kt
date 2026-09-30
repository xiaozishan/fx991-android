package io.paimon.fx991.engine

import kotlin.math.abs

// ---------------------------------------------------------------------------
// 批次 G：GeoGebra 式自定义函数（纯 Kotlin，可 JVM 回归）
//
// 主行直接敲：
//   f(x)=x^2      定义 f（未定义过的名字 + `=` → 定义）
//   f(x):=x^2     强制定义 / 重定义（即使 f 已存在）
//   f(3)          调用 → 9
//   f(x)+1        参与表达式
//   f'(2)         一阶数值导数 → 4；f''(x) 支持二阶
//   f(x)=0        f 已定义时 → 当方程求解（走 EquationSolver 数值扫描）
//
// 登记表是进程内单例（与 STO 寄存器同语义）：重启不保留，README 已写明。
// 解析器 / 求值器在碰到已注册名字时直接查这张表，所以方程求解、∫dx、
// Σ 等存量通道（都走同一 Parser / ValueEvaluator）自动获得调用能力。
// ---------------------------------------------------------------------------

/** 一个已定义的用户函数 */
class UserFnDef internal constructor(
    val name: String,
    val params: List<String>,
    val bodyText: String,
    internal val body: Node,
) {
    /** f(x) 形式的签名 */
    fun signature(): String = "$name(${params.joinToString(",")})"

    /** 完整定义文本：f(x) = x^2 */
    fun fullText(): String = "${signature()} = $bodyText"
}

object UserFunctions {

    /** 递归保护：嵌套调用最大深度（互相重定义可成环） */
    private const val MAX_DEPTH = 24

    /** 参数个数上限 */
    const val MAX_PARAMS = 4

    private val defs = LinkedHashMap<String, UserFnDef>()

    /** 定义表版本号（UI 观察用） */
    var revision = 0
        private set

    @Volatile
    private var callDepth = 0

    // -----------------------------------------------------------------------
    // 定义
    // -----------------------------------------------------------------------

    /** 定义串的解析结果（shapeOnly=true 时不解析 body，供预览使用） */
    class DefShape(val name: String, val params: List<String>, val bodyText: String, val colonEq: Boolean)

    /**
     * 识别「f(x)=…」/「f(x):=…」的定义外形；不是定义返回 null。
     * 只检查 LHS 形状与名字合法性，不解析 body（预览 / 分流用）。
     */
    @JvmStatic
    fun definitionShape(src: String): DefShape? {
        val t = src.trim()
        // 找顶层 '='（LHS 的括号不计深度）
        var depth = 0
        var eq = -1
        var i = 0
        while (i < t.length) {
            when (t[i]) {
                '(' -> depth++
                ')' -> depth--
                '=' -> if (depth == 0) { eq = i; break }
            }
            i++
        }
        if (eq <= 0) return null
        val colonEq = t[eq - 1] == ':'
        val lhs = t.substring(0, if (colonEq) eq - 1 else eq).trim()
        val bodyText = t.substring(eq + 1).trim()
        if (bodyText.isEmpty()) return null
        val m = Regex("^([A-Za-z]+)\\(([A-Za-z,\\s]*)\\)$").matchEntire(lhs) ?: return null
        val name = m.groupValues[1]
        val params = m.groupValues[2].split(',').map { it.trim() }.filter { it.isNotEmpty() }
        if (params.isEmpty() || params.size > MAX_PARAMS) return null
        // 不合法外形一律返回 null（落到方程求解等既有路径），不在这里抛错——
        // 否则 sin(x)=0.5 这类「左边恰好是内置函数调用」的方程会被误伤
        if (params.distinct().size != params.size) return null
        if (!nameOk(name)) return null
        return DefShape(name, params, bodyText, colonEq)
    }

    /**
     * 尝试把 src 当函数定义处理：
     *  - 是定义外形且（名字未注册过 或 用了 `:=`）→ 解析 body、登记并返回定义
     *  - 是定义外形但名字已注册且只写了 `=` → 返回 null（留给方程求解）
     *  - 不是定义外形 → 返回 null
     * body 有语法错误时抛 CalcSyntaxError。
     */
    @JvmStatic
    fun tryDefine(src: String): UserFnDef? {
        val shape = definitionShape(src) ?: return null
        if (defs.containsKey(shape.name) && !shape.colonEq) return null
        val body = Parser(Lexer(shape.bodyText).lex(), shape.params.toSet()).parse()
        val def = UserFnDef(shape.name, shape.params, shape.bodyText, body)
        defs[shape.name] = def
        revision++
        return def
    }

    /** 名字是否可用作自定义函数名（不撞内置 / 常量 / 寄存器） */
    @JvmStatic
    fun nameOk(name: String): Boolean {
        if (name.isEmpty() || !name.all { it.isLetter() }) return false
        val low = name.lowercase()
        if (low in FUNC_NAMES || low in FUNC2_NAMES || low in MAT_FUNC_NAMES || low in VARARG_NAMES) return false
        if (low in setOf("fourier", "pi")) return false
        if (name in setOf("x", "y", "z", "i", "e", "M", "Ans", "PreAns", "\u03C0")) return false
        if (name.length == 1 && name in VAR_NAMES) return false   // A–F 是 STO 变量
        if (name.length == 4 && name.startsWith("Mat") && name[3] in 'A'..'D') return false
        if (name.length == 4 && name.startsWith("Vct") && name[3] in 'A'..'D') return false
        return true
    }

    // -----------------------------------------------------------------------
    // 查询 / 管理（代数区）
    // -----------------------------------------------------------------------

    @JvmStatic
    fun contains(name: String): Boolean = defs.containsKey(name)

    @JvmStatic
    fun get(name: String): UserFnDef? = defs[name]

    @JvmStatic
    fun list(): List<UserFnDef> = defs.values.toList()

    @JvmStatic
    fun remove(name: String): Boolean {
        val r = defs.remove(name) != null
        if (r) revision++
        return r
    }

    @JvmStatic
    fun clear() {
        if (defs.isNotEmpty()) {
            defs.clear()
            revision++
        }
    }

    // -----------------------------------------------------------------------
    // 调用（求值器入口；args 走双轨 Value，参数绑定为浮点 —— 见 README 已知限制）
    // -----------------------------------------------------------------------

    /**
     * 调用已定义函数。
     * @param deriv 导数阶数：0 调用 / 1 一阶导 / 2 二阶导（仅单变量函数）
     */
    @JvmStatic
    fun call(name: String, args: List<Value>, deriv: Int, mode: AngleMode): Value {
        val def = defs[name] ?: throw CalcSyntaxError("未定义函数 $name")
        if (args.size != def.params.size) {
            throw CalcSyntaxError("$name 需要 ${def.params.size} 个参数")
        }
        if (deriv > 0) {
            if (def.params.size != 1) throw CalcMathError("导数只支持单变量函数")
            if (deriv > 2) throw CalcMathError("最多支持二阶导数")
            return Value.of(derivAt(def, args[0].toDouble(), deriv, mode))
        }
        if (callDepth >= MAX_DEPTH) throw CalcMathError("函数递归过深（可能存在循环定义）")
        callDepth++
        try {
            val bindings = HashMap<String, Double>()
            for (k in def.params.indices) bindings[def.params[k]] = args[k].toDouble()
            return ValueEvaluator(
                mode, Value.Floating(0.0), Value.Floating(0.0), vars = bindings,
            ).eval(def.body)
        } finally {
            callDepth--
        }
    }

    /** 浮点轨便捷入口（批次 A 的 Evaluator 用） */
    @JvmStatic
    fun callD(name: String, args: List<Double>, deriv: Int, mode: AngleMode): Double =
        call(name, args.map { Value.of(it) }, deriv, mode).toDouble()

    /** 数值导数：中心差分 + Richardson 外推（与 NumericOps.derivative 同方法） */
    private fun derivAt(def: UserFnDef, x: Double, order: Int, mode: AngleMode): Double {
        val scale = maxOf(1.0, abs(x))
        fun f(t: Double): Double = call(def.name, listOf(Value.of(t)), 0, mode).toDouble()
        val r = if (order == 1) {
            val h0 = 1e-3 * scale
            fun d(h: Double) = (f(x + h) - f(x - h)) / (2.0 * h)
            val d1 = d(h0)
            val d2 = d(h0 / 2.0)
            d2 + (d2 - d1) / 3.0
        } else {
            val h0 = 5e-3 * scale
            fun d(h: Double) = (f(x + h) - 2.0 * f(x) + f(x - h)) / (h * h)
            val d1 = d(h0)
            val d2 = d(h0 / 2.0)
            d2 + (d2 - d1) / 3.0
        }
        if (r.isNaN() || r.isInfinite()) throw CalcMathError("导数计算失败")
        return r
    }
}
