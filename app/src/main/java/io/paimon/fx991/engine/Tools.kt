package io.paimon.fx991.engine

import java.util.Random

// ---------------------------------------------------------------------------
// 批次 B：科学常数 / 单位换算 / SI 前缀 / 随机数 / 变量寄存器（全部纯 Kotlin，
// 无 Compose 依赖 → 可在 JVM 上直接回归测试）
// ---------------------------------------------------------------------------

/** 一条科学常数 */
class SciConstant(val symbol: String, val name: String, val value: Double, val unit: String)

/** 科学常数表（CODATA 2018/2019 SI 定义值） */
object SciConstants {

    @JvmField
    val ALL: List<SciConstant> = listOf(
        SciConstant("c", "光速（真空）", 299792458.0, "m/s"),
        SciConstant("h", "普朗克常数", 6.62607015e-34, "J·s"),
        SciConstant("ħ", "约化普朗克常数", 1.054571817e-34, "J·s"),
        SciConstant("e", "元电荷", 1.602176634e-19, "C"),
        SciConstant("NA", "阿伏伽德罗常数", 6.02214076e23, "/mol"),
        SciConstant("k", "玻尔兹曼常数", 1.380649e-23, "J/K"),
        SciConstant("G", "万有引力常数", 6.67430e-11, "m³/(kg·s²)"),
        SciConstant("g", "标准重力加速度", 9.80665, "m/s²"),
        SciConstant("me", "电子质量", 9.1093837015e-31, "kg"),
        SciConstant("mp", "质子质量", 1.67262192369e-27, "kg"),
        SciConstant("mn", "中子质量", 1.67492749804e-27, "kg"),
        SciConstant("mμ", "缪子质量", 1.883531627e-28, "kg"),
        SciConstant("u", "原子质量单位", 1.66053906660e-27, "kg"),
        SciConstant("R", "摩尔气体常数", 8.314462618, "J/(mol·K)"),
        SciConstant("F", "法拉第常数", 96485.33212, "C/mol"),
        SciConstant("Vm", "摩尔体积（理想气体 273.15K）", 0.02241396954, "m³/mol"),
        SciConstant("ε0", "真空电容率", 8.8541878128e-12, "F/m"),
        SciConstant("μ0", "真空磁导率", 1.25663706212e-6, "N/A²"),
        SciConstant("atm", "标准大气压", 101325.0, "Pa"),
        SciConstant("σ", "斯特藩-玻尔兹曼常数", 5.670374419e-8, "W/(m²·K⁴)"),
        SciConstant("R∞", "里德伯常数", 10973731.568160, "/m"),
        SciConstant("μB", "玻尔磁子", 9.2740100783e-24, "J/T"),
        SciConstant("α", "精细结构常数", 7.2973525693e-3, "—"),
        SciConstant("Ry", "里德伯能量", 2.1798723611e-18, "J"),
    )

    @JvmField
    val COUNT: Int = ALL.size
}

// ---------------------------------------------------------------------------
// 单位换算
// ---------------------------------------------------------------------------

/** 一个单位；`value_base = value × factor`（温度这类仿射换算走 affine 分支） */
class UnitDef(val name: String, val symbol: String, val factor: Double)

/** 一类单位 */
class UnitCategory(
    val name: String,
    val baseSymbol: String,
    val units: List<UnitDef>,
    /** 仿射（非倍数）换算，如温度 */
    val affine: Boolean = false,
)

object UnitConvert {

    @JvmField
    val LENGTH = UnitCategory("长度", "m", listOf(
        UnitDef("纳米", "nm", 1e-9),
        UnitDef("微米", "μm", 1e-6),
        UnitDef("毫米", "mm", 1e-3),
        UnitDef("厘米", "cm", 1e-2),
        UnitDef("米", "m", 1.0),
        UnitDef("千米", "km", 1e3),
        UnitDef("英寸", "in", 0.0254),
        UnitDef("英尺", "ft", 0.3048),
        UnitDef("码", "yd", 0.9144),
        UnitDef("英里", "mi", 1609.344),
        UnitDef("海里", "nmi", 1852.0),
    ))

    @JvmField
    val MASS = UnitCategory("质量", "kg", listOf(
        UnitDef("毫克", "mg", 1e-6),
        UnitDef("克", "g", 1e-3),
        UnitDef("千克", "kg", 1.0),
        UnitDef("吨", "t", 1e3),
        UnitDef("盎司", "oz", 0.028349523125),
        UnitDef("磅", "lb", 0.45359237),
    ))

    @JvmField
    val TIME = UnitCategory("时间", "s", listOf(
        UnitDef("毫秒", "ms", 1e-3),
        UnitDef("秒", "s", 1.0),
        UnitDef("分", "min", 60.0),
        UnitDef("小时", "h", 3600.0),
        UnitDef("天", "d", 86400.0),
        UnitDef("周", "wk", 604800.0),
    ))

    @JvmField
    val AREA = UnitCategory("面积", "m²", listOf(
        UnitDef("平方厘米", "cm²", 1e-4),
        UnitDef("平方米", "m²", 1.0),
        UnitDef("平方千米", "km²", 1e6),
        UnitDef("公顷", "ha", 1e4),
        UnitDef("亩", "亩", 2000.0 / 3.0),
        UnitDef("平方英尺", "ft²", 0.09290304),
        UnitDef("英亩", "acre", 4046.8564224),
    ))

    @JvmField
    val VOLUME = UnitCategory("体积", "L", listOf(
        UnitDef("毫升", "mL", 1e-3),
        UnitDef("立方厘米", "cm³", 1e-3),
        UnitDef("升", "L", 1.0),
        UnitDef("立方米", "m³", 1e3),
        UnitDef("美制加仑", "gal", 3.785411784),
        UnitDef("英制加仑", "gal(UK)", 4.54609),
    ))

    /** 温度：非倍数关系，单独处理 */
    @JvmField
    val TEMPERATURE = UnitCategory("温度", "°C", listOf(
        UnitDef("摄氏度", "°C", 1.0),
        UnitDef("华氏度", "°F", 1.0),
        UnitDef("开尔文", "K", 1.0),
    ), affine = true)

    @JvmField
    val CATEGORIES: List<UnitCategory> =
        listOf(LENGTH, MASS, TIME, AREA, VOLUME, TEMPERATURE)

    /** 按类别对象换算（非倍数关系由 affine 分支处理） */
    fun convert(category: UnitCategory, fromIndex: Int, toIndex: Int, value: Double): Double {
        require(fromIndex in category.units.indices && toIndex in category.units.indices) {
            "单位下标越界"
        }
        if (!category.affine) {
            val base = value * category.units[fromIndex].factor
            return base / category.units[toIndex].factor
        }
        val celsius = toCelsius(category.units[fromIndex].symbol, value)
        return fromCelsius(category.units[toIndex].symbol, celsius)
    }

    /** 按类别下标换算 */
    fun convert(categoryIndex: Int, fromIndex: Int, toIndex: Int, value: Double): Double =
        convert(CATEGORIES[categoryIndex], fromIndex, toIndex, value)

    /** 温度 → 摄氏 */
    fun toCelsius(symbol: String, v: Double): Double = when (symbol) {
        "°C" -> v
        "°F" -> (v - 32.0) * 5.0 / 9.0
        "K" -> v - 273.15
        else -> throw NumericError("未知温度单位 $symbol")
    }

    /** 摄氏 → 温度 */
    fun fromCelsius(symbol: String, c: Double): Double = when (symbol) {
        "°C" -> c
        "°F" -> c * 9.0 / 5.0 + 32.0
        "K" -> c + 273.15
        else -> throw NumericError("未知温度单位 $symbol")
    }
}

// ---------------------------------------------------------------------------
// SI 前缀
// ---------------------------------------------------------------------------

class SiPrefix(val name: String, val symbol: String, val factor: Double)

object SiPrefixes {

    @JvmField
    val ALL: List<SiPrefix> = listOf(
        SiPrefix("尧", "Y", 1e24),
        SiPrefix("泽", "Z", 1e21),
        SiPrefix("艾", "E", 1e18),
        SiPrefix("拍", "P", 1e15),
        SiPrefix("太", "T", 1e12),
        SiPrefix("吉", "G", 1e9),
        SiPrefix("兆", "M", 1e6),
        SiPrefix("千", "k", 1e3),
        SiPrefix("百", "h", 1e2),
        SiPrefix("十", "da", 1e1),
        SiPrefix("（无）", "", 1.0),
        SiPrefix("分", "d", 1e-1),
        SiPrefix("厘", "c", 1e-2),
        SiPrefix("毫", "m", 1e-3),
        SiPrefix("微", "μ", 1e-6),
        SiPrefix("纳", "n", 1e-9),
        SiPrefix("皮", "p", 1e-12),
        SiPrefix("飞", "f", 1e-15),
        SiPrefix("阿", "a", 1e-18),
        SiPrefix("仄", "z", 1e-21),
        SiPrefix("幺", "y", 1e-24),
    )

    /** 默认选中「（无）」的下标 */
    @JvmField
    val NONE_INDEX: Int = ALL.indexOfFirst { it.factor == 1.0 }

    /** 换算：value 以 from 前缀计 → 以 to 前缀计 */
    fun convert(fromIndex: Int, toIndex: Int, value: Double): Double {
        require(fromIndex in ALL.indices && toIndex in ALL.indices) { "SI 前缀下标越界" }
        return value * ALL[fromIndex].factor / ALL[toIndex].factor
    }
}

// ---------------------------------------------------------------------------
// 随机数
// ---------------------------------------------------------------------------

object RandomOps {

    private val rng = Random()

    /** Ran#：0 ≤ x < 1 均匀分布 */
    fun uniform(): Double = rng.nextDouble()

    /** RanInt(a,b)：[a,b] 上的均匀整数 */
    fun ranInt(a: Long, b: Long): Long {
        if (b < a) throw NumericError("上界不能小于下界")
        if (a > 9_000_000_000L || b > 9_000_000_000L || a < -9_000_000_000L) {
            throw NumericError("随机整数范围过大")
        }
        val span = b - a + 1
        return a + (rng.nextDouble() * span).toLong().coerceIn(0, span - 1)
    }

    /** RanInt(a,b,n)：n 个 [a,b] 上的整数 */
    fun ranInts(a: Long, b: Long, n: Int): List<Long> {
        if (n < 1) throw NumericError("次数必须 ≥ 1")
        if (n > 100_000) throw NumericError("次数过多（> 100000）")
        return List(n) { ranInt(a, b) }
    }
}

// ---------------------------------------------------------------------------
// 变量寄存器（STO）：A–F / x / y
// ---------------------------------------------------------------------------

class Registers {

    private val vars = LinkedHashMap<String, Double>()

    /** 存入一个变量，返回给用户的确认提示 */
    fun store(name: String, value: Double): String {
        if (name !in VARIABLES) throw NumericError("未知变量 $name")
        vars[name] = value
        return "已存入 $name = " + CalcEngine.format(value, 10, null)
    }

    fun get(name: String): Double? = vars[name]

    /** 供表达式代入使用的变量表（仅 A–F / x / y） */
    fun snapshot(): Map<String, Double> = LinkedHashMap(vars)

    fun clearAll() {
        vars.clear()
    }

    val isEmpty: Boolean get() = vars.isEmpty()

    companion object {
        /** 变量键位：A–F 数字变量 + x / y（M 是存储器，不在其中） */
        @JvmField
        val VARIABLES: List<String> = listOf("A", "B", "C", "D", "E", "F", "x", "y")
    }
}
