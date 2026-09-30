package io.paimon.fx991.engine

// ---------------------------------------------------------------------------
// 批次 C：矩阵运算（MatA–MatD，最大 4×4；纯 Kotlin，可 JVM 回归）
//
// 元素是双轨 Value：整数 / 分数矩阵的加减乘、行列式、逆全部保持精确分数。
// ---------------------------------------------------------------------------

/** 矩阵（行优先，元素为双轨 Value） */
class Matrix(val rows: Int, val cols: Int, val cells: List<Value>) {

    init {
        if (rows !in 1..MAX_DIM || cols !in 1..MAX_DIM) {
            throw NumericError("矩阵尺寸必须在 1..$MAX_DIM 之间")
        }
        if (cells.size != rows * cols) {
            throw NumericError("矩阵元素个数与尺寸不匹配")
        }
    }

    operator fun get(r: Int, c: Int): Value = cells[r * cols + c]

    /** 矩阵是否全在精确轨上 */
    val exact: Boolean get() = cells.all { it.exact != null }

    fun plus(o: Matrix): Matrix {
        requireSame(o)
        return Matrix(rows, cols, cells.indices.map { ExactMath.add(cells[it], o.cells[it]) })
    }

    fun minus(o: Matrix): Matrix {
        requireSame(o)
        return Matrix(rows, cols, cells.indices.map { ExactMath.sub(cells[it], o.cells[it]) })
    }

    fun times(o: Matrix): Matrix {
        if (cols != o.rows) throw NumericError("矩阵相乘要求左列数 = 右行数")
        val out = ArrayList<Value>(rows * o.cols)
        for (i in 0 until rows) {
            for (j in 0 until o.cols) {
                var s = ExactMath.zero()
                for (k in 0 until cols) s = ExactMath.add(s, ExactMath.mul(this[i, k], o[k, j]))
                out.add(s)
            }
        }
        return Matrix(rows, o.cols, out)
    }

    fun scalar(k: Value): Matrix = Matrix(rows, cols, cells.map { ExactMath.mul(it, k) })

    fun transpose(): Matrix {
        val out = ArrayList<Value>(rows * cols)
        for (j in 0 until cols) for (i in 0 until rows) out.add(this[i, j])
        return Matrix(cols, rows, out)
    }

    /** 行列式（方阵；1..4 阶用余子式展开，精确轨优先） */
    fun det(): Value {
        if (rows != cols) throw NumericError("行列式要求方阵")
        return detOf(cells, rows)
    }

    /** 逆矩阵（不可逆时抛错） */
    fun inverse(): Matrix {
        if (rows != cols) throw NumericError("求逆要求方阵")
        val n = rows
        val d = det()
        if (ExactMath.isZero(d)) throw NumericError("矩阵不可逆（行列式为 0）")
        val out = ArrayList<Value>(n * n)
        for (i in 0 until n) {
            for (j in 0 until n) {
                // (i,j) = 代数余子式 (j,i) / det
                val cof = cofactor(this, j, i, n)
                out.add(ExactMath.div(cof, d))
            }
        }
        return Matrix(n, n, out)
    }

    /** 多行文本（每行元素用两空格分隔） */
    fun format(): String {
        val sb = StringBuilder()
        for (i in 0 until rows) {
            if (i > 0) sb.append('\n')
            for (j in 0 until cols) {
                if (j > 0) sb.append("  ")
                sb.append(ExactMath.str(this[i, j]))
            }
        }
        return sb.toString()
    }

    override fun toString(): String = format()

    private fun requireSame(o: Matrix) {
        if (rows != o.rows || cols != o.cols) throw NumericError("矩阵维度不一致")
    }

    private fun detOf(c: List<Value>, n: Int): Value {
        if (n == 1) return c[0]
        if (n == 2) return ExactMath.sub(
            ExactMath.mul(c[0], c[3]), ExactMath.mul(c[1], c[2])
        )
        var s = ExactMath.zero()
        for (j in 0 until n) {
            val m = minor(c, n, 0, j)
            val term = ExactMath.mul(c[j], detOf(m, n - 1))
            s = if (j % 2 == 0) ExactMath.add(s, term) else ExactMath.sub(s, term)
        }
        return s
    }

    /** 删去第 r 行第 cc 列后的 (n−1) 阶余子式 */
    private fun minor(c: List<Value>, n: Int, r: Int, cc: Int): List<Value> {
        val out = ArrayList<Value>((n - 1) * (n - 1))
        for (i in 0 until n) {
            if (i == r) continue
            for (j in 0 until n) {
                if (j == cc) continue
                out.add(c[i * n + j])
            }
        }
        return out
    }

    /** 代数余子式 (−1)^(i+j) · M(i,j) */
    private fun cofactor(m: Matrix, i: Int, j: Int, n: Int): Value {
        val mn = minor(m.cells, n, i, j)
        val d = detOf(mn, n - 1)
        return if ((i + j) % 2 == 0) d else ExactMath.neg(d)
    }

    companion object {
        const val MAX_DIM = 4

        @JvmStatic
        fun of(rows: Int, cols: Int, cells: List<Value>): Matrix = Matrix(rows, cols, cells)

        /** 由若干整数构造（便于 Java 回归） */
        @JvmStatic
        fun ofInts(rows: Int, cols: Int, values: IntArray): Matrix {
            val list = values.map { ExactMath.long(it.toLong()) }
            return Matrix(rows, cols, list)
        }

        @JvmStatic
        fun identity(n: Int): Matrix {
            if (n !in 1..MAX_DIM) throw NumericError("矩阵尺寸必须在 1..$MAX_DIM 之间")
            val out = ArrayList<Value>(n * n)
            for (i in 0 until n) for (j in 0 until n) {
                out.add(if (i == j) ExactMath.one() else ExactMath.zero())
            }
            return Matrix(n, n, out)
        }

        /** 全零矩阵 */
        @JvmStatic
        fun zeros(rows: Int, cols: Int): Matrix =
            Matrix(rows, cols, List(rows * cols) { ExactMath.zero() })
    }
}

/** MatA–MatD 四个矩阵变量 */
class MatrixStore {

    private val mats = LinkedHashMap<String, Matrix?>()

    fun set(name: String, m: Matrix?) {
        key(name)
        mats[name] = m
    }

    fun get(name: String): Matrix? {
        key(name)
        return mats[name]
    }

    fun clearAll() {
        mats.clear()
    }

    fun snapshot(): Map<String, Matrix?> = LinkedHashMap(mats)

    private fun key(name: String) {
        if (name !in NAMES) throw NumericError("未知矩阵变量 $name")
    }

    companion object {
        @JvmField
        val NAMES: List<String> = listOf("A", "B", "C", "D")
    }
}
