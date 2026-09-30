package io.paimon.fx991.engine

import kotlin.math.acos
import kotlin.math.sqrt

// ---------------------------------------------------------------------------
// 批次 C：三维向量（VctA–VctD；纯 Kotlin，可 JVM 回归）
// 双轨 Value：整数 / 分数分量在加减、点积、叉积里保持精确。
// ---------------------------------------------------------------------------

/** 三维向量 */
class Vector3(val x: Value, val y: Value, val z: Value) {

    val exact: Boolean get() = x.exact != null && y.exact != null && z.exact != null

    fun plus(o: Vector3): Vector3 =
        Vector3(ExactMath.add(x, o.x), ExactMath.add(y, o.y), ExactMath.add(z, o.z))

    fun minus(o: Vector3): Vector3 =
        Vector3(ExactMath.sub(x, o.x), ExactMath.sub(y, o.y), ExactMath.sub(z, o.z))

    /** 数乘 */
    fun scale(k: Value): Vector3 =
        Vector3(ExactMath.mul(x, k), ExactMath.mul(y, k), ExactMath.mul(z, k))

    /** 点积 A·B */
    fun dot(o: Vector3): Value = ExactMath.sum(
        listOf(
            ExactMath.mul(x, o.x),
            ExactMath.mul(y, o.y),
            ExactMath.mul(z, o.z),
        )
    )

    /** 叉积 A×B */
    fun cross(o: Vector3): Vector3 = Vector3(
        ExactMath.sub(ExactMath.mul(y, o.z), ExactMath.mul(z, o.y)),
        ExactMath.sub(ExactMath.mul(z, o.x), ExactMath.mul(x, o.z)),
        ExactMath.sub(ExactMath.mul(x, o.y), ExactMath.mul(y, o.x)),
    )

    /** 模 |A| */
    fun norm(): Double = sqrt(
        x.toDouble() * x.toDouble() +
            y.toDouble() * y.toDouble() +
            z.toDouble() * z.toDouble()
    )

    /** 模的精确值（各项平方和能开尽时给有理数） */
    fun normExact(): Rational? {
        val ex = x.exact ?: return null
        val ey = y.exact ?: return null
        val ez = z.exact ?: return null
        val s = ex.times(ex).plus(ey.times(ey)).plus(ez.times(ez))
        return s.sqrtExact()
    }

    fun isZero(): Boolean = ExactMath.isZero(x) && ExactMath.isZero(y) && ExactMath.isZero(z)

    /** 单位化（零向量抛错） */
    fun unit(): Vector3 {
        val n = norm()
        if (n == 0.0) throw NumericError("零向量无法单位化")
        return Vector3(Value.of(x.toDouble() / n), Value.of(y.toDouble() / n), Value.of(z.toDouble() / n))
    }

    /** A 与 B 的夹角（按角度制返回）；零向量抛错 */
    fun angleTo(o: Vector3, mode: AngleMode): Double {
        val na = norm()
        val nb = o.norm()
        if (na == 0.0 || nb == 0.0) throw NumericError("零向量没有方向")
        val c = dot(o).toDouble() / (na * nb)
        return CalcEngine.fromRadians(mode, acos(c.coerceIn(-1.0, 1.0)))
    }

    fun format(): String = "(" +
        ExactMath.str(x) + ", " + ExactMath.str(y) + ", " + ExactMath.str(z) + ")"

    override fun toString(): String = format()

    companion object {

        @JvmStatic
        fun of(x: Double, y: Double, z: Double): Vector3 =
            Vector3(Value.of(x), Value.of(y), Value.of(z))

        /** 由字面量构造（能转有理数则走精确轨） */
        @JvmStatic
        fun ofLiteral(x: String, y: String, z: String): Vector3 =
            Vector3(ComplexNum.literal(x), ComplexNum.literal(y), ComplexNum.literal(z))

        @JvmStatic
        fun ofInts(x: Int, y: Int, z: Int): Vector3 =
            Vector3(ExactMath.long(x.toLong()), ExactMath.long(y.toLong()), ExactMath.long(z.toLong()))
    }
}

/** VctA–VctD 四个向量变量 */
class VectorStore {

    private val vecs = LinkedHashMap<String, Vector3?>()

    fun set(name: String, v: Vector3?) {
        key(name)
        vecs[name] = v
    }

    fun get(name: String): Vector3? {
        key(name)
        return vecs[name]
    }

    fun clearAll() {
        vecs.clear()
    }

    fun snapshot(): Map<String, Vector3?> = LinkedHashMap(vecs)

    private fun key(name: String) {
        if (name !in NAMES) throw NumericError("未知向量变量 $name")
    }

    companion object {
        @JvmField
        val NAMES: List<String> = listOf("A", "B", "C", "D")
    }
}
