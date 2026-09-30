package io.paimon.fx991.engine

import kotlin.math.abs
import kotlin.math.sqrt

// ---------------------------------------------------------------------------
// 批次 C：统计与回归（单变量 / 双变量；纯 Kotlin，可 JVM 回归）
// ---------------------------------------------------------------------------

/** 单变量统计结果 */
class OneVarStat(
    val n: Int,
    val sum: Double,
    val sumSq: Double,
    val mean: Double,
    /** 总体标准差 σ（分母 n） */
    val popSigma: Double,
    /** 样本标准差 s（分母 n−1） */
    val sampleS: Double,
    val min: Double,
    val max: Double,
    val median: Double,
)

/** 双变量线性回归 y = a + b·x 与相关系数 r */
class LinReg(
    val a: Double,
    val b: Double,
    val r: Double,
    val n: Int,
)

object StatOps {

    /** 单变量统计；空数据抛错 */
    fun oneVar(xs: List<Double>): OneVarStat {
        if (xs.isEmpty()) throw NumericError("没有数据")
        val n = xs.size
        var sum = 0.0
        var sumSq = 0.0
        for (v in xs) {
            sum += v
            sumSq += v * v
        }
        val mean = sum / n
        var dev = 0.0
        for (v in xs) dev += (v - mean) * (v - mean)
        val pop = sqrt(dev / n)
        val samp = if (n >= 2) sqrt(dev / (n - 1)) else Double.NaN
        val sorted = xs.sorted()
        val median = if (n % 2 == 1) sorted[n / 2]
        else (sorted[n / 2 - 1] + sorted[n / 2]) / 2.0
        return OneVarStat(n, sum, sumSq, mean, pop, samp, sorted.first(), sorted.last(), median)
    }

    /** 双变量线性回归 y = a + b·x */
    fun linearRegression(xs: List<Double>, ys: List<Double>): LinReg {
        if (xs.isEmpty() || xs.size != ys.size) throw NumericError("双变量数据长度不一致")
        val n = xs.size
        if (n < 2) throw NumericError("线性回归至少需要 2 组数据")
        var sx = 0.0
        var sy = 0.0
        var sxy = 0.0
        var sxx = 0.0
        var syy = 0.0
        for (i in 0 until n) {
            sx += xs[i]
            sy += ys[i]
            sxy += xs[i] * ys[i]
            sxx += xs[i] * xs[i]
            syy += ys[i] * ys[i]
        }
        val denom = n * sxx - sx * sx
        if (abs(denom) < 1e-300) throw NumericError("x 全部相同，无法回归")
        val b = (n * sxy - sx * sy) / denom
        val a = (sy - b * sx) / n
        val rDenom = sqrt(denom * (n * syy - sy * sy))
        val r = if (rDenom < 1e-300) 0.0 else (n * sxy - sx * sy) / rDenom
        return LinReg(a, b, r, n)
    }
}
